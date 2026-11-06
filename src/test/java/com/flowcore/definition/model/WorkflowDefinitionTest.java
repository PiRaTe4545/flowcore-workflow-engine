package com.flowcore.definition.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowDefinitionTest {

    @Test
    void shouldCreateWorkflowDefinition() {
        WorkflowDefinition definition = new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                List.of(
                        new WorkflowStepDefinition("validate-order", StepType.SERVICE_TASK),
                        new WorkflowStepDefinition("wait-for-payment", StepType.WAIT)
                )
        );

        assertEquals("ORDER_PROCESSING", definition.workflowType());
        assertEquals(1, definition.version());
        assertEquals(2, definition.steps().size());
        assertEquals("validate-order", definition.steps().get(0).name());
    }

    @Test
    void shouldRejectBlankWorkflowType() {
        assertThrows(IllegalArgumentException.class, () ->
                new WorkflowDefinition("", 1, List.of(
                        new WorkflowStepDefinition("step-1", StepType.SERVICE_TASK)
                ))
        );
    }

    @Test
    void shouldRejectInvalidVersion() {
        assertThrows(IllegalArgumentException.class, () ->
                new WorkflowDefinition("ORDER_PROCESSING", 0, List.of(
                        new WorkflowStepDefinition("step-1", StepType.SERVICE_TASK)
                ))
        );
    }

    @Test
    void shouldRejectEmptySteps() {
        assertThrows(IllegalArgumentException.class, () ->
                new WorkflowDefinition("ORDER_PROCESSING", 1, List.of())
        );
    }

    @Test
    void shouldRejectDuplicateStepNames() {
        assertThrows(IllegalArgumentException.class, () ->
                new WorkflowDefinition(
                        "ORDER_PROCESSING",
                        1,
                        List.of(
                                new WorkflowStepDefinition("validate-order", StepType.SERVICE_TASK),
                                new WorkflowStepDefinition("validate-order", StepType.WAIT)
                        )
                )
        );
    }

    @Test
    void shouldPreventModificationOfSteps() {
        WorkflowDefinition definition = new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                List.of(new WorkflowStepDefinition("step-1", StepType.SERVICE_TASK))
        );

        assertThrows(UnsupportedOperationException.class, () ->
                definition.steps().add(
                        new WorkflowStepDefinition("step-2", StepType.WAIT)
                )
        );
    }
}