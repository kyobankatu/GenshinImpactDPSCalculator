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

- Status: `done`
- Source: 3 (sourced artifact-mechanic divergence)
- Symptom: every eligible Swirl appends another 40% same-element RES shred buff
  to every character, so repeated Pyro/Hydro/Cryo/Electro Swirls add to 80%,
  120%, or more instead of refreshing one ten-second element window. The code
  also checks that the owner is active but not that the owner triggered Swirl.
- Scope: VV eligibility and typed no-stack team-buff refresh, focused
  regression, and deterministic Flins/FlinsParty2 acceptance
- Risk: `validated`
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
  Completed with owner-trigger and exact refresh/expiry regressions. Two
  deterministic `FlinsParty` runs accept 18,343,092 / 184,353 over 99.5 seconds
  at normalized SHA-256
  `bb6bc281eeb54ad747502b4bc6259715b9d540e1e225735982ea8e30301c26fd`;
  two `FlinsParty2` runs accept 13,633,123 / 197,296 over 69.1 seconds at
  normalized SHA-256
  `95d03747cc7e917445a2b840db0bb2cbad095ecb211064d895bc6ea4c68c798a`.
  ER and durations are unchanged and no warning lines appear. Deferred B-042
  remains outside this correction.

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

### B-043 — Noblesse Oblige duplicate 4pc buffs stack instead of refreshing

- Status: `done`
- Source: 3 (sourced artifact-mechanic divergence)
- Symptom: each Burst-triggered Noblesse application uses normal team-buff
  insertion, so overlapping same-ID windows contribute 40% or more ATK instead
  of one refreshed 20% value.
- Scope: Noblesse typed team-buff replacement, focused boundary regression, and
  deterministic RaidenParty acceptance
- Risk: `validated`
- Proof: actual Bennett activation, duplicate/multi-instance refresh and exact
  expiry tests, plus matching repeated RaidenParty payloads
- Notes: adopt the maintained Genshin Impact Wiki set description and KQM
  Artifacts guide, accessed 2026-08-02. The 4pc effect grants all party members
  20% ATK for twelve seconds and cannot stack; KQM explicitly says two Noblesse
  wearers do not double it. Sources:
  https://genshin-impact.fandom.com/wiki/Noblesse_Oblige and
  https://keqingmains.com/misc/artifacts/. Adapt repeated applications to one
  `NOBLESSE_OBLIGE_4PC` simulator team buff replaced through
  `applyTeamBuffNoStack`. See `TASKS.md` implementation block
  `Noblesse Oblige Non-Stack Refresh`. Completed with actual Bennett owner/ally
  activation, same- and separate-instance refresh, unrelated-buff coexistence,
  exact expiry, and 2pc static regressions. Two RaidenParty logs match normalized
  SHA-256 `ff1adfd3b3705f1cc34a32036af0950aa8a1246a6412589eb214966b3f3c33dc`
  at unchanged 100%/175%/179%/174% ER and 1,317,080 / 62,718 over 21.0
  seconds, with zero warning matches.

### B-044 — Raiden Eye Burst DMG buff stacks on legal Skill recast

- Status: `done`
- Source: 3 (sourced character-mechanic divergence)
- Symptom: Raiden's ten-second Skill recast appends another 25-second same-ID
  buff to every recipient, doubling their Energy Cost-scaled Burst DMG Bonus for
  the fifteen-second overlap.
- Scope: Raiden recipient-buff replacement, actual recast boundary regression,
  and deterministic RaidenParty acceptance
- Risk: `validated`
- Proof: one typed source-attributed value per recipient before/after an exact-CD
  recast, exact refreshed expiry, and matching repeated RaidenParty payloads
- Notes: adopt the maintained KQM Raiden TCL and guide, accessed 2026-08-02. The
  TCL describes one Eye granted to nearby members, a Talent 9 value of 0.3% per
  Energy, 25-second duration, and ten-second cooldown; the guide explicitly
  describes subsequent Skill use as refreshing it. Sources:
  https://library.keqingmains.com/characters/electro/raiden-shogun and
  https://keqingmains.com/raiden/. Adapt the singular recipient status through
  `removeBuff(RAIDEN_EYE_OF_STORMY_JUDGMENT)` before adding its newly timed,
  recipient-scaled instance. See `TASKS.md` implementation block
  `Raiden Eye Buff Refresh`. Completed with actual exact-CD recast, independent
  90/60-cost scaling, one typed Raiden-sourced instance, and exact half-open
  expiry regressions. Two RaidenParty logs match normalized SHA-256
  `10df2aa5678cb8697eb6de92437c329ade06f0d06f155513c4c76bad64cabec8`
  at unchanged 100%/175%/179%/174% ER and 1,312,883 / 62,518 over 21.0
  seconds. The 4,197 reduction is isolated to seven Xingqiu Raincutter hits
  after the second Skill; all other damage categories and timings are unchanged.

### B-045 — Silken Gleaming Moon Lunar bonus never activates

- Status: `done`
- Source: 3 (sourced artifact-mechanic divergence)
- Symptom: `MoonsignManager` searches simulator team/field/provider buffs for
  Intent and Devotion even though both are character-owned, so its distinct
  count is always zero and Silken's 10%/20% Lunar Reaction bonus is absent.
- Scope: generic artifact team-buff capability, dynamic Silken distinct-effect
  provider, obsolete manager removal, focused boundaries, and deterministic
  Flins/FlinsParty2 acceptance
- Risk: `validated`
- Proof: provider routing tests; 0/10/20% dynamic, duplicate, off-field, exact
  expiry, and multi-wearer regressions; matching repeated party payloads
- Notes: adopt the Luna I Silken Moon's Serenade description from the HoYoLAB
  guide published 2025-09-19 and maintained artifact database, accessed
  2026-08-02. Elemental damage grants eight-second Devotion and 60/120 team EM,
  including off field; each different Gleaming Moon effect grants 10% party
  Lunar Reaction DMG; generated Gleaming effects cannot stack. Sources:
  https://www.hoyolab.com/article/41239522 and
  https://gi.gachabase.net/artifacts/15042/silken-moons-serenade/beta?lang=en.
  Adapt with a generic artifact team-buff provider and one dynamic typed Silken
  buff that counts distinct unexpired character statuses at resolution time.
  See `TASKS.md` implementation block `Silken Gleaming Moon Dynamic Bonus`.
  Completed with generic provider routing plus actual 0/10/20%, off-field EM,
  duplicate, exact expiry, and multi-wearer regressions. Two FlinsParty logs
  accept 18,930,343 / 190,255 over 99.5 seconds at normalized SHA-256
  `f6d276fde49b6677c928545e689f530c6d7cac492a45f2b952c668bb644b32f6`;
  two FlinsParty2 logs accept 14,194,732 / 205,423 over 69.1 seconds at
  normalized SHA-256
  `491cd43e7077114acbe4f00e38c02030426141331d05a921e598573d82347c40`.
  ER and timings are unchanged, warning matches remain zero, and all increases
  are confined to Lunar-classified character damage.

### B-046 — Expired Ascendant Blessing blocks weaker reactivation

- Status: `done`
- Source: 4 (simulator invariant and sourced duration divergence)
- Symptom: `MoonsignManager` compares a new Blessing against every retained
  typed Blessing without filtering expired entries. Once a high-value source's
  20-second window ends, its stale value can still reject every weaker source
  indefinitely.
- Scope: active-state filtering in Blessing precedence, focused public-path
  boundary regression, and deterministic FlinsParty acceptance
- Risk: `validated`
- Proof: exact-expiry 36%-to-9% replacement plus active stronger and
  equal-refresh boundaries, followed by matching repeated party payloads
- Notes: adopt the maintained Genshin Impact Wiki team-bonus page and Icy Veins
  Moonsign guide, accessed 2026-08-02. They record a 20-second elemental
  stat-scaled Lunar Reaction DMG team bonus capped at 36%; the maintained
  team-bonus page explicitly states that the effect cannot stack. Sources:
  https://genshin-impact.fandom.com/wiki/Team_Bonus and
  https://www.icy-veins.com/genshin-impact/nod-krai-moonsign. Preserve current
  active stronger-value precedence, but apply it only to unexpired typed
  Blessings under the repository's `[start, expiration)` buff contract. See
  `TASKS.md` implementation block `Ascendant Blessing Expiry Replacement`.
  Completed with one typed instance across initial, active conflict, refresh,
  and exact-expiry replacement boundaries. Two FlinsParty logs retain normalized
  SHA-256
  `f6d276fde49b6677c928545e689f530c6d7cac492a45f2b952c668bb644b32f6`,
  18,930,343 damage / 190,255 DPS over 99.5 seconds, unchanged ER, and zero
  warning matches.

### B-047 — Guoba C1 shred stacks and excludes off-field attackers

- Status: `done`
- Source: 3 (sourced character-mechanic divergence)
- Symptom: each Guoba hit appends another 15% same-ID field buff, yielding up to
  60% Pyro RES shred for the active character while excluding off-field party
  members from an opponent status.
- Scope: one typed team-visible Guoba C1 refresh, explicit Xiangling source,
  actual four-hit boundaries, and deterministic RaidenParty acceptance
- Risk: `validated`
- Proof: actual owner/ally 15% visibility, one typed instance over four refreshes,
  +6.5/+12.5 timing boundaries, and matching repeated party payloads
- Notes: adopt the KQM Xiangling TCL, maintained KQM guide, and maintained
  constellation description, accessed 2026-08-02. They describe opponents hit
  by Guoba receiving 15% Pyro RES reduction for six seconds. Sources:
  https://library.keqingmains.com/characters/pyro/xiangling,
  https://keqingmains.com/xiangling/, and
  https://genshin-impact.fandom.com/wiki/Crispy_Outside%2C_Tender_Inside. Adapt
  the opponent status as one `XIANGLING_GUOBA_C1_SHRED` team-visible simulator
  buff refreshed by each actual hit and explicitly sourced by Xiangling. See
  `TASKS.md` implementation block `Guoba C1 Enemy Shred Refresh`.
  Completed with actual +2.0/+3.5/+5.0/+6.5 refreshes, active/off-field stat
  visibility, one typed Xiangling-sourced instance, and exact +12.5 expiry.
  Two RaidenParty logs match normalized SHA-256
  `d7fc2de0e9ece10da808a9fbde36e594f759ddbb6532d654de637f3da6be9c76`
  at 1,310,839 damage / 62,421 DPS over 21.0 seconds with unchanged ER and zero
  warnings. The 2,044 reduction is isolated to Bennett's two 14.8-second Pyro
  actions and associated Overload while the old four same-ID instances were
  active. Snapshot attacks remain governed by B-048.

### B-048 — Resistance shred is incorrectly snapshotted with attacker stats

- Status: `done`
- Source: 2 (B-047 integration trace and formula audit)
- Symptom: `DamageCalculator.resolveStats` returns only the caster's stored
  snapshot for snapshot actions, so live enemy-facing RES shred buffs are
  omitted if applied after cast and retained if they expire before impact.
  B-047 therefore makes Guoba C1 visible to off-field Xiangling stat resolution
  but cannot change an already snapshotted Pyronado hit.
- Scope: classify attacker buffs versus enemy-facing resistance state, resolve
  resistance shred at impact for standard/Lunar/direct reaction paths, focused
  snapshot boundaries, and affected deterministic party baselines
- Risk: `validated`
- Proof: snapshot before shred then hit during it, snapshot during shred then hit
  after expiry, live non-snapshot parity, and full-party delta attribution
- Notes: this is broader than Guoba C1 and also affects Viridescent Venerer,
  Superconduct, and any typed elemental/physical RES shred currently represented
  in `StatsContainer`. The game-facing source for B-047 explicitly places its
  status on opponents; repository snapshot documentation says caster stats are
  captured at cast time. Promote only after auditing every RES-shred producer
  and reaction path and writing a phased `TASKS.md` design; do not patch one
  damage strategy in isolation.
  KQM's maintained Xiangling guide explicitly states that RES, DEF, and other
  enemy-state conditions cannot snapshot, while its team-building guide states
  VV resistance reduction cannot snapshot. The KQM enemy-resistance reference
  defines effective resistance from current base resistance minus current
  reduction. Sources accessed 2026-08-02:
  https://keqingmains.com/xiangling/,
  https://keqingmains.com/misc/team-building/, and
  https://library.keqingmains.com/combat-mechanics/enemy-mechanics/enemy-resistances.
  See `TASKS.md` implementation block `Live Resistance Reduction Resolution`.
  Completed by separating impact-time enemy RES effects from attacker snapshots
  across standard, Lunar, immediate, delayed, core, and weighted reaction paths.
  Focused regression covers activation, exact expiry, unrelated elements,
  first-Swirl ordering, later cross-element Swirl, and delayed tick/explosion
  transitions. Repeated accepted payloads are RaidenParty
  `0b437864068daf8d903a7bd755e2428d96962dbce2df1bee527f909221fc79f0`
  at 1,358,959 / 64,712, FlinsParty
  `0afbc2fd6f3b3d2319a3ff224cf2f1117ace1024240e1cbc4c5dd9185408159e`
  at 20,460,639 / 205,635, and FlinsParty2
  `f3d7a0bdfbfe6f56837135a538d20fe08659df2bba22d8444972968781526f26`
  at 14,794,978 / 214,110. All six integration runs are deterministic and
  warning-free with unchanged durations and ER contracts.

