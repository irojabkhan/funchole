CREATE TABLE function_version_build_logs (
    id UUID PRIMARY KEY,
    function_version_id UUID NOT NULL REFERENCES function_versions(id) ON DELETE CASCADE,
    stage VARCHAR(64) NOT NULL,
    command TEXT NOT NULL,
    exit_code INTEGER,
    succeeded BOOLEAN NOT NULL,
    timed_out BOOLEAN NOT NULL DEFAULT FALSE,
    stdout TEXT,
    stderr TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_function_version_build_logs_version ON function_version_build_logs (function_version_id, created_at);
