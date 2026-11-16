
package com.flowcore.definition.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowStepDefinitionTest {

    @Test
    void shouldCreateStepWithRetryPolicy() {
        RetryPolicy retryPolicy = new RetryPolicy(
                5,
                Duration.ofSeconds(2),
                2.0,
                Duration.ofSeconds(30)
        );

        WorkflowStepDefinition step = new WorkflowStepDefinition(
                "process-payment",
                StepType.SERVICE_TASK,
                "ORDER_VALIDATION",
                retryPolicy,
                null
        );

        assertEquals("process-payment", step.name());
        assertEquals(StepType.SERVICE_TASK, step.type());
        assertEquals("ORDER_VALIDATION", step.taskType());
        assertEquals(retryPolicy, step.retryPolicy());
    }

    @Test
    void shouldAllowStepWithoutExplicitRetryPolicy() {
        WorkflowStepDefinition step = new WorkflowStepDefinition(
                "validate-order",
                StepType.SERVICE_TASK,
                "ORDER_VALIDATION",
                null,
                null
        );

        assertNull(step.retryPolicy());
    }

    @Test
    void shouldRejectBlankStepName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowStepDefinition(
                        " ",
                        StepType.SERVICE_TASK,
                        "ORDER_VALIDATION",
                        null,
                        null
                )
        );
    }

    @Test
    void shouldRejectNullStepType() {
        assertThrows(
                NullPointerException.class,
                () -> new WorkflowStepDefinition(
                        "validate-order",
                        null
                )
        );
    }

    @Test
    void shouldCreateStepWithRetryAndTimeoutPolicies() {
        RetryPolicy retryPolicy = new RetryPolicy(
                3,
                Duration.ofSeconds(2),
                2.0,
                Duration.ofSeconds(30)
        );

        TimeoutPolicy timeoutPolicy =
                new TimeoutPolicy(Duration.ofSeconds(60));

        WorkflowStepDefinition step = new WorkflowStepDefinition(
                "process-payment",
                StepType.SERVICE_TASK,
                "ORDER_VALIDATION",
                retryPolicy,
                timeoutPolicy
        );

        assertEquals(retryPolicy, step.retryPolicy());
        assertEquals(timeoutPolicy, step.timeoutPolicy());
    }

    @Test
    void shouldAllowStepWithoutTimeoutPolicy() {
        WorkflowStepDefinition step = new WorkflowStepDefinition(
                "process-payment",
                StepType.SERVICE_TASK,
                "ORDER_VALIDATION",
                null,
                null
        );

        assertNull(step.timeoutPolicy());
    }

    @Test
    void shouldRejectServiceTaskWithoutTaskType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowStepDefinition(
                        "validate-order",
                        StepType.SERVICE_TASK,
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    void shouldRejectWaitStepWithTaskType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowStepDefinition(
                        "wait-for-payment",
                        StepType.WAIT,
                        "PAYMENT_PROCESSING",
                        null,
                        null
                )
        );
    }
}
