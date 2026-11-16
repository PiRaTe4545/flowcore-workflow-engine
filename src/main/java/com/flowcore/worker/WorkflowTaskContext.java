
package com.flowcore.worker;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record WorkflowTaskContext(
        UUID workflowId,
        UUID stepExecutionId,
        String taskType,
        Map<String, Object> inputPayload
) {

    public WorkflowTaskContext {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepExecutionId, "stepExecutionId cannot be null");
        Objects.requireNonNull(taskType, "taskType cannot be null");
        Objects.requireNonNull(inputPayload, "inputPayload cannot be null");

        if (taskType.isBlank()) {
            throw new IllegalArgumentException("taskType cannot be blank");
        }

        inputPayload = Map.copyOf(inputPayload);
    }
}
