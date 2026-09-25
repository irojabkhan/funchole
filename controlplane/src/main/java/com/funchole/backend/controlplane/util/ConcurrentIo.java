package com.funchole.backend.controlplane.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs a list of independent, blocking I/O operations (S3/local-disk reads
 * and writes, one per source file) concurrently instead of one at a time,
 * bounded so a large multi-file submission can't open an unbounded number of
 * simultaneous S3 connections or file handles. Used by {@code SourceStore}
 * implementations and {@code BuildWorkspaceService}, which previously looped
 * over N files sequentially - the dominant cost for a real multi-page app's
 * source.
 */
public final class ConcurrentIo {

    private static final int MAX_CONCURRENCY = 16;

    private ConcurrentIo() {
    }

    /** Applies {@code mapper} to every item concurrently, preserving input order in the result. */
    public static <T, R> List<R> map(List<T> items, Function<T, R> mapper) {
        if (items.size() <= 1) {
            return items.stream().map(mapper).toList();
        }
        int poolSize = Math.min(items.size(), MAX_CONCURRENCY);
        try (ExecutorService executor = Executors.newFixedThreadPool(poolSize)) {
            List<Future<R>> futures = items.stream()
                    .map(item -> executor.submit(() -> mapper.apply(item)))
                    .toList();
            List<R> results = new ArrayList<>(futures.size());
            for (Future<R> future : futures) {
                results.add(resolve(future));
            }
            return results;
        }
    }

    /** Applies {@code action} to every item concurrently; propagates the first failure. */
    public static <T> void forEach(List<T> items, Consumer<T> action) {
        if (items.size() <= 1) {
            items.forEach(action);
            return;
        }
        int poolSize = Math.min(items.size(), MAX_CONCURRENCY);
        try (ExecutorService executor = Executors.newFixedThreadPool(poolSize)) {
            List<Future<Void>> futures = items.stream()
                    .<Future<Void>>map(item -> executor.submit(() -> {
                        action.accept(item);
                        return null;
                    }))
                    .toList();
            for (Future<Void> future : futures) {
                resolve(future);
            }
        }
    }

    private static <R> R resolve(Future<R> future) {
        try {
            return future.get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent I/O to complete", exception);
        }
    }
}
