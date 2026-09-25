package com.funchole.backend.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funchole.backend.controlplane.functionbuild.FunctionBuildExecutor;
import com.funchole.backend.controlplane.functionbuild.process.ProcessExecutor;
import com.funchole.backend.controlplane.functionbuild.process.ProcessResult;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Proves F248/F250 (persisted/queryable build logs) end to end through the
 * real product boundary (REST - {@code get_function_version_build_logs}
 * exposes the same data over MCP) - not just that
 * {@code FunctionVersionBuildLogService} saves a row in isolation. The build
 * command itself is faked (see {@link FakeProcessExecutorConfig}, the same
 * "substitute a fake instead of spawning a real process" approach
 * {@code NodeRuntimeBuilderTests}/{@code StaticRuntimeBuilderTests} already
 * use), so this stays in the fast default suite with no real {@code npm} on
 * PATH required - only {@code DefaultProcessExecutor}'s own OS-process-spawning
 * code is out of scope here, everything above it (build orchestration,
 * persistence, REST read-back, ownership) is real. The failure case
 * reproduces the exact category of bug found live in
 * MCP_TESTING_FEEDBACK.md item 10: a real build failure whose stdout/stderr
 * would otherwise only ever have existed in one transient response body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FunctionVersionBuildLogIntegrationTests {

    private static final FakeProcessExecutor FAKE_PROCESS_EXECUTOR = new FakeProcessExecutor();

    @Autowired
    private MockMvc mockMvc;

    private String adminToken;

    @BeforeEach
    void resetFakeProcessExecutor() throws Exception {
        FAKE_PROCESS_EXECUTOR.reset();
        adminToken = obtainToken();
    }

    @Test
    void deployingWithoutPackageJsonRecordsNoBuildLogs() throws Exception {
        String functionId = createFunction("NODE");
        String versionId = createDraftVersion(functionId);
        submitSource(functionId, versionId, "index.mjs",
                "export async function handler(input) { return input; }", null);

        deploy(functionId, versionId).andExpect(status().isOk());

        assertThat(readBuildLogs(functionId, versionId)).isEmpty();
    }

    @Test
    void successfulDependencyInstallIsPersistedAndReadableAfterTheFact() throws Exception {
        String functionId = createFunction("NODE");
        String versionId = createDraftVersion(functionId);
        submitSource(functionId, versionId, "index.mjs",
                "export async function handler(input) { return input; }", "{}");
        FAKE_PROCESS_EXECUTOR.nextResult(new ProcessResult(0, "up to date", "", false));

        deploy(functionId, versionId).andExpect(status().isOk());

        List<Map<String, Object>> logs = readBuildLogs(functionId, versionId);
        assertThat(logs).hasSize(1);
        Map<String, Object> entry = logs.get(0);
        assertThat(entry.get("stage")).isEqualTo("dependency-install");
        assertThat(entry.get("command")).isEqualTo("npm install");
        assertThat(entry.get("succeeded")).isEqualTo(true);
        assertThat(entry.get("timedOut")).isEqualTo(false);
        assertThat(entry.get("stdout")).isEqualTo("up to date");
        assertThat(((Number) entry.get("exitCode")).intValue()).isZero();
    }

    @Test
    void failedDependencyInstallIsPersistedWithFullStderrEvenThoughTheBuildFails() throws Exception {
        String functionId = createFunction("NODE");
        String versionId = createDraftVersion(functionId);
        submitSource(functionId, versionId, "index.mjs",
                "export async function handler(input) { return input; }", "{}");
        // The real, previously-invisible failure shape this feature exists for:
        // "npm" not found in the build environment (the exact live bug -
        // MCP_TESTING_FEEDBACK.md item 10 - before its own separate fix).
        FAKE_PROCESS_EXECUTOR.nextResult(new ProcessResult(1, "", "npm: command not found", false));

        // deploy() itself no longer surfaces a build failure synchronously -
        // it only starts the (now-async) pipeline, see
        // FunctionVersionDeploymentService - so this returns 200 even though
        // the build fails moments later; the failure detail lives in the
        // build logs and the version's own FAILED status, both checked below.
        deploy(functionId, versionId).andExpect(status().isOk());

        List<Map<String, Object>> logs = readBuildLogs(functionId, versionId);
        assertThat(logs).hasSize(1);
        Map<String, Object> entry = logs.get(0);
        assertThat(entry.get("stage")).isEqualTo("dependency-install");
        assertThat(entry.get("succeeded")).isEqualTo(false);
        assertThat(entry.get("stderr")).isEqualTo("npm: command not found");

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    @Test
    void buildLogsRequireOwnership() throws Exception {
        String functionId = createFunction("NODE");
        String versionId = createDraftVersion(functionId);

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/build-logs", UUID.randomUUID(), versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private ResultActions deploy(String functionId, String versionId) throws Exception {
        return mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/deploy", functionId, versionId)
                .header("Authorization", "Bearer " + adminToken));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readBuildLogs(String functionId, String versionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/build-logs", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data");
    }

    private void submitSource(String functionId, String versionId, String entrypoint, String entrypointContent, String packageJson) throws Exception {
        var request = multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                .file(new MockMultipartFile("files", entrypoint, "text/javascript", entrypointContent.getBytes(StandardCharsets.UTF_8)))
                .param("entrypoint", entrypoint)
                .header("Authorization", "Bearer " + adminToken);
        if (packageJson != null) {
            request = request.file(new MockMultipartFile("files", "package.json", "application/json", packageJson.getBytes(StandardCharsets.UTF_8)));
        }
        mockMvc.perform(request).andExpect(status().isOk());
    }

    private String createFunction(String runtime) throws Exception {
        String functionKey = "fn_buildlog_test_" + UUID.randomUUID().toString().replace("-", "");
        MvcResult result = mockMvc.perform(post("/api/v1/functions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "functionKey": "%s",
                                  "name": "Build Log Test Function",
                                  "runtime": "%s"
                                }
                                """.formatted(functionKey, runtime)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createDraftVersion(String functionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/functions/{functionId}/versions", functionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String obtainToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "admin12345"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @TestConfiguration
    static class FakeProcessExecutorConfig {
        @Bean
        @Primary
        ProcessExecutor processExecutor() {
            return FAKE_PROCESS_EXECUTOR;
        }

        // deploy() hands its build/publish pipeline to a FunctionBuildExecutor
        // and returns immediately (see FunctionVersionDeploymentService); a
        // same-thread executor keeps this suite's straight-line
        // deploy-then-assert calls deterministic instead of racing the real
        // bounded background pool.
        @Bean
        @Primary
        FunctionBuildExecutor synchronousFunctionBuildExecutor() {
            return Runnable::run;
        }
    }

    private static final class FakeProcessExecutor implements ProcessExecutor {
        private ProcessResult nextResult = new ProcessResult(0, "", "", false);

        void nextResult(ProcessResult result) {
            this.nextResult = result;
        }

        void reset() {
            this.nextResult = new ProcessResult(0, "", "", false);
        }

        @Override
        public ProcessResult execute(List<String> command, Path workingDirectory, Duration timeout) {
            return nextResult;
        }
    }
}
