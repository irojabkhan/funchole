package com.funchole.backend.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class OpenBaoFunctionSecretReader implements FunctionSecretReader {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baoAddress;
    private final String baoToken;

    public OpenBaoFunctionSecretReader(String baoAddress, String baoToken) {
        this.httpClient = HttpClient.newHttpClient();
        this.baoAddress = baoAddress;
        this.baoToken = baoToken;
    }

    @Override
    public String read(String secretRef) {
        try {
            HttpRequest request = HttpRequest.newBuilder(secretUri(secretRef))
                    .header("X-Vault-Token", baoToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("OpenBao function secret read failed with status " + response.statusCode());
            }

            JsonNode value = objectMapper.readTree(response.body()).path("data").path("data").path("value");
            if (value.isMissingNode() || value.isNull()) {
                throw new IllegalStateException("Function secret value is missing in OpenBao: " + secretRef);
            }
            return value.asText();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to read function secret from OpenBao", exception);
        }
    }

    private URI secretUri(String secretRef) {
        return URI.create(baoAddress + "/v1/secret/data/" + secretRef);
    }
}
