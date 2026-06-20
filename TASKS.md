# Accuracy Implementation Plan

## Scope

The reaction core, aura/ICD detail passes, Bloom-family behavior, Quicken-family
behavior, Lunar reaction handling, and current regression coverage are treated as
the baseline implementation.

The next work should improve simulator accuracy by auditing and filling gaps in
the currently used parties, weapons, artifacts, and RL party metadata. The goal is
to improve offensive-output accuracy while preserving the current single-target,
non-attacking enemy scope.

Out of scope for this pass:

- enemy attacks, survival pressure, healing prevention, and defensive shield HP
- multi-target positioning, AoE geometry, and enemy movement
- exploration systems such as Arkhe, Nightsoul, or Phlogiston unless an offensive
  kit explicitly depends on them
- new party additions before the existing benchmark parties are audited

## Current Baseline

Implemented and covered by `sample.ReactionRegressionTest`:

- reaction metadata and transformative multipliers
- Superconduct, Freeze, Shatter, Crystallize, and Burning
- Bloom, Hyperbloom, and Burgeon core behavior
- Quicken, Aggravate, and Spread additive damage
- Lunar-Charged, Lunar-Bloom, and Lunar-Crystallize behavior
- aura decay and basic gauge consumption
- ICD hit/time rules, no-ICD behavior, and shared ICD blocking
- selected character, weapon, and artifact trigger regressions

Primary validation command:

- `./gradlew ReactionRegressionTest`

Broader sample validation:

- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`

## Implementation Order

### Phase 1: RaidenParty Accuracy Audit - Done

Why first:

`RaidenParty` is the conventional non-Lunar benchmark team. It is the best place
to find remaining ordinary combat accuracy gaps before touching custom Lunar
logic again.

Target files:

- `src/java/sample/RaidenParty.java`
- `src/java/model/character/RaidenShogun.java`
- `src/java/model/character/Xiangling.java`
- `src/java/model/character/Xingqiu.java`
- `src/java/model/character/Bennett.java`
- relevant files under `src/java/model/weapon/`
- relevant files under `src/java/model/artifact/`
- `config/characters/`
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Inventory which Raiden National mechanics are currently exact, simplified, or
  missing.
- Check action timings, cooldown assumptions, burst windows, summon durations,
  snapshot behavior, and particle generation.
- Check Resolve generation and consumption for Raiden.
- Check Xiangling Pyronado and Guoba snapshot/tick behavior.
- Check Xingqiu Raincutter trigger cadence, wave pattern, and Hydro application.
- Check Bennett burst ATK buff timing, field behavior, and interaction with
  snapshotting.
- Check currently equipped weapons and artifacts for missing offensive passives.
- Record high-impact gaps directly in this file before implementing broad
  changes.

Acceptance criteria:

- `RaidenParty` has an explicit accuracy inventory.
- High-impact missing mechanics are listed as implementation tasks.
- Existing reaction regressions still pass.

Implementation status:

- Audited `RaidenParty`, Raiden, Xiangling, Xingqiu, Bennett, and the equipped
  weapons/artifacts.
- Confirmed implemented baseline coverage for Raiden Resolve and Musou energy,
  Xiangling Pyronado snapshot and Guoba/Chili approximation, Xingqiu Raincutter
  C6 wave pattern and C4 skill multiplier, Bennett burst field/Noblesse trigger,
  Emblem, The Catch, Wolf-Fang, Skyward Spine, and Skyward Blade.
- Found one high-impact Resolve issue: Raiden's action listener credited every
  `ActionType.BURST` hit, so Xiangling's multi-hit burst cast granted Resolve
  more than once.
- Remaining simplifications to track later: Xingqiu orbital rain swords are
  modeled as zero-damage Hydro aura ticks; Xiangling Chili pickup is assumed;
  Skyward Spine uses random Vacuum Blade procs, which can make optimizer/sample
  output nondeterministic.

Test cases to add or update:

- Add tests only when the audit identifies an untested behavior or a likely
  regression point.
- Normal path: fixed-script checks for Resolve gain/consumption, burst window
  uptime, snapshot creation, and expected follow-up trigger counts.
- Error/invalid path: action attempts during cooldown, burst attempts without
  enough energy, and reaction/listener hooks that should not fire outside their
  documented window.
- Boundary values: buff expiry at the exact hit time, summon final tick timing,
  Resolve at zero and cap, and energy values just below and just above burst
  cost.
- Unit-level logic: helper methods or character state transitions that can be
  checked without a full sample rotation.
- Integration path: a representative shortened Raiden National rotation that
  verifies damage attribution, reaction labels, energy flow, and report logging.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`

