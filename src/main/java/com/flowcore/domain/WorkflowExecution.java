
package com.flowcore.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Collections;
import java.util.HashMap;

public class WorkflowExecution {

    private final UUID workflowId;
    private final String workflowType;
    private final int definitionVersion;
    private final Map<String, Object> inputPayload;

    private WorkflowStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private WorkflowExecution(
            UUID workflowId,
            String workflowType,
            int definitionVersion,
            Map<String, Object> inputPayload,
            WorkflowStatus status,
            Instant createdAt,
            Instant updatedAt) {

        this.workflowId = Objects.requireNonNull(
                workflowId, "workflowId cannot be null");

        if (workflowType == null || workflowType.isBlank()) {
            throw new IllegalArgumentException(
                    "workflowType cannot be blank");
        }

        if (definitionVersion <= 0) {
            throw new IllegalArgumentException(
                    "definitionVersion must be positive");
        }

        this.workflowType = workflowType;
        this.definitionVersion = definitionVersion;
        this.inputPayload = Collections.unmodifiableMap(
                new HashMap<>(
                        Objects.requireNonNull(inputPayload, "inputPayload cannot be null")
                )
        );

        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "updatedAt cannot be before createdAt");
        }
    }

    public static WorkflowExecution create(
            String workflowType,
            int definitionVersion,
            Map<String, Object> inputPayload) {

        Instant now = Instant.now();

        return new WorkflowExecution(
                UUID.randomUUID(),
                workflowType,
                definitionVersion,
                inputPayload,
                WorkflowStatus.PENDING,
                now,
                now
        );
    }

    public static WorkflowExecution restore(
            UUID workflowId,
            String workflowType,
            int definitionVersion,
            Map<String, Object> inputPayload,
            WorkflowStatus status,
            Instant createdAt,
            Instant updatedAt) {

        return new WorkflowExecution(
                workflowId,
                workflowType,
                definitionVersion,
                inputPayload,
                status,
                createdAt,
                updatedAt
        );
    }

    public void start() {
        transitionTo(WorkflowStatus.RUNNING);
    }

    public void complete() {
        transitionTo(WorkflowStatus.COMPLETED);
    }

    public void fail() {
        transitionTo(WorkflowStatus.FAILED);
    }

    public void cancel() {
        transitionTo(WorkflowStatus.CANCELLED);
    }

    private void transitionTo(WorkflowStatus targetStatus) {

        boolean validTransition = switch (status) {
            case PENDING -> targetStatus == WorkflowStatus.RUNNING
                    || targetStatus == WorkflowStatus.CANCELLED;

            case RUNNING -> targetStatus == WorkflowStatus.COMPLETED
                    || targetStatus == WorkflowStatus.FAILED
                    || targetStatus == WorkflowStatus.CANCELLED;

            default -> false;
        };

        if (!validTransition) {
            throw new InvalidWorkflowTransitionException(
                    "Invalid workflow transition: "
                            + status + " -> " + targetStatus);
        }

        this.status = targetStatus;
        this.updatedAt = Instant.now();
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public String getWorkflowType() {
        return workflowType;
    }

    public int getDefinitionVersion() {
        return definitionVersion;
    }

    public Map<String, Object> getInputPayload() {
        return inputPayload;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
