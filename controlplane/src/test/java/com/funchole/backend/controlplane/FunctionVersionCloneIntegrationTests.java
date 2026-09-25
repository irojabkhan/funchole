package com.funchole.backend.controlplane;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funchole.backend.controlplane.service.FunctionVersionLifecycleRegistry;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

/**
 * Proves that creating a new FunctionVersion clones the Function's most
 * recent version's source/env vars/database attachments by default - the fix
 * for a coding agent regenerating everything from scratch on every retry
 * after a FAILED build (see FunctionVersionCloneService). Secret cloning is
 * covered separately in FunctionVersionConfigIntegrationTests, which already
 * carries the fake FunctionSecretStore this needs - reusing it there avoids
 * this class standing up its own extra Spring context (and its own
 * connection pool against the shared local Postgres) just for that one
 * assertion; deliberately no nested @TestConfiguration here so this class's
 * context is identical to (and reused from) plain classes like
 * FunctionVersionIntegrationTests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FunctionVersionCloneIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FunctionVersionLifecycleRegistry lifecycleRegistry;

    private String adminToken;
    private String functionId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = obtainToken();
        functionId = createFunction("fn_clone_" + UUID.randomUUID().toString().replace("-", ""));
    }

    @Test
    void newVersionAutomaticallyClonesTheMostRecentVersionsSourceEnvVarAndDatabaseAttachment() throws Exception {
        String versionOne = createDraftVersion();
        submitZipSource(versionOne, "index.mjs", "export async function handler(input) { return input; }");
        upsertEnv(versionOne, "NODE_ENV", "production");
        String databaseId = createDatabase();
        attachDatabase(versionOne, databaseId);

        MvcResult createResult = mockMvc.perform(post("/api/v1/functions/{functionId}/versions", functionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andReturn();
        String versionTwo = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/source/files", functionId, versionTwo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entrypoint").value("index.mjs"))
                .andExpect(jsonPath("$.data.files[0].path").value("index.mjs"))
                .andExpect(jsonPath("$.data.files[0].content").value("export async function handler(input) { return input; }"));

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/config", functionId, versionTwo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.envVars[0].key").value("NODE_ENV"))
                .andExpect(jsonPath("$.data.envVars[0].value").value("production"));

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/databases", functionId, versionTwo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].databaseId").value(databaseId));
    }

    @Test
    void clonesFromTheMostRecentVersionEvenWhenItIsFailed() throws Exception {
        String versionOne = createDraftVersion();
        submitZipSource(versionOne, "index.mjs", "export async function handler(input) { return input; }");
        lifecycleRegistry.beginPublishing(UUID.fromString(versionOne));
        lifecycleRegistry.markFailed(UUID.fromString(versionOne));

        MvcResult createResult = mockMvc.perform(post("/api/v1/functions/{functionId}/versions", functionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        String versionTwo = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/source/files", functionId, versionTwo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files[0].path").value("index.mjs"));
    }

    @Test
    void cloneFromVersionIdClonesASpecificEarlierVersionInsteadOfTheLatest() throws Exception {
        String versionOne = createDraftVersion();
        submitZipSource(versionOne, "index.mjs", "export async function handler(input) { return 'v1'; }");
        String versionTwo = createDraftVersion();
        submitZipSource(versionTwo, "index.mjs", "export async function handler(input) { return 'v2'; }");

        MvcResult createResult = mockMvc.perform(post("/api/v1/functions/{functionId}/versions", functionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cloneFromVersionId": "%s"
                                }
                                """.formatted(versionOne)))
                .andExpect(status().isOk())
                .andReturn();
        String versionThree = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/source/files", functionId, versionThree)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files[0].content").value("export async function handler(input) { return 'v1'; }"));
    }

    @Test
    void rejectsCloneFromVersionIdBelongingToADifferentFunction() throws Exception {
        String otherFunctionId = createFunction("fn_clone_other_" + UUID.randomUUID().toString().replace("-", ""));
        MvcResult otherVersionResult = mockMvc.perform(post("/api/v1/functions/{functionId}/versions", otherFunctionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String otherVersionId = JsonPath.read(otherVersionResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/functions/{functionId}/versions", functionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cloneFromVersionId": "%s"
                                }
                                """.formatted(otherVersionId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void startEmptyOptsOutOfAutoClone() throws Exception {
        String versionOne = createDraftVersion();
        submitZipSource(versionOne, "index.mjs", "export async function handler(input) { return input; }");

        MvcResult createResult = mockMvc.perform(post("/api/v1/functions/{functionId}/versions", functionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startEmpty": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String versionTwo = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/source/files", functionId, versionTwo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private void upsertEnv(String versionId, String key, String value) throws Exception {
        mockMvc.perform(put("/api/v1/functions/{functionId}/versions/{versionId}/config/env/{key}", functionId, versionId, key)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "%s"
                                }
                                """.formatted(value)))
                .andExpect(status().isOk());
    }

    /**
     * Reuses an existing Database for {@code admin} if one is already
     * there rather than always creating a fresh one - the admin account has
     * a 1-database quota, and this test only needs SOME database to attach
     * and confirm the attachment gets cloned, not a specific one.
     */
    private String createDatabase() throws Exception {
        MvcResult listResult = mockMvc.perform(get("/api/v1/databases")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        List<String> existingIds = JsonPath.read(listResult.getResponse().getContentAsString(), "$.data.items[*].id");
        if (!existingIds.isEmpty()) {
            return existingIds.get(0);
        }

        MvcResult result = mockMvc.perform(post("/api/v1/databases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "primary",
                                  "type": "POSTGRES",
                                  "host": "db.example.com",
                                  "port": 5432,
                                  "databaseName": "postgres",
                                  "username": "postgres",
                                  "password": "changeme",
                                  "sslEnabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private void attachDatabase(String versionId, String databaseId) throws Exception {
        mockMvc.perform(put("/api/v1/functions/{functionId}/versions/{versionId}/databases/{databaseId}", functionId, versionId, databaseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
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
                        .content("""
                                {
                                  "startEmpty": true
                                }
                                """))
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
                                  "name": "Clone Test Function",
                                  "runtime": "NODE"
                                }
                                """.formatted(functionKey)))
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
}
