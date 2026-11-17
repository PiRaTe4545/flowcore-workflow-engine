
ALTER TABLE workflow_step_executions
    ADD COLUMN lease_owner VARCHAR(255),
    ADD COLUMN lease_expires_at TIMESTAMPTZ,
    ADD COLUMN lease_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE workflow_step_executions
    ADD CONSTRAINT chk_step_execution_lease_version
    CHECK (lease_version >= 0);

ALTER TABLE workflow_step_executions
    ADD CONSTRAINT chk_step_execution_lease_pair
    CHECK (
        (lease_owner IS NULL AND lease_expires_at IS NULL)
        OR
        (lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
    );
