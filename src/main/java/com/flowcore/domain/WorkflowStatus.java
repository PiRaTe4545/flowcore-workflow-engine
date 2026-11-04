
package com.flowcore.domain;

public enum WorkflowStatus {

    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED
                || this == FAILED
                || this == CANCELLED;
    }
}
