
package com.flowcore.application;

import com.flowcore.domain.WorkflowExecution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import java.util.Map;
import java.util.Objects;

@Service
public class WorkflowExecutionService {

    private final WorkflowExecutionRepository repository;

    private final WorkflowDefinitionRepository definitionRepository;

    public WorkflowExecutionService(
            WorkflowExecutionRepository repository,
            WorkflowDefinitionRepository definitionRepository
    ) {
        this.repository = repository;
        this.definitionRepository = definitionRepository;
    }

    @Transactional
    public PersistedWorkflowExecution create(
            String workflowType,
            int definitionVersion,
            Map<String, Object> inputPayload
    ) {
        definitionRepository.findByTypeAndVersion(
                workflowType,
                definitionVersion
        ).orElseThrow(() ->
                new WorkflowDefinitionNotFoundException(
                        workflowType,
                        definitionVersion
                )
        );

        WorkflowExecution execution = WorkflowExecution.create(
                workflowType,
                definitionVersion,
                inputPayload
        );

        return repository.save(execution);
    }


    @Transactional(readOnly = true)
    public PersistedWorkflowExecution getById(UUID workflowId) {
        return repository.findById(
                Objects.requireNonNull(
                        workflowId,
                        "workflowId cannot be null"
                )
        ).orElseThrow(() -> new WorkflowExecutionNotFoundException(
                workflowId
        ));
    }


    @Transactional
    public PersistedWorkflowExecution start(UUID workflowId) {
        PersistedWorkflowExecution persisted = getById(workflowId);

        persisted.workflowExecution().start();

        return repository.update(
                persisted.workflowExecution(),
                persisted.version()
        );
    }


    @Transactional
    public PersistedWorkflowExecution complete(UUID workflowId) {
        PersistedWorkflowExecution persisted = getById(workflowId);

        persisted.workflowExecution().complete();

        return repository.update(
                persisted.workflowExecution(),
                persisted.version()
        );
    }

    @Transactional
    public PersistedWorkflowExecution fail(UUID workflowId) {
        PersistedWorkflowExecution persisted = getById(workflowId);

        persisted.workflowExecution().fail();

        return repository.update(
                persisted.workflowExecution(),
                persisted.version()
        );
    }

    @Transactional
    public PersistedWorkflowExecution cancel(UUID workflowId) {
        PersistedWorkflowExecution persisted = getById(workflowId);

        persisted.workflowExecution().cancel();

        return repository.update(
                persisted.workflowExecution(),
                persisted.version()
        );
    }



}
