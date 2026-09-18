package com.funchole.backend.gateway.staticsite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves a request's relative path against a static site's local
 * directory: an exact file if one exists, otherwise falls back to
 * {@code index.html} at the site root (SPA-style client-side routing -
 * any sub-path the site itself doesn't have a file for is still handed the
 * app shell). Guards against path traversal escaping the site root.
 */
public final class StaticFileResolver {

    private static final String INDEX_HTML = "index.html";

    private StaticFileResolver() {
    }

    public static Optional<Path> resolve(Path siteRoot, String relativePath) {
        Path normalizedRoot = siteRoot.normalize();
        Path candidate = relativePath == null || relativePath.isBlank()
                ? normalizedRoot.resolve(INDEX_HTML)
                : normalizedRoot.resolve(relativePath).normalize();

        if (!candidate.equals(normalizedRoot) && !candidate.startsWith(normalizedRoot)) {
            return Optional.empty(); // path traversal attempt (e.g. "../../etc/passwd")
        }
        if (Files.isRegularFile(candidate)) {
            return Optional.of(candidate);
        }

        Path indexFallback = normalizedRoot.resolve(INDEX_HTML);
        return Files.isRegularFile(indexFallback) ? Optional.of(indexFallback) : Optional.empty();
    }
}
