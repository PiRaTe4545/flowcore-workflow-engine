
package com.flowcore.api;

import com.flowcore.api.dto.CreateWorkflowRequest;
import com.flowcore.api.dto.WorkflowExecutionResponse;
import com.flowcore.application.PersistedWorkflowExecution;
import com.flowcore.application.WorkflowExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowExecutionController {

    private final WorkflowExecutionService service;

    public WorkflowExecutionController(
            WorkflowExecutionService service
    ) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowExecutionResponse create(
            @Valid @RequestBody CreateWorkflowRequest request
    ) {
        PersistedWorkflowExecution persisted = service.create(
                request.workflowType(),
                request.definitionVersion(),
                request.inputPayload()
        );

        return WorkflowExecutionResponse.from(persisted);
    }

    @GetMapping("/{workflowId}")
    public WorkflowExecutionResponse getById(
            @PathVariable UUID workflowId
    ) {
        return WorkflowExecutionResponse.from(
                service.getById(workflowId)
        );
    }


    @PostMapping("/{workflowId}/start")
    public WorkflowExecutionResponse start(
            @PathVariable UUID workflowId
    ) {
        return WorkflowExecutionResponse.from(
                service.start(workflowId)
        );
    }

    @PostMapping("/{workflowId}/complete")
    public WorkflowExecutionResponse complete(
            @PathVariable UUID workflowId
    ) {
        return WorkflowExecutionResponse.from(
                service.complete(workflowId)
        );
    }

    @PostMapping("/{workflowId}/fail")
    public WorkflowExecutionResponse fail(
            @PathVariable UUID workflowId
    ) {
        return WorkflowExecutionResponse.from(
                service.fail(workflowId)
        );
    }

    @PostMapping("/{workflowId}/cancel")
    public WorkflowExecutionResponse cancel(
            @PathVariable UUID workflowId
    ) {
        return WorkflowExecutionResponse.from(
                service.cancel(workflowId)
        );
    }

}
