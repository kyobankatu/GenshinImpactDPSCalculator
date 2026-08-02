# Work discovery

This exists so a long autonomous session can continue past the end of its plan without inventing scope.
It is a replenishment procedure, not a licence to refactor.

## Entry condition

Discovery starts only when all of these hold:

- the newest user instruction has been converted into an inclusion/exclusion filter;
- every in-scope phase of every active `TASKS.md` plan block is marked ` - Done`, or is waiting only on a
  recorded asynchronous check while independent work is available;
- no explicitly requested in-scope work remains in the primary queue;
- the working tree is clean and the last verification passed.

If any is false, the session has work already. Go do that instead. Plans explicitly paused by the user are
not active for this entry condition; mark their ledger item `deferred` and never rediscover them until the
user explicitly resumes that subsystem.

## Approved sources, in priority order

| Priority | Source | How to sweep it |
|---|---|---|
| 0 | Explicit content-coverage request | inventory missing requested characters, constellations, weapons, artifacts, and parties once; prioritize vertical slices and shared prerequisites |
| 1 | `README.md` known simplifications | each bullet is a pre-approved, user-authored gap; the highest-confidence backlog in the repository |
| 2 | Observable output defects | warnings, `WARN`/`ERROR` lines, and implausible values in `ReactionRegressionTest`, `PartyCatalogRegressionTest`, `ReportRegressionTest`, `RaidenParty`, `FlinsParty2`, and generated report HTML |
| 3 | Game-accuracy divergence | implemented mechanic versus a sourced specification: ICD grouping, gauge consumption, buff snapshot timing, energy particle rules, unimplemented character/weapon/artifact hooks |
| 4 | Test and documentation coverage | mechanics with no regression, missing boundary cases for timing/caps/cooldowns/ICD windows/buff expiry, classes or major methods lacking Javadoc |
| 5 | RL stack improvements | training stability, rollout throughput, observation or reward gaps, evaluation tooling, Python test coverage |

Sources 1 and 2 come from the repository itself and need no external evidence. Source 3 always needs a
citation. Sources 4 and 5 need a named symptom, not a preference. Skip any row excluded by the current
scope filter; in particular, a simulator-only session does not sweep source 5.

Source 0 exists only when the user explicitly asks for broad content expansion. In that mode, missing
coverage is the requested deliverable and need not be reframed as a defect. Each implementation still needs
source readiness, a proof command, and a bounded ownership surface.

## Forbidden zone

`TASKS.md` `## Deferred Systems` is out of scope for autonomous discovery. As of this writing that covers:

- defensive shield absorption and player damage intake;
- enemy attacks, stagger, movement, and survival pressure;
- multi-target geometry and positioning;
- exploration-specific systems;
- full open-world status interactions.

These are deliberate project-level deferrals and large architectural changes. Note the item in `BACKLOG.md`
with status `deferred` and a one-line rationale, then move on. Only the user can unlock one.

Also forbidden without an explicit request: changing damage formula order, RL observation/action/privileged
tensor shapes, the binary rollout protocol, `.gitignore`, `CLAUDE.md`, the Gradle build's structure, and
committed `docs/` output.

## Value gate

An item passes only if it can state all four:

1. **Defect or gap** — what is wrong or missing, in one sentence.
2. **Symptom** — the observable consequence: a wrong number, a missing chart series, an untested branch, a
   warning line, a throughput figure.
3. **Proof of completion** — the command or artifact inspection that will settle it.
4. **Boundary** — which package or role owns the change.

Automatic rejections:

- "the code would be cleaner / more modern / more consistent";
- a refactor with no behavioral symptom;
- a performance change with no measurement;
- anything requiring a new external dependency;
- anything whose acceptance criteria cannot be checked by a command.

The defect/symptom requirement does not reject an explicit Source 0 coverage unit. Its observable gap is the
absence of a loadable, executable, tested content slice named by the campaign inventory.

## Risk gate

| Tier | Scope | Authority |
|---|---|---|
| `local` | one phase's worth of change inside a single role boundary, no public API or contract change | proceed directly |
| `planned` | multiple files, a new class or package, or a mechanic that crosses boundaries | write a `TASKS.md` plan first via `plan-genshin-implementation`, then execute phase by phase |
| `blocked` | needs a decision, a source that could not be found, or hardware that is unavailable | record and move on |
| `deferred` | inside the forbidden zone, or needs user authority | record and move on |

Contract-level surfaces stay `planned` at minimum even when the edit looks small: damage formula order,
`StatType`, action masks, observation and privileged layouts, party ordering, capability profiles, checkpoint
metadata, and the rollout protocol.

## Evidence rule for accuracy work

Never change game-accuracy behavior from inference or memory. An item in source 3 requires:

- a recorded source via `research-genshin-evidence`, with title, version or date, and access date;
- an explicit classification: adopt, adapt, experiment, reject, or unresolved;
- a bounded regression that fails before the change and passes after.

If no source can be established, the entry becomes `blocked`. A plausible-looking formula change with no
provenance can silently break behavior that was already correct, and the sample totals will not reveal it.

## Duplicate suppression

`BACKLOG.md` is the memory that makes the loop safe across restarts.

- Check the ledger before proposing anything. Match on the affected file plus the symptom, not on wording.
- `done`, `rejected`, and `deferred` are settled. Do not re-propose; if genuinely new evidence appears, add a
  new entry that references the old ID and states what changed.
- Never revert a `done` item without a new entry explaining why the original was wrong.
- Never re-litigate a rejection the user recorded.
- Record rejections too. An unrecorded rejection will be rediscovered next cycle.

## Ledger schema

`BACKLOG.md` holds one table row or one short block per item:

| Field | Content |
|---|---|
| ID | stable, never reused, for example `B-014` |
| Status | `candidate`, `planned`, `in-progress`, `done`, `rejected`, `blocked`, `deferred` |
| Source | which priority source above produced it |
| Symptom | the observable consequence |
| Scope | affected paths or packages |
| Risk | `local` or `planned` |
| Proof | the command or artifact that settles it |
| Notes | rationale for a rejection, deferral, or block; plan block name once planned |

For Source 0, use one campaign entry plus a compact unit table instead of one verbose backlog block per
omission. Update the table at the autonomous documentation checkpoint. The session record tracks completed
implementation commits between checkpoints.

## Cycle

1. Confirm the entry condition and scope filter.
2. Sweep sources in priority order until enough candidates exist to choose from.
3. Check each against the ledger; drop settled duplicates.
4. Apply the value gate, then the risk gate. Record every candidate including rejections.
5. Promote exactly one accepted item to `in-progress`. For `planned` risk, write the `TASKS.md` plan first.
6. Implement under the owning domain skill: `develop-genshin-simulator`, `validate-genshin-mechanics`,
   `add-genshin-content`, `validate-genshin-reports`, `develop-genshin-rl`, or
   `benchmark-genshin-optimization`.
7. Verify with `verify-genshin-changes`, commit, mark the entry `done`.
8. Return to step 1.

When an explicitly authorized sub-agent is available, the primary agent may assign a second accepted,
independent item through `coordinate-genshin-agents`. The delegate works from an isolated baseline and cannot
change the ledger status. The primary integration queue still closes one item at a time.

One item in flight at a time. Two half-finished items are worse than one finished item.

## Convergence

The loop ends, cleanly, when a full sweep of all five sources produces no candidate that passes both gates.
Report that the backlog is exhausted and point at the ledger. Rejected and deferred entries are the evidence
that the sweep happened and are more useful than a manufactured task.
