---
name: manage-genshin-experiments
description: Plan, checkpoint, resume, compare, and hand off long-running simulator, optimizer, RL training, evaluation, profiling, sweep, or HPC experiments without relying on chat history. Use when work spans sessions, produces checkpoints, has mutable external state, or needs retry-safe evidence.
---

# Manage Genshin experiments

1. Read repository instructions and `TASKS.md`. Reuse its active implementation plan; do not turn it into a raw training log.
2. Read [experiment-ledger.md](references/experiment-ledger.md). Create the smallest project-local record allowed by the task, excluding credentials and private endpoint values.
3. Record revision, dirty state, objective, owner, workload, seed, environment, command, artifact references, acceptance criteria, status, and next action before a long run.
4. Checkpoint after each material result or failure and before risky, expensive, or interruptible work.
5. Reconcile live process/job/service/checkpoint state before resuming. A failed query is unknown, not evidence of absence.
6. Record exact failures and whether retry is safe. Never replace a queued or ambiguous run without reconciliation.
7. At handoff, state results, modified files, validation, remaining checks, next command or decision, and required authority.