### B-049 — Runtime aura application omits Aura Tax and source decay classes

- Status: `done`
- Source: 1 (README known simplification), confirmed by Source 3 evidence
- Symptom: every finite source currently stores its full action gauge and uses
  `6 + 5U` duration, so 1U/2U/4U begin at 1/2/4 and last 11/16/26 seconds
  instead of beginning at 0.8/1.6/3.2 and lasting 9.5/12/17 seconds. Reapplication
  replaces state and decay rate without same-element extension rules.
- Scope: enemy-owned standard source application, Aura Tax, source decay class,
  non-Pyro and Pyro same-element extension, snapshot preservation, ordinary
  action/EC routing, focused regressions, and audited party baselines
- Risk: `validated`
- Proof: exact 1U/1.5U/2U/4U initial/expiry boundaries, non-Pyro/Pyro extension
  and no-op cases, consume/snapshot restore, actual action/EC routing, and two
  matching payloads for all three audited parties
- Notes: adopt the maintained KQM standard source-application model, accessed
  2026-08-02. Elemental Gauge Theory specifies 0.8 Aura Tax, source-class decay
  rates, first-aura rate retention, same-element max-style extension, and Pyro's
  conditional rate update; the maintained gauge database independently lists
  taxed gauges and durations. Sources:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/elemental-gauge-theory
  and https://library.keqingmains.com/resources/compendiums/elemental-gauges.
  Freeze, Dendro-special reaction tax, Swirl spread, EC terminal ticks,
  multi-target state, and reaction consumption multipliers are excluded. See
  `TASKS.md` implementation block `Standard Aura Tax and Decay Rates`.
  Completed with an enemy-owned source application API, exact source-class
  boundaries, non-Pyro/Pyro extension rules, decay-rate snapshots, ordinary/EC
  runtime routing, and invalid-source handling. Two accepted payloads each are
  RaidenParty
  `1565f197fe7813ef53bc7ee4107a6b68aae7337fdf1efb9e5d865502f2813d80`
  at 1,348,716 / 64,225, FlinsParty
  `b374971bc2ee6237bf0eb9eada25c13b0b6976168a40354cca9b4d050fb77da8`
  at unchanged 20,460,639 / 205,635, and FlinsParty2
  `44712083d51e77ed23637f94b48f390db7414572c14e61123f0085378691968c`
  at unchanged 14,794,978 / 214,110. Raiden's -10,243 delta is exactly two
  immediate EC events and one EC tick removed by the corrected taxed gauges;
  all durations, ER contracts, and warnings remain stable.

### B-050 — Swirl and Crystallize consume full trigger gauge instead of half

- Status: `done`
- Source: 3 (maintained Elemental Gauge Theory divergence)
- Symptom: 1U Anemo/Geo currently subtracts 1U from the aura, so a fresh taxed
  0.8U aura is deleted; the sourced 0.5 modifier should leave 0.3U.
- Scope: typed Swirl, standard Crystallize, and Lunar-Crystallize consumption,
  residual/expiry regressions, unchanged reaction side effects, and three audited
  party baselines
- Risk: `validated`
- Proof: actual 1U/2U residual gauges, standard/Lunar parity, full-depletion and
  unrelated-reaction boundaries, first-VV ordering, and two matching payloads
  for each audited party
- Notes: adopt KQM Elemental Gauge Theory's Anemo/Geo 0.5 unit modifier, accessed
  2026-08-02:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/elemental-gauge-theory.
  Swirl propagation to other targets, absorption, formula order, shield
  absorption, and other reaction modifiers are excluded. See `TASKS.md`
  implementation block `Anemo and Geo Aura Consumption`.
  Completed with typed half-gauge consumption, residual preservation, actual
  standard/Lunar boundaries, and unchanged first-VV ordering. Accepted repeated
  payloads are unchanged RaidenParty
  `1565f197fe7813ef53bc7ee4107a6b68aae7337fdf1efb9e5d865502f2813d80`
  at 1,348,716 / 64,225, FlinsParty
  `4ad65138b5288f4c627194509fc24be69b8d21771efc09a66a1bd79dd92a2b96`
  at 22,620,467 / 227,341, and FlinsParty2
  `118edbd3665d167d31e9bbbbffc97ffc499a40db5199a573a5e25ed8eea023a6`
  at 15,482,126 / 224,054. All durations and warnings remain stable; FlinsParty2
  ER/allocation changes are recorded in the plan acceptance evidence.

### B-051 — Overload and Superconduct discard residual aura after subtraction

- Status: `done`
- Source: 3 (B-050 resolver audit plus maintained Elemental Gauge Theory)
- Symptom: the resolver subtracts the trigger's full gauge and then clears the
  aura unconditionally, so a 1U trigger against a fresh taxed 2U aura ends at
  zero instead of retaining 0.6U.
- Scope: remove redundant full-clear policy for Overload/Superconduct, preserve
  residual decay rate, focused strong-aura boundaries, and affected deterministic
  party baselines
- Risk: `validated`
- Proof: 1U trigger against 1.6U leaves 0.6U for both reaction directions,
  equal/weaker auras still fully clear, and repeated party payloads are
  deterministic and warning-free
- Notes: KQM Elemental Gauge Theory states these reactions use a 1x modifier and
  explicitly gives a 2U taxed Cryo aura followed by 1U Electro leaving 0.6U.
  Source accessed 2026-08-02:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/elemental-gauge-theory.
  B-050 is closed; see `TASKS.md` implementation block
  `Overload and Superconduct Residual Aura` for this separate residual-policy
  change.
  Completed by making typed reduction the only post-reaction aura mutation.
  Focused regressions cover both Overload/Superconduct directions, 1.6 to 0.6U,
  D(2) residual expiry, full depletion, reaction count, and subsequent Physical
  reduction. Accepted repeated payloads are RaidenParty
  `985f95d5c7779b81013dad0cf6232557b453ea44034c224f1b3ec1795a3b8614`
  at 1,363,709 / 64,939, unchanged FlinsParty
  `4ad65138b5288f4c627194509fc24be69b8d21771efc09a66a1bd79dd92a2b96`
  at 22,620,467 / 227,341, and unchanged FlinsParty2
  `118edbd3665d167d31e9bbbbffc97ffc499a40db5199a573a5e25ed8eea023a6`
  at 15,482,126 / 224,054. Durations, ER contracts, and warning-free output are
  stable.

### B-052 — Bloom consumes the same gauge in both trigger directions

- Status: `done`
- Source: 3 (maintained transformative-reaction divergence)
- Symptom: standard Bloom and Lunar-Bloom subtract the trigger's full source
  gauge in both directions, but Hydro is Bloom's weak element and the sourced
  Hydro:Dendro consumption ratio is 2:1.
- Scope: typed Hydro-on-Dendro 0.5x and Dendro-on-Hydro 2.0x aura consumption,
  standard/Lunar parity, focused core/Dew/ownership boundaries, and audited
  no-Dendro party controls
- Risk: `planned`
- Proof: actual 1U/2U residual and full-depletion cases in both directions,
  Lunar-Bloom side-effect parity, and unchanged repeated payloads for all three
  audited parties
- Notes: adopt the maintained KQM Transformative Reactions reference, accessed
  2026-08-02. It records a 2:1 Hydro:Dendro consumption ratio, identifies Hydro
  as the weak element, and states that application order affects gauge consumed
  rather than Bloom damage. Source:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/transformative-reactions.
  Adapt this to the repository's pre-tax trigger gauges as 0.5x
  Hydro-on-Dendro and 2.0x Dendro-on-Hydro. Quicken-as-Dendro coexistence,
  Burning internals, and other special state remain separate. See `TASKS.md`
  implementation block `Bloom Directional Aura Consumption`.
  Completed with one typed consumption policy shared by standard and
  Lunar-Bloom. Actual action regressions cover both directional residuals,
  exact/over-consumption, one reaction/core owner, no immediate damage, and
  Lunar Dew parity. Repeated no-Dendro controls retain RaidenParty
  `985f95d5c7779b81013dad0cf6232557b453ea44034c224f1b3ec1795a3b8614`
  at 1,363,709 / 64,939, FlinsParty
  `4ad65138b5288f4c627194509fc24be69b8d21771efc09a66a1bd79dd92a2b96`
  at 22,620,467 / 227,341, and FlinsParty2
  `118edbd3665d167d31e9bbbbffc97ffc499a40db5199a573a5e25ed8eea023a6`
  at 15,482,126 / 224,054 with stable ER, durations, and warning-free output.

### B-053 — Aubade uses the wrong 2-piece stat and misses initial off-field state

- Status: `done`
- Source: 2/3 (current party impact and maintained artifact data divergence)
- Symptom: Aubade of Morningstar and Moon adds 18% ATK instead of 80 Elemental
  Mastery, and its owner-only 4-piece buff is created only by switch callbacks,
  so an initially off-field wearer has no bonus until entering the field.
- Scope: corrected fixed stat, opt-in simulator artifact initialization,
  owner-only off-field/three-second state boundaries, focused snapshot/no-stack
  regressions, and both affected Flins party baselines
- Risk: `planned`
- Proof: exact constructor stats; initial active/off-field, 20%/60%, ally,
  2.999/3.0-second, reactivation, duplicate, and snapshot boundaries; repeated
  deterministic Raiden/Flins/FlinsParty2 payloads
- Notes: the maintained Genshin Impact Wiki set entry records 80 EM for 2 pieces,
  20% owner Lunar Reaction DMG while off-field, another 40% at Ascendant Gleam,
  and removal after three active seconds; its gameplay notes explicitly say the
  bonus is not a team bonus. Icy Veins independently records the same values.
  Sources accessed 2026-08-02:
  https://genshin-impact.fandom.com/wiki/Aubade_of_Morningstar_and_Moon and
  https://www.icy-veins.com/genshin-impact/artifacts/15043. Both Flins party
  optimizers consume the current wrong static block. Use an opt-in artifact
  initialization capability rather than a concrete-set conditional. See
  `TASKS.md` implementation block
  `Aubade Static Stats and Initial Off-Field State`.
  Completed with exact 80 EM constructors and an opt-in initialized-artifact
  capability. Public regressions cover initial active/off-field state, 20%/60%
  owner-only values, all Lunar types, exact three-second expiry, immediate
  switch-out reactivation, one typed buff, and snapshot continuity. Accepted
  repeated payloads are unchanged RaidenParty
  `985f95d5c7779b81013dad0cf6232557b453ea44034c224f1b3ec1795a3b8614`
  at 1,363,709 / 64,939, FlinsParty
  `1a514b75a60f384c56a577e84a82af3bce4ef652e5304132c184f01c94f2a81f`
  at 22,675,823 / 227,898, and FlinsParty2
  `3077dba03531db0d61f1de2f0d8ae7e8a38fa389edca87855d2860aa965a6c82`
  at 15,817,125 / 228,902 with stable durations, ER, and warning-free output.

### B-054 — Night-only Intent has no Gleaming Moon team synergy provider

- Status: `done`
- Source: 3 (maintained artifact wording plus artifact-provider audit)
- Symptom: Night of the Sky's Unveiling creates Intent but only Silken Moon's
  Serenade implements `ArtifactTeamBuffProvider`, so a party with Night and no
  Silken receives 0% instead of the specified 10% Lunar Reaction DMG bonus.
- Scope: shared canonical synergy policy, Night provider capability,
  Night-only/duplicate/mixed-set expiry regressions, and exact no-change party
  controls
- Risk: `validated`
- Proof: Night-only Intent gives 10% to all party members for all typed Lunar
  reaction bonuses; duplicate providers remain one; mixed Intent plus Devotion
  remains 20% and Silken-sourced; repeated catalog payloads remain exact
- Notes: the maintained KQM Nod-Krai guide records 15%/30% wearer CRIT Rate
  while Intent is active and a separate 10% party-wide Lunar Reaction DMG bonus;
  distinct Intent and Devotion effects stack to 20%, while duplicate effects do
  not. Source accessed 2026-08-02:
  https://keqingmains.com/misc/nod-krai-guide/. Preserve the existing first-
  Silken provider whenever Silken is equipped and use first-Night fallback only
  when it is absent. Thundercloud Strike's Intent trigger classification is a
  separate evidence question and is excluded. See `TASKS.md` implementation
  block `Night-Only Gleaming Moon Synergy`.
  Completed with one shared policy that retains first-Silken precedence and
  uses first-Night fallback only when no Silken exists. Focused regressions
  prove Night-only team-wide 10% across all Lunar types, exact Intent expiry,
  one provider under duplicate Night sets, mixed-set 20% and Silken sourcing,
  plus unchanged Silken EM and duplicate handling. Repeated semantic payloads
  match B-053 exactly: RaidenParty
  `dae58b38c4c64fba719885cfdf1facb0ac229187a712ab9a41ace16bd2dceed2`,
  FlinsParty
  `5d4cd7f4577704c541438bfa4b00525071d9b3183a77a2243fd0b47944d1dd18`,
  and FlinsParty2
  `d8732dfb6f34dcd80d910553525e622178eca55da249895562ac4570158337d5`.
  Values, ER, allocations, cadence, and warnings are unchanged; raw optimizer
  map key order is explicitly excluded from these semantic hashes.

### B-055 — Joint optimizer result key order changes between JVM processes

