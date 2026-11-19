
package com.flowcore.worker;

import com.flowcore.application.*;
import com.flowcore.definition.model.WorkflowDefinition;
import com.flowcore.definition.model.WorkflowStepDefinition;
import com.flowcore.domain.WorkflowExecution;
import com.flowcore.engine.StepExecutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class WorkflowTaskExecutionService {

    private static final Logger log =
            LoggerFactory.getLogger(WorkflowTaskExecutionService.class);

    private static final Duration LEASE_DURATION =
            Duration.ofMinutes(1);

    private static final Duration LEASE_RENEWAL_INTERVAL =
            Duration.ofSeconds(20);

    private final StepExecutionEngine stepExecutionEngine;
    private final WorkflowExecutionService workflowExecutionService;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final WorkflowTaskContextBuilder contextBuilder;
    private final WorkflowTaskWorkerRegistry workerRegistry;
    private final StepExecutionLeaseRenewer leaseRenewer;

    private final String workerId;

    public WorkflowTaskExecutionService(
            StepExecutionEngine stepExecutionEngine,
            WorkflowExecutionService workflowExecutionService,
            WorkflowDefinitionRepository workflowDefinitionRepository,
            StepExecutionRepository stepExecutionRepository,
            WorkflowTaskContextBuilder contextBuilder,
            WorkflowTaskWorkerRegistry workerRegistry,
            StepExecutionLeaseRenewer leaseRenewer
    ) {
        this.stepExecutionEngine =
                Objects.requireNonNull(stepExecutionEngine);
        this.workflowExecutionService =
                Objects.requireNonNull(workflowExecutionService);
        this.workflowDefinitionRepository =
                Objects.requireNonNull(workflowDefinitionRepository);
        this.stepExecutionRepository =
                Objects.requireNonNull(stepExecutionRepository);
        this.contextBuilder =
                Objects.requireNonNull(contextBuilder);
        this.workerRegistry =
                Objects.requireNonNull(workerRegistry);
        this.leaseRenewer =
                Objects.requireNonNull(leaseRenewer);

        this.workerId = UUID.randomUUID().toString();
    }

    public boolean executeNextTask(UUID workflowId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");

        var claimedStepOptional =
                stepExecutionEngine.claimNextServiceTask(
                        workflowId,
                        workerId,
                        LEASE_DURATION
                );

        if (claimedStepOptional.isEmpty()) {
            return false;
        }

        PersistedStepExecution claimedStep = claimedStepOptional.get();

        UUID stepExecutionId =
                claimedStep.stepExecution().getStepExecutionId();

        long leaseVersion =
                claimedStep.stepExecution().getLeaseVersion();

        StepExecutionLeaseRenewer.LeaseRenewal leaseRenewal;

        try {
            leaseRenewal = leaseRenewer.start(
                    stepExecutionId,
                    workerId,
                    leaseVersion,
                    LEASE_DURATION,
                    LEASE_RENEWAL_INTERVAL
            );
        } catch (RuntimeException exception) {
            stepExecutionEngine.handleStepFailure(
                    workflowId,
                    stepExecutionId,
                    getErrorMessage(exception),
                    workerId,
                    leaseVersion
            );

            return true;
        }

        WorkflowTaskResult result = null;
        RuntimeException taskFailure = null;

        try {
            PersistedWorkflowExecution persistedWorkflow =
                    workflowExecutionService.getById(workflowId);

            WorkflowExecution workflow =
                    persistedWorkflow.workflowExecution();

            WorkflowDefinition definition =
                    workflowDefinitionRepository.findByTypeAndVersion(
                                    workflow.getWorkflowType(),
                                    workflow.getDefinitionVersion()
                            )
                            .orElseThrow(() -> new IllegalStateException(
                                    "Workflow definition not found"
                            ));

            String stepName =
                    claimedStep.stepExecution().getStepName();

            WorkflowStepDefinition stepDefinition =
                    definition.steps().stream()
                            .filter(step -> step.name().equals(stepName))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "Step definition not found: " + stepName
                            ));

            List<PersistedStepExecution> persistedSteps =
                    stepExecutionRepository.findByWorkflowId(workflowId);

            WorkflowTaskContext context = contextBuilder.build(
                    workflow,
                    stepDefinition,
                    definition.steps(),
                    persistedSteps,
                    stepExecutionId
            );

            WorkflowTaskWorker worker =
                    workerRegistry.getWorker(stepDefinition.taskType());

            result = Objects.requireNonNull(
                    worker.execute(context),
                    "Worker returned a null result"
            );

        } catch (RuntimeException exception) {
            taskFailure = exception;
        } finally {
            leaseRenewal.stop();
        }

        // A lost lease means this worker no longer has authority
        // to persist the task result or failure.
        if (leaseRenewal.isLeaseLost()) {
            log.warn(
                    "Lease lost for step execution {} in workflow {}. "
                            + "Discarding worker result.",
                    stepExecutionId,
                    workflowId,
                    leaseRenewal.getRenewalFailure()
            );

            return true;
        }

        if (taskFailure != null) {
            stepExecutionEngine.handleStepFailure(
                    workflowId,
                    stepExecutionId,
                    getErrorMessage(taskFailure),
                    workerId,
                    leaseVersion
            );

            return true;
        }

        // Keep success handling outside the worker exception handler.
        // Persistence or fencing failures must propagate to the caller.
        stepExecutionEngine.handleStepSuccess(
                workflowId,
                stepExecutionId,
                result.outputPayload(),
                workerId,
                leaseVersion
        );

        return true;
    }

    private String getErrorMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
