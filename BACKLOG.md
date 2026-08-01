# Backlog Ledger

Durable record of discovered work items for this repository. Maintained by the
`discover-genshin-work` skill so that autonomous sessions do not rediscover, re-litigate, or oscillate on the
same decisions across restarts.

This is a ledger, not a plan. Implementation detail belongs in `TASKS.md`; run measurements belong in an
experiment record.

## Rules

- IDs are stable and never reused.
- `done`, `rejected`, and `deferred` are settled. Do not re-propose an equivalent item; add a new entry
  referencing the old ID if genuinely new evidence appears.
- Record rejections. An unrecorded rejection is rediscovered next cycle.
- Risk `local` may be implemented directly. Risk `planned` requires a `TASKS.md` plan block first.
- Accuracy items require a recorded source; without provenance the status is `blocked`, never implemented on
  inference.
- One item is `in-progress` at a time.

## Status vocabulary

| Status | Meaning |
|---|---|
| `candidate` | discovered and gated, waiting to be promoted |
| `planned` | has a `TASKS.md` plan block; phases not yet complete |
| `in-progress` | currently being implemented |
| `done` | implemented and verified |
| `rejected` | failed the value gate; settled |
| `blocked` | needs a decision, an unfindable source, or unavailable hardware |
| `deferred` | inside the forbidden zone or needs user authority |

## Items

### B-001 — Xingqiu orbital rain swords deal no damage

- Status: `done`
- Source: 1 (README known simplifications)
- Symptom: the premise conflated zero-damage orbital contact pulses with the
  separate damaging Raincutter sword waves. Direct damage already exists, but
  orbitals currently run every 2.2 seconds under standard three-hit/time ICD,
  producing an incorrect effective Hydro application cadence.
- Scope: `src/java/model/character/`, `src/java/simulation/party/`
- Risk: `planned`
- Proof: `./gradlew ReactionRegressionTest`, `./gradlew RaidenParty` with a recorded total delta
- Notes: KQM's maintained Xingqiu reference separates zero-damage Rain Sword
  orbitals (1U, 2.25-second ICD) from damaging Raincutter sword waves (Burst
  damage, standard ICD). The KQM evidence entry independently records the
  orbital ICD as 2.25 seconds. Accessed 2026-08-01:
  https://library.keqingmains.com/characters/hydro/xingqiu and
  https://library.keqingmains.com/evidence/characters/hydro/xingqiu.
  Pre-fix `RaidenParty`: 1,362,938 damage / 64,902 DPS; Xingqiu contributed
  269,959 damage (19.8%). Completed 2026-08-01 by making the 2.25-second event
  cadence own orbital application while preserving zero damage, 1U Hydro, and
  the separate damaging Raincutter path. Three post-fix runs all produced
  1,440,416 damage / 68,591 DPS with Xingqiu at 283,683 damage (19.7%). The
  single-target simulator still assumes continuous enemy contact.

### B-002 — Xiangling Chili pickup is assumed

- Status: `done`
- Source: 1 (README known simplifications)
- Symptom: `RaidenParty` assumes the Chili pickup unconditionally rather than modeling the pickup event.
- Scope: `src/java/simulation/party/`, `src/java/model/character/`
- Risk: `local`
- Proof: `./gradlew RaidenParty` plus a regression covering the no-pickup path
- Notes: make pickup an explicit party opt-in while keeping the generic
  Xiangling model on the conservative no-pickup path. Completed 2026-08-01;
  `ReactionRegressionTest` covers no pickup and the delayed 10-second buff
  window, while `RaidenParty` remained at 1,362,938 damage / 64,902 DPS.

### B-003 — Skyward Spine random procs make output nondeterministic

- Status: `done`
- Source: 1 (README known simplifications)
- Symptom: random Vacuum Blade procs make optimizer and sample totals vary run to run, which weakens every
  numeric baseline comparison.
