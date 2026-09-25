package com.funchole.backend.controlplane;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funchole.backend.controlplane.functionbuild.FunctionBuildExecutor;
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
 * Drives the full source-to-READY pipeline entirely through the public
 * Controlplane API (create Function -&gt; create draft FunctionVersion ->
 * submit source -&gt; deploy), against the real, already-running dev stack's
 * Postgres and RustFS (S3-compatible) - no fakes, no seeded data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FunctionVersionDeploymentIntegrationTests {

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
    void deploysADependencyFreeFunctionVersionToReady() throws Exception {
        String versionId = createDraftVersion();
        submitZipSource(versionId, "index.mjs", "export async function handler(input) { return input; }");

        mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/deploy", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.artifactObjectKey").value("artifacts/" + versionId + "/artifact.tar.gz"))
                .andExpect(jsonPath("$.data.artifactFormat").value("TAR_GZ"))
                .andExpect(jsonPath("$.data.artifactSha256").isNotEmpty())
                .andExpect(jsonPath("$.data.artifactSizeBytes").isNumber());
    }

    @Test
    void rejectsRedeployingAnAlreadyReadyVersion() throws Exception {
        String versionId = createDraftVersion();
        submitZipSource(versionId, "index.mjs", "export async function handler(input) { return input; }");

        mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/deploy", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));

        mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/deploy", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsDeployingAVersionWithNoSourceSubmitted() throws Exception {
        String versionId = createDraftVersion();

        // No source was submitted, so the (now-async) build pipeline fails
        // once it starts - deploy() itself no longer surfaces that failure
        // synchronously (see FunctionVersionDeploymentService), so this
        // still returns 200; the resulting FAILED status is verified below.
        mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/deploy", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    @Test
    void rejectsDeployingAVersionUnderAFunctionThatDoesNotBelongToTheCaller() throws Exception {
        mockMvc.perform(post("/api/v1/functions/{functionId}/versions/{versionId}/deploy", UUID.randomUUID(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
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
        // same-thread executor keeps this suite's straight-line
        // deploy-then-assert calls deterministic instead of racing the real
        // bounded background pool.
        @Bean
        @Primary
        FunctionBuildExecutor synchronousFunctionBuildExecutor() {
            return Runnable::run;
        }
    }
}
