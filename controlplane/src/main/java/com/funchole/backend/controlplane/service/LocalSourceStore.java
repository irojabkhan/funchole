package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.config.SourceStorageProperties;
import com.funchole.backend.controlplane.entity.SourceFile;
import com.funchole.backend.controlplane.util.ConcurrentIo;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Default {@link SourceStore} implementation: files live under
 * {@code <storageRoot>/<functionVersionId>/<relativePath>} on local disk.
 * Each {@link #save} replaces the FunctionVersion's directory wholesale, so a
 * resubmission can never leave a stale file behind from a previous
 * submission.
 *
 * <p>Simple and dependency-free, but not durable across a container being
 * recreated unless {@code storageRoot} is a persistent volume - see
 * {@link S3SourceStore} for a durable alternative, selected via
 * {@code SourceStoreConfig}.
 */
public class LocalSourceStore implements SourceStore {

    private final Path storageRoot;

    public LocalSourceStore(SourceStorageProperties properties) {
        this.storageRoot = Path.of(properties.storageRoot());
    }

    @Override
    public void save(UUID functionVersionId, List<SourceFile> files) {
        Path versionRoot = storageRoot.resolve(functionVersionId.toString());
        try {
            deleteRecursively(versionRoot);
            Files.createDirectories(versionRoot);
            createParentDirectories(versionRoot, files);
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to store source for function version: " + functionVersionId, exception);
        }
        ConcurrentIo.forEach(files, file -> writeFile(versionRoot, file));
    }

    @Override
    public List<SourceFile> load(UUID functionVersionId, List<String> relativePaths) {
        Path versionRoot = storageRoot.resolve(functionVersionId.toString());
        return ConcurrentIo.map(relativePaths, relativePath -> readFile(versionRoot, functionVersionId, relativePath));
    }

    // Directory creation is not safely parallelizable when several files
    // share a parent directory, so parent directories are created
    // sequentially up front; the actual file writes below have no such
    // dependency between them and run concurrently.
    private void createParentDirectories(Path versionRoot, List<SourceFile> files) throws IOException {
        Set<Path> parents = new LinkedHashSet<>();
        for (SourceFile file : files) {
            parents.add(versionRoot.resolve(file.relativePath()).normalize().getParent());
        }
        for (Path parent : parents) {
            Files.createDirectories(parent);
        }
    }

    private void writeFile(Path versionRoot, SourceFile file) {
        Path target = versionRoot.resolve(file.relativePath()).normalize();
        try {
            Files.writeString(target, file.content());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write source file: " + file.relativePath(), exception);
        }
    }

    private SourceFile readFile(Path versionRoot, UUID functionVersionId, String relativePath) {
        Path target = versionRoot.resolve(relativePath).normalize();
        if (!Files.exists(target)) {
            // The Postgres-side manifest (entrypoint/handler/relativePaths)
            // can outlive this on-disk content - e.g. the storage root is
            // wiped or replaced independently of the database. Fail with a
            // clear, specific 404 here rather than letting a raw
            // NoSuchFileException surface as an opaque 500 to callers.
            throw new ResourceNotFoundException(
                    "Source content is no longer available for function version " + functionVersionId
                            + " (file " + relativePath + " is missing from storage, though its metadata survives)");
        }
        try {
            return new SourceFile(relativePath, Files.readString(target));
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to load source for function version: " + functionVersionId, exception);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path candidate : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(candidate);
            }
        }
    }
}