### Phase 2: RaidenParty High-Impact Fixes - Done

Why second:

The audit should determine the exact order, but fixes should prioritize mechanics
that materially affect DPS, rotation timing, energy, or snapshot behavior.

Candidate tasks:

- Fix Resolve generation or burst damage contribution if the audit finds drift.
- Fix Xingqiu burst follow-up timing or application behavior if it changes
  Vaporize/Electro-Charged frequency.
- Fix Xiangling snapshot behavior if active buffs are not captured correctly.
- Fix Bennett burst buff lifecycle if it affects Pyronado or Raiden burst
  snapshots.
- Add or update focused regression tests for each fixed mechanic.

Acceptance criteria:

- Each implemented fix has a focused regression in `ReactionRegressionTest` or a
  similarly lightweight executable sample.
- `RaidenParty` still runs and any DPS delta is explained in final notes.

Implementation status:

- Fixed Raiden Resolve crediting so each teammate contributes once per actual
  burst cast, even when that burst has multiple `ActionType.BURST` damage hits.
- Added a regression that verifies Xiangling's multi-hit burst cast grants one
  Resolve contribution plus the expected Wishes Unnumbered particle trigger.
- `RaidenParty` still runs. The final sample changed from 1,389,957 total damage
  / 66,188 DPS to 1,362,938 total damage / 64,902 DPS because Raiden now consumes
  48 Resolve instead of the incorrect capped 60 Resolve in the benchmark script.

Test cases to add or update:

- Add at least one deterministic regression for every implemented fix.
- Normal path: the corrected mechanic triggers under expected rotation
  conditions and produces the expected state, damage, or energy delta.
- Error/invalid path: the same mechanic does not trigger for the wrong action
  type, wrong active character, missing buff, missing energy, cooldown lockout, or
  expired state.
- Boundary values: just-before and just-after timing around buff expiry, summon
  tick cadence, ICD windows, and burst/state end times.
- Unit-level logic: isolate core calculations such as Resolve contribution,
  snapshot stat capture, Raincutter wave counting, or Pyronado tick scheduling
  when possible.
