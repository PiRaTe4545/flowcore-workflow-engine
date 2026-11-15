CREATE TABLE workflow_step_executions (
    step_execution_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    workflow_id UUID NOT NULL,
    step_name VARCHAR(150) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    attempt_count INTEGER NOT NULL DEFAULT 0,

    next_retry_at TIMESTAMPTZ,
    timeout_at TIMESTAMPTZ,

    last_error TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_step_executions_workflow
        FOREIGN KEY (workflow_id)
        REFERENCES workflow_executions (workflow_id),

    CONSTRAINT uq_workflow_step_name
        UNIQUE (workflow_id, step_name),

    CONSTRAINT chk_step_execution_name
        CHECK (LENGTH(TRIM(step_name)) > 0),

    CONSTRAINT chk_step_execution_status
        CHECK (status IN (
            'PENDING',
            'RUNNING',
            'COMPLETED',
            'FAILED',
            'TIMED_OUT'
        )),

    CONSTRAINT chk_step_execution_attempt_count
        CHECK (attempt_count >= 0),

    CONSTRAINT chk_step_execution_version
        CHECK (version >= 0),

    CONSTRAINT chk_step_execution_timestamps
        CHECK (updated_at >= created_at)
);

CREATE INDEX idx_step_executions_pending_retry
    ON workflow_step_executions (next_retry_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_step_executions_running_timeout
    ON workflow_step_executions (timeout_at)
    WHERE status = 'RUNNING';