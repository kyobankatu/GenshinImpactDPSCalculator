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

- Status: `done`
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
  Completed 2026-08-02. Three post-fix simulator payloads matched after
  excluding Gradle's elapsed-time status line, with SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`.
  All 12 main and 12 Additional hits retained direct damage with zero related
  ICD blocks or elemental reactions. Flins changed from 7,004,707 to 6,834,944
  and Columbina from 4,349,846 to 4,349,309. The accepted result is 15,434,039
  damage / 226,306 DPS.

### B-015 — Flins standard Burst delayed hits incorrectly apply Electro

- Status: `done`
- Source: 1 (source audit of adjacent Flins Burst metadata)
- Symptom: the standard Burst initial, middle, and final actions omit explicit
  ICD metadata and therefore normalize to 1U/Standard under the neutral tag.
  The documented contract is 1U/no-ICD for the initial hit and 0U/no-ICD for
  every middle and final hit.
- Scope: `src/java/model/character/Flins.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: focused actual-Flins standard-Burst metadata/reaction regression plus
  two matching `./gradlew FlinsParty2` summaries
- Notes: adopt the Genshin Impact Wiki Flins advanced-property table (accessed
  2026-08-02), which records Initial Skill Damage as 1U/no-ICD and both Middle
  Phase and Final Phase Lunar-Charged Damage as 0U/no-ICD. KQM's maintained
  Flins TCL corroborates the initial, middle, and final talent-hit split and
  direct Lunar-Charged model. Sources:
  https://genshin-impact.fandom.com/wiki/Flins and
  https://library.keqingmains.com/characters/electro/flins. The current
  `FlinsParty2` rotation does not execute the standard Burst, so its pre-fix
  baseline remains 15,434,039 damage / 226,306 DPS. See `TASKS.md`
  implementation block `Flins Standard Burst Elemental Application`. Completed
  2026-08-02. Two post-fix simulator payloads matched after excluding Gradle's
  elapsed-time line, with SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`.
  Both logs contained zero standard-Burst hits and retained the accepted
  15,434,039 damage / 226,306 DPS baseline.

### B-016 — Flins Skill activation and Spearstorm use incorrect application metadata

- Status: `done`
- Source: 1 (source audit of adjacent Flins Skill metadata)
- Symptom: Manifest Flame activation is modeled as a Physical `OTHER` action,
  while the documented activation is a damageless 0U Electro Skill attack.
  Northland Spearstorm is modeled with standard ICD instead of 1U/no-ICD.
- Scope: `src/java/model/character/Flins.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: focused actual-Flins activation/Spearstorm regression plus two
  matching `./gradlew FlinsParty2` summaries
- Notes: adopt the Genshin Impact Wiki Ancient Rite advanced-property table
  (accessed 2026-08-02), which records Activation Knockback as 0U/no-ICD and
  Northland Spearstorm Damage as 1U/no-ICD. Its gameplay notes identify the
  activation as a damageless Electro attack. The Japanese Genshin Wiki's Flins
  testing notes independently identify the special Skill as a separate
  elemental application. Sources:
  https://genshin-impact.fandom.com/wiki/Ancient_Rite%3A_Arcane_Light and
  https://wikiwiki.jp/genshinwiki/%E6%83%85%E5%A0%B1%E6%8F%90%E4%BE%9B/%E3%83%95%E3%83%AA%E3%83%B3%E3%82%BA.
  The current `FlinsParty2` baseline is 15,434,039 damage / 226,306 DPS, with
  four form activations and 12 Spearstorm hits. See `TASKS.md` implementation
  block `Flins Skill Activation Metadata`. Completed 2026-08-02. Two post-fix
  payloads and the pre-fix payload matched after excluding Gradle's elapsed-time
  line, with SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`.
  Both logs retain four activations and 12 Spearstorm hits with zero related ICD
  blocks. The accepted result remains 15,434,039 damage / 226,306 DPS.

### B-017 — Dendro resonance omits reaction-triggered EM buffs

- Status: `done`
- Source: 2 (source scan of explicit approximation comments)
- Symptom: Sprawling Greenery applies only its permanent +50 EM and omits the
  independently timed +30 and +20 EM reaction-triggered team buffs.
