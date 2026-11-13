
package com.flowcore.application;

import com.flowcore.domain.WorkflowExecution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.flowcore.domain.WorkflowExecutionEvent;
import com.flowcore.domain.WorkflowEventType;
import com.flowcore.domain.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import java.util.Map;
import java.util.Objects;

@Service
public class WorkflowExecutionService {

    private final WorkflowExecutionRepository repository;

    private final WorkflowDefinitionRepository definitionRepository;

    private final WorkflowExecutionEventRepository eventRepository;

    public WorkflowExecutionService(
            WorkflowExecutionRepository repository,
            WorkflowDefinitionRepository definitionRepository, WorkflowExecutionEventRepository eventRepository
    ) {
        this.repository = repository;
        this.definitionRepository = definitionRepository;
        this.eventRepository = eventRepository;
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
        PersistedWorkflowExecution persisted = repository.save(execution);

        eventRepository.append(
                new WorkflowExecutionEvent(
                        null,
                        execution.getWorkflowId(),
                        WorkflowEventType.CREATED,
                        null,
                        WorkflowStatus.PENDING,
                        persisted.version(),
                        Map.of(),
                        Instant.now()
                )
        );

        return persisted;
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
        PersistedWorkflowExecution persisted = repository.findById(workflowId)
                .orElseThrow(() -> new WorkflowExecutionNotFoundException(workflowId));

        WorkflowStatus previousStatus = persisted.workflowExecution().getStatus();

        persisted.workflowExecution().start();

        PersistedWorkflowExecution updated = repository.update(
                persisted.workflowExecution(),
                persisted.version()
        );

        eventRepository.append(new WorkflowExecutionEvent(
                null,
                workflowId,
                WorkflowEventType.STARTED,
                previousStatus,
                updated.workflowExecution().getStatus(),
                updated.version(),
                Map.of(),
                Instant.now()
        ));

        return updated;
    }


    @Transactional
    public PersistedWorkflowExecution complete(UUID workflowId) {
        PersistedWorkflowExecution persisted = repository.findById(workflowId)
                .orElseThrow(() -> new WorkflowExecutionNotFoundException(workflowId));

        WorkflowStatus previousStatus = persisted.workflowExecution().getStatus();

        persisted.workflowExecution().complete();

        PersistedWorkflowExecution updated = repository.update(
                persisted.workflowExecution(),
                persisted.version()
        );

        eventRepository.append(new WorkflowExecutionEvent(
                null,
                workflowId,
                WorkflowEventType.COMPLETED,
                previousStatus,
                updated.workflowExecution().getStatus(),
                updated.version(),
                Map.of(),
                Instant.now()
        ));

        return updated;
    }

    @Transactional
    public PersistedWorkflowExecution fail(UUID workflowId) {
        PersistedWorkflowExecution persisted = repository.findById(workflowId)
                .orElseThrow(() -> new WorkflowExecutionNotFoundException(workflowId));

        WorkflowStatus previousStatus = persisted.workflowExecution().getStatus();

        persisted.workflowExecution().fail();

        PersistedWorkflowExecution updated = repository.update(
                persisted.workflowExecution(),
                persisted.version()
        );

        eventRepository.append(new WorkflowExecutionEvent(
                null,
                workflowId,
                WorkflowEventType.FAILED,
                previousStatus,
                updated.workflowExecution().getStatus(),
                updated.version(),
                Map.of(),
                Instant.now()
        ));

        return updated;
    }

    @Transactional
    public PersistedWorkflowExecution cancel(UUID workflowId) {
        PersistedWorkflowExecution persisted = repository.findById(workflowId)
                .orElseThrow(() -> new WorkflowExecutionNotFoundException(workflowId));

        WorkflowStatus previousStatus = persisted.workflowExecution().getStatus();

        persisted.workflowExecution().cancel();

        PersistedWorkflowExecution updated = repository.update(
                persisted.workflowExecution(),
                persisted.version()
        );

        eventRepository.append(new WorkflowExecutionEvent(
                null,
                workflowId,
                WorkflowEventType.CANCELLED,
                previousStatus,
                updated.workflowExecution().getStatus(),
                updated.version(),
                Map.of(),
                Instant.now()
        ));

        return updated;
    }

    @Transactional(readOnly = true)
    public List<WorkflowExecutionEvent> getHistory(UUID workflowId) {
        getById(workflowId);

        return eventRepository.findByWorkflowId(workflowId);
    }



}
