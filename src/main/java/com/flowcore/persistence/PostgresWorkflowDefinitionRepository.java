package com.flowcore.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcore.application.WorkflowDefinitionRepository;
import com.flowcore.definition.model.WorkflowDefinition;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PostgresWorkflowDefinitionRepository
        implements WorkflowDefinitionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresWorkflowDefinitionRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkflowDefinition register(WorkflowDefinition definition) {
        try {
            String payload = objectMapper.writeValueAsString(definition);

            jdbcTemplate.update("""
                    INSERT INTO workflow_definitions (
                        workflow_type,
                        version,
                        definition_payload
                    )
                    VALUES (?, ?, ?::jsonb)
                    """,
                    definition.workflowType(),
                    definition.version(),
                    payload
            );

            return definition;

        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize workflow definition",
                    exception
            );
        }
    }

    @Override
    public Optional<WorkflowDefinition> findByTypeAndVersion(
            String workflowType,
            int version
    ) {
        return jdbcTemplate.query("""
                SELECT definition_payload
                FROM workflow_definitions
                WHERE workflow_type = ?
                  AND version = ?
                """,
                resultSet -> {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }

                    String payload = resultSet.getString("definition_payload");

                    try {
                        return Optional.of(
                                objectMapper.readValue(
                                        payload,
                                        WorkflowDefinition.class
                                )
                        );
                    } catch (JsonProcessingException exception) {
                        throw new IllegalStateException(
                                "Unable to deserialize workflow definition",
                                exception
                        );
                    }
                },
                workflowType,
                version
        );
    }
}