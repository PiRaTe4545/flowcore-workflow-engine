package com.flowcore.application;

import com.flowcore.definition.model.StepType;
import com.flowcore.definition.model.WorkflowDefinition;
import com.flowcore.definition.model.WorkflowStepDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowDefinitionServiceTest {

    private WorkflowDefinitionRepository repository;
    private WorkflowDefinitionService service;

    @BeforeEach
    void setUp() {
        repository = mock(WorkflowDefinitionRepository.class);
        service = new WorkflowDefinitionService(repository);
    }

    @Test
    void shouldRegisterWorkflowDefinition() {
        WorkflowDefinition definition = createDefinition();

        when(repository.register(definition)).thenReturn(definition);

        WorkflowDefinition result = service.register(definition);

        assertEquals(definition, result);
        verify(repository).register(definition);
    }

    @Test
    void shouldRetrieveWorkflowDefinition() {
        WorkflowDefinition definition = createDefinition();

        when(repository.findByTypeAndVersion("ORDER_PROCESSING", 1))
                .thenReturn(Optional.of(definition));

        WorkflowDefinition result =
                service.getByTypeAndVersion("ORDER_PROCESSING", 1);

        assertEquals(definition, result);
        verify(repository).findByTypeAndVersion("ORDER_PROCESSING", 1);
    }

    @Test
    void shouldThrowWhenDefinitionDoesNotExist() {
        when(repository.findByTypeAndVersion("UNKNOWN_WORKFLOW", 1))
                .thenReturn(Optional.empty());

        assertThrows(
                WorkflowDefinitionNotFoundException.class,
                () -> service.getByTypeAndVersion("UNKNOWN_WORKFLOW", 1)
        );
    }

    @Test
    void shouldRejectNullDefinition() {
        assertThrows(
                NullPointerException.class,
                () -> service.register(null)
        );

        verifyNoInteractions(repository);
    }

    private WorkflowDefinition createDefinition() {
        return new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                List.of(
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