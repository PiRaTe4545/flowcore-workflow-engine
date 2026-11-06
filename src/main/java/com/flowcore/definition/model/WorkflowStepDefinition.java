
package com.flowcore.definition.model;

import java.util.Objects;

public record WorkflowStepDefinition(
        String name,
        StepType type
) {
    public WorkflowStepDefinition {
        Objects.requireNonNull(name, "step name cannot be null");
        Objects.requireNonNull(type, "step type cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "step name cannot be blank"
            );
        }
    }
}
