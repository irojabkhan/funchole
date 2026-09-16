package com.funchole.backend.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funchole.backend.controlplane.service.FunctionVersionLifecycleRegistry;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FunctionVersionSourceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FunctionVersionLifecycleRegistry functionVersionLifecycleRegistry;

    private String adminToken;
    private String functionId;
    private String versionId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = obtainToken("admin", "admin12345");
        functionId = createFunction("fn_test_" + UUID.randomUUID().toString().replace("-", ""));
        versionId = createDraftVersion();
    }

    @Test
    void submitsAndReadsBackAZipArchive() throws Exception {
        MockMultipartFile archive = zipOf("index.mjs", "export async function handler(input) { return input; }",
                "package.json", "{\"name\":\"fn\"}");

        mockMvc.perform(multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .file(archive)
                        .param("entrypoint", "index.mjs")
                        .param("runtimeVersion", "20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.functionVersionId").value(versionId))
                .andExpect(jsonPath("$.data.runtimeType").value("NODE"))
                .andExpect(jsonPath("$.data.runtimeVersion").value("20"))
                .andExpect(jsonPath("$.data.entrypoint").value("index.mjs"))
                .andExpect(jsonPath("$.data.handler").value("handler"))
                .andExpect(jsonPath("$.data.relativePaths", org.hamcrest.Matchers.containsInAnyOrder("index.mjs", "package.json")));

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entrypoint").value("index.mjs"))
                .andExpect(jsonPath("$.data.handler").value("handler"));
    }

    @Test
    void submitsSourceWithACustomExportedHandlerFunctionName() throws Exception {
        MockMultipartFile archive = zipOf("index.mjs", "export async function GrowUp(input) { return input; }");

        mockMvc.perform(multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .file(archive)
                        .param("entrypoint", "index.mjs")
                        .param("handler", "GrowUp")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handler").value("GrowUp"));
    }

    @Test
    void submitsIndividualFilesWithoutAnArchivePreservingFolderStructure() throws Exception {
        MockMultipartFile indexFile = new MockMultipartFile("files", "src/index.mjs", "text/plain",
                "export async function handler(input) { return input; }".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile packageFile = new MockMultipartFile("files", "package.json", "application/json",
                "{\"name\":\"fn\"}".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .file(indexFile)
                        .file(packageFile)
                        .param("entrypoint", "src/index.mjs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entrypoint").value("src/index.mjs"))
                .andExpect(jsonPath("$.data.relativePaths",
                        org.hamcrest.Matchers.containsInAnyOrder("src/index.mjs", "package.json")));
    }

    @Test
    void rejectsSubmittingBothAnArchiveAndIndividualFilesTogether() throws Exception {
        MockMultipartFile archive = zipOf("index.mjs", "content");
        MockMultipartFile individual = new MockMultipartFile("files", "index.mjs", "text/plain",
                "content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .file(archive)
                        .file(individual)
                        .param("entrypoint", "index.mjs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsSubmittingNeitherAnArchiveNorIndividualFiles() throws Exception {
        mockMvc.perform(multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .param("entrypoint", "index.mjs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void replacesSourceOnResubmission() throws Exception {
        submitArchive(zipOf("index.mjs", "v1"), "index.mjs");
        submitArchive(zipOf("main.mjs", "v2"), "main.mjs");

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entrypoint").value("main.mjs"))
                .andExpect(jsonPath("$.data.relativePaths").value(org.hamcrest.Matchers.contains("main.mjs")));
    }

    @Test
    void rejectsAnEntrypointNotPresentInTheArchive() throws Exception {
        MockMultipartFile archive = zipOf("index.mjs", "content");

        mockMvc.perform(multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .file(archive)
                        .param("entrypoint", "missing.mjs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsAnArchiveEntryThatTraversesOutsideTheSourceRoot() throws Exception {
        MockMultipartFile archive = zipOf("../escape.mjs", "content");

        mockMvc.perform(multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .file(archive)
                        .param("entrypoint", "../escape.mjs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsSubmittingSourceOnceTheVersionIsNoLongerDraft() throws Exception {
        functionVersionLifecycleRegistry.beginPublishing(UUID.fromString(versionId));

        MockMultipartFile archive = zipOf("index.mjs", "content");
        mockMvc.perform(multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .file(archive)
                        .param("entrypoint", "index.mjs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsAccessToASourceEndpointForAFunctionThatDoesNotBelongToTheCaller() throws Exception {
        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/source", UUID.randomUUID(), versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void readsBackFullSourceIncludingFileContent() throws Exception {
        submitArchive(zipOf("index.mjs", "export async function handler(input) { return input; }",
                "package.json", "{\"name\":\"fn\"}"), "index.mjs");

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/source/files", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entrypoint").value("index.mjs"))
                .andExpect(jsonPath("$.data.handler").value("handler"))
                .andExpect(jsonPath("$.data.files[?(@.path == 'index.mjs')].content")
                        .value(org.hamcrest.Matchers.contains("export async function handler(input) { return input; }")))
                .andExpect(jsonPath("$.data.files[?(@.path == 'package.json')].content")
                        .value(org.hamcrest.Matchers.contains("{\"name\":\"fn\"}")));
    }

    @Test
    void rejectsReadingFullSourceForAVersionWithNoSourceSubmitted() throws Exception {
        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/source/files", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private void submitArchive(MockMultipartFile archive, String entrypoint) throws Exception {
        mockMvc.perform(multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                        .file(archive)
                        .param("entrypoint", entrypoint)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private MockMultipartFile zipOf(String... pathAndContentPairs) throws Exception {
        assertThat(pathAndContentPairs.length % 2).isEqualTo(0);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (int i = 0; i < pathAndContentPairs.length; i += 2) {
                zip.putNextEntry(new ZipEntry(pathAndContentPairs[i]));
                zip.write(pathAndContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "source.zip", "application/zip", buffer.toByteArray());
    }

    private String createDraftVersion() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/functions/{functionId}/versions", functionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createFunction(String functionKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/functions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
