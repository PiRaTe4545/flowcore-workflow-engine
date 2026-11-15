
package com.flowcore.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class StepExecution {

    private final UUID stepExecutionId;
    private final UUID workflowId;
    private final String stepName;

    private StepExecutionStatus status;
    private int attemptCount;
    private Instant nextRetryAt;
    private Instant timeoutAt;
    private String lastError;
    private final Instant createdAt;
    private Instant updatedAt;

    private StepExecution(
            UUID stepExecutionId,
            UUID workflowId,
            String stepName,
            StepExecutionStatus status,
            int attemptCount,
            Instant nextRetryAt,
            Instant timeoutAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.stepExecutionId = Objects.requireNonNull(stepExecutionId);
        this.workflowId = Objects.requireNonNull(workflowId);
        this.stepName = Objects.requireNonNull(stepName);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);

        if (stepName.isBlank()) {
            throw new IllegalArgumentException("stepName cannot be blank");
        }

        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount cannot be negative");
        }

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "updatedAt cannot be before createdAt");
        }

        this.attemptCount = attemptCount;
        this.nextRetryAt = nextRetryAt;
        this.timeoutAt = timeoutAt;
        this.lastError = lastError;
    }

    public static StepExecution create(UUID workflowId, String stepName) {
        Instant now = Instant.now();

        return new StepExecution(
                UUID.randomUUID(),
                workflowId,
                stepName,
                StepExecutionStatus.PENDING,
                0,
                null,
                null,
                null,
                now,
                now
        );
    }

    public static StepExecution restore(
            UUID stepExecutionId,
            UUID workflowId,
            String stepName,
            StepExecutionStatus status,
            int attemptCount,
            Instant nextRetryAt,
            Instant timeoutAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new StepExecution(
                stepExecutionId,
                workflowId,
                stepName,
                status,
                attemptCount,
                nextRetryAt,
                timeoutAt,
                lastError,
                createdAt,
                updatedAt
        );
    }

    public void start(Instant timeoutAt) {
        requireStatus(StepExecutionStatus.PENDING);

        this.status = StepExecutionStatus.RUNNING;
        this.attemptCount++;
        this.nextRetryAt = null;
        this.timeoutAt = timeoutAt;
        this.lastError = null;
        touch();
    }

    public void scheduleRetry(Instant nextRetryAt, String error) {
        requireStatus(StepExecutionStatus.RUNNING);

        this.status = StepExecutionStatus.PENDING;
        this.nextRetryAt = Objects.requireNonNull(nextRetryAt);
        this.timeoutAt = null;
        this.lastError = requireError(error);
        touch();
    }

    public void complete() {
        requireStatus(StepExecutionStatus.RUNNING);

        this.status = StepExecutionStatus.COMPLETED;
        this.timeoutAt = null;
        this.nextRetryAt = null;
        this.lastError = null;
        touch();
    }

    public void fail(String error) {
        requireStatus(StepExecutionStatus.RUNNING);

        this.status = StepExecutionStatus.FAILED;
        this.timeoutAt = null;
        this.nextRetryAt = null;
        this.lastError = requireError(error);
        touch();
    }

    public void timeOut(String error) {
        requireStatus(StepExecutionStatus.RUNNING);

        this.status = StepExecutionStatus.TIMED_OUT;
        this.timeoutAt = null;
        this.nextRetryAt = null;
        this.lastError = requireError(error);
        touch();
    }

    private void requireStatus(StepExecutionStatus expected) {
        if (status != expected) {
            throw new InvalidStepExecutionTransitionException(
                    "Cannot perform operation when step status is " + status
            );
        }
    }

    private String requireError(String error) {
        if (error == null || error.isBlank()) {
            throw new IllegalArgumentException("error cannot be blank");
        }
        return error;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getStepExecutionId() {
        return stepExecutionId;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public String getStepName() {
        return stepName;
    }

    public StepExecutionStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getTimeoutAt() {
        return timeoutAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
