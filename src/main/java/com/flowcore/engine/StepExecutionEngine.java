
package com.flowcore.engine;

import com.flowcore.application.*;
import com.flowcore.definition.model.WorkflowDefinition;
import com.flowcore.definition.model.WorkflowStepDefinition;
import com.flowcore.domain.StepExecutionStatus;
import com.flowcore.domain.WorkflowStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StepExecutionEngine {

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

        for (WorkflowStepDefinition stepDefinition : definition.steps()) {

            PersistedStepExecution persistedStep =
                    stepsByName.get(stepDefinition.name());

            if (persistedStep == null) {
                throw new IllegalStateException(
                        "Missing step execution: " + stepDefinition.name()
                );
            }

            var step = persistedStep.stepExecution();

            if (step.getStatus() == StepExecutionStatus.COMPLETED) {
                continue;
            }

            if (step.getStatus() != StepExecutionStatus.PENDING) {
                return Optional.empty();
            }

            if (step.getNextRetryAt() != null
                    && step.getNextRetryAt().isAfter(now)) {
                return Optional.empty();
            }

            Instant timeoutAt = stepDefinition.timeoutPolicy() == null
                    ? null
                    : now.plus(stepDefinition.timeoutPolicy().timeout());

            step.start(timeoutAt);

            return Optional.of(
                    stepExecutionRepository.update(persistedStep)
            );
        }

        workflowExecutionService.complete(workflowId);

        return Optional.empty();
    }
}
