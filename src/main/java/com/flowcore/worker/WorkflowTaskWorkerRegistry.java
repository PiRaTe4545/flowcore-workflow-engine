
package com.flowcore.worker;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class WorkflowTaskWorkerRegistry {

    private final Map<String, WorkflowTaskWorker> workers;

    public WorkflowTaskWorkerRegistry(
            List<WorkflowTaskWorker> registeredWorkers
    ) {
        Objects.requireNonNull(
                registeredWorkers,
                "registeredWorkers cannot be null"
        );

        Map<String, WorkflowTaskWorker> workerMap = new HashMap<>();

        for (WorkflowTaskWorker worker : registeredWorkers) {
            Objects.requireNonNull(worker, "worker cannot be null");

            String taskType = Objects.requireNonNull(
                    worker.taskType(),
                    "worker taskType cannot be null"
            );

            if (taskType.isBlank()) {
                throw new IllegalArgumentException(
                        "worker taskType cannot be blank"
                );
            }

            WorkflowTaskWorker existing =
                    workerMap.putIfAbsent(taskType, worker);

            if (existing != null) {
                throw new IllegalStateException(
                        "Multiple workers registered for taskType: "
                                + taskType
                );
            }
        }

        this.workers = Map.copyOf(workerMap);
    }

    public WorkflowTaskWorker getWorker(String taskType) {
        Objects.requireNonNull(taskType, "taskType cannot be null");

        WorkflowTaskWorker worker = workers.get(taskType);

        if (worker == null) {
            throw new IllegalArgumentException(
                    "No worker registered for taskType: " + taskType
            );
        }

        return worker;
    }
}
