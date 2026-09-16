package com.funchole.backend.dispatcher;

import java.util.List;
import java.util.UUID;

public interface FunctionVersionDatabaseResolver {

    List<DatabaseConnectionInfo> resolve(UUID functionVersionId);
}
