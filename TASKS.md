# Accuracy Implementation Plan

## Current Status

The simulator accuracy audit and high-impact fixes for `RaidenParty`,
`FlinsParty2`, and RL-facing metadata are complete.

The previous HTML report detail and UI upgrade is complete. Character, weapon,
and artifact local image assets are now used in the generated report where
available.

The HTML report's reaction damage presentation has been unified. The report now
uses one `Elemental Reaction Damage` view backed by separately recorded elemental
reaction damage, while reaction-labeled direct hits remain in Timeline and
Action Damage only.

Continuous aura decay in simulator mechanics and aura visualization has been
implemented across Phases 1-5 below. Enemy auras now decay continuously over
time, and reaction checks, aura consumption, logs, RL observations, snapshot
restore, and the HTML Aura Timeline all read the same current-time-aware value.

## Scope

The reaction core, aura/ICD detail passes, Bloom-family behavior, Quicken-family
behavior, Lunar reaction handling, and current regression coverage are treated as
the baseline implementation.

This pass upgrades enemy aura from an expiry-only state to a time-aware remaining
gauge model. Aura units should decay continuously over time, and reaction checks,
aura consumption, logs, RL observations, and HTML visualization should all read
the same current-time-aware value.

Out of scope for this pass:

- new report asset downloads or changes to the existing `face.png` files
- introducing a frontend framework, build step, or server-side report viewer
- editing generated `docs/` or committed report output unless explicitly
  requested
- changing elemental reaction formulas beyond the aura amount available at each
  timestamp
- adding multi-target aura behavior or enemy-specific aura rules

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

## Implementation Order: Continuous Aura Decay

Status:

- Implemented (Phases 1-5 complete).
- Validation: `./gradlew ReactionRegressionTest` and `./gradlew ReportRegressionTest`
  pass with added continuous-decay, snapshot, and Aura Timeline coverage.
  `RaidenParty` (1,362,938) and `FlinsParty2` (15,892,535) sample totals are
  unchanged from before the change, as these tight rotations refresh auras before
  natural decay removes them.

Scope:

- Make runtime aura units depend on current simulator time.
- Use decayed aura units for reaction eligibility, reaction consumption, aura
  snapshots, RL observations, and visual reports.
- Keep the existing simplified aura duration formula initially unless a phase
  explicitly changes only its representation.
- Update Aura Timeline so it displays continuous decay instead of event-only
  staircase state.

Out of scope for this pass:

- Reworking elemental reaction damage formulas.
- Reworking ICD rules.
- Implementing multi-target aura gauges.
- Modeling game-specific hidden gauge tax beyond the current simplified duration
  and consumption rules.
- Adding a frontend framework, build step, or server-side report viewer.
- Editing generated `docs/` or committed report output unless explicitly requested.

Definitions:

- Applied aura units:
  The gauge units at application time, after any immediate reaction consumption.
- Current aura units:
  The remaining units at a queried simulator time after continuous natural decay
  and discrete reaction consumption are both applied.
- Aura expiry:
  The time at which current aura units reach zero through natural decay.
- Discrete consumption:
  Aura reduction caused by reaction handling, such as Vaporize, Swirl,
  Electro-Charged ticks, Burning maintenance, or other existing simulator
  mechanics.
- Snapshot time:
  The exact simulator time used when recording logs, reports, stats, or RL
  observations. A snapshot must use current aura units at that time.

Design direction:

- Keep `Enemy` as the owner of target aura state.
- Avoid passing display labels into mechanic decisions.
- Prefer an explicit current-time parameter for aura reads where correctness
  depends on simulation time.
- Preserve no-argument compatibility only where a caller is known to operate
  immediately after `updateAuras(currentTime)`, or replace it during migration.
- Make report Aura Timeline derive from the same mechanics-facing aura model,
  not an unrelated display-only approximation.

### Phase 1: Audit Aura Read/Write Call Sites

Why first:

The current implementation mixes time-aware expiry with non-time-aware aura unit
reads. Before changing `Enemy`, identify every call site that reads, writes, or
serializes aura state so the behavior change is intentional.

