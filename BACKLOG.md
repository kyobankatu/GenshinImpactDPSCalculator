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

### B-026 — Birgitta summons overlap, overrun, and ignore Burst refresh

- Status: `done`
- Source: 2/3 (observable duplicate output and sourced summon divergence)
- Symptom: each Skill registers an independent 11-hit Birgitta event, old
  streams continue after recast, and Burst does not summon or refresh Birgitta
  at all.
- Scope: `src/java/model/character/Ineffa.java`,
  `src/java/sample/ReactionRegressionTest.java`
- Risk: `planned`
- Proof: actual-Ineffa ten-hit and Skill/Burst refresh regression plus two fresh
  deterministic `FlinsParty2` payloads
- Notes: adopt the current Genshin Impact Wiki and KQM contracts (accessed
  2026-08-02): only one Ineffa-summoned Birgitta may exist; Skill or Burst
  summons/refreshes it; it attacks every two seconds for 20 seconds. Sources:
  https://genshin-impact.fandom.com/wiki/Ineffa and
  https://library.keqingmains.com/characters/electro/ineffa. Extract one helper,
  reuse cancellable periodic lifecycle, and use an inclusive 18-second event
  duration from the +2-second first tick to the +20-second tenth tick. Pre-fix
  audited `FlinsParty2` baseline is 15,434,039 damage / 226,306 DPS. See
  `TASKS.md` implementation block `Ineffa Birgitta Summon Lifecycle`.
  Completed 2026-08-02. Actual-Ineffa regression covers Skill and Burst summon,
  ten-hit lifetime, no +22-second hit, and Skill/Burst replacement. Both
  post-fix `FlinsParty2` payloads report 14,316,424 damage / 213,042 DPS with
  normalized SHA-256
  `1990a24b7dea2d237f7d7823c2ca77c64649eaddd98a1174730fdb3a7384a6bb`.

### B-027 — Aggregate ER calibration permits mid-rotation Burst failures

- Status: `done`
- Source: 2 (observable `FlinsParty2` warning and energy timeline)
- Symptom: the accepted final simulation skips Columbina Burst at 20.3 seconds
  with about 52/60 energy and again at 54.3 seconds with 59.8/60 energy even
  though ER calibration reports convergence.
- Scope: `src/java/model/entity/state/EnergyState.java`,
  `src/java/mechanics/analysis/EnergyAnalyzer.java`,
  `src/java/simulation/runtime/ActionGateway.java`, and focused regressions
- Risk: `planned`
- Proof: interval-level ER regression plus two deterministic `FlinsParty2`
  payloads containing no insufficient-energy warning
- Notes: discovered 2026-08-02 after B-026. The current analyzer divides total
  rotation cost by total particle energy, which hides a deficient interval
  behind later particle income. Close an accounting window on every requested
  Burst, including skipped attempts; replay energy cap and carry; combine the
  final tail with the first preloaded window for cyclic refill. The
  pre-fix baseline is 14,316,424 damage / 213,042 DPS. See `TASKS.md`
  implementation block `Timing-Aware ER Calibration`.
  Completed 2026-08-02. Both accepted runs reserve 141%/132%/105%/193% ER
  for Sucrose/Flins/Ineffa/Columbina, execute all 20 requested Bursts including
  all four Columbina Bursts, and contain no insufficient-energy warning. Both
  report 14,077,198 damage / 203,722 DPS with normalized SHA-256
  `63d95d817af04e4263fb92f8492609296980154f37261fceafbc9222a0d248f6`.

### B-028 — Raiden Eye attacks on a timer without triggering damage

- Status: `done`
- Source: 3 (sourced game-accuracy divergence)
- Symptom: Raiden Skill currently schedules autonomous Eye damage every 0.9
  seconds, producing 22 coordinated attacks in the accepted `RaidenParty` trace
  even when no character deals damage at those exact trigger times.
- Scope: resolved-damage event dispatch, `src/java/model/character/RaidenShogun.java`,
  and focused reaction regression coverage
- Risk: `planned`
- Proof: actual-Raiden idle/damage/cooldown/refresh regression plus two
  deterministic `RaidenParty` payloads
