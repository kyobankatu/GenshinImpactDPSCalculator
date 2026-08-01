---
name: run-genshin-autonomous-session
description: Run long unattended implementation sessions on this repository without pausing for approval, by building an ordered work queue, staying inside pre-cleared authority, choosing safe reversible defaults when authority is unclear, checkpointing durably after every phase, replenishing the queue from the backlog when a plan finishes, and never idling. Use whenever work must proceed for hours while the user is unavailable.
---

# Run an unattended Genshin session

1. Read [autonomous-contract.md](references/autonomous-contract.md), root `AGENTS.md`, and the active `TASKS.md` plan. Build an ordered work queue with more independent items than the session can finish before making the first edit.
2. Treat the user as absent for the whole session. Never ask a question, request confirmation, or wait for approval. An unanswered prompt stalls the session, which is worse than a documented conservative choice.
3. When authority or intent is unclear, take the narrowest reversible action that satisfies the most literal reading of the task, record the decision and the alternative you did not take, and continue. Escalate only in the final handoff.
4. Keep every action inside pre-cleared authority: edit `src/`, `config/`, `scripts/`, `.agents/`, `.claude/skills/`, and `TASKS.md`; commit and push a work branch. Anything outside that is deferred to the handoff, never attempted and never asked about mid-session.
5. Work one plan phase at a time. Implement, run the routed verification, commit, then advance. Never leave the tree non-building between phases, and never start a phase whose predecessor failed verification.
6. Never idle. When an item is blocked by a missing decision, a failing dependency, unavailable hardware, or a needed approval, write the block down and move to the next independent queue item immediately.
7. When the queue empties because every plan phase is done, replenish it through `discover-genshin-work` against the `BACKLOG.md` ledger. Promote one gated item, implement it, and continue the loop. Never invent an item that the ledger's value and risk gates would reject.
8. Checkpoint durably after every phase: update the plan status and the ledger entry, commit with a conventional subject, and append to the session record. Assume the session can be killed between any two tool calls.
9. Report honestly. A failed check is recorded as failed with its output; a skipped check is recorded as skipped. Never infer a result you did not observe in order to keep the loop moving.
10. Close with one handoff: completed phases and ledger entries, exact verification commands and their observed results, every deferred decision with its rationale, the remaining queue, and the next command to run.

Never batch a risky action because no one is watching, and never leave the repository in a state that needs your live context to interpret. Widen scope only through the ledger's gates; if a full discovery sweep finds nothing that passes them, stop and report rather than manufacturing work.
