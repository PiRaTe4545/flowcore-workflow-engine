
package com.flowcore.worker;

import com.flowcore.application.PersistedWorkflowExecution;
import com.flowcore.application.WorkflowExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class WorkflowTaskPoller {

    private static final Logger log =
            LoggerFactory.getLogger(WorkflowTaskPoller.class);

    private final WorkflowExecutionRepository workflowRepository;
    private final WorkflowTaskExecutionService taskExecutionService;
    private final int batchSize;

    public WorkflowTaskPoller(
            WorkflowExecutionRepository workflowRepository,
            WorkflowTaskExecutionService taskExecutionService,
            @Value("${flowcore.worker.polling-batch-size:50}")
            int batchSize
    ) {
        this.workflowRepository =
                Objects.requireNonNull(workflowRepository);
        this.taskExecutionService =
                Objects.requireNonNull(taskExecutionService);

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "Polling batch size must be positive"
            );
        }

        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${flowcore.worker.polling-interval-ms:1000}"
    )
    public void poll() {

        List<PersistedWorkflowExecution> workflows;

        try {
            workflows = workflowRepository.findRunning(batchSize);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to discover running workflows",
                    exception
            );
            return;
        }

        for (PersistedWorkflowExecution persistedWorkflow : workflows) {

            UUID workflowId = persistedWorkflow
                    .workflowExecution()
                    .getWorkflowId();

            try {
                taskExecutionService.executeNextTask(workflowId);
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to execute next task for workflow {}",
                        workflowId,
                        exception
                );
            }
        }
    }
}
