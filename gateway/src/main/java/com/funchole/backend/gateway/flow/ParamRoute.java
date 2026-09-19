package com.funchole.backend.gateway.flow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A path-parameter route - a Flow whose stored path has one or more named
 * segments like {@code :id} (e.g. {@code /api/todos/:id}), matching any
 * single path segment in that position and capturing its value under that
 * name. Distinct from {@link PrefixRoute} ("/*"), which owns an entire
 * subtree instead of one fixed-length shape with holes in it.
 */
public record ParamRoute(String method, List<String> templateSegments, FlowResolution resolution) {

    public ParamRoute {
        method = method == null ? "" : method.trim().toUpperCase();
        templateSegments = List.copyOf(templateSegments);
    }

    /**
     * @param rawPath the Flow's stored path column, e.g. "/api/todos/:id"
     * @return its split segments, or empty if this path has no ":name"
     * segment at all (not a param route)
     */
    public static Optional<List<String>> templateSegments(String rawPath) {
        if (rawPath == null) {
            return Optional.empty();
        }
        List<String> segments = splitSegments(RouteKey.normalizePath(rawPath));
        boolean hasParamSegment = segments.stream().anyMatch(segment -> segment.startsWith(":"));
        return hasParamSegment ? Optional.of(segments) : Optional.empty();
    }

    /**
     * How many of this template's segments are literal (not ":name") -
     * used to rank multiple param routes that could structurally match the
     * same request path, so the more specific one wins.
     */
    public int literalSegmentCount() {
        return (int) templateSegments.stream().filter(segment -> !segment.startsWith(":")).count();
    }

    public Optional<Map<String, String>> match(String method, String normalizedPath) {
        if (!this.method.equals(method)) {
            return Optional.empty();
        }
        List<String> requestSegments = splitSegments(normalizedPath);
        if (requestSegments.size() != templateSegments.size()) {
            return Optional.empty();
        }

        Map<String, String> captured = new LinkedHashMap<>();
        for (int i = 0; i < templateSegments.size(); i++) {
            String templateSegment = templateSegments.get(i);
            String requestSegment = requestSegments.get(i);
            if (templateSegment.startsWith(":")) {
                captured.put(templateSegment.substring(1), requestSegment);
            } else if (!templateSegment.equals(requestSegment)) {
                return Optional.empty();
            }
        }
        return Optional.of(captured);
    }

    private static List<String> splitSegments(String normalizedPath) {
        // normalizedPath always starts with "/" and never ends with a
        // trailing "/" unless it IS "/" itself (see RouteKey.normalizePath).
        if ("/".equals(normalizedPath)) {
            return List.of();
        }
        return List.of(normalizedPath.substring(1).split("/", -1));
    }
}
