package com.funchole.backend.artifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * First {@link ArtifactStore} implementation: local directory convention
 * {@code <artifactsRoot>/<componentVersionId>/}, with the entrypoint file
 * and handler function name read from the {@link ArtifactManifest} inside
 * that directory (falling back to {@link ArtifactManifest#defaults()} for
 * artifacts published before the manifest existed).
 *
 * The lookup key is exclusively componentVersionId - no directory scanning,
 * no "latest" symlink convention, no filename-based version inference. An
 * unmapped componentVersionId simply resolves to nothing.
 */
public final class LocalArtifactStore implements ArtifactStore {

    private final Path artifactsRoot;
    private final String runtimeType;

    public LocalArtifactStore(Path artifactsRoot, String runtimeType) {
        this.artifactsRoot = artifactsRoot;
        this.runtimeType = runtimeType;
    }

    @Override
    public Optional<ArtifactReference> resolve(UUID componentId, UUID componentVersionId) {
        if (componentId == null || componentVersionId == null) {
            return Optional.empty();
        }
        Path componentDirectory = artifactsRoot.resolve(componentVersionId.toString());
        ArtifactManifest manifest = ArtifactManifest.readOrDefault(componentDirectory);
        Path artifactPath = componentDirectory.resolve(manifest.entrypoint());
        if (!Files.isRegularFile(artifactPath)) {
            return Optional.empty();
        }
        return Optional.of(new ArtifactReference(
                componentId, componentVersionId, runtimeType, artifactPath.toAbsolutePath(), manifest.handler()));
    }
}
