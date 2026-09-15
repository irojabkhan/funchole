CREATE TABLE function_version_env_vars (
    id UUID PRIMARY KEY,
    function_version_id UUID NOT NULL REFERENCES function_versions(id) ON DELETE CASCADE ON UPDATE CASCADE,
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_function_version_env_vars_version_key UNIQUE (function_version_id, config_key)
);

CREATE TABLE function_version_secrets (
    id UUID PRIMARY KEY,
    function_version_id UUID NOT NULL REFERENCES function_versions(id) ON DELETE CASCADE ON UPDATE CASCADE,
    config_key VARCHAR(255) NOT NULL,
    secret_ref VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_function_version_secrets_version_key UNIQUE (function_version_id, config_key)
);
