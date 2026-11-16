
package com.flowcore.worker;

import com.flowcore.application.*;
import com.flowcore.definition.model.WorkflowDefinition;
import com.flowcore.definition.model.WorkflowStepDefinition;
import com.flowcore.domain.WorkflowExecution;
import com.flowcore.engine.StepExecutionEngine;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class WorkflowTaskExecutionService {

    private final StepExecutionEngine stepExecutionEngine;
    private final WorkflowExecutionService workflowExecutionService;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final WorkflowTaskContextBuilder contextBuilder;
    private final WorkflowTaskWorkerRegistry workerRegistry;

    public WorkflowTaskExecutionService(
            StepExecutionEngine stepExecutionEngine,
            WorkflowExecutionService workflowExecutionService,
            WorkflowDefinitionRepository workflowDefinitionRepository,
            StepExecutionRepository stepExecutionRepository,
            WorkflowTaskContextBuilder contextBuilder,
            WorkflowTaskWorkerRegistry workerRegistry
    ) {
        this.stepExecutionEngine = Objects.requireNonNull(stepExecutionEngine);
        this.workflowExecutionService =
                Objects.requireNonNull(workflowExecutionService);
        this.workflowDefinitionRepository =
                Objects.requireNonNull(workflowDefinitionRepository);
        this.stepExecutionRepository =
                Objects.requireNonNull(stepExecutionRepository);
        this.contextBuilder = Objects.requireNonNull(contextBuilder);
        this.workerRegistry = Objects.requireNonNull(workerRegistry);
    }

    public boolean executeNextTask(UUID workflowId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");

        var claimedStepOptional =
                stepExecutionEngine.claimNextServiceTask(workflowId);

        if (claimedStepOptional.isEmpty()) {
            return false;
        }

        PersistedStepExecution claimedStep = claimedStepOptional.get();
        UUID stepExecutionId =
                claimedStep.stepExecution().getStepExecutionId();

        WorkflowTaskResult result;

        try {
            PersistedWorkflowExecution persistedWorkflow =
                    workflowExecutionService.getById(workflowId);

            WorkflowExecution workflow = persistedWorkflow.workflowExecution();

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
            stepExecutionEngine.handleStepFailure(
                    workflowId,
                    stepExecutionId,
                    exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage()
            );
            return true;
        }

        // Keep success handling outside the worker exception handler.
        stepExecutionEngine.handleStepSuccess(
                workflowId,
                stepExecutionId,
                result.outputPayload()
        );

        return true;
    }
}