- Integration path: run a compact RaidenParty script that exercises the changed
  mechanic together with reactions, buffs, energy, and logging.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`

### Phase 3: FlinsParty2 Lunar Accuracy Audit - Done

Why third:

Lunar reaction infrastructure is implemented, but `FlinsParty2` depends on many
custom character hooks, Lunar-specific stats, reaction listeners, and item
effects. These should be audited after the conventional team is stable.

Target files:

- `src/java/sample/FlinsParty2.java`
- `src/java/model/character/Flins.java`
- `src/java/model/character/Ineffa.java`
- `src/java/model/character/Columbina.java`
- `src/java/model/character/Sucrose.java`
- relevant Lunar weapons under `src/java/model/weapon/`
- relevant Lunar artifacts under `src/java/model/artifact/`
- `src/java/mechanics/reaction/`
- `src/java/mechanics/formula/LunarDamageStrategy.java`
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Inventory implemented, simplified, and missing Lunar character mechanics.
- Check Lunar-Charged Thundercloud ownership, tick cadence, crit rules, and
  conditional follow-up behavior.
- Check Lunar-Bloom Dew state creation, consumption hooks, and direct damage
  conversion hooks.
- Check Lunar-Crystallize Moondrift counters and Harmony cadence.
- Check Columbina Gravity accumulation, Interference triggers, Dew resources, and
  Lunar Domain conditions.
- Check Flins form state, Thundercloud-dependent hits, energy behavior, and
  constellation-triggered reaction hooks.
- Check Ineffa summon behavior, EM sharing, Lunar base-bonus support, and shielded
  skill assumptions.
- Check Lunar-aware weapons and artifacts for trigger conditions and stat routing.

Acceptance criteria:

- `FlinsParty2` has an explicit accuracy inventory.
- Lunar-specific high-impact gaps are listed as implementation tasks.
- Existing Lunar regressions still pass.

Implementation status:

- Audited `FlinsParty2`, Flins, Ineffa, Columbina, Sucrose, Lunar reaction
  routing, Lunar damage strategy, and the equipped Lunar weapons/artifacts.
- Confirmed implemented baseline coverage for auto-detected
  `ASCENDANT_GLEAM`, Lunar-Charged Thundercloud ownership/ticks/crit routing,
  Lunar-Bloom Dew conversion hooks, Lunar-Crystallize Moondrift/Harmony cadence,
  Columbina Gravity/Interference/Dew consumption, Ineffa Overclock/Birgitta
  hooks, and Flins Thunderous Symphony state.
- Found one high-impact timing issue: Flins standard burst delayed hits returned
  `currentTime + delay` from each timer poll, so their scheduled time could drift
  forward as combat time advanced.
- Remaining simplifications to track later: defensive shield HP is logged but
  not consumed by enemy attacks, some custom character effects use deterministic
  stand-ins for random or field-position behavior, and the sample can still fire
  Flins burst with insufficient energy in the scripted rotation while warning.

Test cases to add or update:

- Add tests only when the audit identifies missing coverage or ambiguous Lunar
  behavior.
- Normal path: Lunar-Charged, Lunar-Bloom, and Lunar-Crystallize trigger under
  valid Moonsign and party conditions with expected counters or state changes.
- Error/invalid path: Lunar conversion does not occur without the required
  party-state source, wrong aura pair, expired domain, missing Thundercloud, or
  non-Lunar trigger condition.
- Boundary values: Thundercloud refresh at expiry edge, Dew cooldown/cap edges,
  Moondrift every-third-trigger cadence, Gravity threshold crossing, and domain
  start/end timing.
- Unit-level logic: direct checks for Lunar damage stat routing, state counters,
  Dew/Gravity accumulation, and conversion eligibility helpers.
- Integration path: a representative shortened FlinsParty2 rotation that verifies
  Lunar reaction events, custom character hooks, item triggers, and report
  logging together.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew FlinsParty2`

### Phase 4: FlinsParty2 High-Impact Fixes - Done

Why fourth:

Lunar mechanics affect custom team behavior and RL training distribution. Fixes
should be isolated and measured against `FlinsParty2`.

Candidate tasks:

- Fix Thundercloud conditional hit logic if character hooks do not match runtime
  Lunar state.
- Fix Columbina Gravity or Dew behavior if counters, cooldowns, or domain
  conditions are incomplete.
- Fix Lunar item trigger conditions if they still depend on display labels rather
  than typed reaction metadata.
- Fix Lunar damage stat routing if a direct Lunar action or reaction uses the
  wrong bonus bucket.
- Add focused regression coverage for each fixed custom mechanic.

Acceptance criteria:

- Each implemented fix has deterministic regression coverage.
- `FlinsParty2` still runs and any DPS delta is explained in final notes.
- Standard reaction behavior remains unchanged unless intentionally corrected.

Implementation status:

- Fixed Flins standard burst delayed hit scheduling by capturing absolute target
  times when the timer events are created.
- Added a boundary regression that verifies the no-Thundercloud standard burst
  delayed hits occur at 2.5s, 2.8s, and 3.6s instead of drifting with current
  simulator time.
