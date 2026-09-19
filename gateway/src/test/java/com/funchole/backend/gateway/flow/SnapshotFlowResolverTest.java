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
    private static final UUID TODO_GET_FLOW_ID = UUID.fromString("55555555-5555-5555-5555-555555555556");
    private static final UUID TODO_GET_VERSION_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID GENERIC_RESOURCE_FLOW_ID = UUID.fromString("55555555-5555-5555-5555-555555555557");
    private static final UUID GENERIC_RESOURCE_VERSION_ID = UUID.fromString("66666666-6666-6666-6666-666666666667");

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
                    List.of(
                            // "/api/:resource/:id" (1 literal segment) is less specific
                            // than "/api/todos/:id" (2 literal segments) - both match
                            // "/api/todos/5" by segment count, the more literal one wins.
                            new ParamRoute("GET", List.of("api", "todos", ":id"),
                                    new FlowResolution(TODO_GET_FLOW_ID, "flw_todo_get", TODO_GET_VERSION_ID)),
                            new ParamRoute("GET", List.of("api", ":resource", ":id"),
                                    new FlowResolution(GENERIC_RESOURCE_FLOW_ID, "flw_generic_resource", GENERIC_RESOURCE_VERSION_ID))
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
        Optional<RouteMatch> match = resolve("GET", "/orders");

        assertTrue(match.isPresent());
        assertEquals("flw_orders_list", match.get().resolution().flowKey());
        assertEquals(ORDERS_LIST_FLOW_ID, match.get().resolution().flowId());
        assertEquals(ORDERS_LIST_VERSION_ID, match.get().resolution().flowVersionId());
    }

    @Test
    void resolvesPostOrdersToOrdersCreateFlow() {
        Optional<RouteMatch> match = resolve("POST", "/orders");

        assertTrue(match.isPresent());
        assertEquals("flw_orders_create", match.get().resolution().flowKey());
        assertEquals(ORDERS_CREATE_FLOW_ID, match.get().resolution().flowId());
        assertEquals(ORDERS_CREATE_VERSION_ID, match.get().resolution().flowVersionId());
    }

    @Test
    void resolvesPostCheckoutToCheckoutFlow() {
        Optional<RouteMatch> match = resolve("POST", "/checkout");

        assertTrue(match.isPresent());
        assertEquals("flw_checkout", match.get().resolution().flowKey());
        assertEquals(CHECKOUT_FLOW_ID, match.get().resolution().flowId());
        assertEquals(CHECKOUT_VERSION_ID, match.get().resolution().flowVersionId());
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
        Optional<RouteMatch> match = resolve("GET", "/orders/");

        assertTrue(match.isPresent());
        assertEquals("flw_orders_list", match.get().resolution().flowKey());
    }

    @Test
    void ignoresQueryString() {
        Optional<RouteMatch> match = resolve("GET", "/orders?page=2");

        assertTrue(match.isPresent());
        assertEquals("flw_orders_list", match.get().resolution().flowKey());
    }

    @Test
    void resolvesUnmatchedPathToWildcardFlow() {
        Optional<RouteMatch> match = resolve("GET", "/app/dashboard/settings");

        assertTrue(match.isPresent());
        assertEquals("flw_app", match.get().resolution().flowKey());
        assertEquals(APP_FLOW_ID, match.get().resolution().flowId());
    }

    @Test
    void prefersExactRouteOverWildcardFlow() {
        Optional<RouteMatch> match = resolve("GET", "/orders");

        assertTrue(match.isPresent());
        assertEquals("flw_orders_list", match.get().resolution().flowKey());
    }

    @Test
    void prefersMoreSpecificWildcardOverBroaderOne() {
        Optional<RouteMatch> match = resolve("GET", "/app/admin/users");

        assertTrue(match.isPresent());
        assertEquals("flw_app_admin", match.get().resolution().flowKey());
    }

    @Test
    void wildcardDoesNotMatchUnrelatedPathWithSamePrefixText() {
        assertTrue(resolve("GET", "/apple/foo").isEmpty());
    }

    @Test
    void wildcardMatchesItsOwnRootWithATrailingSlash() {
        Optional<RouteMatch> match = resolve("GET", "/app/");

        assertTrue(match.isPresent());
        assertEquals("flw_app", match.get().resolution().flowKey());
    }

    @Test
    void wildcardMatchesItsOwnRootWithNoTrailingSlash() {
        // RouteKey.normalizePath() always strips a bare trailing slash, so
        // both "/app/" and "/app" arrive at PrefixRoute.matches() as "/app" -
        // this is the exact case that regressed serving a static site's own
        // root URL (see StaticFileResolver/GatewayHttpHandler).
        Optional<RouteMatch> match = resolve("GET", "/app");

        assertTrue(match.isPresent());
        assertEquals("flw_app", match.get().resolution().flowKey());
    }

    @Test
    void resolvesParamRouteAndCapturesTheValue() {
        Optional<RouteMatch> match = resolve("GET", "/api/todos/42");

        assertTrue(match.isPresent());
        assertEquals("flw_todo_get", match.get().resolution().flowKey());
        assertEquals(Map.of("id", "42"), match.get().pathParameters());
    }

    @Test
    void paramRouteRejectsWrongMethod() {
        assertTrue(resolve("DELETE", "/api/todos/42").isEmpty());
    }

    @Test
    void paramRouteRejectsWrongSegmentCount() {
        assertTrue(resolve("GET", "/api/todos/42/extra").isEmpty());
        assertTrue(resolve("GET", "/api/todos").isEmpty());
    }

    @Test
    void prefersExactRouteOverParamRoute() {
        // "/orders" is registered as an exact route; a param route could
        // never structurally collide with it here (different segment
        // shapes), but this pins the documented precedence regardless.
        Optional<RouteMatch> match = resolve("GET", "/orders");

        assertTrue(match.isPresent());
        assertTrue(match.get().pathParameters().isEmpty());
    }

    @Test
    void prefersMoreLiteralParamRouteOverLessSpecificOne() {
        // Both "/api/todos/:id" and "/api/:resource/:id" match "/api/todos/5"
        // by segment count - the one with more literal segments wins.
        Optional<RouteMatch> match = resolve("GET", "/api/todos/5");

        assertTrue(match.isPresent());
        assertEquals("flw_todo_get", match.get().resolution().flowKey());
        assertEquals(Map.of("id", "5"), match.get().pathParameters());
    }

    @Test
    void fallsBackToLessSpecificParamRouteForADifferentResource() {
        Optional<RouteMatch> match = resolve("GET", "/api/users/7");

        assertTrue(match.isPresent());
        assertEquals("flw_generic_resource", match.get().resolution().flowKey());
        assertEquals(Map.of("resource", "users", "id", "7"), match.get().pathParameters());
    }

    @Test
    void rejectsGatewayWithoutRoutes() {
        GatewayRuntimeEntry otherGateway = new GatewayRuntimeEntry(
                UUID.randomUUID(), "Other", "other", "funchole.test", "other.funchole.test", null, null);

        assertTrue(resolver.resolve(otherGateway, new GatewayRequestContext("GET", "other.funchole.test", "/orders", "/orders")).isEmpty());
    }

    private Optional<RouteMatch> resolve(String method, String path) {
        return resolver.resolve(gateway, new GatewayRequestContext(method, gateway.hostname(), path, path));
    }
}