- Notes: adopt the maintained KQM Raiden guide and KQM TCL evidence (accessed
  2026-08-02). The Eye triggers only when an attack deals damage, at most once
  every 0.9 seconds per party, and cooldown begins at the triggering damage.
  Sources: https://keqingmains.com/raiden/ and
  https://library.keqingmains.com/evidence/characters/electro/raiden-shogun.
  Adapt the small real hit delay to a same-timestamp one-shot simulator event.
  Pre-fix `RaidenParty` is 1,331,957 damage / 63,427 DPS. See `TASKS.md`
  implementation block `Raiden Eye Damage Trigger`.
  Completed 2026-08-02. Idle and zero-damage actions no longer trigger Eye;
  timeline and no-time-advance positive damage share one 0.9-second cooldown.
  Both accepted payloads contain 17 damage-triggered Eye attacks and report
  1,283,512 damage / 61,120 DPS with normalized SHA-256
  `58bc339e94e09cbb91ca31f42696a4c2b2c9ce535654916bddfe90e610c6d7fd`.

### B-029 — ReactionCalculator Javadoc describes nonexistent parameters

- Status: `done`
- Source: 4 (Javadoc coverage defect)
- Symptom: `./gradlew javadoc` reports three warnings because
  `convertSwirlName` documents `raw` instead of `aura` and `calculateShatter`
  carries a four-argument generic formula comment for its three parameters.
- Scope: `src/java/mechanics/reaction/ReactionCalculator.java`
- Risk: `local`
- Proof: warning-free `./gradlew javadoc` plus unchanged reaction regression
- Notes: documentation-only correction discovered 2026-08-02; no formula,
  multiplier, public signature, or reaction behavior changes. Completed
  2026-08-02 with warning-free Javadoc and passing reaction regression.

### B-030 — Reaction regression emits a fixture fallback action message

- Status: `done`
- Source: 2 (observable regression output defect)
- Symptom: successful `ReactionRegressionTest` output includes `Reaction Tester
  does nothing specific for SKILL`, which is fixture fallback narration rather
  than a diagnostic or assertion result.
- Scope: `src/java/sample/ReactionRegressionTest.java`
- Risk: `local`
- Proof: passing regression output without the fallback line
- Notes: capture only the fixture Skill call that activates Sunny Morning
  Sleep-In; retain production logging and all assertions. Completed 2026-08-02;
  the regression passes without the fallback line.

### B-031 — Dragon's Bane grants its aura bonus unconditionally

- Status: `done`
- Source: 1/3 (explicit source-code assumption and sourced mechanic divergence)
- Symptom: `DragonsBane.applyPassive` adds 36% all-damage bonus to every stat
  assembly, including no-aura targets and snapshots, even though the passive is
  conditional on the target being affected by Hydro or Pyro.
- Scope: target-dependent weapon capability, damage formula stat resolution,
  Dragon's Bane, and focused reaction regression coverage
- Risk: `planned`
- Proof: actual-weapon damage regression covering Hydro/Pyro, no/Electro aura,
  exact expiry, Vaporize ordering, and repeated snapshot hits
- Notes: adopt the R5 passive description and maintained KQM evidence, accessed
  2026-08-02. HoYoLAB records up to 36% increased damage against enemies
  affected by Hydro or Pyro. KQM's Bane-series testing records that the passive
  applies off-field and to amplifying reaction hits, but not transformative
  reaction damage; its Xiangling guide explicitly classifies Dragon's Bane as
  enemy-state-dependent and non-snapshotting. Sources:
  https://www.hoyolab.com/article/20362150,
  https://library.keqingmains.com/evidence/equipment/weapons, and
  https://keqingmains.com/xiangling/. Adapt by capturing a copied per-hit stat
  view before the triggering hit consumes aura. Existing cataloged parties do
  not equip Dragon's Bane, so no accepted party baseline is expected to change.
  See `TASKS.md` implementation block `Dragon's Bane Target-Aura Passive`.
  Completed 2026-08-02 with a narrow `TargetDependentWeaponEffect` capability.
  `CombatActionResolver` captures copied target stats before reaction handling;
  direct formula callers use the same resolver. Regression covers no/Electro,
  Hydro/Pyro, exact aura expiry, consuming Vaporize, and repeated runtime
  snapshot hits without stored or accumulated bonus.

### B-032 — FlinsParty random streams destabilize ER calibration

- Status: `done`
- Source: 2 (observable sample warning and non-reproducible ER result)
- Symptom: unchanged `FlinsParty` runs choose materially different Sucrose and
  Columbina ER targets, then skip Sucrose Bursts for insufficient energy in the
  final rotation because Moondrift and Favonius use unrelated global random
  draws in each simulator constructed by the optimizer.
