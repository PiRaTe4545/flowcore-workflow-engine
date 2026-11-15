
package com.flowcore.engine;

import com.flowcore.application.*;
import com.flowcore.definition.model.*;
import com.flowcore.domain.*;
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

        verify(stepExecutionRepository).update(any(PersistedStepExecution.class));
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

        when(stepExecutionRepository.update(any(PersistedStepExecution.class)))
                .thenThrow(new OptimisticLockingFailureException(
                        "Concurrent step claim"
                ));

        assertThatThrownBy(() -> engine.claimNextStep(workflowId))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(stepExecutionRepository)
                .update(any(PersistedStepExecution.class));
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

    private WorkflowDefinition definitionWithTwoSteps() {
        return new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                List.of(
                        new WorkflowStepDefinition(
                                "validate-order",
                                StepType.SERVICE_TASK
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
}
