package com.funchole.backend.dispatcher;

import java.util.Map;
import java.util.UUID;

public interface FunctionVersionEnvironmentResolver {

    Map<String, String> resolve(UUID functionVersionId);
}
