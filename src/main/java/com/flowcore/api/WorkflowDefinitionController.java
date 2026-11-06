package com.flowcore.api;

import com.flowcore.api.dto.CreateWorkflowDefinitionRequest;
import com.flowcore.api.dto.WorkflowDefinitionResponse;
import com.flowcore.application.WorkflowDefinitionService;
import com.flowcore.definition.model.WorkflowDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/workflow-definitions")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService service;

    public WorkflowDefinitionController(WorkflowDefinitionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDefinitionResponse register(
            @Valid @RequestBody CreateWorkflowDefinitionRequest request
    ) {
        WorkflowDefinition definition = new WorkflowDefinition(
                request.workflowType(),
                request.version(),
                request.steps()
        );

        return WorkflowDefinitionResponse.from(
                service.register(definition)
        );
    }

    @GetMapping("/{workflowType}/versions/{version}")
    public WorkflowDefinitionResponse getByTypeAndVersion(
            @PathVariable String workflowType,
            @PathVariable int version
    ) {
        return WorkflowDefinitionResponse.from(
                service.getByTypeAndVersion(workflowType, version)
        );
    }
}