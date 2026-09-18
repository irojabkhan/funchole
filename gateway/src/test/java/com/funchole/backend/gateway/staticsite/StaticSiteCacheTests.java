package com.funchole.backend.gateway.staticsite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.funchole.backend.artifact.ArtifactReference;
import com.funchole.backend.artifact.RemoteArtifact;
import com.funchole.backend.artifact.RemoteArtifactStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticSiteCacheTests {

    @TempDir
    Path cacheRoot;

    @Test
    void fetchesFromTheRemoteStoreOnAMissAndPublishesUnderTheCacheRoot() throws IOException {
        UUID functionVersionId = UUID.randomUUID();
        FakeRemoteArtifactStore remoteStore = new FakeRemoteArtifactStore();
        remoteStore.stage(functionVersionId, "<html>hi</html>");
        StaticSiteCache cache = new StaticSiteCache(cacheRoot, remoteStore);

        Optional<Path> resolved = cache.resolve(functionVersionId);

        assertTrue(resolved.isPresent());
        assertEquals("<html>hi</html>", Files.readString(resolved.get().resolve("index.html")));
        assertEquals(1, remoteStore.resolveCallCount.get());
    }

    @Test
    void doesNotFetchAgainOnceAVersionIsAlreadyCached() throws IOException {
        UUID functionVersionId = UUID.randomUUID();
        FakeRemoteArtifactStore remoteStore = new FakeRemoteArtifactStore();
        remoteStore.stage(functionVersionId, "<html>hi</html>");
        StaticSiteCache cache = new StaticSiteCache(cacheRoot, remoteStore);

        cache.resolve(functionVersionId);
        Optional<Path> secondResolve = cache.resolve(functionVersionId);

        assertTrue(secondResolve.isPresent());
        assertEquals(1, remoteStore.resolveCallCount.get());
    }

    @Test
    void returnsEmptyWhenTheRemoteStoreHasNoArtifactForThatVersion() {
        StaticSiteCache cache = new StaticSiteCache(cacheRoot, new FakeRemoteArtifactStore());

        Optional<Path> resolved = cache.resolve(UUID.randomUUID());

        assertTrue(resolved.isEmpty());
    }

    @Test
    void returnsEmptyForANullFunctionVersionId() {
        StaticSiteCache cache = new StaticSiteCache(cacheRoot, new FakeRemoteArtifactStore());

        assertTrue(cache.resolve(null).isEmpty());
    }

    @Test
    void concurrentMissesForTheSameVersionShareOneRemoteFetch() throws Exception {
        UUID functionVersionId = UUID.randomUUID();
        FakeRemoteArtifactStore remoteStore = new FakeRemoteArtifactStore();
        remoteStore.stage(functionVersionId, "<html>hi</html>");
        remoteStore.blockUntilReleased = new CountDownLatch(1);
        StaticSiteCache cache = new StaticSiteCache(cacheRoot, remoteStore);

        CompletableFuture<Optional<Path>> first = CompletableFuture.supplyAsync(() -> cache.resolve(functionVersionId));
        CompletableFuture<Optional<Path>> second = CompletableFuture.supplyAsync(() -> cache.resolve(functionVersionId));

        Thread.sleep(100);
        remoteStore.blockUntilReleased.countDown();

        assertTrue(first.get().isPresent());
        assertTrue(second.get().isPresent());
        assertEquals(1, remoteStore.resolveCallCount.get());
    }

    private static final class FakeRemoteArtifactStore implements RemoteArtifactStore {
        private final AtomicInteger resolveCallCount = new AtomicInteger();
        private final java.util.Map<UUID, String> staged = new java.util.concurrent.ConcurrentHashMap<>();
        private volatile CountDownLatch blockUntilReleased;

        void stage(UUID functionVersionId, String indexHtmlContent) {
            staged.put(functionVersionId, indexHtmlContent);
        }

        @Override
        public Optional<RemoteArtifact> resolve(UUID componentId, UUID componentVersionId) {
            resolveCallCount.incrementAndGet();
            if (blockUntilReleased != null) {
                try {
                    blockUntilReleased.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            String indexHtmlContent = staged.get(componentVersionId);
            if (indexHtmlContent == null) {
                return Optional.empty();
            }
            try {
                Path extractedDirectory = Files.createTempDirectory("static-site-cache-test-");
                Path indexHtml = extractedDirectory.resolve("index.html");
                Files.writeString(indexHtml, indexHtmlContent);
                ArtifactReference reference =
                        new ArtifactReference(componentId, componentVersionId, "STATIC", indexHtml, "");
                return Optional.of(new RemoteArtifact(reference, extractedDirectory));
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }
    }
}
