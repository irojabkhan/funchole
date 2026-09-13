package com.funchole.backend.artifact;

import java.nio.file.Path;
import java.util.UUID;

/**
 * The exact executable artifact pinned to one componentId/componentVersionId
 * pair, plus the exported function name ({@code handler}) within
 * {@code artifactPath} to invoke - both read from the artifact's own
 * {@link ArtifactManifest}, never from Postgres.
 */
public record ArtifactReference(
        UUID componentId,
        UUID componentVersionId,
        String runtimeType,
        Path artifactPath,
        String handler
) {
}
