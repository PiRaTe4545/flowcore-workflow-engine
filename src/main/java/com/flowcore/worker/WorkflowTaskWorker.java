
package com.flowcore.worker;

public interface WorkflowTaskWorker {

    /**
     * Identifies the business task handled by this worker.
     */
    String taskType();

    /**
     * Executes the business task using the supplied context.
     */
    WorkflowTaskResult execute(WorkflowTaskContext context);
}
