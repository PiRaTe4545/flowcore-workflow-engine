package com.flowcore.definition.model;

import jakarta.validation.Valid;

import java.util.Objects;

public record WorkflowStepDefinition(
        String name,
        StepType type,
        @Valid RetryPolicy retryPolicy,
        @Valid TimeoutPolicy timeoutPolicy
) {
    public WorkflowStepDefinition {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
    }

    public WorkflowStepDefinition(String name, StepType type) {
        this(name, type, null, null);
    }
}