
package com.flowcore.persistence;

import com.flowcore.application.WorkflowDefinitionRepository;
import com.flowcore.definition.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class PostgresWorkflowDefinitionRepositoryTest {

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
    private WorkflowDefinitionRepository repository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM workflow_definitions");
    }

    @Test
    void shouldRegisterAndRetrieveWorkflowDefinition() {
        WorkflowDefinition definition = createDefinition(1);

        repository.register(definition);

        WorkflowDefinition retrieved = repository
                .findByTypeAndVersion("ORDER_PROCESSING", 1)
                .orElseThrow();

        assertEquals(definition, retrieved);
        assertEquals(2, retrieved.steps().size());

        assertEquals(
                StepType.SERVICE_TASK,
                retrieved.steps().get(0).type()
        );
        assertEquals(
                "ORDER_VALIDATION",
                retrieved.steps().get(0).taskType()
        );

        assertEquals(StepType.WAIT, retrieved.steps().get(1).type());
        assertNull(retrieved.steps().get(1).taskType());
    }

    @Test
    void shouldReturnEmptyWhenDefinitionDoesNotExist() {
        assertTrue(
                repository.findByTypeAndVersion("UNKNOWN_WORKFLOW", 1)
                        .isEmpty()
        );
    }

    @Test
    void shouldRejectDuplicateWorkflowVersion() {
        WorkflowDefinition definition = createDefinition(1);

        repository.register(definition);

        assertThrows(
                DuplicateKeyException.class,
                () -> repository.register(definition)
        );
    }

    @Test
    void shouldAllowDifferentVersionsOfSameWorkflow() {
        repository.register(createDefinition(1));
        repository.register(createDefinition(2));

        assertTrue(
                repository.findByTypeAndVersion("ORDER_PROCESSING", 1)
                        .isPresent()
        );

        assertTrue(
                repository.findByTypeAndVersion("ORDER_PROCESSING", 2)
                        .isPresent()
        );
    }

    @Test
    void shouldPersistAndRetrieveStepRetryPolicy() {
        RetryPolicy retryPolicy = new RetryPolicy(
                5,
                Duration.ofSeconds(2),
                2.0,
                Duration.ofSeconds(30)
        );

        WorkflowDefinition definition = new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                List.of(
                        new WorkflowStepDefinition(
                                "validate-order",
                                StepType.SERVICE_TASK,
                                "ORDER_VALIDATION",
                                retryPolicy,
                                null
                        ),
                        new WorkflowStepDefinition(
                                "wait-for-payment",
                                StepType.WAIT
                        )
                )
        );

        repository.register(definition);

        WorkflowDefinition retrieved = repository
                .findByTypeAndVersion("ORDER_PROCESSING", 1)
                .orElseThrow();

        WorkflowStepDefinition retrievedStep = retrieved.steps().get(0);
        RetryPolicy retrievedPolicy = retrievedStep.retryPolicy();

        assertEquals("ORDER_VALIDATION", retrievedStep.taskType());
        assertNotNull(retrievedPolicy);
        assertEquals(5, retrievedPolicy.maxAttempts());
        assertEquals(Duration.ofSeconds(2), retrievedPolicy.initialInterval());
        assertEquals(2.0, retrievedPolicy.backoffMultiplier());
        assertEquals(Duration.ofSeconds(30), retrievedPolicy.maxInterval());

        assertNull(retrieved.steps().get(1).retryPolicy());
        assertNull(retrieved.steps().get(1).taskType());
    }

    @Test
    void shouldPersistAndRetrieveStepTimeoutPolicy() {
        TimeoutPolicy timeoutPolicy =
                new TimeoutPolicy(Duration.ofSeconds(60));

        WorkflowDefinition definition = new WorkflowDefinition(
                "TIMEOUT_WORKFLOW",
                1,
                List.of(
                        new WorkflowStepDefinition(
                                "process-payment",
                                StepType.SERVICE_TASK,
                                "ORDER_VALIDATION",
                                null,
                                timeoutPolicy
                        ),
                        new WorkflowStepDefinition(
                                "send-notification",
                                StepType.SERVICE_TASK,
                                "NOTIFICATION",
                                null,
                                null
                        )
                )
        );

        repository.register(definition);

        WorkflowDefinition retrieved = repository
                .findByTypeAndVersion("TIMEOUT_WORKFLOW", 1)
                .orElseThrow();

        assertEquals(
                "ORDER_VALIDATION",
                retrieved.steps().get(0).taskType()
        );

        assertEquals(
                Duration.ofSeconds(60),
                retrieved.steps().get(0).timeoutPolicy().timeout()
        );

        assertEquals(
                "NOTIFICATION",
                retrieved.steps().get(1).taskType()
        );

        assertNull(retrieved.steps().get(1).timeoutPolicy());
    }

    private WorkflowDefinition createDefinition(int version) {
        return new WorkflowDefinition(
                "ORDER_PROCESSING",
                version,
                List.of(
                        new WorkflowStepDefinition(
                                "validate-order",
                                StepType.SERVICE_TASK,
                                "ORDER_VALIDATION",
                                null,
                                null
                        ),
                        new WorkflowStepDefinition(
                                "wait-for-payment",
                                StepType.WAIT
                        )
                )
        );
    }
}