- Scope: `src/java/mechanics/element/ResonanceManager.java`,
  `src/java/mechanics/buff/BuffId.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: focused resonance reaction/duration regression plus unchanged audited
  sample baselines
- Notes: adopt the current Genshin Impact Wiki Dendro and Team Bonus contracts
  (accessed 2026-08-02): +50 base EM; +30 EM for 6 seconds after Burning,
  Quicken, Bloom, or Lunar-Bloom; +20 EM for 6 seconds after Aggravate, Spread,
  Hyperbloom, or Burgeon; the two durations are independent. Sources:
  https://genshin-impact.fandom.com/wiki/Dendro and
  https://genshin-impact.fandom.com/wiki/Team_Bonus. Neither audited sample
  currently has Dendro resonance, so pre-fix baselines are 1,440,416 / 68,591
  for `RaidenParty` and 15,434,039 / 226,306 for `FlinsParty2`. See `TASKS.md`
  implementation block `Dendro Resonance Reaction EM`. Completed 2026-08-02.
  Focused regression covers every trigger kind, Lunar-Bloom, unrelated
  reactions, independent six-second boundaries, and same-group refresh.
  `RaidenParty` remains 1,440,416 / 68,591; `FlinsParty2` remains 15,434,039 /
  226,306 with normalized payload SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`.

### B-018 — Cryo resonance grants unconditional CRIT Rate

- Status: `done`
- Source: 2 (source scan of explicit approximation comments)
- Symptom: Shattering Ice grants +15% CRIT Rate unconditionally, including
  against enemies with no Cryo aura and enemies affected by unrelated elements.
- Scope: `src/java/mechanics/element/ResonanceManager.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: focused dynamic Cryo/Frozen/aura-expiry regression plus unchanged
  audited sample baselines
- Notes: adopt the current Genshin Impact Wiki Cryo contract (accessed
  2026-08-02): +15% CRIT Rate applies only against enemies that are Frozen or
  affected by Cryo. Source: https://genshin-impact.fandom.com/wiki/Cryo. The
  existing time-aware Enemy aura and Freeze state are sufficient; no global
  stat or Buff API change is needed. Neither audited sample has Cryo resonance,
  so pre-fix baselines are 1,440,416 / 68,591 for `RaidenParty` and 15,434,039 /
  226,306 for `FlinsParty2`. See `TASKS.md` implementation block
  `Conditional Cryo Resonance CRIT Rate`. Completed 2026-08-02. Focused
  regression covers no aura, unrelated aura, Cryo, Frozen, and exact aura
  expiry. `RaidenParty` remains 1,440,416 / 68,591; `FlinsParty2` remains
  15,434,039 / 226,306 with normalized payload SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`.

### B-019 — Electro resonance accepts unrelated Lunar reactions

- Status: `done`
- Source: 1 (typed reaction helper audit after resonance corrections)
- Symptom: `ReactionResult.triggersElectroResonance()` returns true for every
  Lunar reaction, so Lunar-Bloom and Lunar-Crystallize can generate Electro
  particles even though High Voltage only includes Lunar-Charged.
- Scope: `src/java/mechanics/reaction/ReactionResult.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: typed eligibility and five-second listener regression plus fresh
  deterministic `FlinsParty2` acceptance
- Notes: adopt the current Genshin Impact Wiki Electro contract (accessed
  2026-08-02): Superconduct, Overloaded, Electro-Charged, Lunar-Charged,
  Quicken, Aggravate, and Hyperbloom generate one Electro particle on a
  five-second cooldown. Source: https://genshin-impact.fandom.com/wiki/Electro.
  Lunar-Bloom, Lunar-Crystallize, Spread, and Burgeon are excluded. Pre-fix
  baselines are 1,440,416 / 68,591 for `RaidenParty` and 15,434,039 / 226,306
  for `FlinsParty2`. See `TASKS.md` implementation block
  `Electro Resonance Typed Trigger Set`. Completed 2026-08-02. Typed and live
  listener regression covers positive/negative sets and the shared five-second
  boundary. `RaidenParty` remains 1,440,416 / 68,591; both `FlinsParty2`
  payloads remain 15,434,039 / 226,306 with normalized SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`.

### B-020 — Guoba incorrectly uses standard Skill ICD

- Status: `done`
- Source: 1 (adjacent periodic-action metadata audit)
- Symptom: Guoba's four flame hits are modeled with standard Skill ICD even
  though each hit applies Pyro independently; the final detailed
  `RaidenParty` trace contains five Guoba hits across casts and two false
  `ElementalSkill` application blocks.
