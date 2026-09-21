package com.funchole.backend.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.funchole.backend.controlplane.mcp.FunctionExampleFixtures;
import com.funchole.backend.controlplane.repository.AppDomainRepository;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import com.funchole.backend.controlplane.repository.GatewayRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
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

/**
 * Drives direct FlowVersion invocation (no Gateway, HTTP route, or TLS)
 * entirely through the public Controlplane API - the "Test flow" capability
 * exposed on the Flow builder's version page, reusing the exact same
 * Invocation/Dispatcher/Runtime execution path a real Gateway-routed HTTP
 * request to this Flow already goes through.
 *
 * <p>Deliberately NOT {@code @Transactional}: {@link com.funchole.backend.invocation.JdbcInvocationRegistry}
 * reads Flow/FlowVersion/FlowStep rows through its own raw JDBC connection,
 * outside Spring's test-managed transaction - the same reason
 * {@code ZeroToHttpResponseE2ETest} avoids it too. Under {@code @Transactional},
 * those rows would still be uncommitted (and thus invisible to that separate
 * connection) at invoke time. Test data is cleaned up explicitly instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlowVersionInvocationIntegrationTests {

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
    private UUID domainId;
    private final List<String> createdFlowIds = new ArrayList<>();
    private final List<String> createdFunctionIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        adminToken = obtainToken("admin", "admin12345");

        AppUser admin = appUserRepository.findByUsername("admin").orElseThrow();
        AppDomain domain = appDomainRepository.save(
                AppDomain.create(admin, "flow-invoke-test-" + UUID.randomUUID() + ".example.com", "verify-me", DomainStatus.VERIFIED));
        domainId = domain.getId();
        Gateway gateway = gatewayRepository.save(
                Gateway.create(admin, domain, "Flow Invoke Test Gateway", "fitg" + System.nanoTime() % 100000, "test gateway", GatewayStatus.ACTIVE));
        gatewayId = gateway.getId();
    }

    @AfterEach
    void tearDown() {
        createdFlowIds.forEach(flowId -> deleteQuietly("/api/v1/flows/" + flowId));
        createdFunctionIds.forEach(functionId -> deleteQuietly("/api/v1/functions/" + functionId));
        gatewayRepository.deleteById(gatewayId);
        appDomainRepository.deleteById(domainId);
    }

    private void deleteQuietly(String path) {
        try {
            mockMvc.perform(delete(path).header("Authorization", "Bearer " + adminToken));
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
    }

    @Test
    void invokesADraftFlowVersionWithoutRequiringAdoption() throws Exception {
        ReadyFunctionVersion checker = createReadyFunctionVersion("export async function handler(input) { return input; }");
        // Shared with get_function_example's NODE_BASIC scenario (FunctionExampleFixtures) -
        // this assertion is that scenario's source of truth; if this source changes, this test
        // fails, so the MCP tool's returned example can never silently drift from real behavior.
        ReadyFunctionVersion responder = createReadyFunctionVersion(FunctionExampleFixtures.NODE_BASIC_SOURCE);
        String flowId = createFlow();
        String versionId = createDraftVersion(flowId);
        createStep(flowId, versionId, "check", "FUNCTION", 10, checker);
        createStep(flowId, versionId, "respond", "RESPONSE", 20, responder);

        MvcResult invokeResult = mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/invoke", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invocationId").isNotEmpty())
                .andExpect(jsonPath("$.data.flowVersionId").value(versionId))
                .andExpect(jsonPath("$.data.initialStatus").value("PENDING"))
                .andReturn();
        String invocationId = JsonPath.read(invokeResult.getResponse().getContentAsString(), "$.data.invocationId");

        Map<String, Object> inspection = pollUntilTerminal(invocationId);

        assertThat(inspection.get("status")).isEqualTo("COMPLETED");
        String resultJson = (String) inspection.get("result");
        assertThat((Integer) JsonPath.read(resultJson, "$.status")).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(resultJson, "$.body.ok")).isTrue();
        assertThat((Integer) JsonPath.read(resultJson, "$.body.input.orderId")).isEqualTo(42);
    }

    @Test
    void invokesAnAdoptedFlowVersion() throws Exception {
        ReadyFunctionVersion responder = createReadyFunctionVersion(
                "export async function handler(input) { return { status: 200, body: { echoed: input } }; }");
        String flowId = createFlow();
        String versionId = createDraftVersion(flowId);
        createStep(flowId, versionId, "respond", "RESPONSE", 10, responder);

        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/adopt", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/invoke", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invocationId").isNotEmpty());
    }

    @Test
    void rejectsInvokingAnArchivedFlowVersion() throws Exception {
        ReadyFunctionVersion responder = createReadyFunctionVersion(
                "export async function handler(input) { return { status: 200, body: input }; }");
        String flowId = createFlow();
        String versionId = createDraftVersion(flowId);
        createStep(flowId, versionId, "respond", "RESPONSE", 10, responder);
        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/adopt", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/archive", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/invoke", flowId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvokingAVersionUnderAFlowThatDoesNotBelongToTheCaller() throws Exception {
        mockMvc.perform(post("/api/v1/flows/{flowId}/versions/{versionId}/invoke", UUID.randomUUID(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private Map<String, Object> pollUntilTerminal(String invocationId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            MvcResult result = mockMvc.perform(get("/api/v1/invocations/{invocationId}", invocationId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();
            Map<String, Object> data = JsonPath.read(result.getResponse().getContentAsString(), "$.data");
            if (!"PENDING".equals(data.get("status"))) {
                return data;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Invocation " + invocationId + " did not reach a terminal status within the deadline");
    }

    private String createFlow() throws Exception {
        String flowKey = "flw_invoke_test_" + UUID.randomUUID().toString().replace("-", "");
        MvcResult result = mockMvc.perform(post("/api/v1/flows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flowKey": "%s",
                                  "name": "Test Flow",
                                  "description": "created by FlowVersionInvocationIntegrationTests",
                                  "gatewayId": "%s",
                                  "httpMethod": "GET",
                                  "path": "/test-%s"
                                }
                                """.formatted(flowKey, gatewayId, flowKey)))
                .andExpect(status().isOk())
                .andReturn();
        String flowId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        createdFlowIds.add(flowId);
        return flowId;
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

    private record ReadyFunctionVersion(String functionId, String functionVersionId) {
    }

    private ReadyFunctionVersion createReadyFunctionVersion(String source) throws Exception {
        String functionId = createFunction();
        String functionVersionId = createDraftFunctionVersion(functionId);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("index.mjs"));
            zip.write(source.getBytes(StandardCharsets.UTF_8));
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
        String functionKey = "fn_invoke_test_" + UUID.randomUUID().toString().replace("-", "");
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
        String functionId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        createdFunctionIds.add(functionId);
        return functionId;
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
