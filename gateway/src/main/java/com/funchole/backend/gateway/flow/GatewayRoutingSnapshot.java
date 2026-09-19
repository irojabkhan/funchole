package com.funchole.backend.gateway.flow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code prefixRoutes} must already be sorted longest-prefix-first by the
 * loader (see {@code GatewayRegistryLoader}) - {@link #resolve} relies on
 * that ordering (first match wins) rather than re-sorting per request, so
 * the more specific of two overlapping wildcard routes (e.g. "/app/admin/*"
 * vs "/app/*") is always tried first.
 *
 * <p>Precedence: an exact match always wins first, then a {@link ParamRoute}
 * match (e.g. "/api/todos/:id"), then a {@link PrefixRoute} wildcard match
 * (e.g. "/api/todos/*"). A param route is a fixed-length shape with named
 * holes, strictly more specific than a wildcard's open-ended subtree, so it
 * is tried first.
 */
public record GatewayRoutingSnapshot(
        Map<RouteKey, FlowResolution> routesByKey,
        List<ParamRoute> paramRoutes,
        List<PrefixRoute> prefixRoutes
) {

    public static final GatewayRoutingSnapshot EMPTY = new GatewayRoutingSnapshot(Map.of(), List.of(), List.of());

    public GatewayRoutingSnapshot {
        paramRoutes = List.copyOf(paramRoutes);
        prefixRoutes = List.copyOf(prefixRoutes);
    }

    public Optional<RouteMatch> resolve(String method, String path) {
        RouteKey key = new RouteKey(method, path);
        FlowResolution exact = routesByKey.get(key);
        if (exact != null) {
            return Optional.of(new RouteMatch(exact, Map.of()));
        }

        Optional<RouteMatch> paramMatch = resolveParam(key.method(), key.path());
        if (paramMatch.isPresent()) {
            return paramMatch;
        }

        for (PrefixRoute route : prefixRoutes) {
            if (route.matches(key.method(), key.path())) {
                return Optional.of(new RouteMatch(route.resolution(), Map.of()));
            }
        }

        return Optional.empty();
    }

    /**
     * Multiple ":param" routes could structurally match the same request
     * (different Flows that happen to share a segment count) - the one with
     * the most literal (non-param) segments wins as the more specific match;
     * ties keep whichever is found first.
     */
    private Optional<RouteMatch> resolveParam(String method, String normalizedPath) {
        ParamRoute best = null;
        Map<String, String> bestCaptured = null;
        for (ParamRoute route : paramRoutes) {
            Optional<Map<String, String>> captured = route.match(method, normalizedPath);
            if (captured.isPresent() && (best == null || route.literalSegmentCount() > best.literalSegmentCount())) {
                best = route;
                bestCaptured = captured.get();
            }
        }
        return best == null ? Optional.empty() : Optional.of(new RouteMatch(best.resolution(), bestCaptured));
    }
}
