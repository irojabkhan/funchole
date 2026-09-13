package com.funchole.backend.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The self-describing execution contract carried inside every published
 * artifact: which file is the entrypoint, and which exported function in
 * that file is the handler to invoke. This travels as a plain file
 * ({@value #FILE_NAME}) inside the artifact package itself, not through
 * Postgres - the Runtime Worker that ultimately needs this information
 * never queries the FuncHole database (see {@code RuntimeWorkerServer}'s own
 * contract), so it can only ever learn it from bytes it already has.
 *
 * <p>An artifact with no manifest file (the two hand-placed dev-seed
 * artifacts predate this contract) falls back to {@link #defaults()} - the
 * original hardcoded convention this replaces: {@code index.mjs} exporting
 * {@code handler}. A corrupt or unreadable manifest falls back the same
 * way, since an artifact that already published successfully must never
 * become unexecutable because of a manifest-reading problem.
 */
public record ArtifactManifest(String entrypoint, String handler) {

    public static final String FILE_NAME = ".funchole-artifact.json";

    private static final String DEFAULT_ENTRYPOINT = "index.mjs";
    private static final String DEFAULT_HANDLER = "handler";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ArtifactManifest defaults() {
        return new ArtifactManifest(DEFAULT_ENTRYPOINT, DEFAULT_HANDLER);
    }

    public static ArtifactManifest readOrDefault(Path artifactDirectory) {
        Path manifestPath = artifactDirectory.resolve(FILE_NAME);
        if (!Files.isRegularFile(manifestPath)) {
            return defaults();
        }
        try {
            ArtifactManifest manifest = OBJECT_MAPPER.readValue(manifestPath.toFile(), ArtifactManifest.class);
            String entrypoint = manifest.entrypoint() != null && !manifest.entrypoint().isBlank()
                    ? manifest.entrypoint() : DEFAULT_ENTRYPOINT;
            String handler = manifest.handler() != null && !manifest.handler().isBlank()
                    ? manifest.handler() : DEFAULT_HANDLER;
            return new ArtifactManifest(entrypoint, handler);
        } catch (IOException exception) {
            return defaults();
        }
    }

    public void writeInto(Path artifactDirectory) {
        try {
            OBJECT_MAPPER.writeValue(artifactDirectory.resolve(FILE_NAME).toFile(), this);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write artifact manifest into " + artifactDirectory, exception);
        }
    }
}
