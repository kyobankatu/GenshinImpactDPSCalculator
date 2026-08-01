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

The current autonomous session is simulator-only. Python RL training and the
Java RL bridge are excluded; the retained NCCL/DDP plan below is paused until a
future explicit user request.

The Xingqiu orbital Hydro application correction is complete. Orbital Rain
Swords now use their sourced 2.25-second application cadence independently from
the damaging Raincutter sword-wave ICD.

The deterministic damage-proc correction is complete. Weapon and artifact
damage hooks dispatch once through `DamageCalculator`, and `RaidenParty` uses
common seeded Skyward Spine draws for reproducible optimizer evaluation.

The Sucrose elemental-application correction is now active. Its sourced
contract and pre-fix `FlinsParty2` baseline are recorded; implementation and
sample acceptance remain.

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

## Implementation Order: Xingqiu Orbital Hydro Application

Status:

- Implemented.
- Phases 1-3 are complete.
- Requirement: Xingqiu's non-damaging orbital Rain Swords must apply Hydro on
  their sourced 2.25-second contact cadence without inheriting the separate
  Raincutter sword-wave three-hit ICD rule.

Scope:

- Keep damaging Raincutter sword waves as Burst damage with their existing
  standard ICD and C6 2-3-5 pattern.
- Keep orbital Rain Swords as zero-damage Hydro application.
- Make the orbital's 2.25-second cadence the single owner of its application
  interval instead of layering a generic standard ICD over it.
- Record the single-target simulator's continuous-contact assumption explicitly.

Out of scope for this pass:

- Player damage reduction, interruption resistance, healing, or Rain Sword
  shattering.
- Multi-target geometry, melee-range checks, movement, and field positioning.
- Changes to Raincutter wave damage multipliers, C6 sequencing, or energy gain.
- Python RL training, the Java RL bridge, tensor layouts, or capability profiles.
- Generated `docs/` and `output/` reports.

Definitions:

- Orbital Rain Sword pulse:
  Xingqiu's zero-damage, 1U Hydro contact application while Rain Swords orbit the
  active character. Its sourced ICD is 2.25 seconds.
- Raincutter sword wave:
  The separate damaging Burst attacks triggered by Normal Attack animations;
  these retain standard hit/time ICD behavior.

### Phase 1: Establish the Sourced Contract and Baseline - Done

Why first:

The zero-damage orbital and damaging sword wave use similar names. Their source
contracts and current simulator ownership must be separated before changing ICD
behavior.

Target files:

- `TASKS.md`
- `BACKLOG.md`
- `README.md`
- `src/java/model/character/Xingqiu.java`
- `config/characters/Xingqiu/Xingqiu_Multipliers.csv`

Tasks:

- Confirm the configured level-12 Raincutter sword multiplier and existing
  damaging sword-wave path.
- Trace the orbital event interval, gauge, ICD tag, and zero-damage path.
- Record KQM's maintained character reference and evidence entry for the
  2.25-second orbital ICD.
- Capture the unchanged pre-fix `RaidenParty` total and Xingqiu contribution.

Acceptance criteria:

- The plan distinguishes orbital pulses from damaging Raincutter sword waves.
- Stable source URLs and the accessed date are recorded in `BACKLOG.md`.
- The pre-fix run records total damage, DPS, and non-zero Xingqiu contribution.

Test cases to add or update:

- No production test change in this audit phase; Phase 2 adds executable
  contract coverage after the intended ownership boundary is fixed.

Verification:

- `./gradlew RaidenParty`

### Phase 2: Make Orbital Cadence the Application Boundary - Done

Why second:

The sourced distinction from Phase 1 permits a local character-owned fix without
adding Xingqiu-specific branches to the generic ICD manager.

Target files:

- `src/java/model/character/Xingqiu.java`
- `src/java/model/type/ICDTag.java`
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Replace the approximate 2.2-second orbital interval with a named 2.25-second
  constant.
- Make each scheduled orbital pulse eligible to apply Hydro, using the event
  cadence rather than standard three-hit/time ICD to throttle it.
- Preserve zero direct damage, 1U Hydro, and separate typed identity for the
  orbital action.
- Leave Raincutter sword waves on their current standard ICD and damage path.

Acceptance criteria:

- Orbital actions occur at exactly 2.25-second intervals through the active
  duration and stop after expiry.
- Every orbital action is zero damage and 1U Hydro, and is not blocked by the
  standard three-hit rule.
