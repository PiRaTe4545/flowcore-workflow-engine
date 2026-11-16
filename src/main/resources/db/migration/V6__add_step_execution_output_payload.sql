ALTER TABLE workflow_step_executions
ADD COLUMN output_payload JSONB NOT NULL DEFAULT '{}'::jsonb;