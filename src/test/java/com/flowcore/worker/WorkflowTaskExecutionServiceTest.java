
package com.flowcore.worker;

import com.flowcore.application.*;
import com.flowcore.definition.model.StepType;
import com.flowcore.definition.model.WorkflowDefinition;
import com.flowcore.definition.model.WorkflowStepDefinition;
import com.flowcore.domain.StepExecution;
import com.flowcore.domain.WorkflowExecution;
import com.flowcore.engine.StepExecutionEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class WorkflowTaskExecutionServiceTest {

    @Test
    void shouldReturnFalseWhenNoStepIsAvailable() {
        StepExecutionEngine stepExecutionEngine =
                mock(StepExecutionEngine.class);
        WorkflowExecutionService workflowExecutionService =
                mock(WorkflowExecutionService.class);
        WorkflowDefinitionRepository workflowDefinitionRepository =
                mock(WorkflowDefinitionRepository.class);
        StepExecutionRepository stepExecutionRepository =
                mock(StepExecutionRepository.class);
        WorkflowTaskContextBuilder contextBuilder =
                mock(WorkflowTaskContextBuilder.class);
        WorkflowTaskWorkerRegistry workerRegistry =
                mock(WorkflowTaskWorkerRegistry.class);

        WorkflowTaskExecutionService service =
                new WorkflowTaskExecutionService(
                        stepExecutionEngine,
                        workflowExecutionService,
                        workflowDefinitionRepository,
                        stepExecutionRepository,
                        contextBuilder,
                        workerRegistry
                );

        UUID workflowId = UUID.randomUUID();

        when(stepExecutionEngine.claimNextServiceTask(workflowId))
                .thenReturn(Optional.empty());

        boolean executed = service.executeNextTask(workflowId);

        assertFalse(executed);

        verify(stepExecutionEngine).claimNextServiceTask(workflowId);
        verifyNoInteractions(
                workflowExecutionService,
                workflowDefinitionRepository,
                stepExecutionRepository,
                contextBuilder,
                workerRegistry
        );
    }


    @Test
    void shouldExecuteWorkerAndPersistSuccessfulOutput() {
        StepExecutionEngine stepExecutionEngine =
                mock(StepExecutionEngine.class);
        WorkflowExecutionService workflowExecutionService =
                mock(WorkflowExecutionService.class);
        WorkflowDefinitionRepository workflowDefinitionRepository =
                mock(WorkflowDefinitionRepository.class);
        StepExecutionRepository stepExecutionRepository =
                mock(StepExecutionRepository.class);
        WorkflowTaskContextBuilder contextBuilder =
                mock(WorkflowTaskContextBuilder.class);
        WorkflowTaskWorkerRegistry workerRegistry =
                mock(WorkflowTaskWorkerRegistry.class);

        WorkflowTaskExecutionService service =
                new WorkflowTaskExecutionService(
                        stepExecutionEngine,
                        workflowExecutionService,
                        workflowDefinitionRepository,
                        stepExecutionRepository,
                        contextBuilder,
                        workerRegistry
                );

        UUID workflowId = UUID.randomUUID();
        UUID stepExecutionId = UUID.randomUUID();

        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getStepExecutionId()).thenReturn(stepExecutionId);
        when(stepExecution.getStepName()).thenReturn("validate-order");

        PersistedStepExecution claimedStep =
                new PersistedStepExecution(stepExecution, 1L);

        WorkflowExecution workflow = mock(WorkflowExecution.class);
        when(workflow.getWorkflowType()).thenReturn("ORDER_PROCESSING");
        when(workflow.getDefinitionVersion()).thenReturn(1);

        PersistedWorkflowExecution persistedWorkflow =
                new PersistedWorkflowExecution(workflow, 1L);

        WorkflowStepDefinition stepDefinition =
                new WorkflowStepDefinition(
                        "validate-order",
                        StepType.SERVICE_TASK,
                        "ORDER_VALIDATION",
                        null,
                        null
                );

        WorkflowDefinition definition = new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                List.of(stepDefinition)
        );

        WorkflowTaskContext context = new WorkflowTaskContext(
                workflowId,
                stepExecutionId,
                "ORDER_VALIDATION",
                Map.of("workflow", Map.of("orderId", "ORD-123"))
        );

        Map<String, Object> output =
                Map.of("valid", true, "validationStatus", "APPROVED");

        WorkflowTaskWorker worker = mock(WorkflowTaskWorker.class);

        when(stepExecutionEngine.claimNextServiceTask(workflowId))
                .thenReturn(Optional.of(claimedStep));
        when(workflowExecutionService.getById(workflowId))
                .thenReturn(persistedWorkflow);
        when(workflowDefinitionRepository.findByTypeAndVersion(
                "ORDER_PROCESSING", 1))
                .thenReturn(Optional.of(definition));
        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(claimedStep));
        when(contextBuilder.build(
                workflow,
                stepDefinition,
                definition.steps(),
                List.of(claimedStep),
                stepExecutionId
        )).thenReturn(context);
        when(workerRegistry.getWorker("ORDER_VALIDATION"))
                .thenReturn(worker);
        when(worker.execute(context))
                .thenReturn(WorkflowTaskResult.success(output));

        boolean executed = service.executeNextTask(workflowId);

        assertTrue(executed);

        verify(worker).execute(context);
        verify(stepExecutionEngine).handleStepSuccess(
                workflowId,
                stepExecutionId,
                output
        );
        verify(stepExecutionEngine, never())
                .handleStepFailure(any(), any(), anyString());
    }


    @Test
    void shouldDelegateWorkerFailureToStepExecutionEngine() {
        StepExecutionEngine stepExecutionEngine =
                mock(StepExecutionEngine.class);
        WorkflowExecutionService workflowExecutionService =
                mock(WorkflowExecutionService.class);
        WorkflowDefinitionRepository workflowDefinitionRepository =
                mock(WorkflowDefinitionRepository.class);
        StepExecutionRepository stepExecutionRepository =
                mock(StepExecutionRepository.class);
        WorkflowTaskContextBuilder contextBuilder =
                mock(WorkflowTaskContextBuilder.class);
        WorkflowTaskWorkerRegistry workerRegistry =
                mock(WorkflowTaskWorkerRegistry.class);

        WorkflowTaskExecutionService service =
                new WorkflowTaskExecutionService(
                        stepExecutionEngine,
                        workflowExecutionService,
                        workflowDefinitionRepository,
                        stepExecutionRepository,
                        contextBuilder,
                        workerRegistry
                );

        UUID workflowId = UUID.randomUUID();
        UUID stepExecutionId = UUID.randomUUID();

        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getStepExecutionId()).thenReturn(stepExecutionId);
        when(stepExecution.getStepName()).thenReturn("validate-order");

        PersistedStepExecution claimedStep =
                new PersistedStepExecution(stepExecution, 1L);

        WorkflowExecution workflow = mock(WorkflowExecution.class);
        when(workflow.getWorkflowType()).thenReturn("ORDER_PROCESSING");
        when(workflow.getDefinitionVersion()).thenReturn(1);

        PersistedWorkflowExecution persistedWorkflow =
                new PersistedWorkflowExecution(workflow, 1L);

        WorkflowStepDefinition stepDefinition =
                new WorkflowStepDefinition(
                        "validate-order",
                        StepType.SERVICE_TASK,
                        "ORDER_VALIDATION",
                        null,
                        null
                );

        WorkflowDefinition definition = new WorkflowDefinition(
                "ORDER_PROCESSING",
                1,
                List.of(stepDefinition)
        );

        WorkflowTaskContext context = new WorkflowTaskContext(
                workflowId,
                stepExecutionId,
                "ORDER_VALIDATION",
                Map.of("workflow", Map.of("orderId", "ORD-123"))
        );

        WorkflowTaskWorker worker = mock(WorkflowTaskWorker.class);

        when(stepExecutionEngine.claimNextServiceTask(workflowId))
                .thenReturn(Optional.of(claimedStep));
        when(workflowExecutionService.getById(workflowId))
                .thenReturn(persistedWorkflow);
        when(workflowDefinitionRepository.findByTypeAndVersion(
                "ORDER_PROCESSING", 1))
                .thenReturn(Optional.of(definition));
        when(stepExecutionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(claimedStep));
        when(contextBuilder.build(
                workflow,
                stepDefinition,
                definition.steps(),
                List.of(claimedStep),
                stepExecutionId
        )).thenReturn(context);
        when(workerRegistry.getWorker("ORDER_VALIDATION"))
                .thenReturn(worker);
        when(worker.execute(context))
                .thenThrow(new IllegalStateException("Order validation failed"));

        boolean executed = service.executeNextTask(workflowId);

        assertTrue(executed);

        verify(stepExecutionEngine).handleStepFailure(
                workflowId,
                stepExecutionId,
                "Order validation failed"
        );

        verify(stepExecutionEngine, never())
                .handleStepSuccess(any(), any(), anyMap());
    }


}
