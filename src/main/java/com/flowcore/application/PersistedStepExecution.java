package com.flowcore.application;

import com.flowcore.domain.StepExecution;

import java.util.Objects;

public record PersistedStepExecution(
        StepExecution stepExecution,
        long version
) {
    public PersistedStepExecution {
        Objects.requireNonNull(
                stepExecution,
                "stepExecution cannot be null"
        );

        if (version < 0) {
            throw new IllegalArgumentException(
                    "version cannot be negative"
            );
        }
    }
}