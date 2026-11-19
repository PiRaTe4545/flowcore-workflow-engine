
package com.flowcore.application;

import com.flowcore.domain.StepExecution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StepExecutionRepository {

    PersistedStepExecution save(StepExecution stepExecution);

    Optional<PersistedStepExecution> findById(UUID stepExecutionId);

    List<PersistedStepExecution> findByWorkflowId(UUID workflowId);

    PersistedStepExecution update(
            PersistedStepExecution persistedStepExecution
    );

    Optional<PersistedStepExecution> claim(
            UUID stepExecutionId,
            String workerId,
            Instant timeoutAt,
            Instant leaseExpiresAt,
            Instant now
    );

    PersistedStepExecution updateWithLease(
            PersistedStepExecution persistedStepExecution,
            String workerId,
            long expectedLeaseVersion,
            Instant now
    );

    PersistedStepExecution renewLease(
            UUID stepExecutionId,
            String workerId,
            long expectedLeaseVersion,
            Instant newLeaseExpiresAt,
            Instant now
    );
}