- Scope: `src/java/model/weapon/`, `src/java/mechanics/optimization/`
- Risk: `planned`
- Proof: repeated `./gradlew RaidenParty` runs showing a controlled or reported distribution
- Notes: read-only audit at `d0561ea` found two independent causes. Standard and
  Lunar formula strategies dispatch damage hooks before `CombatActionResolver`
  dispatches them again, so a failed 50% Skyward Spine draw receives a second
  same-hit draw and becomes 75% effective probability. `Math.random()` then
  gives optimizer candidates different streams. Three isolated pre-fix runs
  produced 1,362,938 / 64,902, 1,361,884 / 64,852, and 1,362,938 / 64,902 with
  differing optimizer iterations and Vacuum Blade counts. Planned correction:
  single facade-owned hook dispatch plus injected, per-simulator seeded draws
  for `RaidenParty`; stochastic general construction remains available.
  Completed 2026-08-01. Three fresh post-fix summaries had identical SHA-256
  `65330f4d67cf44ba65142950c615fab0d085850a74d2baf33fd1f30f3319cbca`,
  including ER/roll decisions, two optimizer passes, three Vacuum Blade procs,
  per-source contributions, and 1,440,416 damage / 68,591 DPS.

### B-004 — FlinsParty2 shield HP is logged but never consumed

- Status: `deferred`
- Source: 1 (README known simplifications)
- Symptom: defensive shield HP appears in logs but no enemy damage consumes it.
- Scope: `src/java/mechanics/buff/`, `src/java/simulation/runtime/`
- Risk: `deferred`
- Proof: n/a
- Notes: depends on defensive shield absorption and player damage intake, both listed under `TASKS.md`
  `## Deferred Systems`. Requires user authority to unlock.

### B-005 — FlinsParty2 custom effects use deterministic stand-ins

- Status: `done`
- Source: 1 (README known simplifications)
- Symptom: some custom effects substitute deterministic values for random or field-position behavior, so the
  reported total is a point estimate presented as exact.
- Scope: `src/java/model/character/`, `src/java/mechanics/buff/`
- Risk: `local`
- Proof: regression asserting the stand-in value plus a log or report note making it visible
- Notes: completed 2026-08-01 by naming the two actual assumptions: active
  Gravity Ripple implies nearby field position, and Thundercloud's 33% extra
  strike chance is represented as expected damage. The report labels the latter
  as `33% Expected Extra`; regression covers the expected value and inclusive
  expiry boundaries. Moondrift's separate 33% extra attack remains random and
  is documented as such. `FlinsParty2` remained at 15,892,535 damage / 233,028
  DPS.

### B-006 — FlinsParty2 sample can fire Flins burst below full energy

- Status: `done`
- Source: 1 (README known simplifications)
- Symptom: the scripted sample emits a warning when the burst fires below full energy, so the rotation is
  known to be slightly invalid.
- Scope: `src/java/simulation/party/`
- Risk: `local`
- Proof: `./gradlew FlinsParty2` with no energy warning, and a recorded total delta
- Notes: the premise became stale after commit `27c99a1d`, which separated
  Flins's 80-energy maximum from Thunderous Symphony's active 30-energy cost
  and made insufficient bursts skip. Confirmed 2026-08-01 without changing the
  rotation or ER target; regression now covers the 29/80 failure and 30/80
  success boundaries. Two unchanged-tree `FlinsParty2` runs both produced
  15,892,535 damage / 233,028 DPS with no Flins insufficient-energy warning.

### B-007 — NCCL/DDP distributed RL training

- Status: `deferred`
- Source: user request
- Symptom: recurrent PPO trains in a single process, leaving the second GPU of an `rtx6000-ada2_2`
  allocation idle.
- Scope: `src/python/rl/`
- Risk: `planned`
- Proof: the per-phase verification matrix in the plan block
- Notes: paused by the user on 2026-08-01 so the current autonomous session can
  focus exclusively on the simulator. The retained `TASKS.md` plan may be
  resumed only by a future explicit RL request.

### B-008 — FlinsParty2 audited numeric baseline is stale

- Status: `done`
- Source: 2 (sample output observed while verifying B-006)
- Symptom: `README.md` and the verification skill record 17,044,468 damage /
  246,664 DPS, while repeated runs at the current revision produce 15,892,535
  damage / 233,028 DPS.
- Scope: `README.md`, `.agents/skills/verify-genshin-changes/`
- Risk: `local`
- Proof: repeated `./gradlew FlinsParty2` runs with identical totals, followed
  by `python scripts/validate_agent_assets.py`
- Notes: documentation-only correction completed 2026-08-01. Two consecutive
  current-revision runs and the independent B-006 audit produced the same
  15,892,535 damage / 233,028 DPS result. The mismatch predated B-006 and was
  not caused by its regression-test change.
