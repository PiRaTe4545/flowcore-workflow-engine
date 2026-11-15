
package com.flowcore.application;

import com.flowcore.domain.WorkflowExecutionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class WorkflowExecutionTransactionTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private WorkflowExecutionService workflowExecutionService;

    @Autowired
    private WorkflowDefinitionRepository definitionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private WorkflowExecutionEventRepository eventRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM workflow_execution_events");
        jdbcTemplate.update("DELETE FROM workflow_step_executions");
        jdbcTemplate.update("DELETE FROM workflow_executions");

        if (definitionRepository
                .findByTypeAndVersion("ORDER_PROCESSING", 1)
                .isEmpty()) {

            definitionRepository.register(
                    new com.flowcore.definition.model.WorkflowDefinition(
                            "ORDER_PROCESSING",
                            1,
                            java.util.List.of(
                                    new com.flowcore.definition.model.WorkflowStepDefinition(
                                            "validate-order",
                                            com.flowcore.definition.model.StepType.SERVICE_TASK
                                    ),
                                    new com.flowcore.definition.model.WorkflowStepDefinition(
                                            "wait-for-payment",
                                            com.flowcore.definition.model.StepType.WAIT
                                    )
                            )
                    )
            );
        }
    }

    @Test
    void shouldRollbackExecutionWhenEventPersistenceFails() {
        when(eventRepository.append(any(WorkflowExecutionEvent.class)))
                .thenThrow(new IllegalStateException(
                        "Simulated event persistence failure"
                ));

        assertThatThrownBy(() ->
                workflowExecutionService.create(
                        "ORDER_PROCESSING",
                        1,
                        Map.of("orderId", "order-123")
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated event persistence failure");

        Integer executionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM workflow_executions
                WHERE workflow_type = ?
                """,
                Integer.class,
                "ORDER_PROCESSING"
        );

        assertThat(executionCount).isZero();
        Integer stepCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM workflow_step_executions
                """,
                Integer.class
        );

        assertThat(stepCount).isZero();
    }

    @Test
    void shouldRollbackWorkflowTransitionWhenEventPersistenceFails() {
        WorkflowExecutionService workflowExecutionService =
                this.workflowExecutionService;

        PersistedWorkflowExecution created = workflowExecutionService.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-rollback-123")
        );

        Mockito.reset(eventRepository);

        Mockito.doThrow(new IllegalStateException("Event persistence failed"))
                .when(eventRepository)
                .append(Mockito.any(WorkflowExecutionEvent.class));

        assertThrows(
                IllegalStateException.class,
                () -> workflowExecutionService.start(
                        created.workflowExecution().getWorkflowId()
                )
        );

        Map<String, Object> result = jdbcTemplate.queryForMap(
                """
                SELECT status, version
                FROM workflow_executions
                WHERE workflow_id = ?
                """,
                created.workflowExecution().getWorkflowId()
        );

        assertEquals("PENDING", result.get("status"));
        assertEquals(0L, ((Number) result.get("version")).longValue());
    }


    @Test
    void shouldPersistWorkflowAndStepExecutionsTogether() {
        PersistedWorkflowExecution created = workflowExecutionService.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-success-123")
        );

        Integer executionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM workflow_executions
                WHERE workflow_id = ?
                """,
                Integer.class,
                created.workflowExecution().getWorkflowId()
        );

        Integer stepCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM workflow_step_executions
                WHERE workflow_id = ?
                """,
                Integer.class,
                created.workflowExecution().getWorkflowId()
        );

        assertThat(executionCount).isEqualTo(1);
        assertThat(stepCount).isEqualTo(2);

        java.util.List<String> stepNames = jdbcTemplate.queryForList(
                """
                SELECT step_name
                FROM workflow_step_executions
                WHERE workflow_id = ?
                ORDER BY created_at ASC, step_execution_id ASC
                """,
                String.class,
                created.workflowExecution().getWorkflowId()
        );

        assertThat(stepNames).containsExactly(
                "validate-order",
                "wait-for-payment"
        );

        Mockito.verify(eventRepository).append(
                Mockito.argThat(event ->
                        event.eventType() ==
                                com.flowcore.domain.WorkflowEventType.CREATED
                )
        );
    }
}