- Scope: `src/java/model/character/Xiangling.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: actual-Xiangling four-hit metadata/reaction regression plus two fresh
  deterministic `RaidenParty` payloads
- Notes: adopt the KQM Theorycrafting Library Xiangling attack table (accessed
  2026-08-02), which records Guoba as 1U Pyro, no ICD, snapshotting Skill
  damage. The same table records Pyronado's three cast swings as standard ICD
  and its periodic Pyronado hit as no ICD, matching current code and excluding
  those actions from this fix. Source:
  https://library.keqingmains.com/characters/pyro/xiangling. Pre-fix audited
  `RaidenParty` baseline is 1,440,416 damage / 68,591 DPS. See `TASKS.md`
  implementation block `Xiangling Guoba No-ICD Application`. Completed
  2026-08-02. Focused regression covers four no-ICD hits with and without a
  Hydro aura. Both post-fix `RaidenParty` payloads report 1,461,315 damage /
  69,586 DPS with normalized SHA-256
  `66fbe8eb153acd729db6d51b9cc545c8fe366e43bc0d126fd5ab30b660477fc4`;
  the final detailed trace contains five Guoba hits and zero Guoba ICD blocks.

### B-021 — Fatal Rainscreen's second hit is blocked by Skill ICD

- Status: `done`
- Source: 3 (adjacent audited-party action metadata divergence)
- Symptom: Xingqiu's two Skill strikes share standard Skill ICD, so the second
  1U Hydro application is blocked in the final `RaidenParty` trace at 2.6
  seconds.
- Scope: `src/java/model/character/Xingqiu.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: actual-Xingqiu two-hit metadata/reaction regression plus two fresh
  deterministic `RaidenParty` payloads
- Notes: adopt the KQM Theorycrafting Library Xingqiu attack table (accessed
  2026-08-02), which records Fatal Rainscreen as two 1U Hydro Skill hits with
  no ICD. Its separate Rain Sword orbitals use 1U/2.25-second application and
  Raincutter sword waves use standard ICD; those paths are already modeled and
  remain excluded. Source:
  https://library.keqingmains.com/characters/hydro/xingqiu. Pre-fix audited
  `RaidenParty` baseline is 1,461,315 damage / 69,586 DPS. See `TASKS.md`
  implementation block `Xingqiu Fatal Rainscreen No-ICD Application`.
  Completed 2026-08-02. Focused regression covers both 1U/no-ICD hits with and
  without an aura. Both post-fix `RaidenParty` payloads report 1,464,729 damage
  / 69,749 DPS with normalized SHA-256
  `2fbe19b421aa18de1dd2d9e342c0de369177635ea49f6644ced01046c0f0342e`;
  the detailed Hit 2 now triggers Electro-Charged without a Skill ICD block.

### B-022 — Bennett Press Skill uses the wrong gauge and ICD

- Status: `done`
- Source: 3 (adjacent audited-party action metadata divergence)
- Symptom: Passion Overload Press is modeled as 1U with standard Skill ICD,
  while the sourced attack is 2U with no ICD; Fantastic Voyage also uses a
  standard type despite its sourced no-ICD metadata.
- Scope: `src/java/model/character/Bennett.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: actual-Bennett Skill/Burst metadata and aura regression plus fresh
  deterministic `RaidenParty` acceptance
- Notes: adopt KQM's Bennett attack table (accessed 2026-08-02), which records
  Press as 2U/no ICD and Fantastic Voyage as 2U/no ICD. Held variants have
  distinct hit structures and remain excluded. Source:
  https://library.keqingmains.com/characters/pyro/bennett. Pre-fix audited
  `RaidenParty` baseline is 1,464,729 damage / 69,749 DPS. See `TASKS.md`
  implementation block `Bennett Skill and Burst Application Metadata`.
  Completed 2026-08-02. Focused regression covers sourced Skill/Burst
  metadata, Overloaded, Vaporize, and isolated no-aura paths. Both post-fix
  `RaidenParty` payloads report 1,433,347 damage / 68,255 DPS with normalized
  SHA-256
  `e35dfd3b864ddbc2ff132dae447eeed51506bbe26c5980fecf1bf1535c0ef59f`.
  The accepted decrease follows the stronger 2U Press aura changing downstream
  reaction ownership.

### B-023 — Raiden cast hits and Musou attacks use incorrect ICD groups

- Status: `done`
- Source: 3 (audited-party action metadata divergence)
- Symptom: Raiden's Skill cast incorrectly shares standard ICD with Eye and
  suppresses its first coordinated application; Burst initial uses standard ICD;
  Burst-state Normal and Charged attacks use independent generic groups instead
  of their existing shared Musou Isshin tag.
- Scope: `src/java/model/character/RaidenShogun.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: actual-Raiden cast/Eye/Burst/shared-group regression plus two fresh
  deterministic `RaidenParty` payloads
