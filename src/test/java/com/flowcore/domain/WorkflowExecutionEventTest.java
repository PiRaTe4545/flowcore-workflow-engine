
package com.flowcore.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowExecutionEventTest {

    @Test
    void shouldCreateWorkflowExecutionEvent() {
        UUID workflowId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        WorkflowExecutionEvent event = new WorkflowExecutionEvent(
                null,
                workflowId,
                WorkflowEventType.CREATED,
                null,
                WorkflowStatus.PENDING,
                0,
                Map.of("source", "api"),
                occurredAt
        );

        assertThat(event.eventId()).isNull();
        assertThat(event.workflowId()).isEqualTo(workflowId);
        assertThat(event.eventType()).isEqualTo(WorkflowEventType.CREATED);
        assertThat(event.previousStatus()).isNull();
        assertThat(event.newStatus()).isEqualTo(WorkflowStatus.PENDING);
        assertThat(event.executionVersion()).isZero();
        assertThat(event.eventPayload()).containsEntry("source", "api");
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void shouldRejectNegativeExecutionVersion() {
        assertThatThrownBy(() -> new WorkflowExecutionEvent(
                null,
                UUID.randomUUID(),
                WorkflowEventType.CREATED,
                null,
                WorkflowStatus.PENDING,
                -1,
                Map.of(),
                Instant.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullWorkflowId() {
        assertThatThrownBy(() -> new WorkflowExecutionEvent(
                null,
                null,
                WorkflowEventType.CREATED,
                null,
                WorkflowStatus.PENDING,
                0,
                Map.of(),
                Instant.now()
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldMakeEventPayloadImmutable() {
        WorkflowExecutionEvent event = new WorkflowExecutionEvent(
                null,
                UUID.randomUUID(),
                WorkflowEventType.CREATED,
                null,
                WorkflowStatus.PENDING,
                0,
                Map.of("source", "api"),
                Instant.now()
        );

        assertThatThrownBy(() ->
                event.eventPayload().put("modified", true)
        ).isInstanceOf(UnsupportedOperationException.class);
    }
}