- Scope: injectable Favonius/Moondrift draws, `FlinsPartyDefinition` seeds,
  focused regressions, and repeated sample acceptance
- Risk: `planned`
- Proof: draw/cooldown boundary regression plus repeated `./gradlew FlinsParty`
  runs with identical ER results and normalized payloads
- Notes: new evidence after B-005; generic Moondrift randomness remains an
  accepted model choice, but it changes the number of Favonius-eligible damage
  hooks and therefore the energy scenario used during calibration. Two runs on
  2026-08-02 produced initial ER results Sucrose/Columbina 282%/151% and
  233%/148%. The first skipped Sucrose Bursts at 25.9 and 80.6 seconds and
  reported 18,314,718 damage / 174,759 DPS over 104.8 seconds; the second
  skipped at 45.9 and 79.5 seconds and reported 18,455,373 / 177,969 over
  103.7 seconds. Preserve stochastic default constructors, but adapt the sample
  to independent fixed Favonius and Moondrift streams recreated for every
  simulator. See `TASKS.md` implementation block
  `Deterministic FlinsParty Energy Scenario`. Completed 2026-08-02. Both
  accepted runs report 258%/100%/100%/164% ER, 18,241,614 damage / 176,247 DPS
  over 103.5 seconds, 2,771 Windfall triggers, and normalized SHA-256
  `97f8887f286c20ff2872dc3c8fa2d3934e0494a5d1900da1f0e54cd19deaa389`.
  The identical residual Sucrose Burst warnings are not random-stream drift;
  they exposed the separate feasibility defect B-033.

### B-033 — Artifact optimizer silently accepts unreachable ER targets

- Status: `done`
- Source: 2 (reproducible sample warning and optimizer/loadout mismatch)
- Symptom: `FlinsParty` requests 258.3% ER for Sucrose, but the fixed triple-EM
  main stats and ten-liquid-roll per-stat cap can supply only about 166.1% ER;
  `ArtifactOptimizer` returns that underfilled build without a diagnostic and
  the final rotation skips Sucrose Burst at 25.9, 60.6, and 95.3 seconds.
- Scope: artifact ER feasibility reporting/allocation, optimizer pipeline
  contract, party loadout response, and focused plus repeated sample regression
- Risk: `planned`
- Proof: regression that detects an unmet `minER` before final optimization and
  a repeated `FlinsParty` result with an explicit feasible resolution and no
  silently underfilled ER target
- Notes: discovered after B-032 made the stochastic scenario reproducible.
  The deterministic result proves this is not seed variance. Plan must choose
  and document whether an unreachable target changes main stats/loadout,
  changes the legal rotation, or fails explicitly; it must not exceed KQMS roll
  constraints or clamp the requirement without evidence. Adopt fail-fast
  artifact generation and remove the unsupported second Sucrose Burst from
  each outer loop while preserving the Skill and first Burst. See `TASKS.md`
  implementation block
  `Artifact ER Feasibility and Legal FlinsParty Burst Cadence`. Completed
  2026-08-02. Exact-cap, just-over-cap, and insufficient-manual-allocation
  regressions pass. Catalog parties now include equipped artifact-set static
  stats during allocation, and fatal sample errors propagate to Gradle.
  Two accepted `FlinsParty` runs report Sucrose/Flins/Ineffa/Columbina ER of
  109%/100%/100%/180%, three successful Sucrose Bursts, zero warnings,
  18,765,805 damage / 188,601 DPS over 99.5 seconds, and normalized SHA-256
  `acbb84038b3846771d1af195410e3d11daad7f152ea4ea450c37c2d05ee2dd85`.
  Two set-aware RaidenParty runs report 1,317,080 damage / 62,718 DPS over
  21.0 seconds and normalized SHA-256
  `ff1adfd3b3705f1cc34a32036af0950aa8a1246a6412589eb214966b3f3c33dc`.
  FlinsParty2 retains 14,077,198 damage / 203,722 DPS over 69.1 seconds.

### B-034 — RaidenParty validation baseline remains stale after set-aware allocation

- Status: `done`
- Source: 1/4 (README validation baseline and verification-skill coverage)
- Symptom: README's accepted accuracy note records the set-aware result, but
  the validation command list and verification skill still expect the prior
  1,283,512 damage / 61,120 DPS payload.
