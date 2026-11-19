package com.flowcore.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.flowcore.domain.WorkflowExecution;

public interface WorkflowExecutionRepository {

    PersistedWorkflowExecution save(
            WorkflowExecution workflowExecution
    );

    Optional<PersistedWorkflowExecution> findById(
            UUID workflowId
    );

    List<PersistedWorkflowExecution> findRunning(
            int limit
    );

    PersistedWorkflowExecution update(
            WorkflowExecution workflowExecution,
            long expectedVersion
    );
}