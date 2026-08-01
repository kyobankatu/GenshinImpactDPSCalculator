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

### B-009 — Zero-gauge attacks are logged as ICD-blocked

- Status: `done`
- Source: 2 (observable `FlinsParty2` output defect)
- Symptom: successful zero-gauge attacks print `[ICD] Applied blocked (None)`,
  falsely reporting an elemental-application rejection and obscuring real ICD
  blocks in the 2,900-line sample log.
- Scope: `src/java/simulation/runtime/CombatActionResolver.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `local`
- Proof: `./gradlew ReactionRegressionTest` plus `./gradlew FlinsParty2` with an
  unchanged numeric baseline
- Notes: completed 2026-08-02. A focused output-capture regression proves that
  zero-gauge attacks remain silent while a positive-gauge application rejected
  by standard ICD retains its diagnostic. `FlinsParty2` remained at 15,892,535
  damage / 233,028 DPS. Positive-gauge actions that use `ICDTag.None` still
  produce real blocked decisions and require separate mechanic evidence rather
  than being hidden by this logging fix.

### B-010 — Reserved special ICD type has no implementation

- Status: `rejected`
- Source: 4 (implementation marker audit)
- Symptom: none; `ICDType.Special` is reserved but has no production or test
  callers.
- Scope: `src/java/model/type/ICDType.java`, `src/java/mechanics/element/ICDManager.java`
- Risk: `local`
- Proof: n/a
- Notes: rejected by the value gate. Implementing an unused policy without a
  mechanic-specific contract or observable caller would manufacture scope.

### B-011 — Sucrose Skill and Burst use incorrect standard ICD defaults

- Status: `done`
- Source: 3 (sourced game-accuracy divergence exposed by B-009 output)
- Symptom: Sucrose's zero-damage Burst cast applies a default 1U Anemo hit,
  while the real Burst DoT and absorbed-element pulses use standard ICD; three
  DoT pulses are suppressed in the current `FlinsParty2` run.
- Scope: `src/java/model/character/Sucrose.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: focused action/reaction regression plus two matching
  `./gradlew FlinsParty2` summaries
- Notes: adopt the maintained KQM TCL table (accessed 2026-08-02), which records
  Skill, Burst DoT, and Burst absorbed damage as 1U with no ICD. KQM's Sucrose
  Evidence Vault (v2.3 test, added 2021-12-16) records simultaneous Anemo and
  absorbed-element Burst damage, and the current Genshin Impact Wiki character
  data independently lists Sucrose gauge/ICD fields. Sources:
  https://library.keqingmains.com/characters/anemo/sucrose,
  https://library.keqingmains.com/evidence/characters/anemo/sucrose, and
  https://genshin-impact.fandom.com/wiki/Sucrose. Pre-fix `FlinsParty2` is
  15,892,535 damage / 233,028 DPS. See `TASKS.md` implementation block
  `Sucrose No-ICD Elemental Application`. Completed 2026-08-02. The Burst cast
  now has 0U, while Skill, Burst DoT, and absorbed damage explicitly use 1U with
  no ICD. Two complete post-fix logs were byte-identical with SHA-256
  `b57d5f0e3619ee2b6788c1c075434a3c1f9b794c8435e60354cdb8d25eac9e40`.
  All five modeled Burst DoT pulses applied; only standard Normal Attack ICD
  blocks remained for Sucrose. The accepted result is 15,562,611 damage /
  228,191 DPS.

### B-012 — Ineffa Overclock incorrectly applies Electro

- Status: `done`
- Source: 3 (sourced game-accuracy divergence exposed by B-009 output)
- Symptom: Overclocking Circuit defaults to 1U/standard ICD, so five of 40
  follow-ups apply Electro in the current `FlinsParty2` run and the other 35
  emit misleading mechanic-level ICD blocks; the passive should apply no aura.
