
package com.flowcore.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowExecutionTest {

    @Test
    void shouldCreateWorkflowInPendingState() {
        WorkflowExecution execution = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "ORD-1001")
        );

        assertNotNull(execution.getWorkflowId());
        assertEquals("ORDER_PROCESSING", execution.getWorkflowType());
        assertEquals(1, execution.getDefinitionVersion());
        assertEquals(WorkflowStatus.PENDING, execution.getStatus());
        assertEquals(execution.getCreatedAt(), execution.getUpdatedAt());
    }

    @Test
    void shouldTransitionFromPendingToRunning() {
        WorkflowExecution execution = createWorkflow();

        execution.start();

        assertEquals(WorkflowStatus.RUNNING, execution.getStatus());
    }

    @Test
    void shouldTransitionFromRunningToCompleted() {
        WorkflowExecution execution = createWorkflow();

        execution.start();
        execution.complete();

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
    }

    @Test
    void shouldTransitionFromRunningToFailed() {
        WorkflowExecution execution = createWorkflow();

        execution.start();
        execution.fail();

        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
    }

    @Test
    void shouldAllowCancellationFromPending() {
        WorkflowExecution execution = createWorkflow();

        execution.cancel();

        assertEquals(WorkflowStatus.CANCELLED, execution.getStatus());
    }

    @Test
    void shouldAllowCancellationFromRunning() {
        WorkflowExecution execution = createWorkflow();

        execution.start();
        execution.cancel();

        assertEquals(WorkflowStatus.CANCELLED, execution.getStatus());
    }

    @Test
    void shouldRejectCompletionFromPending() {
        WorkflowExecution execution = createWorkflow();

        assertThrows(
                InvalidWorkflowTransitionException.class,
                execution::complete
        );
    }

    @Test
    void shouldRejectRestartAfterCompletion() {
        WorkflowExecution execution = createWorkflow();

        execution.start();
        execution.complete();

        assertThrows(
                InvalidWorkflowTransitionException.class,
                execution::start
        );
    }

    @Test
    void shouldRejectTransitionAfterFailure() {
        WorkflowExecution execution = createWorkflow();

        execution.start();
        execution.fail();

        assertThrows(
                InvalidWorkflowTransitionException.class,
                execution::complete
        );
    }

    @Test
    void shouldRejectBlankWorkflowType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowExecution.create(
                        " ",
                        1,
                        Map.of()
                )
        );
    }

    @Test
    void shouldRejectInvalidDefinitionVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowExecution.create(
                        "ORDER_PROCESSING",
                        0,
                        Map.of()
                )
        );
    }

    @Test
    void shouldRestoreWorkflowWithOriginalStateAndTimestamps() {
        UUID workflowId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-10-01T10:05:00Z");

        WorkflowExecution execution = WorkflowExecution.restore(
                workflowId,
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "ORD-1001"),
                WorkflowStatus.RUNNING,
                createdAt,
                updatedAt
        );

        assertEquals(workflowId, execution.getWorkflowId());
        assertEquals(WorkflowStatus.RUNNING, execution.getStatus());
        assertEquals(createdAt, execution.getCreatedAt());
        assertEquals(updatedAt, execution.getUpdatedAt());
    }

    private WorkflowExecution createWorkflow() {
        return WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "ORD-1001")
        );
    }
}
