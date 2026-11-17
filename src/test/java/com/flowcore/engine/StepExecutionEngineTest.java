
package com.flowcore.engine;

import com.flowcore.application.*;
import com.flowcore.definition.model.*;
import com.flowcore.domain.StepExecution;
import com.flowcore.domain.StepExecutionStatus;
import com.flowcore.domain.WorkflowExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StepExecutionEngineTest {

    private WorkflowExecutionRepository workflowRepository;
    private WorkflowDefinitionRepository definitionRepository;
    private StepExecutionRepository stepExecutionRepository;
    private WorkflowExecutionService workflowExecutionService;

    private StepExecutionEngine engine;

    private UUID workflowId;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowExecutionRepository.class);
        definitionRepository = mock(WorkflowDefinitionRepository.class);
        stepExecutionRepository = mock(StepExecutionRepository.class);
        workflowExecutionService = mock(WorkflowExecutionService.class);

        engine = new StepExecutionEngine(
                workflowRepository,
                definitionRepository,
                stepExecutionRepository,
                workflowExecutionService
        );

        workflowId = UUID.randomUUID();
    }

    @Test
    void shouldClaimFirstStepInDefinitionOrder() {
        WorkflowDefinition definition = definitionWithTwoSteps();

        WorkflowExecution workflow = runningWorkflow();

        StepExecution first = StepExecution.create(workflowId, "validate-order");
        StepExecution second = StepExecution.create(workflowId, "wait-for-payment");

        stubSuccessfulClaim(
                new PersistedStepExecution(first, 0),
                new PersistedStepExecution(second, 0)
        );

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(new PersistedWorkflowExecution(workflow, 0)));

        when(definitionRepository.findByTypeAndVersion("ORDER_PROCESSING", 1))
                .thenReturn(Optional.of(definition));

        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(
                        new PersistedStepExecution(first, 0),
                        new PersistedStepExecution(second, 0)
                ));

        when(stepExecutionRepository.update(any(PersistedStepExecution.class)))
                .thenAnswer(invocation -> {
                    PersistedStepExecution persisted = invocation.getArgument(0);
                    return new PersistedStepExecution(
                            persisted.stepExecution(),
                            persisted.version() + 1
                    );
                });

        Optional<PersistedStepExecution> result =
                engine.claimNextStep(workflowId);

        assertThat(result).isPresent();
        assertThat(result.get().stepExecution().getStepName())
                .isEqualTo("validate-order");
        assertThat(result.get().stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.RUNNING);
        assertThat(result.get().stepExecution().getAttemptCount())
                .isEqualTo(1);

        verify(stepExecutionRepository).claim(
                eq(first.getStepExecutionId()),
                anyString(),
                isNull(),
                any(Instant.class),
                any(Instant.class)
        );
        verify(stepExecutionRepository).claim(
                eq(first.getStepExecutionId()),
                anyString(),
                isNull(),
                any(Instant.class),
                any(Instant.class)
        );
    }

    @Test
    void shouldRejectStepThatDoesNotBelongToWorkflow() {
        WorkflowExecution workflow = runningWorkflow();

        StepExecution step = StepExecution.create(
                UUID.randomUUID(),
                "validate-order"
        );
        step.start(null);

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 1)
                ));

        when(stepExecutionRepository.findById(step.getStepExecutionId()))
                .thenReturn(Optional.of(
                        new PersistedStepExecution(step, 1)
                ));

        assertThatThrownBy(() ->
                engine.handleStepSuccess(
                        workflowId,
                        step.getStepExecutionId(),
                        Map.of(),
                        "worker-1",
                        0L
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to workflow");

        verify(stepExecutionRepository, never())
                .update(any(PersistedStepExecution.class));
    }

    @Test
    void shouldNotClaimSecondStepWhileFirstStepIsRunning() {
        WorkflowDefinition definition = definitionWithTwoSteps();

        WorkflowExecution workflow = runningWorkflow();

        StepExecution first = StepExecution.create(workflowId, "validate-order");
        first.start(null);

        StepExecution second = StepExecution.create(workflowId, "wait-for-payment");

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(new PersistedWorkflowExecution(workflow, 0)));

        when(definitionRepository.findByTypeAndVersion("ORDER_PROCESSING", 1))
                .thenReturn(Optional.of(definition));

        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(
                        new PersistedStepExecution(first, 1),
                        new PersistedStepExecution(second, 0)
                ));

        Optional<PersistedStepExecution> result =
                engine.claimNextStep(workflowId);

        assertThat(result).isEmpty();

        verify(stepExecutionRepository, never())
                .update(any(PersistedStepExecution.class));
    }

    @Test
    void shouldClaimSecondStepAfterFirstStepCompletes() {
        WorkflowDefinition definition = definitionWithTwoSteps();

        WorkflowExecution workflow = runningWorkflow();

        StepExecution first = StepExecution.create(workflowId, "validate-order");
        first.start(null);
        first.complete();

        StepExecution second = StepExecution.create(workflowId, "wait-for-payment");

        stubSuccessfulClaim(
                new PersistedStepExecution(first, 2),
                new PersistedStepExecution(second, 0)
        );

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(new PersistedWorkflowExecution(workflow, 0)));

        when(definitionRepository.findByTypeAndVersion("ORDER_PROCESSING", 1))
                .thenReturn(Optional.of(definition));

        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(
                        new PersistedStepExecution(first, 2),
                        new PersistedStepExecution(second, 0)
                ));

        when(stepExecutionRepository.update(any(PersistedStepExecution.class)))
                .thenAnswer(invocation -> {
                    PersistedStepExecution persisted = invocation.getArgument(0);
                    return new PersistedStepExecution(
                            persisted.stepExecution(),
                            persisted.version() + 1
                    );
                });

        Optional<PersistedStepExecution> result =
                engine.claimNextStep(workflowId);

        assertThat(result).isPresent();
        assertThat(result.get().stepExecution().getStepName())
                .isEqualTo("wait-for-payment");
        assertThat(result.get().stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.RUNNING);
    }

    @Test
    void shouldClaimStepAfterRetryTime() {
        WorkflowExecution workflow = runningWorkflow();

        StepExecution step = StepExecution.create(workflowId, "validate-order");
        step.start(null);
        step.scheduleRetry(
                Instant.now().minusSeconds(10),
                "Temporary failure"
        );

        stubSuccessfulClaim(
                new PersistedStepExecution(step, 2),
                new PersistedStepExecution(
                        StepExecution.create(workflowId, "wait-for-payment"),
                        0
                )
        );

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(new PersistedWorkflowExecution(workflow, 0)));

        when(definitionRepository.findByTypeAndVersion("ORDER_PROCESSING", 1))
                .thenReturn(Optional.of(definitionWithTwoSteps()));

        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(
                        new PersistedStepExecution(step, 2),
                        new PersistedStepExecution(
                                StepExecution.create(workflowId, "wait-for-payment"),
                                0
                        )
                ));

        when(stepExecutionRepository.update(any(PersistedStepExecution.class)))
                .thenAnswer(invocation -> {
                    PersistedStepExecution persisted = invocation.getArgument(0);
                    return new PersistedStepExecution(
                            persisted.stepExecution(),
                            persisted.version() + 1
                    );
                });

        Optional<PersistedStepExecution> result =
                engine.claimNextStep(workflowId);

        assertThat(result).isPresent();
        assertThat(result.get().stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.RUNNING);
        assertThat(result.get().stepExecution().getAttemptCount())
                .isEqualTo(2);
    }

    @Test
    void shouldNotClaimStepBeforeRetryTime() {
        WorkflowExecution workflow = runningWorkflow();

        StepExecution step = StepExecution.create(workflowId, "validate-order");
        step.start(null);
        step.scheduleRetry(
                Instant.now().plusSeconds(300),
                "Temporary failure"
        );

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(new PersistedWorkflowExecution(workflow, 0)));

        when(definitionRepository.findByTypeAndVersion("ORDER_PROCESSING", 1))
                .thenReturn(Optional.of(definitionWithTwoSteps()));

        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(
                        new PersistedStepExecution(step, 2),
                        new PersistedStepExecution(
                                StepExecution.create(workflowId, "wait-for-payment"),
                                0
                        )
                ));

        Optional<PersistedStepExecution> result =
                engine.claimNextStep(workflowId);

        assertThat(result).isEmpty();

        verify(stepExecutionRepository, never())
                .update(any(PersistedStepExecution.class));
    }

    @Test
    void shouldApplyConfiguredTimeoutWhenClaimingStep() {
        WorkflowExecution workflow = runningWorkflow();

        WorkflowDefinition definition = new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                List.of(
                        new WorkflowStepDefinition(
                                "validate-order",
                                StepType.SERVICE_TASK,
                                "ORDER_VALIDATION",
                                null,
                                new TimeoutPolicy(Duration.ofMinutes(2))
                        ),
                        new WorkflowStepDefinition(
                                "wait-for-payment",
                                StepType.WAIT
                        )
                )
        );

        StepExecution step = StepExecution.create(workflowId, "validate-order");

        stubSuccessfulClaim(
                new PersistedStepExecution(step, 0),
                new PersistedStepExecution(
                        StepExecution.create(workflowId, "wait-for-payment"),
                        0
                )
        );

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(new PersistedWorkflowExecution(workflow, 0)));

        when(definitionRepository.findByTypeAndVersion("ORDER_PROCESSING", 1))
                .thenReturn(Optional.of(definition));

        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(
                        new PersistedStepExecution(step, 0),
                        new PersistedStepExecution(
                                StepExecution.create(workflowId, "wait-for-payment"),
                                0
                        )
                ));

        when(stepExecutionRepository.update(any(PersistedStepExecution.class)))
                .thenAnswer(invocation -> {
                    PersistedStepExecution persisted = invocation.getArgument(0);
                    return new PersistedStepExecution(
                            persisted.stepExecution(),
                            persisted.version() + 1
                    );
                });

        Instant beforeClaim = Instant.now();

        Optional<PersistedStepExecution> result =
                engine.claimNextStep(workflowId);

        Instant afterClaim = Instant.now();

        assertThat(result).isPresent();

        Instant timeoutAt = result.get().stepExecution().getTimeoutAt();

        assertThat(timeoutAt)
                .isAfterOrEqualTo(beforeClaim.plus(Duration.ofMinutes(2)))
                .isBeforeOrEqualTo(afterClaim.plus(Duration.ofMinutes(2)));
    }


    @Test
    void shouldTimeOutExpiredRunningStepAndFailWorkflow() {
        WorkflowExecution workflow = runningWorkflow();

        StepExecution step = StepExecution.create(
                workflowId,
                "validate-order"
        );

        Instant now = Instant.now();

        step.start(now.minusSeconds(10));
        step.acquireLease(
                "worker-1",
                now.plusSeconds(60)
        );

        PersistedStepExecution persistedStep =
                new PersistedStepExecution(step, 1);

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 0)
                ));

        when(definitionRepository.findByTypeAndVersion(
                "ORDER_PROCESSING", 1
        )).thenReturn(Optional.of(definitionWithTwoSteps()));

        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(
                        persistedStep,
                        new PersistedStepExecution(
                                StepExecution.create(
                                        workflowId,
                                        "wait-for-payment"
                                ),
                                0
                        )
                ));

        when(stepExecutionRepository.update(
                any(PersistedStepExecution.class)
        )).thenAnswer(invocation -> {
            PersistedStepExecution updated = invocation.getArgument(0);
            return new PersistedStepExecution(
                    updated.stepExecution(),
                    updated.version() + 1
            );
        });

        Optional<PersistedStepExecution> result =
                engine.claimNextStep(workflowId);

        assertThat(result).isEmpty();

        assertThat(step.getStatus())
                .isEqualTo(StepExecutionStatus.TIMED_OUT);

        assertThat(step.getLastError())
                .isEqualTo("Step execution timed out");

        assertThat(step.getLeaseOwner()).isNull();
        assertThat(step.getLeaseExpiresAt()).isNull();

        verify(stepExecutionRepository).update(persistedStep);

        verify(stepExecutionRepository, never()).claim(
                any(UUID.class),
                anyString(),
                nullable(Instant.class),
                any(Instant.class),
                any(Instant.class)
        );

        verify(workflowExecutionService).fail(workflowId);
    }


    @Test
    void shouldNotClaimStepWhenWorkflowIsNotRunning() {
        WorkflowExecution workflow = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of()
        );

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 0)
                ));

        Optional<PersistedStepExecution> result =
                engine.claimNextStep(workflowId);

        assertThat(result).isEmpty();

        verifyNoInteractions(definitionRepository);
        verifyNoInteractions(stepExecutionRepository);
    }

    @Test
    void shouldPropagateOptimisticLockFailureWhenStepClaimLosesRace() {
        WorkflowDefinition definition = definitionWithTwoSteps();
        WorkflowExecution workflow = runningWorkflow();

        StepExecution first = StepExecution.create(
                workflowId, "validate-order"
        );
        StepExecution second = StepExecution.create(
                workflowId, "wait-for-payment"
        );

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 0)
                ));

        when(definitionRepository.findByTypeAndVersion(
                "ORDER_PROCESSING", 1
        )).thenReturn(Optional.of(definition));

        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(
                        new PersistedStepExecution(first, 0),
                        new PersistedStepExecution(second, 0)
                ));

        when(stepExecutionRepository.claim(
                any(UUID.class),
                anyString(),
                nullable(Instant.class),
                any(Instant.class),
                any(Instant.class)
        )).thenThrow(new OptimisticLockingFailureException(
                "Concurrent step claim"
        ));

        assertThatThrownBy(() -> engine.claimNextStep(workflowId))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(stepExecutionRepository).claim(
                eq(first.getStepExecutionId()),
                anyString(),
                isNull(),
                any(Instant.class),
                any(Instant.class)
        );
    }

    @Test
    void shouldCompleteWorkflowWhenAllStepsAreCompleted() {
        WorkflowDefinition definition = definitionWithTwoSteps();
        WorkflowExecution workflow = runningWorkflow();

        StepExecution first = StepExecution.create(
                workflowId, "validate-order"
        );
        first.start(null);
        first.complete();

        StepExecution second = StepExecution.create(
                workflowId, "wait-for-payment"
        );
        second.start(null);
        second.complete();

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 0)
                ));

        when(definitionRepository.findByTypeAndVersion(
                "ORDER_PROCESSING", 1
        )).thenReturn(Optional.of(definition));

        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(
                        new PersistedStepExecution(first, 2),
                        new PersistedStepExecution(second, 2)
                ));

        Optional<PersistedStepExecution> result =
                engine.claimNextStep(workflowId);

        assertThat(result).isEmpty();

        verify(workflowExecutionService).complete(workflowId);

        verify(stepExecutionRepository, never())
                .update(any(PersistedStepExecution.class));
    }

    @Test
    void shouldNotCompleteWorkflowWhenStepsAreStillPending() {
        WorkflowDefinition definition = definitionWithTwoSteps();
        WorkflowExecution workflow = runningWorkflow();

        StepExecution first = StepExecution.create(
                workflowId, "validate-order"
        );
        first.start(null);
        first.complete();

        StepExecution second = StepExecution.create(
                workflowId, "wait-for-payment"
        );

        stubSuccessfulClaim(
                new PersistedStepExecution(first, 2),
                new PersistedStepExecution(second, 0)
        );

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 0)
                ));

        when(definitionRepository.findByTypeAndVersion(
                "ORDER_PROCESSING", 1
        )).thenReturn(Optional.of(definition));

        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(
                        new PersistedStepExecution(first, 2),
                        new PersistedStepExecution(second, 0)
                ));

        when(stepExecutionRepository.update(any(PersistedStepExecution.class)))
                .thenAnswer(invocation -> {
                    PersistedStepExecution persisted = invocation.getArgument(0);

                    return new PersistedStepExecution(
                            persisted.stepExecution(),
                            persisted.version() + 1
                    );
                });

        Optional<PersistedStepExecution> result =
                engine.claimNextStep(workflowId);

        assertThat(result).isPresent();
        assertThat(result.get().stepExecution().getStepName())
                .isEqualTo("wait-for-payment");

        verify(workflowExecutionService, never()).complete(workflowId);
    }

    @Test
    void shouldCompleteRunningStep() {
        WorkflowExecution workflow = runningWorkflow();

        StepExecution step = StepExecution.create(
                workflowId,
                "validate-order"
        );
        step.start(null);

        String workerId = "worker-1";
        step.acquireLease(
                workerId,
                Instant.now().plusSeconds(60)
        );
        long leaseVersion = step.getLeaseVersion();

        PersistedStepExecution persistedStep =
                new PersistedStepExecution(step, 1);

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 1)
                ));

        when(stepExecutionRepository.findById(step.getStepExecutionId()))
                .thenReturn(Optional.of(persistedStep));

        when(stepExecutionRepository.updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(leaseVersion),
                any(Instant.class)
        )).thenAnswer(invocation -> {
            PersistedStepExecution persisted = invocation.getArgument(0);
            return new PersistedStepExecution(
                    persisted.stepExecution(),
                    persisted.version() + 1
            );
        });

        PersistedStepExecution result = engine.handleStepSuccess(
                workflowId,
                step.getStepExecutionId(),
                Map.of(),
                workerId,
                leaseVersion
        );

        assertThat(result.stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.COMPLETED);

        assertThat(result.stepExecution().getTimeoutAt()).isNull();
        assertThat(result.stepExecution().getNextRetryAt()).isNull();

        verify(stepExecutionRepository).updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(leaseVersion),
                any(Instant.class)
        );

        verify(stepExecutionRepository, never())
                .update(any(PersistedStepExecution.class));

        verifyNoInteractions(definitionRepository);
        verify(workflowExecutionService, never()).complete(workflowId);
    }

    @Test
    void shouldScheduleRetryWhenAttemptsRemain() {
        WorkflowExecution workflow = runningWorkflow();

        RetryPolicy retryPolicy = new RetryPolicy(
                3,
                Duration.ofSeconds(5),
                2.0,
                Duration.ofSeconds(30)
        );

        WorkflowDefinition definition = new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                List.of(
                        new WorkflowStepDefinition(
                                "validate-order",
                                StepType.SERVICE_TASK,
                                "ORDER_VALIDATION",
                                retryPolicy,
                                null
                        ),
                        new WorkflowStepDefinition(
                                "wait-for-payment",
                                StepType.WAIT
                        )
                )
        );

        StepExecution step = StepExecution.create(
                workflowId,
                "validate-order"
        );
        step.start(null);
        String workerId = "worker-1";
        step.acquireLease(
                workerId,
                Instant.now().plusSeconds(60)
        );
        long leaseVersion = step.getLeaseVersion();

        PersistedStepExecution persistedStep =
                new PersistedStepExecution(step, 1);

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 0)
                ));

        when(stepExecutionRepository.findById(step.getStepExecutionId()))
                .thenReturn(Optional.of(persistedStep));

        when(definitionRepository.findByTypeAndVersion(
                "ORDER_PROCESSING", 1
        )).thenReturn(Optional.of(definition));

        when(stepExecutionRepository.updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(leaseVersion),
                any(Instant.class)
        )).thenAnswer(invocation -> {
                    PersistedStepExecution persisted = invocation.getArgument(0);
                    return new PersistedStepExecution(
                            persisted.stepExecution(),
                            persisted.version() + 1
                    );
                });

        Instant beforeFailure = Instant.now();

        PersistedStepExecution result = engine.handleStepFailure(
                workflowId,
                step.getStepExecutionId(),
                "Temporary service failure",
                workerId,
                leaseVersion
        );

        Instant afterFailure = Instant.now();

        assertThat(result.stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.PENDING);

        assertThat(result.stepExecution().getAttemptCount())
                .isEqualTo(1);

        assertThat(result.stepExecution().getLastError())
                .isEqualTo("Temporary service failure");

        assertThat(result.stepExecution().getNextRetryAt())
                .isAfterOrEqualTo(beforeFailure.plusSeconds(5))
                .isBeforeOrEqualTo(afterFailure.plusSeconds(5));

        verify(stepExecutionRepository).updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(leaseVersion),
                any(Instant.class)
        );
        verify(workflowExecutionService, never()).fail(workflowId);
    }

    @Test
    void shouldFailStepAndWorkflowWhenMaximumAttemptsReached() {
        WorkflowExecution workflow = runningWorkflow();

        RetryPolicy retryPolicy = new RetryPolicy(
                2,
                Duration.ofSeconds(5),
                2.0,
                Duration.ofSeconds(30)
        );

        WorkflowDefinition definition = new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                List.of(
                        new WorkflowStepDefinition(
                                "validate-order",
                                StepType.SERVICE_TASK,
                                "ORDER_VALIDATION",
                                retryPolicy,
                                null
                        ),
                        new WorkflowStepDefinition(
                                "wait-for-payment",
                                StepType.WAIT
                        )
                )
        );

        StepExecution step = StepExecution.create(
                workflowId,
                "validate-order"
        );

        // First attempt.
        step.start(null);

        String workerId = "worker-1";
        step.acquireLease(
                workerId,
                Instant.now().plusSeconds(60)
        );
        long leaseVersion = step.getLeaseVersion();

        // Simulate a retry.
        step.scheduleRetry(
                Instant.now().minusSeconds(10),
                "First attempt failed"
        );
        step.start(null);

        PersistedStepExecution persistedStep =
                new PersistedStepExecution(step, 3);

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 0)
                ));

        when(stepExecutionRepository.findById(step.getStepExecutionId()))
                .thenReturn(Optional.of(persistedStep));

        when(definitionRepository.findByTypeAndVersion(
                "ORDER_PROCESSING", 1
        )).thenReturn(Optional.of(definition));

        when(stepExecutionRepository.updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(leaseVersion),
                any(Instant.class)
        )).thenAnswer(invocation -> {
                    PersistedStepExecution persisted = invocation.getArgument(0);
                    return new PersistedStepExecution(
                            persisted.stepExecution(),
                            persisted.version() + 1
                    );
                });

        PersistedStepExecution result = engine.handleStepFailure(
                workflowId,
                step.getStepExecutionId(),
                "Second attempt failed",
                workerId,
                leaseVersion
        );

        assertThat(result.stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.FAILED);

        assertThat(result.stepExecution().getAttemptCount())
                .isEqualTo(2);

        assertThat(result.stepExecution().getLastError())
                .isEqualTo("Second attempt failed");

        assertThat(result.stepExecution().getNextRetryAt())
                .isNull();

        verify(stepExecutionRepository).updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(leaseVersion),
                any(Instant.class)
        );
        verify(workflowExecutionService).fail(workflowId);
    }

    private void stubSuccessfulClaim(
            PersistedStepExecution... candidates
    ) {
        when(stepExecutionRepository.claim(
                any(UUID.class),
                anyString(),
                nullable(Instant.class),
                any(Instant.class),
                any(Instant.class)
        )).thenAnswer(invocation -> {
            UUID stepExecutionId = invocation.getArgument(0);
            String workerId = invocation.getArgument(1);
            Instant timeoutAt = invocation.getArgument(2);
            Instant leaseExpiresAt = invocation.getArgument(3);

            PersistedStepExecution persisted = null;

            for (PersistedStepExecution candidate : candidates) {
                if (candidate.stepExecution().getStepExecutionId()
                        .equals(stepExecutionId)) {
                    persisted = candidate;
                    break;
                }
            }

            if (persisted == null) {
                throw new IllegalStateException(
                        "Unexpected step claim: " + stepExecutionId
                );
            }

            var step = persisted.stepExecution();
            step.start(timeoutAt);
            step.acquireLease(workerId, leaseExpiresAt);

            return Optional.of(
                    new PersistedStepExecution(
                            step,
                            persisted.version() + 1
                    )
            );
        });
    }

    private WorkflowDefinition definitionWithTwoSteps() {
        return new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                List.of(
                        new WorkflowStepDefinition(
                                "validate-order",
                                StepType.SERVICE_TASK,
                                "ORDER_VALIDATION",
                                null,
                                null
                        ),
                        new WorkflowStepDefinition(
                                "wait-for-payment",
                                StepType.WAIT
                        )
                )
        );
    }


    private WorkflowExecution runningWorkflow() {
        WorkflowExecution workflow = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of()
        );
        workflow.start();
        return workflow;
    }


    @Test
    void shouldCompleteStepUsingValidWorkerLease() {
        WorkflowExecution workflow = runningWorkflow();

        StepExecution step = StepExecution.create(
                workflowId,
                "validate-order"
        );
        step.start(null);

        String workerId = "worker-1";
        long leaseVersion = 1L;

        step.acquireLease(
                workerId,
                Instant.now().plusSeconds(60)
        );

        PersistedStepExecution persistedStep =
                new PersistedStepExecution(step, 1);

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 1)
                ));

        when(stepExecutionRepository.findById(step.getStepExecutionId()))
                .thenReturn(Optional.of(persistedStep));

        when(stepExecutionRepository.updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(leaseVersion),
                any(Instant.class)
        )).thenAnswer(invocation -> {
            PersistedStepExecution persisted = invocation.getArgument(0);
            return new PersistedStepExecution(
                    persisted.stepExecution(),
                    persisted.version() + 1
            );
        });

        PersistedStepExecution result = engine.handleStepSuccess(
                workflowId,
                step.getStepExecutionId(),
                Map.of("valid", true),
                workerId,
                leaseVersion
        );

        assertThat(result.stepExecution().getStatus())
                .isEqualTo(StepExecutionStatus.COMPLETED);

        verify(stepExecutionRepository).updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(leaseVersion),
                any(Instant.class)
        );

        verify(stepExecutionRepository, never())
                .update(any(PersistedStepExecution.class));
    }

    @Test
    void shouldRejectWorkerCompletionWhenLeaseHasExpired() {
        WorkflowExecution workflow = runningWorkflow();

        StepExecution step = StepExecution.create(
                workflowId,
                "validate-order"
        );
        step.start(null);

        String workerId = "worker-1";
        long leaseVersion = 1L;

        step.acquireLease(
                workerId,
                Instant.now().plusSeconds(60)
        );

        PersistedStepExecution persistedStep =
                new PersistedStepExecution(step, 1);

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 1)
                ));

        when(stepExecutionRepository.findById(step.getStepExecutionId()))
                .thenReturn(Optional.of(persistedStep));

        when(stepExecutionRepository.updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(leaseVersion),
                any(Instant.class)
        )).thenThrow(new OptimisticLockingFailureException(
                "Worker lease has expired"
        ));

        assertThatThrownBy(() ->
                engine.handleStepSuccess(
                        workflowId,
                        step.getStepExecutionId(),
                        Map.of("valid", true),
                        workerId,
                        leaseVersion
                )
        ).isInstanceOf(OptimisticLockingFailureException.class);

        verify(stepExecutionRepository).updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(leaseVersion),
                any(Instant.class)
        );

        verify(workflowExecutionService, never()).complete(workflowId);
    }

    @Test
    void shouldRejectFailureReportedWithStaleLeaseVersion() {
        WorkflowExecution workflow = runningWorkflow();

        StepExecution step = StepExecution.create(
                workflowId,
                "validate-order"
        );
        step.start(null);

        String workerId = "worker-1";

        // The step currently has lease version 1.
        step.acquireLease(
                workerId,
                Instant.now().plusSeconds(60)
        );

        PersistedStepExecution persistedStep =
                new PersistedStepExecution(step, 1);

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(
                        new PersistedWorkflowExecution(workflow, 1)
                ));

        when(stepExecutionRepository.findById(step.getStepExecutionId()))
                .thenReturn(Optional.of(persistedStep));

        when(definitionRepository.findByTypeAndVersion(
                "ORDER_PROCESSING",
                1
        )).thenReturn(Optional.of(definitionWithTwoSteps()));

        when(stepExecutionRepository.updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(0L),
                any(Instant.class)
        )).thenThrow(new OptimisticLockingFailureException(
                "Stale worker lease version"
        ));

        assertThatThrownBy(() ->
                engine.handleStepFailure(
                        workflowId,
                        step.getStepExecutionId(),
                        "Worker failed",
                        workerId,
                        0L
                )
        ).isInstanceOf(OptimisticLockingFailureException.class);

        verify(stepExecutionRepository).updateWithLease(
                eq(persistedStep),
                eq(workerId),
                eq(0L),
                any(Instant.class)
        );

        verify(workflowExecutionService, never()).fail(workflowId);
    }

}
