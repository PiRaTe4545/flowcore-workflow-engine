package com.flowcore.application;

import com.flowcore.domain.StepExecution;

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
}