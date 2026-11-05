
package com.flowcore.application;

import java.util.Optional;
import java.util.UUID;

import com.flowcore.domain.WorkflowExecution;

public interface WorkflowExecutionRepository {

    PersistedWorkflowExecution save(WorkflowExecution workflowExecution);

    Optional<PersistedWorkflowExecution> findById(UUID workflowId);

    PersistedWorkflowExecution update(
            WorkflowExecution workflowExecution,
            long expectedVersion
    );
}
