# Planned workflow state model

The first schema allows these workflow statuses: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, and `CANCELLED`.

These are initial persistence values, not yet an implemented state machine. Before execution logic is added, define and test allowed transitions. Later milestones may introduce explicit retry-wait, compensation, and manual-intervention states through a versioned database migration.