- Raincutter sword waves still use the configured level-12 multiplier, Burst
  damage type, and standard `Xingqiu_Raincutter` ICD.
- Generic ICD, event, and action-runtime classes gain no Xingqiu-specific logic.

Test cases to add or update:

- Normal path: capture consecutive orbital action times and assert 2.25-second
  spacing, zero multiplier, 1U gauge, and unrestricted application ICD.
- Boundary path: assert the last valid pulse is inside the 18-second Raincutter
  window and no pulse occurs after event expiry.
- Separation path: assert a generated Raincutter sword remains damaging Burst
  damage with standard `Xingqiu_Raincutter` ICD.
- Abnormal/no-trigger path: advance a simulator without casting Raincutter and
  assert that no orbital action appears.

Verification:

- `./gradlew build`
- `./gradlew ReactionRegressionTest`

### Phase 3: Validate the Party Delta and Close the Accuracy Note - Done

Why last:

The benchmark and documentation can be updated only after the corrected cadence
and its focused regression pass.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run `RaidenParty` repeatedly and record the total, DPS, Xingqiu contribution,
  and the delta from the Phase 1 run.
- Describe orbital pulses as zero-damage Hydro contact application and damaging
  Raincutter waves as a separate modeled source.
- Update the audited numeric baseline only from observed current output, noting
  any remaining Skyward Spine nondeterminism until B-003 is complete.
- Mark B-001 done with source provenance and the verified simulator decision.

Acceptance criteria:

- `RaidenParty` completes with non-zero Xingqiu direct damage and corrected
  orbital application cadence.
- README and the verification skill agree on the observed current baseline.
- The ledger records the implementation decision, source URLs, accessed date,
  and remaining continuous-contact simplification.

Test cases to add or update:

- No additional production test is required; Phase 2 owns the mechanic contract
  and this phase performs the catalog-backed sample integration check.

Verification:

- `./gradlew RaidenParty`
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

## Implementation Order: Deterministic Damage Proc Evaluation

Status:

- Implemented.
- Phases 1-4 are complete.
- Requirement: one resolved hit must dispatch each damage-trigger hook exactly
  once, and `RaidenParty` optimizer candidates must evaluate Skyward Spine under
  the same reproducible random sequence.

Scope:

- Give `DamageCalculator` facade sole ownership of post-calculation weapon and
  artifact damage-hook dispatch.
- Keep standard and Lunar damage strategies responsible only for formulas.
- Inject Skyward Spine's proc draw behind a `DoubleSupplier` while preserving a
  stochastic default constructor for general simulations.
- Give `RaidenPartyDefinition` a fixed per-simulator seed so every optimizer
  candidate receives common random numbers.
- Use explicit optimization-target iteration order for the benchmark party.

Out of scope for this pass:

- Changing Skyward Spine's documented 50% chance, 2-second cooldown, 40% ATK
  damage, or eligible Normal/Charged action types.
- Converting random procs to expected-value state machines.
- Seeding unrelated weapons, Columbina effects, or RL policy sampling.
- Adding weapon/passive state to `SimulatorSnapshot`; RL snapshot consumers are
  excluded from the current session.
- Python RL, Java RL bridge, capability profiles, generated reports, and HPC
  jobs.

Definitions:

- Damage hook:
  A post-final-damage callback through `DamageTriggeredWeaponEffect` or
  `DamageTriggeredArtifactEffect` for one resolved `AttackAction`.
- Proc draw source:
  A `DoubleSupplier` returning values in the random draw domain used by Skyward
  Spine's chance gate.
- Common random numbers:
  Reinitializing every optimizer candidate's weapon with the same seed so
  candidate differences reflect builds rather than different random streams.

### Phase 1: Audit Hook and Randomness Ownership - Done

Why first:

Determinism cannot be repaired by seeding alone while one hit may consume two
draws and dispatch artifact effects twice.

Target files:

- `src/java/mechanics/formula/StandardDamageStrategy.java`
- `src/java/mechanics/formula/LunarDamageStrategy.java`
- `src/java/mechanics/formula/DamageCalculator.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/model/weapon/SkywardSpine.java`
- `src/java/model/weapon/FavoniusCodex.java`
- `src/java/mechanics/optimization/IterativeSimulator.java`
- `src/java/simulation/party/RaidenPartyDefinition.java`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Trace every production `DamageTriggeredWeaponEffect` and
  `DamageTriggeredArtifactEffect` dispatch site.
