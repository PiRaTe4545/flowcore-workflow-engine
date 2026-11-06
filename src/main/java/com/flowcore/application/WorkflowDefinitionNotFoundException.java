package com.flowcore.application;

public class WorkflowDefinitionNotFoundException extends RuntimeException {

    public WorkflowDefinitionNotFoundException(
            String workflowType,
            int version
    ) {
        super(
                "Workflow definition not found: "
                        + workflowType + ", version: " + version
        );
    }
}