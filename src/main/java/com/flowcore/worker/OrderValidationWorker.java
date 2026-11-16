
package com.flowcore.worker;

import org.springframework.stereotype.Component;

@Component
public class OrderValidationWorker implements WorkflowTaskWorker {

    @Override
    public String taskType() {
        return "ORDER_VALIDATION";
    }

    @Override
    public WorkflowTaskResult execute(WorkflowTaskContext context) {
        return WorkflowTaskResult.success(
                java.util.Map.of(
                        "valid", true,
                        "validationStatus", "APPROVED"
                )
        );
    }
}
