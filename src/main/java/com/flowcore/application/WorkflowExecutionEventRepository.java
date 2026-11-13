
package com.flowcore.application;

import com.flowcore.domain.WorkflowExecutionEvent;

import java.util.List;
import java.util.UUID;

public interface WorkflowExecutionEventRepository {

    WorkflowExecutionEvent append(WorkflowExecutionEvent event);

    List<WorkflowExecutionEvent> findByWorkflowId(UUID workflowId);
}
