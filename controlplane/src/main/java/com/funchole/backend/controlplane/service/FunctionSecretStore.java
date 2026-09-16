package com.funchole.backend.controlplane.service;

import java.util.UUID;

public interface FunctionSecretStore {

    String save(UUID functionVersionId, String key, String value);

    String saveForDatabase(UUID databaseId, String key, String value);
}
