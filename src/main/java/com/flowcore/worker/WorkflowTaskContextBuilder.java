
package com.flowcore.worker;

import com.flowcore.application.PersistedStepExecution;
import com.flowcore.definition.model.WorkflowStepDefinition;
import com.flowcore.domain.WorkflowExecution;
import com.flowcore.domain.StepExecutionStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WorkflowTaskContextBuilder {

    public WorkflowTaskContext build(
            WorkflowExecution workflow,
            WorkflowStepDefinition currentStepDefinition,
            List<WorkflowStepDefinition> orderedStepDefinitions,
            List<PersistedStepExecution> persistedSteps,
            UUID currentStepExecutionId
    ) {
        Objects.requireNonNull(workflow, "workflow cannot be null");
        Objects.requireNonNull(
                currentStepDefinition,
                "currentStepDefinition cannot be null"
        );
        Objects.requireNonNull(
                orderedStepDefinitions,
                "orderedStepDefinitions cannot be null"
        );
        Objects.requireNonNull(persistedSteps, "persistedSteps cannot be null");
        Objects.requireNonNull(
                currentStepExecutionId,
                "currentStepExecutionId cannot be null"
        );

        Map<String, PersistedStepExecution> stepsByName =
                persistedSteps.stream().collect(Collectors.toMap(
                        step -> step.stepExecution().getStepName(),
                        Function.identity()
                ));

        PersistedStepExecution currentPersistedStep = persistedSteps.stream()
                .filter(step -> step.stepExecution()
                        .getStepExecutionId().equals(currentStepExecutionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Current step execution not found: "
                                + currentStepExecutionId
                ));

        if (!currentPersistedStep.stepExecution().getWorkflowId()
                .equals(workflow.getWorkflowId())) {
            throw new IllegalArgumentException(
                    "Current step execution does not belong to workflow"
            );
        }

        if (!currentPersistedStep.stepExecution().getStepName()
                .equals(currentStepDefinition.name())) {
            throw new IllegalArgumentException(
                    "Current step execution does not match step definition"
            );
        }

        boolean currentStepExists = orderedStepDefinitions.stream()
                .anyMatch(definition ->
                        definition.name().equals(currentStepDefinition.name()));

        if (!currentStepExists) {
            throw new IllegalArgumentException(
                    "Current step is not part of the workflow definition"
            );
        }

        Map<String, Map<String, Object>> completedStepOutputs =
                new LinkedHashMap<>();

        for (WorkflowStepDefinition definition : orderedStepDefinitions) {

            if (definition.name().equals(currentStepDefinition.name())) {
                break;
            }

            PersistedStepExecution persistedStep =
                    stepsByName.get(definition.name());

            if (persistedStep == null) {
                throw new IllegalStateException(
                        "Missing step execution: " + definition.name()
                );
            }

            if (persistedStep.stepExecution().getStatus()
                    == StepExecutionStatus.COMPLETED) {

                completedStepOutputs.put(
                        definition.name(),
                        persistedStep.stepExecution().getOutputPayload()
                );
            }
        }

        Map<String, Object> contextPayload = new LinkedHashMap<>();
        contextPayload.put("workflow", workflow.getInputPayload());
        contextPayload.put("steps", completedStepOutputs);

        return new WorkflowTaskContext(
                workflow.getWorkflowId(),
                currentStepExecutionId,
                currentStepDefinition.taskType(),
                contextPayload
        );
    }
}
