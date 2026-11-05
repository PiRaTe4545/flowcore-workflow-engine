
package com.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

public record CreateWorkflowRequest(

        @NotBlank(message = "workflowType cannot be blank")
        String workflowType,

        @Positive(message = "definitionVersion must be positive")
        int definitionVersion,

        @NotNull(message = "inputPayload cannot be null")
        Map<String, Object> inputPayload
) {
}
