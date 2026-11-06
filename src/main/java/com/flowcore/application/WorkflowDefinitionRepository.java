package com.flowcore.application;

import com.flowcore.definition.model.WorkflowDefinition;

import java.util.Optional;

public interface WorkflowDefinitionRepository {

    WorkflowDefinition register(WorkflowDefinition definition);

    Optional<WorkflowDefinition> findByTypeAndVersion(
            String workflowType,
            int version
    );
}