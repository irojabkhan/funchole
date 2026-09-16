package com.funchole.backend.dispatcher;

import java.util.Map;
import java.util.UUID;

public interface FunctionVersionEnvironmentResolver {

    Map<String, String> resolve(UUID functionVersionId);

    default Map<String, String> resolve(InvocationStepExecution stepExecution) {
        return resolve(stepExecution.componentVersionId());
    }
}
