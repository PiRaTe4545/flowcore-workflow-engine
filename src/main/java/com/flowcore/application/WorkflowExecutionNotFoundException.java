package com.flowcore.application;

import java.util.UUID;

public class WorkflowExecutionNotFoundException
        extends RuntimeException {

    public WorkflowExecutionNotFoundException(UUID workflowId) {
        super("Workflow execution not found: " + workflowId);
    }
}