- Scope: `README.md`, `.agents/skills/verify-genshin-changes/`
- Risk: `local`
- Proof: both references contain the twice-reproduced 1,317,080 damage /
  62,718 DPS baseline and agent-asset validation passes
- Notes: completed 2026-08-02 as a documentation-only follow-up to B-033. No
  simulator behavior or historical TASKS/BACKLOG evidence was changed.

### B-035 — Wandering Evenstar evaluates the owner's bonus before artifact EM

- Status: `done`
- Source: 3 (sourced weapon-mechanic divergence)
- Symptom: `Weapon.applyPassive` runs before artifact stats are merged, so
  Wandering Evenstar derives its owner's 33.6% self-only remainder from base
  and weapon EM while its 14.4% team share uses base, weapon, and artifact EM.
  The two pieces therefore do not total the declared R5 48% conversion for the
  high-EM Sucrose equipped in `FlinsParty`; the buff is also active at time zero
  and dynamically recalculated instead of snapshotting every ten seconds.
- Scope: weapon simulator-initialization capability, Wandering Evenstar timed
  snapshot buffs, focused regression, and FlinsParty acceptance
- Risk: `planned`
- Proof: exact first-activation and ten-second resnapshot regressions plus two
  matching warning-free `./gradlew FlinsParty` payloads
- Notes: adopt the R5 Wildling Nightstar values from HoYoWiki's Wandering
  Evenstar entry and adapt the maintained KQM TCL timing evidence, accessed
  2026-08-02. HoYoWiki specifies a 48% owner conversion every 10 seconds for
  12 seconds, a 30% share of that buff to nearby party members, and off-field
  activation. KQM's v3.2 evidence, added 2022-11-09, records first activation
  after 64 frames and confirms Tulaytullah-series resnapshotting every 10
  seconds. Sources:
  https://wiki.hoyolab.com/pc/genshin/entry/2910 and
  https://library.keqingmains.com/evidence/equipment/weapons. Implement one
  per-equipped-weapon timer with captured flat ATK buffs; do not make generic
  weapon passives aware of artifact or simulator internals. See `TASKS.md`
  implementation block `Wandering Evenstar Timed EM Snapshot`. Completed
  2026-08-02 with a narrow simulator-initialized weapon capability. Regression
  covers pre-activation, the 64-frame boundary, shared owner/ally snapshot
  values, interval stability, the +10-second refresh, off-field activation,
  and two-copy stacking. Two accepted FlinsParty runs retain
  109%/100%/100%/180% ER and three Sucrose Bursts, emit zero warnings, and
  report 18,843,690 damage / 189,384 DPS over 99.5 seconds with normalized
  SHA-256 `5fcfe756770e925d4afde9f5e1bc9a23ba9cd86b2620aa99af3cab4e61234744`.

### B-036 — Shattering Ice Javadoc still describes the removed unconditional buff

- Status: `done`
- Source: 4 (documentation accuracy audit)
- Symptom: `ResonanceManager` class documentation says Shattering Ice applies
  Crit Rate unconditionally and should eventually become aura-conditional,
  while B-018 already implemented and tested the Cryo/Frozen condition.
- Scope: `src/java/mechanics/element/ResonanceManager.java`
- Risk: `local`
- Proof: Javadoc describes the current condition, `./gradlew javadoc` is
  warning-free, and the existing resonance regression passes
- Notes: completed 2026-08-02 as a documentation-only correction. No resonance
  values, aura checks, buff timing, or party baseline changed.

### B-037 — StatsRecorder prints an Ineffa-only debug window in normal samples

- Status: `done`
- Source: 2 (observable FlinsParty and FlinsParty2 output defect)
- Symptom: every recorded simulation prints `[StatsRecorder] Buffs on Ineffa`
  and its internal applicable-buff list at 1.9, 2.0, and 2.1 seconds despite no
  warning or diagnostic condition.
- Scope: `src/java/mechanics/analysis/StatsRecorder.java`
- Risk: `local`
- Proof: `./gradlew FlinsParty` retains its accepted numeric result with zero
  `[StatsRecorder]` lines; report and build regressions pass
- Notes: completed 2026-08-02 by removing only the character/time-specific
  console branch. Snapshot stat and buff-name collection remain unchanged.

### B-038 — Off-field particle energy always uses the four-character multiplier

- Status: `done`
- Source: 3 (sourced energy-mechanic divergence)
- Symptom: `EnergyDistributor` applies an unconditional 0.6 off-field factor,
  so two- and three-character parties receive less particle energy than the
  game's 0.8 and 0.7 party-size factors.
