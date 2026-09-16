package com.funchole.backend.dispatcher;

import java.util.List;
import java.util.UUID;

public final class NoopFunctionVersionDatabaseResolver implements FunctionVersionDatabaseResolver {

    @Override
    public List<DatabaseConnectionInfo> resolve(UUID functionVersionId) {
        return List.of();
    }
}