Target files:

- `src/java/model/entity/Enemy.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/runtime/SimulationClock.java`
- `src/java/simulation/runtime/SwitchManager.java`
- `src/java/mechanics/rl/ObservationEncoder.java`
- `src/java/mechanics/rl/PrivilegedStateEncoder.java`
- `src/java/visualization/*`

Tasks:

- List all uses of `setAura`, `reduceAura`, `getAuraUnits`, `getAuraMap`,
  `getActiveAuras`, `getPrimaryAura`, and `updateAuras`.
- Classify each use as:
  - mechanic decision
  - reaction aftermath or scheduled tick
  - report/log snapshot
  - RL observation
  - persistence/snapshot restore
  - sample/debug display
- Identify callers that currently rely on `getAuraUnits(element)` returning the
  originally stored units instead of a decayed value.
- Decide the minimum API surface for current-time-aware reads.

Acceptance criteria:

- All aura read/write call sites are accounted for.
- The implementation phases know which call sites must be migrated and which can
  remain compatibility wrappers.
- Risky call sites in RL and report generation are explicitly listed.

Test cases to add or update:

- No code changes required unless a call-site inventory test is useful.

Verification:

- `./gradlew classes`

### Phase 2: Implement Time-Aware Aura State

Why second:

All downstream mechanics should consume one source of truth for current aura
units. This phase changes the model while keeping the public behavior as close as
possible except for natural continuous decay.

Target files:

- `src/java/model/entity/Enemy.java`
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Store enough data in each aura state to compute remaining units at any time:
  application time, initial units, current units after discrete reductions, and
  expiry behavior.
- Add current-time-aware APIs such as:
  - `getAuraUnits(element, currentTime)`
  - `getAuraMap(currentTime)`
  - `getActiveAuras(currentTime)`
  - `getPrimaryAura(currentTime)`
- Make `updateAuras(currentTime)` remove naturally expired auras after computing
  current units.
- Make `reduceAura(element, amount, currentTime)` consume the decayed current
  value, then continue natural decay from the remaining value.
- Keep no-argument methods only as compatibility wrappers, and document their
  intended use or migrate them away in later phases.
- Preserve snapshot restore behavior for simulator rollback.

Acceptance criteria:

- A 1U aura naturally reaches zero at its configured expiry instead of staying at
  1U until deletion.
- Discrete aura consumption after partial natural decay uses the decayed value.
- Snapshot restore can still round-trip active aura state.
- Existing callers compile after API migration stubs are in place.

Test cases to add or update:

- Normal path: applying 1U at `t=0` returns a positive lower value at mid-duration
  and `0` at expiry.
- Error path: zero or negative aura application removes the aura and never
  returns a negative current value.
- Boundary values: query before application time, exactly at application time,
  exactly at expiry, and just after expiry.
- Major logic unit test: reduce a partially decayed aura and verify the new
  remaining units and subsequent expiry.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew classes`

### Phase 3: Migrate Reaction Mechanics to Current-Time Aura Reads

Why third:

Once `Enemy` can answer current aura values, reaction decisions and aura
consumption must use those values. This is the behavioral core of the change.

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/model/character/Sucrose.java`
- other character, weapon, or artifact hooks discovered in Phase 1
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Replace mechanic-facing no-argument aura reads with current-time-aware reads.
- Update reaction loops so active aura sets are based on decayed current units.
- Update all `reduceAura` calls to pass the current simulator time.
- Ensure scheduled reaction ticks such as Electro-Charged, Burning, Lunar
  reactions, and related aftermath consume or inspect decayed aura values.
- Update aura-dependent passives or absorption checks to use current-time aura.
- Preserve existing ICD behavior.

Acceptance criteria:

- Reactions no longer trigger from an aura that has naturally decayed to zero.
- Reactions still trigger correctly while a partially decayed aura remains above
  zero.
- Reaction consumption never drives stored aura below zero.
- Existing sample rotations still complete.

Test cases to add or update:

