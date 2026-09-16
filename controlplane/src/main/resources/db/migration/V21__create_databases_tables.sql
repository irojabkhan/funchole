CREATE TABLE databases (
    id UUID PRIMARY KEY,
    app_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE ON UPDATE CASCADE,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(50) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    database_name VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password_secret_ref VARCHAR(2048) NOT NULL,
    ssl_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_databases_app_user_name UNIQUE (app_user_id, name)
);

CREATE TABLE function_version_database_attachments (
    id UUID PRIMARY KEY,
    function_version_id UUID NOT NULL REFERENCES function_versions(id) ON DELETE CASCADE ON UPDATE CASCADE,
    database_id UUID NOT NULL REFERENCES databases(id) ON DELETE CASCADE ON UPDATE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_function_version_database_attachments UNIQUE (function_version_id, database_id)
);
