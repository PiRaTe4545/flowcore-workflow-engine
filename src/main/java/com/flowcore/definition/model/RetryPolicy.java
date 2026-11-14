
package com.flowcore.definition.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Duration;
import java.util.Objects;

public record RetryPolicy(
        @Positive(message = "maxAttempts must be positive")
        int maxAttempts,

        @NotNull
        Duration initialInterval,

        @DecimalMin(value = "1.0", message = "backoffMultiplier must be at least 1.0")
        double backoffMultiplier,

        @NotNull
        Duration maxInterval
) {
    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }

        Objects.requireNonNull(initialInterval, "initialInterval cannot be null");
        Objects.requireNonNull(maxInterval, "maxInterval cannot be null");

        if (initialInterval.isZero() || initialInterval.isNegative()) {
            throw new IllegalArgumentException("initialInterval must be positive");
        }

        if (!Double.isFinite(backoffMultiplier) || backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be finite and at least 1.0");
        }

        if (maxInterval.compareTo(initialInterval) < 0) {
            throw new IllegalArgumentException("maxInterval cannot be less than initialInterval");
        }
    }

    public Duration calculateDelay(int failedAttempt) {
        if (failedAttempt < 1) {
            throw new IllegalArgumentException(
                    "failedAttempt must be at least 1"
            );
        }

        long initialMillis = initialInterval.toMillis();
        long maximumMillis = maxInterval.toMillis();

        double multiplier = Math.pow(
                backoffMultiplier,
                failedAttempt - 1
        );

        if (!Double.isFinite(multiplier)
                || multiplier >= (double) maximumMillis / initialMillis) {
            return maxInterval;
        }

        long delayMillis = (long) (initialMillis * multiplier);

        return Duration.ofMillis(Math.min(delayMillis, maximumMillis));
    }
}
