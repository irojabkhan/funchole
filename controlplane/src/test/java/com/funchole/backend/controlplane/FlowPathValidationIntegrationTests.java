package com.funchole.backend.controlplane;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * No prior test in this repo exercised {@code FlowService.validatePath} at
 * all (neither its pre-existing "*" wildcard rules nor the new ":name"
 * path-parameter rules) - this closes that gap for both.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FlowPathValidationIntegrationTests {

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
                AppDomain.create(admin, "path-validation-" + UUID.randomUUID() + ".example.com", "verify-me", DomainStatus.VERIFIED));
        Gateway gateway = gatewayRepository.save(
                Gateway.create(admin, domain, "Path Validation Test Gateway", "pvt" + System.nanoTime() % 100000, "test gateway", GatewayStatus.ACTIVE));
        gatewayId = gateway.getId();
    }

    @Test
    void acceptsAnExactPath() throws Exception {
        createFlow("/api/todos").andExpect(status().isOk());
    }

    @Test
    void acceptsATrailingWildcard() throws Exception {
        createFlow("/app/*").andExpect(status().isOk());
    }

    @Test
    void rejectsAWildcardNotAtTheEnd() throws Exception {
        createFlow("/app/*/settings").andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsMultipleWildcards() throws Exception {
        createFlow("/app/*/nested/*").andExpect(status().is4xxClientError());
    }

    @Test
    void acceptsASinglePathParameter() throws Exception {
        createFlow("/api/todos/:id").andExpect(status().isOk());
    }

    @Test
    void acceptsMultiplePathParameters() throws Exception {
        createFlow("/api/:resource/:id").andExpect(status().isOk());
    }

    @Test
    void rejectsADuplicateParamNameInTheSamePath() throws Exception {
        createFlow("/api/:id/nested/:id").andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsAnEmptyParamName() throws Exception {
        createFlow("/api/todos/:").andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsAParamNameStartingWithADigit() throws Exception {
        createFlow("/api/todos/:1id").andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsCombiningAWildcardWithAParamSegment() throws Exception {
        createFlow("/api/:id/*").andExpect(status().is4xxClientError());
    }

    private org.springframework.test.web.servlet.ResultActions createFlow(String path) throws Exception {
        String flowKey = "flw_path_validation_" + UUID.randomUUID().toString().replace("-", "");
        return mockMvc.perform(post("/api/v1/flows")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "flowKey": "%s",
                          "name": "Path Validation Test Flow",
                          "gatewayId": "%s",
                          "httpMethod": "GET",
                          "path": "%s"
                        }
                        """.formatted(flowKey, gatewayId, path)));
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