- Status: `done`
- Source: 2 (B-054 repeated sample output)
- Symptom: identical Flins allocations render `Result:` maps in different key
  orders across JVM executions, changing full payload hashes even though every
  stat value, DPS, ER, final total, and event is identical.
- Scope: insertion-ordered hill-climber result map, exact output/return
  regression, and fresh-JVM Flins sample acceptance
- Risk: `validated`
- Proof: focused three-stat output order plus matching raw normalized hashes from
  two no-daemon runs of each affected Flins sample
- Notes: B-053 versus B-054 differs only in six/five optimizer `Result:` lines
  for FlinsParty/FlinsParty2. Java `HashMap` does not guarantee iteration order;
  preserve the caller's typed optimization-stat order without changing search
  traversal, allocation values, or DPS. See `TASKS.md` implementation block
  `Deterministic Optimizer Result Rendering`.
  Completed by preserving insertion order in each hill-climber result and by
  adding an equal-DPS three-stat regression for returned and rendered order.
  Independent no-daemon runs now match raw normalized hashes without excluding
  result lines: FlinsParty
  `6338bcc75a29a52f3245cb4573823ba1245724d3be60064dcebefa7b38aa03ab`
  and FlinsParty2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
  Totals, ER, allocations, cadence, and warnings remain unchanged.

### B-056 — Standard Electro-Charged cannot tick at premature Aura expiry

- Status: `done`
- Source: 1/3 (README known simplification confirmed by maintained EGT evidence)
- Symptom: the EC event wakes only at one-second intervals, so an Aura that
  naturally expires 0.5-1.0 seconds after the prior tick produces no sourced
  premature terminal tick; sub-0.5 suppression is not explicitly modeled.
- Scope: read-only typed Aura expiry, standard EC wake policy, early/suppressed/
  extension regressions, and three audited party baselines
- Risk: `validated`
- Proof: exact finite expiry queries, one eligible early tick, one suppressed
  expiry, extension cancellation, unchanged nominal/Lunar behavior, and repeated
  deterministic party payloads
- Notes: adopt the maintained KQM Elemental Gauge Theory and independently
  recorded Evidence Vault experiments, accessed 2026-08-02. EGT specifies
  one-second ticks, premature damage when an Aura completely decays before the
  next interval except within 0.5 seconds of the previous tick, and 0.4U dual
  consumption per damage tick. The Evidence Vault gives concrete 0.8-second
  early-damage and 0.4-second no-damage cases. Sources:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/elemental-gauge-theory
  and
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions.
  Adapt only standard single-target no-hitlag timing. Ownership refresh, EC ICD,
  AoE, and Lunar-Charged are separate. See `TASKS.md` implementation block
  `Electro-Charged Premature Expiry Ticks`.
  Completed with a read-only Aura expiry API and separate standard/Lunar event
  policies. Focused regressions prove finite/infinite/absent/snapshot expiry,
  ordinary 0.4U consumption, a 0.7-second terminal tick, 0.4-second suppression,
  extension cancellation, live RES, and unchanged two-second Lunar cadence.
  Repeated accepted payloads are RaidenParty
  `d5dd65169069937a30bf8b8be0c32765dc26309e6132b58f29e9b28bb3cde7c3`
  at 1,365,787 / 65,037, unchanged FlinsParty
  `8271526ca511bcb8c49f2a3d15fc22114c2044124ed7bf2f61a2255fc9a45d67`,
  and unchanged FlinsParty2
  `b28a4a831f4e91ea687ac6c0f3df542fc06364e48514f1e3ef7460111257b27d`.
  Raiden's +2,078 is fully attributed to seven added delayed EC ticks, one
  removed immediate EC, and one removed Vaporize; ER, rolls, duration, and
  warnings remain stable.

### B-057 — Night Intent accepts Thundercloud Strike as a Lunar Reaction trigger

- Status: `blocked`
- Source: 3 (B-054 artifact trigger audit)
- Symptom: `NightOfTheSkysUnveiling` explicitly refreshes Intent for
  `THUNDERCLOUD_STRIKE` in addition to typed Lunar Reactions, but the set wording
  requires party members to trigger Lunar Reactions and the strike is modeled as
  a separate follow-up kind.
- Scope: Night trigger classification and exact four-second uptime regression
- Risk: `blocked`
- Proof: maintained in-game experiment or technical finding that explicitly
  states whether Thundercloud Strike does or does not activate/refresh Intent
- Notes: official-description mirrors, KQM's artifact catalog, and current
  guides repeat only the generic "trigger Lunar Reactions" wording. No maintained
  source found on 2026-08-02 resolves Thundercloud Strike specifically. Do not
  infer behavior from the repository enum name; leave current behavior unchanged
  until direct evidence is available.

### B-058 — Burning discards its Dendro fuel and uses a fixed lifetime

- Status: `done`
- Source: 1/3 (README known difference confirmed by maintained gauge references)
- Symptom: the resolver immediately subtracts the triggering source from the
  existing Aura, then the scheduler creates an infinite Pyro Aura and keeps
  Burning alive for exactly two seconds. This loses the underlying Dendro fuel,
  cannot derive duration from its gauge, and leaves a stale damage owner when
  Dendro or Pyro refreshes an active Burning state.
- Scope: typed single-target Burning fuel state, source-direction setup,
  0.25-second special Dendro consumption, refresh ownership/damage, snapshot
  payload, focused regressions, and unaffected catalog-party controls
- Risk: `planned`
- Proof: 1U/2U fuel durations, exact depletion, Dendro overwrite, Pyro refresh
  without fuel replacement, live-resistance ticks, snapshot payload continuity,
  and repeated deterministic party baselines
- Notes: maintained KQM documents 0.25-second Burning damage, a separate 1U
  Pyro application with two-second ICD, and a state requiring both Burning and
  Dendro Aura. The current Genshin Impact Wiki gauge reference specifies that
  Burning replaces natural Dendro decay with
  `max(0.4 U/s, 2 * natural decay rate)` and that Dendro reapplication
  overwrites the fuel. The independently maintained gcsim implementation uses
  a distinct non-decaying Burning Aura and Burning-fuel durability with the
  same minimum/s doubled-natural rate and refresh ownership. Sources accessed
  2026-08-02:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/transformative-reactions,
  https://genshin-impact.fandom.com/wiki/Elemental_Gauge_Theory/Advanced_Mechanics,
  and https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/burning.go.
  This pass models uninterrupted single-target fuel and refresh behavior.
  Burning Aura consumption by Hydro/Cryo/Electro/Anemo/Geo, the separate Pyro
  application ICD, Quicken fuel, AoE, and hitlag remain out of scope. See
  `TASKS.md` implementation block `Burning Fuel and Refresh State`.
  Completed with an immutable simulator-owned fuel/damage payload, exact
  `max(0.4 U/s, 2 * natural rate)` depletion, source-direction Aura setup,
  Dendro overwrite, latest-applier ownership, live-resistance ticks, and stale
  event generations. Focused regression proves 8 ticks/2 seconds for 1U and 16
  ticks/4 seconds for 2U, exact no-late-tick depletion, both source directions,
  weaker Dendro replacement, Pyro owner-only refresh, snapshot continuity, and
  one tick under competing generations. Six catalog control runs are
  warning-free and pairwise exact at 1,365,787 / 65,037, 22,675,823 / 227,898,
  and 15,817,125 / 228,902 with no Burning events.

### B-059 — Quicken is an expiry timestamp instead of a consumable Aura

- Status: `done`
- Source: 1/3 (README Dendro difference plus maintained Quicken gauge references)
- Symptom: Quicken stores only an end timestamp, every retrigger extends it even
  when the new gauge is weaker, and Hydro cannot trigger Bloom on a Quicken-only
  target or consume Quicken alongside underlying Dendro.
- Scope: typed Quicken gauge/decay state, stronger-only refresh, snapshot
  payload, Hydro Bloom consumption with/without underlying Dendro, additive
  non-consumption regressions, and deterministic catalog-party controls
- Risk: `planned`
- Proof: exact 0.8U duration/decay, weaker no-op and stronger replacement,
  Quicken-only Bloom, combined Dendro+Quicken one-core dual consumption,
  Spread/Aggravate non-consumption, expiry/snapshot boundaries, and unchanged
  party baselines
- Notes: maintained KQM specifies that Quicken acts as Dendro when reacting with
  Hydro/Pyro, coexists with Dendro/Electro/Cryo, and uses
  `min(Dendro, Electro) * 5 + 6` duration. The maintained advanced gauge
  reference additionally specifies that weaker retriggers do not change the
  Aura, stronger retriggers replace gauge/rate, and Hydro/Pyro consume it while
  Spread/Aggravate do not. Its simultaneous-priority reference states that
  Hydro Bloom consumes coexisting Quicken and Dendro simultaneously. gcsim
  independently implements stronger-only Quicken attachment and one Bloom core
  while reducing both gauges. Sources accessed 2026-08-02:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/additive-reactions,
  https://genshin-impact.fandom.com/wiki/Elemental_Gauge_Theory/Advanced_Mechanics,
  https://genshin-impact.fandom.com/wiki/Elemental_Gauge_Theory/Simultaneous_Reaction_Priority,
  and https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/catalyze.go.
  Pyro consumption into Burning fuel is deferred to a separate reaction-priority
  item; this pass implements Quicken lifecycle and Hydro Bloom.
  Completed with immutable units/rate/update state, stronger-or-equal refresh,
  typed consumption, snapshot continuity, and resolver-owned Quicken-only and
  coexisting-Dendro Hydro Bloom. Regression proves 0.8U/10s in both trigger
  directions, weaker/stronger real retriggers, additive non-consumption, one
  standard/Lunar core with exact gauge/dew ownership, dual 0.5U consumption,
  and exact-expiry suppression. Six catalog controls retain B-058 hashes,
  values, ER, and cadence with no Quicken/Bloom or warning lines.

### B-060 — Pyro cannot consume Quicken into Burning fuel

- Status: `done`
- Source: 3 (B-058/B-059 excluded boundary with maintained priority evidence)
- Symptom: a Quicken-only target is invisible to the ordinary Aura resolver, so
  Pyro applies without Burning; when Dendro and Quicken coexist, B-058 consumes
  only Dendro and leaves Quicken untouched.
- Scope: Quicken-only Pyro Burning, shared Dendro/Quicken special fuel decay,
  exact depletion/ownership/live-RES regressions, and catalog controls
- Risk: `validated`
- Proof: Quicken-only Burning ticks and gauge depletion, coexisting gauge decay,
  Dendro refresh continuity, exact no-late-tick end, and unchanged party hashes
- Notes: KQM states Quicken acts as Dendro when reacted with Pyro. The maintained
  simultaneous-priority reference states that Pyro on Quicken+Dendro triggers
  Burning and consumes both simultaneously. The advanced gauge reference gives
  Quicken's current decay and Burning's minimum/doubled-natural special decay;
  gcsim independently applies the Burning-fuel rate to both Quicken and Dendro.
  Sources accessed 2026-08-02 are the B-059 URLs plus
  https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/reactable.go.
  This pass handles Quicken-only and Dendro+Quicken. Electro/Cryo coexisting
  reaction priority, a separately consumable Burning Aura, Pyro application,
  AoE, and hitlag remain separate.
  Completed with Quicken-only resolver routing, max-current shared fuel,
  per-gauge replacement of natural decay, and exact epsilon cleanup. Regression
  proves eight ticks over two seconds for 0.8U Quicken, one equal-coexistence
  stream, smaller-gauge early depletion, Dendro overwrite, ordinary Pyro after
  exact Quicken expiry, and retained owner/RES/generation contracts. Six catalog
  controls exactly retain B-059 hashes, values, ER, cadence, and zero affected
  reaction or warning lines.

### B-061 — Freeze Aura never decays or survives snapshot restore

- Status: `done`
- Source: 1/3 (README known difference, KQM gauge evidence, and gcsim reference)
- Symptom: `Enemy` stores one non-time-aware `freezeAuraUnits` value, so a target
  remains Frozen forever unless Shattered; snapshot save/restore omits Freeze
  entirely, and repeated Freeze replaces instead of extending its gauge.
- Scope: typed accelerating Freeze gauge, natural expiry, refreeze extension,
  Shatter consumption, snapshot continuity, and deterministic catalog controls
- Risk: `validated`
- Proof: exact 1U-source gauge/duration, midpoint nonlinear decay, exact expiry,
  active extension without decay reset, thawing-rate recovery, Shatter clear,
  snapshot restore, and unchanged non-Freeze party hashes
- Notes: KQM defines the initial Frozen Aura as twice the smaller current origin
  and trigger gauge, with duration `2 * sqrt(5 * frozen gauge + 4) - 4`; it also
  states that underlying Hydro/Cryo continues natural decay and matching
  reapplication extends Freeze. This is equivalent to
  `F(t) = F0 - 0.4t - 0.05t^2`. gcsim independently starts at twice the smaller
  gauge, adds refreeze durability without resetting the current decay rate,
  accelerates active decay by 0.1U/s^2, and recovers the inactive rate by
  0.2U/s^2 toward 0.4U/s. Sources accessed 2026-08-02:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/elemental-gauge-theory,
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#duration-of-freeze-aura,
  and https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/freeze.go.
  This pass preserves the existing single-target resolver and implements finite
  Freeze lifecycle only. Dual underlying Aura reaction priority, trigger
  residual attachment, Freeze resistance, hitlag, poise damage, and Shatter
  damage ICD remain separate.
  Completed with immutable gauge/rate/update state, exact nonlinear expiry,
  active extension, inactive rate recovery, time-aware Shatter/resonance, and
  full snapshot continuity. Regression covers both source directions, midpoint,
  exact expiry, extension, reduction, recovery, invalid input, Shatter, and
  resonance. Six catalog controls are pairwise exact with unchanged values, ER,
  cadence, and zero Freeze/Shatter/warning lines. B-060 hash differences in
  Raiden/Flins are line-order-only and exposed B-062.

