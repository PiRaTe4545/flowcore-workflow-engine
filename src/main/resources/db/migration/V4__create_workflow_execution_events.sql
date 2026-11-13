
CREATE TABLE workflow_execution_events (
    event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    workflow_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,

    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,

    execution_version BIGINT NOT NULL,

    event_payload JSONB NOT NULL DEFAULT '{}'::jsonb,

    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_workflow_execution_events_execution
        FOREIGN KEY (workflow_id)
        REFERENCES workflow_executions (workflow_id),

    CONSTRAINT uq_workflow_execution_events_version
        UNIQUE (workflow_id, execution_version),

    CONSTRAINT chk_workflow_execution_events_type
        CHECK (
            event_type IN (
                'CREATED',
                'STARTED',
                'COMPLETED',
                'FAILED',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_workflow_execution_events_previous_status
        CHECK (
            previous_status IS NULL OR previous_status IN (
                'PENDING',
                'RUNNING',
                'COMPLETED',
                'FAILED',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_workflow_execution_events_new_status
        CHECK (
            new_status IN (
                'PENDING',
                'RUNNING',
                'COMPLETED',
                'FAILED',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_workflow_execution_events_version
        CHECK (execution_version >= 0),

    CONSTRAINT chk_workflow_execution_events_payload
        CHECK (JSONB_TYPEOF(event_payload) = 'object')
);

CREATE INDEX idx_workflow_execution_events_history
    ON workflow_execution_events (workflow_id, execution_version);