- Confirm standard and Lunar paths both dispatch before the resolver dispatches
  the same hit again.
- Reproduce `RaidenParty` variance across isolated runs and identify the random
  sources on that execution path.
- Record snapshot and iteration-order risks without expanding into excluded RL
  state work.

Acceptance criteria:

- The plan names the single intended hook owner and every duplicate call site.
- The 50% Skyward Spine chance is shown to become 75% when a failed first draw
  receives a duplicate same-hit draw.
- At least three pre-fix runs record optimizer iteration count, Vacuum Blade
  count, total damage, and DPS variance.

Test cases to add or update:

- No production tests in the audit phase; Phase 2 adds hook-count coverage and
  Phase 3 adds deterministic draw boundaries.

Verification:

- `./gradlew build`
- `./gradlew ReactionRegressionTest`
- three isolated `./gradlew RaidenParty` runs

### Phase 2: Dispatch Each Damage Hook Once - Done

Why second:

The number and ordering of proc draws must be correct before a deterministic
source can make optimizer comparisons meaningful.

Target files:

- `src/java/mechanics/formula/StandardDamageStrategy.java`
- `src/java/mechanics/formula/LunarDamageStrategy.java`
- `src/java/mechanics/formula/DamageCalculator.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/mechanics/formula/AGENTS.md`

Tasks:

- Remove post-damage side effects from both formula strategies.
- Make `DamageCalculator` invoke weapon and artifact hooks once after the chosen
  strategy returns final damage.
- Remove duplicate resolver dispatch while preserving damage recording, direct
  damage capture, normal-attack energy, and combat logging order.
- Update package guidance to state the facade's single-dispatch contract.

Acceptance criteria:

- One standard hit invokes one weapon hook and one artifact hook.
- One Lunar hit invokes one weapon hook and one artifact hook.
- Direct `DamageCalculator` callers still receive the same single hook dispatch.
- Neither formula strategies nor resolver contain independent hook fan-out.

Test cases to add or update:

- Normal path: counting weapon and artifact capabilities each observe one
  standard hit.
- Alternate path: the same capabilities each observe one Lunar hit.
- Boundary path: a direct `DamageCalculator` call dispatches once without a
  resolver and an `OTHER` follow-up does not duplicate the parent hit.
- Abnormal path: characters without damage-trigger capabilities resolve damage
  normally.

Verification:

- `./gradlew build`
- `./gradlew ReactionRegressionTest`

### Phase 3: Inject and Seed Skyward Spine Proc Draws - Done

Why third:

After Phase 2 fixes draw count, an injected source can make chance boundaries
testable and optimizer candidates comparable.

Target files:

- `src/java/model/weapon/SkywardSpine.java`
- `src/java/simulation/party/RaidenPartyDefinition.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/model/weapon/AGENTS.md`

Tasks:

- Add a `DoubleSupplier` constructor and have the no-argument constructor retain
  stochastic `Math.random` behavior.
- Reject a null draw source before weapon use.
- Use one fixed seed for each newly created `RaidenParty` simulator, including
  optimizer candidates and final sample simulation.
- Replace unordered optimization-target construction with party-order-preserving
  iteration.

Acceptance criteria:

- Draw `0.499999` triggers and draw `0.5` does not.
- A successful proc blocks draws through 1.999 seconds and permits a new draw at
  exactly 2.0 seconds.
- A failed draw does not start cooldown, and `OTHER` follow-ups consume no draw.
- Two separately created benchmark simulators receive identical proc sequences.
- Default `new SkywardSpine()` remains source-compatible and stochastic.

Test cases to add or update:

- Normal path: identical injected sequences produce identical proc totals.
- Invalid path: null supplier construction fails immediately.
- Probability boundary: `0.499999` succeeds and `0.5` fails.
- Cooldown boundaries: 1.999 blocked and 2.0 eligible.
- Recursion guard: Vacuum Blade's `OTHER` action does not consume another draw.

Verification:

