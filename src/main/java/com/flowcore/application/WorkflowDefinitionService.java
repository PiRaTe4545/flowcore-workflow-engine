package com.flowcore.application;

import com.flowcore.definition.model.WorkflowDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class WorkflowDefinitionService {

    private final WorkflowDefinitionRepository repository;

    public WorkflowDefinitionService(WorkflowDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WorkflowDefinition register(WorkflowDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");

        return repository.register(definition);
    }

    @Transactional(readOnly = true)
    public WorkflowDefinition getByTypeAndVersion(
            String workflowType,
            int version
    ) {
        Objects.requireNonNull(workflowType, "workflowType cannot be null");

        return repository.findByTypeAndVersion(workflowType, version)
                .orElseThrow(() ->
                        new WorkflowDefinitionNotFoundException(
                                workflowType,
                                version
                        )
                );
    }
}