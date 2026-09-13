package com.funchole.backend.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalArtifactStoreTest {

    @TempDir
    Path artifactsRoot;

    @Test
    void resolvesExactRegisteredComponentVersion() throws Exception {
        UUID componentId = UUID.randomUUID();
        UUID componentVersionId = UUID.randomUUID();
        writeArtifact(componentVersionId, "export async function handler(input) { return { ok: true }; }");
        ArtifactStore store = new LocalArtifactStore(artifactsRoot, "NODE");

        Optional<ArtifactReference> resolved = store.resolve(componentId, componentVersionId);

        assertTrue(resolved.isPresent());
        assertEquals(componentId, resolved.get().componentId());
        assertEquals(componentVersionId, resolved.get().componentVersionId());
        assertEquals("NODE", resolved.get().runtimeType());
        assertTrue(resolved.get().artifactPath().toString().endsWith("index.mjs"));
    }

    @Test
    void pinnedVersionStaysStableWhenANewerVersionIsRegistered() throws Exception {
        UUID componentId = UUID.randomUUID();
        UUID versionA = UUID.randomUUID();
        UUID versionB = UUID.randomUUID();
        writeArtifact(versionA, "export async function handler() { return 'A'; }");
        writeArtifact(versionB, "export async function handler() { return 'B'; }");
        ArtifactStore store = new LocalArtifactStore(artifactsRoot, "NODE");

        Optional<ArtifactReference> resolved = store.resolve(componentId, versionA);

        assertTrue(resolved.isPresent());
        assertEquals(versionA, resolved.get().componentVersionId());
        assertTrue(resolved.get().artifactPath().toString().contains(versionA.toString()));
        assertTrue(!resolved.get().artifactPath().toString().contains(versionB.toString()));
    }

    @Test
    void resolvesTheManifestEntrypointAndHandlerWhenPresent() throws Exception {
        UUID componentId = UUID.randomUUID();
        UUID componentVersionId = UUID.randomUUID();
        Path directory = artifactsRoot.resolve(componentVersionId.toString());
        Files.createDirectories(directory.resolve("src"));
        Files.writeString(directory.resolve("src/main.mjs"), "export async function GrowUp(input) { return input; }");
        new ArtifactManifest("src/main.mjs", "GrowUp").writeInto(directory);
        ArtifactStore store = new LocalArtifactStore(artifactsRoot, "NODE");

        Optional<ArtifactReference> resolved = store.resolve(componentId, componentVersionId);

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().artifactPath().toString().endsWith("src/main.mjs"));
        assertEquals("GrowUp", resolved.get().handler());
    }

    @Test
    void defaultsToIndexMjsAndHandlerWhenNoManifestIsPresent() throws Exception {
        UUID componentId = UUID.randomUUID();
        UUID componentVersionId = UUID.randomUUID();
        writeArtifact(componentVersionId, "export async function handler(input) { return input; }");
        ArtifactStore store = new LocalArtifactStore(artifactsRoot, "NODE");

        Optional<ArtifactReference> resolved = store.resolve(componentId, componentVersionId);

        assertTrue(resolved.isPresent());
        assertEquals("handler", resolved.get().handler());
    }

    @Test
    void resolvesEmptyForUnknownComponentVersion() {
        ArtifactStore store = new LocalArtifactStore(artifactsRoot, "NODE");

        Optional<ArtifactReference> resolved = store.resolve(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(resolved.isEmpty());
    }

    private void writeArtifact(UUID componentVersionId, String source) throws IOException {
        Path directory = artifactsRoot.resolve(componentVersionId.toString());
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("index.mjs"), source);
    }
}
