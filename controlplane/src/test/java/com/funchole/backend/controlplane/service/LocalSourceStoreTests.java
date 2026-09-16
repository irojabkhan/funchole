package com.funchole.backend.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funchole.backend.controlplane.config.SourceStorageProperties;
import com.funchole.backend.controlplane.entity.SourceFile;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSourceStoreTests {

    @TempDir
    Path storageRoot;

    @Test
    void savesAndLoadsFilesRoundTrip() {
        LocalSourceStore store = new LocalSourceStore(new SourceStorageProperties(storageRoot.toString()));
        UUID versionId = UUID.randomUUID();

        store.save(versionId, List.of(new SourceFile("index.mjs", "content-a"), new SourceFile("package.json", "content-b")));
        List<SourceFile> loaded = store.load(versionId, List.of("index.mjs", "package.json"));

        assertThat(loaded).containsExactlyInAnyOrder(
                new SourceFile("index.mjs", "content-a"),
                new SourceFile("package.json", "content-b"));
    }

    @Test
    void loadingAFileMissingFromDiskFailsWithAClearResourceNotFoundExceptionInsteadOfAnOpaqueIOError() {
        LocalSourceStore store = new LocalSourceStore(new SourceStorageProperties(storageRoot.toString()));
        UUID versionId = UUID.randomUUID();
        store.save(versionId, List.of(new SourceFile("index.mjs", "content")));

        // Simulate the manifest (Postgres) outliving the actual file content
        // (disk) - e.g. storage was wiped independently of the database.
        assertThatThrownBy(() -> store.load(versionId, List.of("index.mjs", "server.mjs")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("server.mjs")
                .hasMessageContaining(versionId.toString());
    }
}
