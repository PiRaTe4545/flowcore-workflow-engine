
package com.flowcore.application;

import com.flowcore.definition.model.StepType;
import com.flowcore.definition.model.WorkflowDefinition;
import com.flowcore.definition.model.WorkflowStepDefinition;
import com.flowcore.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutionServiceTest {

    @Mock
    private WorkflowExecutionRepository repository;

    @Mock
    private WorkflowDefinitionRepository definitionRepository;

    @Mock
    private WorkflowExecutionEventRepository eventRepository;

    private WorkflowExecutionService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowExecutionService(
                repository,
                definitionRepository,
                eventRepository
        );
    }

    @Test
    void shouldCreateWorkflowExecution() {
        Map<String, Object> payload = Map.of(
                "orderId", "order-123"
        );

        when(repository.save(any(WorkflowExecution.class)))
                .thenAnswer(invocation -> {
                    WorkflowExecution execution =
                            invocation.getArgument(0);
                    return new PersistedWorkflowExecution(
                            execution,
                            0
                    );
                });
        when(definitionRepository.findByTypeAndVersion(
                "ORDER_PROCESSING",
                1
        )).thenReturn(Optional.of(createDefinition()));
        PersistedWorkflowExecution result = service.create(
                "ORDER_PROCESSING",
                1,
                payload
        );

        assertThat(result.workflowExecution().getWorkflowType())
                .isEqualTo("ORDER_PROCESSING");
        assertThat(result.workflowExecution().getStatus())
                .isEqualTo(WorkflowStatus.PENDING);

        verify(eventRepository).append(
                argThat(event ->
                        event.eventType() == WorkflowEventType.CREATED
                                && event.previousStatus() == null
                                && event.newStatus() == WorkflowStatus.PENDING
                                && event.executionVersion() == 0
                )
        );
        verify(repository).save(any(WorkflowExecution.class));
    }

    @Test
    void shouldRetrieveWorkflowExecution() {
        UUID workflowId = UUID.randomUUID();

        WorkflowExecution execution = WorkflowExecution.restore(
                workflowId,
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-123"),
                WorkflowStatus.PENDING,
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        PersistedWorkflowExecution persisted =
                new PersistedWorkflowExecution(execution, 0);

        when(repository.findById(workflowId))
                .thenReturn(Optional.of(persisted));

        PersistedWorkflowExecution result =
                service.getById(workflowId);

        assertThat(result.workflowExecution().getWorkflowId())
                .isEqualTo(workflowId);

        verify(repository).findById(workflowId);
    }

    @Test
    void shouldStartWorkflowExecution() {
        UUID workflowId = UUID.randomUUID();

        WorkflowExecution execution = WorkflowExecution.restore(
                workflowId,
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-123"),
                WorkflowStatus.PENDING,
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        PersistedWorkflowExecution persisted =
                new PersistedWorkflowExecution(execution, 0);

        when(repository.findById(workflowId))
                .thenReturn(Optional.of(persisted));

        when(repository.update(execution, 0))
                .thenReturn(new PersistedWorkflowExecution(
                        execution,
                        1
                ));

        PersistedWorkflowExecution result =
                service.start(workflowId);

        assertThat(result.workflowExecution().getStatus())
                .isEqualTo(WorkflowStatus.RUNNING);
        assertThat(result.version()).isEqualTo(1);

        verify(repository).update(execution, 0);
    }


    @Test
    void shouldCompleteWorkflowExecution() {
        PersistedWorkflowExecution persisted = createRunningWorkflow();

        when(repository.findById(persisted.workflowExecution().getWorkflowId()))
                .thenReturn(Optional.of(persisted));

        when(repository.update(persisted.workflowExecution(), 0))
                .thenReturn(new PersistedWorkflowExecution(
                        persisted.workflowExecution(),
                        1
                ));

        PersistedWorkflowExecution result = service.complete(
                persisted.workflowExecution().getWorkflowId()
        );

        assertThat(result.workflowExecution().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(result.version()).isEqualTo(1);
    }

    @Test
    void shouldFailWorkflowExecution() {
        PersistedWorkflowExecution persisted = createRunningWorkflow();

        when(repository.findById(persisted.workflowExecution().getWorkflowId()))
                .thenReturn(Optional.of(persisted));

        when(repository.update(persisted.workflowExecution(), 0))
                .thenReturn(new PersistedWorkflowExecution(
                        persisted.workflowExecution(),
                        1
                ));

        PersistedWorkflowExecution result = service.fail(
                persisted.workflowExecution().getWorkflowId()
        );

        assertThat(result.workflowExecution().getStatus())
                .isEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void shouldCancelWorkflowExecution() {
        PersistedWorkflowExecution persisted = createRunningWorkflow();

        when(repository.findById(persisted.workflowExecution().getWorkflowId()))
                .thenReturn(Optional.of(persisted));

        when(repository.update(persisted.workflowExecution(), 0))
                .thenReturn(new PersistedWorkflowExecution(
                        persisted.workflowExecution(),
                        1
                ));

        PersistedWorkflowExecution result = service.cancel(
                persisted.workflowExecution().getWorkflowId()
        );

        assertThat(result.workflowExecution().getStatus())
                .isEqualTo(WorkflowStatus.CANCELLED);
    }


    private PersistedWorkflowExecution createRunningWorkflow() {
        WorkflowExecution execution = WorkflowExecution.restore(
                UUID.randomUUID(),
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-123"),
                WorkflowStatus.RUNNING,
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        return new PersistedWorkflowExecution(execution, 0);
    }


    @Test
    void shouldThrowExceptionWhenWorkflowDoesNotExist() {
        UUID workflowId = UUID.randomUUID();

        when(repository.findById(workflowId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(workflowId))
                .isInstanceOf(WorkflowExecutionNotFoundException.class);

        verify(repository).findById(workflowId);
    }

    @Test
    void shouldRejectInvalidWorkflowTransition() {
        UUID workflowId = UUID.randomUUID();

        WorkflowExecution execution = WorkflowExecution.restore(
                workflowId,
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-123"),
                WorkflowStatus.COMPLETED,
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        PersistedWorkflowExecution persisted =
                new PersistedWorkflowExecution(execution, 1);

        when(repository.findById(workflowId))
                .thenReturn(Optional.of(persisted));

        assertThatThrownBy(() -> service.start(workflowId))
                .isInstanceOf(InvalidWorkflowTransitionException.class);

        verify(repository, never()).update(any(), anyLong());
    }

    @Test
    void shouldRejectExecutionWhenDefinitionDoesNotExist() {
        when(definitionRepository.findByTypeAndVersion(
                "UNKNOWN_WORKFLOW",
                1
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.create(
                        "UNKNOWN_WORKFLOW",
                        1,
                        Map.of("orderId", "order-123")
                )
        ).isInstanceOf(WorkflowDefinitionNotFoundException.class);

        verify(repository, never()).save(any());
        verify(repository, never()).save(any());
        verify(eventRepository, never()).append(any());
    }


    @Test
    void shouldRetrieveWorkflowExecutionHistory() {
        UUID workflowId = UUID.randomUUID();

        PersistedWorkflowExecution persisted = createRunningWorkflow();

        WorkflowExecutionEvent event = new WorkflowExecutionEvent(
                1L,
                workflowId,
                WorkflowEventType.STARTED,
                WorkflowStatus.PENDING,
                WorkflowStatus.RUNNING,
                1,
                Map.of(),
                Instant.now()
        );

        when(repository.findById(workflowId))
                .thenReturn(Optional.of(persisted));

        when(eventRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(event));

        List<WorkflowExecutionEvent> history =
                service.getHistory(workflowId);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).eventType())
                .isEqualTo(WorkflowEventType.STARTED);

        verify(eventRepository).findByWorkflowId(workflowId);
    }

    @Test
    void shouldThrowWhenRetrievingHistoryForNonExistingWorkflow() {
        UUID workflowId = UUID.randomUUID();

        when(repository.findById(workflowId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory(workflowId))
                .isInstanceOf(WorkflowExecutionNotFoundException.class);

        verify(eventRepository, never()).findByWorkflowId(any());
    }


    private WorkflowDefinition createDefinition() {
        return new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                java.util.List.of(
                        new WorkflowStepDefinition(
                                "validate-order",
                                StepType.SERVICE_TASK
                        ),
                        new WorkflowStepDefinition(
                                "wait-for-payment",
                                StepType.WAIT
                        )
                )
        );
    }

}
