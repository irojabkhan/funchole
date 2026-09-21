package com.funchole.backend.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funchole.backend.controlplane.mcp.FunctionExampleFixtures;
import com.funchole.backend.controlplane.mcp.FunctionExampleFixtures.ExampleFile;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
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
 * Proves {@link FunctionExampleFixtures#staticMultipageFiles()} - the exact content
 * {@code get_function_example} returns for the STATIC_MULTIPAGE scenario - is real, submittable
 * source: round-trips through the same submit/read endpoints a coding agent would use, byte for
 * byte, including its nested {@code blog/} path. Deliberately does NOT run a real {@code npm run
 * build} (that would additionally require {@code node} on the test machine, matching the
 * repo's existing e2e-only tests) - this is a submit/read fidelity proof for the example's
 * content, not a build-pipeline test; the pipeline itself is already covered by
 * {@code StaticRuntimeBuilderTests} and was live-verified end-to-end for this exact cp/mkdir
 * build-script pattern in MCP_TESTING_FEEDBACK.md item 7a.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FunctionExampleFixturesIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    private String adminToken;
    private String functionId;
    private String versionId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = obtainToken("admin", "admin12345");
        functionId = createStaticFunction("fn_example_static_" + UUID.randomUUID().toString().replace("-", ""));
        versionId = createDraftVersion();
    }

    @Test
    void staticMultipageExampleRoundTripsThroughSubmitAndRead() throws Exception {
        List<ExampleFile> files = FunctionExampleFixtures.staticMultipageFiles();

        var request = multipart("/api/v1/functions/{functionId}/versions/{versionId}/source", functionId, versionId)
                .param("entrypoint", FunctionExampleFixtures.STATIC_ENTRYPOINT)
                .header("Authorization", "Bearer " + adminToken);
        for (ExampleFile file : files) {
            request = request.file(new MockMultipartFile(
                    "files", file.path(), "text/plain", file.content().getBytes(StandardCharsets.UTF_8)));
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runtimeType").value("STATIC"))
                .andExpect(jsonPath("$.data.entrypoint").value(FunctionExampleFixtures.STATIC_ENTRYPOINT))
                .andExpect(jsonPath("$.data.relativePaths", org.hamcrest.Matchers.containsInAnyOrder(
                        "package.json", "index.html", "about.html", "blog/index.html", "blog/first-post.html")));

        MvcResult readResult = mockMvc.perform(get("/api/v1/functions/{functionId}/versions/{versionId}/source/files", functionId, versionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String body = readResult.getResponse().getContentAsString();

        for (ExampleFile file : files) {
            List<String> content = JsonPath.read(body, "$.data.files[?(@.path == '" + file.path() + "')].content");
            assertThat(content).containsExactly(file.content());
        }
    }

    private String createStaticFunction(String functionKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/functions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "functionKey": "%s",
                                  "name": "Example Static Function",
                                  "runtime": "STATIC"
                                }
                                """.formatted(functionKey)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
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
