CREATE TABLE acme_challenges (
    token VARCHAR(255) PRIMARY KEY,
    key_authorization TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
