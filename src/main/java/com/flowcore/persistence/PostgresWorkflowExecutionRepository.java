
package com.flowcore.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcore.application.PersistedWorkflowExecution;
import com.flowcore.application.WorkflowExecutionRepository;
import com.flowcore.domain.WorkflowExecution;
import com.flowcore.domain.WorkflowStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresWorkflowExecutionRepository
        implements WorkflowExecutionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresWorkflowExecutionRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private static final String INSERT_SQL = """
            INSERT INTO workflow_executions (
                workflow_id,
                workflow_type,
                definition_version,
                status,
                input_payload,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT workflow_id,
                   workflow_type,
                   definition_version,
                   status,
                   input_payload,
                   created_at,
                   updated_at,
                   version
            FROM workflow_executions
            WHERE workflow_id = ?
            """;

    private static final String UPDATE_SQL = """
            UPDATE workflow_executions
            SET status = ?,
                updated_at = ?,
                version = version + 1
            WHERE workflow_id = ?
              AND version = ?
            """;

    @Override
    public PersistedWorkflowExecution save(
            WorkflowExecution workflowExecution
    ) {
        jdbcTemplate.update(
                INSERT_SQL,
                workflowExecution.getWorkflowId(),
                workflowExecution.getWorkflowType(),
                workflowExecution.getDefinitionVersion(),
                workflowExecution.getStatus().name(),
                serializePayload(workflowExecution.getInputPayload()),
                Timestamp.from(workflowExecution.getCreatedAt()),
                Timestamp.from(workflowExecution.getUpdatedAt())
        );

        return new PersistedWorkflowExecution(workflowExecution, 0);
    }

    @Override
    public Optional<PersistedWorkflowExecution> findById(UUID workflowId) {
        return jdbcTemplate.query(
                FIND_BY_ID_SQL,
                workflowExecutionRowMapper(),
                workflowId
        ).stream().findFirst();
    }

    @Override
    public PersistedWorkflowExecution update(
            WorkflowExecution workflowExecution,
            long expectedVersion
    ) {
        int updatedRows = jdbcTemplate.update(
                UPDATE_SQL,
                workflowExecution.getStatus().name(),
                Timestamp.from(workflowExecution.getUpdatedAt()),
                workflowExecution.getWorkflowId(),
                expectedVersion
        );

        if (updatedRows == 0) {
            throw new OptimisticLockingFailureException(
                    "Workflow execution was modified or does not exist: "
                            + workflowExecution.getWorkflowId()
            );
        }

        return new PersistedWorkflowExecution(
                workflowExecution,
                expectedVersion + 1
        );
    }

    private RowMapper<PersistedWorkflowExecution>
    workflowExecutionRowMapper() {

        return (resultSet, rowNumber) -> {

            UUID workflowId = resultSet.getObject(
                    "workflow_id",
                    UUID.class
            );

            String workflowType = resultSet.getString("workflow_type");

            int definitionVersion =
                    resultSet.getInt("definition_version");

            WorkflowStatus status = WorkflowStatus.valueOf(
                    resultSet.getString("status")
            );

            Map<String, Object> inputPayload =
                    deserializePayload(
                            resultSet.getString("input_payload")
                    );

            Instant createdAt = resultSet
                    .getTimestamp("created_at")
                    .toInstant();

            Instant updatedAt = resultSet
                    .getTimestamp("updated_at")
                    .toInstant();

            long version = resultSet.getLong("version");

            WorkflowExecution execution = WorkflowExecution.restore(
                    workflowId,
                    workflowType,
                    definitionVersion,
                    inputPayload,
                    status,
                    createdAt,
                    updatedAt
            );

            return new PersistedWorkflowExecution(
                    execution,
                    version
            );
        };
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize workflow input payload",
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
                    "Unable to deserialize workflow input payload",
                    exception
            );
        }
    }
}
