
package com.flowcore.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcore.application.WorkflowExecutionEventRepository;
import com.flowcore.domain.WorkflowExecutionEvent;
import com.flowcore.domain.WorkflowEventType;
import com.flowcore.domain.WorkflowStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class PostgresWorkflowExecutionEventRepository
        implements WorkflowExecutionEventRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresWorkflowExecutionEventRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkflowExecutionEvent append(WorkflowExecutionEvent event) {
        String sql = """
                INSERT INTO workflow_execution_events (
                    workflow_id,
                    event_type,
                    previous_status,
                    new_status,
                    execution_version,
                    event_payload,
                    occurred_at
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """;

        String payload = serialize(event.eventPayload());

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    sql,
                    new String[]{"event_id"}
            );

            statement.setObject(1, event.workflowId());
            statement.setString(2, event.eventType().name());

            if (event.previousStatus() == null) {
                statement.setNull(3, Types.VARCHAR);
            } else {
                statement.setString(3, event.previousStatus().name());
            }

            statement.setString(4, event.newStatus().name());
            statement.setLong(5, event.executionVersion());
            statement.setString(6, payload);
            statement.setTimestamp(
                    7,
                    Timestamp.from(event.occurredAt())
            );

            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (generatedId == null) {
            throw new IllegalStateException(
                    "Database did not return an event ID"
            );
        }

        return new WorkflowExecutionEvent(
                generatedId.longValue(),
                event.workflowId(),
                event.eventType(),
                event.previousStatus(),
                event.newStatus(),
                event.executionVersion(),
                event.eventPayload(),
                event.occurredAt()
        );
    }

    @Override
    public List<WorkflowExecutionEvent> findByWorkflowId(UUID workflowId) {
        String sql = """
                SELECT
                    event_id,
                    workflow_id,
                    event_type,
                    previous_status,
                    new_status,
                    execution_version,
                    event_payload,
                    occurred_at
                FROM workflow_execution_events
                WHERE workflow_id = ?
                ORDER BY execution_version ASC
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new WorkflowExecutionEvent(
                        rs.getLong("event_id"),
                        rs.getObject("workflow_id", UUID.class),
                        WorkflowEventType.valueOf(
                                rs.getString("event_type")
                        ),
                        rs.getString("previous_status") == null
                                ? null
                                : WorkflowStatus.valueOf(
                                        rs.getString("previous_status")
                                ),
                        WorkflowStatus.valueOf(
                                rs.getString("new_status")
                        ),
                        rs.getLong("execution_version"),
                        deserialize(rs.getString("event_payload")),
                        rs.getTimestamp("occurred_at").toInstant()
                ),
                workflowId
        );
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize event payload",
                    exception
            );
        }
    }

    private Map<String, Object> deserialize(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to deserialize event payload",
                    exception
            );
        }
    }
}
