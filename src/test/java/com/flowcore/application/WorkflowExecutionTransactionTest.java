
package com.flowcore.application;

import com.flowcore.definition.model.RetryPolicy;
import com.flowcore.definition.model.StepType;
import com.flowcore.definition.model.WorkflowDefinition;
import com.flowcore.definition.model.WorkflowStepDefinition;
import com.flowcore.domain.WorkflowEventType;
import com.flowcore.domain.WorkflowExecutionEvent;
import com.flowcore.domain.WorkflowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.flowcore.engine.StepExecutionEngine;
import com.flowcore.domain.StepExecutionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private StepExecutionRepository stepExecutionRepository;

    @Autowired
    private WorkflowDefinitionRepository definitionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StepExecutionEngine stepExecutionEngine;

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

    @Test
    void shouldClaimFirstStepAndPersistClaimToDatabase() {
        // Create workflow and its step executions.
        PersistedWorkflowExecution created =
                workflowExecutionService.create(
                        "ORDER_PROCESSING",
                        1,
                        Map.of("orderId", "order-claim-123")
                );

        var workflowId =
                created.workflowExecution().getWorkflowId();

        // Start the workflow.
        workflowExecutionService.start(workflowId);

        // Claim the next eligible step.
        var claimed = stepExecutionEngine
                .claimNextStep(workflowId);

        assertThat(claimed).isPresent();

        assertThat(claimed.get().stepExecution().getStepName())
                .isEqualTo("validate-order");

        assertThat(claimed.get().stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.RUNNING);

        assertThat(claimed.get().stepExecution().getAttemptCount())
                .isEqualTo(1);

        assertThat(claimed.get().version())
                .isEqualTo(1);

        // Reload from PostgreSQL to verify durable state.
        var persistedSteps = stepExecutionRepository
                .findByWorkflowId(workflowId);

        assertThat(persistedSteps).hasSize(2);

        assertThat(persistedSteps.get(0).stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.RUNNING);

        assertThat(persistedSteps.get(0).version())
                .isEqualTo(1);

        assertThat(persistedSteps.get(1).stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.PENDING);

        assertThat(persistedSteps.get(1).version())
                .isZero();
    }

    @Test
    void shouldClaimSecondStepOnlyAfterFirstStepCompletes() {
        PersistedWorkflowExecution created =
                workflowExecutionService.create(
                        "ORDER_PROCESSING",
                        1,
                        Map.of("orderId", "order-sequential-123")
                );

        var workflowId =
                created.workflowExecution().getWorkflowId();

        workflowExecutionService.start(workflowId);

        var firstClaim = stepExecutionEngine
                .claimNextStep(workflowId);

        assertThat(firstClaim).isPresent();
        assertThat(firstClaim.get().stepExecution().getStepName())
                .isEqualTo("validate-order");

        // The first step is still running.
        assertThat(stepExecutionEngine.claimNextStep(workflowId))
                .isEmpty();

        // Complete the first step using its persisted state.
        var firstStep = stepExecutionRepository
                .findById(firstClaim.get().stepExecution().getStepExecutionId())
                .orElseThrow();

        firstStep.stepExecution().complete();
        stepExecutionRepository.update(firstStep);

        // The second step can now be claimed.
        var secondClaim = stepExecutionEngine
                .claimNextStep(workflowId);

        assertThat(secondClaim).isPresent();
        assertThat(secondClaim.get().stepExecution().getStepName())
                .isEqualTo("wait-for-payment");

        assertThat(secondClaim.get().stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.RUNNING);
    }

    @Test
    void shouldCompleteWorkflowWhenAllStepsAreCompleted() {
        PersistedWorkflowExecution created =
                workflowExecutionService.create(
                        "ORDER_PROCESSING",
                        1,
                        Map.of("orderId", "order-complete-123")
                );

        var workflowId =
                created.workflowExecution().getWorkflowId();

        workflowExecutionService.start(workflowId);

        // Claim and complete the first step.
        var firstClaim = stepExecutionEngine
                .claimNextStep(workflowId)
                .orElseThrow();

        firstClaim.stepExecution().complete();
        stepExecutionRepository.update(firstClaim);

        // Claim and complete the second step.
        var secondClaim = stepExecutionEngine
                .claimNextStep(workflowId)
                .orElseThrow();

        secondClaim.stepExecution().complete();
        stepExecutionRepository.update(secondClaim);

        // No steps remain. The engine should complete the workflow.
        var result = stepExecutionEngine.claimNextStep(workflowId);

        assertThat(result).isEmpty();

        var completed = workflowExecutionService.getById(workflowId);

        assertThat(completed.workflowExecution().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);

        assertThat(completed.version()).isEqualTo(2);

        // Verify the completion event was appended.
        Mockito.verify(eventRepository).append(
                Mockito.argThat(event ->
                        event.workflowId().equals(workflowId)
                                && event.eventType() == WorkflowEventType.COMPLETED
                                && event.newStatus() == WorkflowStatus.COMPLETED
                )
        );
    }

    @Test
    void shouldRollbackWorkflowCompletionWhenEventPersistenceFails() {
        PersistedWorkflowExecution created =
                workflowExecutionService.create(
                        "ORDER_PROCESSING",
                        1,
                        Map.of("orderId", "order-completion-rollback")
                );

        var workflowId =
                created.workflowExecution().getWorkflowId();

        workflowExecutionService.start(workflowId);

        var first = stepExecutionEngine.claimNextStep(workflowId)
                .orElseThrow();

        first.stepExecution().complete();
        stepExecutionRepository.update(first);

        var second = stepExecutionEngine.claimNextStep(workflowId)
                .orElseThrow();

        second.stepExecution().complete();
        stepExecutionRepository.update(second);

        Mockito.doThrow(new IllegalStateException("Event persistence failed"))
                .when(eventRepository)
                .append(Mockito.argThat(event ->
                        event.eventType() == WorkflowEventType.COMPLETED
                ));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> stepExecutionEngine.claimNextStep(workflowId)
                ).isInstanceOf(IllegalStateException.class)
                .hasMessage("Event persistence failed");

        var persisted = workflowExecutionService.getById(workflowId);

        assertThat(persisted.workflowExecution().getStatus())
                .isEqualTo(WorkflowStatus.RUNNING);

        assertThat(persisted.version()).isEqualTo(1);
    }

    @Test
    void shouldRollbackStepAndWorkflowFailureWhenEventPersistenceFails() {
        PersistedWorkflowExecution created =
                workflowExecutionService.create(
                        "ORDER_PROCESSING",
                        1,
                        Map.of("orderId", "order-failure-rollback")
                );

        var workflowId = created.workflowExecution().getWorkflowId();

        workflowExecutionService.start(workflowId);

        var claimed = stepExecutionEngine.claimNextStep(workflowId)
                .orElseThrow();

        // Force the workflow FAILED event to fail.
        Mockito.doThrow(new IllegalStateException("Event persistence failed"))
                .when(eventRepository)
                .append(Mockito.argThat(event ->
                        event.eventType() == WorkflowEventType.FAILED
                ));

        assertThatThrownBy(() ->
                stepExecutionEngine.handleStepFailure(
                        workflowId,
                        claimed.stepExecution().getStepExecutionId(),
                        "Permanent service failure"
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Event persistence failed");

        // The step update must have rolled back.
        var persistedStep = stepExecutionRepository
                .findById(claimed.stepExecution().getStepExecutionId())
                .orElseThrow();

        assertThat(persistedStep.stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.RUNNING);

        assertThat(persistedStep.stepExecution().getLastError())
                .isNull();

        // The workflow update must also have rolled back.
        var persistedWorkflow = workflowExecutionService.getById(workflowId);

        assertThat(persistedWorkflow.workflowExecution().getStatus())
                .isEqualTo(WorkflowStatus.RUNNING);

        assertThat(persistedWorkflow.version()).isEqualTo(1);
    }

    @Test
    void shouldPersistStepAndWorkflowFailureTogether() {
        PersistedWorkflowExecution created =
                workflowExecutionService.create(
                        "ORDER_PROCESSING",
                        1,
                        Map.of("orderId", "order-permanent-failure")
                );

        var workflowId = created.workflowExecution().getWorkflowId();

        workflowExecutionService.start(workflowId);

        var claimed = stepExecutionEngine.claimNextStep(workflowId)
                .orElseThrow();

        var failedStep = stepExecutionEngine.handleStepFailure(
                workflowId,
                claimed.stepExecution().getStepExecutionId(),
                "Permanent service failure"
        );

        // Verify the step failure was persisted.
        var persistedStep = stepExecutionRepository
                .findById(failedStep.stepExecution().getStepExecutionId())
                .orElseThrow();

        assertThat(persistedStep.stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.FAILED);

        assertThat(persistedStep.stepExecution().getAttemptCount())
                .isEqualTo(1);

        assertThat(persistedStep.stepExecution().getLastError())
                .isEqualTo("Permanent service failure");

        assertThat(persistedStep.stepExecution().getNextRetryAt())
                .isNull();

        // Verify the workflow failure was persisted.
        var persistedWorkflow = workflowExecutionService.getById(workflowId);

        assertThat(persistedWorkflow.workflowExecution().getStatus())
                .isEqualTo(WorkflowStatus.FAILED);

        assertThat(persistedWorkflow.version()).isEqualTo(2);

        // Verify the FAILED event was emitted.
        Mockito.verify(eventRepository).append(Mockito.argThat(event ->
                event.workflowId().equals(workflowId)
                        && event.eventType() == WorkflowEventType.FAILED
        ));
    }

    @Test
    void shouldPersistStepRetryScheduleAndKeepWorkflowRunning() {
        String workflowType = "RETRY_PROCESSING";

        if (definitionRepository
                .findByTypeAndVersion(workflowType, 1)
                .isEmpty()) {

            definitionRepository.register(
                    new WorkflowDefinition(
                            workflowType,
                            1,
                            List.of(
                                    new WorkflowStepDefinition(
                                            "retryable-step",
                                            StepType.SERVICE_TASK,
                                            new RetryPolicy(
                                                    2,
                                                    Duration.ofSeconds(5),
                                                    2.0,
                                                    Duration.ofSeconds(30)
                                            ),
                                            null
                                    )
                            )
                    )
            );
        }

        PersistedWorkflowExecution created =
                workflowExecutionService.create(
                        workflowType,
                        1,
                        Map.of("orderId", "order-retry")
                );

        var workflowId = created.workflowExecution().getWorkflowId();

        workflowExecutionService.start(workflowId);

        var claimed = stepExecutionEngine.claimNextStep(workflowId)
                .orElseThrow();

        var failedStep = stepExecutionEngine.handleStepFailure(
                workflowId,
                claimed.stepExecution().getStepExecutionId(),
                "Temporary service failure"
        );

        var persistedStep = stepExecutionRepository
                .findById(failedStep.stepExecution().getStepExecutionId())
                .orElseThrow();

        assertThat(persistedStep.stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.PENDING);

        assertThat(persistedStep.stepExecution().getAttemptCount())
                .isEqualTo(1);

        assertThat(persistedStep.stepExecution().getLastError())
                .isEqualTo("Temporary service failure");

        assertThat(persistedStep.stepExecution().getNextRetryAt())
                .isNotNull()
                .isAfter(Instant.now());

        // Retry scheduling must not fail the workflow.
        var persistedWorkflow = workflowExecutionService.getById(workflowId);

        assertThat(persistedWorkflow.workflowExecution().getStatus())
                .isEqualTo(WorkflowStatus.RUNNING);

        assertThat(persistedWorkflow.version()).isEqualTo(1);
    }
}
