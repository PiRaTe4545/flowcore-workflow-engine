
package com.flowcore.worker;

import java.util.Map;
import java.util.Objects;

public record WorkflowTaskResult(
        Map<String, Object> outputPayload
) {

    public WorkflowTaskResult {
        Objects.requireNonNull(
                outputPayload,
                "outputPayload cannot be null"
        );

        outputPayload = Map.copyOf(outputPayload);
    }

    public static WorkflowTaskResult success(
            Map<String, Object> outputPayload
    ) {
        return new WorkflowTaskResult(outputPayload);
    }

    public static WorkflowTaskResult empty() {
        return new WorkflowTaskResult(Map.of());
    }
}
