package com.flowcore.definition.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record WorkflowDefinition(
        String workflowType,
        int version,
        List<WorkflowStepDefinition> steps
) {
    public WorkflowDefinition {
        Objects.requireNonNull(workflowType, "workflowType cannot be null");
        Objects.requireNonNull(steps, "steps cannot be null");

        if (workflowType.isBlank()) {
            throw new IllegalArgumentException(
                    "workflowType cannot be blank"
            );
        }

        if (version <= 0) {
            throw new IllegalArgumentException(
                    "version must be positive"
            );
        }

        if (steps.isEmpty()) {
            throw new IllegalArgumentException(
                    "workflow must contain at least one step"
            );
        }

        steps = List.copyOf(steps);

        Set<String> stepNames = new HashSet<>();

        for (WorkflowStepDefinition step : steps) {
            if (!stepNames.add(step.name())) {
                throw new IllegalArgumentException(
                        "Duplicate workflow step name: " + step.name()
                );
            }
        }
    }
}