### B-062 — Simultaneous Aura reaction order depends on HashSet iteration

- Status: `done`
- Source: 3 (B-061 control diff plus maintained gcsim reaction order)
- Symptom: `Enemy.getActiveAuras` returns a `HashSet`, so adding unrelated fields
  changed Pyro-on-Hydro+Electro and Anemo-on-Hydro+Electro log order between
  builds. B-060 itself used different order for FlinsParty and FlinsParty2.
- Scope: trigger-specific deterministic single-target Aura reaction priority,
  dual-Aura order regressions, and catalog baseline re-acceptance
- Risk: `validated`
- Proof: explicit order for every supported trigger, stable logs across fresh JVM
  processes, unchanged reaction values, and documented unresolved residual rules
- Notes: gcsim's maintained `React` dispatcher orders Electro, Pyro, Cryo,
  Hydro, Anemo, Geo, and Dendro reaction attempts explicitly rather than
  iterating target storage. Its current order places Overload before Vaporize
  for Pyro and Electro Swirl before Hydro Swirl for Anemo, matching the B-061
  post-layout logs. Source accessed 2026-08-02:
  https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/reactable.go.
  Do not infer trigger residual attachment or Frozen/Burning synthetic Aura
  priority in this item; order only already-supported ordinary Aura reactions.
  Completed with a pure typed `ReactionPriority` policy and resolver routing
  independent of target storage. Regression covers all seven elemental trigger
  lists, stable Physical/same-element fallback, Pyro Overload-before-Vaporize,
  and Anemo Electro-before-Hydro Swirl. Six fresh-JVM controls are pairwise
  exact, retain every B-061 value/ER/count, and contain zero warning lines.

### B-063 — Pyro Vaporizes hidden Hydro instead of Melting Frozen Aura

- Status: `done`
- Source: 1/3 (README Frozen priority exclusion plus KQM/gcsim evidence)
- Symptom: the ordinary resolver cannot see typed Frozen Aura, so non-blunt Pyro
  iterates a stronger Freeze origin's hidden Hydro and triggers Vaporize while
  leaving Freeze active.
- Scope: non-blunt Pyro Melt against typed Frozen, simultaneous underlying Cryo
  consumption, hidden Hydro preservation, blunt Shatter order, exact expiry,
  and catalog controls
- Risk: `validated`
- Proof: Melt-only hidden-Hydro case, dual Frozen/Cryo reduction, partial Frozen
  survival, Shatter-before-Vaporize blunt case, expiry fallback, and unchanged
  no-Freeze party baselines
- Notes: KQM states Frozen behaves as Cryo, hidden Hydro/Cryo continues decaying,
  Pyro on Frozen with underlying Hydro triggers only Melt and leaves Hydro, and
  heavy attacks Shatter before elemental reaction. gcsim independently reduces
  both Frozen and ordinary Cryo with Pyro's 2x Melt modifier. Sources accessed
  2026-08-02:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/elemental-gauge-theory,
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#freeze-aura-mechanics,
  and https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/melt.go.
  This pass implements Pyro/Frozen only. Electro/Anemo/Geo interactions,
  additional coexisting ordinary elements, trigger residuals, Freeze resistance,
  Shatter ICD, hitlag, poise, and multi-target behavior remain separate.
  Implemented with one resolver-local typed-Frozen branch. Focused regressions
  cover Melt-only hidden Hydro, dual Frozen/Cryo consumption, partial survival,
  Shatter-before-Vaporize, and exact expiry. Six fresh catalog controls exactly
  retain the B-062 hashes, values, ER/cadence counts, and zero-warning contract.

### B-064 — Overload damage ignores target and owner damage sequences

- Status: `done`
- Source: 1/3 (README known difference plus KQM/gcsim evidence)
- Symptom: every notified Overload records transformative damage, so rapid
  reactions from one or multiple owners overcount damage instead of retaining
  the reaction/gauge effects while a reaction-damage limit is active.
- Scope: single-target 0.1-second global GCD, per-`CharacterId` 0.5-second
  damage sequence, snapshot continuity, focused regressions, and catalog controls
- Risk: `validated`
- Proof: first/pre-boundary/exact-boundary damage, cross-owner independence after
  the global limit, blocked notification/gauge continuity, snapshot replay, and
  repeated deterministic party payloads
- Notes: KQM's "Overload Reaction ICD" finding (added/tested in v1.5) records a
  0.5-second damage immunity for Overload from the same character while gauge
  reduction and stagger still occur. Maintained gcsim independently combines a
  target-wide 0.1-second `overloadGCD` with `ICDGroupReactionB`, whose damage
  sequence permits one hit per character every 0.5 seconds. Adopt both limits
  in the repository's one-enemy abstraction. Sources accessed 2026-08-02:
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#overload-reaction-icd,
  https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/overload.go,
  https://github.com/genshinsim/gcsim/blob/main/pkg/core/attacks/icd_groups.dm.go,
  and https://github.com/genshinsim/gcsim/blob/main/pkg/target/icd.go.
  Completed with snapshot-safe target/owner cooldown state and resolver-local
  damage gating after notification/consumption. Focused tests cover both
  pre-boundaries, exact boundaries, cross-owner behavior, six unchanged
  notifications/consumptions, restore replay, and Superconduct isolation.
  Raiden suppresses one 13,412-damage Xiangling Overload and accepts
  1,352,375/64,399; both Flins samples remain byte-identical to B-063.

### B-065 — Remaining Frozen dual-Aura reactions need trigger-residual policy

- Status: `blocked`
- Source: 1/3 (README known difference plus KQM/gcsim audit)
- Symptom: Electro and Anemo cannot react with typed Frozen, while Geo always
  clears it through Shatter before ordinary Aura resolution.
- Scope: Electro/Frozen, Anemo/Frozen, Geo/Shatter residual, hidden-Aura priority,
  and trigger-gauge carry between multiple reactions
- Risk: `blocked`
- Proof: sourced directional gauge matrices for Frozen-only and hidden
  Hydro/Cryo cases plus focused reaction-order/consumption regressions
- Notes: the KQM Evidence Vault marks its original Freeze table inaccurate and
  records gauge-dependent exceptions for Electro, Anemo, Geo, and heavy hits.
  Maintained gcsim models these with residual trigger durability and separate
  reaction attempts, but this simulator intentionally lacks trigger carry and
  poise-aware Shatter durability. A narrow change would encode known-wrong dual
  reactions. Keep blocked until a trigger-residual plan or explicit
  simplification decision is authorized.

### B-066 — Standard Crystallize has no one-second global cooldown

- Status: `done`
- Source: 3 (sourced reaction-mechanic divergence)
- Symptom: repeated Geo hits and one Geo hit against multiple ordinary Auras
  notify and consume every Crystallize, producing extra reactions/shard effects
  and excessive Aura consumption within one second.
- Scope: standard-only one-second single-target GCD, shared owner/element policy,
  no-consumption suppression, snapshot continuity, and catalog controls
- Risk: `validated`
- Proof: same-hit dual Aura winner, pre/exact time boundary, cross-owner/element
  sharing, blocked no-consumption, snapshot replay, Lunar bypass, and repeated
  party payloads
- Notes: KQM's Crystallize ICD finding (v2.7) records a one-second GCD per
  monster shared across attacks, owners, and Aura elements. Its v2.8 correction
  proves the GCD also prevents gauge consumption. Maintained gcsim independently
  checks one target `crystallizeGCD` before standard event/reduction and sets it
  for 60 frames. `TryAddLCr` is a separate Lunar path without this standard GCD.
  Adopt the standard-only rule in the one-enemy abstraction. Sources accessed
  2026-08-02:
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#crystallize-icd,
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#crystallize-icd-correction,
  https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/crystallize.go,
  and https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/lunarcrystallize.go.
  Completed with one snapshot-safe standard-only boundary checked after Lunar
  conversion and before notification or Aura handling. Focused regression
  covers same-hit Electro/Hydro priority, shared owner/element suppression at
  0.999 seconds, exact 1.000-second acceptance, no blocked consumption/refresh,
  restore replay, and uninterrupted three-trigger Lunar Harmony cadence. Six
  fresh controls exactly retain B-064 hashes `03301ef5a3d650a91cfa07660cb0077d8c1585da04d79803a97f36e8249ba85a`,
  `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`,
  and `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`
  with unchanged values, event counts, and zero warning matches.

### B-067 — Superconduct damage ignores its reaction damage sequence

- Status: `done`
- Source: 3 (KQM/gcsim reaction-damage divergence)
- Symptom: every rapid Superconduct notification records damage, exceeding the
  sourced two damage hits per owner in 0.5 seconds and target-wide 0.1-second GCD.
- Scope: target/owner hit-window state, continued reaction/gauge/shred effects,
  snapshot continuity, and catalog controls
- Risk: `validated`
- Proof: global pre/exact boundary, first/second/third owner hit sequence,
  cross-owner behavior, unaffected gauge/shred, restore replay, and repeated
  party payloads
- Notes: KQM's v2.5 Superconduct update and maintained gcsim `superconductGCD`
  plus `ICDGroupReactionA` provide matching target/owner dimensions. Promote
  only after B-066 closes; do not fold it into Crystallize's whole-reaction GCD.
  KQM specifies at most two damage instances from one character in a 0.5-second
  interval while later reactions still reduce gauge and stagger. Maintained
  gcsim emits `OnSuperconduct` before its target-wide 0.1-second attack GCD;
  target-passing attacks then use owner-specific `ICDGroupReactionA`, whose
  fixed 0.5-second counter accepts entries one and two and suppresses later
  entries until the timer started by entry one resets. Sources accessed
  2026-08-02:
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#superconduct-mechanic-update,
  https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/superconduct.go,
  https://github.com/genshinsim/gcsim/blob/main/pkg/core/attacks/icd_groups.dm.go,
  and https://github.com/genshinsim/gcsim/blob/main/pkg/target/icd.go.
  Completed with snapshot-safe target and immutable owner sequence state.
  Notification, Aura consumption, and physical shred refresh precede damage-only
  gating. Focused regression covers 0.05/0.10-second target timing, owner entries
  one/two/three, an independent owner, owner-blocked target-GCD advancement,
  exact 0.5-second reset, seven unchanged reactions/consumptions, restore replay,
  and blocked-hit shred refresh. Six fresh catalog controls exactly retain
  B-066 hashes `03301ef5a3d650a91cfa07660cb0077d8c1585da04d79803a97f36e8249ba85a`,
  `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`,
  and `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`
  with unchanged values/counts and zero Superconduct or warning matches.

### B-068 — Shatter damage ignores target and owner damage sequences

- Status: `done`
- Source: 3 (B-063 out-of-scope accuracy gap plus KQM/gcsim evidence)
- Symptom: every rapid Shatter notification records damage, ignoring both the
  target attack GCD and fixed two-hit owner damage sequence.
- Scope: target/owner damage-only state, continued notification/Freeze clear,
  snapshot continuity, and catalog controls
- Risk: `validated`
- Proof: target pre/exact boundary, owner first/second/third/reset sequence,
  cross-owner state, Freeze clear on blocked damage, restore replay, and
  repeated party payloads
- Notes: KQM's v1.5 Shatter Damage ICD finding records at most two Shatter
  damage instances in 0.5 seconds. Maintained gcsim independently emits
  `OnShatter` and reduces Frozen before a target-wide 0.2-second `shatterGCD`,
  then routes target-passing damage through owner-specific
  `ICDGroupReactionA`, whose fixed 0.5-second sequence accepts entries one and
  two. Adopt those dimensions in the one-enemy abstraction without changing
  the repository's current whole-Freeze clear simplification. Sources accessed
  2026-08-02:
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#shatter-damage-icd,
  https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/freeze.go,
  https://github.com/genshinsim/gcsim/blob/main/pkg/core/attacks/icd_groups.dm.go,
  and https://github.com/genshinsim/gcsim/blob/main/pkg/target/icd.go.
  Completed by generalizing B-067's immutable fixed owner payload while keeping
  separate target clocks/maps. Notification and current whole-Freeze clear now
  precede Shatter damage-only gating. Focused regression covers 0.1/0.2-second
  target timing, owner entries one/two/three, independent owner state,
  owner-blocked target-GCD advancement, six notifications with blocked Freeze
  clears, active-target restore, and exact 0.5-second owner reset. Six fresh
  catalog controls exactly retain B-067 hashes
  `03301ef5a3d650a91cfa07660cb0077d8c1585da04d79803a97f36e8249ba85a`,
  `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`,
  and `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`
  with unchanged values/counts and zero Shatter or warning matches.

### B-069 — Active standard Electro-Charged refreshes retain stale tick ownership

- Status: `done`
- Source: 3 (README simplification audit plus KQM/gcsim evidence)
- Symptom: every standard Electro-Charged reapplication deals another immediate
  damage instance, while its active timer closure retains the first trigger's
  pre-resistance damage and reports every periodic tick as `Thundercloud`.
