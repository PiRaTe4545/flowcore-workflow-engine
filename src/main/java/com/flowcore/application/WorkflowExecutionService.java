
package com.flowcore.application;

import com.flowcore.definition.model.WorkflowDefinition;
import com.flowcore.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final StepExecutionRepository stepExecutionRepository;

    public WorkflowExecutionService(
            WorkflowExecutionRepository repository,
            WorkflowDefinitionRepository definitionRepository,
            WorkflowExecutionEventRepository eventRepository,
            StepExecutionRepository stepExecutionRepository
    ) {
        this.repository = repository;
        this.definitionRepository = definitionRepository;
        this.eventRepository = eventRepository;
        this.stepExecutionRepository = stepExecutionRepository;
    }

    @Transactional
    public PersistedWorkflowExecution create(
            String workflowType,
            int definitionVersion,
            Map<String, Object> inputPayload
    ) {
        WorkflowDefinition definition =
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

        for (var stepDefinition : definition.steps()) {
            StepExecution stepExecution = StepExecution.create(
                    execution.getWorkflowId(),
                    stepDefinition.name()
            );

            stepExecutionRepository.save(stepExecution);
        }

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
