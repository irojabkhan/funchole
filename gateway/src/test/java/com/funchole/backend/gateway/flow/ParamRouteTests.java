package com.funchole.backend.gateway.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParamRouteTests {

    private static final FlowResolution RESOLUTION =
            new FlowResolution(UUID.randomUUID(), "flw_test", UUID.randomUUID());

    @Test
    void templateSegmentsIsEmptyForAPathWithNoParamSegment() {
        assertTrue(ParamRoute.templateSegments("/api/todos").isEmpty());
        assertTrue(ParamRoute.templateSegments("/api/todos/*").isEmpty());
    }

    @Test
    void templateSegmentsSplitsAPathWithAParamSegment() {
        Optional<List<String>> segments = ParamRoute.templateSegments("/api/todos/:id");

        assertTrue(segments.isPresent());
        assertEquals(List.of("api", "todos", ":id"), segments.get());
    }

    @Test
    void matchesAndCapturesASingleParam() {
        ParamRoute route = new ParamRoute("GET", List.of("api", "todos", ":id"), RESOLUTION);

        Optional<Map<String, String>> captured = route.match("GET", "/api/todos/42");

        assertTrue(captured.isPresent());
        assertEquals(Map.of("id", "42"), captured.get());
    }

    @Test
    void matchesAndCapturesMultipleParams() {
        ParamRoute route = new ParamRoute("GET", List.of("api", ":resource", ":id"), RESOLUTION);

        Optional<Map<String, String>> captured = route.match("GET", "/api/users/7");

        assertTrue(captured.isPresent());
        assertEquals(Map.of("resource", "users", "id", "7"), captured.get());
    }

    @Test
    void rejectsAWrongMethod() {
        ParamRoute route = new ParamRoute("GET", List.of("api", "todos", ":id"), RESOLUTION);

        assertTrue(route.match("DELETE", "/api/todos/42").isEmpty());
    }

    @Test
    void rejectsAMismatchedLiteralSegment() {
        ParamRoute route = new ParamRoute("GET", List.of("api", "todos", ":id"), RESOLUTION);

        assertTrue(route.match("GET", "/api/orders/42").isEmpty());
    }

    @Test
    void rejectsAWrongSegmentCount() {
        ParamRoute route = new ParamRoute("GET", List.of("api", "todos", ":id"), RESOLUTION);

        assertTrue(route.match("GET", "/api/todos/42/extra").isEmpty());
        assertTrue(route.match("GET", "/api/todos").isEmpty());
    }

    @Test
    void literalSegmentCountCountsOnlyNonParamSegments() {
        ParamRoute specific = new ParamRoute("GET", List.of("api", "todos", ":id"), RESOLUTION);
        ParamRoute generic = new ParamRoute("GET", List.of("api", ":resource", ":id"), RESOLUTION);

        assertEquals(2, specific.literalSegmentCount());
        assertEquals(1, generic.literalSegmentCount());
    }
}