- Scope: standard refresh damage policy, latest owner/pre-resistance payload,
  typed tick attribution, snapshot continuity, and catalog controls
- Risk: `planned`
- Proof: low/high-EM owner refresh, repeated refresh suppression, continued
  notification/Aura effects, payload restore, B-056 timing, Lunar no-change,
  and repeated catalog payloads
- Notes: KQM records the first Electro-Charged tick under the reaction trigger
  and later ticks under the latest character to apply an elemental source before
  the tick. Its EM snapshot evidence separately shows that reapplication updates
  subsequent damage. Maintained gcsim similarly updates the active attack
  snapshot on every reaction while only a newly created sequence queues the
  immediate tick and timer. Adopt this in the one-enemy standard path without
  claiming multi-target spread/ICD behavior and without changing
  Lunar-Charged. Live impact resistance remains B-048 policy. Sources accessed
  2026-08-02:
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#what-determines-whether-electro-charged-damage-is-calculated-using-the-electro-or-hydro-user,
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#electro-charged-snapshots-em-until-reapplying,
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#electro-charged-icd,
  and https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/electrocharged.go.
  Completed with immutable latest-owner/pre-resistance state, typed periodic
  attribution, and snapshot forwarding. Focused regression covers low/high-EM
  owner replacement, active refreshes at 0.2/0.6 seconds, continued notification
  and Aura application, owner-specific damage, live RES, B-056 timing, Lunar
  no-change, and payload restore. Raiden's 13 formerly immediate active refreshes
  are now deferred while all 11 periodic ticks remain and move from
  `Thundercloud` to their typed owner. Its accepted result is 1,307,990 damage /
  62,285 DPS with normalized hash
  `dc46bf544a8c07c2db8177bf1f9f4b8114bd7bd6e4f29fdd35230823694b2ac0`.
  Flins/Flins2 retain B-068 hashes, totals, and event counts exactly.

### B-070 — Standard Electro-Charged damage cooldown resets between sequences

- Status: `done`
- Source: 3 (B-069 excluded boundary plus KQM/gcsim evidence)
- Symptom: B-056 may finish one standard Electro-Charged timer less than 0.5
  seconds after its last damage, after which a newly triggered sequence records
  immediate damage without retaining the target's prior cooldown.
- Scope: one-enemy standard EC damage cooldown, successful-damage timestamp,
  no-consumption blocked ticks, snapshot continuity, and catalog controls
- Risk: `planned`
- Proof: cross-sequence pre/exact boundary, blocked new-sequence side effects,
  premature tick threshold, no blocked gauge consumption, restore replay,
  B-069 ownership, Lunar no-change, and repeated catalog payloads
- Notes: KQM v2.3 records one Electro-Charged damage instance per enemy in about
  0.5 seconds, including primary and AoE ticks, and separately establishes that
  blocked ticks consume no Hydro/Electro gauge. Maintained gcsim tags both the
  new-sequence attack and later ticks with `ICDTagECDamage` and the fixed
  0.5-second `ICDGroupReactionB`; only successful damage schedules gauge wane.
  Adopt the target dimension in the one-enemy standard path without claiming
  adjacent-target synchronization or changing Lunar-Charged. Sources accessed
  2026-08-02:
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#electro-charged-icd,
  https://library.keqingmains.com/evidence/combat-mechanics/elemental-effects/transformative-reactions#ec-ticks-only-consume-gauge-when-they-deal-damage,
  https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/electrocharged.go,
  and https://github.com/genshinsim/gcsim/blob/main/pkg/core/attacks/icd_groups.dm.go.
  Completed with snapshot-safe cooldown/last-success timing shared by immediate,
  nominal, and premature standard ticks. Focused regression covers cross-sequence
  pre/exact timing, retained notification/Aura/owner/timer effects, blocked
  nominal no-consumption, restart-relative tick timing, and snapshot replay.
  Raiden has one Xingqiu-owned new-sequence immediate hit blocked less than 0.5
  seconds after the preceding periodic tick, reducing 1,307,990/62,285 to
  1,304,576/62,123 with normalized hash
  `86a70a9357148363fcc465e648accb749cc774a3a3adf8c0aac35a583c37e601`.
  Event counts and ER remain exact; Flins/Flins2 retain B-069 hashes and totals.

### B-071 — Swirl damage ignores per-element target and owner sequences

- Status: `done`
- Source: 3 (remaining reaction-sequence audit plus KQM/gcsim evidence)
- Symptom: every rapid Swirl reaction records damage, without the target-wide
  0.1-second per-element GCD or owner/element two-hit 0.5-second sequence.
- Scope: single-target per-element damage-only state, owner/element fixed
  sequences, continued notification/Aura consumption, snapshots, and controls
- Risk: `planned`
- Proof: target pre/exact boundary, owner first/second/third/reset, cross-owner
  and cross-element independence, continued side effects, restore replay, and
  repeated catalog payloads
- Notes: KQM records at most two Swirl damage instances per Element on one enemy
  in 0.5 seconds. Maintained gcsim emits the typed Swirl event and consumes Aura
  before a separate 0.1-second GCD for each Swirled Element, then queues damage
  with element-specific ICD tags and owner-specific `ICDGroupReactionA`. Adopt
  both damage-only dimensions in the one-enemy model. Do not add adjacent-target
  AoE, element spread, chain reactions, or alter valid double Swirl across
  distinct elements. Sources accessed 2026-08-02:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/transformative-reactions#swirl,
  https://library.keqingmains.com/combat-mechanics/internal-cooldown,
  https://github.com/genshinsim/gcsim/blob/main/pkg/reactable/swirl.go,
  https://github.com/genshinsim/gcsim/blob/main/pkg/core/attacks/icd_groups.dm.go,
  and https://github.com/genshinsim/gcsim/blob/main/pkg/target/icd.go.
  Completed with snapshot-safe typed target and owner state plus actual-action
  timing, side-effect, independence, and restore regressions. Repeated catalog
  payloads match at Raiden
  `86a70a9357148363fcc465e648accb749cc774a3a3adf8c0aac35a583c37e601`,
  FlinsParty
  `2d530f72e3cf4d0d6ee6209ef68dff6cf1454707fd3b5e43fb21e249a682ed68`,
  and FlinsParty2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
  Raiden and FlinsParty2 remain exact to B-070. FlinsParty retains all 58
  Swirl notifications but suppresses five damage instances, reducing only
  Sucrose and the party total by 36,413 to 22,639,410 / 227,532; its 172
  immediate Lunar reactions, 48 ticks, optimizer allocation, and ER remain
  unchanged.

### B-072 — Dendro Core damage-cap history survives snapshot rollback

- Status: `done`
- Source: 4 (snapshot invariant and regression-coverage audit)
- Symptom: active Dendro Core payloads are snapshotted, but the two-hit/0.5-second
  damage-cap history remains mutable only inside `ReactionEffectScheduler`.
  Hits executed after a save therefore remain in the scheduler after restore
  and can suppress valid Bloom, Hyperbloom, or Burgeon damage on the replayed
  branch.
- Scope: reaction-owned core damage history, defensive snapshot round trip,
  focused branch-replay regression, and deterministic catalog controls
- Risk: `planned`
- Proof: save after one accepted core hit, mutate the future branch, restore,
  accept the replayed second hit, reject only the replayed third hit, accept at
  the exact 0.5-second boundary, and repeat catalog payloads
