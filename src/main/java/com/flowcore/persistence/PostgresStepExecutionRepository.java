
package com.flowcore.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcore.application.PersistedStepExecution;
import com.flowcore.application.StepExecutionRepository;
import com.flowcore.domain.StepExecution;
import com.flowcore.domain.StepExecutionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresStepExecutionRepository
        implements StepExecutionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public PostgresStepExecutionRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // Retained for tests or existing direct construction.
    public PostgresStepExecutionRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new ObjectMapper());
    }

    @Override
    public PersistedStepExecution save(StepExecution step) {
        String sql = """
                INSERT INTO workflow_step_executions (
                    step_execution_id,
                    workflow_id,
                    step_name,
                    status,
                    attempt_count,
                    next_retry_at,
                    timeout_at,
                    last_error,
                    output_payload,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                step.getStepExecutionId(),
                step.getWorkflowId(),
                step.getStepName(),
                step.getStatus().name(),
                step.getAttemptCount(),
                toTimestamp(step.getNextRetryAt()),
                toTimestamp(step.getTimeoutAt()),
                step.getLastError(),
                serializePayload(step.getOutputPayload()),
                toTimestamp(step.getCreatedAt()),
                toTimestamp(step.getUpdatedAt())
        );

        return new PersistedStepExecution(step, 0);
    }

    @Override
    public Optional<PersistedStepExecution> findById(
            UUID stepExecutionId
    ) {
        String sql = """
                SELECT *
                FROM workflow_step_executions
                WHERE step_execution_id = ?
                """;

        List<PersistedStepExecution> results = jdbcTemplate.query(
                sql,
                stepExecutionRowMapper,
                stepExecutionId
        );

        return results.stream().findFirst();
    }

    @Override
    public List<PersistedStepExecution> findByWorkflowId(UUID workflowId) {
        String sql = """
                SELECT *
                FROM workflow_step_executions
                WHERE workflow_id = ?
                ORDER BY created_at ASC, step_execution_id ASC
                """;

        return jdbcTemplate.query(
                sql,
                stepExecutionRowMapper,
                workflowId
        );
    }

    @Override
    public PersistedStepExecution update(
            PersistedStepExecution persisted
    ) {
        StepExecution step = persisted.stepExecution();

        String sql = """
                UPDATE workflow_step_executions
                SET status = ?,
                    attempt_count = ?,
                    next_retry_at = ?,
                    timeout_at = ?,
                    last_error = ?,
                    output_payload = ?::jsonb,
                    updated_at = ?,
                    version = version + 1
                WHERE step_execution_id = ?
                  AND version = ?
                """;

        int updatedRows = jdbcTemplate.update(
                sql,
                step.getStatus().name(),
                step.getAttemptCount(),
                toTimestamp(step.getNextRetryAt()),
                toTimestamp(step.getTimeoutAt()),
                step.getLastError(),
                serializePayload(step.getOutputPayload()),
                toTimestamp(step.getUpdatedAt()),
                step.getStepExecutionId(),
                persisted.version()
        );

        if (updatedRows == 0) {
            throw new OptimisticLockingFailureException(
                    "Step execution was modified by another transaction: "
                            + step.getStepExecutionId()
            );
        }

        return new PersistedStepExecution(
                step,
                persisted.version() + 1
        );
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize step output payload",
                    exception
            );
        }
    }

    private Map<String, Object> deserializePayload(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to deserialize step output payload",
                    exception
            );
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private final RowMapper<PersistedStepExecution> stepExecutionRowMapper =
            (rs, rowNum) -> {
                StepExecution step = StepExecution.restore(
                        rs.getObject("step_execution_id", UUID.class),
                        rs.getObject("workflow_id", UUID.class),
                        rs.getString("step_name"),
                        StepExecutionStatus.valueOf(rs.getString("status")),
                        rs.getInt("attempt_count"),
                        toInstant(rs.getTimestamp("next_retry_at")),
                        toInstant(rs.getTimestamp("timeout_at")),
                        rs.getString("last_error"),
                        deserializePayload(rs.getString("output_payload")),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("updated_at"))
                );

                return new PersistedStepExecution(
                        step,
                        rs.getLong("version")
                );
            };

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