- Scope: party-size particle distribution, energy Javadoc, focused regression,
  and accepted four-character sample baselines
- Risk: `planned`
- Proof: executable two-/three-/four-character particle cases and unchanged
  deterministic `RaidenParty` and `FlinsParty2` results
- Notes: adopt the maintained KQM Energy TCL examples and the community Energy
  reference, accessed 2026-08-02. KQM's controlled two-character nonmatching
  particle example derives the off-field result with a 0.8 factor and its
  full-party examples use 0.6. The community Energy reference explicitly maps
  inactive conversion to 80%, 70%, and 60% for parties of two, three, and four.
  Sources: https://library.keqingmains.com/evidence/combat-mechanics/energy and
  https://genshin-impact.fandom.com/wiki/Energy. Adapt only the off-field range
  multiplier: active collection remains 1.0, particle base values and ER still
  compose normally, flat energy remains unscaled, and four-or-more members use
  the current 0.6 minimum. See `TASKS.md` implementation block
  `Party-Size Particle Energy Multipliers`. Completed 2026-08-02. Regression
  covers two through five registered members, active and off-field neutral
  particles, same-element particles, neutral orbs, flat energy, and an empty
  simulator. RaidenParty retains 100%/175%/179%/174% ER and 1,317,080 damage /
  62,718 DPS over 21.0 seconds. FlinsParty2 retains
  141%/132%/105%/193% ER and 14,077,198 damage / 203,722 DPS over 69.1 seconds;
  neither four-character acceptance run adds an energy or optimizer warning.

### B-039 — Impetuous Winds cooldown reduction is never consumed

- Status: `done`
- Source: 3 (sourced resonance-mechanic divergence)
- Symptom: `ResonanceManager` adds `CD_REDUCTION = 0.05`, but Skill/Burst
  readiness, remaining cooldowns, charge restoration, and snapshots use only
  base cooldown values, so Anemo resonance shortens no cooldown at all.
- Scope: cast-time cooldown state, Character stat adaptation, simulator
  snapshots, actual resonance regression, and unaffected catalog baselines
- Risk: `planned`
- Proof: exact 5%-reduced Skill/Burst and multi-charge boundaries that survive
  snapshot restore, plus unchanged non-Anemo-resonance samples
- Notes: adopt KQM's maintained Elemental Resonance and Cooldowns contracts,
  accessed 2026-08-02. The resonance table lists Anemo resonance as 5% cooldown
  reduction for all Skills and Bursts. The cooldown reference says cooldown is
  calculated at ability cast; its multi-charge experiment was added and last
  tested 2021-04-18 in v1.4 and records that the first charge entering cooldown
  snapshots reduction for the active charge queue. Sources:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/elemental-resonance,
  https://library.keqingmains.com/combat-mechanics/cooldowns, and
  https://library.keqingmains.com/evidence/combat-mechanics/cooldowns. Adapt by
  capturing an effective non-negative duration in `CooldownState` while
  retaining base cooldown metadata. Movement speed and stamina portions of
  Impetuous Winds remain outside the DPS simulator's modeled state. See
  `TASKS.md` implementation block
  `Cooldown Reduction Snapshot and Impetuous Winds`. Completed 2026-08-02.
  Actual two-Anemo regression proves exact 9.5-second Skill and 19.0-second
  Burst boundaries, one-Anemo no-trigger behavior, cast-time persistence after
  a temporary buff expires, multi-charge queue reuse, defensive clamping, and
  exact simulator snapshot restoration. All eight production character models
  now pass simulator-applicable buffs when starting cooldowns. RaidenParty
  retains 100%/175%/179%/174% ER and 1,317,080 damage / 62,718 DPS over 21.0
  seconds; FlinsParty2 retains 141%/132%/105%/193% ER and 14,077,198 damage /
  203,722 DPS over 69.1 seconds, with no new warning in either run.

### B-040 — Sacrificial Sword has no Composed passive

- Status: `done`
- Source: 3 (explicit stat-only weapon and sourced passive divergence)
- Symptom: `SacrificialSword` provides 454 base ATK and 61.3% ER but never
  attempts or applies its Skill cooldown reset, so every equipped simulation
  omits the weapon's defining passive.
- Scope: Skill-cooldown reset state, damage-triggered Sacrificial Sword,
  injected draws, focused regression, and weapon catalog documentation
