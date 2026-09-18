package com.funchole.backend.gateway.flow;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code prefixRoutes} must already be sorted longest-prefix-first by the
 * loader (see {@code GatewayRegistryLoader}) - {@link #resolve} relies on
 * that ordering (first match wins) rather than re-sorting per request, so
 * the more specific of two overlapping wildcard routes (e.g. "/app/admin/*"
 * vs "/app/*") is always tried first.
 */
public record GatewayRoutingSnapshot(Map<RouteKey, FlowResolution> routesByKey, List<PrefixRoute> prefixRoutes) {

    public static final GatewayRoutingSnapshot EMPTY = new GatewayRoutingSnapshot(Map.of(), List.of());

    public GatewayRoutingSnapshot {
        prefixRoutes = List.copyOf(prefixRoutes);
    }

    public Optional<FlowResolution> resolve(String method, String path) {
        RouteKey key = new RouteKey(method, path);
        FlowResolution exact = routesByKey.get(key);
        if (exact != null) {
            return Optional.of(exact);
        }

        for (PrefixRoute route : prefixRoutes) {
            if (route.matches(key.method(), key.path())) {
                return Optional.of(route.resolution());
            }
        }

        return Optional.empty();
    }
}
