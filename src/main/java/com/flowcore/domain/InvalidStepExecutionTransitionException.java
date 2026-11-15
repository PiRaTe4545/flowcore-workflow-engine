package com.flowcore.domain;

public class InvalidStepExecutionTransitionException
        extends IllegalStateException {

    public InvalidStepExecutionTransitionException(String message) {
        super(message);
    }
}