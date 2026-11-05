
package com.flowcore.api.dto;

import com.flowcore.application.PersistedWorkflowExecution;
import com.flowcore.domain.WorkflowExecution;
import com.flowcore.domain.WorkflowStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WorkflowExecutionResponse(
        UUID workflowId,
        String workflowType,
        int definitionVersion,
        WorkflowStatus status,
        Map<String, Object> inputPayload,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public static WorkflowExecutionResponse from(
            PersistedWorkflowExecution persisted
    ) {
        WorkflowExecution execution = persisted.workflowExecution();

        return new WorkflowExecutionResponse(
                execution.getWorkflowId(),
                execution.getWorkflowType(),
                execution.getDefinitionVersion(),
                execution.getStatus(),
                execution.getInputPayload(),
                execution.getCreatedAt(),
                execution.getUpdatedAt(),
                persisted.version()
        );
    }
}
