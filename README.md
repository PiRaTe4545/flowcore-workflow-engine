# FlowCore

FlowCore is a durable workflow orchestration engine built with Java 17, Spring Boot, and PostgreSQL. The initial milestone establishes the application foundation; workflow execution, workers, retries, and Saga compensation will be implemented in later milestones.

## Requirements

- JDK 17 or newer
- Maven 3.9+ (or configure the Maven wrapper in a future change)
- Docker Desktop with Docker Compose

## Local setup

1. Copy the example environment file and update the local database password:

   ```bash
   cp .env.example .env
   ```

2. Start PostgreSQL:

   ```bash
   docker compose up -d postgres
   ```

3. Export the variables from `.env` into your shell. For example, with a POSIX-compatible shell:

   ```bash
   set -a
   source .env
   set +a
   ```

   Do not commit `.env`; it is ignored by Git.

4. Run the tests:

   ```bash
   mvn test
   ```

5. Start the application:

   ```bash
   mvn spring-boot:run
   ```

   Flyway applies database migrations automatically during startup.

## Health endpoints

- Application endpoint: `GET http://localhost:8080/api/v1/health`
- Spring Boot Actuator: `GET http://localhost:8080/actuator/health`

Example application response:

```json
{
  "status": "UP",
  "service": "flowcore",
  "timestamp": "2026-01-01T12:00:00Z"
}
```

The application health endpoint reports process/API availability. Actuator health additionally reports configured infrastructure health, including the database when its health indicator is available.

## Initial database schema

Migration `V1__create_workflow_executions.sql` creates `workflow_executions`, with a UUID identifier, workflow type, definition version, constrained status, JSONB input payload, timestamps, and query indexes. Do not edit a migration after it has been applied in a shared environment; add a new versioned migration instead.

## Current scope

Included:
- Spring Boot application and REST health endpoint
- PostgreSQL connection configuration through environment variables
- Flyway migration for workflow executions
- Modular package boundaries and a health endpoint test

Not included yet:
- Workflow creation and execution APIs
- Worker scheduler or distributed leases
- Retries, timeout handling, or crash recovery
- Saga compensation
- Kafka or a UI

## Configuration

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL for PostgreSQL |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `SERVER_PORT` | HTTP port (defaults to 8080) |
| `POSTGRES_DB` | Local Docker database name |

The database URL, username, and password have no application-level defaults, so credentials are not embedded in application configuration.

## Architecture notes

See `docs/architecture.md` for the initial package boundaries and `docs/state-machines.md` for the planned state model.
