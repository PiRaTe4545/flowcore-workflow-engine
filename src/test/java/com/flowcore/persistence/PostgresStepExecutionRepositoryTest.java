
package com.flowcore.persistence;

import com.flowcore.application.PersistedStepExecution;
import com.flowcore.application.StepExecutionRepository;
import com.flowcore.application.WorkflowExecutionRepository;
import com.flowcore.domain.StepExecution;
import com.flowcore.domain.WorkflowExecution;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PostgresStepExecutionRepositoryTest {

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
    private StepExecutionRepository stepRepository;

    @Autowired
    private WorkflowExecutionRepository workflowRepository;

    private StepExecution createStepExecution() {
        WorkflowExecution workflow = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", UUID.randomUUID().toString())
        );

        workflowRepository.save(workflow);

        return StepExecution.create(
                workflow.getWorkflowId(),
                "process-payment"
        );
    }

    @Test
    void shouldSaveAndRetrieveStepExecution() {
        StepExecution step = createStepExecution();

        PersistedStepExecution saved = stepRepository.save(step);

        assertThat(saved.version()).isZero();

        PersistedStepExecution retrieved = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(retrieved.stepExecution().getStepExecutionId())
                .isEqualTo(step.getStepExecutionId());

        assertThat(retrieved.stepExecution().getWorkflowId())
                .isEqualTo(step.getWorkflowId());

        assertThat(retrieved.stepExecution().getStepName())
                .isEqualTo("process-payment");

        assertThat(retrieved.stepExecution().getStatus())
                .isEqualTo(step.getStatus());

        assertThat(retrieved.stepExecution().getAttemptCount())
                .isZero();

        assertThat(retrieved.stepExecution().getOutputPayload())
                .isEmpty();

        assertThat(retrieved.version()).isZero();
    }

    @Test
    void shouldPersistAndRetrieveOutputPayload() {
        StepExecution step = createStepExecution();

        step.start(null);

        Map<String, Object> output = Map.of(
                "paymentId", "PAY-12345",
                "paymentStatus", "APPROVED",
                "amount", 149.99
        );

        step.complete(output);

        stepRepository.save(step);

        PersistedStepExecution retrieved = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(retrieved.stepExecution().getOutputPayload())
                .containsEntry("paymentId", "PAY-12345")
                .containsEntry("paymentStatus", "APPROVED")
                .containsEntry("amount", 149.99);
    }

    @Test
    void shouldUpdateAndRetrieveOutputPayload() {
        StepExecution step = createStepExecution();

        stepRepository.save(step);

        PersistedStepExecution persisted = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        persisted.stepExecution().start(null);

        persisted.stepExecution().complete(
                Map.of(
                        "reservationId", "INV-789",
                        "reservationStatus", "CONFIRMED"
                )
        );

        PersistedStepExecution updated =
                stepRepository.update(persisted);

        assertThat(updated.version()).isEqualTo(1);

        PersistedStepExecution retrieved = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(retrieved.stepExecution().getOutputPayload())
                .containsEntry("reservationId", "INV-789")
                .containsEntry("reservationStatus", "CONFIRMED");

        assertThat(retrieved.stepExecution().getStatus().name())
                .isEqualTo("COMPLETED");

        assertThat(retrieved.version()).isEqualTo(1);
    }

    @Test
    void shouldUpdateStepExecution() {
        StepExecution step = createStepExecution();

        stepRepository.save(step);

        PersistedStepExecution persisted = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        Instant timeoutAt = Instant.now().plusSeconds(60);

        persisted.stepExecution().start(timeoutAt);

        PersistedStepExecution updated =
                stepRepository.update(persisted);

        assertThat(updated.version()).isEqualTo(1);

        PersistedStepExecution retrieved = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(retrieved.stepExecution().getStatus().name())
                .isEqualTo("RUNNING");

        assertThat(retrieved.stepExecution().getAttemptCount())
                .isEqualTo(1);

        assertThat(retrieved.stepExecution().getTimeoutAt())
                .isEqualTo(timeoutAt);

        assertThat(retrieved.version()).isEqualTo(1);
    }

    @Test
    void shouldPersistRetryAndTimeoutTimestamps() {
        StepExecution step = createStepExecution();

        stepRepository.save(step);

        PersistedStepExecution persisted = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        persisted.stepExecution().start(
                Instant.now().plusSeconds(60)
        );

        persisted.stepExecution().scheduleRetry(
                Instant.now().plusSeconds(10),
                "Temporary payment failure"
        );

        stepRepository.update(persisted);

        PersistedStepExecution retrieved = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(retrieved.stepExecution().getStatus().name())
                .isEqualTo("PENDING");

        assertThat(retrieved.stepExecution().getNextRetryAt())
                .isNotNull();

        assertThat(retrieved.stepExecution().getTimeoutAt())
                .isNull();

        assertThat(retrieved.stepExecution().getLastError())
                .isEqualTo("Temporary payment failure");

        assertThat(retrieved.stepExecution().getAttemptCount())
                .isEqualTo(1);
    }

    @Test
    void shouldRejectUpdateWhenVersionIsStale() {
        StepExecution step = createStepExecution();

        stepRepository.save(step);

        PersistedStepExecution firstRead = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        PersistedStepExecution secondRead = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        firstRead.stepExecution().start(
                Instant.now().plusSeconds(60)
        );

        PersistedStepExecution updated =
                stepRepository.update(firstRead);

        assertThat(updated.version()).isEqualTo(1);

        secondRead.stepExecution().start(
                Instant.now().plusSeconds(60)
        );

        assertThatThrownBy(
                () -> stepRepository.update(secondRead)
        ).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldReturnEmptyWhenStepExecutionDoesNotExist() {
        Optional<PersistedStepExecution> result =
                stepRepository.findById(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindAllStepExecutionsForWorkflow() {
        WorkflowExecution workflow = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", UUID.randomUUID().toString())
        );
        workflowRepository.save(workflow);

        StepExecution payment = StepExecution.create(
                workflow.getWorkflowId(),
                "process-payment"
        );

        StepExecution inventory = StepExecution.create(
                workflow.getWorkflowId(),
                "reserve-inventory"
        );

        stepRepository.save(payment);
        stepRepository.save(inventory);

        List<PersistedStepExecution> results =
                stepRepository.findByWorkflowId(workflow.getWorkflowId());

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(item -> item.stepExecution().getStepName())
                .containsExactly("process-payment", "reserve-inventory");
    }

    @Test
    void shouldReturnEmptyListWhenWorkflowHasNoStepExecutions() {
        WorkflowExecution workflow = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", UUID.randomUUID().toString())
        );
        workflowRepository.save(workflow);

        List<PersistedStepExecution> results =
                stepRepository.findByWorkflowId(workflow.getWorkflowId());

        assertThat(results).isEmpty();
    }
}
