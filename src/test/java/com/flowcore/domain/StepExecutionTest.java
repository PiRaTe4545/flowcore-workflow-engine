
package com.flowcore.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StepExecutionTest {

    private final UUID workflowId = UUID.randomUUID();

    @Test
    void shouldCreateStepExecution() {
        StepExecution step = StepExecution.create(
                workflowId, "process-payment");

        assertNotNull(step.getStepExecutionId());
        assertEquals(workflowId, step.getWorkflowId());
        assertEquals("process-payment", step.getStepName());
        assertEquals(StepExecutionStatus.PENDING, step.getStatus());
        assertEquals(0, step.getAttemptCount());
        assertNull(step.getNextRetryAt());
        assertNull(step.getTimeoutAt());
        assertNull(step.getLastError());
    }

    @Test
    void shouldStartStepAndIncrementAttemptCount() {
        StepExecution step = StepExecution.create(
                workflowId, "process-payment");

        Instant timeoutAt = Instant.now().plusSeconds(60);
        step.start(timeoutAt);

        assertEquals(StepExecutionStatus.RUNNING, step.getStatus());
        assertEquals(1, step.getAttemptCount());
        assertEquals(timeoutAt, step.getTimeoutAt());
    }

    @Test
    void shouldScheduleRetryAndAllowAnotherAttempt() {
        StepExecution step = StepExecution.create(
                workflowId, "process-payment");

        step.start(Instant.now().plusSeconds(60));

        Instant retryAt = Instant.now().plusSeconds(10);
        step.scheduleRetry(retryAt, "Temporary payment failure");

        assertEquals(StepExecutionStatus.PENDING, step.getStatus());
        assertEquals(retryAt, step.getNextRetryAt());
        assertNull(step.getTimeoutAt());
        assertEquals("Temporary payment failure", step.getLastError());

        step.start(Instant.now().plusSeconds(60));

        assertEquals(StepExecutionStatus.RUNNING, step.getStatus());
        assertEquals(2, step.getAttemptCount());
        assertNull(step.getNextRetryAt());
    }

    @Test
    void shouldCompleteRunningStep() {
        StepExecution step = StepExecution.create(
                workflowId, "process-payment");

        step.start(Instant.now().plusSeconds(60));
        step.complete();

        assertEquals(StepExecutionStatus.COMPLETED, step.getStatus());
        assertNull(step.getTimeoutAt());
        assertNull(step.getLastError());
    }

    @Test
    void shouldFailRunningStep() {
        StepExecution step = StepExecution.create(
                workflowId, "process-payment");

        step.start(Instant.now().plusSeconds(60));
        step.fail("Payment service unavailable");

        assertEquals(StepExecutionStatus.FAILED, step.getStatus());
        assertEquals("Payment service unavailable", step.getLastError());
        assertNull(step.getTimeoutAt());
    }

    @Test
    void shouldTimeOutRunningStep() {
        StepExecution step = StepExecution.create(
                workflowId, "process-payment");

        step.start(Instant.now().plusSeconds(60));
        step.timeOut("Step execution deadline exceeded");

        assertEquals(StepExecutionStatus.TIMED_OUT, step.getStatus());
        assertEquals("Step execution deadline exceeded", step.getLastError());
        assertNull(step.getTimeoutAt());
    }

    @Test
    void shouldRejectStartingAlreadyRunningStep() {
        StepExecution step = StepExecution.create(
                workflowId, "process-payment");

        step.start(Instant.now().plusSeconds(60));

        assertThrows(
                InvalidStepExecutionTransitionException.class,
                () -> step.start(Instant.now().plusSeconds(60))
        );
    }

    @Test
    void shouldRejectCompletingPendingStep() {
        StepExecution step = StepExecution.create(
                workflowId, "process-payment");

        assertThrows(
                InvalidStepExecutionTransitionException.class,
                step::complete
        );
    }

    @Test
    void shouldRejectTransitionFromTerminalState() {
        StepExecution step = StepExecution.create(
                workflowId, "process-payment");

        step.start(Instant.now().plusSeconds(60));
        step.complete();

        assertThrows(
                InvalidStepExecutionTransitionException.class,
                () -> step.fail("Late failure")
        );
    }

    @Test
    void shouldRejectBlankRetryError() {
        StepExecution step = StepExecution.create(
                workflowId, "process-payment");

        step.start(Instant.now().plusSeconds(60));

        assertThrows(
                IllegalArgumentException.class,
                () -> step.scheduleRetry(
                        Instant.now().plusSeconds(10), " ")
        );
    }

    @Test
    void shouldRestoreStepExecution() {
        UUID stepId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-01T10:01:00Z");

        StepExecution step = StepExecution.restore(
                stepId,
                workflowId,
                "process-payment",
                StepExecutionStatus.PENDING,
                2,
                Instant.parse("2026-01-01T10:05:00Z"),
                null,
                "Temporary failure",
                createdAt,
                updatedAt
        );

        assertEquals(stepId, step.getStepExecutionId());
        assertEquals(2, step.getAttemptCount());
        assertEquals(StepExecutionStatus.PENDING, step.getStatus());
        assertEquals("Temporary failure", step.getLastError());
    }
}