- Risk: `planned`
- Proof: actual-weapon trigger/failure/internal-cooldown/multi-charge regressions
  plus build and catalog validation
- Notes: adopt the maintained KQM Swords page, KQM Sacrificial-series evidence,
  and the community Sacrificial Sword page, accessed 2026-08-02. All record the
  R5 Composed values as 80% and sixteen seconds; the weapon pages also confirm
  Lv90 454 base ATK and 61.3% ER. KQM experiments record multi-hit Skill retry,
  procs while Skill is already ready, and reset of only the displayed earliest
  timer for multi-charge Skills. Relevant experiments were last tested in
  v1.3 (shield exception, 2021-02-04), v1.5 (multi-charge scope, 2021-05-06),
  v2.0 (multi-hit interactions, 2021-08-19), and v3.3 (ready-Skill proc,
  2022-12-11). Sources:
  https://library.keqingmains.com/equipment/weapons/swords,
  https://library.keqingmains.com/evidence/equipment/weapons, and
  https://genshin-impact.fandom.com/wiki/Sacrificial_Sword. Adapt to the
  simulator's single unshielded target: positive direct Skill damage receives
  one draw per resolved hit, a success resets current Skill state and starts
  weapon cooldown, and a multi-charge reset removes only the earliest restore.
  Enemy shields and multiple-enemy trials remain unmodeled. See `TASKS.md`
  implementation block `Sacrificial Sword Composed Passive`. Completed
  2026-08-02 with an injectable R5 damage-triggered passive and focused
  `CooldownState.resetSkillCooldown`. Regression covers actual dispatch,
  0.799999 success and 0.8 failure, multi-hit retry, same-time and 15.999-second
  suppression, exact 16.0-second eligibility, non-Skill and zero-motion-value
  exclusions, ready-Skill cooldown consumption, multi-charge earliest-only
  reset, null injection, and unchanged Lv90 stats. Catalog validation passes;
  no registered party equips Sacrificial Sword, so accepted sample behavior is
  unchanged.

### B-041 — Viridescent Venerer same-element shred stacks instead of refreshing

- Status: `in-progress`
- Source: 3 (sourced artifact-mechanic divergence)
- Symptom: every eligible Swirl appends another 40% same-element RES shred buff
  to every character, so repeated Pyro/Hydro/Cryo/Electro Swirls add to 80%,
  120%, or more instead of refreshing one ten-second element window. The code
  also checks that the owner is active but not that the owner triggered Swirl.
- Scope: VV eligibility and typed no-stack team-buff refresh, focused
  regression, and deterministic Flins/FlinsParty2 acceptance
- Risk: `planned`
- Proof: same-element refresh/expiry and owner-trigger regressions plus matching
  repeated full-party payloads
- Notes: adopt the maintained KQM Artifacts page and VV evidence, accessed
  2026-08-02. The set grants 40% matching RES shred for ten seconds; different
  Swirled elements can coexist with independent durations. KQM trigger tests,
  added and last tested 2021-05-22 in v1.5, require the equipping character to
  trigger Swirl while on field. Sources:
  https://library.keqingmains.com/equipment/artifacts and
  https://library.keqingmains.com/evidence/equipment/artifacts. Adapt each
  element to one typed simulator team buff replaced through
  `applyTeamBuffNoStack`; preserve different element IDs independently. See
  `TASKS.md` implementation block `Viridescent Venerer Shred Refresh`.

### B-042 — First single-target Swirl does not receive immediate VV shred

- Status: `deferred`
- Source: 3 (sourced artifact/formula-order divergence)
- Symptom: reaction stats are captured before `notifyReaction` applies VV, so
  the triggering single-target Swirl's transformative damage uses pre-shred RES
  even though maintained KQM evidence says the shred applies instantly to that
  Swirl.
- Scope: reaction notification and transformative resistance calculation order
- Risk: `planned`
- Proof: first-Swirl damage regression versus the 40% shred resistance result
- Notes: KQM Artifacts and v1.6 VV evidence, accessed 2026-08-02, state that
  single-target shred applies instantly and increases the Swirl on that enemy.
  Sources: https://library.keqingmains.com/equipment/artifacts and
  https://library.keqingmains.com/evidence/equipment/artifacts. Deferred because
  autonomous discovery is explicitly forbidden from changing damage formula
  order without new user authority. B-041 must not hide or claim to solve this
  separate ordering issue.
