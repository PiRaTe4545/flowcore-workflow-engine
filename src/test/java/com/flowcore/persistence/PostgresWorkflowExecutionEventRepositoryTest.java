
package com.flowcore.persistence;

import com.flowcore.application.WorkflowExecutionEventRepository;
import com.flowcore.application.WorkflowExecutionRepository;
import com.flowcore.application.PersistedWorkflowExecution;
import com.flowcore.domain.WorkflowExecution;
import com.flowcore.domain.WorkflowExecutionEvent;
import com.flowcore.domain.WorkflowEventType;
import com.flowcore.domain.WorkflowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PostgresWorkflowExecutionEventRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private WorkflowExecutionEventRepository eventRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM workflow_execution_events");
        jdbcTemplate.update("DELETE FROM workflow_executions");
    }

    @Test
    void shouldAppendAndRetrieveWorkflowExecutionEvent() {
        WorkflowExecution execution = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of("orderId", "order-123")
        );

        PersistedWorkflowExecution persisted =
                executionRepository.save(execution);

        WorkflowExecutionEvent event = new WorkflowExecutionEvent(
                null,
                execution.getWorkflowId(),
                WorkflowEventType.CREATED,
                null,
                WorkflowStatus.PENDING,
                persisted.version(),
                Map.of("source", "api"),
                Instant.now()
        );

        WorkflowExecutionEvent savedEvent =
                eventRepository.append(event);

        assertThat(savedEvent.eventId()).isNotNull();

        List<WorkflowExecutionEvent> events =
                eventRepository.findByWorkflowId(execution.getWorkflowId());

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventId())
                .isEqualTo(savedEvent.eventId());
        assertThat(events.get(0).eventType())
                .isEqualTo(WorkflowEventType.CREATED);
        assertThat(events.get(0).newStatus())
                .isEqualTo(WorkflowStatus.PENDING);
        assertThat(events.get(0).eventPayload())
                .containsEntry("source", "api");
    }

    @Test
    void shouldReturnEmptyHistoryWhenNoEventsExist() {
        List<WorkflowExecutionEvent> events =
                eventRepository.findByWorkflowId(UUID.randomUUID());

        assertThat(events).isEmpty();
    }

    @Test
    void shouldRetrieveEventsInExecutionVersionOrder() {
        WorkflowExecution execution = WorkflowExecution.create(
                "ORDER_PROCESSING",
                1,
                Map.of()
        );

        executionRepository.save(execution);

        Instant now = Instant.now();

        eventRepository.append(new WorkflowExecutionEvent(
                null,
                execution.getWorkflowId(),
                WorkflowEventType.STARTED,
                WorkflowStatus.PENDING,
                WorkflowStatus.RUNNING,
                1,
                Map.of(),
                now.plusSeconds(1)
        ));

        eventRepository.append(new WorkflowExecutionEvent(
                null,
                execution.getWorkflowId(),
                WorkflowEventType.CREATED,
                null,
                WorkflowStatus.PENDING,
                0,
                Map.of(),
                now
        ));

        List<WorkflowExecutionEvent> events =
                eventRepository.findByWorkflowId(execution.getWorkflowId());

        assertThat(events)
                .extracting(WorkflowExecutionEvent::executionVersion)
                .containsExactly(0L, 1L);
    }
}
