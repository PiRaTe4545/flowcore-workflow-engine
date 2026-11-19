
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

import java.util.List;
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

    @Test
    void shouldReturnOnlyRunningWorkflowExecutions() {

        WorkflowExecution runningExecution1 = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-101")
        );

        WorkflowExecution runningExecution2 = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-102")
        );

        WorkflowExecution pendingExecution = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-103")
        );

        repository.save(runningExecution1);
        repository.save(runningExecution2);
        repository.save(pendingExecution);

        PersistedWorkflowExecution persisted1 = repository.findById(
                runningExecution1.getWorkflowId()
        ).orElseThrow();

        PersistedWorkflowExecution persisted2 = repository.findById(
                runningExecution2.getWorkflowId()
        ).orElseThrow();

        persisted1.workflowExecution().start();

        repository.update(
                persisted1.workflowExecution(),
                persisted1.version()
        );

        persisted2.workflowExecution().start();

        repository.update(
                persisted2.workflowExecution(),
                persisted2.version()
        );

        List<PersistedWorkflowExecution> runningWorkflows =
                repository.findRunning(100);

        assertThat(runningWorkflows)
                .extracting(
                        persisted ->
                                persisted.workflowExecution().getWorkflowId()
                )
                .contains(
                        runningExecution1.getWorkflowId(),
                        runningExecution2.getWorkflowId()
                )
                .doesNotContain(
                        pendingExecution.getWorkflowId()
                );
    }

    @Test
    void shouldRejectNonPositiveLimitWhenFindingRunningWorkflows() {

        assertThatThrownBy(() -> repository.findRunning(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be positive");

        assertThatThrownBy(() -> repository.findRunning(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be positive");
    }

    @Test
    void shouldRespectLimitWhenFindingRunningWorkflows() {

        for (int i = 0; i < 3; i++) {
            WorkflowExecution execution = WorkflowExecution.create(
                    "ORDER_PROCESSING",
                    1,
                    Map.of("orderId", "order-" + i)
            );

            PersistedWorkflowExecution persisted =
                    repository.save(execution);

            persisted.workflowExecution().start();

            repository.update(
                    persisted.workflowExecution(),
                    persisted.version()
            );
        }

        List<PersistedWorkflowExecution> result =
                repository.findRunning(2);

        assertThat(result).hasSize(2);
    }


}
