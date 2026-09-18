package com.funchole.backend.gateway.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.funchole.backend.gateway.GatewayRegistry;
import com.funchole.backend.gateway.GatewayRegistrySnapshot;
import com.funchole.backend.gateway.GatewayRequestContext;
import com.funchole.backend.gateway.GatewayRuntimeEntry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnapshotFlowResolverTest {

    private static final UUID GATEWAY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ORDERS_LIST_FLOW_ID = UUID.fromString("55555555-5555-5555-5555-555555555551");
    private static final UUID ORDERS_CREATE_FLOW_ID = UUID.fromString("55555555-5555-5555-5555-555555555552");
    private static final UUID CHECKOUT_FLOW_ID = UUID.fromString("55555555-5555-5555-5555-555555555553");
    private static final UUID ORDERS_LIST_VERSION_ID = UUID.fromString("66666666-6666-6666-6666-666666666661");
    private static final UUID ORDERS_CREATE_VERSION_ID = UUID.fromString("66666666-6666-6666-6666-666666666662");
    private static final UUID CHECKOUT_VERSION_ID = UUID.fromString("66666666-6666-6666-6666-666666666663");
    private static final UUID APP_FLOW_ID = UUID.fromString("55555555-5555-5555-5555-555555555554");
    private static final UUID APP_VERSION_ID = UUID.fromString("66666666-6666-6666-6666-666666666664");
    private static final UUID APP_ADMIN_FLOW_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID APP_ADMIN_VERSION_ID = UUID.fromString("66666666-6666-6666-6666-666666666665");

    private final GatewayRuntimeEntry gateway = new GatewayRuntimeEntry(
            GATEWAY_ID,
            "Primary Gateway",
            "a6n1y8",
            "funchole.test",
            "a6n1y8.funchole.test",
            null,
            null
    );

    private final GatewayRegistry registry = new GatewayRegistry(new GatewayRegistrySnapshot(
            Map.of(),
            null,
            Map.of(GATEWAY_ID, new GatewayRoutingSnapshot(
                    Map.of(
                            new RouteKey("GET", "/orders"),
                            new FlowResolution(ORDERS_LIST_FLOW_ID, "flw_orders_list", ORDERS_LIST_VERSION_ID),
                            new RouteKey("POST", "/orders"),
                            new FlowResolution(ORDERS_CREATE_FLOW_ID, "flw_orders_create", ORDERS_CREATE_VERSION_ID),
                            new RouteKey("POST", "/checkout"),
                            new FlowResolution(CHECKOUT_FLOW_ID, "flw_checkout", CHECKOUT_VERSION_ID)
                    ),
                    // Longest prefix first, matching the ordering GatewayRegistryLoader
                    // itself produces - "/app/admin/*" must be tried before "/app/*".
                    List.of(
                            new PrefixRoute("GET", "/app/admin/", new FlowResolution(APP_ADMIN_FLOW_ID, "flw_app_admin", APP_ADMIN_VERSION_ID)),
                            new PrefixRoute("GET", "/app/", new FlowResolution(APP_FLOW_ID, "flw_app", APP_VERSION_ID))
                    )
            ))
    ));

    private final SnapshotFlowResolver resolver = new SnapshotFlowResolver(registry);

    @Test
    void resolvesGetOrdersToOrdersListFlow() {
        Optional<FlowResolution> resolution = resolve("GET", "/orders");

        assertTrue(resolution.isPresent());
        assertEquals("flw_orders_list", resolution.get().flowKey());
        assertEquals(ORDERS_LIST_FLOW_ID, resolution.get().flowId());
        assertEquals(ORDERS_LIST_VERSION_ID, resolution.get().flowVersionId());
    }

    @Test
    void resolvesPostOrdersToOrdersCreateFlow() {
        Optional<FlowResolution> resolution = resolve("POST", "/orders");

        assertTrue(resolution.isPresent());
        assertEquals("flw_orders_create", resolution.get().flowKey());
        assertEquals(ORDERS_CREATE_FLOW_ID, resolution.get().flowId());
        assertEquals(ORDERS_CREATE_VERSION_ID, resolution.get().flowVersionId());
    }

    @Test
    void resolvesPostCheckoutToCheckoutFlow() {
        Optional<FlowResolution> resolution = resolve("POST", "/checkout");

        assertTrue(resolution.isPresent());
        assertEquals("flw_checkout", resolution.get().flowKey());
        assertEquals(CHECKOUT_FLOW_ID, resolution.get().flowId());
        assertEquals(CHECKOUT_VERSION_ID, resolution.get().flowVersionId());
    }

    @Test
    void rejectsKnownPathWithWrongMethod() {
        assertTrue(resolve("GET", "/checkout").isEmpty());
    }

    @Test
    void rejectsUnknownPath() {
        assertTrue(resolve("GET", "/unknown").isEmpty());
    }

    @Test
    void normalizesTrailingSlash() {
        Optional<FlowResolution> resolution = resolve("GET", "/orders/");

        assertTrue(resolution.isPresent());
        assertEquals("flw_orders_list", resolution.get().flowKey());
    }

    @Test
    void ignoresQueryString() {
        Optional<FlowResolution> resolution = resolve("GET", "/orders?page=2");

        assertTrue(resolution.isPresent());
        assertEquals("flw_orders_list", resolution.get().flowKey());
    }

    @Test
    void resolvesUnmatchedPathToWildcardFlow() {
        Optional<FlowResolution> resolution = resolve("GET", "/app/dashboard/settings");

        assertTrue(resolution.isPresent());
        assertEquals("flw_app", resolution.get().flowKey());
        assertEquals(APP_FLOW_ID, resolution.get().flowId());
    }

    @Test
    void prefersExactRouteOverWildcardFlow() {
        Optional<FlowResolution> resolution = resolve("GET", "/orders");

        assertTrue(resolution.isPresent());
        assertEquals("flw_orders_list", resolution.get().flowKey());
    }

    @Test
    void prefersMoreSpecificWildcardOverBroaderOne() {
        Optional<FlowResolution> resolution = resolve("GET", "/app/admin/users");

        assertTrue(resolution.isPresent());
        assertEquals("flw_app_admin", resolution.get().flowKey());
    }

    @Test
    void wildcardDoesNotMatchUnrelatedPathWithSamePrefixText() {
        assertTrue(resolve("GET", "/apple/foo").isEmpty());
    }

    @Test
    void wildcardMatchesItsOwnRootWithATrailingSlash() {
        Optional<FlowResolution> resolution = resolve("GET", "/app/");

        assertTrue(resolution.isPresent());
        assertEquals("flw_app", resolution.get().flowKey());
    }

    @Test
    void wildcardMatchesItsOwnRootWithNoTrailingSlash() {
        // RouteKey.normalizePath() always strips a bare trailing slash, so
        // both "/app/" and "/app" arrive at PrefixRoute.matches() as "/app" -
        // this is the exact case that regressed serving a static site's own
        // root URL (see StaticFileResolver/GatewayHttpHandler).
        Optional<FlowResolution> resolution = resolve("GET", "/app");

        assertTrue(resolution.isPresent());
        assertEquals("flw_app", resolution.get().flowKey());
    }

    @Test
    void rejectsGatewayWithoutRoutes() {
        GatewayRuntimeEntry otherGateway = new GatewayRuntimeEntry(
                UUID.randomUUID(), "Other", "other", "funchole.test", "other.funchole.test", null, null);

        assertTrue(resolver.resolve(otherGateway, new GatewayRequestContext("GET", "other.funchole.test", "/orders", "/orders")).isEmpty());
    }

    private Optional<FlowResolution> resolve(String method, String path) {
        return resolver.resolve(gateway, new GatewayRequestContext(method, gateway.hostname(), path, path));
    }
}
