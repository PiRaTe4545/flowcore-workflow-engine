package com.flowcore.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcore.api.dto.CreateWorkflowDefinitionRequest;
import com.flowcore.definition.model.StepType;
import com.flowcore.definition.model.WorkflowStepDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WorkflowDefinitionControllerTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM workflow_definitions");
    }

    @Test
    void shouldRegisterWorkflowDefinition() throws Exception {
        mockMvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workflowType").value("ORDER_PROCESSING"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].name").value("validate-order"))
                .andExpect(jsonPath("$.steps[0].type").value("SERVICE_TASK"));
    }

    @Test
    void shouldRetrieveWorkflowDefinition() throws Exception {
        registerDefinition(1);

        mockMvc.perform(get(
                        "/api/v1/workflow-definitions/ORDER_PROCESSING/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowType").value("ORDER_PROCESSING"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.steps.length()").value(2));
    }

    @Test
    void shouldReturnNotFoundWhenDefinitionDoesNotExist() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/workflow-definitions/UNKNOWN/versions/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnConflictForDuplicateDefinitionVersion() throws Exception {
        registerDefinition(1);

        mockMvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(1))))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectInvalidDefinitionRequest() throws Exception {
        CreateWorkflowDefinitionRequest invalidRequest =
                new CreateWorkflowDefinitionRequest(
                        "",
                        0,
                        List.of()
                );

        mockMvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }


    @Test
    void shouldRegisterAndRetrieveWorkflowDefinitionWithRetryPolicy() throws Exception {
        String request = """
            {
                "workflowType": "RETRY_WORKFLOW",
                "version": 1,
                "steps": [
                    {
                        "name": "process-payment",
                        "type": "SERVICE_TASK",
                        "retryPolicy": {
                            "maxAttempts": 5,
                            "initialInterval": "PT2S",
                            "backoffMultiplier": 2.0,
                            "maxInterval": "PT30S"
                        }
                    }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(get(
                        "/api/v1/workflow-definitions/RETRY_WORKFLOW/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].name").value("process-payment"))
                .andExpect(jsonPath("$.steps[0].retryPolicy.maxAttempts").value(5))
                .andExpect(jsonPath("$.steps[0].retryPolicy.initialInterval").value("PT2S"))
                .andExpect(jsonPath("$.steps[0].retryPolicy.backoffMultiplier").value(2.0))
                .andExpect(jsonPath("$.steps[0].retryPolicy.maxInterval").value("PT30S"));
    }

    @Test
    void shouldRejectWorkflowDefinitionWithInvalidRetryPolicy() throws Exception {
        String request = """
            {
                "workflowType": "INVALID_RETRY_WORKFLOW",
                "version": 1,
                "steps": [
                    {
                        "name": "process-payment",
                        "type": "SERVICE_TASK",
                        "retryPolicy": {
                            "maxAttempts": 0,
                            "initialInterval": "PT2S",
                            "backoffMultiplier": 2.0,
                            "maxInterval": "PT30S"
                        }
                    }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRegisterAndRetrieveWorkflowDefinitionWithTimeoutPolicy() throws Exception {
        String request = """
            {
                "workflowType": "TIMEOUT_WORKFLOW",
                "version": 1,
                "steps": [
                    {
                        "name": "process-payment",
                        "type": "SERVICE_TASK",
                        "timeoutPolicy": {
                            "timeout": "PT60S"
                        }
                    }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(get(
                        "/api/v1/workflow-definitions/TIMEOUT_WORKFLOW/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].name").value("process-payment"))
                .andExpect(jsonPath("$.steps[0].timeoutPolicy.timeout").value("PT1M"));
    }

    @Test
    void shouldRejectWorkflowDefinitionWithZeroTimeout() throws Exception {
        String request = """
            {
                "workflowType": "INVALID_TIMEOUT_WORKFLOW",
                "version": 1,
                "steps": [
                    {
                        "name": "process-payment",
                        "type": "SERVICE_TASK",
                        "timeoutPolicy": {
                            "timeout": "PT0S"
                        }
                    }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }


    private void registerDefinition(int version) throws Exception {
        mockMvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(version))))
                .andExpect(status().isCreated());
    }

    private CreateWorkflowDefinitionRequest createRequest(int version) {
        return new CreateWorkflowDefinitionRequest(
                "ORDER_PROCESSING",
                version,
                List.of(
                        new WorkflowStepDefinition(
                                "validate-order",
                                StepType.SERVICE_TASK
                        ),
                        new WorkflowStepDefinition(
                                "wait-for-payment",
                                StepType.WAIT
                        )
                )
        );
    }
}