- Removed leftover `FlinsParty2` debug stat dumping from party setup so sample
  validation no longer writes `stats_dump.txt`.
- `FlinsParty2` still runs at 17,044,468 total damage / 246,664 DPS after the
  fix and debug-output cleanup.

Test cases to add or update:

- Add at least one deterministic regression for every implemented Lunar fix.
- Normal path: corrected Lunar hooks trigger with the expected owner, stat bucket,
  counter change, and damage or resource result.
- Error/invalid path: hooks do not trigger for standard reactions, wrong Lunar
  subtype, off-window domain state, missing Moonsign condition, or display-label
  mismatches.
- Boundary values: exact Thundercloud tick/expiry times, Dew caps, Gravity caps,
  Moondrift third-trigger boundary, and Lunar buff expiry on the hit frame.
- Unit-level logic: isolate conversion eligibility, Lunar stat selection, state
  increment/reset behavior, and reaction-listener filters.
- Integration path: a compact FlinsParty2 script that exercises the changed
  Lunar mechanic with reactions, character hooks, item hooks, damage attribution,
  and logging.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew FlinsParty2`

### Phase 5: RL Party and Capability Profile Audit - Done

Why fifth:

RL parties consume the same simulator but add observation, action-mask, reward,
role-profile, and report-generation contracts. Accuracy changes can silently
shift training behavior even when tensor shapes do not change.

Target files:

- `src/java/mechanics/rl/`
- `src/java/mechanics/rl/bridge/`
- `src/java/sample/ServeRLJava.java`
- `src/java/sample/BenchmarkRLJava.java`
- `src/java/sample/ProfileCharacterCapabilities.java`
- `src/python/rl/`
- `config/capability_profiles/`

Tasks:

- Verify registered RL parties match the audited simulator parties.
- Check capability profiles for stale role expectations after character or item
  fixes.
- Check action masks for newly adjusted cooldown, burst, or form-state behavior.
- Check observation and privileged-observation fields that expose reaction or
  role state.
- Check report generation paths for deterministic evaluation.
- Regenerate capability profiles only when role behavior changes materially.

Acceptance criteria:

- RL registry entries remain the single source of party selection.
- Capability profiles are either confirmed current or regenerated with a clear
  reason.
- Python evaluation still derives party names and summaries from service
  metadata.

Implementation status:

- Audited `RLPartyRegistry`, `RLPartySpec`, the FlinsParty2 and RaidenParty RL
  factories, multi-party episode selection, action masks, observation encoding,
  privileged-state encoding, Java benchmark entry point, and Python evaluation
  metadata usage.
- Confirmed registered RL parties still match the audited simulator parties:
  `FlinsParty2` and `RaidenParty`.
- No observation layout, privileged observation layout, action ID, action-mask,
  reward, or binary protocol change was required.
- Regenerated `config/capability_profiles/profiles.json` because Phase 2 and
  Phase 4 changed simulator behavior used by role profiling. Flins role values
  changed materially after fixed burst scheduling, and Raiden values changed
  slightly after Resolve crediting was corrected.
- Confirmed Java rollout benchmark succeeds with the regenerated profile file.
  Python checkpoint evaluation was not run because no usable checkpoint was
  required or validated as part of this pass.

Test cases to add or update:

- Add or update tests only when RL-facing behavior or metadata changes.
- Normal path: registered parties can be selected by name, default, and all; the
  service exposes consistent observation/action/role metadata; valid actions step
  successfully.
- Error/invalid path: unknown party selections, invalid action IDs, masked
  actions, unavailable checkpoints, and service/client shape mismatches fail
  clearly.
- Boundary values: one-party versus multi-party catalogs, one environment versus
  vectorized environments, episode termination at the configured time limit, and
  action-mask behavior around cooldown or energy thresholds.
- Unit-level logic: action mask generation, reward calculation, role-alignment
  scoring, capability profile parsing, and protocol encode/decode helpers.
- Integration path: Java rollout benchmark plus Python evaluation against a
  running service when a usable checkpoint exists.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew BenchmarkRLJava`
