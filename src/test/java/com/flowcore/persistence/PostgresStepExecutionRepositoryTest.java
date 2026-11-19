
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

    @Test
    void shouldPersistAndUpdateLeaseDetails() {
        StepExecution step = createStepExecution();

        step.start(null);

        Instant initialLeaseExpiry = Instant.now().plusSeconds(60);
        step.acquireLease("worker-1", initialLeaseExpiry);

        stepRepository.save(step);

        PersistedStepExecution persisted = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(persisted.stepExecution().getLeaseOwner())
                .isEqualTo("worker-1");
        assertThat(persisted.stepExecution().getLeaseExpiresAt())
                .isEqualTo(initialLeaseExpiry);
        assertThat(persisted.stepExecution().getLeaseVersion())
                .isEqualTo(1);

        Instant renewedLeaseExpiry = Instant.now().plusSeconds(120);

        persisted.stepExecution().renewLease(
                "worker-1",
                1,
                renewedLeaseExpiry
        );

        stepRepository.update(persisted);

        PersistedStepExecution retrieved = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(retrieved.stepExecution().getLeaseOwner())
                .isEqualTo("worker-1");
        assertThat(retrieved.stepExecution().getLeaseExpiresAt())
                .isEqualTo(renewedLeaseExpiry);
        assertThat(retrieved.stepExecution().getLeaseVersion())
                .isEqualTo(1);
        assertThat(retrieved.version()).isEqualTo(1);
    }

    @Test
    void shouldAtomicallyClaimPendingStep() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();
        Instant timeoutAt = now.plusSeconds(300);
        Instant leaseExpiresAt = now.plusSeconds(60);

        Optional<PersistedStepExecution> result = stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                timeoutAt,
                leaseExpiresAt,
                now
        );

        assertThat(result).isPresent();

        PersistedStepExecution claimed = result.orElseThrow();
        StepExecution claimedStep = claimed.stepExecution();

        assertThat(claimedStep.getStatus().name()).isEqualTo("RUNNING");
        assertThat(claimedStep.getAttemptCount()).isEqualTo(1);
        assertThat(claimedStep.getLeaseOwner()).isEqualTo("worker-1");
        assertThat(claimedStep.getLeaseExpiresAt()).isEqualTo(leaseExpiresAt);
        assertThat(claimedStep.getLeaseVersion()).isEqualTo(1);
        assertThat(claimed.version()).isEqualTo(1);
    }

    @Test
    void shouldNotClaimStepWithActiveLease() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();

        Optional<PersistedStepExecution> firstClaim =
                stepRepository.claim(
                        step.getStepExecutionId(),
                        "worker-1",
                        now.plusSeconds(300),
                        now.plusSeconds(60),
                        now
                );

        assertThat(firstClaim).isPresent();

        Optional<PersistedStepExecution> secondClaim =
                stepRepository.claim(
                        step.getStepExecutionId(),
                        "worker-2",
                        now.plusSeconds(300),
                        now.plusSeconds(120),
                        now
                );

        assertThat(secondClaim).isEmpty();

        PersistedStepExecution persisted = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(persisted.stepExecution().getLeaseOwner())
                .isEqualTo("worker-1");
        assertThat(persisted.stepExecution().getLeaseVersion()).isEqualTo(1);
        assertThat(persisted.version()).isEqualTo(1);
    }

    @Test
    void shouldReclaimStepAfterLeaseExpires() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant firstNow = Instant.now();
        Instant originalTimeoutAt = firstNow.plusSeconds(300);
        stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                originalTimeoutAt,
                firstNow.plusSeconds(30),
                firstNow
        ).orElseThrow();

        Instant recoveryNow = firstNow.plusSeconds(60);
        Instant newLeaseExpiry = recoveryNow.plusSeconds(60);

        Optional<PersistedStepExecution> recovered =
                stepRepository.claim(
                        step.getStepExecutionId(),
                        "worker-2",
                        recoveryNow.plusSeconds(300),
                        newLeaseExpiry,
                        recoveryNow
                );

        assertThat(recovered).isPresent();

        PersistedStepExecution claimed = recovered.orElseThrow();
        assertThat(claimed.stepExecution().getTimeoutAt())
                .isEqualTo(originalTimeoutAt);

        assertThat(claimed.stepExecution().getLeaseOwner())
                .isEqualTo("worker-2");
        assertThat(claimed.stepExecution().getLeaseExpiresAt())
                .isEqualTo(newLeaseExpiry);
        assertThat(claimed.stepExecution().getLeaseVersion()).isEqualTo(2);
        assertThat(claimed.stepExecution().getAttemptCount()).isEqualTo(2);
        assertThat(claimed.version()).isEqualTo(2);
    }

    @Test
    void shouldNotClaimStepBeforeRetryTime() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();

        PersistedStepExecution persisted = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        persisted.stepExecution().start(now.plusSeconds(300));
        persisted.stepExecution().scheduleRetry(
                now.plusSeconds(120),
                "Temporary failure"
        );

        stepRepository.update(persisted);

        Optional<PersistedStepExecution> earlyClaim = stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                now.plusSeconds(300),
                now.plusSeconds(180),
                now.plusSeconds(60)
        );

        assertThat(earlyClaim).isEmpty();

        Optional<PersistedStepExecution> eligibleClaim = stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                now.plusSeconds(300),
                now.plusSeconds(180),
                now.plusSeconds(120)
        );

        assertThat(eligibleClaim).isPresent();
        assertThat(eligibleClaim.orElseThrow().stepExecution().getAttemptCount())
                .isEqualTo(2);
    }

    @Test
    void shouldAllowOnlyOneWorkerToClaimStepConcurrently()
            throws Exception {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();

        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(2);

        try {
            java.util.concurrent.CountDownLatch startGate =
                    new java.util.concurrent.CountDownLatch(1);

            java.util.concurrent.Callable<Optional<PersistedStepExecution>> claim =
                    () -> {
                        startGate.await();

                        return stepRepository.claim(
                                step.getStepExecutionId(),
                                Thread.currentThread().getName(),
                                now.plusSeconds(300),
                                now.plusSeconds(60),
                                now
                        );
                    };

            var first = executor.submit(claim);
            var second = executor.submit(claim);

            startGate.countDown();

            Optional<PersistedStepExecution> firstResult = first.get();
            Optional<PersistedStepExecution> secondResult = second.get();

            assertThat(
                    (firstResult.isPresent() ? 1 : 0)
                            + (secondResult.isPresent() ? 1 : 0)
            ).isEqualTo(1);

            PersistedStepExecution persisted = stepRepository
                    .findById(step.getStepExecutionId())
                    .orElseThrow();

            assertThat(persisted.stepExecution().getLeaseVersion()).isEqualTo(1);
            assertThat(persisted.stepExecution().getAttemptCount()).isEqualTo(1);
            assertThat(persisted.version()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldAllowWorkerToUpdateStepWithValidLease() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();
        Instant leaseExpiresAt = now.plusSeconds(60);

        PersistedStepExecution claimed = stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                null,
                leaseExpiresAt,
                now
        ).orElseThrow();

        claimed.stepExecution().complete(
                Map.of("paymentStatus", "APPROVED")
        );

        PersistedStepExecution updated = stepRepository.updateWithLease(
                claimed,
                "worker-1",
                1,
                now.plusSeconds(1)
        );

        assertThat(updated.version()).isEqualTo(2);

        PersistedStepExecution retrieved = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(retrieved.stepExecution().getStatus().name())
                .isEqualTo("COMPLETED");
        assertThat(retrieved.stepExecution().getOutputPayload())
                .containsEntry("paymentStatus", "APPROVED");
        assertThat(retrieved.stepExecution().getLeaseOwner()).isNull();
        assertThat(retrieved.version()).isEqualTo(2);
    }

    @Test
    void shouldRejectUpdateFromDifferentWorker() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();
        Instant leaseExpiresAt = now.plusSeconds(60);

        PersistedStepExecution claimed = stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                null,
                leaseExpiresAt,
                now
        ).orElseThrow();

        claimed.stepExecution().complete(Map.of("result", "SUCCESS"));

        assertThatThrownBy(
                () -> stepRepository.updateWithLease(
                        claimed,
                        "worker-2",
                        1,
                        now.plusSeconds(1)
                )
        ).isInstanceOf(OptimisticLockingFailureException.class);

        PersistedStepExecution retrieved = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(retrieved.stepExecution().getStatus().name())
                .isEqualTo("RUNNING");
        assertThat(retrieved.stepExecution().getLeaseOwner())
                .isEqualTo("worker-1");
        assertThat(retrieved.stepExecution().getLeaseVersion()).isEqualTo(1);
        assertThat(retrieved.version()).isEqualTo(1);
    }

    @Test
    void shouldRejectUpdateAfterLeaseExpires() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();
        Instant leaseExpiresAt = now.plusSeconds(30);

        PersistedStepExecution claimed = stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                null,
                leaseExpiresAt,
                now
        ).orElseThrow();

        claimed.stepExecution().complete(Map.of("result", "SUCCESS"));

        Instant afterLeaseExpiry = leaseExpiresAt.plusSeconds(1);

        assertThatThrownBy(
                () -> stepRepository.updateWithLease(
                        claimed,
                        "worker-1",
                        1,
                        afterLeaseExpiry
                )
        ).isInstanceOf(OptimisticLockingFailureException.class);

        PersistedStepExecution retrieved = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(retrieved.stepExecution().getStatus().name())
                .isEqualTo("RUNNING");
        assertThat(retrieved.stepExecution().getLeaseOwner())
                .isEqualTo("worker-1");
        assertThat(retrieved.version()).isEqualTo(1);
    }

    @Test
    void shouldRejectStaleWorkerAfterLeaseReclamation() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant firstNow = Instant.now();
        Instant firstLeaseExpiry = firstNow.plusSeconds(30);

        PersistedStepExecution firstClaim = stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                null,
                firstLeaseExpiry,
                firstNow
        ).orElseThrow();

        // Worker 1's lease expires. Worker 2 reclaims the step.
        Instant recoveryNow = firstLeaseExpiry.plusSeconds(1);
        Instant secondLeaseExpiry = recoveryNow.plusSeconds(60);

        PersistedStepExecution secondClaim = stepRepository.claim(
                step.getStepExecutionId(),
                "worker-2",
                null,
                secondLeaseExpiry,
                recoveryNow
        ).orElseThrow();

        assertThat(secondClaim.stepExecution().getLeaseOwner())
                .isEqualTo("worker-2");
        assertThat(secondClaim.stepExecution().getLeaseVersion()).isEqualTo(2);
        assertThat(secondClaim.version()).isEqualTo(2);

        // The old worker attempts to complete using its original lease.
        firstClaim.stepExecution().complete(
                Map.of("result", "STALE_RESULT")
        );

        assertThatThrownBy(
                () -> stepRepository.updateWithLease(
                        firstClaim,
                        "worker-1",
                        1,
                        recoveryNow.plusSeconds(1)
                )
        ).isInstanceOf(OptimisticLockingFailureException.class);

        PersistedStepExecution retrieved = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(retrieved.stepExecution().getStatus().name())
                .isEqualTo("RUNNING");
        assertThat(retrieved.stepExecution().getLeaseOwner())
                .isEqualTo("worker-2");
        assertThat(retrieved.stepExecution().getLeaseVersion()).isEqualTo(2);
        assertThat(retrieved.version()).isEqualTo(2);
    }

    @Test
    void shouldNotReclaimStepAfterTaskTimeout() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant firstNow = Instant.now();
        Instant taskTimeout = firstNow.plusSeconds(30);
        Instant leaseExpiry = firstNow.plusSeconds(10);

        stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                taskTimeout,
                leaseExpiry,
                firstNow
        ).orElseThrow();

        Instant recoveryNow = taskTimeout.plusSeconds(1);

        Optional<PersistedStepExecution> recovered =
                stepRepository.claim(
                        step.getStepExecutionId(),
                        "worker-2",
                        recoveryNow.plusSeconds(300),
                        recoveryNow.plusSeconds(60),
                        recoveryNow
                );

        assertThat(recovered).isEmpty();

        PersistedStepExecution persisted = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(persisted.stepExecution().getStatus().name())
                .isEqualTo("RUNNING");
        assertThat(persisted.stepExecution().getLeaseOwner())
                .isEqualTo("worker-1");
        assertThat(persisted.stepExecution().getTimeoutAt())
                .isEqualTo(taskTimeout);
        assertThat(persisted.stepExecution().getLeaseVersion()).isEqualTo(1);
        assertThat(persisted.version()).isEqualTo(1);
    }

    @Test
    void shouldRejectUpdateAfterTaskTimeout() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();
        Instant taskTimeout = now.plusSeconds(30);
        Instant leaseExpiry = now.plusSeconds(60);

        PersistedStepExecution claimed = stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                taskTimeout,
                leaseExpiry,
                now
        ).orElseThrow();

        claimed.stepExecution().complete(
                Map.of("result", "SUCCESS")
        );

        Instant afterTaskTimeout = taskTimeout.plusSeconds(1);

        assertThatThrownBy(
                () -> stepRepository.updateWithLease(
                        claimed,
                        "worker-1",
                        1,
                        afterTaskTimeout
                )
        ).isInstanceOf(OptimisticLockingFailureException.class);

        PersistedStepExecution persisted = stepRepository
                .findById(step.getStepExecutionId())
                .orElseThrow();

        assertThat(persisted.stepExecution().getStatus().name())
                .isEqualTo("RUNNING");
        assertThat(persisted.stepExecution().getLeaseOwner())
                .isEqualTo("worker-1");
        assertThat(persisted.stepExecution().getTimeoutAt())
                .isEqualTo(taskTimeout);
        assertThat(persisted.version()).isEqualTo(1);
    }

    @Test
    void shouldRenewLeaseAndPreserveLeaseVersion() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();
        Instant initialLeaseExpiry = now.plusSeconds(60);
        Instant timeoutAt = now.plusSeconds(300);

        PersistedStepExecution claimed = stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                timeoutAt,
                initialLeaseExpiry,
                now
        ).orElseThrow();

        Instant renewedExpiry = now.plusSeconds(120);

        PersistedStepExecution renewed = stepRepository.renewLease(
                step.getStepExecutionId(),
                "worker-1",
                1,
                renewedExpiry,
                now.plusSeconds(10)
        );

        assertThat(renewed.stepExecution().getLeaseOwner())
                .isEqualTo("worker-1");
        assertThat(renewed.stepExecution().getLeaseExpiresAt())
                .isEqualTo(renewedExpiry);
        assertThat(renewed.stepExecution().getLeaseVersion())
                .isEqualTo(1);

        assertThat(renewed.version())
                .isEqualTo(claimed.version() + 1);
    }

    @Test
    void shouldRejectLeaseRenewalFromDifferentWorker() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();

        stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                null,
                now.plusSeconds(60),
                now
        ).orElseThrow();

        assertThatThrownBy(() -> stepRepository.renewLease(
                step.getStepExecutionId(),
                "worker-2",
                1,
                now.plusSeconds(120),
                now.plusSeconds(10)
        )).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldRejectLeaseRenewalAfterExpiration() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();
        Instant leaseExpiry = now.plusSeconds(30);

        stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                null,
                leaseExpiry,
                now
        ).orElseThrow();

        assertThatThrownBy(() -> stepRepository.renewLease(
                step.getStepExecutionId(),
                "worker-1",
                1,
                now.plusSeconds(120),
                leaseExpiry.plusSeconds(1)
        )).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldAllowLeaseRenewalBeyondTaskTimeout() {
        StepExecution step = createStepExecution();
        stepRepository.save(step);

        Instant now = Instant.now();
        Instant timeoutAt = now.plusSeconds(90);
        Instant initialLeaseExpiry = now.plusSeconds(30);
        Instant renewedLeaseExpiry = now.plusSeconds(120);

        stepRepository.claim(
                step.getStepExecutionId(),
                "worker-1",
                timeoutAt,
                initialLeaseExpiry,
                now
        ).orElseThrow();

        PersistedStepExecution renewed = stepRepository.renewLease(
                step.getStepExecutionId(),
                "worker-1",
                1,
                renewedLeaseExpiry,
                now.plusSeconds(10)
        );

        assertThat(renewed.stepExecution().getLeaseExpiresAt())
                .isEqualTo(renewedLeaseExpiry);

        assertThat(renewed.stepExecution().getLeaseVersion())
                .isEqualTo(1);

        assertThat(renewed.version())
                .isEqualTo(2);
    }
}
