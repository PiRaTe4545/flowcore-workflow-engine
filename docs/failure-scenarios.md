# Planned failure scenarios

The workflow engine milestone must design and test recovery for:

- Engine restart after a workflow has been acknowledged.
- Worker termination after claiming a step.
- External side effect succeeding before the result is persisted.
- Duplicate step delivery.
- Stale worker attempting to commit after its lease expires.
- Compensation action failing or timing out.

These behaviors are not implemented in the initial foundation.
