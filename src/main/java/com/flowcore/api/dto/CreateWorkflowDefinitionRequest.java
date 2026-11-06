package com.flowcore.api.dto;

import com.flowcore.definition.model.WorkflowStepDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateWorkflowDefinitionRequest(

        @NotBlank(message = "workflowType cannot be blank")
        String workflowType,

        @Positive(message = "version must be positive")
        int version,

        @NotEmpty(message = "steps cannot be empty")
        List<@NotNull @Valid WorkflowStepDefinition> steps
) {
}