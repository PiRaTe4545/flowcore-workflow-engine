package com.flowcore.domain;

public enum StepExecutionStatus {

    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == COMPLETED
                || this == FAILED
                || this == TIMED_OUT;
    }
}