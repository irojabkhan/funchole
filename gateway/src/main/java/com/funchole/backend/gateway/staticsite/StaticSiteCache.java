package com.funchole.backend.gateway.staticsite;

import com.funchole.backend.artifact.RemoteArtifact;
import com.funchole.backend.artifact.RemoteArtifactStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Gateway-local cache of {@code STATIC}-runtime FunctionVersion artifacts,
 * fetched from {@code remoteStore} on a miss. Deliberately its own small
 * class rather than a dependency on the {@code runtime} module's
 * ArtifactCache/CachedArtifactStore - those are shaped around the Node
 * executor's needs (an entrypoint file + handler function name to invoke);
 * the Gateway only ever needs "give me the local directory this site's
 * files live in," so it mirrors that pattern instead of reusing it across
 * a module boundary this codebase otherwise avoids crossing.
 *
 * <p>Layout: {@code <cacheRoot>/<functionVersionId>/} - one directory per
 * exact version, published atomically (extract to a temp dir, then rename)
 * so a concurrent reader never observes a partially-extracted site.
 * Same-process single-flight: concurrent misses for the same version share
 * one remote fetch instead of each downloading independently. No eviction,
 * no TTL - same documented limitation as {@code FilesystemArtifactCache}.
 */
public final class StaticSiteCache {

    private final Path cacheRoot;
    private final RemoteArtifactStore remoteStore;
    private final ConcurrentMap<UUID, CompletableFuture<Optional<Path>>> inFlightResolutions = new ConcurrentHashMap<>();

    public StaticSiteCache(Path cacheRoot, RemoteArtifactStore remoteStore) {
        this.cacheRoot = cacheRoot;
        this.remoteStore = remoteStore;
    }

    public Optional<Path> resolve(UUID functionVersionId) {
        if (functionVersionId == null) {
            return Optional.empty();
        }
        Path cached = cacheEntry(functionVersionId);
        if (Files.isDirectory(cached)) {
            return Optional.of(cached);
        }

        CompletableFuture<Optional<Path>> resolver = new CompletableFuture<>();
        CompletableFuture<Optional<Path>> inFlight = inFlightResolutions.putIfAbsent(functionVersionId, resolver);
        if (inFlight != null) {
            return await(inFlight);
        }

        try {
            if (Files.isDirectory(cached)) {
                resolver.complete(Optional.of(cached));
                return Optional.of(cached);
            }
            Optional<Path> resolved = fetchAndCache(functionVersionId);
            resolver.complete(resolved);
            return resolved;
        } catch (RuntimeException exception) {
            resolver.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightResolutions.remove(functionVersionId, resolver);
        }
    }

    private Optional<Path> await(CompletableFuture<Optional<Path>> inFlight) {
        try {
            return inFlight.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private Optional<Path> fetchAndCache(UUID functionVersionId) {
        // The Gateway never learns a separate componentId for a static site -
        // it only ever has the FunctionVersion id from routing - so the same
        // value is passed for both; RemoteArtifactStore only actually keys
        // its remote lookup on componentVersionId.
        Optional<RemoteArtifact> remote = remoteStore.resolve(functionVersionId, functionVersionId);
        if (remote.isEmpty()) {
            return Optional.empty();
        }
        try (RemoteArtifact remoteArtifact = remote.get()) {
            Path extractedDirectory = remoteArtifact.reference().artifactPath().getParent();
            return Optional.of(publish(functionVersionId, extractedDirectory));
        }
    }

    private Path publish(UUID functionVersionId, Path extractedDirectory) {
        Path entry = cacheEntry(functionVersionId);
        if (Files.isDirectory(entry)) {
            return entry;
        }
        try {
            Files.createDirectories(cacheRoot);
            try {
                Files.move(extractedDirectory, entry, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException exception) {
                // Another thread published it between our check and this move.
                deleteRecursively(extractedDirectory);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to cache static site for functionVersionId=" + functionVersionId, exception);
        }
        return entry;
    }

    private Path cacheEntry(UUID functionVersionId) {
        return cacheRoot.resolve(functionVersionId.toString());
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Cleanup after a losing race to publish is best-effort.
                }
            });
        } catch (IOException ignored) {
            // Cleanup after a losing race to publish is best-effort.
        }
    }
}
