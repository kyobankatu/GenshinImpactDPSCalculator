# Autonomous session contract

The governing constraint: the user is asleep or otherwise unavailable. A session that halts waiting for an
answer has failed, even if every individual decision in it was defensible. Design the session so no approval
is ever needed.

## Pre-cleared authority

Do these without asking:

- read anything in the working tree;
- create and edit files under `src/`, `config/`, `scripts/`, `.agents/skills/`, `.claude/skills/`;
- update `TASKS.md` plan status, phases, and cross-cutting rules;
- add and update `BACKLOG.md` ledger entries, including recording rejections and deferrals;
- update `README.md` and `AGENTS.md` when a change makes their statements untrue;
- run `./gradlew` builds, regression entry points, and sample parties;
- run `python -m pytest src/python/rl/tests`, `python scripts/agent_validate.py`,
  `python scripts/validate_agent_assets.py`, `python scripts/preflight.py`;
- create a work branch from current `master`;
- commit on `dev_0` or a session branch, and push it including the first upstream-setting push;
- stash and restore paths the session itself modified.

## Deferred, never attempted, never asked about

Record these in the handoff and keep working on something else:

- committing or pushing to `master`; opening or merging a pull request;
- force push, history rewrite, hard reset of a branch with an upstream;
- deleting stashes, branches, or tags the session did not create;
- recursive or wildcard deletion of any directory; deleting `logs/` or `output/` content;
- cancelling scheduler jobs the session did not submit;
- submitting long GPU jobs unless the session's task explicitly asked for a run;
- installing packages, changing `requirements.txt` pins, or altering `.venv`;
- anything that sends data off the machine or publishes output;
- rewriting `CLAUDE.md`, `.gitignore`, or the Gradle build in ways that change project policy.

## Deciding without asking

Apply in order:

1. **Most literal reading.** Implement exactly what the task says, not the larger thing it implies.
2. **Reversibility.** Prefer the option that a later commit can undo. Additive beats destructive; new file
   beats rewriting a shared one; feature-local change beats a cross-package refactor.
3. **Existing precedent.** Copy the pattern already used by the nearest comparable code, even when a
   different design looks better. Stability outranks cleanliness in this repository.
4. **Narrow scope.** When a task could be read as touching one subsystem or several, do the one and list the
   others in the handoff as candidate follow-ups.
5. **Record the fork.** Every applied decision gets one line: what was chosen, what was not, and what would
   change the answer.

If all readings of a task require a deferred action, the item is blocked. Write the block and move on.

## Queue discipline

- Derive an explicit scope filter from the newest user instruction. Excluded subsystems never enter the queue,
  discovery sweep, delegated work, or opportunistic cleanup. A previously active excluded plan is marked paused
  in `TASKS.md` and its ledger item is deferred until the user explicitly resumes it.
- Build the queue from allowed `TASKS.md` phases plus explicitly requested work. Include spare independent items so
  a block never leaves the session with nothing to do.
- Order by dependency first, then by risk: land the low-risk verifiable items before speculative ones.
- Integrate one phase at a time on the primary branch. Delegated branches may be in flight concurrently only
  when the user explicitly authorized sub-agents and their write sets do not overlap.
- Re-derive the queue from the repository, not from memory, after any interruption.

## Asynchronous jobs and delegated branches

- Submission is not completion. Record the scheduler job ID, source revision or dirty-state hash, command,
  resource, output paths, and retry safety before leaving the job in the background.
- Never poll in a tight loop or wait for a queued/training job when independent allowed work exists. Check at
  phase boundaries or when the scheduler's state is likely to have changed.
- A mandatory asynchronous result keeps its owning phase in `validating`. Continue only work that does not
  depend on that result, and never claim the phase complete early.
- Sub-agent authority is never inferred from session length. When the user explicitly grants it, compose
  `coordinate-genshin-agents` and assign a backlog-gated task to an isolated branch or worktree with a fixed
  baseline, exact write set, tests, deadline, and no publication authority.
- The primary agent owns conflict resolution, review, integration order, final verification, commits to the
  session branch, pushes, scheduler actions, and user communication. Do not wait for a delegate unless its
  result is the next unavoidable dependency.
- At wind-down, collect completed delegate handoffs and close session-owned delegates. Leave unfinished
  branches unintegrated and record their exact state; never rush an unverified merge to meet the deadline.

## Replenishment

The session is a loop, not a single pass. When every phase of every active `TASKS.md` plan is done and no
requested work remains, replenish rather than stop:

1. Enter `discover-genshin-work` and sweep the approved in-scope sources against `BACKLOG.md`.
2. Promote exactly one item that passes the value and risk gates.
3. For risk `local`, implement directly. For risk `planned`, write the `TASKS.md` plan block first through
   `plan-genshin-implementation`, then execute its phases.
4. Verify, commit, mark the ledger entry `done`, and loop.

Guard rails that make the loop safe to leave running:

- `TASKS.md` `## Deferred Systems` is forbidden. Record such items as `deferred` and move on.
- Game-accuracy changes need a recorded source; without provenance the item is `blocked`, never guessed.
- Contract surfaces stay risk `planned` at minimum: damage formula order, `StatType`, action masks,
  observation and privileged layouts, party ordering, capability profiles, checkpoint metadata, rollout
  protocol.
- Record rejections in the ledger, or the next cycle rediscovers them.
- Never revert a `done` item, or re-litigate a rejection the user recorded, without a new ledger entry.
- One item in flight. Two half-finished items are worse than one finished item.

The loop has two clean endings: convergence, meaning a full sweep produces no item passing both gates, or the
time box expiring. Report whichever applies and stop. Manufacturing work to fill remaining time is a failure,
not diligence.

Time-limited sessions add a scheduling constraint on top of this loop; see
[time-box.md](time-box.md) for deadline handling, the fit test before starting work, the reserve, and the
wind-down procedure.

## Per-phase loop

1. Restate the phase's acceptance criteria and target files from `TASKS.md`.
2. Implement the narrowest change that satisfies them.
3. Run `python scripts/preflight.py --run` and any additional command the phase's Verification section names.
4. On failure: fix forward if the cause is inside the phase's own change; otherwise revert the phase's edits,
   record the failure and its output, and move to the next queue item. Do not leave a broken tree.
5. Update the phase heading to ` - Done` and refresh the plan status.
6. Commit with a conventional subject; push if the branch has an upstream.
7. Append to the session record.

## Session record

Keep one running record so the session is resumable without live context. Put durable plan state in
`TASKS.md` and volatile run state in an untracked scratch file, not in tracked files.

Each entry: timestamp, phase, action taken, commands run with observed results, decision forks with
rationale, blocks discovered, next intended item.

## Honesty rules

- Verification claims must name the command and quote or summarize its real output.
- A command that was not run is "not run", never "should pass".
- A partially implemented phase stays not-done, even if the remaining work looks trivial.
- If a benchmark or damage total changes, report the old and new numbers rather than asserting equivalence.

## Handoff

One message at session end containing:

- branch, base revision, and ordered commit subjects;
- phases completed and their verification evidence;
- phases attempted and reverted, with failure output;
- every deferred action and why it was deferred;
- remaining queue in priority order;
- the exact next command to run;
- anything the user must decide before the next session can proceed.