- `./gradlew build`
- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`

### Phase 4: Prove Optimizer Reproducibility and Refresh Baselines - Done

Why last:

Only the combined hook and seeded-proc changes can establish a trustworthy
numeric benchmark.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run `RaidenParty` at least three times in fresh Gradle invocations.
- Compare optimizer outer iterations, ER targets, roll allocations, Vacuum Blade
  count, total damage, and DPS byte-for-byte for the relevant summary lines.
- Update README and verification-skill baselines to the observed corrected value.
- Mark B-003 done and record remaining stochastic defaults outside the benchmark
  definition.

Acceptance criteria:

- Three fresh runs have identical optimizer decisions and final summaries.
- The audited baseline reflects single hook dispatch and the seeded 50% proc.
- README no longer describes `RaidenParty` itself as nondeterministic.
- Agent assets and all routed preflight checks pass.

Test cases to add or update:

- No further production test; Phases 2-3 own the contracts and this phase is the
  full optimizer/sample acceptance check.

Verification:

- three fresh `./gradlew RaidenParty` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

## Implementation Order: Sucrose No-ICD Elemental Application

Status:

- In progress.
- Phases 1-2 are complete; Phase 3 remains.
- Requirement: Sucrose's Skill and every Burst damage pulse must apply their
  documented 1U element without ICD, while the zero-damage Burst cast must not
  apply Anemo.

Scope:

- Make `Sucrose` explicitly define gauge and ICD for Skill, Burst cast, Burst
  Anemo DoT, and absorbed-element damage.
- Keep those action contracts inside the character model that creates them.
- Add focused executable regression coverage for the action metadata and the
  no-ICD behavior across consecutive Burst ticks.
- Re-run `FlinsParty2` and refresh its audited numeric baseline if the corrected
  reaction cadence changes the result.

Out of scope for this pass:

- Changing generic `AttackAction` defaults or `ICDManager` behavior.
- Correcting Flins, Ineffa, or other characters that still use
  `ICDTag.None`; each needs separate mechanic evidence.
- Changing Sucrose animation timing, absorption priority, damage multipliers,
  particle generation, buffs, or constellation behavior.
- Changing reaction formulas, aura decay, rotations, optimizer algorithms, RL
  contracts, or generated `docs/` output.

Design boundaries:

- `model.character.Sucrose` owns the game-specific gauge and ICD metadata.
- `CombatActionResolver` and `ICDManager` continue consuming typed action
  metadata without Sucrose-specific branches (open/closed and single
  responsibility).
- `ReactionRegressionTest` observes emitted actions and reaction behavior
  through public simulator listeners rather than exposing new production test
  APIs (dependency inversion and interface segregation).

### Phase 1: Record the Sourced Contract and Pre-Fix Baseline - Done

Why first:

The exact gauge and ICD rules must be established before changing an accuracy
behavior that affects reaction count and sample damage.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the maintained KQM TCL Sucrose attack table accessed 2026-08-02: Skill
  `1U / ICD None`; Burst Anemo DoT `1U / ICD None`; Burst absorbed damage
  `1U / ICD None`.
- Record KQM's v2.3 Evidence Vault observation that the Anemo and absorbed
  Burst damage occur simultaneously and produce one combined tick reaction
  sequence.
- Corroborate the no-ICD classification against the current Genshin Impact Wiki
  character-data search result and classify the simulator decision as `adopt`.
- Preserve the current `FlinsParty2` baseline and identify blocked Sucrose Burst
  pulses before editing.

Acceptance criteria:

- Stable source URLs, access date, classification, simulator decision, and
  bounded regression design are recorded.
- The pre-fix sample remains 15,892,535 damage / 233,028 DPS.
- The pre-fix log identifies three Sucrose Burst DoT pulses rejected by the
  default Standard/None ICD state.

Test cases to add or update:

- No production test in this documentation phase; Phase 2 adds the executable
  contract before source correction is committed.

Verification:

- `./gradlew FlinsParty2`
- `python scripts/preflight.py --run`

### Phase 2: Encode Sucrose Gauge and ICD Metadata - Done

Why second:

With the evidence fixed, the character can explicitly emit correct actions and
the regression can prove both application and no-application paths.

Target files:

- `src/java/model/character/Sucrose.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Set Sucrose Skill to `ICDType.None` with 1U Anemo application.
- Make the zero-damage Burst cast use zero gauge so it cannot cause an early
  Swirl or consume aura.
- Set each Burst Anemo DoT and absorbed-element hit to `ICDType.None` with 1U.
- Keep typed tags descriptive even though no-ICD actions do not share state.
- Add a focused actual-character regression through simulator action and
  reaction listeners.

Acceptance criteria:

- The Burst cast emits zero gauge and no reaction.
- Consecutive Burst ticks each emit 1U/no-ICD metadata and can each trigger the
  expected reaction when aura is available.
