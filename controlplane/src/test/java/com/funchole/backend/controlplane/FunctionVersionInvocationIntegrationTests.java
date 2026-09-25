package com.funchole.backend.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funchole.backend.controlplane.functionbuild.FunctionBuildExecutor;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

/**
 * Drives direct FunctionVersion invocation (no Flow/Gateway) entirely
 * through the public Controlplane API - closes F117, the REST transport for
 * the already-tested (F116) FunctionVersionInvocationService.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FunctionVersionInvocationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    private String adminToken;
    private String functionId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = obtainToken("admin", "admin12345");
        functionId = createFunction("fn_test_" + UUID.randomUUID().toString().replace("-", ""));
    }

    @Test
    void invokesAReadyFunctionVersionWithTheGivenInputPayload() throws Exception {
        String versionId = createReadyVersion("export async function handler(input) { return { echoed: input }; }");

        mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/invoke", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/orders\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invocationId").isNotEmpty())
                .andExpect(jsonPath("$.data.functionVersionId").value(versionId))
                .andExpect(jsonPath("$.data.initialStatus").value("PENDING"));
    }

    @Test
    void defaultsToAnEmptyObjectPayloadWhenNoBodyIsSent() throws Exception {
        String versionId = createReadyVersion("export async function handler(input) { return input; }");

        mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/invoke", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invocationId").isNotEmpty());
    }

    @Test
    void persistsAndSurfacesRuntimeConsoleOutputThroughInspection() throws Exception {
        String versionId = createReadyVersion("""
                export async function handler(input) {
                  console.log("hello from the function");
                  console.error("a warning");
                  return { echoed: input };
                }
                """);

        MvcResult invokeResult = mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/invoke", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String invocationId = JsonPath.read(invokeResult.getResponse().getContentAsString(), "$.data.invocationId");

        Map<String, Object> inspection = pollUntilTerminal(invocationId);

        assertThat(inspection.get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> steps = (List<Map<String, Object>>) inspection.get("steps");
        assertThat(steps).hasSize(1);
        List<Map<String, Object>> logs = (List<Map<String, Object>>) steps.get(0).get("logs");
        assertThat(logs)
                .extracting(log -> log.get("stream") + ":" + log.get("message"))
                .contains("stdout:hello from the function", "stderr:a warning");
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

    @Test
    void rejectsInvokingADraftFunctionVersion() throws Exception {
        String versionId = createDraftVersion();

        mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/invoke", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvokingAVersionUnderAFunctionThatDoesNotBelongToTheCaller() throws Exception {
        mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/invoke", UUID.randomUUID(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private String createReadyVersion(String source) throws Exception {
        String versionId = createDraftVersion();
        submitZipSource(versionId, "index.mjs", source);
        mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/deploy", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));
        return versionId;
    }

    private void submitZipSource(String versionId, String entrypoint, String content) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry(entrypoint));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MockMultipartFile archive = new MockMultipartFile("file", "source.zip", "application/zip", buffer.toByteArray());

        mockMvc.perform(multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .file(archive)
                        .param("entrypoint", entrypoint)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private String createDraftVersion() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/functions/{functionId}/versions", functionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createFunction(String functionKey) throws Exception {
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
        // same-thread executor keeps createReadyVersion's deploy-then-assert
        // call deterministic instead of racing the real bounded background pool.
        @Bean
        @Primary
        FunctionBuildExecutor synchronousFunctionBuildExecutor() {
            return Runnable::run;
        }
    }
}
