package com.flowcore.api.dto;

import com.flowcore.definition.model.WorkflowDefinition;
import com.flowcore.definition.model.WorkflowStepDefinition;

import java.util.List;

public record WorkflowDefinitionResponse(
        String workflowType,
        int version,
        List<WorkflowStepDefinition> steps
) {
    public static WorkflowDefinitionResponse from(
            WorkflowDefinition definition
    ) {
        return new WorkflowDefinitionResponse(
                definition.workflowType(),
                definition.version(),
                definition.steps()
        );
    }
}