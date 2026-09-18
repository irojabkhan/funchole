package com.funchole.backend.gateway.staticsite;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * File-extension to {@code Content-Type} lookup for served static site
 * files - covers what a Vite/CRA/Next static build actually produces.
 * Unknown extensions fall back to a generic binary type rather than
 * guessing.
 */
public final class StaticContentTypes {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry("html", "text/html; charset=UTF-8"),
            Map.entry("htm", "text/html; charset=UTF-8"),
            Map.entry("css", "text/css; charset=UTF-8"),
            Map.entry("js", "text/javascript; charset=UTF-8"),
            Map.entry("mjs", "text/javascript; charset=UTF-8"),
            Map.entry("json", "application/json; charset=UTF-8"),
            Map.entry("map", "application/json; charset=UTF-8"),
            Map.entry("txt", "text/plain; charset=UTF-8"),
            Map.entry("xml", "application/xml; charset=UTF-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf")
    );

    private StaticContentTypes() {
    }

    public static String forPath(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return DEFAULT_CONTENT_TYPE;
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return BY_EXTENSION.getOrDefault(extension, DEFAULT_CONTENT_TYPE);
    }
}
