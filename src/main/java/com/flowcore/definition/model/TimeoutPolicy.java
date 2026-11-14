package com.flowcore.definition.model;

import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.Objects;

public record TimeoutPolicy(
        @NotNull Duration timeout
) {
    public TimeoutPolicy {
        Objects.requireNonNull(timeout, "timeout cannot be null");

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}