- Skill remains 1U and is explicitly no-ICD.
- No generic runtime, formula, or reaction policy changes.

Test cases to add or update:

- Normal: Skill action metadata is 1U, `ICDType.None`, and the Skill tag.
- Normal: Burst Anemo and absorbed-element actions are both 1U and
  `ICDType.None`.
- No-trigger: the zero-damage Burst cast has 0U and does not notify a reaction.
- Boundary: two consecutive modeled Burst ticks are not suppressed by a shared
  standard ICD counter.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the FlinsParty2 Delta and Close the Accuracy Note

Why last:

The optimizer/sample baseline can be refreshed only after the corrected action
contract and focused regression are committed.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `FlinsParty2` invocations and compare ER targets, optimizer
  rolls, Sucrose reaction activity, total damage, and DPS.
- Confirm no Sucrose Burst pulse is logged as ICD-blocked.
- Update the audited README and verification-skill baseline to the corrected
  value.
- Mark B-011 and this plan complete while preserving unrelated
  positive-gauge `ICDTag.None` findings for later sourced work.

Acceptance criteria:

- Two fresh runs produce the same corrected summary or any remaining declared
  random component is reported explicitly.
- The expected Sucrose no-ICD applications are visible and no Burst cast
  application is present.
- README, verification gate, backlog, and plan agree on the accepted result.
- Agent assets and all routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns the mechanic contract and this phase
  is the optimizer/sample integration acceptance check.

Verification:

- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

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

## NCCL/DDP Distributed RL Training Plan

Status:

- Paused by user on 2026-08-01 and excluded from the current simulator-only
  autonomous session.
- Resume only after a new explicit RL request; none of its phases are active for
  work discovery or queue ordering.

### Objective and Target Topology

Enable data-parallel recurrent PPO training on one `rtx6000-ada2_2` allocation
without changing Java combat semantics or the Java/Python binary protocol. This
resource provides two NVIDIA RTX 6000 Ada GPUs (48 GB each), 96 CPU threads,
and 384 GB host memory. The initial production topology is therefore:

- two PyTorch learner processes launched by `torchrun`, one process per GPU
- `nccl` as the distributed process group backend
- one Java rollout service per learner rank, each bound to a distinct localhost
  port and configured with a bounded share of Java worker threads
- rank-local rollout collection and PPO loss calculation; DDP performs gradient
  all-reduce during `backward()`
- rank 0 owns checkpoints, CSV/W&B output, and user-facing summaries; all ranks
  fail together on a fatal error

The first implementation targets exactly one node and two GPUs. Multi-node
support is deferred until the single-node contract, recovery behavior, and
performance characteristics are proven.

Non-goals:

- replacing the Java rollout service, action mask, observations, reward, or
  binary protocol
- model parallelism, FSDP, parameter-server architectures, or PPO algorithm
  changes unrelated to distribution
- requiring NCCL/GPU availability for current local CPU or single-GPU commands
- Java-service-to-Java-service communication

### Distributed Ownership and SOLID Boundaries

- `distributed_runtime.py` owns process-group lifecycle, rank discovery,
  collective helpers, and rank-aware logging/checkpoint permissions. It must
  not know PPO or socket protocol details (single responsibility).
- `distributed_rollout.py` maps one DDP rank to exactly one Java endpoint and
  validates service metadata. It depends on the existing rollout-client
  interface rather than a concrete socket implementation (dependency inversion).
- `train_recurrent_ppo.py` remains the composition root and training loop. It
  delegates distributed concerns rather than branching across the loop
  (open/closed and single responsibility).
- Checkpointing, metrics, and launch validation are small injectable helpers,
  so they can be tested without a GPU or live Java service (interface
  segregation and dependency inversion).
- Java remains responsible only for simulation and vectorized rollouts. A Java
  endpoint is private to one learner rank; runner and Vine snapshot state never
  cross rank boundaries.

Every phase must preserve `python3 src/python/rl/train_recurrent_ppo.py` as the
single-process development path. New source belongs under `src/python/rl/`;
generated checkpoints, logs, reports, and scheduler output do not belong in
commits.

### Phase 0: Record Baseline and Validate the Allocation

Target files:

- `TASKS.md`
- `README.md`
- optional new `src/python/rl/benchmark_training.py` only if current benchmarks
  cannot record learner-versus-rollout timings

Requirements:

