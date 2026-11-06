
package com.flowcore.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcore.api.dto.CreateWorkflowRequest;
import com.flowcore.api.dto.WorkflowExecutionResponse;
import com.flowcore.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.flowcore.application.WorkflowDefinitionRepository;
import com.flowcore.definition.model.WorkflowDefinition;
import com.flowcore.definition.model.WorkflowStepDefinition;
import com.flowcore.definition.model.StepType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WorkflowExecutionControllerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateWorkflow() throws Exception {
        registerOrderProcessingDefinition();
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-123")
        );

        String response = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workflowId").exists())
                .andExpect(jsonPath("$.workflowType")
                        .value("ORDER_PROCESSING"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        WorkflowExecutionResponse result =
                objectMapper.readValue(
                        response,
                        WorkflowExecutionResponse.class
                );

        assertThat(result.workflowId()).isNotNull();
    }

    @Test
    void shouldRetrieveWorkflow() throws Exception {
        registerOrderProcessingDefinition();
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-456")
        );

        String response = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        WorkflowExecutionResponse created =
                objectMapper.readValue(
                        response,
                        WorkflowExecutionResponse.class
                );

        mockMvc.perform(get(
                        "/api/v1/workflows/{workflowId}",
                        created.workflowId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId")
                        .value(created.workflowId().toString()))
                .andExpect(jsonPath("$.workflowType")
                        .value("ORDER_PROCESSING"))
                .andExpect(jsonPath("$.status")
                        .value(WorkflowStatus.PENDING.name()))
                .andExpect(jsonPath("$.inputPayload.orderId")
                        .value("order-456"));
    }


    @Test
    void shouldExecuteWorkflowLifecycleThroughApi() throws Exception {
        registerOrderProcessingDefinition();
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-789")
        );

        String response = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        WorkflowExecutionResponse created =
                objectMapper.readValue(
                        response,
                        WorkflowExecutionResponse.class
                );

        UUID workflowId = created.workflowId();

        mockMvc.perform(post(
                        "/api/v1/workflows/{id}/start",
                        workflowId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post(
                        "/api/v1/workflows/{id}/complete",
                        workflowId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get(
                        "/api/v1/workflows/{id}",
                        workflowId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.version").value(2));
    }


    @Test
    void shouldReturn404WhenWorkflowDoesNotExist() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/workflows/{id}",
                        UUID.randomUUID()
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString(
                                "Workflow execution not found"
                        )));
    }


    @Test
    void shouldReturn400ForInvalidWorkflowRequest() throws Exception {
        String invalidRequest = """
            {
                "workflowType": "",
                "definitionVersion": 0,
                "inputPayload": null
            }
            """;

        mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }


    @Test
    void shouldReturn409ForInvalidWorkflowTransition() throws Exception {
        registerOrderProcessingDefinition();
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-999")
        );

        String response = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        WorkflowExecutionResponse created =
                objectMapper.readValue(
                        response,
                        WorkflowExecutionResponse.class
                );

        mockMvc.perform(post(
                        "/api/v1/workflows/{id}/complete",
                        created.workflowId()
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void shouldReturn404WhenWorkflowDefinitionDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "workflowType": "UNKNOWN_WORKFLOW",
                                "definitionVersion": 1,
                                "inputPayload": {
                                    "orderId": "order-123"
                                }
                            }
                            """))
                .andExpect(status().isNotFound());
    }

    private void registerOrderProcessingDefinition() {
        boolean exists = workflowDefinitionRepository
                .findByTypeAndVersion("ORDER_PROCESSING", 1)
                .isPresent();

        if (!exists) {
            workflowDefinitionRepository.register(
                    new WorkflowDefinition(
                            "ORDER_PROCESSING",
                            1,
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
                    )
            );
        }
    }

}
