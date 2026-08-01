---
name: run-genshin-autonomous-session
description: Run long unattended or time-limited implementation sessions on this repository without pausing for approval, by building an ordered work queue, staying inside pre-cleared authority, choosing safe reversible defaults when authority is unclear, checkpointing durably after every phase, replenishing the queue from the backlog when a plan finishes, sizing work against a deadline with a wind-down reserve, and never idling. Use whenever work must proceed for hours while the user is unavailable.
---

# Run an unattended Genshin session

1. Read [autonomous-contract.md](references/autonomous-contract.md), root `AGENTS.md`, and the active `TASKS.md` plan. Build an ordered work queue with more independent items than the session can finish before making the first edit.
2. Establish the deadline first. Read [time-box.md](references/time-box.md), resolve the stated stop time to an absolute wall clock value with `date`, and record it. When no deadline is given, assume the session can be killed abruptly at any moment and optimize for that instead.
3. Treat the user as absent for the whole session. Never ask a question, request confirmation, or wait for approval. An unanswered prompt stalls the session, which is worse than a documented conservative choice.
4. When authority or intent is unclear, take the narrowest reversible action that satisfies the most literal reading of the task, record the decision and the alternative you did not take, and continue. Escalate only in the final handoff.
5. Keep every action inside pre-cleared authority: edit `src/`, `config/`, `scripts/`, `.agents/`, `.claude/skills/`, `TASKS.md`, and `BACKLOG.md`; commit and push a work branch. Anything outside that is deferred to the handoff, never attempted and never asked about mid-session.
6. Work one plan phase at a time. Implement, run the routed verification, commit, then advance. Never leave the tree non-building between phases, and never start a phase whose predecessor failed verification.
7. Check the clock before promoting any item or starting any phase. Start it only when its work plus verification plus commit fits in the time remaining before the wind-down reserve. Prefer a smaller item over a partially finished larger one.
8. Never idle. When an item is blocked by a missing decision, a failing dependency, unavailable hardware, or a needed approval, write the block down and move to the next independent queue item immediately.
9. When the queue empties because every plan phase is done, replenish it through `discover-genshin-work` against the `BACKLOG.md` ledger. Promote one gated item, implement it, and continue the loop. Never invent an item that the ledger's value and risk gates would reject.
10. Checkpoint durably after every phase: update the plan status and the ledger entry, commit with a conventional subject, and append to the session record. Assume the session can be killed between any two tool calls, and keep the repository self-describing at every commit boundary.
11. Enter wind-down when the reserve is reached. Stop promoting work, finish or revert the in-flight phase, commit, synchronize `TASKS.md` and `BACKLOG.md` with reality, push, and write the handoff. Never carry an unfinishable phase past the deadline.
12. Report honestly. A failed check is recorded as failed with its output; a skipped check is recorded as skipped. Never infer a result you did not observe in order to keep the loop moving.
13. Close with one handoff: completed phases and ledger entries, exact verification commands and their observed results, every deferred decision with its rationale, anything reverted at wind-down, the remaining queue, and the next command to run.

Never batch a risky action because no one is watching, and never leave the repository in a state that needs your live context to interpret. Widen scope only through the ledger's gates; if a full discovery sweep finds nothing that passes them, stop and report rather than manufacturing work.