- Normal integration: apply aura, wait within duration, then trigger a reaction.
- Error integration: apply aura, wait past expiry, then verify no reaction occurs.
- Boundary integration: trigger just before and exactly at expiry.
- Major logic unit test: discrete consumption after partial decay removes or
  preserves aura according to remaining units.
- Representative integration: verify Sucrose absorption/passive checks use
  decayed aura state.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew FlinsParty2`
- `./gradlew RaidenParty`

### Phase 4: Migrate Logging, Snapshots, Reports, and RL Observations

Why fourth:

After mechanic decisions use decayed aura, every observer must report the same
state. Otherwise the simulator may be correct while HTML or RL observations show
stale aura values.

Target files:

- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/runtime/VisualLoggerSink.java`
- `src/java/simulation/runtime/SwitchManager.java`
- `src/java/mechanics/rl/ObservationEncoder.java`
- `src/java/mechanics/rl/PrivilegedStateEncoder.java`
- `src/java/visualization/ReportDataBuilder.java`
- `src/java/visualization/ReportHtmlRenderer.java`
- `src/java/sample/ReportRegressionTest.java`

Tasks:

- Make combat logs and timeline records snapshot `enemyAura` using current-time
  aura values.
- Make simulator snapshots and restores preserve enough aura state for rollback
  without flattening natural decay incorrectly.
- Make RL observations use decayed aura units at the observation time.
- Update Aura Timeline data generation to include sampled/interpolated current
  aura units, not only event-time values.
- Change Aura Timeline line rendering away from staircase mode when displaying
  continuous decay.
- Keep report downsampling safe for dense aura series.

Acceptance criteria:

- Timeline aura bars and Aura Timeline agree at the same timestamps.
- Aura Timeline visibly slopes down over time for naturally decaying aura.
- RL observation values decrease over time for an untouched aura.
- Snapshot restore preserves future decay behavior.

Test cases to add or update:

- Normal path: report data includes a midpoint aura value below initial units.
- Error path: empty/no-aura simulations still generate valid HTML.
- Boundary values: report includes `0` at or after aura expiry.
- Major logic unit test: RL observation aura value decreases between two sampled
  times without reactions.
- Renderer test: Aura Timeline no longer uses stepped display for continuous
  aura data.

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`

### Phase 5: Sample Regression and Balance Review

Why last:

Continuous aura decay changes reaction availability, so sample totals and
optimizer behavior may shift. This phase validates that shifts are explainable
and not caused by stale call sites.

Target files:

- `TASKS.md`
- `src/java/sample/RaidenParty.java`
- `src/java/sample/FlinsParty2.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/sample/ReportRegressionTest.java`
- `README.md` if aura semantics are documented there

Tasks:

- Run conventional and Lunar sample parties and compare major changes in total
  damage, reaction counts, and aura timeline shape.
- Update tests only where the old expectation depended on expiry-only aura
  behavior.
- Add documentation for the simplified continuous aura model:
  - current duration formula
  - linear decay assumption
  - discrete reaction consumption
  - known differences from exact game internals if any remain
- Manually inspect generated reports for aura readability and chart consistency.
- Confirm browser console has no JavaScript errors.

Acceptance criteria:

- Sample output changes are understood and documented in handoff notes.
- Regression tests cover continuous decay, expiry boundaries, consumption, and
  report rendering.
- Aura Timeline communicates continuous decay clearly without misleading
  staircase presentation.
- Existing energy, buff, damage, and reaction report sections still render.

Manual inspection checklist:

- Aura Timeline slopes down between application and expiry.
- Aura Timeline reaches zero at expiry.
- Timeline aura bars match nearby chart values.
- Reaction events do not appear after aura expiry unless a new aura was applied.
- Energy, rolling DPS, action damage, buff uptime, and reaction damage charts
  still render.
- Narrow viewport does not break chart containers.

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
- Manual browser inspection of `output/simulation_report.html`

Status note:

- Implemented. Continuous aura decay is live across mechanics, logging,
  snapshots, RL observations, and the HTML Aura Timeline.

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
