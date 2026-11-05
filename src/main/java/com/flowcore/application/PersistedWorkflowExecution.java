
package com.flowcore.application;

import com.flowcore.domain.WorkflowExecution;

import java.util.Objects;

/**
 * Represents a workflow execution loaded from persistence,
 * together with its database optimistic-locking version.
 */
public record PersistedWorkflowExecution(
        WorkflowExecution workflowExecution,
        long version
) {

    public PersistedWorkflowExecution {
        Objects.requireNonNull(
                workflowExecution,
                "workflowExecution cannot be null"
        );

        if (version < 0) {
            throw new IllegalArgumentException(
                    "version cannot be negative"
            );
        }
    }
}
