CREATE TABLE environment_profiles (
    id UUID PRIMARY KEY,
    app_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE ON UPDATE CASCADE,
    environment_key VARCHAR(150) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_environment_profiles_user_key UNIQUE (app_user_id, environment_key)
);

CREATE TABLE environment_profile_env_vars (
    id UUID PRIMARY KEY,
    environment_profile_id UUID NOT NULL REFERENCES environment_profiles(id) ON DELETE CASCADE ON UPDATE CASCADE,
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_environment_profile_env_vars_profile_key UNIQUE (environment_profile_id, config_key)
);

CREATE TABLE environment_profile_secrets (
    id UUID PRIMARY KEY,
    environment_profile_id UUID NOT NULL REFERENCES environment_profiles(id) ON DELETE CASCADE ON UPDATE CASCADE,
    config_key VARCHAR(255) NOT NULL,
    secret_ref VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_environment_profile_secrets_profile_key UNIQUE (environment_profile_id, config_key)
);

CREATE TABLE flow_environment_attachments (
    id UUID PRIMARY KEY,
    flow_id UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE ON UPDATE CASCADE,
    environment_profile_id UUID NOT NULL REFERENCES environment_profiles(id) ON DELETE CASCADE ON UPDATE CASCADE,
    priority INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_flow_environment_attachments UNIQUE (flow_id, environment_profile_id)
);

CREATE TABLE flow_database_attachments (
    id UUID PRIMARY KEY,
    flow_id UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE ON UPDATE CASCADE,
    database_id UUID NOT NULL REFERENCES databases(id) ON DELETE CASCADE ON UPDATE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_flow_database_attachments UNIQUE (flow_id, database_id)
);
