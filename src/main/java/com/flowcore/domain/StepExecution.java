
package com.flowcore.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private Map<String, Object> outputPayload;

    private String leaseOwner;
    private Instant leaseExpiresAt;
    private long leaseVersion;

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
            Map<String, Object> outputPayload,
            String leaseOwner,
            Instant leaseExpiresAt,
            long leaseVersion,
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
            throw new IllegalArgumentException(
                    "attemptCount cannot be negative"
            );
        }

        if (leaseVersion < 0) {
            throw new IllegalArgumentException(
                    "leaseVersion cannot be negative"
            );
        }

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "updatedAt cannot be before createdAt"
            );
        }

        if ((leaseOwner == null) != (leaseExpiresAt == null)) {
            throw new IllegalArgumentException(
                    "leaseOwner and leaseExpiresAt must both be null or non-null"
            );
        }

        this.attemptCount = attemptCount;
        this.nextRetryAt = nextRetryAt;
        this.timeoutAt = timeoutAt;
        this.lastError = lastError;
        this.outputPayload = immutablePayload(outputPayload);
        this.leaseOwner = leaseOwner;
        this.leaseExpiresAt = leaseExpiresAt;
        this.leaseVersion = leaseVersion;
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
                Map.of(),
                null,
                null,
                0,
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
        return restore(
                stepExecutionId,
                workflowId,
                stepName,
                status,
                attemptCount,
                nextRetryAt,
                timeoutAt,
                lastError,
                Map.of(),
                null,
                null,
                0,
                createdAt,
                updatedAt
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
            Map<String, Object> outputPayload,
            Instant createdAt,
            Instant updatedAt
    ) {
        return restore(
                stepExecutionId,
                workflowId,
                stepName,
                status,
                attemptCount,
                nextRetryAt,
                timeoutAt,
                lastError,
                outputPayload,
                null,
                null,
                0,
                createdAt,
                updatedAt
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
            Map<String, Object> outputPayload,
            String leaseOwner,
            Instant leaseExpiresAt,
            long leaseVersion,
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
                outputPayload,
                leaseOwner,
                leaseExpiresAt,
                leaseVersion,
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
        clearLease();
        touch();
    }

    public void acquireLease(
            String workerId,
            Instant leaseExpiresAt
    ) {
        requireStatus(StepExecutionStatus.RUNNING);

        Objects.requireNonNull(workerId, "workerId cannot be null");
        Objects.requireNonNull(
                leaseExpiresAt,
                "leaseExpiresAt cannot be null"
        );

        if (workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "workerId cannot be blank"
            );
        }

        if (!leaseExpiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "leaseExpiresAt must be in the future"
            );
        }

        if (this.leaseOwner != null
                && this.leaseExpiresAt.isAfter(Instant.now())) {
            throw new IllegalStateException(
                    "Step already has an active lease"
            );
        }

        this.leaseOwner = workerId;
        this.leaseExpiresAt = leaseExpiresAt;
        this.leaseVersion++;
        touch();
    }

    public void renewLease(
            String workerId,
            long expectedLeaseVersion,
            Instant newLeaseExpiresAt
    ) {
        verifyLease(workerId, expectedLeaseVersion);

        Objects.requireNonNull(
                newLeaseExpiresAt,
                "newLeaseExpiresAt cannot be null"
        );

        Instant now = Instant.now();

        if (!leaseExpiresAt.isAfter(now)) {
            throw new IllegalStateException(
                    "Cannot renew an expired lease"
            );
        }

        if (!newLeaseExpiresAt.isAfter(leaseExpiresAt)) {
            throw new IllegalArgumentException(
                    "New lease expiration must extend the existing lease"
            );
        }

        this.leaseExpiresAt = newLeaseExpiresAt;
        touch();
    }

    public void verifyLease(
            String workerId,
            long expectedLeaseVersion
    ) {
        Objects.requireNonNull(workerId, "workerId cannot be null");

        if (workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "workerId cannot be blank"
            );
        }

        if (status != StepExecutionStatus.RUNNING) {
            throw new IllegalStateException(
                    "Step is not running"
            );
        }

        if (!workerId.equals(leaseOwner)
                || expectedLeaseVersion != leaseVersion) {
            throw new IllegalStateException(
                    "Worker no longer owns this step execution"
            );
        }

        if (leaseExpiresAt == null
                || !leaseExpiresAt.isAfter(Instant.now())) {
            throw new IllegalStateException(
                    "Worker lease has expired"
            );
        }
    }

    public void scheduleRetry(Instant nextRetryAt, String error) {
        requireStatus(StepExecutionStatus.RUNNING);

        this.status = StepExecutionStatus.PENDING;
        this.nextRetryAt = Objects.requireNonNull(nextRetryAt);
        this.timeoutAt = null;
        this.lastError = requireError(error);
        clearLease();
        touch();
    }

    public void complete() {
        complete(Map.of());
    }

    public void complete(Map<String, Object> outputPayload) {
        requireStatus(StepExecutionStatus.RUNNING);

        this.outputPayload = immutablePayload(outputPayload);
        this.status = StepExecutionStatus.COMPLETED;
        this.timeoutAt = null;
        this.nextRetryAt = null;
        this.lastError = null;
        clearLease();
        touch();
    }

    public void fail(String error) {
        requireStatus(StepExecutionStatus.RUNNING);

        this.status = StepExecutionStatus.FAILED;
        this.timeoutAt = null;
        this.nextRetryAt = null;
        this.lastError = requireError(error);
        clearLease();
        touch();
    }

    public void timeOut(String error) {
        requireStatus(StepExecutionStatus.RUNNING);

        this.status = StepExecutionStatus.TIMED_OUT;
        this.timeoutAt = null;
        this.nextRetryAt = null;
        this.lastError = requireError(error);
        clearLease();
        touch();
    }

    private void clearLease() {
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
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

    private Map<String, Object> immutablePayload(
            Map<String, Object> payload
    ) {
        Objects.requireNonNull(
                payload,
                "outputPayload cannot be null"
        );

        return Collections.unmodifiableMap(
                new LinkedHashMap<>(payload)
        );
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

    public Map<String, Object> getOutputPayload() {
        return outputPayload;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public long getLeaseVersion() {
        return leaseVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
