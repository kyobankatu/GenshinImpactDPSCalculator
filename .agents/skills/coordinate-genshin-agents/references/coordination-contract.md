# Coordination contract

Good independent scopes include mechanic-source research, snapshot-state audit, Java/Python protocol audit, report attribution audit, or read-only benchmark-design critique.

Keep tightly coupled edits together: `CombatSimulator` with runtime policies, reaction/aura/ICD state, Java/Python protocol pairs, and party definition plus RL registry changes.

Every assignment states:

- objective and exact working set;
- read/write authority and forbidden paths;
- baseline revision and dirty-tree ownership;
- expected artifact or response;
- validation and completion criteria;
- deadline or stopping condition when applicable.

## Implementation isolation

- Start each coding delegate from an immutable named baseline on its own branch or worktree. Record both in
  the assignment and handoff.
- Give each delegate a disjoint write set. If two tasks need the same runtime, mechanic, test, plan, or ledger
  file, serialize them instead of relying on conflict resolution.
- Keep `TASKS.md`, `BACKLOG.md`, publication, scheduler operations, and integration commits with the primary
  agent unless one of those paths is the delegate's entire bounded assignment.
- Delegates return a commit or patch plus evidence; they do not merge, rebase, push, or declare the parent
  phase complete.
- The primary agent reviews the diff against the assigned baseline, rejects scope drift, integrates one branch,
  reruns routed verification, then updates durable status.

Long-running external jobs and sub-agents are sidecars, not reasons to idle. The primary agent continues a
non-overlapping queue item and checks sidecars only at useful boundaries. At deadline, unverified sidecar work
stays unintegrated and is reported with its exact branch or job identity.
