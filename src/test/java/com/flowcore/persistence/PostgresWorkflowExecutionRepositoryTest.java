
package com.flowcore.persistence;

import com.flowcore.application.PersistedWorkflowExecution;
import com.flowcore.application.WorkflowExecutionRepository;
import com.flowcore.domain.WorkflowExecution;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.dao.OptimisticLockingFailureException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PostgresWorkflowExecutionRepositoryTest {

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
    private WorkflowExecutionRepository repository;

    @Test
    void shouldSaveAndRetrieveWorkflowExecution() {
        Map<String, Object> payload = Map.of(
                "customerId", "customer-123",
                "amount", 125.50,
                "priority", true
        );

        WorkflowExecution execution = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                payload
        );

        PersistedWorkflowExecution saved =
                repository.save(execution);

        assertThat(saved.version()).isZero();

        UUID workflowId = execution.getWorkflowId();

        PersistedWorkflowExecution retrieved = repository
                .findById(workflowId)
                .orElseThrow();

        assertThat(retrieved.workflowExecution().getWorkflowId())
                .isEqualTo(workflowId);

        assertThat(retrieved.workflowExecution().getWorkflowType())
                .isEqualTo("ORDER_PROCESSING");

        assertThat(retrieved.workflowExecution().getDefinitionVersion())
                .isEqualTo(1);

        assertThat(retrieved.workflowExecution().getStatus().name())
                .isEqualTo("PENDING");

        assertThat(retrieved.workflowExecution().getInputPayload())
                .containsEntry("customerId", "customer-123")
                .containsEntry("priority", true);

        assertThat(retrieved.version()).isZero();
    }


    @Test
    void shouldRejectUpdateWhenVersionIsStale() {
        WorkflowExecution execution = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-123")
        );

        repository.save(execution);

        PersistedWorkflowExecution firstRead = repository
                .findById(execution.getWorkflowId())
                .orElseThrow();

        PersistedWorkflowExecution secondRead = repository
                .findById(execution.getWorkflowId())
                .orElseThrow();

        firstRead.workflowExecution().start();

        PersistedWorkflowExecution updated = repository.update(
                firstRead.workflowExecution(),
                firstRead.version()
        );

        assertThat(updated.version()).isEqualTo(1);

        secondRead.workflowExecution().start();

        assertThatThrownBy(() -> repository.update(
                secondRead.workflowExecution(),
                secondRead.version()
        )).isInstanceOf(
                OptimisticLockingFailureException.class
        );
    }


    @Test
    void shouldReturnEmptyWhenWorkflowDoesNotExist() {
        Optional<PersistedWorkflowExecution> result =
                repository.findById(UUID.randomUUID());

        assertThat(result).isEmpty();
    }


}
