
package com.flowcore.worker;

import com.flowcore.application.PersistedStepExecution;
import com.flowcore.definition.model.StepType;
import com.flowcore.definition.model.WorkflowStepDefinition;
import com.flowcore.domain.StepExecution;
import com.flowcore.domain.WorkflowExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowTaskContextBuilderTest {

    private WorkflowTaskContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new WorkflowTaskContextBuilder();
    }

    @Test
    void shouldBuildContextWithWorkflowInputAndCompletedPreviousStepOutputs() {
        WorkflowExecution workflow = createWorkflow();

        WorkflowStepDefinition validateStep =
                serviceStep("validate-order", "ORDER_VALIDATION");

        WorkflowStepDefinition paymentStep =
                serviceStep("process-payment", "PAYMENT_PROCESSING");

        WorkflowStepDefinition notificationStep =
                serviceStep("send-notification", "NOTIFICATION");

        PersistedStepExecution completedValidation =
                completedStep(
                        workflow.getWorkflowId(),
                        "validate-order",
                        Map.of("valid", true)
                );

        PersistedStepExecution currentPayment =
                pendingStep(workflow.getWorkflowId(), "process-payment");

        PersistedStepExecution futureNotification =
                completedStep(
                        workflow.getWorkflowId(),
                        "send-notification",
                        Map.of("sent", true)
                );

        WorkflowTaskContext context = builder.build(
                workflow,
                paymentStep,
                List.of(validateStep, paymentStep, notificationStep),
                List.of(
                        completedValidation,
                        currentPayment,
                        futureNotification
                ),
                currentPayment.stepExecution().getStepExecutionId()
        );

        assertEquals(workflow.getWorkflowId(), context.workflowId());
        assertEquals(
                currentPayment.stepExecution().getStepExecutionId(),
                context.stepExecutionId()
        );
        assertEquals("PAYMENT_PROCESSING", context.taskType());

        assertEquals(
                Map.of(
                        "orderId", "ORD-123",
                        "customerId", "CUST-456"
                ),
                context.inputPayload().get("workflow")
        );

        Map<?, ?> stepOutputs =
                (Map<?, ?>) context.inputPayload().get("steps");

        assertEquals(
                Map.of("valid", true),
                stepOutputs.get("validate-order")
        );

        assertFalse(stepOutputs.containsKey("process-payment"));
        assertFalse(stepOutputs.containsKey("send-notification"));
    }

    @Test
    void shouldExcludePreviousStepsThatAreNotCompleted() {
        WorkflowExecution workflow = createWorkflow();

        WorkflowStepDefinition firstStep =
                serviceStep("validate-order", "ORDER_VALIDATION");

        WorkflowStepDefinition secondStep =
                serviceStep("process-payment", "PAYMENT_PROCESSING");

        PersistedStepExecution pendingValidation =
                pendingStep(workflow.getWorkflowId(), "validate-order");

        PersistedStepExecution currentPayment =
                pendingStep(workflow.getWorkflowId(), "process-payment");

        WorkflowTaskContext context = builder.build(
                workflow,
                secondStep,
                List.of(firstStep, secondStep),
                List.of(pendingValidation, currentPayment),
                currentPayment.stepExecution().getStepExecutionId()
        );

        Map<?, ?> stepOutputs =
                (Map<?, ?>) context.inputPayload().get("steps");

        assertTrue(stepOutputs.isEmpty());
    }

    @Test
    void shouldRejectMissingPreviousStepExecution() {
        WorkflowExecution workflow = createWorkflow();

        WorkflowStepDefinition firstStep =
                serviceStep("validate-order", "ORDER_VALIDATION");

        WorkflowStepDefinition secondStep =
                serviceStep("process-payment", "PAYMENT_PROCESSING");

        PersistedStepExecution currentPayment =
                pendingStep(workflow.getWorkflowId(), "process-payment");

        assertThrows(
                IllegalStateException.class,
                () -> builder.build(
                        workflow,
                        secondStep,
                        List.of(firstStep, secondStep),
                        List.of(currentPayment),
                        currentPayment.stepExecution().getStepExecutionId()
                )
        );
    }

    @Test
    void shouldRejectCurrentStepExecutionFromAnotherWorkflow() {
        WorkflowExecution workflow = createWorkflow();

        WorkflowStepDefinition currentStep =
                serviceStep("process-payment", "PAYMENT_PROCESSING");

        PersistedStepExecution stepFromAnotherWorkflow =
                pendingStep(UUID.randomUUID(), "process-payment");

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.build(
                        workflow,
                        currentStep,
                        List.of(currentStep),
                        List.of(stepFromAnotherWorkflow),
                        stepFromAnotherWorkflow.stepExecution().getStepExecutionId()
                )
        );
    }

    @Test
    void shouldRejectCurrentStepNotPresentInDefinition() {
        WorkflowExecution workflow = createWorkflow();

        WorkflowStepDefinition currentStep =
                serviceStep("process-payment", "PAYMENT_PROCESSING");

        WorkflowStepDefinition anotherStep =
                serviceStep("validate-order", "ORDER_VALIDATION");

        PersistedStepExecution persistedStep =
                pendingStep(workflow.getWorkflowId(), "process-payment");

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.build(
                        workflow,
                        currentStep,
                        List.of(anotherStep),
                        List.of(persistedStep),
                        persistedStep.stepExecution().getStepExecutionId()
                )
        );
    }

    private WorkflowExecution createWorkflow() {
        return WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of(
                        "orderId", "ORD-123",
                        "customerId", "CUST-456"
                )
        );
    }

    private WorkflowStepDefinition serviceStep(
            String name,
            String taskType
    ) {
        return new WorkflowStepDefinition(
                name,
                StepType.SERVICE_TASK,
                taskType,
                null,
                null
        );
    }

    private PersistedStepExecution pendingStep(
            UUID workflowId,
            String stepName
    ) {
        return new PersistedStepExecution(
                StepExecution.create(workflowId, stepName),
                0
        );
    }

    private PersistedStepExecution completedStep(
            UUID workflowId,
            String stepName,
            Map<String, Object> output
    ) {
        StepExecution step = StepExecution.create(workflowId, stepName);
        step.start(null);
        step.complete(output);

        return new PersistedStepExecution(step, 1);
    }
}