- Notes: this is a simulator rollback correction, not a change to the accepted
  two-hit/0.5-second game-mechanic policy. See `TASKS.md` implementation block
  `Dendro Core Damage-Cap Snapshot State`. Completed by moving policy state to
  `ReactionState`, validating defensive copy/restore, and replaying real core
  consumption after a future branch mutation. Repeated catalog payloads remain
  exact at Raiden
  `86a70a9357148363fcc465e648accb749cc774a3a3adf8c0aac35a583c37e601`,
  FlinsParty
  `2d530f72e3cf4d0d6ee6209ef68dff6cf1454707fd3b5e43fb21e249a682ed68`,
  and FlinsParty2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`;
  all six logs contain zero Core/Bloom-family and warning matches.

## Autonomous Discovery Convergence - 2026-08-02

- Focus: simulator correctness only. RL training, rollout benchmarks, NCCL/DDP,
  tensor/protocol changes, and persistent jobs remain excluded by the newest
  user instruction.
- Source 1: remaining README differences map to settled B-004/B-042/B-057/B-065
  or the deferred multi-target, positioning, defensive, and full Burning-Aura
  boundaries. None may be reopened autonomously.
- Source 2: `ReactionRegressionTest`, `PartyCatalogRegressionTest`,
  `ReportRegressionTest`, and all six B-072 catalog controls pass. Catalog logs
  contain no warning, error, failed-action, insufficient-energy, or implausible
  total, and generated report output remains untracked/restored.
- Source 3: the single-target reaction sequence/state audit through B-071 found
  no additional sourced divergence inside the current authority boundary.
- Source 4: the snapshot audit found B-072, which is complete with focused
  branch replay. Remaining comment/Javadoc markers either describe deliberate
  approximations, settled items, or have no observable defect and therefore do
  not pass the value gate.
- Source 5: not swept because RL is explicitly excluded.
- Result: no unblocked simulator candidate passes both value and risk gates.
  The autonomous queue is exhausted; do not manufacture further work without
  new evidence or user scope.

### B-073 — Favonius weapon family content coverage

- **Status:** done
- **Scope:** simulator content; RL and generated docs excluded
- **Value/risk:** high-value local expansion using the existing Codex hook;
  shared extraction is planned before four independent weapon slices
- **Evidence boundary:** canonical Lv. 90 stats and R1-R5 Windfall values must be
  recorded; no unsupported enemy or incoming-damage behavior is required
- **Plan:** `TASKS.md` Favonius Weapon Family Content Campaign
- **Checkpoint 1:** shared Windfall, Codex compatibility, Sword, Greatsword, and
  Lance were verified and pushed; Warbow remained ready
- **Completion:** `9d6d1e5` adds Warbow; all family variants pass metadata,
  inherited Windfall, reaction regression, build, Javadoc, and leak gates

### B-074 — Sacrificial weapon family content coverage

- **Status:** done
- **Scope:** simulator weapon content; RL and generated docs excluded
- **Value/risk:** high-value planned extraction of proven Sword behavior followed
  by three local content variants
- **Evidence boundary:** Lv. 90 stats and R1-R5 Composed chance/cooldowns are
  sourced; unsupported multi-target and non-damaging behavior remains explicit
- **Plan:** `TASKS.md` Sacrificial Weapon Family Content Campaign
- **Completion:** `ba6a19d`, `0773001`, `ecda078`, and `496a79b` provide shared
  R1-R5 Composed and all family variants with focused/build/Javadoc gates

### B-075 — Target-Aura weapon content coverage

- **Status:** done
- **Scope:** simulator weapon content; live target Aura only
- **Value/risk:** planned shared extraction of proven Dragon's Bane behavior,
  followed by three independently reversible variants
- **Evidence boundary:** Lv. 90 metadata, eligible elements, and R1-R5 bonuses
  are sourced; no formula, Aura, RL, or multi-target changes
- **Plan:** `TASKS.md` Target-Aura Weapon Content Campaign
- **Completion:** `10b9db9`, `4c940ef`, `814f2b0`, and `96f48c7` add shared
  live-Aura evaluation and three sourced variants with focused/build/Javadoc gates

### B-076 — Kaeya character and constellation coverage

- **Status:** done
- **Scope:** one-enemy simulator character vertical slice; RL excluded
- **Value/risk:** planned content addition using existing typed actions, periodic
  event, Frozen state, snapshot, and per-action bonus contracts
- **Evidence boundary:** KQM TCL Lv. 90/talent/gauge/ICD/particle/constellation
  data; C2/C4, healing, movement, and multi-target behavior remain unsupported
- **Plan:** `TASKS.md` Kaeya Character Vertical Slice
- **Completion:** `f891f32` adds the sourced one-enemy vertical slice with exact
  C0/C6 Burst counts; reaction regression, build, Javadoc, and leak gates pass

### B-077 — Remaining 3-star Bane weapon coverage

- **Status:** done
- **Scope:** Cool Steel, Bloodtainted Greatsword, and Raven Bow; RL excluded
- **Value/risk:** three local content additions reuse the verified impact-time
  Aura base with no shared formula or mutable-state change
- **Evidence boundary:** KQM TCL Lv. 90 metadata, eligible Aura pairs, and R1-R5
  values; Black Tassel remains excluded because enemy type is not modeled
- **Plan:** `TASKS.md` Remaining 3-Star Bane Weapon Campaign
- **Completion:** `45eae33`, `53abaaf`, and `9749303` add all three supported
  variants with reaction regression, build, Javadoc, and leak gates passing

### B-078 — Static action-bonus weapon coverage

- **Status:** done
- **Scope:** The Stringless, Rust, and White Tassel; RL excluded
- **Value/risk:** exact static mappings onto existing action damage stats with
  no event, formula, or target-state changes
- **Evidence boundary:** KQM TCL Lv. 90 metadata and R1-R5 passive values;
  projectile travel and weak-point weapons remain outside this batch
- **Plan:** `TASKS.md` Static Action-Bonus Weapon Campaign
- **Completion:** `e65e673`, `31fd61e`, and `b814dd2` add all three weapons with
  action isolation, refinement boundaries, build, Javadoc, and leak gates passing

### B-079 — Amber character and constellation coverage

- **Status:** done
- **Scope:** stationary one-enemy Amber vertical slice; RL excluded
- **Value/risk:** high-value starter character using existing delayed event,
  snapshot, charge, typed action, ICD, particle, and team-buff contracts
- **Evidence boundary:** KQM TCL stats/talents/frames/gauge/ICD/particles and
  constellations; weak points, C2, summon HP/taunt, and random placement excluded
- **Plan:** `TASKS.md` Amber Character Vertical Slice
- **Completion:** `90f7f8c` adds the complete allowed vertical slice with exact
  delayed/fixed-count events; reaction regression, build, Javadoc, and leaks pass

### B-080 — Legacy weapon refinement coverage

- **Status:** done
- **Scope:** Alley Flash, Deathmatch, and The Catch R1-R5; RL excluded
- **Value/risk:** closes fixed-R1/R5 content gaps with local constructor/passive
  changes while preserving all no-argument callers
- **Evidence boundary:** KQM TCL Lv. 90 metadata/refinement values; incoming
  damage and dynamic enemy-count transitions remain outside simulator state
- **Plan:** `TASKS.md` Legacy Weapon Refinement Campaign
- **Completion:** `e587803`, `ca085a2`, and `d29055f` add R1-R5 while preserving
  defaults; focused/build/Javadoc/leak gates pass

### B-081 — Skill-focused event weapon coverage

- **Status:** done
- **Scope:** Oathsworn Eye, Windblume Ode, and Festering Desire; RL excluded
- **Value/risk:** three complete local weapon additions using existing typed
  Skill dispatch and action-specific stats
- **Evidence boundary:** KQM TCL metadata, refinement values, non-stacking
  refresh, and expiry; no unsupported combat state required
- **Plan:** `TASKS.md` Skill-Focused Event Weapon Campaign
- **Completion:** `139657b`, `340c244`, and `4074bb1` add the shared window and
  all three weapons; focused/build/Javadoc/leak gates pass

### B-082 — Watatsumi Wavewalker weapon coverage

- **Status:** done
- **Scope:** Akuoumaru, Mouun's Moon, and Wavebreaker's Fin; RL excluded
- **Value/risk:** complete weapon family with one read-only party aggregate and
  no formula, event, target-state, or timing changes
- **Evidence boundary:** KQM TCL Lv. 90 metadata and R1-R5 per-Energy/cap values;
  the implementation uses the simulator's existing `getMaxEnergy()` contract
- **Plan:** `TASKS.md` Watatsumi Wavewalker Weapon Campaign
- **Completion:** `f111166`, `3951ccc`, and `3c562e6` add the shared party
  aggregate and all variants; focused/build/Javadoc/leak gates pass

### B-083 — Reciprocal hit weapon coverage

- **Status:** done
- **Scope:** Solar Pearl, Mitternachts Waltz, and Dodoco Tales; RL excluded
- **Value/risk:** three complete weapon passives share existing typed direct-hit
  dispatch and add no formula, random, target, or party state
- **Evidence boundary:** KQM TCL Lv. 90 metadata, R1-R5 values, trigger action
  groups, and five-/six-second durations
- **Plan:** `TASKS.md` Reciprocal Hit Weapon Campaign
- **Completion:** `a0e1368`, `696fb36`, and `a3fc809` add the shared dual-window
  policy and all variants; focused/build/Javadoc/leak gates pass

### B-084 — Reaction-window weapon coverage

- **Status:** done
- **Scope:** Mappa Mare, Emerald Orb, and Dark Iron Sword; RL excluded
- **Value/risk:** three complete passives reuse typed reaction attribution and
  add no reaction resolution, formula, random, or target state
- **Evidence boundary:** KQM TCL current metadata/reaction lists and refinement
  values; shared-duration Mappa stacks; Stellar-Conduct remains unsupported
- **Plan:** `TASKS.md` Reaction-Window Weapon Campaign
- **Completion:** `73a6661`, `74d3f5d`, and `2f780f8` add the shared listener and
  all variants; focused/build/Javadoc/leak gates pass

### B-085 — Hit-stack weapon coverage

- **Status:** done
- **Scope:** Ballad of the Boundless Blue, Compound Bow, and Ibis Piercer; RL excluded
- **Value/risk:** three full passives share existing typed direct-hit dispatch
  and a bounded deterministic stack state
- **Evidence boundary:** KQM TCL metadata, R1-R5 values, action gates, 0.3/0.5s
  CTs, stack caps, six-second duration, and off-field stack persistence
- **Plan:** `TASKS.md` Hit-Stack Weapon Campaign
- **Completion:** `10c88a6`, `f0de472`, and `0185589` add the shared hit stack
  policy and all variants; focused/build/Javadoc/leak gates pass

### B-086 — Action-use window weapon coverage

- **Status:** done
- **Scope:** Etherlight Spindlelute, Wine and Song, and Skyrider Sword; RL excluded
- **Value/risk:** three local content additions plus a source-compatible
  generalization of the already verified Skill-use window
- **Evidence boundary:** KQM TCL metadata, R1-R5 values, typed Skill/Dash/Burst
  triggers and durations; movement speed/stamina remain unmodeled
- **Plan:** `TASKS.md` Action-Use Window Weapon Campaign
- **Completion:** `c672bbf`, `a59b00c`, and `dbf3b15` preserve the Skill API and
  add all variants; focused/build/Javadoc/leak gates pass

### B-087 — Claymore hit-stack weapon coverage

- **Status:** done
- **Scope:** Skyrider Greatsword and Whiteblind; RL excluded
- **Value/risk:** two complete passives reuse the verified typed hit-stack
  policy without extending simulator state or trigger dispatch
- **Evidence boundary:** KQM TCL metadata, R1-R5 values, Normal/Charged action
  gates, 0.5-second CT, four-stack cap, and shared six-second duration
- **Plan:** `TASKS.md` Claymore Hit-Stack Weapon Campaign
- **Completion:** `b718c14` and `7e4cba0` add both variants; focused/build/
  Javadoc/leak gates pass

### B-088 — Direct physical proc weapon coverage

- **Status:** done
- **Scope:** Prototype Archaic, Fillet Blade, and Halberd; RL excluded
- **Value/risk:** three complete passives share one bounded proc implementation;
  injected draws keep tests and optimizers reproducible
- **Evidence boundary:** KQM TCL metadata, positive-hit/action gates, R1-R5
  chances, multipliers, cooldowns, and Physical damage formula
- **Plan:** `TASKS.md` Direct Physical Proc Weapon Campaign
- **Completion:** `9833fa3`, `20cfb49`, and `7695dde` add the shared proc policy
  and all variants; focused/build/Javadoc/leak gates pass

### B-089 — Lisa character vertical slice

- **Status:** done
- **Scope:** stationary single-target Hold Skill/Burst model; RL excluded
- **Value/risk:** adds a missing starter character with explicit input- and
  target-model limits; no shared runtime contract is changed
- **Evidence boundary:** KQM TCL Lv. 90 stats, level-9/12 talents, frames, gauges,
  ICD, particles, Conductive timing, Burst count/cadence, and C1/C3/C5
- **Plan:** `TASKS.md` Lisa Character Vertical Slice
- **Completion:** `dc358b7` adds Lisa identity, config, combat model, and focused
  regressions; build/Javadoc/leak gates pass

### B-090 — Samurai Conduct weapon coverage

- **Status:** done
- **Scope:** Kitain Cross Spear and Katsuragikiri Nagamasa; RL excluded
- **Value/risk:** two complete passives share one typed trigger and add one
  narrow, reusable Energy spend operation
- **Evidence boundary:** KQM TCL metadata, R1-R5 Skill bonus/recovery, positive
  Skill hit, 10-second CT, 22-24-frame drain delay, off-field and zero-Energy behavior
- **Plan:** `TASKS.md` Samurai Conduct Weapon Campaign
- **Completion:** `0a1c228` adds Energy spend, shared Samurai Conduct, both
  weapons, and focused regressions; build/Javadoc/leak gates pass

### B-091 — Skill-use stat weapon expansion

- **Status:** done
- **Scope:** Flute of Ezpitzal, Footprint of the Rainbow, and Tamayuratei no
  Ohanashi; RL excluded
- **Value/risk:** three complete combat-stat windows reuse a verified typed
  action policy with no shared-code changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 values, Skill use, refresh-only
  15/10-second windows; Movement SPD remains unmodeled
- **Plan:** `TASKS.md` Skill-Use Stat Weapon Expansion
- **Completion:** `9232f8d` adds all three variants and focused regressions;
  build/Javadoc/leak gates pass

### B-092 — Additional hit-stack weapon coverage

- **Status:** done
- **Scope:** Prototype Rancour and Sacrificer's Staff; RL excluded
- **Value/risk:** two complete multi-stat passives reuse the verified stack
  policy without shared-code changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 values, Normal/Charged/Skill
  gates, 0.3-second CT, stack caps, six-second duration, and off-field Staff
- **Plan:** `TASKS.md` Additional Hit-Stack Weapon Expansion
- **Completion:** `bf06373` adds both variants and focused regressions;
  build/Javadoc/leak gates pass

### B-093 — Hybrid reaction-window weapon coverage

- **Status:** done
- **Scope:** Missive Windspear and Mailed Flower; RL excluded
- **Value/risk:** two complete unequal-stat windows share attributed reaction
  and post-damage Skill hooks already used by the simulator
- **Evidence boundary:** KQM TCL metadata, R1-R5 ATK/EM values, 10/8-second
  windows, Mailed active-owner gate and trigger-hit ordering
- **Plan:** `TASKS.md` Hybrid Reaction Window Weapon Campaign
- **Completion:** `9a7e8ae` adds the shared attributed trigger policy, both
  weapons, and focused regressions; build/Javadoc/leak gates pass

### B-094 — Self-contained four-star weapon coverage

- **Status:** done
- **Scope:** Prototype Starglitter, Iron Sting, and Ballad of the Fjords; RL excluded
- **Value/risk:** three complete passives use existing typed action, damage, and
  simulator-party reads with no new shared runtime dispatch
- **Evidence boundary:** KQM TCL metadata, R1-R5 values, Starglitter shared
  stack duration, Iron Sting elemental-hit CT, and Fjords element diversity
- **Plan:** `TASKS.md` Self-Contained Four-Star Weapon Expansion
- **Completion:** `e0564b2` adds all three weapons and focused regressions;
  build/Javadoc/leak gates pass

### B-095 — Frost Burial weapon coverage

- **Status:** done
- **Scope:** Dragonspine Spear, Snow-Tombed Starsilver, Frostbearer; RL excluded
- **Value/risk:** three complete variants share one bounded, injectable proc
  implementation and reuse the live target-Aura API
- **Evidence boundary:** KQM TCL metadata, R1-R5 chance/base/Cryo multipliers,
  Normal/Charged gates, and ten-second cooldown
- **Plan:** `TASKS.md` Frost Burial Weapon Campaign
- **Completion:** `cd34797` adds the shared proc policy, all three variants, and
  focused regressions; build/Javadoc/leak gates pass

### B-096 — Energy-aware action weapon coverage

- **Status:** done
- **Scope:** Hamayumi and Moonweaver's Dawn; RL excluded
- **Value/risk:** two complete dynamic passives consume stable owner Energy APIs
  without adding event dispatch or mutating runtime state
- **Evidence boundary:** KQM TCL metadata, R1-R5 action bonuses, full-Energy
  doubling, and 60/40 maximum-Energy tiers
- **Plan:** `TASKS.md` Energy-Aware Action Weapon Campaign
- **Completion:** `8ff2ef7` adds both dynamic weapons and focused regressions;
  build/Javadoc/leak gates pass

### B-097 — Deterministic physical proc weapon coverage

- **Status:** done
- **Scope:** Kagotsurube Isshin, The Flute, and Debate Club; RL excluded
- **Value/risk:** three complete proc passives add bounded local state only and
  reuse the verified generated Physical action pipeline
- **Evidence boundary:** KQM TCL metadata, typed gates, 180% Isshin proc/window,
  five-Harmonic Flute, and 15-second/three-second Debate policy
- **Plan:** `TASKS.md` Deterministic Physical Proc Weapon Campaign
- **Completion:** `2e69b5f` adds all three state machines and focused regressions;
  build/Javadoc/leak gates pass

### B-098 — Off-field hit bow coverage

- **Status:** done
- **Scope:** Fading Twilight and Rainbow Serpent's Rain Bow; RL excluded
- **Value/risk:** two complete passives use existing active-character and
  post-damage state without new dispatch or shared runtime mutation
- **Evidence boundary:** KQM TCL metadata, R1-R5 three-state DMG values,
  seven-second switch CT, and off-field 28-56% ATK eight-second window
- **Plan:** `TASKS.md` Off-Field Hit Weapon Campaign
- **Completion:** `aee7c8b` adds both bows and focused off-field regressions;
  build/Javadoc/leak gates pass

### B-099 — Skill/Burst offensive weapon coverage

- **Status:** done
- **Scope:** Fleuve Cendre Ferryman and Luxurious Sea-Lord; RL excluded
- **Value/risk:** two complete passives reuse typed Skill windows and generated
  Physical Burst procs without changing shared dispatch
- **Evidence boundary:** KQM TCL metadata, R1-R5 Skill CRIT/ER, Burst DMG/proc,
  five-second window, active-owner gate, and 15-second CT
- **Plan:** `TASKS.md` Skill/Burst Offensive Weapon Campaign
- **Completion:** `7e88815` adds both weapons and focused typed regressions;
  build/Javadoc/leak gates pass

### B-100 — Moonsign reaction weapon coverage

- **Status:** done
- **Scope:** Master Key and Serenity's Call; RL excluded
- **Value/risk:** two complete off-field passives share one bounded reaction
  listener and consume the existing live Moonsign API without dispatch changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 EM/HP values, attributed
  off-field reactions, 12-second duration, and Ascendant Gleam doubling
- **Plan:** `TASKS.md` Moonsign Reaction Weapon Campaign
- **Completion:** `d0c59b0` adds the shared live-Moonsign window, both weapons,
  and focused regressions; build/Javadoc/leak gates pass

### B-101 — Reaction utility claymore coverage

- **Status:** done
- **Scope:** Earth Shaker and Flame-Forged Insight; RL excluded
- **Value/risk:** two complete passives reuse typed reaction attribution, party
  membership, Skill DMG stats, and flat Energy without runtime dispatch changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 values, reaction families,
  off-field behavior, eight/15-second windows, and Flame's 15-second CT
- **Plan:** `TASKS.md` Reaction Utility Claymore Campaign
- **Completion:** `8fbad3a` adds both typed reaction listeners and focused
  regressions; build/Javadoc/leak gates pass

### B-102 — Timed EM team-stat weapon coverage

- **Status:** done
- **Scope:** Wandering Evenstar refinement support, Makhaira Aquamarine, and
  Xiphos' Moonlight; RL excluded
- **Value/risk:** two additions reuse and consolidate an already-regressed timer,
  snapshot, source attribution, and stacking policy
- **Evidence boundary:** KQM TCL metadata, R1-R5 conversion ratios, 64-frame
  first trigger, ten-second cadence, 12-second duration, 30% ally share, and
  Xiphos exclusion from Raiden A4/Emblem conversion
- **Plan:** `TASKS.md` Timed EM Team Weapon Campaign
- **Completion:** `961825a` adds Makhaira/Xiphos, refines Wandering, and adds a
  typed non-converting ER contract; build/Javadoc/reaction/report/leak gates pass

### B-103 — Self-contained five-star weapon coverage

- **Status:** done
- **Scope:** Skyward Pride and Lightbearing Moonshard; RL excluded
- **Value/risk:** two complete passives reuse verified action/hit/proc/window
  paths with no shared runtime dispatch changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 all-DMG/proc/DEF/Lunar values,
  eight blades, 20-second state, and five-second Skill window
- **Plan:** `TASKS.md` Self-Contained Five-Star Weapon Campaign
- **Completion:** `4130ebc` adds both weapons and focused typed regressions;
  build/Javadoc/leak gates pass

### B-104 — Energy and proximity five-star weapon coverage

- **Status:** done
- **Scope:** Azurelight and Aqua Simulacra; RL excluded
- **Value/risk:** two complete passives reuse live Energy and static stat
  assembly; Aqua's nearby condition is explicit under the single-enemy model
- **Evidence boundary:** KQM TCL metadata, R1-R5 ATK/CRIT/HP/DMG values,
  Skill triggering-hit order, zero Energy, and 12-second duration
- **Plan:** `TASKS.md` Energy and Proximity Five-Star Weapon Campaign
- **Completion:** `ab98b46` adds both weapons and focused live-state/static
  regressions; build/Javadoc/leak gates pass

### B-105 — Sumeru action-proc bow coverage

- **Status:** done
- **Scope:** End of the Line and King's Squire; RL excluded
- **Value/risk:** two complete passives reuse verified typed action, hit,
  switch-out, timer, and generated Physical damage contracts
- **Evidence boundary:** KQM TCL metadata, R1-R5 proc/EM values, three/two-second
  Flowrider limits, 12/15-second states, and 12/20-second activation CTs
- **Plan:** `TASKS.md` Sumeru Action-Proc Bow Campaign
- **Completion:** `6e74d66` adds both state machines and focused regressions;
  build/Javadoc/leak gates pass

### B-106 — Typed five-star bow coverage

- **Status:** done
- **Scope:** Polar Star and Astral Vulture's Crimson Plumage; RL excluded
- **Value/risk:** two complete passives reuse typed hit/reaction and live party
  reads without simulator dispatch changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 stack/ATK/Charged/Burst values,
  independent 12-second hit stacks, Swirl attribution, and one/two ally tiers
- **Plan:** `TASKS.md` Typed Five-Star Bow Campaign
- **Completion:** `08637ba` adds both typed state machines and focused
  regressions; build/Javadoc/leak gates pass

### B-107 — Energy-conditional emblem weapon coverage

- **Status:** done
- **Scope:** Mistsplitter Reforged and Thundering Pulse; RL excluded
- **Value/risk:** two complete passives reuse typed pre/post action ordering and
  live owner Energy without shared dispatch changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 static/emblem tiers, five/ten-
  second independent stacks, elemental/Normal hit gates, and Energy-full state
- **Plan:** `TASKS.md` Energy-Conditional Emblem Weapon Campaign
- **Completion:** `a3935ae` adds both emblem state machines and focused
  regressions; build/Javadoc/leak gates pass

### B-108 — Five-star EM support weapon coverage

- **Status:** done
- **Scope:** Elegy for the End and A Thousand Floating Dreams; RL excluded
- **Value/risk:** two complete support passives reuse typed damage hooks and
  party buffs; one dormant provider-targeting path needs a local correction
- **Evidence boundary:** KQM TCL metadata, R1-R5 EM/ATK/elemental values,
  Elegy 0.2/12/20-second boundaries, live composition, and provider stacking
- **Plan:** `TASKS.md` Five-Star EM Support Weapon Campaign
- **Completion:** `47a0650` adds both support weapons, typed provider targeting,
  and focused regressions; build/Javadoc/leak gates pass

### B-109 — Five-star catalyst stack weapon coverage

- **Status:** done
- **Scope:** Kagura's Verity and Lost Prayer to the Sacred Winds; RL excluded
- **Value/risk:** two complete DPS passives reuse typed Skill, periodic timer,
  active-character, and switch contracts without shared runtime changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 Skill/elemental tiers, shared
  24-second Kagura expiry, fixed four-second Lost Prayer cadence, switch reset
- **Plan:** `TASKS.md` Five-Star Catalyst Stack Weapon Campaign
- **Completion:** `ce792c0` adds both stack state machines and focused
  regressions; build/Javadoc/leak gates pass

### B-110 — Injected bow proc weapon coverage

- **Status:** done
- **Scope:** Skyward Harp and The Viridescent Hunt; RL excluded
- **Value/risk:** two complete passives reuse injected draws and nonrecursive
  Physical actions; Cyclone adds only one bounded timer
- **Evidence boundary:** KQM TCL metadata, R1-R5 CRIT/chance/MV/CT values,
  immediate Harp proc and eight half-second Cyclone ticks over four seconds
- **Plan:** `TASKS.md` Injected Bow Proc Weapon Campaign
- **Completion:** `877a526` adds both injected proc implementations and focused
  regressions; build/Javadoc/leak gates pass

### B-111 — Live-party five-star weapon coverage

- **Status:** done
- **Scope:** The First Great Magic and Uraku Misugiri; RL excluded
- **Value/risk:** two complete DPS passives reuse live party reads and the
  attributed global damage listener without shared runtime changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 Charged/ATK/Normal/Skill/DEF
  values, same-element tiers, positive active-character Geo, 15-second window
- **Plan:** `TASKS.md` Live Party Five-Star Weapon Campaign
- **Completion:** `35c25d2` adds both live-party passives and focused
  regressions; build/Javadoc/leak gates pass

### B-112 — Moonsign EM and Bloom weapon coverage

- **Status:** done
- **Scope:** Snare Hook and Blackmarrow Lantern; RL excluded
- **Value/risk:** two complete passives reuse verified Moonsign reaction-window
  and live-state contracts without shared runtime changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 EM/Bloom/Lunar-Bloom values,
  off-field reactions, exact 12-second window, live Ascendant doubling
- **Plan:** `TASKS.md` Moonsign EM and Bloom Weapon Campaign
- **Completion:** `8908f92` adds both Moonsign weapons and focused regressions;
  build/Javadoc/leak gates pass

### B-113 — Catalyst dual-window weapon coverage

- **Status:** done
- **Scope:** Dawning Frost and Reliquary of Truth; RL excluded
- **Value/risk:** two complete passives reuse typed hit/action/reaction hooks
  and independent windows without shared runtime changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 EM/CRIT values, Dawning
  ten-second windows, Reliquary four/12-second windows and 1.5x intersection
- **Plan:** `TASKS.md` Catalyst Dual-Window Weapon Campaign
- **Completion:** `542fceb` adds both dual-window state machines and focused
  regressions; build/Javadoc/leak gates pass

### B-114 — Fruit of Fulfillment coverage

- **Status:** done
- **Scope:** Fruit of Fulfillment; RL excluded
- **Value/risk:** one complete passive reuses attributed reaction and bounded
  timer contracts; generation tokens prevent stale inactivity decay
- **Evidence boundary:** KQM TCL metadata, R1-R5 EM values, fixed -5% ATK per
  stack, 0.3-second gain CT, five-stack cap, six-second repeated decay
- **Plan:** `TASKS.md` Fruit of Fulfillment Campaign
- **Completion:** `c7ebd3a` adds the complete stack/decay state machine and
  focused regressions; build/Javadoc/leak gates pass

### B-115 — Scion of the Blazing Sun coverage

- **Status:** done
- **Scope:** Scion of the Blazing Sun; RL excluded
- **Value/risk:** one complete passive reuses typed post-hit and immediate
  nonrecursive Physical action contracts under the single-enemy model
- **Evidence boundary:** KQM TCL metadata, R1-R5 proc/Charged values, positive
  active Charged gate, ten-second activation CT and Heartsearer duration
- **Plan:** `TASKS.md` Scion of the Blazing Sun Campaign
- **Completion:** `a22e1ba` adds the immediate Physical proc, post-hit
  Heartsearer window, exact CT behavior, and focused abnormal regressions;
  build/Javadoc/leak gates pass

### B-116 — Alley Hunter coverage

- **Status:** done
- **Scope:** Alley Hunter; RL excluded
- **Value/risk:** one complete passive reuses the simulator's fixed timer and
  live active-character state without extending switch contracts
- **Evidence boundary:** KQM TCL metadata, R1-R5 growth/decay values, ten-stack
  cap, one-second cadence, and four-second on-field grace
- **Plan:** `TASKS.md` Alley Hunter Campaign
- **Completion:** `4aca5e4` adds fixed-cadence off-field growth, delayed
  on-field decay, grace reset, cap/floor behavior, and focused regressions;
  build/Javadoc/leak gates pass

### B-117 — Sequence of Solitude coverage

- **Status:** done
- **Scope:** Sequence of Solitude; RL excluded
- **Value/risk:** one complete passive reuses immediate nonrecursive damage and
  the existing Max-HP scaling path without shared runtime changes
- **Evidence boundary:** KQM TCL metadata, R1-R5 Max-HP proc values, positive
  active hit gate, and 15-second activation CT
- **Plan:** `TASKS.md` Sequence of Solitude Campaign
- **Completion:** `09eb8ff` adds immediate nonrecursive total-HP damage, exact
  cooldown behavior, R1/R5 ratios, and focused abnormal regressions;
  build/Javadoc/leak gates pass

### B-118 — Eye of Perception coverage

- **Status:** done
- **Scope:** Eye of Perception; RL excluded
- **Value/risk:** one complete passive directly specializes the tested injected
  Physical-proc policy under the single-enemy model
- **Evidence boundary:** KQM TCL metadata, R1-R5 Bolt values/cooldowns, 50%
  Normal/Charged chance, and four-opponent bounce cap
- **Plan:** `TASKS.md` Eye of Perception Campaign
- **Completion:** `ed9d3a8` specializes the injected direct-proc policy with
  exact chance/cooldown boundaries, R1/R5 ratios, and focused abnormal tests;
  build/Javadoc/leak gates pass

### B-119 — One-star weapon series coverage

- **Status:** done
- **Scope:** Dull Blade, Waster Greatsword, Beginner's Protector, Apprentice's
  Notes, and Hunter's Bow; RL excluded
- **Value/risk:** closes five exact passive-free weapon gaps without adding
  runtime hooks, randomness, or shared behavior
- **Evidence boundary:** maintained Genshin Impact Wiki 1-Star Series and weapon
  entries, accessed 2026-08-02, record all five weapon types with maximum-level
  185 base ATK and no secondary attribute, refinement, or passive:
  https://genshin-impact.fandom.com/wiki/1-Star_Series
- **Plan:** `TASKS.md` One-Star Weapon Series Campaign
- **Completion:** all five classes expose only 185 base ATK and the matching
  weapon type; the table-driven regression proves the absent substat/passive
  contract and build/Javadoc/preflight gates pass

### B-120 — Two-star weapon series coverage

- **Status:** done
- **Scope:** Silver Sword, Old Merc's Pal, Iron Point, Pocket Grimoire, and
  Seasoned Hunter's Bow; RL excluded
- **Value/risk:** closes five exact passive-free weapon gaps without adding
  runtime hooks, randomness, or shared behavior
- **Evidence boundary:** maintained Genshin Impact Wiki 2-Star Series and weapon
  entries, accessed 2026-08-02, record all five weapon types with maximum-level
  243 base ATK and no secondary attribute, refinement, or passive:
  https://genshin-impact.fandom.com/wiki/2-Star_Series
- **Plan:** `TASKS.md` Two-Star Weapon Series Campaign
- **Completion:** all five classes expose only 243 base ATK and the matching
  weapon type; shared table-driven regressions and all local gates pass

### B-121 — Isolated runtime weapon expansion

- **Status:** done
- **Scope:** "Ultimate Overlord's Mega Magic Sword", Ash-Graven Drinking Horn,
  Toukabou Shigure, and Waveriding Whirl; RL excluded
- **Value/risk:** four complete passives fit existing static, action-triggered,
  damage-triggered, simulator-binding, live-party, and Max-HP contracts without
  a shared runtime change
- **Evidence boundary:** maintained KQM weapon catalogs, accessed 2026-08-02,
  provide Lv. 90 metadata, R1-R5 values, durations, cooldowns, and trigger text:
  https://library.keqingmains.com/equipment/weapons/claymores#ultimate-overlords-mega-magic-sword
  https://library.keqingmains.com/equipment/weapons/catalysts#ash-graven-drinking-horn
  https://library.keqingmains.com/equipment/weapons/swords#toukabou-shigure
  https://library.keqingmains.com/equipment/weapons/catalysts#waveriding-whirl
- **Boundary:** enemy-defeat cooldown reset, multi-target proc multiplication,
  and swimming stamina remain outside the current simulator
- **Plan:** `TASKS.md` Isolated Runtime Weapon Expansion Campaign
- **Completion:** two isolated branch commits were reviewed and integrated;
  focused regressions cover all four metadata/passive contracts and abnormal
  boundaries, and reaction regression/build/Javadoc/preflight gates pass

### B-122 — Blackcliff weapon family coverage

- **Status:** done
- **Scope:** Blackcliff Longsword, Slasher, Pole, Agate, and Warbow; RL excluded
- **Value/risk:** closes five canonical weapon gaps through one family boundary
  without pretending the current immortal-enemy simulator emits defeat events
- **Evidence boundary:** maintained KQM Sword, Claymore, Polearm, Catalyst, Bow,
  and weapon-evidence catalogs, accessed 2026-08-02, record Lv. 90 metadata,
  R1-R5 ATK stacks, 30-second independent durations, and on-field kill
  attribution; representative evidence:
  https://library.keqingmains.com/evidence/equipment/weapons#blackcliff-series
- **Boundary:** Press the Advantage cannot activate until enemy defeat and kill
  attribution become modeled runtime events
- **Plan:** `TASKS.md` Blackcliff Weapon Family Campaign
- **Completion:** branch-isolated family implementation `ace27be` centralizes
  the defeat boundary; table-driven regressions and reaction regression,
  build, Javadoc, and preflight gates pass

### B-123 — Three-star runtime-boundary weapon coverage

- **Status:** done
- **Scope:** Harbinger of Dawn, Traveler's Handy Sword, Ferrous Shadow, White
  Iron Greatsword, Black Tassel, Messenger, Recurve Bow, Sharpshooter's Oath,
  Slingshot, Otherworldly Story, and Twin Nephrite; RL excluded
- **Value/risk:** closes every remaining three-star gap whose active state is
  uniquely determined under current full-player-HP, generic immortal enemy,
  immediate-impact combat boundaries
- **Evidence boundary:** maintained KQM weapon catalogs and evidence vault,
  accessed 2026-08-02, provide metadata/passive values and healing/travel
  behavior: https://library.keqingmains.com/evidence/equipment/weapons
- **Boundary:** TTDS remains unimplemented because character swaps occur but
  weapons receive no incoming-character callback; representing it as no-op
  would be observably incomplete
- **Plan:** `TASKS.md` Three-Star Runtime-Boundary Weapon Campaign
- **Completion:** both phases pass table-driven metadata, refinement,
  full-HP, immediate-impact, and explicitly inactive boundary regressions;
  reaction regression, build, Javadoc, and preflight gates pass

### B-124 — Stateful craftable weapon coverage

- **Status:** done
- **Scope:** Ring of Yaxche, Cloudforged, Hakushin Ring, and Crescent Pike; RL
  excluded
- **Value/risk:** closes four missing weapons through existing typed hooks;
  reaction-element filtering and generated follow-up recursion require focused
  regression coverage
- **Evidence boundary:** maintained KQM weapon catalogs and evidence vault,
  accessed 2026-08-02, provide Lv. 90 metadata, R1-R5 values, durations,
  stack refresh, on-field, reaction-element, and follow-up behavior:
  https://library.keqingmains.com/equipment/weapons/catalysts#ring-of-yaxche
  https://library.keqingmains.com/equipment/weapons/bows#cloudforged
  https://library.keqingmains.com/equipment/weapons/catalysts#hakushin-ring
  https://library.keqingmains.com/equipment/weapons/polearms#crescent-pike
- **Boundary:** Cloudforged observes successful Burst requests as the current
  Energy-decrease path; Crescent Pike observes particle notification while the
  holder is active as collection
- **Plan:** `TASKS.md` Stateful Craftable Weapon Campaign
- **Completion:** both isolated source phases were reviewed on the main branch;
  focused metadata, snapshot, stack, element-window, collection, and follow-up
  regressions plus reaction regression, build, Javadoc, and preflight pass

### B-125 — Switch-activated weapon coverage

- **Status:** done
- **Scope:** backward-compatible incoming/target weapon switch callbacks, The
  Widsith, Sacrificial Jade, and Thrilling Tales of Dragon Slayers; RL excluded
- **Value/risk:** closes the known TTDS/Widsith boundary and the last fully
  representable catalyst while preserving legacy switch-aware weapon behavior
- **Evidence boundary:** maintained KQM catalyst catalog and weapon evidence,
  accessed 2026-08-02, provide metadata, R1-R5 values, effect selection,
  off/on-field thresholds, durations, and cooldowns:
  https://library.keqingmains.com/equipment/weapons/catalysts#the-widsith
  https://library.keqingmains.com/equipment/weapons/catalysts#sacrificial-jade
  https://library.keqingmains.com/equipment/weapons/catalysts#thrilling-tales-of-dragon-slayers
- **Boundary:** stochastic Widsith selection is injectable for reproducibility;
  direct active-character setters intentionally remain callback-free fixtures
- **Plan:** `TASKS.md` Switch-Activated Weapon Campaign
- **Completion:** backward-compatible incoming/target callbacks plus The
  Widsith, Sacrificial Jade, and Thrilling Tales of Dragon Slayers are covered
  by focused timing, refinement, replacement, and abnormal regressions; all
  local gates passed on 2026-08-02

### B-126 — Core artifact set expansion

- **Status:** done
- **Scope:** Gladiator's Finale, Golden Troupe, Gilded Dreams, and Pale Flame;
  RL excluded
- **Value/risk:** adds four broadly useful complete sets through existing
  capabilities; timing/order and owner binding require focused regressions
- **Evidence boundary:** maintained KQM artifact catalog, accessed 2026-08-02,
  provides two-/four-piece values, field grace, reaction ordering, composition,
  stack cadence, and expiry: https://library.keqingmains.com/equipment/artifacts
- **Boundary:** each class represents an equipped four-piece set, matching the
  repository's existing artifact abstraction
- **Plan:** `TASKS.md` Core Artifact Set Expansion Campaign
- **Completion:** Gladiator's Finale, Golden Troupe, Gilded Dreams, and Pale
  Flame passed focused metadata, field, composition, timing, trigger, binding,
  independence, reaction regression, build, Javadoc, and preflight checks

### B-127 — Expanded artifact set coverage

- **Status:** done
- **Source:** explicit broad content-coverage request
- **Scope:** Wanderer's Troupe, Finale of the Deep Galleries, Instructor,
  Deepwood Memories, Blizzard Strayer, and Nymph's Dream; RL excluded
- **Value/risk:** adds six complete and broadly useful sets through existing
  owner, reaction, damage, party-buff, energy, and enemy-state capabilities
- **Evidence boundary:** maintained KQM artifact catalog and evidence vault,
  accessed 2026-08-02, provide fixed values, pre-/post-hit ordering, exact
  durations, category independence, target state, and field requirements:
  https://library.keqingmains.com/equipment/artifacts
  https://library.keqingmains.com/evidence/equipment/artifacts
- **Classification:** adopt exact described combat behavior; unavailable
  unequip/co-op behavior remains outside simulator lifecycle
- **Risk/proof:** planned; focused regressions plus reaction regression, build,
  Javadoc, and preflight
- **Plan:** `TASKS.md` Expanded Artifact Coverage Campaign
- **Completion:** all six sets pass focused metadata, supplied-stat, owner,
  field, target-state, energy, reaction, hit-category, exact timing, invalid
  callback, reaction regression, build, Javadoc, and preflight checks

### B-128 — Action-use artifact coverage

- **Status:** done
- **Source:** explicit broad content-coverage request
- **Scope:** shared successful-action artifact callback, Heart of Depth, and
  Martial Artist; RL excluded
- **Value/risk:** removes a cross-cutting capability gap and adds two complete
  sets without character-specific dispatch; callback ordering requires a
  planned contract regression
- **Evidence boundary:** maintained KQM artifact catalog and evidence vault,
  accessed 2026-08-02, provide fixed values, Skill-use triggers, durations,
  refresh behavior, and damage categories:
  https://library.keqingmains.com/equipment/artifacts
  https://library.keqingmains.com/evidence/equipment/artifacts
- **Classification:** adopt successful typed Skill requests as Skill use;
  rejected Burst requests must not dispatch artifact action callbacks
- **Risk/proof:** planned; focused dispatch/timing regressions plus reaction
  regression, build, Javadoc, and preflight
- **Plan:** `TASKS.md` Action-Use Artifact Campaign
- **Completion:** accepted-action dispatch plus Heart of Depth and Martial
  Artist pass focused ordering, fixed-stat, activation, refresh, exact expiry,
  invalid callback, binding, reaction regression, build, Javadoc, and
  preflight checks

### B-129 — Husk of Opulent Dreams Curiosity state

- **Status:** done
- **Source:** content inventory following explicit broad coverage request
- **Scope:** complete Husk of Opulent Dreams two-/four-piece outgoing behavior;
  RL excluded
- **Value/risk:** adds a broadly used exact DEF/Geo set; overlapping switch,
  hit cooldown, off-field gain, and no-gain decay timers require one planned
  state-machine phase
- **Evidence boundary:** maintained KQM catalog and evidence, accessed
  2026-08-02, establish values, zero-damage/immune Geo hits, timer restart,
  field-independent decay, and multiple stack losses inside ten seconds:
  https://library.keqingmains.com/equipment/artifacts#husk-of-opulent-dreams
  https://library.keqingmains.com/evidence/equipment/artifacts#husk-of-opulent-dreams
- **Classification:** implement exact simulator-lifecycle combat state;
  artifact unequip and party removal remain outside the runtime lifecycle
- **Risk/proof:** planned; focused timing/state regressions plus reaction
  regression, build, Javadoc, and preflight
- **Plan:** `TASKS.md` Husk Curiosity Campaign
- **Completion:** complete fixed/stack stats, field-aware hit and timer gains,
  switch resets, stale/coincident event ordering, exact decay, invalid binding,
  reaction regression, build, Javadoc, and preflight checks pass

### B-130 — Scholar party Energy set

- **Status:** done
- **Source:** content inventory following explicit broad coverage request
- **Scope/risk:** local artifact listener; RL excluded
- **Evidence:** maintained KQM artifact catalog, accessed 2026-08-02, specifies
  ER +20%, three flat Energy to Bow/Catalyst party members, and three-second CT:
  https://library.keqingmains.com/equipment/artifacts#scholar
- **Boundary:** the existing particle notification represents both modeled
  particles and orbs after normal Energy distribution
- **Completion:** supplied/fixed stats, weapon filtering, field independence,
  flat Energy, cap, exact cooldown, rejected notifications, listener binding,
  reaction regression, build, Javadoc, and preflight checks pass

### B-131 — Supported character passive and constellation accuracy

- **Status:** in-progress
- **Source:** read-only supported-character inventory after explicit broad
  content request
- **Scope:** Bennett A1, Xiangling C2, and Xingqiu C2/C4/C6; RL excluded
- **Value/risk:** fixes two observable Xingqiu mis-gates and party-wide Energy,
  adds two missing offensive passives, and preserves disjoint source ownership
- **Evidence boundary:** maintained KQM character pages and Xiangling evidence,
  accessed 2026-08-02, establish cooldown, hit-time delay, unsnapshotted/no-tag
  damage, constellation durations, ordering, multipliers, wave cycle, and
  Energy ownership:
  https://library.keqingmains.com/characters/pyro/bennett
  https://library.keqingmains.com/characters/pyro/xiangling
  https://library.keqingmains.com/evidence/characters/pyro/xiangling
  https://library.keqingmains.com/characters/hydro/xingqiu
- **Risk/proof:** planned; focused character regressions plus reaction
  regression, build, Javadoc, and preflight
- **Plan:** `TASKS.md` Supported Character Passive Campaign
