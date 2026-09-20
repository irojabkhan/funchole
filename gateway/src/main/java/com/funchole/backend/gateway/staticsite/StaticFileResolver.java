package com.funchole.backend.gateway.staticsite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves a request's relative path against a static site's local
 * directory, trying the same "clean URL" candidates every static host
 * (Netlify, Vercel, nginx {@code try_files}) does before giving up: the
 * exact path, {@code <path>.html}, then {@code <path>/index.html} - so a
 * genuine multi-page site (built by any static site generator's default
 * clean-URL output) resolves {@code /about} to its own page rather than
 * always falling back to the site's root. Only once none of those exist
 * does it fall back to {@code index.html} at the site root (SPA-style
 * client-side routing - a route the site itself has no file for is handed
 * the app shell).
 *
 * <p>A path-traversal attempt (e.g. {@code "../../etc/passwd"}) is a hard
 * reject with no fallback at all, checked once up front against the exact
 * candidate - it is a different case from "legitimately missing", which
 * still gets the SPA fallback.
 */
public final class StaticFileResolver {

    private static final String INDEX_HTML = "index.html";
    private static final String HTML_EXTENSION = ".html";

    private StaticFileResolver() {
    }

    public static Optional<Path> resolve(Path siteRoot, String relativePath) {
        Path normalizedRoot = siteRoot.normalize();
        String trimmed = trimTrailingSlashes(relativePath);

        if (trimmed.isEmpty()) {
            return existingFile(normalizedRoot, normalizedRoot.resolve(INDEX_HTML));
        }

        Path exactCandidate = normalizedRoot.resolve(trimmed).normalize();
        if (!isWithinRoot(normalizedRoot, exactCandidate)) {
            return Optional.empty(); // path traversal attempt - reject outright, no SPA fallback either
        }
        if (Files.isRegularFile(exactCandidate)) {
            return Optional.of(exactCandidate);
        }

        Path htmlCandidate = normalizedRoot.resolve(trimmed + HTML_EXTENSION).normalize();
        if (isWithinRoot(normalizedRoot, htmlCandidate) && Files.isRegularFile(htmlCandidate)) {
            return Optional.of(htmlCandidate);
        }

        Path directoryIndexCandidate = normalizedRoot.resolve(trimmed).resolve(INDEX_HTML).normalize();
        if (isWithinRoot(normalizedRoot, directoryIndexCandidate) && Files.isRegularFile(directoryIndexCandidate)) {
            return Optional.of(directoryIndexCandidate);
        }

        return existingFile(normalizedRoot, normalizedRoot.resolve(INDEX_HTML));
    }

    private static String trimTrailingSlashes(String relativePath) {
        if (relativePath == null) {
            return "";
        }
        int end = relativePath.length();
        while (end > 0 && relativePath.charAt(end - 1) == '/') {
            end--;
        }
        return relativePath.substring(0, end);
    }

    private static boolean isWithinRoot(Path normalizedRoot, Path candidate) {
        return candidate.equals(normalizedRoot) || candidate.startsWith(normalizedRoot);
    }

    private static Optional<Path> existingFile(Path normalizedRoot, Path candidate) {
        if (!isWithinRoot(normalizedRoot, candidate)) {
            return Optional.empty();
        }
        return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }
}
