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

Generic simulation and RL launch paths backed by shared party definitions are
now implemented. Party-specific sample wrappers and party-specific RL simulator
factories have been removed for the migrated parties.

## Scope

The reaction core, aura/ICD detail passes, Bloom-family behavior, Quicken-family
behavior, Lunar reaction handling, and current regression coverage are treated as
the baseline implementation.

This pass removes party-specific simulation and RL entry-point duplication. A new
party should require only one party-specific definition containing its mechanical
setup and rotation. Generic simulation and RL launchers should handle optimizer
execution, reports, Gradle task dispatch, RL registry/spec wiring, and episode
factory creation.

Out of scope for this pass:

- new report asset downloads or changes to the existing `face.png` files
- introducing a frontend framework, build step, or server-side report viewer
- editing generated `docs/` or committed report output unless explicitly
  requested
- changing simulator combat mechanics, damage formulas, or rotation timing
- changing RL observation/action tensor shapes unless a validation test proves it
  is already required
- adding new party variants beyond the existing sample/RL parties
- requiring party-specific wrapper classes for `./gradlew <PartyName>`

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

## Implementation Order: Generic Party Definitions and Launchers

Status:

- Implemented.
- Phase 1-5 are complete.
- Requirement: adding a party should require one party-specific definition, not
  separate simulation and RL entry-point classes.

Scope:

- Remove duplicated party setup and party-specific runner code from `sample` and
  `mechanics.rl`.
- Create one shared source of truth per party for:
  - party order
  - enemy level
  - character constructors
  - weapon choices
  - artifact sets
  - artifact main stats
  - substat priorities
  - default ER targets
  - resonance and moonsign application
  - scripted rotation
- Move sample execution concerns to a generic simulation runner:
  - optimization
  - final simulation
  - stats recording
  - report generation
  - docs report generation
- Move RL concerns to generic RL catalog/factory code:
  - party spec construction
  - episode factory wiring
  - logging disabled
  - party selection for training/evaluation/benchmark/profile flows
- Update Gradle dynamic execution so `./gradlew <PartyName>` launches the generic
  runner with `<PartyName>` instead of requiring `sample.<PartyName>` wrapper
  classes.

Out of scope for this pass:

- Changing sample rotation scripts.
- Changing optimizer algorithms.
- Changing RL reward, action mask, observation, privileged observation, or
  rollout protocol layout.
- Adding a frontend framework, build step, or server-side report viewer.
- Editing generated `docs/` or committed report output unless explicitly requested.

Definitions:

- Party definition:
  The only party-specific Java class required for a party. It owns party name,
  party order, enemy level, equipment, artifact configuration, ER/default build
  assumptions, optimizer targets, simulator construction, and scripted rotation.
- Party catalog:
  A shared registry of party definitions used by both generic sample execution
  and RL party specs.
- Generic simulation runner:
  A single runnable entry point that accepts a party name, looks up the
  definition, runs optimization and the scripted rotation, then emits reports.