- Scope: `src/java/model/character/Ineffa.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: focused actual-Ineffa regression plus two matching
  `./gradlew FlinsParty2` summaries
- Notes: adopt the Genshin Impact Wiki advanced-property row for Overclocking
  Circuit (Version 5.8 mechanic, accessed 2026-08-02), which identifies Extra
  Lunar-Charged as 0U/no-ICD. The Japanese Genshin Wiki independently describes
  the passive as direct Lunar-Charged damage with no Electro application.
  Sources: https://genshin-impact.fandom.com/wiki/Overclocking_Circuit and
  https://wikiwiki.jp/genshinwiki/%E3%82%A4%E3%83%8D%E3%83%95%E3%82%A1.
  Pre-fix `FlinsParty2` is 15,562,611 damage / 228,191 DPS. See `TASKS.md`
  implementation block `Ineffa Overclock Zero-Gauge Damage`. Completed
  2026-08-02. Two complete post-fix logs were byte-identical with SHA-256
  `5bfd4741b9334150863210acc31be5a5f394c74355dadd2374a6615e1db3ea04`.
  All 40 Overclock follow-ups retained direct damage with zero related ICD
  blocks; only Ineffa's contribution changed, from 3,164,054 to 2,946,003.
  The accepted result is 15,344,560 damage / 224,994 DPS.

### B-013 — Ineffa Skill and Birgitta use incorrect standard ICD

- Status: `done`
- Source: 3 (sourced game-accuracy divergence exposed by B-009 output)
- Symptom: the Skill hit uses standard ICD and Birgitta Discharge inherits the
  Standard/None default, suppressing 18 of 40 periodic Electro applications in
  the current `FlinsParty2` run.
- Scope: `src/java/model/character/Ineffa.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: focused repeated-Birgitta regression plus two matching
  `./gradlew FlinsParty2` summaries
- Notes: adopt the Genshin Impact Wiki Ineffa advanced-property table (accessed
  2026-08-02), which records both Skill Damage and Birgitta Discharge Damage as
  1U/no-ICD. The Japanese Genshin Wiki independently records 1U for the Skill
  and a 2-second Birgitta Discharge cadence. Sources:
  https://genshin-impact.fandom.com/wiki/Ineffa and
  https://wikiwiki.jp/genshinwiki/%E3%82%A4%E3%83%8D%E3%83%95%E3%82%A1.
  Pre-fix `FlinsParty2` is 15,344,560 damage / 224,994 DPS. See `TASKS.md`
  implementation block `Ineffa Skill No-ICD Application`. Completed 2026-08-02.
  Two complete post-fix logs were byte-identical with SHA-256
  `c8e837b24225f64f7a19081fed4d99fbadd8d515c403564894304651a615978a`.
  All 40 Birgitta Discharge hits retained direct damage with zero related ICD
  blocks; only Ineffa's rounded contribution changed, from 2,946,003 to
  3,205,782. The accepted result is 15,604,338 damage / 228,803 DPS.

### B-014 — Flins Thunderous Symphony incorrectly applies Electro

- Status: `planned`
- Source: 3 (sourced game-accuracy divergence exposed by B-009 output)
- Symptom: Thunderous Symphony and its Additional hit inherit the 1U/Standard
  default under the neutral ICD tag. In the current `FlinsParty2` run, all 12
  main and 12 Additional hits enter that shared group; four main and eight
  Additional applications are blocked instead of both actions applying 0U.
- Scope: `src/java/model/character/Flins.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: focused actual-Flins zero-gauge regression plus two matching
  `./gradlew FlinsParty2` summaries
- Notes: adopt the Genshin Impact Wiki Flins advanced-property table (accessed
  2026-08-02), which records Thunderous Symphony Damage and Thunderous Symphony
  Additional Damage as 0U/no-ICD. KQM's maintained Flins TCL independently
  corroborates the two distinct talent hits and their direct Lunar-Charged
  damage model. Sources: https://genshin-impact.fandom.com/wiki/Flins and
  https://library.keqingmains.com/characters/electro/flins. Pre-fix
  `FlinsParty2` is 15,604,338 damage / 228,803 DPS. See `TASKS.md`
  implementation block `Flins Thunderous Symphony Zero-Gauge Damage`.
