# Autonomous session contract

The governing constraint: the user is asleep or otherwise unavailable. A session that halts waiting for an
answer has failed, even if every individual decision in it was defensible. Design the session so no approval
is ever needed.

## Pre-cleared authority

Do these without asking:

- read anything in the working tree;
- create and edit files under `src/`, `config/`, `scripts/`, `.agents/skills/`, `.claude/skills/`;
- update `TASKS.md` plan status, phases, and cross-cutting rules;
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

- Build the queue from `TASKS.md` phases plus explicitly requested work. Include spare independent items so
  a block never leaves the session with nothing to do.
- Order by dependency first, then by risk: land the low-risk verifiable items before speculative ones.
- One phase in flight at a time. Do not interleave edits from two phases in one commit.
- Re-derive the queue from the repository, not from memory, after any interruption.

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