- Document `rtx6000-ada2_2`: 2 GPUs, 96 CPU threads, 384 GB RAM, 48 GB/GPU.
- Define a reproducible one-GPU baseline with fixed seed, party catalog, update
  count, rollout service, and evaluation cadence.
- Capture update wall time, rollout env-steps/s, optimizer time, GPU memory, CPU
  utilization, Java worker count, and deterministic evaluation.
- Start with two Java services at 40 workers each, leaving CPU headroom for the
  Python learners/system threads; tune only after measurement.
- Preflight `torch.cuda.is_available()`, GPU count, CUDA/PyTorch versions, and
  `torch.distributed.is_nccl_available()`.

Normal tests:

- One-GPU short training writes a valid checkpoint and deterministic summary.
- `benchmark_rollout.py` against the matching Java service reports non-zero,
  stable env-steps/s.

Abnormal tests:

- Fewer than two visible GPUs is an actionable DDP preflight error while the
  normal single-process command still works.
- Java worker totals above the allocation budget are rejected or warned before
  training begins.
- An unreachable rollout endpoint fails before checkpoint/log creation.

Acceptance criteria:

- An exact baseline record with commands, versions, allocation, and metrics.
- A comparator exists for later correctness and speedup decisions.

### Phase 1: Introduce a Testable Distributed Runtime Abstraction

Target files:

- new `src/python/rl/distributed_runtime.py`
- new `src/python/rl/tests/test_distributed_runtime.py`
- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/AGENTS.md`
- `README.md`

Requirements:

- Add immutable `DistributedContext`: `enabled`, `rank`, `local_rank`,
  `world_size`, `device`, `is_primary`.
- Add one lifecycle API that reads `torchrun` variables, validates them,
  initializes `torch.distributed` with `nccl`, pins the local CUDA device, and
  always destroys the process group in `finally` cleanup.
- Add `--distributed off|auto|required`: `off` preserves current behavior,
  `auto` enables only under `torchrun`, and `required` rejects direct execution.
- Hide collective operations behind helpers such as scalar mean/sum, barrier,
  and failure propagation. Other modules must not import `torch.distributed`.
- Derive per-rank random seeds deterministically from user seed and rank; store
  the derivation in checkpoint metadata.

Normal tests:

- Unit test constructs a disabled context without process-group initialization.
- `torchrun --standalone --nproc_per_node=2` assigns devices 0 and 1; both ranks
  pass a barrier and exit cleanly.
- A DDP smoke program confirms scalar all-reduce agrees on both ranks.

Abnormal tests:

- `--distributed required` outside `torchrun` identifies missing variables.
- `LOCAL_RANK` outside the visible CUDA range fails before model creation.
- Partial `RANK`/`WORLD_SIZE` environment fails rather than silently mixing
  distributed and local operation.
- Initialization failure cleans up partial process-group state with rank-aware
  diagnostics.

Acceptance criteria:

- Direct single-process execution is behaviorally unchanged.
- DDP lifecycle contains no PPO, Java protocol, W&B, or checkpoint code.
- Disabled, valid two-rank, and invalid-launch paths are covered.

### Phase 2: Define Rank-Local Rollout Topology and Preflight Validation

Target files:

- new `src/python/rl/distributed_rollout.py`
- new `src/python/rl/tests/test_distributed_rollout.py`
- `src/python/rl/rollout_service_client.py`
- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/benchmark_rollout.py`
- `src/python/rl/AGENTS.md`
- `README.md`

Requirements:

- Add `--rank-endpoints host:port,...` for DDP. Count must equal `WORLD_SIZE`;
  rank `r` owns only endpoint `r`.
- Keep current `--endpoints` fan-out semantics unchanged outside DDP; do not
  reinterpret a fan-out client as a rank-local DDP client.
- Add `RankLocalRolloutClientFactory`, creating exactly one
  `RolloutServiceClient` for the current rank behind the existing client API.
- Before creating a runner, validate cross-rank protocol version, observation,
  action/privileged size, feature layout, and ordered party catalog.
- Require `envs % world_size == 0` initially and report `local_envs`; do not
  silently alter global batch size.
- Each rank creates, steps, releases snapshots for, and closes only its local
  Java runner.

Normal tests:

- Unit test maps two endpoints deterministically to ranks 0 and 1.
- Two-service smoke run proves distinct runner/snapshot ownership.
- Matching endpoint metadata permits equal local environment counts.

Abnormal tests:

