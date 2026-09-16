package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.config.SourceStorageProperties;
import com.funchole.backend.controlplane.entity.SourceFile;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * First {@link SourceStore} implementation: files live under
 * {@code <storageRoot>/<functionVersionId>/<relativePath>} on local disk.
 * Each {@link #save} replaces the FunctionVersion's directory wholesale, so a
 * resubmission can never leave a stale file behind from a previous
 * submission.
 */
@Component
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
            for (SourceFile file : files) {
                Path target = versionRoot.resolve(file.relativePath()).normalize();
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.content());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to store source for function version: " + functionVersionId, exception);
        }
    }

    @Override
    public List<SourceFile> load(UUID functionVersionId, List<String> relativePaths) {
        Path versionRoot = storageRoot.resolve(functionVersionId.toString());
        try {
            List<SourceFile> files = new ArrayList<>();
            for (String relativePath : relativePaths) {
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
                files.add(new SourceFile(relativePath, Files.readString(target)));
            }
            return files;
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
