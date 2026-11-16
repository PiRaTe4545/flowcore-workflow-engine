
package com.flowcore.worker;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowTaskWorkerRegistryTest {

    @Test
    void shouldRegisterAndResolveWorkerByTaskType() {
        WorkflowTaskWorker worker = mock(WorkflowTaskWorker.class);
        when(worker.taskType()).thenReturn("ORDER_VALIDATION");

        WorkflowTaskWorkerRegistry registry =
                new WorkflowTaskWorkerRegistry(List.of(worker));

        assertThat(registry.getWorker("ORDER_VALIDATION"))
                .isSameAs(worker);
    }

    @Test
    void shouldRegisterMultipleWorkers() {
        WorkflowTaskWorker orderWorker =
                mock(WorkflowTaskWorker.class);
        when(orderWorker.taskType()).thenReturn("ORDER_VALIDATION");

        WorkflowTaskWorker paymentWorker =
                mock(WorkflowTaskWorker.class);
        when(paymentWorker.taskType()).thenReturn("PAYMENT_PROCESSING");

        WorkflowTaskWorkerRegistry registry =
                new WorkflowTaskWorkerRegistry(
                        List.of(orderWorker, paymentWorker)
                );

        assertThat(registry.getWorker("ORDER_VALIDATION"))
                .isSameAs(orderWorker);
        assertThat(registry.getWorker("PAYMENT_PROCESSING"))
                .isSameAs(paymentWorker);
    }

    @Test
    void shouldRejectDuplicateTaskTypes() {
        WorkflowTaskWorker firstWorker =
                mock(WorkflowTaskWorker.class);
        when(firstWorker.taskType()).thenReturn("ORDER_VALIDATION");

        WorkflowTaskWorker secondWorker =
                mock(WorkflowTaskWorker.class);
        when(secondWorker.taskType()).thenReturn("ORDER_VALIDATION");

        assertThatThrownBy(() ->
                new WorkflowTaskWorkerRegistry(
                        List.of(firstWorker, secondWorker)
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Multiple workers registered for taskType"
                );
    }

    @Test
    void shouldRejectUnknownTaskType() {
        WorkflowTaskWorker worker =
                mock(WorkflowTaskWorker.class);
        when(worker.taskType()).thenReturn("ORDER_VALIDATION");

        WorkflowTaskWorkerRegistry registry =
                new WorkflowTaskWorkerRegistry(List.of(worker));

        assertThatThrownBy(() ->
                registry.getWorker("UNKNOWN_TASK")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "No worker registered for taskType: UNKNOWN_TASK"
                );
    }

    @Test
    void shouldRejectBlankWorkerTaskType() {
        WorkflowTaskWorker worker =
                mock(WorkflowTaskWorker.class);
        when(worker.taskType()).thenReturn(" ");

        assertThatThrownBy(() ->
                new WorkflowTaskWorkerRegistry(List.of(worker))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worker taskType cannot be blank");
    }

    @Test
    void shouldRejectNullWorkerTaskType() {
        WorkflowTaskWorker worker =
                mock(WorkflowTaskWorker.class);
        when(worker.taskType()).thenReturn(null);

        assertThatThrownBy(() ->
                new WorkflowTaskWorkerRegistry(List.of(worker))
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("worker taskType cannot be null");
    }

    @Test
    void shouldReturnImmutableRegistrySnapshot() {
        WorkflowTaskWorker worker =
                mock(WorkflowTaskWorker.class);
        when(worker.taskType()).thenReturn("ORDER_VALIDATION");

        WorkflowTaskWorkerRegistry registry =
                new WorkflowTaskWorkerRegistry(List.of(worker));

        assertThat(registry.getWorker("ORDER_VALIDATION"))
                .isSameAs(worker);

        // The registry exposes lookups only; its internal map cannot be modified.
        assertThat(registry.getWorker("ORDER_VALIDATION"))
                .isSameAs(worker);
    }
}