- Endpoint count mismatch, duplicate rank endpoint, malformed endpoint, and
  connection timeout fail before runner creation.
- Metadata or party-catalog mismatch identifies the differing field.
- `envs % world_size != 0` fails before the first PPO update.

Acceptance criteria:

- One DDP rank owns one Java service without a protocol-version change.
- Existing local/multi-endpoint benchmark behavior remains unchanged.
- Endpoint selection is unit-testable without GPUs.

### Phase 3: Make Recurrent PPO Correct Under DDP

Target files:

- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/recurrent_ppo.py`
- new `src/python/rl/distributed_metrics.py`
- new `src/python/rl/tests/test_distributed_training.py`
- `src/python/rl/tests/test_recurrent_ppo.py` or the closest focused test module

Requirements:

- Wrap the policy with `torch.nn.parallel.DistributedDataParallel` only after
  movement to rank-local CUDA. Provide an unwrapped-policy accessor for metadata,
  checkpointing, and evaluation.
- Retain rank-local rollout buffers/hidden states; never gather trajectories
  merely to train.
- Normalize advantages with global count, sum, and squared-sum collectives so
  all ranks use identical normalization, including defined zero-variance logic.
- Require identical PPO minibatch/update counts on every rank. Validate local
  sequence chunks before backward to prevent a DDP deadlock.
- Reduce metrics with count-aware sums. Rank 0 reports global loss, throughput,
  invalid-action rate, and per-party values.
- Give RND, SIL, Vine PPO, role metrics, and evaluation an explicit policy:
  DDP-correct collectives or early validation rejection. Initial scope supports
  plain PPO and RND; gate Vine/SIL until implemented and tested.
- Run periodic evaluation only on rank 0 after a barrier; peers wait at the same
  synchronization point.

Normal tests:

- Deterministic CPU `gloo` test verifies two ranks obtain identical globally
  normalized advantages and reduced metrics.
- Two-GPU NCCL smoke training completes two updates and shows matching final
  model parameter hashes across ranks.
- Single-process training retains tensor shapes, action masks, and checkpoint
  schema.

Abnormal tests:

- Unequal local minibatch availability fails on every rank before DDP backward.
- NaN/Inf loss, gradient, advantage, or metric triggers coordinated termination
  with offending rank/tensor category logged.
- Unsupported Vine/SIL distributed mode fails at argument validation.
- Rollout/optimization failure on one rank reaches peers without an indefinite
  NCCL wait.

Acceptance criteria:

- DDP synchronizes gradients and all ranks have the same optimizer-step count.
- Global metrics are sample-count-weighted, not averages of rank averages.
- The model implementation remains free of distributed-only business logic.

### Phase 4: Add Rank-Safe Checkpointing, Resumption, and Observability

Target files:

- new `src/python/rl/checkpointing.py`
- new `src/python/rl/training_metrics.py`
- new `src/python/rl/tests/test_checkpointing.py`
- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/evaluate_policy.py`
- `src/python/rl/AGENTS.md`
- `README.md`

Requirements:

- Only rank 0 writes checkpoints, CSV files, W&B events, and reports. Other
  ranks log rank-local diagnostics to stdout with rank prefixes only.
- Save atomically through a temporary file and rename after successful
  `torch.save`; synchronize only after the final path exists.
- Add format version, world size, global/local batch size, seed derivation,
  policy metadata, and service/party compatibility metadata. Legacy
  single-process checkpoints remain loadable when fields are absent.
- Store unwrapped policy and optimizer state. Initially reject resume with a
  different world size unless optimizer-state resharding is added deliberately.
- W&B init/finalization are rank-0-only. Log allocation, rank count,
  local/global envs, and reduced performance metrics.
- Bounded shutdown closes local clients/runners, releases snapshots, finalizes
  the process group, and preserves the last complete checkpoint.

Normal tests:

- Two-rank smoke training produces one complete checkpoint and one CSV row per
  global update.
- Same-world-size resume validates model, optimizer, topology, and metadata.
- `evaluate_policy.py` loads legacy and DDP-produced checkpoints without DDP.

Abnormal tests:

- Simulated rank-0 write failure leaves no corrupted final checkpoint and all
  ranks exit clearly.
- Incompatible world size, observation layout, party catalog, or policy config
  fails before rollout creation.
- Interruption closes both local Java runners; a non-primary rank never writes
  outputs or starts another W&B run.

Acceptance criteria:

