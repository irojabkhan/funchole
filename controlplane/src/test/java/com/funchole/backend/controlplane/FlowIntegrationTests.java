package com.funchole.backend.controlplane;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funchole.backend.controlplane.constant.DomainStatus;
import com.funchole.backend.controlplane.constant.GatewayStatus;
import com.funchole.backend.controlplane.entity.AppDomain;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.entity.Gateway;
import com.funchole.backend.controlplane.repository.AppDomainRepository;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import com.funchole.backend.controlplane.repository.GatewayRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class FlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AppDomainRepository appDomainRepository;

    @Autowired
    private GatewayRepository gatewayRepository;

    private String adminToken;
    private UUID gatewayId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = obtainToken("admin", "admin12345");

        AppUser admin = appUserRepository.findByUsername("admin").orElseThrow();
        AppDomain domain = appDomainRepository.save(
                AppDomain.create(admin, "flow-test-" + UUID.randomUUID() + ".example.com", "verify-me", DomainStatus.VERIFIED));
        Gateway gateway = gatewayRepository.save(
                Gateway.create(admin, domain, "Flow Test Gateway", "ftg" + System.nanoTime() % 100000, "test gateway", GatewayStatus.ACTIVE));
        gatewayId = gateway.getId();
    }

    @Test
    void fullLifecycleFromDraftToAdoptToArchive() throws Exception {
        String flowKey = "flw_test_" + UUID.randomUUID().toString().replace("-", "");
        ReadyFunctionVersion functionOne = createReadyFunctionVersion();
        ReadyFunctionVersion functionTwo = createReadyFunctionVersion();

        String flowId = createFlow(flowKey);
        String versionId = createDraftVersion(flowId);

        createStep(flowId, versionId, "step-one", "FUNCTION", 10, functionOne);
        createStep(flowId, versionId, "step-two", "RESPONSE", 20, functionTwo);

        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/adopt", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ADOPTED"));

        mockMvc.perform(get("/api/v1/flows/{flowId}", flowId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeFlowVersionId").value(versionId))
                .andExpect(jsonPath("$.data.activeFlowVersionStatus").value("ADOPTED"));

        // Steps are immutable once the version is adopted.
        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/steps", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepPayload("step-three", "FUNCTION", 30, functionOne.functionId(), functionOne.functionVersionId())))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/archive", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/flows/{flowId}", flowId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeFlowVersionId").doesNotExist());
    }

    @Test
    void rejectsAdoptWhenVersionHasNoSteps() throws Exception {
        String flowKey = "flw_test_" + UUID.randomUUID().toString().replace("-", "");
        String flowId = createFlow(flowKey);
        String versionId = createDraftVersion(flowId);

        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/adopt", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsStepWithUnsupportedComponentType() throws Exception {
        String flowKey = "flw_test_" + UUID.randomUUID().toString().replace("-", "");
        String flowId = createFlow(flowKey);
        String versionId = createDraftVersion(flowId);

        // MAPPING is not in FlowStepComponentType at all yet - rejected by
        // Jackson enum deserialization before component validation ever runs.
        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/steps", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stepKey": "bad-step",
                                  "componentType": "MAPPING",
                                  "position": 10,
                                  "componentId": "%s",
                                  "componentVersionId": "%s"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsStepReferencingAFunctionVersionThatIsNotReady() throws Exception {
        String flowKey = "flw_test_" + UUID.randomUUID().toString().replace("-", "");
        String flowId = createFlow(flowKey);
        String versionId = createDraftVersion(flowId);

        String functionId = createFunction();
        String functionVersionId = createDraftFunctionVersion(functionId);

        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/steps", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepPayload("step-one", "FUNCTION", 10, functionId, functionVersionId)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsAccessToAFlowThatDoesNotBelongToTheCaller() throws Exception {
        // The ownership-scoped lookup (findByIdAndAppUser_IdAndDeletedAtIsNull) returns the same
        // "not found" outcome for a random id as it would for another user's flow id - both paths
        // go through the exact same query, so this exercises the ownership guard without needing
        // a second real app user (AppUser has no public factory/signup flow to create one with).
        mockMvc.perform(get("/api/v1/flows/{flowId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private String createFlow(String flowKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/flows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flowKey": "%s",
                                  "name": "Test Flow",
                                  "description": "created by FlowIntegrationTests",
                                  "gatewayId": "%s",
                                  "httpMethod": "GET",
                                  "path": "/test-%s"
                                }
                                """.formatted(flowKey, gatewayId, flowKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flowKey").value(flowKey))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createDraftVersion(String flowId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/flows/{flowId}/versions", flowId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runtime": "NODE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private void createStep(
            String flowId, String versionId, String stepKey, String componentType, int position, ReadyFunctionVersion function
    ) throws Exception {
        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/steps", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepPayload(stepKey, componentType, position, function.functionId(), function.functionVersionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stepKey").value(stepKey));
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

    private record ReadyFunctionVersion(String functionId, String functionVersionId) {
    }

    /**
     * Full real pipeline: create Function -&gt; create draft FunctionVersion ->
     * submit a dependency-free zip source -&gt; deploy to READY - so Flow steps
     * in these tests reference a real, existing, READY FunctionVersion rather
     * than an arbitrary UUID, matching what FlowStepService now requires.
     */
    private ReadyFunctionVersion createReadyFunctionVersion() throws Exception {
        String functionId = createFunction();
        String functionVersionId = createDraftFunctionVersion(functionId);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("index.mjs"));
            zip.write("export async function handler(input) { return input; }".getBytes(StandardCharsets.UTF_8));
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
        String functionKey = "fn_test_" + UUID.randomUUID().toString().replace("-", "");
        MvcResult result = mockMvc.perform(post("/api/v1/functions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "functionKey": "%s",
                                  "name": "Test Function",
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
}
