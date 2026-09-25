package com.funchole.backend.controlplane.service;

import java.util.UUID;

public interface FunctionSecretStore {

    String save(UUID functionVersionId, String key, String value);

    String saveForEnvironment(UUID environmentProfileId, String key, String value);

    String saveForDatabase(UUID databaseId, String key, String value);

    /**
     * Reads back a secret value from its ref (as returned by one of the
     * {@code save*} methods above) - used for credentials that must be
     * user-recoverable later, unlike function/environment secrets which are
     * only ever read by the dispatcher/runtime at invocation time.
     */
    String readSecretValue(String secretRef);
}
