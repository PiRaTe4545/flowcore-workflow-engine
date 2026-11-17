
package com.flowcore.engine;

import com.flowcore.application.*;
import com.flowcore.definition.model.StepType;
import com.flowcore.definition.model.WorkflowDefinition;
import com.flowcore.definition.model.WorkflowStepDefinition;
import com.flowcore.domain.StepExecutionStatus;
import com.flowcore.domain.WorkflowStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StepExecutionEngine {

    private static final Duration DEFAULT_LEASE_DURATION =
            Duration.ofMinutes(1);

    private final WorkflowExecutionRepository workflowRepository;
    private final WorkflowDefinitionRepository definitionRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final WorkflowExecutionService workflowExecutionService;

    public StepExecutionEngine(
            WorkflowExecutionRepository workflowRepository,
            WorkflowDefinitionRepository definitionRepository,
            StepExecutionRepository stepExecutionRepository,
            WorkflowExecutionService workflowExecutionService
    ) {
        this.workflowRepository = workflowRepository;
        this.definitionRepository = definitionRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.workflowExecutionService = workflowExecutionService;
    }

    @Transactional
    public Optional<PersistedStepExecution> claimNextStep(UUID workflowId) {
        return claimNextStep(
                workflowId,
                false,
                UUID.randomUUID().toString(),
                DEFAULT_LEASE_DURATION
        );
    }

    @Transactional
    public Optional<PersistedStepExecution> claimNextServiceTask(
            UUID workflowId
    ) {
        return claimNextStep(
                workflowId,
                true,
                UUID.randomUUID().toString(),
                DEFAULT_LEASE_DURATION
        );
    }

    @Transactional
    public Optional<PersistedStepExecution> claimNextServiceTask(
            UUID workflowId,
            String workerId,
            Duration leaseDuration
    ) {
        return claimNextStep(
                workflowId,
                true,
                workerId,
                leaseDuration
        );
    }

    private Optional<PersistedStepExecution> claimNextStep(
            UUID workflowId,
            boolean serviceTasksOnly,
            String workerId,
            Duration leaseDuration
    ) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "workerId cannot be blank"
            );
        }

        if (leaseDuration == null
                || leaseDuration.isZero()
                || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "leaseDuration must be positive"
            );
        }

        var persistedWorkflow = workflowRepository.findById(workflowId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Workflow not found: " + workflowId
                        )
                );

        if (persistedWorkflow.workflowExecution().getStatus()
                != WorkflowStatus.RUNNING) {
            return Optional.empty();
        }

        WorkflowDefinition definition = definitionRepository
                .findByTypeAndVersion(
                        persistedWorkflow.workflowExecution().getWorkflowType(),
                        persistedWorkflow.workflowExecution().getDefinitionVersion()
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Workflow definition not found"
                        )
                );

        List<PersistedStepExecution> persistedSteps =
                stepExecutionRepository.findByWorkflowId(workflowId);

        Map<String, PersistedStepExecution> stepsByName =
                persistedSteps.stream().collect(Collectors.toMap(
                        step -> step.stepExecution().getStepName(),
                        Function.identity()
                ));

        Instant now = Instant.now();

        for (PersistedStepExecution persistedStep : persistedSteps) {
            if (handleExpiredTimeout(workflowId, persistedStep, now)) {
                return Optional.empty();
            }
        }

        for (WorkflowStepDefinition stepDefinition : definition.steps()) {

            PersistedStepExecution persistedStep =
                    stepsByName.get(stepDefinition.name());

            if (persistedStep == null) {
                throw new IllegalStateException(
                        "Missing step execution: " + stepDefinition.name()
                );
            }

            var step = persistedStep.stepExecution();

            // Completed steps must always be skipped first.
            if (step.getStatus() == StepExecutionStatus.COMPLETED) {
                continue;
            }

            // Worker orchestration must not claim WAIT steps.
            // A pending or running WAIT step blocks subsequent service tasks.
            if (serviceTasksOnly
                    && stepDefinition.type() == StepType.WAIT) {
                return Optional.empty();
            }

            // Only pending steps and running steps can be claimed.
            // PostgreSQL determines whether a running step's lease expired.
            if (step.getStatus() != StepExecutionStatus.PENDING
                    && step.getStatus() != StepExecutionStatus.RUNNING) {
                return Optional.empty();
            }

            Instant timeoutAt = stepDefinition.timeoutPolicy() == null
                    ? null
                    : now.plus(stepDefinition.timeoutPolicy().timeout());

            Instant leaseExpiresAt = now.plus(leaseDuration);

            return stepExecutionRepository.claim(
                    step.getStepExecutionId(),
                    workerId,
                    timeoutAt,
                    leaseExpiresAt,
                    now
            );
        }

        boolean allStepsCompleted = persistedSteps.stream()
                .allMatch(persisted ->
                        persisted.stepExecution().getStatus()
                                == StepExecutionStatus.COMPLETED
                );

        if (allStepsCompleted) {
            workflowExecutionService.complete(workflowId);
        }

        return Optional.empty();
    }


    private boolean handleExpiredTimeout(
            UUID workflowId,
            PersistedStepExecution persistedStep,
            Instant now
    ) {
        var step = persistedStep.stepExecution();

        if (step.getStatus() != StepExecutionStatus.RUNNING
                || step.getTimeoutAt() == null
                || step.getTimeoutAt().isAfter(now)) {
            return false;
        }

        step.timeOut("Step execution timed out");

        stepExecutionRepository.update(persistedStep);

        workflowExecutionService.fail(workflowId);

        return true;
    }


    /**
     * Handles worker failure only if the worker still owns the active lease.
     */
    @Transactional
    public PersistedStepExecution handleStepFailure(
            UUID workflowId,
            UUID stepExecutionId,
            String error,
            String workerId,
            long expectedLeaseVersion
    ) {
        validateLeaseIdentity(workerId, expectedLeaseVersion);

        return handleStepFailureInternal(
                workflowId,
                stepExecutionId,
                error,
                workerId,
                expectedLeaseVersion
        );
    }

    private PersistedStepExecution handleStepFailureInternal(
            UUID workflowId,
            UUID stepExecutionId,
            String error,
            String workerId,
            long expectedLeaseVersion
    ) {
        var persistedWorkflow = workflowRepository.findById(workflowId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Workflow not found: " + workflowId
                        )
                );

        if (persistedWorkflow.workflowExecution().getStatus()
                != WorkflowStatus.RUNNING) {
            throw new IllegalStateException(
                    "Cannot fail a step when workflow is not RUNNING"
            );
        }

        PersistedStepExecution persistedStep =
                stepExecutionRepository.findById(stepExecutionId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Step execution not found: " + stepExecutionId
                                )
                        );

        var step = persistedStep.stepExecution();

        if (!step.getWorkflowId().equals(workflowId)) {
            throw new IllegalArgumentException(
                    "Step execution does not belong to workflow: " + workflowId
            );
        }

        WorkflowDefinition definition = definitionRepository
                .findByTypeAndVersion(
                        persistedWorkflow.workflowExecution().getWorkflowType(),
                        persistedWorkflow.workflowExecution().getDefinitionVersion()
                )
                .orElseThrow(() ->
                        new IllegalStateException("Workflow definition not found")
                );

        WorkflowStepDefinition stepDefinition = definition.steps().stream()
                .filter(candidate -> candidate.name().equals(step.getStepName()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Step definition not found: " + step.getStepName()
                        )
                );

        var retryPolicy = stepDefinition.retryPolicy();

        if (retryPolicy != null
                && step.getAttemptCount() < retryPolicy.maxAttempts()) {

            Instant nextRetryAt = Instant.now()
                    .plus(retryPolicy.calculateDelay(step.getAttemptCount()));

            step.scheduleRetry(nextRetryAt, error);

            return persistStepUpdate(
                    persistedStep,
                    workerId,
                    expectedLeaseVersion
            );
        }

        step.fail(error);

        PersistedStepExecution failedStep = persistStepUpdate(
                persistedStep,
                workerId,
                expectedLeaseVersion
        );

        workflowExecutionService.fail(workflowId);

        return failedStep;
    }

    /**
     * Handles worker success only if the worker still owns the active lease.
     */
    @Transactional
    public PersistedStepExecution handleStepSuccess(
            UUID workflowId,
            UUID stepExecutionId,
            Map<String, Object> outputPayload,
            String workerId,
            long expectedLeaseVersion
    ) {
        validateLeaseIdentity(workerId, expectedLeaseVersion);

        return handleStepSuccessInternal(
                workflowId,
                stepExecutionId,
                outputPayload,
                workerId,
                expectedLeaseVersion
        );
    }

    private PersistedStepExecution handleStepSuccessInternal(
            UUID workflowId,
            UUID stepExecutionId,
            Map<String, Object> outputPayload,
            String workerId,
            long expectedLeaseVersion
    ) {
        var persistedWorkflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Workflow not found: " + workflowId
                ));

        if (persistedWorkflow.workflowExecution().getStatus()
                != WorkflowStatus.RUNNING) {
            throw new IllegalStateException(
                    "Cannot complete a step when workflow is not RUNNING"
            );
        }

        PersistedStepExecution persistedStep =
                stepExecutionRepository.findById(stepExecutionId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Step execution not found: " + stepExecutionId
                        ));

        var step = persistedStep.stepExecution();

        if (!step.getWorkflowId().equals(workflowId)) {
            throw new IllegalArgumentException(
                    "Step execution does not belong to workflow: " + workflowId
            );
        }

        step.complete(outputPayload);

        return persistStepUpdate(
                persistedStep,
                workerId,
                expectedLeaseVersion
        );
    }

    private PersistedStepExecution persistStepUpdate(
            PersistedStepExecution persistedStep,
            String workerId,
            long expectedLeaseVersion
    ) {
        return stepExecutionRepository.updateWithLease(
                persistedStep,
                workerId,
                expectedLeaseVersion,
                Instant.now()
        );
    }

    private void validateLeaseIdentity(
            String workerId,
            long expectedLeaseVersion
    ) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "workerId cannot be blank"
            );
        }

        if (expectedLeaseVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedLeaseVersion cannot be negative"
            );
        }
    }
}
