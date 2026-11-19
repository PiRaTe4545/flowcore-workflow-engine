
package com.flowcore.worker;

import com.flowcore.application.StepExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StepExecutionLeaseRenewerTest {

    private static final Duration LEASE_DURATION =
            Duration.ofMinutes(1);

    private static final Duration RENEWAL_INTERVAL =
            Duration.ofSeconds(20);

    @Test
    void shouldScheduleAndExecuteLeaseRenewal() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        StepExecutionRepository repository =
                mock(StepExecutionRepository.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);

        doReturn(future).when(taskScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                eq(RENEWAL_INTERVAL)
        );

        StepExecutionLeaseRenewer renewer =
                new StepExecutionLeaseRenewer(taskScheduler, repository);

        UUID stepExecutionId = UUID.randomUUID();
        String workerId = "worker-1";
        long leaseVersion = 3L;

        var taskCaptor =
                org.mockito.ArgumentCaptor.forClass(Runnable.class);

        var startTimeCaptor =
                org.mockito.ArgumentCaptor.forClass(Instant.class);

        StepExecutionLeaseRenewer.LeaseRenewal renewal =
                renewer.start(
                        stepExecutionId,
                        workerId,
                        leaseVersion,
                        LEASE_DURATION,
                        RENEWAL_INTERVAL
                );

        verify(taskScheduler).scheduleAtFixedRate(
                taskCaptor.capture(),
                startTimeCaptor.capture(),
                eq(RENEWAL_INTERVAL)
        );

        assertThat(startTimeCaptor.getValue())
                .isAfter(Instant.now().minusSeconds(1));

        Instant scheduledStart = startTimeCaptor.getValue();

        assertThat(scheduledStart)
                .isBefore(Instant.now().plus(RENEWAL_INTERVAL)
                        .plusSeconds(1));

        taskCaptor.getValue().run();

        var nowCaptor =
                org.mockito.ArgumentCaptor.forClass(Instant.class);

        var expiryCaptor =
                org.mockito.ArgumentCaptor.forClass(Instant.class);

        verify(repository).renewLease(
                eq(stepExecutionId),
                eq(workerId),
                eq(leaseVersion),
                expiryCaptor.capture(),
                nowCaptor.capture()
        );

        assertThat(expiryCaptor.getValue())
                .isEqualTo(nowCaptor.getValue().plus(LEASE_DURATION));

        assertThat(renewal.isLeaseLost()).isFalse();

        renewal.stop();

        verify(future).cancel(false);
    }

    @Test
    void shouldRecordRenewalFailureAndStopFurtherRenewals() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        StepExecutionRepository repository =
                mock(StepExecutionRepository.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);

        doReturn(future).when(taskScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                eq(RENEWAL_INTERVAL)
        );

        RuntimeException failure =
                new IllegalStateException("Database unavailable");

        when(repository.renewLease(
                any(),
                anyString(),
                anyLong(),
                any(),
                any()
        )).thenThrow(failure);

        StepExecutionLeaseRenewer renewer =
                new StepExecutionLeaseRenewer(taskScheduler, repository);

        var taskCaptor =
                org.mockito.ArgumentCaptor.forClass(Runnable.class);

        StepExecutionLeaseRenewer.LeaseRenewal renewal =
                renewer.start(
                        UUID.randomUUID(),
                        "worker-1",
                        1L,
                        LEASE_DURATION,
                        RENEWAL_INTERVAL
                );

        verify(taskScheduler).scheduleAtFixedRate(
                taskCaptor.capture(),
                any(Instant.class),
                eq(RENEWAL_INTERVAL)
        );

        Runnable renewalTask = taskCaptor.getValue();

        renewalTask.run();
        renewalTask.run();

        assertThat(renewal.isLeaseLost()).isTrue();
        assertThat(renewal.getRenewalFailure()).isSameAs(failure);

        verify(repository, times(1)).renewLease(
                any(),
                anyString(),
                anyLong(),
                any(),
                any()
        );

        renewal.stop();
    }

    @Test
    void shouldNotRenewAfterStop() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        StepExecutionRepository repository =
                mock(StepExecutionRepository.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);

        doReturn(future).when(taskScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                eq(RENEWAL_INTERVAL)
        );

        StepExecutionLeaseRenewer renewer =
                new StepExecutionLeaseRenewer(taskScheduler, repository);

        var taskCaptor =
                org.mockito.ArgumentCaptor.forClass(Runnable.class);

        StepExecutionLeaseRenewer.LeaseRenewal renewal =
                renewer.start(
                        UUID.randomUUID(),
                        "worker-1",
                        1L,
                        LEASE_DURATION,
                        RENEWAL_INTERVAL
                );

        verify(taskScheduler).scheduleAtFixedRate(
                taskCaptor.capture(),
                any(Instant.class),
                eq(RENEWAL_INTERVAL)
        );

        renewal.stop();

        taskCaptor.getValue().run();

        verify(future).cancel(false);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldWaitForInProgressRenewalWhenStopped()
            throws InterruptedException {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        StepExecutionRepository repository =
                mock(StepExecutionRepository.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);

        doReturn(future).when(taskScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                eq(RENEWAL_INTERVAL)
        );

        CountDownLatch renewalStarted = new CountDownLatch(1);
        CountDownLatch allowRenewalToFinish = new CountDownLatch(1);
        CountDownLatch stopStarted = new CountDownLatch(1);
        CountDownLatch stopFinished = new CountDownLatch(1);

        doAnswer(invocation -> {
            renewalStarted.countDown();

            if (!allowRenewalToFinish.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for renewal test release"
                );
            }

            return null;
        }).when(repository).renewLease(
                any(),
                anyString(),
                anyLong(),
                any(),
                any()
        );

        StepExecutionLeaseRenewer renewer =
                new StepExecutionLeaseRenewer(taskScheduler, repository);

        var taskCaptor =
                org.mockito.ArgumentCaptor.forClass(Runnable.class);

        StepExecutionLeaseRenewer.LeaseRenewal renewal =
                renewer.start(
                        UUID.randomUUID(),
                        "worker-1",
                        1L,
                        LEASE_DURATION,
                        RENEWAL_INTERVAL
                );

        verify(taskScheduler).scheduleAtFixedRate(
                taskCaptor.capture(),
                any(Instant.class),
                eq(RENEWAL_INTERVAL)
        );

        Thread renewalThread =
                new Thread(taskCaptor.getValue());

        Thread stopThread = new Thread(() -> {
            stopStarted.countDown();
            renewal.stop();
            stopFinished.countDown();
        });

        try {
            renewalThread.start();

            assertThat(renewalStarted.await(2, TimeUnit.SECONDS))
                    .isTrue();

            stopThread.start();

            assertThat(stopStarted.await(2, TimeUnit.SECONDS))
                    .isTrue();

            assertThat(stopFinished.await(100, TimeUnit.MILLISECONDS))
                    .isFalse();

            allowRenewalToFinish.countDown();

            assertThat(stopFinished.await(2, TimeUnit.SECONDS))
                    .isTrue();
        } finally {
            allowRenewalToFinish.countDown();
            renewalThread.join(2000);
            stopThread.join(2000);
        }

        assertThat(renewalThread.isAlive()).isFalse();
        assertThat(stopThread.isAlive()).isFalse();

        verify(future).cancel(false);
    }
}
