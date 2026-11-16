
package com.flowcore.worker;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderValidationWorkerTest {

    private final OrderValidationWorker worker =
            new OrderValidationWorker();

    @Test
    void shouldIdentifyOrderValidationTaskType() {
        assertThat(worker.taskType())
                .isEqualTo("ORDER_VALIDATION");
    }

    @Test
    void shouldReturnSuccessfulValidationResult() {
        WorkflowTaskContext context = new WorkflowTaskContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ORDER_VALIDATION",
                Map.of("orderId", "ORD-123")
        );

        WorkflowTaskResult result = worker.execute(context);

        assertThat(result.outputPayload())
                .containsEntry("valid", true)
                .containsEntry("validationStatus", "APPROVED");
    }
}
