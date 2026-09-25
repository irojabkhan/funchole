package com.funchole.backend.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funchole.backend.controlplane.config.SourceStorageProperties;
import com.funchole.backend.controlplane.constant.DomainStatus;
import com.funchole.backend.controlplane.constant.FlowStepComponentType;
import com.funchole.backend.controlplane.constant.GatewayStatus;
import com.funchole.backend.controlplane.dto.FlowFullSourceResponse;
import com.funchole.backend.controlplane.dto.FlowStepSourceResponse;
import com.funchole.backend.controlplane.entity.AppDomain;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.entity.FlowStep;
import com.funchole.backend.controlplane.entity.FlowVersion;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.entity.Gateway;
import com.funchole.backend.controlplane.repository.AppDomainRepository;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import com.funchole.backend.controlplane.repository.FlowStepRepository;
import com.funchole.backend.controlplane.repository.FlowVersionRepository;
import com.funchole.backend.controlplane.repository.FunctionVersionRepository;
import com.funchole.backend.controlplane.repository.GatewayRepository;
import com.funchole.backend.controlplane.functionbuild.FunctionBuildExecutor;
import com.funchole.backend.controlplane.service.FlowFullSourceService;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FlowFullSourceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FlowFullSourceService flowFullSourceService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AppDomainRepository appDomainRepository;

    @Autowired
    private GatewayRepository gatewayRepository;

    @Autowired
    private FlowStepRepository flowStepRepository;

    @Autowired
    private FlowVersionRepository flowVersionRepository;

    @Autowired
    private FunctionVersionRepository functionVersionRepository;

    @Autowired
    private SourceStorageProperties sourceStorageProperties;

    private String adminToken;
    private UUID adminId;
    private UUID gatewayId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = obtainToken("admin", "admin12345");
        AppUser admin = appUserRepository.findByUsername("admin").orElseThrow();
        adminId = admin.getId();

        AppDomain domain = appDomainRepository.save(
                AppDomain.create(admin, "flow-full-source-" + UUID.randomUUID() + ".example.com", "verify-me", DomainStatus.VERIFIED));
        Gateway gateway = gatewayRepository.save(
                Gateway.create(admin, domain, "Full Source Test Gateway", "fst" + System.nanoTime() % 100000, "test gateway", GatewayStatus.ACTIVE));
        gatewayId = gateway.getId();
    }

    @Test
    void returnsEveryStepsFunctionSourceForASimpleFlow() throws Exception {
        ReadyFunctionVersion logic = createReadyFunctionVersion("export function handler(input) { return { total: 42 }; }");
        ReadyFunctionVersion response = createReadyFunctionVersion("export function handler(input) { return { status: 200, body: input }; }");

        String flowId = createFlow();
        String versionId = createDraftVersion(flowId);
        createStep(flowId, versionId, "logic", "FUNCTION", 10, logic);
        createStep(flowId, versionId, "response", "RESPONSE", 20, response);
        adopt(flowId, versionId);

        FlowFullSourceResponse fullSource = flowFullSourceService.getFullSource(adminId, UUID.fromString(flowId), UUID.fromString(versionId));

        assertThat(fullSource.steps()).hasSize(2);
        FlowStepSourceResponse logicStep = fullSource.steps().get(0);
        assertThat(logicStep.componentType()).isEqualTo("FUNCTION");
        assertThat(logicStep.unavailableReason()).isNull();
        assertThat(logicStep.function()).isNotNull();
        assertThat(logicStep.function().functionVersionId()).isEqualTo(UUID.fromString(logic.functionVersionId()));
        assertThat(logicStep.function().files()).anySatisfy(file -> {
            assertThat(file.path()).isEqualTo("index.mjs");
            assertThat(file.content()).contains("total: 42");
        });

        FlowStepSourceResponse responseStep = fullSource.steps().get(1);
        assertThat(responseStep.function().files()).anySatisfy(file -> assertThat(file.content()).contains("status: 200"));
    }

    @Test
    void expandsASubFlowStepRecursively() throws Exception {
        ReadyFunctionVersion innerFunction = createReadyFunctionVersion("export function handler(input) { return { status: 200, body: \"inner\" }; }");
        String innerFlowId = createFlow();
        String innerVersionId = createDraftVersion(innerFlowId);
        createStep(innerFlowId, innerVersionId, "response", "RESPONSE", 10, innerFunction);
        adopt(innerFlowId, innerVersionId);

        String outerFlowId = createFlow();
        String outerVersionId = createDraftVersion(outerFlowId);
        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/steps", outerFlowId, outerVersionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepPayload("delegate", "SUB_FLOW", 10, innerFlowId, innerVersionId)))
                .andExpect(status().isOk());
        adopt(outerFlowId, outerVersionId);

        FlowFullSourceResponse fullSource =
                flowFullSourceService.getFullSource(adminId, UUID.fromString(outerFlowId), UUID.fromString(outerVersionId));

        assertThat(fullSource.steps()).hasSize(1);
        FlowStepSourceResponse subFlowStep = fullSource.steps().get(0);
        assertThat(subFlowStep.componentType()).isEqualTo("SUB_FLOW");
        assertThat(subFlowStep.unavailableReason()).isNull();
        assertThat(subFlowStep.function()).isNull();
        assertThat(subFlowStep.subFlow()).isNotNull();
        assertThat(subFlowStep.subFlow().flowId()).isEqualTo(UUID.fromString(innerFlowId));
        assertThat(subFlowStep.subFlow().steps()).hasSize(1);
        assertThat(subFlowStep.subFlow().steps().get(0).function().files())
                .anySatisfy(file -> assertThat(file.content()).contains("inner"));
    }

    @Test
    void reportsUnavailableReasonWhenAFunctionVersionNoLongerExists() throws Exception {
        ReadyFunctionVersion response = createReadyFunctionVersion("export function handler(input) { return { status: 200, body: {} }; }");
        String flowId = createFlow();
        String versionId = createDraftVersion(flowId);
        createStep(flowId, versionId, "response", "RESPONSE", 10, response);
        adopt(flowId, versionId);

        // Simulate the exact orphaned-data condition found live in this session
        // (a Flow step referencing a FunctionVersion row that no longer exists).
        FunctionVersion functionVersion = functionVersionRepository.findById(UUID.fromString(response.functionVersionId())).orElseThrow();
        functionVersionRepository.delete(functionVersion);
        functionVersionRepository.flush();

        FlowFullSourceResponse fullSource = flowFullSourceService.getFullSource(adminId, UUID.fromString(flowId), UUID.fromString(versionId));

        assertThat(fullSource.steps()).hasSize(1);
        FlowStepSourceResponse step = fullSource.steps().get(0);
        assertThat(step.function()).isNull();
        assertThat(step.subFlow()).isNull();
        assertThat(step.unavailableReason()).isNotBlank();
    }

    @Test
    void reportsUnavailableReasonWhenSourceContentIsMissingFromStorage() throws Exception {
        ReadyFunctionVersion response = createReadyFunctionVersion("export function handler(input) { return { status: 200, body: {} }; }");
        String flowId = createFlow();
        String versionId = createDraftVersion(flowId);
        createStep(flowId, versionId, "response", "RESPONSE", 10, response);
        adopt(flowId, versionId);

        // Simulate storage/DB drift (LocalSourceStore's own documented failure
        // mode): the manifest survives in Postgres but the file content on
        // disk is gone.
        deleteSourceContentFromDisk(response.functionVersionId());

        FlowFullSourceResponse fullSource = flowFullSourceService.getFullSource(adminId, UUID.fromString(flowId), UUID.fromString(versionId));

        FlowStepSourceResponse step = fullSource.steps().get(0);
        assertThat(step.function()).isNull();
        assertThat(step.unavailableReason()).contains("no longer available");
    }

    @Test
    void detectsACycleWithoutInfiniteRecursion() throws Exception {
        String flowId = createFlow();
        String versionId = createDraftVersion(flowId);
        FlowVersion flowVersion = flowVersionRepository.findByIdAndFlow_Id(UUID.fromString(versionId), UUID.fromString(flowId)).orElseThrow();

        // A step referencing its OWN FlowVersion as a SUB_FLOW cannot be
        // created through create_flow_step (it requires an already-ADOPTED
        // FlowVersion, which this one isn't yet) - inserted directly to
        // simulate the kind of data anomaly FlowStepReferenceValidator's own
        // javadoc says it does not itself prevent.
        flowStepRepository.save(FlowStep.create(
                flowVersion, "self-reference", FlowStepComponentType.SUB_FLOW, 10,
                UUID.fromString(flowId), UUID.fromString(versionId), null));
        flowStepRepository.flush();

        FlowFullSourceResponse fullSource = flowFullSourceService.getFullSource(adminId, UUID.fromString(flowId), UUID.fromString(versionId));

        assertThat(fullSource.steps()).hasSize(1);
        FlowStepSourceResponse step = fullSource.steps().get(0);
        assertThat(step.subFlow()).isNull();
        assertThat(step.unavailableReason()).contains("Cycle detected");
    }

    private void deleteSourceContentFromDisk(String functionVersionId) throws Exception {
        Path versionRoot = Path.of(sourceStorageProperties.storageRoot()).resolve(functionVersionId);
        try (var paths = Files.walk(versionRoot)) {
            List<Path> toDelete = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : toDelete) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void adopt(String flowId, String versionId) throws Exception {
        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/adopt", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ADOPTED"));
    }

    private void createStep(
            String flowId, String versionId, String stepKey, String componentType, int position, ReadyFunctionVersion function
    ) throws Exception {
        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/steps", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepPayload(stepKey, componentType, position, function.functionId(), function.functionVersionId())))
                .andExpect(status().isOk());
    }

    private String stepPayload(String stepKey, String componentType, int position, String componentId, String componentVersionId) {
        return """
                {
                  "stepKey": "%s",
                  "componentType": "%s",
                  "position": %d,
                  "componentId": "%s",
                  "componentVersionId": "%s"
                }
                """.formatted(stepKey, componentType, position, componentId, componentVersionId);
    }

    private String createFlow() throws Exception {
        String flowKey = "flw_full_source_" + UUID.randomUUID().toString().replace("-", "");
        MvcResult result = mockMvc.perform(post("/api/v1/flows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flowKey": "%s",
                                  "name": "Full Source Test Flow",
                                  "gatewayId": "%s",
                                  "httpMethod": "GET",
                                  "path": "/full-source-%s"
                                }
                                """.formatted(flowKey, gatewayId, flowKey)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createDraftVersion(String flowId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/flows/{flowId}/versions", flowId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runtime\": \"NODE\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private record ReadyFunctionVersion(String functionId, String functionVersionId) {
    }

    private ReadyFunctionVersion createReadyFunctionVersion(String indexMjsContent) throws Exception {
        String functionId = createFunction();
        String functionVersionId = createDraftFunctionVersion(functionId);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("index.mjs"));
            zip.write(indexMjsContent.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MockMultipartFile archive = new MockMultipartFile("file", "source.zip", "application/zip", buffer.toByteArray());

        mockMvc.perform(multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, functionVersionId)
                        .file(archive)
                        .param("entrypoint", "index.mjs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/deploy", functionId, functionVersionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));

        return new ReadyFunctionVersion(functionId, functionVersionId);
    }

    private String createFunction() throws Exception {
        String functionKey = "fn_full_source_" + UUID.randomUUID().toString().replace("-", "");
        MvcResult result = mockMvc.perform(post("/api/v1/functions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "functionKey": "%s",
                                  "name": "Full Source Test Function",
                                  "runtime": "NODE"
                                }
                                """.formatted(functionKey)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createDraftFunctionVersion(String functionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/functions/{functionId}/versions", functionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String obtainToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @TestConfiguration
    static class SynchronousBuildExecutorConfig {
        // deploy() hands its build/publish pipeline to a FunctionBuildExecutor
        // and returns immediately (see FunctionVersionDeploymentService); a
        // same-thread executor keeps createReadyFunctionVersion's
        // deploy-then-assert call deterministic instead of racing the real
        // bounded background pool.
        @Bean
        @Primary
        FunctionBuildExecutor synchronousFunctionBuildExecutor() {
            return Runnable::run;
        }
    }
}
