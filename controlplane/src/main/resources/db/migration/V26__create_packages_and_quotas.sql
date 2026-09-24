-- Cloud-only package/quota system (see PackageLimitService). Every table
-- here exists in every deployment, including self-hosted ones, but stays
-- empty/unused unless CLOUD_MODE_ENABLED=true - PackageLimitService.enforce
-- short-circuits to a no-op before ever querying them.

CREATE TABLE packages (
    id UUID PRIMARY KEY,
    key VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_packages_key UNIQUE (key)
);

-- The actual "change it from the database" lever: one row per limit per
-- package. limit_key is a plain string, not a DB enum, so an operator can
-- insert a row for a limit type that doesn't have enforcement code yet.
-- NULL limit_value means unlimited.
CREATE TABLE package_limits (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES packages(id) ON DELETE CASCADE ON UPDATE CASCADE,
    limit_key VARCHAR(100) NOT NULL,
    limit_value INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_package_limits_package_key UNIQUE (package_id, limit_key)
);

-- One active package per user.
CREATE TABLE user_packages (
    id UUID PRIMARY KEY,
    app_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE ON UPDATE CASCADE,
    package_id UUID NOT NULL REFERENCES packages(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_packages_app_user UNIQUE (app_user_id)
);

-- Per-user, per-limit exceptions on top of the assigned package - e.g.
-- "give this one user +1 gateway" without creating a whole new package
-- tier. Resolution order (see PackageLimitService): override -> package's
-- own limit -> unlimited.
CREATE TABLE user_package_overrides (
    id UUID PRIMARY KEY,
    app_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE ON UPDATE CASCADE,
    limit_key VARCHAR(100) NOT NULL,
    limit_value INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_package_overrides_user_key UNIQUE (app_user_id, limit_key)
);

CREATE INDEX idx_package_limits_package_id ON package_limits (package_id);
CREATE INDEX idx_user_packages_package_id ON user_packages (package_id);
CREATE INDEX idx_user_package_overrides_app_user_id ON user_package_overrides (app_user_id);

INSERT INTO packages (id, key, name, description)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    'free',
    'Free',
    'Default package assigned to every self-registered cloud user.'
)
ON CONFLICT (key) DO NOTHING;

INSERT INTO package_limits (id, package_id, limit_key, limit_value)
VALUES
    ('33333333-3333-3333-3333-333333333334', '33333333-3333-3333-3333-333333333333', 'MAX_GATEWAYS', 1),
    ('33333333-3333-3333-3333-333333333335', '33333333-3333-3333-3333-333333333333', 'MAX_FLOWS', 20),
    ('33333333-3333-3333-3333-333333333336', '33333333-3333-3333-3333-333333333333', 'MAX_FUNCTIONS', 100),
    ('33333333-3333-3333-3333-333333333337', '33333333-3333-3333-3333-333333333333', 'MAX_DOMAINS', 0)
ON CONFLICT (package_id, limit_key) DO NOTHING;
