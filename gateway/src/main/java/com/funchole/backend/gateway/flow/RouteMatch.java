package com.funchole.backend.gateway.flow;

import java.util.Map;

/**
 * A resolved route plus whatever path-parameter values were captured to
 * reach it - empty for an exact or wildcard match, populated for a
 * {@link ParamRoute} match (e.g. {@code {"id": "42"}} for
 * {@code /api/todos/:id} against a request for {@code /api/todos/42}).
 */
public record RouteMatch(FlowResolution resolution, Map<String, String> pathParameters) {

    public RouteMatch {
        pathParameters = Map.copyOf(pathParameters);
    }
}
