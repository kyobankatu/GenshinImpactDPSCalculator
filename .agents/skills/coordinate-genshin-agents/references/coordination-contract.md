# Coordination contract

Good independent scopes include mechanic-source research, snapshot-state audit, Java/Python protocol audit, report attribution audit, or read-only benchmark-design critique.

Keep tightly coupled edits together: `CombatSimulator` with runtime policies, reaction/aura/ICD state, Java/Python protocol pairs, and party definition plus RL registry changes.

## Session-level parallel lanes

- Treat explicit authorization such as "use sub-agents when useful during this session" as continuing authority
  until the session's deadline or cancellation. Re-evaluate delegation at queue creation and every checkpoint.
- Keep the current critical-path unit with the primary. Spawn a sidecar only when its output is independently
  useful and the primary can proceed without waiting for it.
- Start with at most two concurrent delegates. Good pairings are one bounded implementation plus one evidence
  inventory, or two read-only inventories over distinct content families.
- Central files such as `ReactionRegressionTest`, `TASKS.md`, and `BACKLOG.md` are primary-owned during parallel
  content work. A coding delegate may change its unique content files and return proposed test cases for the
  primary, or coding delegates that need the same test file must be serialized.
- Close completed agents after their handoff is captured. Unverified output never enters the primary branch.

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
