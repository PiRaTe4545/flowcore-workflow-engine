
package com.flowcore.definition.model;

import jakarta.validation.Valid;

import java.util.Objects;

public record WorkflowStepDefinition(
        String name,
        StepType type,
        String taskType,
        @Valid RetryPolicy retryPolicy,
        @Valid TimeoutPolicy timeoutPolicy
) {
    public WorkflowStepDefinition {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }

        if (type == StepType.SERVICE_TASK
                && (taskType == null || taskType.isBlank())) {
            throw new IllegalArgumentException(
                    "taskType is required for SERVICE_TASK"
            );
        }

        if (type == StepType.WAIT && taskType != null) {
            throw new IllegalArgumentException(
                    "taskType is not allowed for WAIT"
            );
        }

        if (taskType != null && !taskType.equals(taskType.trim())) {
            throw new IllegalArgumentException(
                    "taskType cannot contain leading or trailing whitespace"
            );
        }
    }

    public WorkflowStepDefinition(String name, StepType type) {
        this(name, type, null, null, null);
    }
}