- Generic sample launcher:
  The Java main class used by Gradle's dynamic rule. It resolves `./gradlew
  <PartyName>` through the party catalog first, then optionally falls back to
  legacy sample classes for non-party utilities.
- Generic RL factory:
  A party-name-driven RL factory that creates `RLPartySpec` and fresh
  `CombatSimulator` instances from the same party definitions.

Design direction:

- Introduce shared party definition/catalog classes outside `mechanics.rl`.
- Replace party-specific sample and RL factory responsibilities with generic
  launchers backed by the catalog.
- Prefer typed `CharacterId`, `StatType`, `Weapon`, and `ArtifactSet`
  construction over display-name branching.
- Keep existing `./gradlew FlinsParty2` and `./gradlew RaidenParty` commands
  working without party-specific wrapper classes.
- Keep legacy non-party sample execution available through the generic launcher
  where practical.

Implementation summary:

- Added `simulation.party.PartyDefinition`, `AbstractPartyDefinition`, and
  `PartyCatalog`.
- Added concrete party definitions for `RaidenParty`, `FlinsParty`, and
  `FlinsParty2`.
- Added `sample.RunPartySimulation` and `sample.SampleLauncher` so dynamic
  Gradle party tasks run through one generic entry point.
- Added `mechanics.rl.GenericRLSimulatorFactory` and changed
  `RLPartyRegistry` to register RL-enabled catalog parties.
- Removed party-specific sample wrappers and party-specific RL simulator
  factories for the migrated parties.
- Added `sample.PartyCatalogRegressionTest` to cover catalog lookup, fixed setup
  parity, RL registry membership, and fresh simulator creation.

### Phase 1: Audit Party-Specific Responsibilities - Done

Why first:

Before introducing generic launchers, identify which responsibilities are truly
party-specific and which can be moved into shared runtime code.

Target files:

- `src/java/sample/RaidenParty.java`
- `src/java/sample/FlinsParty.java`
- `src/java/sample/FlinsParty2.java`
- `src/java/mechanics/rl/RaidenPartyRLSimulatorFactory.java`
- `src/java/mechanics/rl/FlinsParty2RLSimulatorFactory.java`
- `src/java/mechanics/rl/RLPartyRegistry.java`
- `src/java/mechanics/rl/RLPartySpec.java`
- `src/java/mechanics/rl/SinglePartyRLEpisodeFactory.java`
- `src/java/mechanics/rl/MultiPartyRLSimulatorFactory.java`
- `build.gradle`
- `src/java/sample/BenchmarkRLJava.java`
- `src/java/sample/ProfileCharacterCapabilities.java`
- `src/java/sample/ServeRLJava.java`

Tasks:

- Identify all parties with sample entry points and/or RL registrations.
- For each party, classify code as:
  - party-specific mechanics/setup
  - party-specific rotation
  - generic simulation orchestration
  - generic reporting
  - generic RL wiring
  - legacy CLI compatibility
- Compare sample and RL setup for registered parties:
  - enemy level
  - party order
  - character constructors
  - weapons
  - artifact sets
  - artifact main stats
  - substat priorities
  - minimum ER targets
  - manual roll behavior
  - resonance and moonsign calls
  - logging defaults
- Decide which value wins when there is a mismatch. The normal sample is the
  source of truth unless the mismatch is explicitly identified as a sample bug.
- Identify non-party sample utilities that must keep class-name execution.
- Define the Gradle dispatch behavior:
  - party name -> generic simulation runner
  - legacy sample class -> reflective fallback or explicit task
  - unknown name -> error with available party/sample names

Acceptance criteria:

- Every party-specific setup/rotation concern has a planned home in a party
  definition.
- Every generic sample/RL concern has a planned shared class.
- Gradle dynamic dispatch behavior is explicit before implementation.
- Mismatches between existing sample and RL setup have a planned resolution per
  party.

Test cases to add or update:

- No production test changes required in this phase.
- Optional: add a temporary local comparison helper only if it clarifies expected
  parity values.

Verification:

- `./gradlew classes`

### Phase 2: Introduce PartyDefinition and PartyCatalog - Done

Why second:

The party definition API and catalog must exist before generic simulation or RL
launchers can use them.

Target files:

- new shared package, likely `src/java/simulation/party/` or
  `src/java/sample/party/`
- shared party definition interfaces/classes, for example:
  - `PartyDefinition`
  - `PartyBuildConfig`
  - `PartyCatalog`
  - one concrete definition per party such as `FlinsParty2Definition`
- sample files only if package visibility requires small access adjustments

Tasks:

- Define a small typed API for party definitions:
  - party name
  - party order
  - enemy level
  - optimization targets
  - default ER targets for fixed builds
  - simulator creation from ER targets and roll maps
  - scripted rotation execution
- Add concrete definitions for all parties that currently have both sample and
  RL paths.
- Add definitions for sample-only parties when they should also be executable by
  the generic runner.
- Add simulator creation methods per definition:
  - optimized/manual-roll setup used by sample optimization
  - fixed/default setup used by RL and parity tests
- Keep report generation, CLI behavior, and RL registry types out of party
  definitions.
- Keep RL registry types out of shared definitions.

Acceptance criteria:

- The catalog resolves party definitions by name.
- Each definition can construct a simulator and execute its rotation.
- Definitions have no dependency on RL registry classes or Gradle/task concepts.
- Definitions expose enough data for both generic sample execution and generic
  RL creation without duplicating artifact configuration.
- Existing behavior is unchanged because callers are not migrated yet, or
  migrated only through equivalent wrappers.

Test cases to add or update:

- Unit-level parity helper: every shared party definition returns a stable party
  order.
- Normal path: every shared party definition creates the expected number of party
  members with expected enemy level and initial moonsign state.
- Boundary path: null ER target and null roll maps produce valid default setup
  for every shared definition.
- Major logic unit test: catalog lookup succeeds for registered party names and
  fails with a useful message for unknown names.

Verification:

- `./gradlew classes`

### Phase 3: Add Generic Simulation Runner and Gradle Dispatch - Done

Why third:

Once party definitions exist, sample execution can become generic. Gradle should
dispatch party names to this generic runner without creating party-specific
wrapper classes.

Target files:

- new generic runner, for example `src/java/sample/RunPartySimulation.java`
- new launcher, for example `src/java/sample/SampleLauncher.java`
- `build.gradle`
- `src/java/sample/RaidenParty.java`
- `src/java/sample/FlinsParty.java` if it should share a definition now
- `src/java/sample/FlinsParty2.java`
- shared party definition classes
- `src/java/sample/ReportRegressionTest.java` only if report assumptions need
  adjustment

Tasks:

- Add a generic simulation runner that:
  - accepts a party name
  - resolves `PartyDefinition`
  - runs optimizer with `definition.optimizationTargets()`
  - creates final simulator with optimized ER targets and rolls
  - starts `StatsRecorder`
  - calls `definition.executeRotation(sim)`
  - prints report
  - generates `output/simulation_report.html`
  - generates docs report when appropriate
- Add a generic launcher used by Gradle dynamic tasks:
  - first resolve task name as a party
  - otherwise fall back to legacy `sample.<TaskName>` if needed
  - otherwise print available party names and fail
- Update `build.gradle` dynamic rule so unknown task names run
  `sample.SampleLauncher` with the task name as an argument.
- Remove or stop relying on party-specific sample wrappers after generic runner
  parity is proven.

Acceptance criteria:

- `./gradlew FlinsParty2` and `./gradlew RaidenParty` run through the generic
  launcher without requiring party-specific wrapper classes.
- Generic runner preserves existing output/report behavior.
- Legacy non-party sample tasks still run or have explicit replacement tasks.
- Party-specific sample files no longer own mechanical setup once migrated.

Test cases to add or update:

- Normal integration: `RunPartySimulation` generates the same party character,
  weapon, and artifact details as the previous party-specific sample.
- Error path: unknown party name fails with available party names.
- Boundary path: legacy sample fallback still works for non-party sample classes
  that remain.
- Representative integration: generated HTML still contains character, weapon,
  and artifact details for report-generating parties.

Verification:

- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
- `./gradlew ReportRegressionTest`
- `./gradlew classes`

### Phase 4: Replace Party-Specific RL Factories with Generic RL Factory - Done

Why fourth:

After generic simulation uses party definitions, RL can use the same catalog
instead of party-specific RL factory classes.

Target files:

- new generic RL factory, for example `src/java/mechanics/rl/GenericRLSimulatorFactory.java`
- `src/java/mechanics/rl/RaidenPartyRLSimulatorFactory.java`
- `src/java/mechanics/rl/FlinsParty2RLSimulatorFactory.java`
- shared party definition classes
- `src/java/mechanics/rl/RLPartyRegistry.java`
- `src/java/mechanics/rl/RLPartySpec.java`
- `src/java/mechanics/rl/SinglePartyRLEpisodeFactory.java`
- `src/java/mechanics/rl/MultiPartyRLSimulatorFactory.java`
- `src/java/sample/ReactionRegressionTest.java` or a new regression entry point
  if parity checks fit better there

Tasks:

- Add generic RL simulator creation from `PartyDefinition`.
- Make `RLPartyRegistry` derive registered `RLPartySpec`s from `PartyCatalog`
  instead of hardcoding party-specific factories.
- Keep `sim.setLoggingEnabled(false)` as generic RL-only setup.
- Remove or deprecate party-specific RL simulator factories after parity is
  covered.
- Ensure party selection strings used by training/evaluation/benchmark/profile
  still resolve to the same party names.

Acceptance criteria:

- RL simulator setup is created from the same `PartyDefinition` used by generic
  sample execution.
- Party-specific RL simulator factories are no longer needed for registered
  parties.
- RL logging remains disabled.
- RL registry still exposes the same party names and default selection.

Test cases to add or update:

- Normal path: generic simulation setup and generic RL setup produce the same
  enemy level, party order, character names, weapon names, artifact set names,
  and artifact roll maps when using the same fixed setup.
- Error path: generic RL factory creates a valid simulator with logging disabled.
- Boundary path: repeated generic RL factory calls create fresh simulator
  instances and do not share mutable character state.
- Major logic unit test: all RL registry specs come from party catalog
  definitions.

Verification:

- `./gradlew BenchmarkRLJava`
- `./gradlew ProfileCapabilities` when capability profile regeneration is needed
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
- `./gradlew classes`

### Phase 5: Remove Duplication and Document Party Addition Workflow - Done

Why last:

The refactor changes ownership boundaries, so the final phase must make future
divergence hard to reintroduce and document how to add a party with only a
definition file/class.

Target files:

- `TASKS.md`
- `build.gradle`
- generic launcher/runner classes
- generic RL factory/registry classes
- `src/java/sample/RaidenParty.java`
- `src/java/mechanics/rl/RaidenPartyRLSimulatorFactory.java`
- `src/java/sample/FlinsParty2.java`
- `src/java/mechanics/rl/FlinsParty2RLSimulatorFactory.java`
- shared party definition classes
- `src/java/sample/AGENTS.md`
- `src/java/mechanics/rl/AGENTS.md`
- `README.md`

Tasks:

- Remove or reduce party-specific sample/RL classes once generic launch paths are
  proven.
- Add or update documentation stating that adding a party requires only a party
  definition and catalog registration.
- Document the dynamic Gradle behavior:
  - `./gradlew <PartyName>` runs the generic simulation runner
  - non-party sample classes use fallback or explicit tasks
- Document which differences are allowed:
  - generic simulation may enable logging/reporting
  - generic RL may disable logging
  - both must share mechanical setup and rotation source
- Run parity checks and relevant sample/RL commands for all registered parties.
- Record any intentional sample-vs-RL differences in the final handoff.

Acceptance criteria:

- New party addition does not require a new simulation entry point or a new RL
  simulator factory.
- Future edits to mechanical party setup have one obvious definition to change
  per party.
- Sample entry points and RL factories no longer duplicate party setup details.
- Automated parity coverage protects enemy level, party order, equipment,
  artifacts, and roll maps for all registered parties.
- Existing `./gradlew RaidenParty` and `./gradlew FlinsParty2` commands still
  work.
- Report generation and RL registry still work.

Manual inspection checklist:

- `build.gradle` dynamic rule points to the generic sample launcher.
- Generic simulation runner contains orchestration only: optimize, run, report.
- Generic RL factory contains RL wiring only: spec, supplier, episode factory,
  logging disabled.
- Party definitions contain the only party-specific mechanical setup and
  rotation.
- HTML report still shows the expected characters, weapons, and artifact sets.

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
- `./gradlew BenchmarkRLJava`
- `./gradlew ProfileCapabilities` when capability profile regeneration is needed
- Manual browser inspection of `output/simulation_report.html`

Status note:

- Implemented. `ProfileCapabilities` is intentionally not required for routine
  verification because it is expensive on local machines and rewrites capability
  profile data.

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
