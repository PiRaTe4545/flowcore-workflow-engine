
package com.flowcore.worker;

import com.flowcore.application.StepExecutionRepository;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class StepExecutionLeaseRenewer {

    private final TaskScheduler taskScheduler;
    private final StepExecutionRepository stepExecutionRepository;

    public StepExecutionLeaseRenewer(
            TaskScheduler taskScheduler,
            StepExecutionRepository stepExecutionRepository
    ) {
        this.taskScheduler = Objects.requireNonNull(taskScheduler);
        this.stepExecutionRepository =
                Objects.requireNonNull(stepExecutionRepository);
    }

    public LeaseRenewal start(
            java.util.UUID stepExecutionId,
            String workerId,
            long leaseVersion,
            Duration leaseDuration,
            Duration renewalInterval
    ) {
        AtomicBoolean stopped = new AtomicBoolean(false);
        Objects.requireNonNull(stepExecutionId);
        Objects.requireNonNull(leaseDuration);
        Objects.requireNonNull(renewalInterval);

        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId cannot be blank");
        }

        if (leaseVersion < 0) {
            throw new IllegalArgumentException(
                    "leaseVersion cannot be negative"
            );
        }

        if (leaseDuration.isZero() || leaseDuration.isNegative()
                || renewalInterval.isZero()
                || renewalInterval.isNegative()
                || renewalInterval.compareTo(leaseDuration) >= 0) {
            throw new IllegalArgumentException(
                    "Renewal interval must be positive and shorter "
                            + "than the lease duration"
            );
        }

        Object renewalLock = new Object();
        AtomicReference<RuntimeException> renewalFailure =
                new AtomicReference<>();

        Runnable renewalTask = () -> {
            synchronized (renewalLock) {
                if (stopped.get() || renewalFailure.get() != null) {
                    return;
                }

                Instant now = Instant.now();

                try {
                    stepExecutionRepository.renewLease(
                            stepExecutionId,
                            workerId,
                            leaseVersion,
                            now.plus(leaseDuration),
                            now
                    );
                } catch (RuntimeException exception) {
                    renewalFailure.compareAndSet(null, exception);
                }
            }
        };

        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                renewalTask,
                Instant.now().plus(renewalInterval),
                renewalInterval
        );

        if (future == null) {
            throw new IllegalStateException(
                    "Could not schedule lease renewal"
            );
        }

        return new LeaseRenewal(
                future,
                renewalLock,
                renewalFailure,
                stopped
        );
    }

    public static class LeaseRenewal {

        private final ScheduledFuture<?> future;
        private final Object renewalLock;
        private final AtomicReference<RuntimeException> renewalFailure;
        private final AtomicBoolean stopped;

        private LeaseRenewal(
                ScheduledFuture<?> future,
                Object renewalLock,
                AtomicReference<RuntimeException> renewalFailure,
                AtomicBoolean stopped) {
            this.future = future;
            this.renewalLock = renewalLock;
            this.renewalFailure = renewalFailure;
            this.stopped = stopped;
        }

        public void stop() {
            stopped.set(true);
            future.cancel(false);

            // Wait for an already-running renewal to finish.
            synchronized (renewalLock) {
                // Synchronization barrier.
            }
        }

        public boolean isLeaseLost() {
            return renewalFailure.get() != null;
        }

        public RuntimeException getRenewalFailure() {
            return renewalFailure.get();
        }
    }
}
