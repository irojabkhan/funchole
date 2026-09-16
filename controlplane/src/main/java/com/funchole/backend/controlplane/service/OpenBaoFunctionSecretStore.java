package com.funchole.backend.controlplane.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenBaoFunctionSecretStore implements FunctionSecretStore {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baoAddress;
    private final String baoToken;

    public OpenBaoFunctionSecretStore(
            @Value("${BAO_ADDR:http://localhost:8200}") String baoAddress,
            @Value("${BAO_TOKEN:root}") String baoToken
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.baoAddress = baoAddress;
        this.baoToken = baoToken;
    }

    @Override
    public String save(UUID functionVersionId, String key, String value) {
        return writeSecret(secretRef(functionVersionId, key), value);
    }

    @Override
    public String saveForEnvironment(UUID environmentProfileId, String key, String value) {
        return writeSecret(environmentSecretRef(environmentProfileId, key), value);
    }

    @Override
    public String saveForDatabase(UUID databaseId, String key, String value) {
        return writeSecret(databaseSecretRef(databaseId, key), value);
    }

    private String writeSecret(String secretRef, String value) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "data", Map.of("value", value)
            ));

            HttpRequest request = HttpRequest.newBuilder(secretUri(secretRef))
                    .header("X-Vault-Token", baoToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("OpenBao secret write failed with status " + response.statusCode());
            }
            return secretRef;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to save secret to OpenBao", exception);
        }
    }

    private static String secretRef(UUID functionVersionId, String key) {
        return "function-versions/" + functionVersionId + "/secrets/" + key;
    }

    private static String environmentSecretRef(UUID environmentProfileId, String key) {
        return "environments/" + environmentProfileId + "/secrets/" + key;
    }

    private static String databaseSecretRef(UUID databaseId, String key) {
        return "databases/" + databaseId + "/" + key;
    }

    private URI secretUri(String secretRef) {
        return URI.create(baoAddress + "/v1/secret/data/" + secretRef);
    }
}
