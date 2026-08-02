# Time box

A time-limited session has one dominant failure mode: being cut off in the middle of an implementation unit,
leaving a half-implemented mechanic that costs more to diagnose than the unit was worth. Everything
below exists to make that impossible.

## Establishing the deadline

At session start, before the first edit:

1. Resolve the stated stop time to an absolute wall clock value. Run `date` rather than assuming the current
   time; never compute a deadline from an estimate of how long you have been running.
2. Record the deadline and the derived wind-down entry time in the session record.
3. Re-read the clock with `date` at every implementation-unit boundary and before every promotion. Elapsed-time intuition
   is unreliable across long sessions.

When no deadline is stated, treat the session as subject to abrupt termination at any moment. The
kill-safety rules below still apply in full; only the wind-down scheduling drops out.

## Reserve

The last **15 minutes** before the deadline are reserved. They are not working time.

- Wind-down entry time = deadline minus 15 minutes.
- Once that time is reached, no new item is promoted and no new implementation unit is started, even if the remaining work
  looks trivial and even if the loop has momentum.
- The reserve is spent finishing, committing, synchronizing records, pushing, and writing the handoff.

## Fit test before starting anything

Before promoting or starting an implementation unit, estimate its full cost: implementation, routed
verification, and commit. Start it only if that fits before wind-down entry.

| Remaining before wind-down | What may be started |
|---|---|
| comfortably more than one unit | any queued unit, or promote a new item |
| roughly one unit | one small unit; do not open a new shared prerequisite campaign |
| less than one unit | nothing new; reconcile documentation or records |
| inside the reserve | wind-down only |

Verification cost dominates on this project. A unit touching `src/java/mechanics/` usually needs
`./gradlew build` plus `ReactionRegressionTest`; an optimizer unit may need affected sample parties. Budget
for the check set the router will select, not just for the edit.

Prefer finishing a small unit over half-finishing a large one.

## Kill safety

Assume termination between any two tool calls. Therefore:

- Never let uncommitted work exceed one implementation unit. Commit each verified unit independently.
- Implementation commits may lead tracked campaign status only within the bounded documentation window:
  four completed units or 60 minutes. The untracked session record must identify every such commit, source,
  verification result, and next unit so abrupt termination remains resumable.
- Synchronize `TASKS.md`, `BACKLOG.md`, and any affected README at each documentation checkpoint, immediately
  for public contract/scope changes, and always during wind-down.
- Push after each commit when the branch has an upstream, so a killed session's work is not only local.
- The worst acceptable outcome of an abrupt kill is one uncommitted unit and bounded tracked-status lag that
  can be reconstructed from pushed commits plus the session record.

## Wind-down procedure

On reaching wind-down entry time:

1. **Resolve the in-flight unit.** If it can be completed and verified inside the reserve, finish it.
   Otherwise revert its edits, return the unit to pending/candidate, and note in the session record what was
   attempted and why it was rolled back. Never leave partial implementation behind.
2. **Verify.** Run `python scripts/preflight.py --run` on the final tree. A wind-down that ends on an
   unverified tree is a failed session regardless of how much was implemented.
3. **Synchronize records.** Reconcile all implementation commits since the last documentation checkpoint so
   `TASKS.md`, `BACKLOG.md`, and user-facing README statements describe reality exactly.
4. **Commit and push.** Leave no uncommitted change other than deliberately untracked local files.
5. **Write the handoff**, including anything reverted in step 1 and the exact next command.

If wind-down itself runs out of time, prioritize in this order: commit what is verified, correct the status
records, push. A truthful record with less code beats more code with a misleading record.

## Reverting rather than leaving partial work

Reverting at a deadline is the correct outcome, not a failure. Record it plainly:

- the ledger unit returns to pending/candidate with a note naming the unit and reason;
- the campaign phase remains incomplete;
- the handoff states what was rolled back so the next session does not assume it exists.

A reverted attempt that is documented is cheap to resume. An undocumented partial implementation is expensive
to even detect.

## Anti-patterns

- Starting a `planned` multi-phase item with one phase's worth of time left.
- Skipping verification to fit a phase in before the deadline.
- Marking a phase ` - Done` because the deadline arrived rather than because it is done.
- Batching several unrelated implementation units into one commit to save time.
- Creating plan and acceptance documentation commits around every homogeneous content unit.
- Treating the reserve as slack to be reclaimed when the work is going well.
- Estimating remaining time from memory instead of reading the clock.
