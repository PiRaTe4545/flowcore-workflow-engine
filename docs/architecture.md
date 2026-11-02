# FlowCore initial architecture

## Approach

FlowCore starts as a modular monolith: one Spring Boot deployable with package boundaries that keep the domain independent from HTTP and persistence frameworks. This avoids premature distributed-service complexity while leaving room to extract components if justified later.

## Package responsibilities

- `api`: HTTP controllers, request/response DTOs, and API error mapping.
- `application`: application use cases and transaction coordination.
- `domain`: workflow concepts, state transitions, and domain rules.
- `engine`: workflow progression and scheduling decisions.
- `worker`: handler invocation and worker lifecycle.
- `persistence`: database repositories and persistence adapters.
- `definition`: workflow registration, versioning, and validation.
- `compensation`: Saga compensation coordination.
- `observability`: metrics, tracing, and diagnostic context.
- `config`: framework and infrastructure configuration.

Only the application entry point and health API are implemented in the initial foundation. Other packages are intentionally placeholders until their milestone is reached.

## Initial persistence

Flyway owns schema changes. The first migration creates `workflow_executions` with a UUID primary key, workflow type, definition version, status, JSONB input, and timestamps. Application-level state transitions and execution semantics will be introduced in later milestones.
