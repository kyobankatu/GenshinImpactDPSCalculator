# Time box

A time-limited session has one dominant failure mode: being cut off in the middle of a phase, leaving a
half-implemented mechanic that costs more to diagnose in the morning than the phase was worth. Everything
below exists to make that impossible.

## Establishing the deadline

At session start, before the first edit:

1. Resolve the stated stop time to an absolute wall clock value. Run `date` rather than assuming the current
   time; never compute a deadline from an estimate of how long you have been running.
2. Record the deadline and the derived wind-down entry time in the session record.
3. Re-read the clock with `date` at every phase boundary and before every promotion. Elapsed-time intuition
   is unreliable across long sessions.

When no deadline is stated, treat the session as subject to abrupt termination at any moment. The
kill-safety rules below still apply in full; only the wind-down scheduling drops out.

## Reserve

The last **15 minutes** before the deadline are reserved. They are not working time.

- Wind-down entry time = deadline minus 15 minutes.
- Once that time is reached, no new item is promoted and no new phase is started, even if the remaining work
  looks trivial and even if the loop has momentum.
- The reserve is spent finishing, committing, synchronizing records, pushing, and writing the handoff.

## Fit test before starting anything

Before promoting a backlog item or starting a plan phase, estimate its full cost: implementation, routed
verification, and commit. Start it only if that fits before wind-down entry.

| Remaining before wind-down | What may be started |
|---|---|
| comfortably more than one phase | any queued phase, or promote a new item |
| roughly one phase | a `local` risk item or a single small phase; do not open a `planned` multi-phase item |
| less than one phase | nothing new; do documentation, ledger, or record work that is complete in itself |
| inside the reserve | wind-down only |

Verification cost dominates on this project. A phase touching `src/java/mechanics/` pulls in
`./gradlew build` plus `ReactionRegressionTest`; an optimizer phase pulls in two full sample parties. Budget
for the check set the router will select, not just for the edit.

Prefer finishing a small item over half-finishing a large one. A completed `local` item is worth more at
handoff than 70% of a `planned` one.

## Kill safety

Assume termination between any two tool calls. Therefore:

- Never let uncommitted work exceed one phase. Commit at every phase boundary, not at item boundaries.
- Keep the repository self-describing at every commit boundary: `TASKS.md` phase status and `BACKLOG.md`
  entry status must match what the tree actually contains. A commit that advances code without advancing
  those records creates exactly the ambiguity this rule exists to prevent.
- Update the status records in the same commit as the change they describe, not in a follow-up commit.
- Push after each commit when the branch has an upstream, so a killed session's work is not only local.
- The worst acceptable outcome of an abrupt kill is the loss of one in-flight phase.

## Wind-down procedure

On reaching wind-down entry time:

1. **Resolve the in-flight phase.** If it can be completed and verified inside the reserve, finish it.
   Otherwise revert its edits, return the ledger entry to `candidate`, and note in the entry what was
   attempted and why it was rolled back. Never leave partial implementation behind.
2. **Verify.** Run `python scripts/preflight.py --run` on the final tree. A wind-down that ends on an
   unverified tree is a failed session regardless of how much was implemented.
3. **Synchronize records.** Make `TASKS.md` phase markers and `BACKLOG.md` statuses describe reality
   exactly. Correct any status that ran ahead of the code.
4. **Commit and push.** Leave no uncommitted change other than deliberately untracked local files.
5. **Write the handoff**, including anything reverted in step 1 and the exact next command.

If wind-down itself runs out of time, prioritize in this order: commit what is verified, correct the status
records, push. A truthful record with less code beats more code with a misleading record.

## Reverting rather than leaving partial work

Reverting at a deadline is the correct outcome, not a failure. Record it plainly:

- the ledger entry returns to `candidate` with a note naming the phase and the reason;
- the plan phase keeps its heading without ` - Done`;
- the handoff states what was rolled back so the next session does not assume it exists.

A reverted attempt that is documented is cheap to resume. An undocumented partial implementation is expensive
to even detect.

## Anti-patterns

- Starting a `planned` multi-phase item with one phase's worth of time left.
- Skipping verification to fit a phase in before the deadline.
- Marking a phase ` - Done` because the deadline arrived rather than because it is done.
- Batching several phases into one commit to save time.
- Treating the reserve as slack to be reclaimed when the work is going well.
- Estimating remaining time from memory instead of reading the clock.
