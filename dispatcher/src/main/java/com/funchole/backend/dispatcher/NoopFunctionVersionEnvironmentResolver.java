package com.funchole.backend.dispatcher;

import java.util.Map;
import java.util.UUID;

public final class NoopFunctionVersionEnvironmentResolver implements FunctionVersionEnvironmentResolver {

    @Override
    public Map<String, String> resolve(UUID functionVersionId) {
        return Map.of();
    }
}
