CREATE TABLE workflow_definitions (
    workflow_type VARCHAR(150) NOT NULL,
    version INTEGER NOT NULL,
    definition_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_workflow_definitions
        PRIMARY KEY (workflow_type, version),

    CONSTRAINT chk_workflow_definition_type
        CHECK (LENGTH(TRIM(workflow_type)) > 0),

    CONSTRAINT chk_workflow_definition_version
        CHECK (version > 0),

    CONSTRAINT chk_workflow_definition_payload
        CHECK (JSONB_TYPEOF(definition_payload) = 'object')
);