- Notes: adopt the current Genshin Impact Wiki advanced properties and KQM
  Raiden table (accessed 2026-08-02): Skill DMG is 1U/no ICD, Eye is 1U/standard
  Skill ICD, Burst initial is 2U/no ICD, and Musou Isshin Normal/Charged attacks
  share standard ICD. Sources:
  https://genshin-impact.fandom.com/wiki/Raiden_Shogun and
  https://library.keqingmains.com/characters/electro/raiden-shogun. The existing
  dedicated `ICDTag.Raiden_MusouIsshin` is currently unused and can express the
  shared group without an API change. Plunge ICD remains excluded pending exact
  grouping evidence. Pre-fix audited `RaidenParty` baseline is 1,433,347 damage
  / 68,255 DPS. See `TASKS.md` implementation block
  `Raiden Cast and Musou Isshin ICD Metadata`. Completed 2026-08-02. Focused
  regression covers cast/Eye separation, 2U/no-ICD Burst initial, shared Burst
  N/CA ICD, and unchanged physical tags. Both post-fix `RaidenParty` payloads
  report 1,402,417 damage / 66,782 DPS with normalized SHA-256
  `c1b6624fb2ea1a3d361a778a5aeead7d731c8bb3f0dae05c5c8d85b6d34c4da0`.

### B-024 — Raiden Skill recast leaves two Eye event streams active

- Status: `done`
- Source: 2 (observable audited-sample duplicate output)
- Symptom: the 14.2-second Skill recast registers a second 0.9-second periodic
  Eye event without ending the first; the final trace contains old-stream hits
  at 15.0 through 20.4 seconds and new-stream hits at 15.6 through 21.0 seconds.
- Scope: `src/java/simulation/event/PeriodicDamageEvent.java`,
  `src/java/model/character/RaidenShogun.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: timer cancellation and actual-Raiden recast regression plus two fresh
  deterministic `RaidenParty` payloads
- Notes: adopt the current KQM and Genshin Impact Wiki Skill contract (accessed
  2026-08-02): Eye is one 25-second status whose coordinated attack can occur
  once every 0.9 seconds per party. Recasting refreshes that status; it does not
  create an independently stacking Eye. Sources:
  https://library.keqingmains.com/characters/electro/raiden-shogun and
  https://genshin-impact.fandom.com/wiki/Raiden_Shogun. Implement explicit,
  idempotent `PeriodicDamageEvent` cancellation and let Raiden retain only the
  current event handle. Pre-fix audited `RaidenParty` baseline is 1,402,417
  damage / 66,782 DPS. See `TASKS.md` implementation block
  `Raiden Eye Refresh Lifecycle`. Completed 2026-08-02. Timer and actual-Raiden
  regression cover idempotent cancellation and replacement cadence. Both
  post-fix `RaidenParty` payloads report 1,361,340 damage / 64,826 DPS with
  normalized SHA-256
  `df040feaf6c35da05783cca75d2992824d3246c654fc917e050888588b9586f4`.
  The final trace contains only the seven replacement-stream hits from 15.6
  through 21.0 seconds after the 14.2-second recast.

### B-025 — Guoba periodic event produces a fifth flame hit

- Status: `done`
- Source: 2 (observable sample hit-count defect after periodic-event audit)
- Symptom: one Guoba cast is configured from +2.0 seconds at 1.5-second
  intervals with an inclusive six-second event duration, producing hits at
  +2.0, +3.5, +5.0, +6.5, and the out-of-duration +8.0 seconds.
- Scope: `src/java/model/character/Xiangling.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: actual-Xiangling timestamp/boundary regression plus two fresh
  deterministic `RaidenParty` payloads
- Notes: adopt the KQM Xiangling table (accessed 2026-08-02), which records
  Guoba as four flame hits during its seven-second duration. Source:
  https://library.keqingmains.com/characters/pyro/xiangling. Preserve generic
  inclusive periodic boundaries because Xingqiu orbital and Sucrose pulse
  regressions rely on them; set Guoba's local duration to end on its fourth
  +6.5-second tick. Pre-fix audited `RaidenParty` baseline is 1,361,340 damage
  / 64,826 DPS. See `TASKS.md` implementation block
  `Guoba Four-Hit Lifetime`. Completed 2026-08-02. Boundary regression confirms
  four exact timestamps and no +8.0-second hit. Both post-fix `RaidenParty`
  payloads report 1,331,957 damage / 63,427 DPS with normalized SHA-256
  `c7f2780605ab245f1482ea80263d396d8bd7b802cbe273d4860f43e8655ec1cf`;
  the final trace contains only four Guoba hits.
