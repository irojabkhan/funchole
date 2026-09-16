package com.funchole.backend.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistentNodeExecutorTest {

    private static final Path SCRIPT_PATH = Path.of("node", "executor.mjs").toAbsolutePath();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path artifactsRoot;

    private PersistentNodeExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.close();
        }
    }

    @Test
    void executesRealJavaScriptHandlerAndReturnsOutput() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("success", "export async function handler(input) { return { ok: true, input }; }");

        NodeExecutionResult result = execute(artifact, "{\"path\":\"/orders\"}");

        assertTrue(result.success());
        assertEquals("{\"ok\":true,\"input\":{\"path\":\"/orders\"}}", result.output());
    }

    @Test
    void executesACustomExportedFunctionNameOtherThanHandler() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("custom-name", "export async function GrowUp(input) { return input; }");

        NodeExecutionRequest request = new NodeExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), artifact, "GrowUp", "{\"n\":1}");
        NodeExecutionResult result = executor.execute(request, logMessage -> { }).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertEquals("{\"n\":1}", result.output());
    }

    @Test
    void injectsEnvironmentVariablesIntoHandlerProcessEnv() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("env", """
                export async function handler(input) {
                    return {
                        nodeEnv: process.env.NODE_ENV,
                        apiToken: process.env.API_TOKEN
                    };
                }
                """);

        NodeExecutionRequest request = new NodeExecutionRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                artifact,
                "handler",
                "{}",
                Map.of("NODE_ENV", "test", "API_TOKEN", "secret-token"),
                List.of()
        );
        NodeExecutionResult result = executor.execute(request, logMessage -> { }).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertEquals("{\"nodeEnv\":\"test\",\"apiToken\":\"secret-token\"}", result.output());
    }

    @Test
    void userConsoleLogDoesNotCorruptProtocolResult() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("logging", """
                export async function handler(input) {
                    console.log("hello from artifact", input);
                    console.error("artifact warning");
                    return { ok: true };
                }
                """);

        NodeExecutionResult result = execute(artifact, "{\"path\":\"/orders\"}");

        assertTrue(result.success());
        assertEquals("{\"ok\":true}", result.output());
    }

    @Test
    void emittedFunctionLogsRedactInjectedSecretValues() throws Exception {
        Path artifact = writeArtifact("redacted-logging", """
                export async function handler(input) {
                    console.log("token", process.env.API_TOKEN);
                    return { ok: true };
                }
                """);
        UUID executionId = UUID.randomUUID();
        Process process = new ProcessBuilder("node", SCRIPT_PATH.toString()).start();

        try (
                OutputStream stdin = process.getOutputStream();
                BufferedReader stdout = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
        ) {
            String executeMessage = OBJECT_MAPPER.writeValueAsString(Map.of(
                    "type", "EXECUTE",
                    "executionId", executionId,
                    "componentId", UUID.randomUUID(),
                    "componentVersionId", UUID.randomUUID(),
                    "artifactPath", artifact.toString(),
                    "handler", "handler",
                    "input", "{}",
                    "environment", Map.of("API_TOKEN", "secret-token")
            ));
            stdin.write((executeMessage + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();

            JsonNode log = OBJECT_MAPPER.readTree(stdout.readLine());
            JsonNode result = OBJECT_MAPPER.readTree(stdout.readLine());

            assertEquals("LOG", log.path("type").asText());
            assertEquals(executionId.toString(), log.path("executionId").asText());
            assertEquals("stdout", log.path("stream").asText());
            assertEquals("token [REDACTED]", log.path("message").asText());
            assertEquals("RESULT", result.path("type").asText());
            assertEquals("{\"ok\":true}", result.path("output").asText());
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void restoresEnvironmentVariablesAfterExecution() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("env-restore", """
                export async function handler(input) {
                    return { apiToken: process.env.API_TOKEN ?? null };
                }
                """);

        NodeExecutionRequest first = new NodeExecutionRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                artifact,
                "handler",
                "{}",
                Map.of("API_TOKEN", "secret-token"),
                List.of()
        );
        NodeExecutionResult firstResult = executor.execute(first, logMessage -> { }).toCompletableFuture().get(5, TimeUnit.SECONDS);
        NodeExecutionResult secondResult = execute(artifact, "{}");

        assertTrue(firstResult.success());
        assertTrue(secondResult.success());
        assertEquals("{\"apiToken\":\"secret-token\"}", firstResult.output());
        assertEquals("{\"apiToken\":null}", secondResult.output());
    }

    @Test
    void mapsAMissingCustomHandlerNameToHandlerNotFound() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("wrong-custom-name", "export async function GrowUp(input) { return input; }");

        NodeExecutionRequest request = new NodeExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), artifact, "NotExported", "{}");
        NodeExecutionResult result = executor.execute(request, logMessage -> { }).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertEquals("HANDLER_NOT_FOUND", result.errorCode());
    }

    @Test
    void awaitsAsyncHandlerCorrectly() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("async", """
                export async function handler(input) {
                    await new Promise((resolve) => setTimeout(resolve, 50));
                    return { delayed: true };
                }
                """);

        NodeExecutionResult result = execute(artifact, "{}");

        assertTrue(result.success());
        assertEquals("{\"delayed\":true}", result.output());
    }

    @Test
    void mapsThrownHandlerErrorToArtifactExecutionError() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("throwing", """
                export async function handler(input) {
                    throw new Error("simulated artifact failure");
                }
                """);

        NodeExecutionResult result = execute(artifact, "{}");

        assertFalse(result.success());
        assertEquals("ARTIFACT_EXECUTION_ERROR", result.errorCode());
        assertEquals("simulated artifact failure", result.errorMessage());
    }

    @Test
    void mapsMissingHandlerExportToHandlerNotFound() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("no-handler", "export const notAHandler = 42;");

        NodeExecutionResult result = execute(artifact, "{}");

        assertFalse(result.success());
        assertEquals("HANDLER_NOT_FOUND", result.errorCode());
    }

    @Test
    void mapsMissingArtifactFileToArtifactNotFound() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path missing = artifactsRoot.resolve("does-not-exist.mjs");

        NodeExecutionResult result = execute(missing, "{}");

        assertFalse(result.success());
        assertEquals("ARTIFACT_NOT_FOUND", result.errorCode());
    }

    @Test
    void reusesTheSameProcessAcrossMultipleExecutions() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("success", "export async function handler(input) { return { ok: true, input }; }");

        long pidBeforeFirst = executor.pid();
        NodeExecutionResult first = execute(artifact, "{\"n\":1}");
        long pidAfterFirst = executor.pid();
        NodeExecutionResult second = execute(artifact, "{\"n\":2}");
        long pidAfterSecond = executor.pid();

        assertTrue(first.success());
        assertTrue(second.success());
        assertTrue(executor.isAlive());
        assertEquals(pidBeforeFirst, pidAfterFirst);
        assertEquals(pidAfterFirst, pidAfterSecond);
    }

    @Test
    void correlatesConcurrentExecutionsByExecutionId() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("async", """
                export async function handler(input) {
                    await new Promise((resolve) => setTimeout(resolve, 20 + Math.random() * 30));
                    return { echoed: input };
                }
                """);

        List<NodeExecutionRequest> requests = IntStream.range(0, 8)
                .mapToObj(i -> new NodeExecutionRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        artifact, "handler", "{\"n\":" + i + "}"))
                .toList();

        List<CompletableFuture<NodeExecutionResult>> futures = requests.stream()
                .map(request -> executor.execute(request, logMessage -> { }).toCompletableFuture())
                .toList();

        for (int i = 0; i < requests.size(); i++) {
            NodeExecutionResult result = futures.get(i).get(5, TimeUnit.SECONDS);
            assertEquals(requests.get(i).executionId(), result.executionId());
            assertEquals("{\"echoed\":{\"n\":" + i + "}}", result.output());
        }
    }

    @Test
    void concurrentWritesDoNotCorruptFraming() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("success", "export async function handler(input) { return input; }");

        List<NodeExecutionRequest> requests = IntStream.range(0, 20)
                .mapToObj(i -> new NodeExecutionRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        artifact, "handler", "{\"index\":" + i + "}"))
                .toList();

        List<CompletableFuture<NodeExecutionResult>> futures = requests.stream()
                .map(request -> executor.execute(request, logMessage -> { }).toCompletableFuture())
                .toList();

        for (int i = 0; i < requests.size(); i++) {
            NodeExecutionResult result = futures.get(i).get(5, TimeUnit.SECONDS);
            assertTrue(result.success());
            assertEquals(requests.get(i).executionId(), result.executionId());
            assertEquals("{\"index\":" + i + "}", result.output());
        }
    }

    @Test
    void failsPendingExecutionWhenProcessDiesRatherThanHangingForever() throws Exception {
        executor = PersistentNodeExecutor.start("node", SCRIPT_PATH);
        Path artifact = writeArtifact("slow", """
                export async function handler(input) {
                    await new Promise((resolve) => setTimeout(resolve, 30000));
                    return { tooLate: true };
                }
                """);
        NodeExecutionRequest request = new NodeExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), artifact, "handler", "{}");

        CompletableFuture<NodeExecutionResult> pending = executor.execute(request, logMessage -> { }).toCompletableFuture();
        Thread.sleep(100);
        executor.close();

        ExecutionException exception = assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
        assertNotNull(exception.getCause());
    }

    private NodeExecutionResult execute(Path artifact, String input) throws Exception {
        NodeExecutionRequest request = new NodeExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), artifact, "handler", input);
        return executor.execute(request, logMessage -> { }).toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private Path writeArtifact(String name, String source) throws IOException {
        Path path = artifactsRoot.resolve(name + ".mjs");
        Files.writeString(path, source);
        return path;
    }
}
