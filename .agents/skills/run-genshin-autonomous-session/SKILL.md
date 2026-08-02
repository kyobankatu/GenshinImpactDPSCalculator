---
name: run-genshin-autonomous-session
description: Run long unattended or time-limited implementation sessions on this repository without pausing for approval, including scope-filtered queues, implementation-first content batches, bounded documentation checkpoints, proactively selected branch-isolated sub-agents after explicit authorization, non-blocking external jobs, backlog replenishment, and deadline wind-down. Use whenever work must proceed for hours while the user is unavailable.
---

# Run an unattended Genshin session

1. Read [autonomous-contract.md](references/autonomous-contract.md), root `AGENTS.md`, and the active `TASKS.md` plan. Record the user's included and excluded subsystems, then build an ordered work queue containing only allowed work and more independent items than the session can finish before making the first edit. Mark which units are local critical-path work and which are eligible parallel sidecars.
2. Establish the deadline first. Read [time-box.md](references/time-box.md), resolve the stated stop time to an absolute wall clock value with `date`, and record it. When no deadline is given, assume the session can be killed abruptly at any moment and optimize for that instead.
3. Treat the user as absent for the whole session. Never ask a question, request confirmation, or wait for approval. An unanswered prompt stalls the session, which is worse than a documented conservative choice.
4. When authority or intent is unclear, take the narrowest reversible action that satisfies the most literal reading of the task, record the decision and the alternative you did not take, and continue. Escalate only in the final handoff.
5. Keep every action inside pre-cleared authority: edit `src/`, `config/`, `scripts/`, `.agents/`, `.claude/skills/`, `TASKS.md`, and `BACKLOG.md`; commit and push a work branch. Anything outside that is deferred to the handoff, never attempted and never asked about mid-session.
6. Integrate one implementation unit at a time on the primary branch. Implement, run the routed verification, commit, then advance. A content unit is one character, weapon, artifact, party, or shared prerequisite with its tests. Parallel branches do not count as integrated work until the primary agent reviews and verifies them; never leave the primary tree non-building or advance past a failed predecessor.
7. Check the clock before promoting or starting any implementation unit. Start it only when its work plus verification plus commit fits before the wind-down reserve. Prefer a smaller unit over a partial larger one.
8. Never idle on an external job. Record its immutable identity and artifact paths, poll it only at useful intervals, and immediately continue an independent allowed item. A mandatory result keeps its phase in validation; it does not reserve the primary agent's attention.
9. When the user explicitly authorizes sub-agents for a session, treat that as continuing authority for the whole session. At queue creation and every implementation or documentation checkpoint, run a delegation-fit scan through `coordinate-genshin-agents`. Start useful sidecars proactively when at least two independent units exist and the primary has non-overlapping local work; do not spawn merely to occupy capacity.
10. Start with at most two concurrent delegates. Prefer bounded content research, inventory, independent review, or implementation with disjoint source and test files. When the shared `ReactionRegressionTest` would collide, keep that file with the primary or serialize coding delegates. Give coding delegates an isolated branch or worktree from an immutable baseline, require a commit or patch plus verification evidence, and keep merge, push, plan, and ledger authority with the primary.
11. Continue meaningful primary work immediately after spawning. Inspect and integrate completed branches one at a time, rerun routed checks on the combined primary tree, close agents that are no longer needed, and never count unintegrated delegate output as completed work.
12. When an item is blocked by a missing decision, failing dependency, unavailable hardware, or needed approval, write the block down and move to the next independent queue item immediately.
13. When the allowed queue empties because every active campaign unit or in-scope plan phase is done, replenish it through `discover-genshin-work` against the ledger and recorded scope filter. An explicit content-coverage campaign remains nonempty while its inventory has ready units. Never reopen an excluded subsystem or invent work outside the request.
14. Checkpoint code after every implementation unit: commit source, config, and tests; push; and append exact verification and evidence to the untracked session record. Batch tracked documentation as specified below instead of creating plan/acceptance documentation commits around every unit.
15. Enter wind-down when the reserve is reached. Stop promoting work, finish or revert the in-flight unit, collect or stop only session-owned delegates and jobs, commit, synchronize tracked records, push, and write the handoff. Never carry an unfinishable unit past the deadline.
16. Report honestly. A failed check is recorded as failed with its output; a skipped check is recorded as skipped. Never infer a result you did not observe in order to keep the loop moving.
17. Close with one handoff: completed phases and ledger entries, exact verification commands and their observed results, delegated branches and job IDs, every deferred decision with its rationale, anything reverted at wind-down, the remaining queue, and the next command to run.

Never batch a risky action because no one is watching, and never leave the repository in a state that needs your live context to interpret. Widen scope only through the ledger's gates; if a full discovery sweep finds nothing that passes them, stop and report rather than manufacturing work.

## Documentation cadence

Use implementation-first batching during long content-expansion or repeated-item sessions:

- Write one compact campaign plan before implementation, not one plan block per content unit.
- Update `TASKS.md`, `BACKLOG.md`, and `README.md` after four completed units or 60 minutes since the previous tracked documentation update, whichever comes first.
- Update tracked documentation immediately only when scope/authority changes, a blocker changes the queue, a public contract changes, or later work depends on a newly fixed baseline.
- At each documentation checkpoint, reconcile every completed commit, source URL, assumption, verification result, and remaining item in one concise edit and one docs commit.
- Always synchronize tracked records during wind-down and final handoff, even when the normal batch threshold was not reached.
- Keep documentation work near 10% of active session time. Compress repeated evidence into tables or short batch summaries; never omit required provenance or failed checks to meet this target.
- Do not create the recurring sequence `docs: plan one item` / implementation / `docs: accept one item` for homogeneous content additions. Prefer one campaign-plan commit, several verified implementation commits, then one batch-reconciliation docs commit.
- Update `README.md` only for user-facing commands, supported content, accepted public baselines, or statements made false by the batch. Detailed run evidence belongs in the session record and compact ledger summary.