- `./gradlew ProfileCapabilities` when role profiles change
- `python3 src/python/rl/evaluate_policy.py --mode both --summary` when a usable
  checkpoint exists

### Phase 6: Documentation and Accuracy Notes - Done

Why last:

After the benchmark parties are audited and fixed, the project should clearly
state which mechanics are exact, simplified, or intentionally deferred.

Target files:

- `README.md`
- `TASKS.md`
- relevant package `AGENTS.md`
- optional docs or generated reports only when explicitly requested

Tasks:

- Update this plan with completed items and remaining gaps.
- Document known simplifications for RaidenParty and FlinsParty2.
- Keep validation commands and expected sample outputs current.
- Avoid editing generated `docs/` unless the task is specifically about
  published documentation.

Acceptance criteria:

- Future implementation work can start from a current gap list instead of
  re-auditing the whole project.
- Handoff notes clearly state which commands were run and which were skipped.

Implementation status:

- Updated this plan with completed phase notes, known simplifications, validation
  commands, and sample DPS baselines.
- Updated `README.md` with current accuracy notes for the audited benchmark
  parties and the latest validation baseline.
- Did not edit generated `docs/` output.

Test cases to add or update:

- Documentation-only changes do not require new tests unless they document a new
  expected behavior that is not covered.
- When docs record a newly fixed mechanic, make sure the corresponding phase
  added or updated normal, invalid, boundary, unit-level, or integration coverage
  as appropriate.
- If validation commands, report paths, or RL workflows are changed, run the
  smallest command that proves the documented workflow still works.

## Cross-Cutting Rules

### Testing

- Use `./gradlew ReactionRegressionTest` for reaction, aura, ICD, Lunar,
  character-hook, weapon-hook, artifact-hook, and formula regressions.
- Use `./gradlew RaidenParty` for conventional sample regressions.
- Use `./gradlew FlinsParty2` for Lunar sample regressions.
- Use RL commands only when RL-facing behavior, profile data, or protocol
  contracts change.
- For each implementation phase, decide whether new tests are required before
  editing. At minimum, cover the normal path and an invalid/no-trigger path for a
  changed mechanic.
- Add boundary-value tests when timing, counters, caps, cooldowns, energy
  thresholds, ICD windows, or buff expiry are part of the behavior.
- Add unit-level tests for major helper logic when it can be isolated without a
  full simulator run.
- Add representative integration tests or executable sample checks when multiple
  systems interact, such as character hooks plus reactions plus item passives.
- If a phase intentionally skips new tests, record why in the final handoff.

### Implementation Style

- Prefer minimal local changes in the affected character, item, runtime, or
  mechanic package.
- Add helper methods only when they remove real duplication or clarify a
  mechanic.
- Keep runtime logic typed: use `CharacterId`, `CharacterActionKey`, `BuffId`,
  `ReactionResult.Kind`, and `ReactionResult.LunarType` instead of display
  labels.
- Preserve current single-target assumptions unless a target party needs a more
  detailed offensive abstraction.

### Reporting

- New or corrected mechanics should appear clearly in logs or HTML reports when
  they affect damage attribution, reaction labels, aura state, or timed effects.
- Presentation labels belong in sample, log, report, and data-boundary code; they
  should not become control-flow keys in simulator internals.

### RL Compatibility

- Treat damage, timing, action masks, observation layout, privileged observation
  layout, party ordering, and role profiles as RL-relevant contracts.
- Existing checkpoints may become behaviorally incompatible after simulator
  accuracy fixes even when tensor shapes remain unchanged.

## Deferred Systems

These systems remain deferred unless a current benchmark party's offensive kit
depends on them:

- defensive shield absorption and player damage intake
- enemy attacks, stagger, movement, and survival pressure
- multi-target geometry and positioning
- exploration-specific systems
- full open-world status interactions that do not affect offensive output in the
  current single-target simulator