- Output directories have no concurrent-writer corruption.
- Resumed runs state their topology/compatibility assumptions.
- Evaluation remains a single-process checkpoint consumer.

### Phase 5: Provide a Reproducible Two-GPU Launch and Failure Diagnostics

Target files:

- new `src/python/rl/launch_distributed_training.py`
- new `src/python/rl/tests/test_distributed_launch.py`
- `README.md`
- `src/python/rl/AGENTS.md`
- `TASKS.md`

Requirements:

- Add a local single-node launcher that validates inputs, starts two Java rollout
  services on explicit ports, and invokes
  `torchrun --standalone --nproc_per_node=2` with `--distributed required` and
  matching `--rank-endpoints`.
- Keep it scheduler-neutral: no submission syntax or external network dependency.
- Default to 40 Java workers/service; expose a validated override that reserves
  configurable CPU headroom.
- Add health checks, bounded startup timeout, child logs under `output/`, signal
  forwarding, ordered shutdown, and non-zero exit propagation.
- Print resolved commands, GPU/rank mapping, ports, local/global envs, and output
  paths before training.
- Document helper and equivalent manual commands for `rtx6000-ada2_2`.

Normal tests:

- Dry-run renders two Java commands and one torchrun command without processes.
- Two-GPU smoke starts two ports, completes one or two updates, writes one
  checkpoint, and cleans up children.
- Documented manual launch creates the same topology as the helper.

Abnormal tests:

- Occupied port, Java startup/missing classes, missing CUDA/NCCL, or learner
  non-zero exit terminates siblings and returns non-zero.
- Invalid worker allocation, duplicate ports, metadata timeout, and stale PID
  state fail with actionable diagnostics.
- A second signal during shutdown does not skip cleanup or hang NCCL.

Acceptance criteria:

- One documented command starts the supported `rtx6000-ada2_2` two-GPU job.
- The helper requires no Java changes or scheduler-specific tracked files.

### Phase 6: Performance and Correctness Acceptance

Target files:

- `README.md`
- `TASKS.md`
- new or updated `src/python/rl/benchmark_training.py`
- new `src/python/rl/tests/test_training_configuration.py`

Requirements:

- Run the fixed Phase 0 workload on one GPU and two GPUs with equal global batch
  semantics, seed, party selection, and evaluation cadence.
- Report rollout env-steps/s, optimizer updates/s, wall time, GPU memory, Java
  CPU utilization, NCCL communication/wait where available, and deterministic/
  stochastic per-party evaluation.
- Define acceptance before the final run: two-GPU throughput must improve over
  baseline without increased invalid-action rate or material deterministic-eval
  regression outside a documented seed tolerance.
- Document Java worker/local-environment tuning; do not leave hidden defaults.
- Record unsupported features and scaling limits such as small-PPO-batch
  all-reduce overhead or Java rollout bottlenecks.

Normal tests:

- Two-GPU benchmark repeats successfully with synchronized ranks, valid
  checkpoint/evaluation, matching parameters, and clean Java-service exit.
- `./gradlew build`, local single-process training, and evaluation remain
  runnable.

Abnormal tests:

- Below-baseline throughput, GPU OOM, NCCL timeout, or evaluation regression
  marks the run non-accepted and retains diagnostics.
- GPU-memory/CPU-capacity-invalid configurations fail in preflight, not after a
  long rollout.

Acceptance criteria:

- A repeatable accepted `rtx6000-ada2_2` configuration and observed performance
  are documented.
- Correctness, failure behavior, and cleanup are acceptance requirements.

### Phase-Gated Verification Matrix

- Phase 0: one-GPU baseline, `benchmark_rollout.py`, deterministic evaluation.
- Phase 1: focused runtime tests and two-rank NCCL barrier smoke.
- Phase 2: focused topology tests and two-endpoint metadata smoke.
- Phase 3: focused DDP/PPO tests, two-update `torchrun` smoke, and
  single-process training smoke.
- Phase 4: checkpoint tests, same-world-size resume, and single-process
  evaluation of a DDP checkpoint.
- Phase 5: launcher dry run and orchestrated local two-GPU smoke.
- Phase 6: benchmark comparison, final deterministic/stochastic evaluation, and
  `./gradlew build` because the rollout service is an integration boundary.

Do not run `ProfileCapabilities` for this plan unless party definitions or
capability inputs change; it rewrites generated capability-profile data and is
unrelated to NCCL transport.
