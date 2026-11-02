CREATE TABLE workflow_executions (
    workflow_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_type VARCHAR(150) NOT NULL,
    definition_version INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    input_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_workflow_executions_type_not_blank
        CHECK (length(trim(workflow_type)) > 0),
    CONSTRAINT ck_workflow_executions_definition_version_positive
        CHECK (definition_version > 0),
    CONSTRAINT ck_workflow_executions_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_workflow_executions_input_object
        CHECK (jsonb_typeof(input_payload) = 'object'),
    CONSTRAINT ck_workflow_executions_timestamps
        CHECK (updated_at >= created_at)
);

CREATE INDEX idx_workflow_executions_status_created_at
    ON workflow_executions (status, created_at DESC);

CREATE INDEX idx_workflow_executions_type_created_at
    ON workflow_executions (workflow_type, created_at DESC);
