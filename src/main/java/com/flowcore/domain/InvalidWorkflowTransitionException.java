
package com.flowcore.domain;

public class InvalidWorkflowTransitionException
        extends IllegalStateException {

    public InvalidWorkflowTransitionException(String message) {
        super(message);
    }
}
