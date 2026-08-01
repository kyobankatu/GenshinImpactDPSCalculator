---
name: discover-genshin-work
description: Find, triage, prioritize, and record the next worthwhile simulator or RL work item when the active plan is finished and the work queue is empty, using the tracked BACKLOG.md ledger, the approved discovery sources, a value and risk gate, duplicate suppression, and an explicit convergence condition. Use to replenish an autonomous session's queue, never to justify unrequested scope.
---

# Discover Genshin work

1. Read [work-discovery.md](references/work-discovery.md) and `BACKLOG.md` before searching. Every candidate is checked against the ledger first; a `done`, `rejected`, or `deferred` entry is settled and must not be re-proposed.
2. Only replenish when the queue is genuinely empty: the active `TASKS.md` plan has every phase marked done, and no explicitly requested work remains. A partially finished plan is never a reason to start discovering.
3. Sweep the approved sources in priority order: `README.md` known simplifications, warnings and gaps visible in regression and sample output, game-accuracy divergences, test and Javadoc coverage holes, then RL stack improvements. Treat `TASKS.md` `## Deferred Systems` as forbidden, not as backlog.
4. Apply the value gate before the effort gate. An item must name the concrete defect or gap, the observable symptom, and how completion will be proven. Discard anything whose only justification is that the code could look better.
5. Apply the risk gate. A single-phase local change inside one role boundary may proceed directly. Anything larger requires a `TASKS.md` plan written first through `plan-genshin-implementation`, then phase-by-phase execution.
6. Never change game-accuracy behavior on inference. An accuracy item needs a recorded source through `research-genshin-evidence`; if provenance cannot be established, mark the entry `blocked` and move on rather than guessing a formula.
7. Record every candidate in `BACKLOG.md` with an ID, source, symptom, proposed scope, risk tier, and status, including the ones you reject and why. The ledger is what stops the next cycle from rediscovering the same item.
8. Promote exactly one accepted item at a time into the queue, implement it under the owning domain skill, verify it, then return here. Do not open a second item while one is in progress.
9. Converge honestly. When a full sweep yields no item that passes both gates, stop the loop, report that the backlog is exhausted, and leave the ledger as the evidence. Do not manufacture work to fill remaining time.

Never re-litigate a rejection the user recorded, never revert a `done` item without a new ledger entry explaining why, and never let discovery become a standing excuse for refactoring working code.
