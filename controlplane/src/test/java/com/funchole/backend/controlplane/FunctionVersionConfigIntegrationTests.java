package com.funchole.backend.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funchole.backend.controlplane.service.FunctionSecretStore;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FunctionVersionConfigIntegrationTests {

    private static final RecordingFunctionSecretStore SECRET_STORE = new RecordingFunctionSecretStore();

    @Autowired
    private MockMvc mockMvc;

    private String adminToken;
    private String functionId;
    private String versionId;

    @BeforeEach
    void setUp() throws Exception {
        SECRET_STORE.clear();
        adminToken = obtainToken("admin", "admin12345");
        functionId = createFunction("fn_config_" + UUID.randomUUID().toString().replace("-", ""));
        versionId = createDraftVersion();
    }

    @Test
    void returnsEmptyConfigForNewFunctionVersion() throws Exception {
        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/config", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.functionVersionId").value(versionId))
                .andExpect(jsonPath("$.data.envVars").isEmpty())
                .andExpect(jsonPath("$.data.secrets").isEmpty());
    }

    @Test
    void storesPlainEnvVarOnExactFunctionVersion() throws Exception {
        mockMvc.perform(put("/api/v1/functions/{functionId}/versions/{versionId}/config/env/NODE_ENV", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "production"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.envVars[0].key").value("NODE_ENV"))
                .andExpect(jsonPath("$.data.envVars[0].value").value("production"));

        mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/config", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.envVars[0].key").value("NODE_ENV"))
                .andExpect(jsonPath("$.data.envVars[0].value").value("production"));
    }

    @Test
    void storesSecretValueInSecretStoreAndReturnsOnlySecretReference() throws Exception {
        mockMvc.perform(put("/api/v1/functions/{functionId}/versions/{versionId}/config/secrets/API_TOKEN", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "super-secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secrets[0].key").value("API_TOKEN"))
                .andExpect(jsonPath("$.data.secrets[0].secretRef").value("function-versions/" + versionId + "/secrets/API_TOKEN"))
                .andExpect(jsonPath("$.data.secrets[0].value").doesNotExist());

        assertThat(SECRET_STORE.valueFor(UUID.fromString(versionId), "API_TOKEN")).isEqualTo("super-secret");
    }

    @Test
    void upsertingSameKeyUpdatesSameConfigEntry() throws Exception {
        upsertEnv("NODE_ENV", "development");

        mockMvc.perform(put("/api/v1/functions/{functionId}/versions/{versionId}/config/env/NODE_ENV", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "production"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.envVars.length()").value(1))
                .andExpect(jsonPath("$.data.envVars[0].value").value("production"));
    }

    @Test
    void rejectsInvalidConfigKey() throws Exception {
        mockMvc.perform(put("/api/v1/functions/{functionId}/versions/{versionId}/config/env/1BAD", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "production"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsUsingSameKeyAsPlainEnvVarAndSecret() throws Exception {
        upsertEnv("API_TOKEN", "plain-token");

        mockMvc.perform(put("/api/v1/functions/{functionId}/versions/{versionId}/config/secrets/API_TOKEN", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "secret-token"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    private void upsertEnv(String key, String value) throws Exception {
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
                                  "name": "Config Test Function",
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
    static class SecretStoreTestConfig {

        @Bean
        @Primary
        FunctionSecretStore functionSecretStore() {
            return SECRET_STORE;
        }
    }

    private static final class RecordingFunctionSecretStore implements FunctionSecretStore {
        private final Map<String, String> values = new ConcurrentHashMap<>();

        @Override
        public String save(UUID functionVersionId, String key, String value) {
            values.put(cacheKey(functionVersionId, key), value);
            return "function-versions/" + functionVersionId + "/secrets/" + key;
        }

        @Override
        public String saveForEnvironment(UUID environmentProfileId, String key, String value) {
            values.put(cacheKey(environmentProfileId, key), value);
            return "environments/" + environmentProfileId + "/secrets/" + key;
        }

        @Override
        public String saveForDatabase(UUID databaseId, String key, String value) {
            values.put(cacheKey(databaseId, key), value);
            return "databases/" + databaseId + "/" + key;
        }

        String valueFor(UUID functionVersionId, String key) {
            return values.get(cacheKey(functionVersionId, key));
        }

        void clear() {
            values.clear();
        }

        private String cacheKey(UUID functionVersionId, String key) {
            return functionVersionId + ":" + key;
        }
    }
}
