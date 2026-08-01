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

- Status: `candidate`
- Source: 1 (README known simplifications)
- Symptom: `RaidenParty` models rain swords as zero-damage Hydro aura ticks, so Xingqiu contributes aura
  application but no direct damage in the contribution chart.
- Scope: `src/java/model/character/`, `src/java/simulation/party/`
- Risk: `planned`
- Proof: `./gradlew ReactionRegressionTest`, `./gradlew RaidenParty` with a recorded total delta
- Notes: needs a sourced multiplier and ICD grouping before implementation.

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

- Status: `candidate`
- Source: 1 (README known simplifications)
- Symptom: random Vacuum Blade procs make optimizer and sample totals vary run to run, which weakens every
  numeric baseline comparison.
- Scope: `src/java/model/weapon/`, `src/java/mechanics/optimization/`
- Risk: `planned`
- Proof: repeated `./gradlew RaidenParty` runs showing a controlled or reported distribution
- Notes: prefer a seeded or expectation-based option over removing the mechanic.

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

- Status: `candidate`
- Source: 1 (README known simplifications)
- Symptom: some custom effects substitute deterministic values for random or field-position behavior, so the
  reported total is a point estimate presented as exact.
- Scope: `src/java/model/character/`, `src/java/mechanics/buff/`
- Risk: `local`
- Proof: regression asserting the stand-in value plus a log or report note making it visible
- Notes: these are non-canonical Lunar mechanics; making the assumption explicit may be preferable to
  modeling randomness.

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

- Status: `candidate`
- Source: 2 (sample output observed while verifying B-006)
- Symptom: `README.md` and the verification skill record 17,044,468 damage /
  246,664 DPS, while repeated runs at the current revision produce 15,892,535
  damage / 233,028 DPS.
- Scope: `README.md`, `.agents/skills/verify-genshin-changes/`
- Risk: `local`
- Proof: repeated `./gradlew FlinsParty2` runs with identical totals, followed
  by `python scripts/validate_agent_assets.py`
- Notes: documentation-only correction. The mismatch predates B-006 and is not
  caused by its regression-test change.
