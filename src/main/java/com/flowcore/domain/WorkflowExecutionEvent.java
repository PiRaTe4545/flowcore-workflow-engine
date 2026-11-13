
package com.flowcore.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record WorkflowExecutionEvent(
        Long eventId,
        UUID workflowId,
        WorkflowEventType eventType,
        WorkflowStatus previousStatus,
        WorkflowStatus newStatus,
        long executionVersion,
        Map<String, Object> eventPayload,
        Instant occurredAt
) {

    public WorkflowExecutionEvent {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(newStatus, "newStatus cannot be null");
        Objects.requireNonNull(eventPayload, "eventPayload cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");

        if (executionVersion < 0) {
            throw new IllegalArgumentException(
                    "executionVersion cannot be negative"
            );
        }

        eventPayload = Map.copyOf(eventPayload);
    }
}
