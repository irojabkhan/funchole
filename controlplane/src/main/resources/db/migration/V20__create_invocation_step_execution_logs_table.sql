CREATE TABLE invocation_step_execution_logs (
    id UUID PRIMARY KEY,
    invocation_step_execution_id UUID NOT NULL REFERENCES invocation_step_executions(id) ON DELETE CASCADE,
    stream VARCHAR(16) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_invocation_step_execution_logs_step_execution ON invocation_step_execution_logs (invocation_step_execution_id, created_at);
