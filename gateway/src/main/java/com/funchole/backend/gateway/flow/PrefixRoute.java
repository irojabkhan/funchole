package com.funchole.backend.gateway.flow;

import java.util.Optional;

/**
 * A wildcard/prefix route - a Flow whose stored path ends in {@code /*},
 * meaning it owns an entire path subtree (a whole SPA/SSR frontend app, or
 * any route that wants to do its own internal sub-routing) rather than one
 * exact path. {@code prefix} keeps its trailing slash deliberately - "/app/"
 * must not match "/apple/foo", so the check is a literal
 * {@code path.startsWith(prefix)} against a prefix that always ends in "/".
 */
public record PrefixRoute(String method, String prefix, FlowResolution resolution) {

    public PrefixRoute {
        method = method == null ? "" : method.trim().toUpperCase();
    }

    /**
     * @param rawPath the Flow's stored path column, e.g. "/*" or "/app/*"
     * @return the matching prefix (always ending in "/"), or empty if this
     * path is not a wildcard route at all (no trailing "/*")
     */
    public static Optional<String> wildcardPrefix(String rawPath) {
        if (rawPath == null) {
            return Optional.empty();
        }
        String trimmed = rawPath.trim();
        if (!trimmed.endsWith("/*")) {
            return Optional.empty();
        }
        return Optional.of(trimmed.substring(0, trimmed.length() - 1)); // strip trailing "*", keep the "/"
    }

    public boolean matches(String method, String normalizedPath) {
        if (!this.method.equals(method)) {
            return false;
        }
        if (normalizedPath.startsWith(prefix)) {
            return true;
        }
        // RouteKey.normalizePath() always strips a bare trailing slash, so a
        // request for this route's own root - "/app/" or even "/app" for a
        // "/app/*" Flow - arrives here as "/app" (prefix minus its trailing
        // "/"), never as "/app/". Without this, the wildcard route's own
        // root URL would 404 even though anything under it resolves fine.
        return normalizedPath.length() == prefix.length() - 1 && prefix.startsWith(normalizedPath);
    }
}
