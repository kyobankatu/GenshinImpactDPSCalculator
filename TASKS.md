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

The Sucrose elemental-application correction is complete. Skill and every Burst
damage pulse use their sourced 1U/no-ICD contract, while the zero-damage Burst
cast no longer applies Anemo.

The Ineffa Overclock correction is complete. The direct Lunar-Charged follow-up
retains its damage while applying 0U and bypassing elemental ICD state.

The current simulator-only accuracy queue through B-020 is complete. Xiangling's
Guoba now follows its sourced 1U/no-ICD contract, while Pyronado's distinct
application metadata remains unchanged.

The B-021 correction is complete. Xingqiu's two Fatal Rainscreen strikes now
each use their sourced 1U/no-ICD contract instead of sharing standard Skill
ICD.

The B-022 correction is complete. Bennett's Press Skill now uses 2U/no ICD and
his Burst retains 2U without entering a Burst ICD group.

The B-023 correction is complete. Raiden's Skill and Burst cast metadata and
Musou Isshin Normal/Charged shared ICD group now match their sourced contracts.

The B-024 correction is complete. Recasting Raiden's Skill now cancels the
previous Eye periodic event, leaving one refreshed stream.

The B-025 correction is complete. Guoba's periodic duration now stops after its
four sourced flame hits.

The B-026 correction is complete. Ineffa's Skill and Burst now refresh one
Birgitta summon whose 20-second lifetime contains exactly ten two-second
Discharge attacks.

The B-027 correction is complete. ER calibration now replays requested Burst
intervals with energy cap, carry, and cyclic refill instead of allowing later
particle income to hide an earlier deficit.

The B-028 correction is complete. Raiden's Eye now triggers from resolved party
damage instead of attacking autonomously on a fixed timer.

The B-031 Dragon's Bane correction is complete. Its enemy-aura condition is
evaluated per hit before reaction consumption instead of being folded
unconditionally into the wielder's stat sheet and snapshots.

The B-032 `FlinsParty` random-stream correction is complete. Independent seeded
Favonius and Moondrift streams keep ER calibration and the final rotation on
the same stochastic scenario without changing generic randomness. The stable
trace exposed a separate ER-feasibility defect recorded as B-033.

The B-033 ER-feasibility correction is complete. Artifact generation rejects
unmet `minER` contracts, `FlinsParty` requests only the three Sucrose Bursts its
fixed loadout and rotation energy can legally sustain, and catalog parties
include equipped artifact-set static stats in allocation. Fatal sample
configuration errors propagate to the Gradle caller.

The B-035 Wandering Evenstar correction is complete. Its R5 owner and ally ATK
bonuses share one effective-EM snapshot, activate after the sourced 64-frame
delay, and refresh every ten seconds while the owner is off-field.

The B-038 party-size energy correction is complete. Off-field particle
recipients in two- and three-character parties use the sourced 0.8 and 0.7
multipliers while the established four-character 0.6 contract remains
unchanged.

The B-039 Impetuous Winds correction is complete. Its existing 5% cooldown
reduction stat shortens Skill and Burst cooldown state at cast time and remains
exact across multi-charge scheduling and simulator snapshot restore.

The B-040 Sacrificial Sword correction is complete. Its R5 Composed passive
uses deterministic-testable Skill-damage draws, resets only the applicable
Skill cooldown, and enforces the sourced sixteen-second weapon cooldown.

The B-041 Viridescent Venerer correction is in progress. Same-element 40% RES
shred applications will refresh one ten-second typed debuff instead of stacking,
and only an on-field equipping owner who triggered the Swirl may apply it.

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

- Implemented.
- Phases 1-3 are complete.
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

### Phase 3: Accept the FlinsParty2 Delta and Close the Accuracy Note - Done

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

## Implementation Order: Ineffa Overclock Zero-Gauge Damage

Status:

- Implemented.
- Phases 1-3 are complete.
- Requirement: Ineffa's Overclocking Circuit follow-up must deal direct
  Lunar-Charged damage without applying Electro or entering an ICD group.

Scope:

- Explicitly encode the Overclock follow-up as 0U/no-ICD in `Ineffa`.
- Add focused actual-character regression coverage for Thundercloud inactive
  and active paths.
- Re-run `FlinsParty2` and accept the resulting aura/reaction/DPS delta.

Out of scope for this pass:

- Changing Birgitta Discharge gauge, ICD, cadence, duration, particles, or
  damage.
- Changing Ineffa's shield, Burst, constellations, Lunar damage formula, or
  Thundercloud creation.
- Correcting Flins or other remaining `ICDTag.None` actions without their own
  sourced contract.
- Changing runtime ICD policy, reaction formulas, optimizer behavior, RL
  contracts, or generated `docs/` output.

Design boundaries:

- `model.character.Ineffa` owns the passive follow-up metadata and condition.
- Existing `CombatSimulator` Thundercloud state and `AttackAction` typed fields
  are consumed unchanged; no character branch enters runtime policy.
- `ReactionRegressionTest` verifies emitted actions through public listeners
  and owns no production helper API.

### Phase 1: Record the Overclock Contract and Pre-Fix Behavior - Done

Why first:

Direct Lunar-Charged damage and elemental Electro damage use different
application rules, so the exact 0U contract must be sourced before editing.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the current Genshin Impact Wiki Overclocking Circuit advanced-property
  row, accessed 2026-08-02: `Extra Lunar-Charged`, 0U, no ICD.
- Corroborate against the Japanese Genshin Wiki explanation that the passive
  follow-up is direct Lunar-Charged damage and does not apply Electro.
- Classify the simulator decision as `adopt` and define a bounded actual-
  character regression.
- Record the current full-sample Overclock count and incorrect ICD behavior.

Acceptance criteria:

- Stable source URLs, version/scope, access date, classification, and test
  design are recorded.
- The current `FlinsParty2` baseline is 15,562,611 damage / 228,191 DPS.
- The pre-fix log identifies 40 Overclock follow-ups: 35 standard-ICD blocks
  and five unintended Electro applications.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds the executable
  contract with the source correction.

Verification:

- `./gradlew FlinsParty2`
- `python scripts/preflight.py --run`

### Phase 2: Make Overclock Explicitly Zero-Gauge - Done

Why second:

Once the source contract is fixed, the character-local action metadata can be
corrected and proven independently from the full party.

Target files:

- `src/java/model/character/Ineffa.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Set Overclock to `ICDType.None`, a typed neutral tag, and 0U.
- Preserve its Lunar-Charged classification, 65% ATK motion value, timing, and
  damage attribution.
- Add actual Ineffa tests that advance Birgitta across a Thundercloud boundary
  and inspect the emitted follow-up.

Acceptance criteria:

- No Overclock action is emitted while Thundercloud is inactive.
- Active Thundercloud emits one Overclock follow-up per Birgitta tick with 0U
  and no ICD.
- The follow-up still deals positive direct Lunar-Charged damage and cannot
  create an elemental reaction from an existing aura.
- No generic runtime or formula code changes.

Test cases to add or update:

- No-trigger: Birgitta fires without Overclock when Thundercloud is inactive.
- Normal: active Thundercloud emits Overclock with Lunar-Charged metadata and
  positive damage.
- Invalid application: a Hydro aura remains free of an Overclock-triggered
  Lunar-Charged reaction because the follow-up applies 0U.
- Boundary: an expired Thundercloud does not emit the follow-up on the next
  Birgitta tick.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the FlinsParty2 Aura and Damage Delta - Done

Why last:

Full-party optimizer and reaction results can be accepted only after the
character-local contract is committed.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `FlinsParty2` invocations and compare complete logs.
- Confirm all Overclock follow-ups avoid ICD diagnostics and elemental aura
  application while retaining damage.
- Update the audited baseline and close B-012.

Acceptance criteria:

- Two fresh runs have matching optimizer decisions and final summaries.
- Overclock contributes direct damage without any elemental application or ICD
  state.
- README, verification gate, backlog, and plan agree on the accepted baseline.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns the mechanic contract and this phase
  performs full-party integration acceptance.

Verification:

- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

## Implementation Order: Ineffa Skill No-ICD Application

Status:

- Implemented and verified.
- Phases 1-3 are complete.
- Requirement: Ineffa's Skill hit and every Birgitta Discharge must apply their
  documented 1U Electro without ICD suppression.

Scope:

- Explicitly encode Skill and Birgitta Discharge as 1U/no-ICD in `Ineffa`.
- Add actual-character regression coverage for repeated Birgitta applications.
- Re-run `FlinsParty2` and accept the aura/reaction/DPS delta.

Out of scope for this pass:

- Changing Skill/Birgitta damage, timing, duration, particle generation,
  shield behavior, or Overclock.
- Changing Ineffa Burst, Flins actions, generic ICD rules, reaction formulas,
  optimizer behavior, RL contracts, or generated `docs/` output.

Design boundaries:

- `model.character.Ineffa` remains the only owner of Skill action metadata.
- Runtime ICD and reaction services remain generic consumers of typed actions.
- Regression observes the character through simulator actions and reactions;
  no test-only production API is introduced.

### Phase 1: Record Skill and Birgitta Application Evidence - Done

Why first:

The periodic 2-second cadence differs materially between no ICD and the default
2.5-second/three-hit rule, so source evidence must precede implementation.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the Genshin Impact Wiki Ineffa advanced-property table, accessed
  2026-08-02: Skill Damage and Birgitta Discharge Damage are each 1U/no-ICD.
- Corroborate the 1U Skill contract and 2-second Birgitta cadence against the
  Japanese Genshin Wiki character analysis.
- Classify the simulator decision as `adopt` and record the pre-fix hit/block
  counts.

Acceptance criteria:

- Source URLs, access date, classification, and bounded test design are
  recorded.
- Pre-fix `FlinsParty2` is 15,344,560 damage / 224,994 DPS.
- The log identifies 40 Birgitta hits, of which 18 are incorrectly blocked by
  default Standard/None ICD.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- `./gradlew FlinsParty2`
- `python scripts/preflight.py --run`

### Phase 2: Encode and Test No-ICD Skill Applications - Done

Why second:

After evidence is fixed, the character-local metadata and repeated-hit behavior
can be corrected together.

Target files:

- `src/java/model/character/Ineffa.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Set the initial Skill action to `ICDType.None`, Skill tag, and 1U.
- Create Birgitta's periodic action explicitly and set it to
  `ICDType.None`, Skill tag, and 1U before event registration.
- Add an actual-Ineffa regression covering metadata and consecutive 2-second
  applications.

Acceptance criteria:

- Skill and Birgitta actions expose 1U/no-ICD metadata.
- Consecutive Birgitta hits can each trigger Electro application when Hydro
  aura is replenished.
- Birgitta with no reactive aura deals damage without a false reaction.
- Overclock remains 0U and no runtime/formula code changes.

Test cases to add or update:

- Normal: Skill emits 1U/no-ICD with the typed Skill tag.
- Normal: two consecutive Birgitta ticks each trigger the expected reaction
  against replenished Hydro.
- No-trigger: a Birgitta tick against no aura still deals direct damage and
  produces no elemental reaction.
- Boundary: the second tick at the exact 2-second cadence is not suppressed.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the FlinsParty2 Application Delta - Done

Why last:

The integrated reaction cadence and optimizer baseline can be accepted only
after the focused contract is committed.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `FlinsParty2` invocations and compare complete logs.
- Confirm all 40 Birgitta hits avoid ICD blocks and retain direct damage.
- Update the audited baseline and close B-013.

Acceptance criteria:

- Two fresh runs have identical optimizer decisions and final summaries.
- No Birgitta Discharge is ICD-blocked at its 2-second cadence.
- Current README, verification gate, backlog, and plan values agree.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns the mechanic contract and this phase
  performs full-party integration acceptance.

Verification:

- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Two complete logs were byte-identical with SHA-256
  `c8e837b24225f64f7a19081fed4d99fbadd8d515c403564894304651a615978a`.
- All 40 Birgitta Discharge hits executed with zero related ICD blocks.
- Only Ineffa's rounded contribution changed, from 2,946,003 to 3,205,782;
  the accepted result is 15,604,338 damage / 228,803 DPS.

## Implementation Order: Flins Thunderous Symphony Zero-Gauge Damage

Status:

- Implemented and verified.
- Phases 1-3 are complete.
- Requirement: Thunderous Symphony and its conditional Additional hit must
  retain direct Lunar-Charged damage while applying 0U and entering no ICD
  group.

Scope:

- Explicitly encode both Thunderous Symphony actions as 0U/no-ICD in `Flins`.
- Add actual-character regression coverage for main and Additional hit
  metadata, aura preservation, direct damage, and conditional scheduling.
- Re-run `FlinsParty2` and accept the reaction/DPS delta.

Out of scope for this pass:

- Changing damage multipliers, Lunar classification, animation or Additional
  hit timing, Thundercloud conditions, form duration, cooldowns, or energy.
- Changing the standard Flins Burst, generic ICD/reaction services, optimizer
  behavior, RL contracts, or generated `docs/` output.

Design boundaries:

- `model.character.Flins` remains the only owner of Symphony action metadata.
- Runtime ICD, aura, and Lunar formula services remain generic typed-action
  consumers.
- Regression observes actual Flins requests and simulator state; no test-only
  production API is introduced.

### Phase 1: Record Thunderous Symphony Application Evidence - Done

Why first:

These attacks are Electro damage but intentionally carry no gauge, so the
source contract must be fixed before changing their inherited defaults.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the Genshin Impact Wiki Flins advanced-property table, accessed
  2026-08-02: main and Additional Symphony damage are each 0U/no-ICD.
- Corroborate the distinct direct Lunar-Charged talent hits against KQM TCL.
- Classify the simulator decision as `adopt` and record pre-fix hit/block
  counts and baseline.

Acceptance criteria:

- Source URLs, access date, classification, and bounded test design are
  recorded.
- Pre-fix `FlinsParty2` is 15,604,338 damage / 228,803 DPS.
- The log identifies 12 main and 12 Additional hits, with four and eight
  inherited neutral-tag ICD blocks respectively.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect current `FlinsParty2` action and ICD counts
- `python scripts/preflight.py --run`

### Phase 2: Encode and Test Zero-Gauge Symphony Damage - Done

Why second:

After evidence is fixed, both action definitions and the actual-character
contract can be corrected atomically.

Target files:

- `src/java/model/character/Flins.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Set main and Additional Symphony actions to `ICDType.None`, Burst tag, and
  0U before execution or event registration.
- Extend actual-Flins regression coverage for both actions and the 0.1-second
  conditional Additional hit.
- Verify Hydro aura and reaction listeners remain unchanged while positive
  direct damage is recorded.

Acceptance criteria:

- Both actions expose 0U/no-ICD metadata and retain Burst typing and Lunar
  consideration.
- Main and Additional hits cannot consume Hydro aura or trigger an elemental
  reaction.
- Direct damage and conditional Additional scheduling remain active.
- No runtime/formula or standard-Burst code changes.

Test cases to add or update:

- Normal: active Thundercloud produces main plus one Additional direct hit.
- No-trigger: both 0U hits preserve a pre-existing Hydro aura and emit no
  reaction.
- Conditional: without Thundercloud, Symphony emits only the main hit.
- Boundary: the Additional hit remains scheduled 0.1 seconds after the main
  hit without entering an ICD group.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the FlinsParty2 Zero-Gauge Delta - Done

Why last:

The integrated reaction and optimizer baseline can be accepted only after the
focused action contract is committed.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `FlinsParty2` invocations and compare complete logs.
- Confirm all 24 Symphony hits retain direct damage with zero related ICD
  blocks or elemental application.
- Update the audited baseline and close B-014.

Acceptance criteria:

- Two fresh runs have identical optimizer decisions and final summaries.
- No Symphony hit mutates aura or enters ICD state.
- Current README, verification gate, backlog, and plan values agree.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns the mechanic contract and this phase
  performs full-party integration acceptance.

Verification:

- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Three simulator payloads were identical after excluding Gradle's elapsed-time
  status line, with SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`.
- All 12 main and 12 Additional hits retained direct damage with zero related
  ICD blocks or elemental reactions.
- Flins changed from 7,004,707 to 6,834,944 and Columbina from 4,349,846 to
  4,349,309; the accepted result is 15,434,039 damage / 226,306 DPS.

## Implementation Order: Flins Standard Burst Elemental Application

Status:

- Implemented and verified.
- Phases 1-3 are complete.
- Requirement: the standard Burst initial hit must apply 1U without ICD while
  every delayed middle and final hit retains direct Lunar damage at 0U/no-ICD.

Scope:

- Explicitly encode initial, middle, and final standard-Burst ICD/gauge
  metadata in `Flins`.
- Extend actual-Flins regression coverage for application, delayed hit counts,
  metadata, reactions, and direct damage.
- Confirm the unaffected `FlinsParty2` baseline remains deterministic.

Out of scope for this pass:

- Changing multipliers, Lunar classification, delayed timing or hit count,
  Thundercloud conditions, cooldowns, energy, or Thunderous Symphony.
- Changing generic ICD/reaction services, optimizer behavior, RL contracts, or
  generated `docs/` output.

Design boundaries:

- `model.character.Flins` remains the sole owner of standard-Burst action
  metadata and scheduling.
- Generic simulator services consume typed actions without Flins branches.
- Regression uses actual action requests and listeners without a test-only
  production API.

### Phase 1: Record Standard-Burst Application Evidence - Done

Why first:

The initial and delayed hits intentionally differ in gauge despite sharing one
talent, so the per-hit source contract must precede implementation.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the Genshin Impact Wiki advanced-property rows, accessed 2026-08-02:
  initial 1U/no-ICD, middle and final 0U/no-ICD.
- Corroborate the three hit classes and direct Lunar model against KQM TCL.
- Record inherited current metadata and the absence of standard Burst from the
  current `FlinsParty2` rotation.

Acceptance criteria:

- Source URLs, access date, adopted classification, and bounded test design are
  recorded.
- Pre-fix `FlinsParty2` is 15,434,039 damage / 226,306 DPS and contains zero
  standard-Burst hits.
- The expected no-change integration result is explicit rather than inferred
  after implementation.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect `Flins.burst_standard` and the current `FlinsParty2` log
- `python scripts/preflight.py --run`

### Phase 2: Encode and Test Per-Hit Standard-Burst Gauge - Done

Why second:

After evidence is fixed, all three action definitions and their shared
actual-character regression can change atomically.

Target files:

- `src/java/model/character/Flins.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Set initial Burst to `ICDType.None`, Burst tag, and 1U.
- Set every middle and final action to `ICDType.None`, Burst tag, and 0U.
- Extend the standard-Burst regression to inspect all actions and reaction
  behavior after delayed execution.

Acceptance criteria:

- Initial, middle, and final actions expose the documented metadata.
- Against Hydro, only the initial elemental application triggers a positive
  Lunar-Charged reaction; delayed 0U hits do not reapply Electro.
- All delayed hits retain positive direct damage and existing timing/counts.
- No Symphony or generic runtime code changes.

Test cases to add or update:

- Normal: no-Thundercloud cast emits one initial, two middle, and one final hit.
- Application: initial is 1U/no-ICD and can trigger one elemental reaction.
- No-trigger: all delayed middle/final hits are 0U/no-ICD and add no elemental
  reaction.
- Conditional: active Thundercloud retains four middle hits with identical 0U
  metadata.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Confirm the Unchanged FlinsParty2 Baseline - Done

Why last:

The current rotation does not use this action, but a full integration pass must
prove that the scoped correction has no unrelated effect.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh `FlinsParty2` invocations and compare simulator payloads.
- Confirm zero standard-Burst hits and an unchanged accepted baseline.
- Close B-015 without rewriting numeric baselines when they remain unchanged.

Acceptance criteria:

- Two fresh simulator payloads and summaries match.
- `FlinsParty2` remains 15,434,039 damage / 226,306 DPS.
- README and verification gate remain unchanged.
- Routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns the mechanic contract.

Verification:

- two fresh `./gradlew FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Both simulator payloads matched with SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`
  after excluding Gradle's elapsed-time status line.
- Both logs contained zero standard-Burst hits, as expected for this rotation.
- The accepted result remains 15,434,039 damage / 226,306 DPS, so README and
  the verification gate require no numeric update.

## Implementation Order: Flins Skill Activation Metadata

Status:

- Implemented and verified.
- Phases 1-3 are complete.
- Requirement: Manifest Flame activation must be a damageless 0U Electro Skill
  with no ICD, and Northland Spearstorm must apply 1U without ICD.

Scope:

- Explicitly type and configure the activation and Spearstorm actions in
  `Flins`.
- Add actual-character regression coverage for action identity, gauge, aura,
  reaction, timing, and direct damage.
- Re-run `FlinsParty2` and accept or reject any integration delta.

Out of scope for this pass:

- Adding missing constellations, charged/plunge attacks, knockback physics, or
  changing form duration, particle generation, cooldowns, Burst actions, or
  multipliers.
- Changing generic ICD/reaction services, optimizer behavior, RL contracts, or
  generated `docs/` output.

Design boundaries:

- `model.character.Flins` remains the sole owner of Skill action metadata.
- Runtime action, ICD, aura, and formula services remain generic consumers.
- Regression drives public action requests without a test-only production API.

### Phase 1: Record Skill Activation and Spearstorm Evidence - Done

Why first:

The activation is an Electro hit identity with 0 gauge rather than Physical
damage, while the follow-up Skill applies 1U, so both contracts need explicit
source evidence before implementation.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record activation 0U/no-ICD and Spearstorm 1U/no-ICD from the maintained
  advanced-property table, accessed 2026-08-02.
- Corroborate the special Skill's separate application from Japanese testing
  notes.
- Record pre-fix action counts and the current integration baseline.

Acceptance criteria:

- Sources, access date, adopted classification, scope, and test design are
  recorded.
- Pre-fix `FlinsParty2` is 15,434,039 damage / 226,306 DPS with four activation
  and 12 Spearstorm actions.
- Activation identity changes are distinguished from damage/application
  changes.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect `Flins.skill_enterForm`, `Flins.skill_spearstorm`, and current log
- `python scripts/preflight.py --run`

### Phase 2: Encode and Test Skill Action Contracts - Done

Why second:

After evidence is fixed, both Skill action definitions and their sequential
actual-character behavior can change together.

Target files:

- `src/java/model/character/Flins.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Replace the implicit activation action with an explicit Electro Skill action
  using `ICDType.None`, Skill tag, and 0U.
- Set Spearstorm to `ICDType.None`, Skill tag, and 1U.
- Add regression coverage for activation no-reaction and Spearstorm reaction.

Acceptance criteria:

- Activation is Electro/Skill/0U/no-ICD, deals zero damage, advances 0.3 s,
  preserves aura, and emits no elemental reaction.
- Spearstorm is Electro/Skill/1U/no-ICD and can trigger a reaction while
  retaining positive direct damage.
- Form and Symphony state transitions remain active.
- No Burst or generic runtime code changes.

Test cases to add or update:

- Normal: first Skill enters Manifest Flame through an Electro Skill action.
- No-trigger: 0U activation on Hydro preserves aura and creates no reaction.
- Application: second Skill emits 1U/no-ICD Spearstorm and reacts with Hydro.
- Boundary: both actions retain their existing 0.3-second durations.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the FlinsParty2 Skill-Metadata Delta - Done

Why last:

Skill typing may affect listeners even when gauge and direct damage do not, so
the full party result requires fresh deterministic acceptance.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `FlinsParty2` invocations and compare simulator payloads.
- Confirm four activation and 12 Spearstorm actions retain intended behavior.
- Update the audited baseline only if the typed Skill correction changes it.

Acceptance criteria:

- Two fresh simulator payloads and summaries match.
- No activation consumes aura or deals damage; no Spearstorm is ICD-blocked.
- Numeric baseline documents agree if changed.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns the mechanic contract.

Verification:

- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py` when the baseline gate changes
- `python scripts/preflight.py --run`

Completion evidence:

- Both post-fix payloads and the pre-fix payload matched after excluding
  Gradle's elapsed-time line, with SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`.
- Both logs contain four damageless activations and 12 Spearstorm hits, with
  zero Spearstorm-related ICD blocks.
- The accepted result remains 15,434,039 damage / 226,306 DPS, so README and
  the verification gate require no numeric update.

## Implementation Order: Dendro Resonance Reaction EM

Status:

- Implemented and verified.
- Phases 1-3 are complete.
- Requirement: Sprawling Greenery must grant permanent +50 EM and independently
  refreshed +30/+20 EM windows for their documented reaction groups.

Scope:

- Add typed identities for the two temporary resonance buffs.
- Register one Dendro-resonance reaction listener with explicit typed reaction
  classification and independent six-second no-stack refreshes.
- Add focused regression coverage for trigger groups, stacking, refresh, and
  inclusive/exclusive time boundaries.
- Confirm audited non-Dendro sample baselines remain unchanged.

Out of scope for this pass:

- Cryo or Geo resonance conditions, defensive resonance effects, changes to
  reaction calculation, Dendro Core behavior, Lunar-Bloom conversion, reports,
  optimizer logic, or RL contracts.
- Introducing display-string reaction dispatch or a generic buff framework
  refactor.

Design boundaries:

- `ResonanceManager` owns resonance reaction classification and registration.
- `BuffId` provides independent logical identities; `BuffManager` continues to
  own no-stack replacement and timing.
- `ReactionResult.Kind` is the only trigger identity used by the listener.
- Tests drive the public event and stat-resolution paths without test APIs.

### Phase 1: Record Sprawling Greenery Trigger Evidence - Done

Why first:

The two reaction groups have separate values and independent durations, and the
current Lunar-Bloom wording extends the first group beyond the older contract.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record current +50 base, +30 primary-group, +20 secondary-group, six-second,
  and independent-duration rules, accessed 2026-08-02.
- Include Lunar-Bloom in the primary group and retain typed standard reaction
  kinds for every trigger.
- Record unchanged pre-fix sample baselines and bounded implementation scope.

Acceptance criteria:

- Source URLs, access date, adopted classification, exact groups, values, and
  duration semantics are recorded.
- The current omission is linked to the explicit implementation comment.
- Sample non-coverage is explicit, so focused regression is the primary proof.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect `ResonanceManager` and `ReactionResult.Kind`
- `python scripts/preflight.py --run`

### Phase 2: Implement Independent Reaction EM Windows - Done

Why second:

After trigger evidence is fixed, typed identities, listener policy, and time
boundaries can be implemented and tested atomically.

Target files:

- `src/java/mechanics/element/ResonanceManager.java`
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add separate primary and secondary Sprawling Greenery temporary Buff IDs.
- Register a Dendro resonance listener that refreshes each six-second buff
  without same-group stacking.
- Classify all documented standard and Lunar reaction kinds through small
  private helpers.
- Add focused public-path regression for values, stacking, refresh, and expiry.

Acceptance criteria:

- Base/primary/secondary resolved EM values are 50/80/70 and simultaneous EM
  is 100 above the unbuffed character value.
- Repeated triggers refresh but do not stack the same group.
- Primary and secondary windows expire independently at `[start, end)`.
- Unrelated reaction kinds do not grant temporary EM.
- No display labels or generic reaction code determine eligibility.

Test cases to add or update:

- Normal: Quicken grants +30 and Aggravate grants +20.
- Stacking: active primary and secondary buffs combine with base +50.
- Refresh: repeated Bloom/Lunar-Bloom replaces the primary window at +30.
- Boundary: each buff applies before 6 seconds and is absent exactly at expiry.
- Abnormal: Vaporize grants no temporary Dendro resonance EM.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Confirm Shared-Resonance Integration Stability - Done

Why last:

The focused test owns Dendro behavior, while current audited non-Dendro parties
must prove the shared manager change has no unrelated effect.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run fresh `RaidenParty` and `FlinsParty2` samples.
- Confirm their current deterministic summaries remain unchanged.
- Close B-017 and record focused plus integration evidence.

Acceptance criteria:

- `RaidenParty` remains 1,440,416 damage / 68,591 DPS.
- `FlinsParty2` remains 15,434,039 damage / 226,306 DPS.
- README and verification gate require no numeric update.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns Dendro resonance behavior.

Verification:

- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
- `python scripts/preflight.py --run`

Completion evidence:

- Focused regression covers all eight documented trigger kinds including
  Lunar-Bloom, unrelated Vaporize, independent stacking/expiry, and same-group
  refresh.
- `RaidenParty` remains 1,440,416 damage / 68,591 DPS.
- `FlinsParty2` remains 15,434,039 damage / 226,306 DPS and its normalized
  payload retains SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`.

## Implementation Order: Conditional Cryo Resonance CRIT Rate

Status:

- Implemented and verified.
- Phases 1-3 are complete.
- Requirement: Shattering Ice must grant +15% CRIT Rate only while the target
  is Frozen or has a positive, current-time Cryo aura.

Scope:

- Replace the unconditional SimpleBuff effect with a time-aware resonance Buff.
- Add focused regression coverage for inactive, Cryo, Frozen, unrelated aura,
  and exact aura-expiry states.
- Confirm audited non-Cryo samples remain unchanged.

Out of scope for this pass:

- Electro status-duration reduction, Freeze duration, aura formulas, Geo or
  Dendro resonance, optimizer logic, reports, defensive systems, or RL.
- Generalizing conditional Buff construction beyond this resonance.

Design boundaries:

- `ResonanceManager` owns the condition; `Enemy` remains the source of current
  Cryo/Freeze state.
- The existing `Buff.applyStats` time parameter drives aura evaluation.
- No static/global simulator reference or display-string condition is added.

### Phase 1: Record Shattering Ice Condition Evidence - Done

Why first:

The +15% value is correct but its target-state condition is omitted, so source
wording and boundary semantics must be recorded before changing stat assembly.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the current Frozen-or-Cryo condition and +15% value, accessed
  2026-08-02.
- Confirm Enemy exposes current-time aura units and explicit Freeze state.
- Record unchanged non-Cryo sample baselines and local implementation scope.

Acceptance criteria:

- Source URL, access date, adopted classification, and exact condition are
  recorded.
- The existing unconditional approximation comment is linked to this item.
- Tests distinguish Cryo aura, Freeze state, unrelated aura, and expiry.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect `ResonanceManager`, `Enemy`, and stat resolution
- `python scripts/preflight.py --run`

### Phase 2: Implement and Test Dynamic Cryo Resonance - Done

Why second:

After the condition is fixed, a single time-aware Buff and focused stat tests
can establish the runtime contract atomically.

Target files:

- `src/java/mechanics/element/ResonanceManager.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Apply +15% CRIT Rate only when current Cryo aura is positive or Freeze state
  is active.
- Preserve the permanent resonance registration and typed Buff identity.
- Add resolved-stat regression at inactive, active, and expiry boundaries.

Acceptance criteria:

- No aura and Pyro aura grant no CRIT Rate.
- Cryo aura and Freeze state each grant exactly +15% CRIT Rate.
- A finite Cryo aura grants the bonus before expiry and not exactly at expiry.
- No Enemy or generic Buff API changes.

Test cases to add or update:

- Normal: positive Cryo aura grants +15% CRIT Rate.
- Alternate normal: explicit Frozen state grants +15% without Cryo aura.
- No-trigger: no aura and Pyro aura grant 0%.
- Boundary: finite Cryo aura bonus is absent exactly at aura expiry.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Confirm Shared-Resonance Integration Stability - Done

Why last:

Focused tests own Cryo behavior; current non-Cryo reference parties must remain
unchanged after a shared-manager edit.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run fresh `RaidenParty` and `FlinsParty2` samples.
- Confirm deterministic summaries and close B-018.

Acceptance criteria:

- `RaidenParty` remains 1,440,416 damage / 68,591 DPS.
- `FlinsParty2` remains 15,434,039 damage / 226,306 DPS.
- README and verification gate remain unchanged.
- Routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns Cryo resonance behavior.

Verification:

- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
- `python scripts/preflight.py --run`

Completion evidence:

- Focused regression covers no aura, unrelated Pyro, Cryo aura, Frozen state,
  and the exact finite-aura expiry boundary.
- `RaidenParty` remains 1,440,416 damage / 68,591 DPS.
- `FlinsParty2` remains 15,434,039 damage / 226,306 DPS with normalized
  payload SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`.

## Implementation Order: Electro Resonance Typed Trigger Set

Status:

- Implemented and verified.
- Phases 1-3 are complete.
- Requirement: High Voltage must generate particles only for its seven
  documented reaction kinds, including Lunar-Charged but excluding other Lunar
  reactions, with one shared five-second cooldown.

Scope:

- Narrow `ReactionResult.triggersElectroResonance()` to the exact typed set.
- Add direct eligibility and listener cooldown regression coverage.
- Re-run audited Electro-resonance parties and accept any energy/DPS delta.

Out of scope for this pass:

- Hydro status-duration reduction, particle energy coefficients, reaction
  formulas, Dendro/Cryo/Geo/Anemo resonance, optimizer policy, reports, or RL.
- Display-string fallback or changes to Lunar reaction classification itself.

Design boundaries:

- `ReactionResult.Kind` remains the source of truth for eligibility.
- `ResonanceManager` remains the owner of the shared five-second cooldown and
  particle distribution.
- Tests distinguish pure helper policy from event-listener behavior.

### Phase 1: Record High Voltage Trigger Evidence - Done

Why first:

The current broad Lunar helper is attractive but semantically wrong, so the
exact positive and negative typed sets must be fixed before narrowing it.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the seven current trigger reactions and five-second cooldown, accessed
  2026-08-02.
- Explicitly reject Lunar-Bloom and Lunar-Crystallize from High Voltage.
- Record pre-fix audited baselines and focused listener-test design.

Acceptance criteria:

- Source URL, access date, exact typed sets, cooldown, and scope are recorded.
- Lunar-Charged remains eligible while other Lunar kinds are explicitly
  excluded.
- Integration impact is measured rather than assumed.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect `ReactionResult.triggersElectroResonance` and resonance listener
- `python scripts/preflight.py --run`

### Phase 2: Narrow and Test Electro Resonance Eligibility - Done

Why second:

After the source set is fixed, helper policy and listener cooldown can be
corrected and verified atomically.

Target files:

- `src/java/mechanics/reaction/ReactionResult.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Replace broad Lunar eligibility with explicit Electro-Charged and
  Lunar-Charged kinds plus the five other documented reactions.
- Add positive/negative Kind regression.
- Add a two-Electro-party listener regression for unrelated reactions and the
  shared five-second cooldown.

Acceptance criteria:

- All seven documented kinds return true.
- Lunar-Bloom, Lunar-Crystallize, Spread, Burgeon, and unrelated reactions
  return false.
- Eligible reactions inside five seconds produce no second particle; one at
  exactly five seconds does.
- No display labels influence policy.

Test cases to add or update:

- Normal: each documented standard reaction and Lunar-Charged is eligible.
- Abnormal: other Lunar and non-Electro Dendro reactions are ineligible.
- Boundary: shared cooldown blocks at 4.999 seconds and permits at 5.0 seconds.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept Electro-Party Integration Results - Done

Why last:

Removing false particles can alter energy and rotation output, especially in
the Electro-heavy Flins reference party.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run fresh `RaidenParty` and two `FlinsParty2` payloads.
- Confirm false Lunar-Bloom/Crystallize triggers no longer generate particles.
- Update numeric baselines only if integration results change.

Acceptance criteria:

- Raiden and Flins summaries are deterministic and documented.
- High Voltage particle logs correspond only to eligible reactions and CD.
- Numeric baseline documents agree if changed.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns trigger and cooldown policy.

Verification:

- `./gradlew RaidenParty`
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py` when baseline gate changes
- `python scripts/preflight.py --run`

Completion evidence:

- Typed regression accepts the seven documented reactions (with both internal
  Overloaded aliases), rejects unrelated Lunar/Dendro reactions, and covers the
  shared 4.999/5.0-second cooldown boundary.
- `RaidenParty` remains 1,440,416 damage / 68,591 DPS.
- Both `FlinsParty2` payloads remain 15,434,039 damage / 226,306 DPS with
  normalized SHA-256
  `a9cdfbf0d3a0a01356d9d113afdd7f0afe8ef8510494f4b193107d533c8dbb6e`.

## Implementation Order: Xiangling Guoba No-ICD Application

Status:

- Implemented and verified; Phases 1-3 are complete.
- Requirement: every Guoba flame hit must apply 1U Pyro without entering or
  consulting a shared Skill ICD group.

Scope:

- Correct Guoba's `AttackAction` metadata in `Xiangling`.
- Add actual-character regression coverage for all four periodic hits.
- Re-run and accept the deterministic `RaidenParty` integration delta.

Out of scope for this pass:

- Pyronado metadata, hit cadence, animation timing, particle generation, C1
  shred, chili pickup, multipliers, optimizer policy, reports, or RL.
- Changing generic ICD behavior or `ICDTag.ElementalSkill` semantics.

Design boundaries:

- `Xiangling` owns Guoba's character-specific action metadata.
- The generic ICD engine remains unchanged and is tested through the scheduled
  Guoba hits rather than bypassed in test setup.
- Pyronado's three cast swings retain standard Burst ICD; its periodic hit
  retains no ICD.

### Phase 1: Record Guoba Application Evidence - Done

Why first:

Guoba and Pyronado have different per-attack ICD contracts, so the exact local
change must be fixed before editing the character implementation.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the current KQM Xiangling attack table, accessed 2026-08-02.
- Record Guoba's 1U/no-ICD contract and the already-correct Pyronado contracts.
- Record the pre-fix audited sample summary and focused test design.

Acceptance criteria:

- Source URL, access date, gauge, ICD, damage type, and excluded Pyronado scope
  are explicit.
- The final detailed pre-fix `RaidenParty` trace records five Guoba hits, two of
  which are incorrectly blocked by `ElementalSkill` ICD.
- Phase 2 changes remain character-local.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect `Xiangling.skill` and the final detailed `RaidenParty` trace
- `python scripts/preflight.py --run`

### Phase 2: Encode and Test Guoba No-ICD Hits - Done

Why second:

The source contract can be expressed by one local action-metadata change and
verified through the real periodic event path.

Target files:

- `src/java/model/character/Xiangling.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Set Guoba to no ICD while retaining its Skill tag and 1U Pyro application.
- Capture the four scheduled Guoba actions from an actual Xiangling instance.
- Verify that consecutive hits react independently instead of being blocked.

Acceptance criteria:

- All four Guoba hits are Pyro Skill damage with 1U, `ICDType.None`, and the
  typed Skill tag.
- All four hits can trigger reactions at the 1.5-second cadence.
- A no-aura run deals damage without fabricating reactions.
- Generic ICD code and Pyronado metadata are unchanged.

Test cases to add or update:

- Normal: four consecutive Guoba hits against refreshed Hydro each Vaporize.
- Abnormal: no-aura Guoba still produces four damage actions and no reactions.
- Contract: every captured hit retains Skill damage, 1U, no ICD, and Skill tag.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the RaidenParty Application Delta - Done

Why last:

Removing false application blocks can alter aura ownership, reactions, and
optimized output in the reference party and therefore requires fresh evidence.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `RaidenParty` payloads after the correction.
- Confirm the detailed trace has five Guoba hits and zero Guoba ICD blocks.
- Update documented Raiden totals only if the deterministic result changes.

Acceptance criteria:

- Both normalized payloads and numeric summaries match.
- Guoba's sourced application cadence is visible in the detailed trace.
- Numeric baseline documents agree if changed.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns action and reaction behavior.

Verification:

- two fresh `./gradlew RaidenParty` runs
- `python scripts/validate_agent_assets.py` when baseline gate changes
- `python scripts/preflight.py --run`

Completion evidence:

- The actual-character regression captures four periodic Guoba hits as Pyro
  Skill damage with 1U, no ICD, and the typed Skill tag; all four Vaporize with
  Hydro and the no-aura path produces four hits with no reactions.
- Both post-fix `RaidenParty` payloads report 1,461,315 damage / 69,586 DPS and
  match after excluding Gradle's elapsed-time line, with SHA-256
  `66fbe8eb153acd729db6d51b9cc545c8fe366e43bc0d126fd5ab30b660477fc4`.
- The final detailed trace contains five Guoba hits and zero Guoba application
  blocks; unrelated Skill actions continue to report their own ICD decisions.

## Implementation Order: Xingqiu Fatal Rainscreen No-ICD Application

Status:

- Implemented and verified; Phases 1-3 are complete.
- Requirement: both Fatal Rainscreen strikes must independently apply 1U Hydro
  without entering or consulting a Skill ICD group.

Scope:

- Correct the two Skill-hit `AttackAction` metadata entries in `Xingqiu`.
- Add actual-character regression coverage for both strikes.
- Re-run and accept the deterministic `RaidenParty` integration delta.

Out of scope for this pass:

- Rain Sword orbitals, Raincutter sword waves, Burst duration, C4 multiplier,
  particle generation, animation timing, optimizer policy, reports, or RL.
- Changing generic ICD behavior or typed tag semantics.

Design boundaries:

- `Xingqiu` owns Fatal Rainscreen's character-specific action metadata.
- Both hits retain the typed Skill tag for diagnostics without sharing ICD.
- Existing orbital cadence and Raincutter's standard typed ICD remain separate.

### Phase 1: Record Fatal Rainscreen Application Evidence - Done

Why first:

Xingqiu has three distinct Hydro application paths, so the Skill correction
must be isolated from the already-audited orbital and sword-wave contracts.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the current KQM Xingqiu attack table, accessed 2026-08-02.
- Record Fatal Rainscreen's two 1U/no-ICD hits and excluded Burst paths.
- Record the pre-fix final-trace block and focused test design.

Acceptance criteria:

- Source URL, access date, hit count, gauge, ICD, and damage type are explicit.
- The pre-fix detailed `RaidenParty` trace shows Hit 2 blocked by
  `ElementalSkill` ICD at 2.6 seconds.
- Phase 2 changes remain character-local.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect `Xingqiu.skill` and the final detailed `RaidenParty` trace
- `python scripts/preflight.py --run`

### Phase 2: Encode and Test Two Independent Skill Applications - Done

Why second:

The sourced contract can be expressed by two local metadata changes and tested
through the actual Skill execution path.

Target files:

- `src/java/model/character/Xingqiu.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Set both Fatal Rainscreen hits to no ICD while retaining Skill tags and 1U.
- Capture both damage actions through the production hook path.
- Verify both hits can react independently at their modeled cadence.

Acceptance criteria:

- Both hits are Hydro Skill damage with 1U, `ICDType.None`, and the typed Skill
  tag.
- Both hits trigger reactions against a sufficient Pyro aura.
- A no-aura run produces both damage actions and no reactions.
- Orbital and Raincutter metadata are unchanged.

Test cases to add or update:

- Normal: both Fatal Rainscreen strikes Vaporize independently.
- Abnormal: no-aura Skill still produces two damage hits and no reactions.
- Contract: both captured hits retain Hydro Skill, 1U, no ICD, and Skill tag.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the RaidenParty Skill-Application Delta - Done

Why last:

The second Hydro application can alter the early aura sequence and optimized
damage in the audited reference party.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `RaidenParty` payloads after the correction.
- Confirm both detailed Skill hits apply and Hit 2 has no Skill ICD block.
- Update documented Raiden totals only if the deterministic result changes.

Acceptance criteria:

- Both normalized payloads and numeric summaries match.
- The sourced two-hit application is visible in the detailed trace.
- Numeric baseline documents agree if changed.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns action and reaction behavior.

Verification:

- two fresh `./gradlew RaidenParty` runs
- `python scripts/validate_agent_assets.py` when baseline gate changes
- `python scripts/preflight.py --run`

Completion evidence:

- Actual-character regression captures both Fatal Rainscreen hits as Hydro
  Skill damage with 1U, no ICD, and the typed Skill tag; both Vaporize with a
  Pyro aura and the no-aura path produces two hits with no reactions.
- Both post-fix `RaidenParty` payloads report 1,464,729 damage / 69,749 DPS and
  match after excluding Gradle's elapsed-time line, with SHA-256
  `2fbe19b421aa18de1dd2d9e342c0de369177635ea49f6644ced01046c0f0342e`.
- The final detailed trace shows both Skill hits trigger Electro-Charged and Hit
  2 has no preceding `ElementalSkill` ICD block.

## Implementation Order: Bennett Skill and Burst Application Metadata

Status:

- Implemented and verified; Phases 1-3 are complete.
- Requirement: Passion Overload Press applies 2U Pyro with no ICD, while
  Fantastic Voyage applies 2U Pyro with no ICD.

Scope:

- Correct Bennett's Press Skill gauge and Skill/Burst ICD metadata.
- Add actual-character regression coverage for both actions.
- Re-run and accept the deterministic `RaidenParty` integration delta.

Out of scope for this pass:

- Held Skill variants, C4 follow-up, cooldown reduction, field buffs, healing,
  C6 infusion, particles, animation timing, optimizer policy, reports, or RL.
- Changing generic ICD or elemental-gauge formulas.

Design boundaries:

- `Bennett` owns its character-specific action metadata.
- Press and Burst retain typed Skill/Burst tags for diagnostics without sharing
  ICD state.
- Buff, energy, and artifact-trigger behavior remains unchanged.

### Phase 1: Record Bennett Application Evidence - Done

Why first:

Press and Burst share a 2U/no-ICD contract but have different action types, so
both exact metadata rows and the excluded held variants must be fixed first.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the current KQM Bennett attack table, accessed 2026-08-02.
- Record Press and Fantastic Voyage gauge, ICD, element, and damage type.
- Record the pre-fix audited baseline and focused test design.

Acceptance criteria:

- Source URL, access date, 2U gauge, no-ICD policy, and action types are
  explicit.
- Existing Press 1U/standard ICD and Burst 2U/standard ICD divergences are
  recorded separately.
- Held Skill and cooldown behavior remain out of scope.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect `Bennett.skill`, `Bennett.burst`, and the detailed sample trace
- `python scripts/preflight.py --run`

### Phase 2: Encode and Test Bennett Application Contracts - Done

Why second:

Both sourced rows can be corrected locally and tested through actual Bennett
actions without modifying shared gauge or ICD infrastructure.

Target files:

- `src/java/model/character/Bennett.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Set Press Skill to 2U/no ICD with its typed Skill tag.
- Set Fantastic Voyage to 2U/no ICD with its typed Burst tag.
- Capture and verify both actions through the production damage-hook path.

Acceptance criteria:

- Press is Pyro Skill damage with 2U, `ICDType.None`, and Skill tag.
- Fantastic Voyage is Pyro Burst damage with 2U, `ICDType.None`, and Burst tag.
- Each action independently triggers its expected reaction with a valid aura.
- No-aura actions deal damage without fabricating reactions.

Test cases to add or update:

- Normal: Press triggers Overloaded and Burst triggers Vaporize.
- Abnormal: isolated no-aura actions produce damage and no reactions.
- Contract: captured Skill/Burst actions retain 2U, no ICD, and typed tags.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the RaidenParty Gauge Delta - Done

Why last:

Press's stronger Pyro gauge can alter aura ownership and downstream reactions in
the audited reference rotation.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `RaidenParty` payloads after the correction.
- Inspect Press/Burst application and downstream reaction logs.
- Update documented Raiden totals only if the deterministic result changes.

Acceptance criteria:

- Both normalized payloads and numeric summaries match.
- Bennett's sourced action metadata is visible in the detailed trace.
- Numeric baseline documents agree if changed.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns action and reaction behavior.

Verification:

- two fresh `./gradlew RaidenParty` runs
- `python scripts/validate_agent_assets.py` when baseline gate changes
- `python scripts/preflight.py --run`

Completion evidence:

- Actual-character regression captures Press as 2U/no-ICD Pyro Skill damage
  and Fantastic Voyage as 2U/no-ICD Pyro Burst damage, including Overloaded,
  Vaporize, and isolated no-aura paths.
- Both post-fix `RaidenParty` payloads report 1,433,347 damage / 68,255 DPS and
  match after excluding Gradle's elapsed-time line, with SHA-256
  `e35dfd3b864ddbc2ff132dae447eeed51506bbe26c5980fecf1bf1535c0ef59f`.
- The intentional decrease reflects the sourced stronger Press gauge retaining
  a 2U Pyro aura at 14.8 seconds and changing downstream reaction ownership.

## Implementation Order: Raiden Cast and Musou Isshin ICD Metadata

Status:

- Implemented and verified; Phases 1-3 are complete.
- Requirement: Skill and Burst initial hits use no ICD, Eye uses standard Skill
  ICD, and Musou Isshin Normal/Charged attacks share one standard ICD group.

Scope:

- Correct Raiden's Skill cast and Burst initial ICD metadata.
- Route Burst-state Normal and Charged attacks through the existing dedicated
  `Raiden_MusouIsshin` tag.
- Add actual-character regression and re-accept `RaidenParty`.

Out of scope for this pass:

- Plunging-attack ICD, attack multipliers, hit splitting, Eye trigger policy,
  particle probability, Resolve, energy restoration, cooldowns, optimizer
  policy, reports, or RL.
- New ICD tags or generic ICD behavior.

Design boundaries:

- `RaidenShogun` owns the action-specific metadata and state-dependent tag
  choice.
- Existing `ICDTag.Raiden_MusouIsshin` is the single typed group for Burst-state
  Normal and Charged attacks.
- Physical pre-Burst Normal and Charged attacks retain their generic groups.

### Phase 1: Record Raiden Application and Grouping Evidence - Done

Why first:

Raiden has separate cast, coordinated, and converted-attack contracts, so the
exact no-ICD and shared-ICD boundaries must be fixed before editing.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record current KQM and Genshin Impact Wiki tables, accessed 2026-08-02.
- Record Skill cast, Eye, Burst initial, and Musou Isshin N/CA contracts.
- Record pre-fix detailed blocks and focused test design.

Acceptance criteria:

- Sources, access date, gauges, ICD policies, and shared group are explicit.
- The pre-fix Skill cast suppresses the first Eye application and Burst N/CA use
  independent generic groups.
- Plunge behavior remains explicitly unresolved and out of scope.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect Raiden action construction and the detailed `RaidenParty` trace
- `python scripts/preflight.py --run`

### Phase 2: Encode and Test Raiden Typed ICD Contracts - Done

Why second:

All corrected policies fit existing `AttackAction`, `ICDType`, and `ICDTag`
contracts and can be tested through a real Raiden instance.

Target files:

- `src/java/model/character/RaidenShogun.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Set Skill cast to 1U/no ICD while retaining its Skill tag.
- Keep Eye at 1U/standard Skill ICD and set Burst initial to 2U/no ICD.
- Use `Raiden_MusouIsshin` for Burst-state Normal and Charged attacks only.

Acceptance criteria:

- Skill cast and first Eye can both apply Electro; subsequent Eye hits obey
  standard ICD.
- Burst initial is 2U/no-ICD Electro Burst damage.
- Burst-state N and CA share standard typed ICD and block the second immediate
  application; physical N/CA keep independent generic tags.
- No display labels drive grouping.

Test cases to add or update:

- Normal: Skill cast and first Eye both react; Burst initial has 2U/no ICD.
- Boundary: immediate Burst N then CA shares one group and permits only one
  application.
- Abnormal: non-Burst physical N/CA retain NormalAttack/ChargedAttack tags.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the RaidenParty ICD Delta - Done

Why last:

The first Eye application and shared Burst combo ICD directly affect the
reference party's reaction sequence and damage.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `RaidenParty` payloads after the correction.
- Inspect the first Eye and Burst N/CA application decisions.
- Update documented Raiden totals to the deterministic accepted result.

Acceptance criteria:

- Both normalized payloads and numeric summaries match.
- Detailed logs reflect sourced cast/Eye and shared Musou ICD behavior.
- Numeric baseline documents agree.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns action and ICD grouping behavior.

Verification:

- two fresh `./gradlew RaidenParty` runs
- `python scripts/validate_agent_assets.py` when baseline gate changes
- `python scripts/preflight.py --run`

Completion evidence:

- Actual-character regression covers Skill cast plus first/second Eye decisions,
  2U/no-ICD Burst initial, shared Burst N/CA ICD, and unaffected physical tags.
- Both post-fix `RaidenParty` payloads report 1,402,417 damage / 66,782 DPS and
  match after excluding Gradle's elapsed-time line, with SHA-256
  `c1b6624fb2ea1a3d361a778a5aeead7d731c8bb3f0dae05c5c8d85b6d34c4da0`.
- Detailed logs show the first Eye can apply independently from Skill cast and
  Burst Normal/Charged blocks use `Raiden_MusouIsshin`.

## Implementation Order: Raiden Eye Refresh Lifecycle

Status:

- Implemented and verified; Phases 1-3 are complete.
- Requirement: recasting Transcendence: Baleful Omen replaces the previous Eye
  periodic stream and refreshes its duration without overlapping attacks.

Scope:

- Add explicit cancellation lifecycle to `PeriodicDamageEvent`.
- Retain and cancel Raiden's prior Eye event before registering its replacement.
- Add timer-level and actual-Raiden refresh regression coverage.

Out of scope for this pass:

- Eye's 0.9-second cadence, 25-second duration, trigger-on-damage fidelity,
  particles, ICD metadata, Skill cooldown, damage multiplier, optimizer policy,
  reports, or RL.
- General event deduplication or changing other periodic effects.

Design boundaries:

- `PeriodicDamageEvent` owns its cancelled/finished state.
- `RaidenShogun` owns only the currently registered Eye event handle.
- Cancellation is idempotent and a cancelled event deals no further damage or
  invokes its callback.

### Phase 1: Record Overlapping Eye Evidence - Done

Why first:

The duplicate stream must be distinguished from legitimate 0.9-second Eye
cadence before changing reusable timer lifecycle.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the current 25-second Eye and 0.9-second per-party cadence sources.
- Record the post-recast duplicate timestamps in the audited sample.
- Define cancellation behavior and focused timer/character tests.

Acceptance criteria:

- Source URLs, access date, refresh interpretation, and observed duplicate count
  are recorded.
- The pre-fix final trace records old-stream times 15.0, 15.9, ..., 20.4 and
  new-stream times 15.6, 16.5, ..., 21.0 after the 14.2-second recast.
- Other periodic event users remain out of scope.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect `RaidenShogun.skill`, `PeriodicDamageEvent`, and sample timestamps
- `python scripts/preflight.py --run`

### Phase 2: Implement and Test Eye Event Replacement - Done

Why second:

An explicit event lifecycle keeps cancellation policy reusable and avoids
embedding scheduler internals in the character.

Target files:

- `src/java/simulation/event/PeriodicDamageEvent.java`
- `src/java/model/character/RaidenShogun.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add idempotent `cancel()` and make cancelled events immediately finished.
- Prevent cancelled events from executing damage or callbacks.
- Cancel Raiden's retained Eye event before registering a replacement.

Acceptance criteria:

- A cancelled periodic event executes neither action nor callback.
- Repeated cancellation is safe and the scheduler drops the event at its next
  due time.
- Raiden recast produces only replacement-stream Eye hits after refresh.
- Other non-cancelled periodic events retain existing cadence.

Test cases to add or update:

- Normal: non-cancelled periodic event still ticks on schedule.
- Abnormal: cancel before due time yields zero damage/callback ticks.
- Refresh: actual Raiden recast has no old-stream Eye hit after replacement.
- Boundary: calling `cancel()` twice remains a no-op after the first call.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the RaidenParty Eye-Refresh Delta - Done

Why last:

Removing duplicate Eye hits changes damage, particles, aura, and optimizer
results in the audited rotation after the second Skill cast.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `RaidenParty` payloads after event replacement.
- Confirm only the replacement Eye stream remains after the 14.2-second recast.
- Update the deterministic Raiden baseline.

Acceptance criteria:

- Both normalized payloads and numeric summaries match.
- Detailed trace has no stale old-stream Eye timestamps after refresh.
- Numeric baseline documents agree.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns timer and refresh behavior.

Verification:

- two fresh `./gradlew RaidenParty` runs
- `python scripts/validate_agent_assets.py` when baseline gate changes
- `python scripts/preflight.py --run`

Completion evidence:

- Regression covers non-cancelled cadence, idempotent pre-due cancellation, no
  cancelled callback, and actual Raiden replacement timestamps.
- Both post-fix `RaidenParty` payloads report 1,361,340 damage / 64,826 DPS and
  match after excluding Gradle's elapsed-time line, with SHA-256
  `df040feaf6c35da05783cca75d2992824d3246c654fc917e050888588b9586f4`.
- After the 14.2-second recast, only replacement hits at 15.6, 16.5, 17.4,
  18.3, 19.2, 20.1, and 21.0 seconds remain; all stale old-stream hits are gone.

## Implementation Order: Guoba Four-Hit Lifetime

Status:

- Implemented and verified; Phases 1-3 are complete.
- Requirement: one Guoba cast deals exactly four flame hits at +2.0, +3.5,
  +5.0, and +6.5 seconds, with no +8.0-second hit.

Scope:

- Correct Guoba's character-local periodic duration.
- Extend actual-Xiangling regression through the former fifth-hit boundary.
- Re-run and accept the deterministic `RaidenParty` integration delta.

Out of scope for this pass:

- Generic `PeriodicDamageEvent` boundary semantics, other periodic effects,
  Guoba ICD/gauge, particles, C1, chili timing, multipliers, optimizer policy,
  reports, or RL.

Design boundaries:

- `Xiangling` owns the number and schedule of Guoba flame hits.
- Generic timer semantics remain unchanged because existing effects explicitly
  rely on inclusive terminal ticks.
- The existing +7.0-second chili timing remains independent from flame ticks.

### Phase 1: Record Guoba Lifetime Evidence - Done

Why first:

The generic periodic boundary is shared by effects with different intended
counts, so Guoba's exact four-hit timestamps must be fixed before editing.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record KQM's four-hit, seven-second Guoba contract, accessed 2026-08-02.
- Record the five pre-fix sample timestamps and local duration cause.
- Explicitly preserve generic inclusive timer behavior.

Acceptance criteria:

- Source URL, access date, hit count, duration, and expected timestamps are
  recorded.
- Pre-fix trace evidence includes the erroneous +8.0-second fifth hit.
- The correction is scoped to `Xiangling` and its regression.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 extends executable
  coverage.

Verification:

- inspect `Xiangling.skill`, periodic semantics, and final sample timestamps
- `python scripts/preflight.py --run`

### Phase 2: Enforce and Test Four Guoba Hits - Done

Why second:

The sourced hit count can be represented by a character-local duration change
without changing the shared scheduler.

Target files:

- `src/java/model/character/Xiangling.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Set Guoba's periodic duration so its inclusive final tick is +6.5 seconds.
- Advance the existing actual-character tests past +8.0 seconds.
- Assert exact four-hit count and timestamps with and without an aura.

Acceptance criteria:

- Guoba hits exactly at +2.0, +3.5, +5.0, and +6.5 seconds.
- No damage, reaction, particle, or C1 callback occurs at +8.0 seconds.
- Existing 1U/no-ICD metadata and chili timing remain unchanged.
- Other periodic event tests are unchanged.

Test cases to add or update:

- Normal: four sourced timestamps each react against sufficient Hydro.
- Abnormal: advancing beyond +8.0 produces no fifth action or reaction.
- No aura: four damage actions and zero reactions across the full boundary.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the RaidenParty Guoba-Lifetime Delta - Done

Why last:

The invalid fifth hit currently contributes damage, particles, aura, and
downstream reactions in the audited rotation.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `RaidenParty` payloads after the lifetime correction.
- Confirm each cast contributes only its four in-lifetime hits.
- Update the deterministic Raiden baseline.

Acceptance criteria:

- Both normalized payloads and numeric summaries match.
- Detailed trace contains no Guoba hit at cast time +8.0 seconds.
- Numeric baseline documents agree.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns hit-count and boundary behavior.

Verification:

- two fresh `./gradlew RaidenParty` runs
- `python scripts/validate_agent_assets.py` when baseline gate changes
- `python scripts/preflight.py --run`

Completion evidence:

- Actual-Xiangling regression advances past +8.0 seconds and retains exactly
  four hits at +2.0, +3.5, +5.0, and +6.5 seconds with and without an aura.
- Both post-fix `RaidenParty` payloads report 1,331,957 damage / 63,427 DPS and
  match after excluding Gradle's elapsed-time line, with SHA-256
  `c7f2780605ab245f1482ea80263d396d8bd7b802cbe273d4860f43e8655ec1cf`.
- The final trace contains Guoba hits at 8.4, 9.9, 11.4, and 12.9 seconds only;
  the invalid 14.4-second fifth hit is gone.

## Implementation Order: Ineffa Birgitta Summon Lifecycle

Status:

- Complete; Phases 1-3 passed their acceptance criteria.
- Requirement: Skill or Burst summons/refreshed exactly one Birgitta, which
  attacks ten times at two-second intervals during its 20-second lifetime.

Scope:

- Extract one Ineffa-owned Birgitta summon helper used by Skill and Burst.
- Cancel the previous Birgitta periodic event when either action refreshes it.
- Correct the local duration to ten attacks and re-accept `FlinsParty2`.

Out of scope for this pass:

- Birgitta ICD/gauge, Overclock damage, Thundercloud state, particles, shield,
  Burst damage/buffs, summon positioning, optimizer policy, reports, or RL.
- Generic periodic cadence/boundary changes.

Design boundaries:

- `Ineffa` owns the current Birgitta event handle and summon policy.
- `PeriodicDamageEvent` owns cancellation mechanics already introduced by
  B-024.
- Skill and Burst depend on one private helper rather than duplicate event
  construction.

### Phase 1: Record Birgitta Summon and Lifetime Evidence - Done

Why first:

Missing Burst refresh, overlapping Skill streams, and the terminal eleventh hit
all derive from one summon lifecycle and must be specified together.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the current Wiki/KQM summon contract, accessed 2026-08-02.
- Record duplicate post-recast timestamps and the +22-second terminal hit.
- Define one helper and ten-hit replacement-stream test design.

Acceptance criteria:

- Sources, access date, one-summon rule, Skill/Burst entry points, interval,
  duration, and hit count are explicit.
- Pre-fix overlap and missing Burst-refresh symptoms are recorded.
- Generic timer and unrelated Ineffa behavior remain excluded.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable coverage.

Verification:

- inspect Ineffa Skill/Burst and complete `FlinsParty2` Birgitta timestamps
- `python scripts/preflight.py --run`

### Phase 2: Implement and Test One Refreshable Birgitta - Done

Why second:

A single helper gives Skill and Burst the same summon contract and centralizes
event replacement and hit-count policy.

Target files:

- `src/java/model/character/Ineffa.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Extract Birgitta action/event construction into a private helper.
- Call it from Skill and Burst, cancelling any prior event first.
- End the inclusive periodic duration on the tenth +20-second hit.

Acceptance criteria:

- One summon attacks at +2, +4, ..., +20 seconds and never at +22.
- Skill recast and Burst refresh each leave one replacement stream.
- Burst alone summons Birgitta.
- Existing no-ICD metadata, Overclock callback, and particles are preserved.

Test cases to add or update:

- Normal: one Skill yields ten exact Discharge timestamps.
- Burst: Burst without prior Skill creates a Discharge at +2 seconds.
- Refresh: Skill recast removes the old stream at the next due timestamp.
- Abnormal: advancing past +22 seconds produces no eleventh hit.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the FlinsParty2 Birgitta Delta - Done

Why last:

The reference rotation repeatedly casts both Skill and Burst, so corrected
replacement semantics alter damage, Lunar follow-ups, particles, and aura.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `FlinsParty2` payloads after the correction.
- Confirm no overlapping Birgitta timestamps and no +22-second terminal hit.
- Update the deterministic Flins baseline.

Acceptance criteria:

- Both normalized payloads and numeric summaries match.
- Detailed trace reflects one current Skill/Burst-refreshed Birgitta stream.
- Numeric baseline documents agree.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns summon lifecycle and hit count.

Verification:

- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py` when baseline gate changes
- `python scripts/preflight.py --run`

Completion evidence:

- Both post-fix payloads report 14,316,424 damage / 213,042 DPS and match after
  excluding Gradle's elapsed-time line, with SHA-256
  `1990a24b7dea2d237f7d7823c2ca77c64649eaddd98a1174730fdb3a7384a6bb`.
- The trace contains one Birgitta stream after each Skill or Burst completion;
  each replacement begins two seconds later and no superseded stream resumes.
- The executable actual-Ineffa regression verifies ten exact hits at +2 through
  +20 seconds, no +22-second hit, Burst-only summon, and Skill/Burst refresh.

## Implementation Order: Timing-Aware ER Calibration

Status:

- Complete; Phases 1-3 passed their acceptance criteria.
- Requirement: ER calibration must select a target that funds every requested
  Burst in rotation order, including the cyclic final-to-first interval.

Scope:

- Close energy-accounting windows on successful and skipped Burst requests.
- Calculate required ER by replaying chronological intervals with energy cap
  and carry rather than averaging aggregate rotation totals.
- Keep runtime energy spending and optimizer analysis as separate roles.
- Re-accept the `FlinsParty2` optimizer and numeric baseline.

Out of scope for this pass:

- Particle values, funnel timing, Burst costs/cooldowns, artifact roll sizes,
  rotation scripts, damage formulas, RL tensor/protocol changes, or generated
  report output.
- Replacing the iterative optimizer or changing its public result shape.

Design boundaries:

- `EnergyState` records interval inputs and requested Burst costs without
  deciding the target ER.
- `EnergyAnalyzer` owns cyclic interval reconstruction and worst-case ER math.
- `ActionGateway` owns the user-facing insufficient-energy diagnostic.
- `IterativeSimulator` continues to own convergence and simulator rebuilding.

### Phase 1: Record the Mid-Rotation ER Failure - Done

Why first:

The current aggregate formula, exact warning points, and intended cyclic
boundary must be fixed before changing accounting semantics.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record both Columbina Burst failures in the accepted `FlinsParty2` trace.
- Distinguish the displayed `60/60` rounding from the recorded 59.8/60 energy.
- Specify per-request windows and final-tail-to-first-window cyclic handling.

Acceptance criteria:

- The ledger names the observable warnings and pre-fix baseline.
- Runtime accounting, analysis, and convergence ownership are explicit.
- Unrelated energy generation and RL contracts remain excluded.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds interval and
  integration regressions.

Verification:

- inspect `FlinsParty2` warnings and generated energy timeline
- `python scripts/preflight.py --run`

### Phase 2: Calibrate Against the Worst Burst Interval - Done

Why second:

Attempt-level windows provide the minimum chronological input needed for the
analyzer to reject an aggregate target that fails in the middle of a rotation.

Target files:

- `src/java/model/entity/state/EnergyState.java`
- `src/java/model/entity/Character.java`
- `src/java/mechanics/analysis/EnergyAnalyzer.java`
- `src/java/simulation/runtime/ActionGateway.java`
- `src/java/sample/ReactionRegressionTest.java`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record and reset one particle/flat window for every successful or skipped
  Burst request while preserving actual energy on a skip.
- Reconstruct the cyclic first interval from post-final tail income plus the
  income before the first requested Burst.
- Find the minimum ER that sustains the chronological cycle, retaining the
  existing 9.99 sentinel when the sequence cannot be funded.
- Print insufficient energy with enough precision to distinguish 59.8 from 60.

Acceptance criteria:

- A locally deficient interval cannot be hidden by excess energy in a later
  interval.
- Alternate Burst costs preserve legitimate post-Burst energy between requests.
- A skipped request closes only its analysis window and does not spend runtime
  energy or start Burst cooldown.
- One-Burst rotations retain cyclic start-full/end-full calibration behavior.
- Existing Flins alternate 30-energy Burst accounting remains correct.

Test cases to add or update:

- Normal: balanced chronological windows retain the expected ER target.
- Boundary: final tail and pre-first income combine into the cyclic first
  interval.
- Abnormal: one 30-particle interval for a 60-cost Burst requires 200% ER even
  when later windows contain excess particles.
- No-particle: an unfunded interval returns the 9.99 sentinel.
- Diagnostic: a 59.8/60 skip is logged without rounding to 60/60.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py --run`

### Phase 3: Accept the Warning-Free FlinsParty2 Baseline - Done

Why last:

The real optimizer run proves the new interval policy reserves enough ER after
artifact quantization and repeated simulation.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `FlinsParty2` payloads with timing-aware calibration.
- Confirm every scripted Columbina Burst executes and no energy warning remains.
- Update the deterministic Flins baseline and record normalized payload hashes.

Acceptance criteria:

- Both normalized payloads and numeric summaries match.
- The final trace has no insufficient-energy warning for any character.
- Numeric baseline documents agree.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns interval math and skip semantics.

Verification:

- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Both payloads reserve 141%/132%/105%/193% ER for
  Sucrose/Flins/Ineffa/Columbina and execute all 20 requested Bursts, including
  all four Columbina Bursts, without an insufficient-energy warning.
- Both payloads report 14,077,198 damage / 203,722 DPS and match after excluding
  Gradle's elapsed-time line, with SHA-256
  `63d95d817af04e4263fb92f8492609296980154f37261fceafbc9222a0d248f6`.
- Interval regression covers skipped-window closure, later-income masking,
  cyclic tail/first income, no-particle sentinel behavior, alternate Burst
  costs, and precise insufficient-energy diagnostics.

## Implementation Order: Raiden Eye Damage Trigger

Status:

- Complete; Phases 1-3 passed their acceptance criteria.
- Requirement: while active, the Eye performs one coordinated attack only when
  a party attack deals positive damage and its party-wide 0.9-second cooldown
  is ready.

Scope:

- Add a resolved-direct-damage listener contract below action orchestration.
- Dispatch damage events for timeline and no-time-advance attack resolution.
- Replace Raiden's autonomous periodic event with damage-triggered Eye state.
- Preserve Eye duration, ICD/gauge, dynamic Raiden stats, and particle model.

Out of scope for this pass:

- Multi-target trigger rules, shielded/immune enemies, transformative-reaction
  trigger exceptions, co-op scaling, exact frame delay, damage formulas,
  optimizer policy, RL tensor/protocol changes, or generated reports.
- Changing generic action-listener semantics or periodic-event behavior.

Definitions:

- `DamageListener`: simulation event listener receiving actor, action, resolved
  direct damage, and timestamp for every resolved `AttackAction`.

Design boundaries:

- `CombatActionResolver` emits factual resolved-damage events.
- `SimulationEventDispatcher` owns listener registration and fan-out.
- `RaidenShogun` owns Eye activation, expiry, cooldown, recursion prevention,
  attack construction, and particles.
- Eye resolution uses a same-timestamp one-shot event so the triggering attack
  finishes resolution before the coordinated attack executes.

### Phase 1: Record Eye Trigger Evidence and Runtime Boundary - Done

Why first:

The trigger condition, cooldown origin, event ordering, and deliberate delay
simplification must be explicit before replacing the existing timer.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record current KQM guide and TCL evidence with access date.
- Record the pre-fix autonomous Eye count and accepted Raiden baseline.
- Define resolved-damage dispatch and same-timestamp deferred resolution.

Acceptance criteria:

- Damage requirement, party-wide 0.9-second cooldown, cooldown origin, and
  25-second active state are explicit.
- The simulator adaptation and excluded shield/reaction edge cases are named.
- No production code changes in the evidence phase.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds event and character
  lifecycle coverage.

Verification:

- inspect `RaidenShogun.skill`, action resolution, and `RaidenParty` Eye times
- `python scripts/preflight.py --run`

### Phase 2: Trigger One Eye from Resolved Damage - Done

Why second:

A resolver-level event covers player actions and off-field/no-time-advance hits
without coupling Raiden to action orchestration or display labels.

Target files:

- `new src/java/simulation/DamageListener.java`
- `src/java/simulation/SimulationEventBus.java`
- `src/java/simulation/runtime/SimulationEventDispatcher.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/model/character/RaidenShogun.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/simulation/AGENTS.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Dispatch resolved direct damage with typed actor/action identity and amount.
- Register one Raiden-owned listener and refresh Eye attack/expiry/cooldown state
  on each Skill cast.
- Schedule one same-time coordinated attack for eligible positive damage and
  reject zero damage, Eye self-damage, cooldown, and expiry cases.
- Generate the existing expected 0.5 Electro particles only on an Eye attack.
- Remove Raiden's periodic event handle and timer registration.

Acceptance criteria:

- Advancing time without damage produces no Eye attacks or Eye particles.
- Timeline and no-time-advance positive damage can trigger the Eye.
- Repeated damage inside 0.9 seconds produces one attack; exact cooldown expiry
  permits the next attack and uses triggering-damage time as the boundary.
- Skill recast updates one listener-owned Eye state without duplicate attacks.
- Existing Eye 1U/standard-Skill ICD metadata remains unchanged.

Test cases to add or update:

- Normal: positive party damage triggers one Eye and 0.5 expected particles.
- Idle: advancing several seconds after Skill causes no Eye damage.
- Boundary: same-time and +0.899-second hits are blocked; +0.900 is accepted.
- Off-field: `performActionWithoutTimeAdvance` damage triggers after due events run.
- Abnormal: zero direct damage and expired Eye state produce no trigger.
- Refresh: multiple Skill casts still yield one coordinated attack per eligible
  damage event.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py --run`

### Phase 3: Accept the Triggered RaidenParty Baseline - Done

Why last:

The reference rotation establishes the intended damage, aura, particle, ER,
and optimizer delta after autonomous Eye ticks are removed.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`

Tasks:

- Run two fresh `RaidenParty` payloads after the trigger correction.
- Confirm Eye timestamps follow eligible damage and never appear during idle.
- Update the deterministic Raiden baseline and normalized payload hash.

Acceptance criteria:

- Both normalized payloads and numeric summaries match.
- Detailed trace contains only damage-triggered Eye attacks at legal cadence.
- Numeric baseline documents agree.
- Agent assets and routed preflight checks pass.

Test cases to add or update:

- No further production test; Phase 2 owns generic dispatch and Eye lifecycle.

Verification:

- two fresh `./gradlew RaidenParty` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Both payloads report 1,283,512 damage / 61,120 DPS and match after excluding
  Gradle's elapsed-time line, with SHA-256
  `58bc339e94e09cbb91ca31f42696a4c2b2c9ce535654916bddfe90e610c6d7fd`.
- Each payload contains 17 Eye attacks aligned to positive damage events rather
  than the pre-fix 22 autonomous periodic ticks.
- Executable regression covers idle, positive and zero damage, timeline and
  no-time-advance dispatch, exact 0.9-second cooldown, expiry, refresh,
  recursion prevention, particles, and unchanged Eye ICD/gauge metadata.

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

## Implementation Order: Dragon's Bane Target-Aura Passive

Status:

- Complete; Phases 1-2 passed their acceptance criteria.
- Requirement: Dragon's Bane R5 grants 36% all-damage bonus only when the
  target is currently affected by Hydro or Pyro, evaluated for every direct
  hit before that hit consumes or replaces the aura.

Scope:

- Add a narrow weapon capability for stats that depend on the current target.
- Resolve that capability per direct hit without mutating stat snapshots or
  pre-resolved stat containers.
- Replace Dragon's Bane's unconditional stat passive with live Hydro/Pyro aura
  evaluation.
- Cover direct, amplifying-reaction, expiry, and snapshot behavior in the
  executable reaction regression.

Out of scope for this pass:

- Equipping Dragon's Bane in an existing party, adding weapon refinements,
  changing optimizer loadouts, or changing accepted Raiden/Flins baselines.
- Applying the bonus to transformative reaction damage, adding multi-target
  enemy selection, changing aura decay/consumption, changing standard or Lunar
  formulas, RL contracts, reports, or generated `docs/` output.
- Converting Deathmatch or any other manually configured conditional weapon.

Definitions:

- `TargetDependentWeaponEffect`: weapon capability that adds per-hit stats from
  the target's current state without changing the character's persistent or
  snapshotted stats.
- `DamageCalculator.resolveTargetStats`: formula-boundary helper that copies
  resolved stats and applies `TargetDependentWeaponEffect` for one target and
  timestamp.

Design boundaries:

- `DragonsBane` owns Hydro/Pyro eligibility and the R5 36% value.
- `CombatActionResolver` captures target-dependent stats before reaction aura
  mutation and passes that immutable per-hit view to the damage formula.
- `DamageCalculator` owns the same per-hit resolution for direct callers and
  never mutates a character snapshot or a caller-owned stat container.
- Standard and Lunar strategies consume the resolved view without knowing a
  concrete weapon class.

### Phase 1: Record Bane Passive Evidence and Formula Boundary - Done

Why first:

The passive text alone does not settle snapshot timing or reaction ordering, so
the target-state and non-snapshot contracts must be fixed before code changes.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record current HoYoLAB passive wording and maintained KQM Bane-series tests.
- Record KQM's explicit finding that Dragon's Bane is enemy-state-dependent
  and cannot be snapshotted.
- Define pre-reaction target-state capture and copied per-hit stats as the
  implementation boundary.

Acceptance criteria:

- R5 value, eligible auras, direct/amplifying scope, off-field behavior, and
  non-snapshot timing are traceable to recorded sources accessed 2026-08-02.
- The plan distinguishes pre-hit aura eligibility from post-reaction aura
  state and excludes transformative reaction damage.
- No production source changes occur in this phase.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds executable weapon,
  reaction-order, expiry, and snapshot coverage.

Verification:

- inspect `DragonsBane`, `StatAssembler`, `DamageCalculator`, and
  `CombatActionResolver`
- `python scripts/preflight.py --run`

### Phase 2: Resolve Dragon's Bane from Pre-Hit Target State - Done

Why second:

The recorded contract now identifies the narrow capability and the exact point
where target state must be captured before reaction handling mutates aura.

Target files:

- `new src/java/model/entity/TargetDependentWeaponEffect.java`
- `src/java/model/weapon/DragonsBane.java`
- `src/java/mechanics/formula/DamageCalculator.java`
- `src/java/mechanics/formula/StandardDamageStrategy.java`
- `src/java/mechanics/formula/LunarDamageStrategy.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/model/entity/AGENTS.md`
- `src/java/model/weapon/AGENTS.md`
- `src/java/mechanics/formula/AGENTS.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Introduce the target-dependent weapon capability with documented mutation
  and ownership semantics.
- Remove Dragon's Bane's unconditional `applyPassive` bonus and add 36% only
  for positive current Hydro or Pyro aura units.
- Copy and augment resolved stats once per hit before reaction processing;
  preserve direct `DamageCalculator` caller behavior.
- Add actual-weapon regressions for eligible/ineligible auras, exact expiry,
  amplifying reaction ordering, and repeated snapshotted hits.

Acceptance criteria:

- No aura, Electro aura, and an exactly expired Hydro/Pyro aura receive no
  Dragon's Bane damage bonus.
- Live Hydro and Pyro auras each grant exactly 36% additive all-damage bonus.
- A Pyro hit that Vaporizes a Hydro aura receives the bonus even when reaction
  handling consumes that aura before final damage is recorded.
- Snapshot capture never stores the conditional bonus; repeated snapshot hits
  evaluate live target state independently and do not accumulate mutations.
- Existing weapons, direct formula calls, reaction behavior, and builds remain
  valid.

Test cases to add or update:

- Normal: non-reacting direct damage against live Hydro and Pyro receives 36%.
- No-trigger: no aura and Electro aura match the unmodified damage result.
- Boundary: a decaying aura is eligible immediately before expiry and
  ineligible at exact expiry.
- Reaction ordering: Pyro-on-Hydro Vaporize receives both the 1.5 multiplier
  and the 36% pre-hit target bonus.
- Snapshot: capture without target aura, then alternate eligible and ineligible
  targets across repeated hits with no retained or stacked bonus.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/entity/TargetDependentWeaponEffect.java --path src/java/model/weapon/DragonsBane.java --path src/java/mechanics/formula/DamageCalculator.java --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Focused regression proves no/Electro aura exclusion, Hydro/Pyro inclusion,
  immediate-pre-expiry inclusion, exact-expiry exclusion, and a consuming
  Pyro-on-Hydro Vaporize using the pre-hit condition.
- Runtime snapshot hits alternate eligible and ineligible target state without
  storing or accumulating the 36% bonus.
- `ReactionRegressionTest`, `build`, warning-free Javadoc, focused agent
  validation, and preflight pass; cataloged party baselines are unchanged
  because no current party equips Dragon's Bane.

## Implementation Order: Deterministic FlinsParty Energy Scenario

Status:

- Complete; Phases 1-3 passed the revised reproducibility acceptance criteria.
- Requirement: every simulator created for one `FlinsParty` optimization run
  uses the same independent Favonius and Moondrift random streams so ER
  calibration, artifact optimization, and final rotation are comparable and
  reproducible.

Scope:

- Make Favonius Codex proc draws injectable while preserving stochastic default
  construction.
- Make Columbina's Moondrift Harmony draw injectable independently from weapon
  proc draws while preserving stochastic default construction.
- Give `FlinsPartyDefinition` fixed, separate seeds for both streams on every
  simulator construction.
- Cover draw thresholds, cooldown behavior, stream independence, and repeated
  `FlinsParty` integration output.

Out of scope for this pass:

- Replacing probability with expected-value damage/energy, changing Favonius
  particle count or cooldown, or changing Moondrift's 33% chance.
- Making every generic simulation deterministic, changing the documented
  random `FlinsParty2` Moondrift behavior, or changing accepted Raiden/FlinsParty2
  numeric baselines.
- Changing ER replay formulas, optimizer search policy, rotation timing, RL
  contracts, reports, or generated `docs/` output.

Definitions:

- `FavoniusCodex(DoubleSupplier)`: constructor whose supplier provides Windfall
  chance draws for deterministic tests and party scenarios.
- `Columbina(..., DoubleSupplier)`: constructor whose supplier provides only
  Moondrift Harmony extra-attack draws.
- Favonius and Moondrift seed constants: independent `FlinsPartyDefinition`
  streams recreated from the same seeds for every simulator instance.

Design boundaries:

- Each stochastic mechanic owns its draw source and cooldown/chance decision.
- `FlinsPartyDefinition` owns reproducibility policy for its optimizer and final
  sample; generic weapon and character constructors remain stochastic.
- Separate streams prevent an added Moondrift hit from shifting later Favonius
  decisions, while repeated simulator construction receives common random
  numbers.
- Tests inject finite deterministic sequences without adding global RNG state.

### Phase 1: Record FlinsParty ER Nondeterminism - Done

Why first:

Known random damage is already accepted under B-005, so this phase must prove
the separate symptom that random hit/proc ordering changes ER decisions and
rotation validity.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record two unchanged-tree `FlinsParty` runs with ER results, Burst warnings,
  duration, and final totals.
- Trace Moondrift extra hits through damage hooks into Favonius draw count and
  neutral particle distribution.
- Define independent per-simulator random streams instead of global seeding or
  expected-value replacement.

Acceptance criteria:

- Two runs demonstrate different Sucrose/Columbina ER targets and each contains
  skipped Sucrose Bursts after calibration.
- The symptom is distinguished from B-005's accepted random damage total.
- No production source changes occur in this phase.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds deterministic draw,
  threshold, cooldown, and Moondrift coverage.

Verification:

- two fresh `./gradlew FlinsParty` runs with extracted `ER Result`, Burst
  warning, total, DPS, and duration lines
- `python scripts/preflight.py --run`

### Phase 2: Inject Independent Favonius and Moondrift Draws - Done

Why second:

The observed coupling identifies two owning classes and requires separate draw
sources before the party can seed a stable scenario.

Target files:

- `src/java/model/weapon/FavoniusCodex.java`
- `src/java/model/character/Columbina.java`
- `src/java/simulation/party/FlinsPartyDefinition.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/model/weapon/AGENTS.md`
- `src/java/model/character/AGENTS.md`
- `src/java/simulation/AGENTS.md`
- `TASKS.md`

Tasks:

- Add null-rejecting `DoubleSupplier` constructors and route default
  construction through `Math::random`.
- Replace direct `Math.random()` calls with the mechanic-owned suppliers.
- Construct `FlinsParty` Columbina and Favonius Codex with independent seeded
  `Random` streams recreated for each simulator.
- Add regressions for chance boundaries, failed-draw retry, cooldown boundaries,
  deterministic replay, and Moondrift extra-hit decisions.

Acceptance criteria:

- Favonius draws below CRIT Rate proc, equal/above values fail, failed draws do
  not start cooldown, and +6.000 seconds permits the next draw.
- Moondrift draws below 0.33 add one attack and a draw at 0.33 does not.
- Null suppliers fail at construction and identical supplied sequences replay
  identically.
- Existing default constructors remain stochastic and public talent-data
  construction remains source-compatible.

Test cases to add or update:

- Favonius normal: successful draw generates the R5 neutral particles.
- Favonius abnormal: null supplier and equal-threshold draw are rejected/no-proc.
- Favonius boundary: failed same-time retry and 5.999/6.000-second cooldown.
- Moondrift normal/boundary: 0.329999 adds an extra hit; 0.33 does not.
- Replay: two fresh injected instances consume equal draw counts and produce
  equal particle/action outcomes.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew PartyCatalogRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/weapon/FavoniusCodex.java --path src/java/model/character/Columbina.java --path src/java/simulation/party/FlinsPartyDefinition.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

### Phase 3: Accept the Stable FlinsParty Scenario - Done

Why last:

Only complete optimizer-to-final runs can prove that common random scenarios
stabilize ER allocation and distinguish random variation from an independent
deterministic defect.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run at least two fresh `FlinsParty` invocations and compare ER results,
  warnings, duration, contribution totals, and final summary.
- Classify any identical residual warning as a separate deterministic defect
  instead of attributing it to random-stream drift.
- Document generic stochastic behavior and the party-local fixed scenario.

Acceptance criteria:

- Repeated runs produce identical ER targets and normalized simulator payloads.
- The accepted seeded total, DPS, and duration are recorded without changing
  RaidenParty or FlinsParty2 baselines.
- Any remaining warning has a new ledger entry with a concrete owner, symptom,
  and proof rather than being silently accepted under B-032.
- Preflight passes with no generated report staged.

Test cases to add or update:

- No additional production test; Phase 2 owns draw contracts and this phase
  owns full integration reproducibility.

Verification:

- at least two fresh `./gradlew FlinsParty` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Both full runs report ER targets Sucrose/Flins/Ineffa/Columbina =
  258%/100%/100%/164%, 18,241,614 damage / 176,247 DPS over 103.5 seconds,
  2,771 Windfall triggers across all optimizer/final simulations, and normalized
  SHA-256 `97f8887f286c20ff2872dc3c8fa2d3934e0494a5d1900da1f0e54cd19deaa389`.
- The complete normalized logs are identical. Generic constructors remain
  stochastic; only `FlinsPartyDefinition` fixes independent streams.
- Both traces also identically skip Sucrose Burst at 25.9, 60.6, and 95.3
  seconds. This disproves random-stream drift as the cause: the requested
  258.3% exceeds the current loadout's roughly 166.1% artifact ER ceiling and
  is silently accepted. B-033 owns that newly isolated defect.

## Implementation Order: Artifact ER Feasibility and Legal FlinsParty Burst Cadence

Status:

- Implemented; Phases 1-3 are complete.
- Requirement: artifact generation must never return a build below its declared
  `minER`, and `FlinsParty` must use a deterministic Burst cadence that is
  feasible under its KQMS loadout instead of relying on an impossible target.

Scope:

- Validate achieved static ER after all fixed, main-stat, and liquid-roll
  allocation in `ArtifactOptimizer`.
- Fail an infeasible `minER` with requested/achieved diagnostics before an
  underfilled build enters the optimizer pipeline.
- Remove the second Sucrose Burst request from each `FlinsParty` loop while
  retaining its Skill and the first Burst, yielding three evenly repeated
  Burst casts across the full sample.
- Include equipped artifact-set static stats for every catalog-party allocation.
  This corrects Emblem and Silken ER reservation without special-casing either
  set in the optimizer.
- Emblem of Severed Fate's static 20% ER makes RaidenParty Xingqiu's target legal.
  The new invariant initially exposed Xingqiu's 178.829% cyclic target as above
  an incorrectly calculated 166.120% cap; the real set-aware cap is 186.120%.
- Propagate fatal generic sample failures instead of printing them and returning
  a successful process status.
- Cover exact feasible and just-over-cap ER boundaries plus repeated full-party
  acceptance.

Out of scope for this pass:

- Raising or bypassing KQMS per-stat/liquid-roll caps, changing main stats,
  automatically substituting weapons, artifacts, or party members, or clamping
  an ER requirement.
- Changing `EnergyAnalyzer` replay math, optimizer DPS hill-climbing, character
  energy generation, action cooldowns, RL contracts, or generated reports.
- Claiming the revised sample rotation is globally DPS-optimal; it is a legal,
  reproducible reference rotation under the existing loadout.

Definitions:

- ER feasibility check: post-allocation comparison of `config.minER` against
  the static ER sum from character, weapon, set, and generated artifact stats.
- Legal Sucrose cadence: one Skill+Burst setup near the start of each of the
  three outer `FlinsParty` loops; the later Skill remains but its unsupported
  Burst request is removed.

Design boundaries:

- `ArtifactOptimizer` owns the invariant that a successful result satisfies its
  declared static minimum stats.
- `OptimizerPipeline` continues to consume successful results without knowing
  party-specific loadout details.
- `FlinsPartyDefinition` owns the scripted action cadence and adapts the sample
  rather than weakening global KQMS constraints.
- Failure diagnostics report values and the `ENERGY_RECHARGE` stat identity;
  display names do not control runtime behavior.

### Phase 1: Isolate the Unreachable ER Contract - Done

Why first:

The deterministic B-032 trace distinguishes an allocation-feasibility defect
from random energy variance and makes the legal response testable.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the 258.3% Sucrose target, approximately 166.1% achievable build, and
  three stable skipped-Burst timestamps.
- Reject cap bypass, silent clamping, and unrequested loadout substitution.
- Define fail-fast artifact generation plus a party-owned legal Burst cadence.

Acceptance criteria:

- Requested, achievable, and observed runtime values are recorded from matching
  seeded runs.
- The generic invariant and party-specific adaptation have separate owners.
- No production source changes occur in this phase.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds allocation boundaries
  and full sample behavior.

Verification:

- inspect `ArtifactOptimizer`, `OptimizerPipeline`, Sucrose artifact config, and
  both Sucrose Burst sites in `FlinsPartyDefinition`
- `python scripts/preflight.py --run`

### Phase 2: Reject Underfilled ER and Use a Legal Burst Cadence - Done

Why second:

The invariant and legal party response must land together so no committed
phase leaves the runnable sample failing solely because the new guard exposed
its known invalid request.

Target files:

- `src/java/mechanics/optimization/ArtifactOptimizer.java`
- `src/java/simulation/party/FlinsPartyDefinition.java`
- `src/java/simulation/party/FlinsParty2Definition.java`
- `src/java/simulation/party/RaidenPartyDefinition.java`
- `src/java/sample/RunPartySimulation.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/mechanics/optimization/AGENTS.md`
- `TASKS.md`

Tasks:

- Calculate achieved ER after allocation and throw an actionable state error
  when it remains below `minER` beyond floating-point tolerance.
- Cover heuristic and manual-roll infeasibility without changing allocation
  caps or priorities.
- Remove only the later Sucrose Burst in each outer loop; retain the associated
  Skill for damage, particles, and buff behavior.
- Pass actual static set stats for each catalog-party character so allocation
  and feasibility validation match the subsequently equipped artifact object.
- Let fatal optimizer and simulator failures escape the generic runner so Gradle
  reports a failed task.
- Verify FlinsParty2 retains its accepted baseline and re-accept RaidenParty if
  correcting its set-aware ER reservation changes the optimized roll result.

Acceptance criteria:

- A target at the exact legal ER ceiling succeeds; a target just above it fails
  with requested and achieved ER in the message.
- Manual ER rolls that underfill `minER` fail through the same invariant.
- `FlinsParty` creates and optimizes successfully with exactly three requested
  and three executed Sucrose Bursts and no insufficient-energy warning.
- RaidenParty satisfies Xingqiu's cyclic ER target with its existing ATK% sands
  and completes without a hidden exception; FlinsParty2 retains its accepted total.

Test cases to add or update:

- Normal: exact maximum achievable ER returns an artifact result.
- Boundary: target one micro-unit above the maximum throws.
- Abnormal: explicit insufficient manual ER allocation throws.
- Integration: three-loop `FlinsParty` requests no unsupported second Burst.
- Integration: RaidenParty completes with set-aware feasible Xingqiu ER and a
  real nonzero process failure would surface for any future fatal configuration.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew PartyCatalogRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
- `python scripts/agent_validate.py --path src/java/mechanics/optimization/ArtifactOptimizer.java --path src/java/simulation/party/FlinsPartyDefinition.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Exact-cap heuristic allocation succeeds; a target 0.0001 percentage points
  above it and an insufficient manual ER allocation both fail through the same
  requested/achieved `ENERGY_RECHARGE` diagnostic.
- Catalog party definitions now pass equipped artifact-set static stats into
  allocation. RaidenParty converges at Xingqiu 179% and Xiangling 174%, reserves
  nine Xingqiu ER rolls, and reports 1,317,080 damage / 62,718 DPS over 21.0s.
- FlinsParty completes with exactly three Sucrose Burst casts, no energy or
  feasibility warning, and 18,765,805 damage / 188,601 DPS over 99.5s.
- FlinsParty2 retains 14,077,198 damage / 203,722 DPS over 69.1s.
- ReactionRegressionTest, PartyCatalogRegressionTest, build, Javadoc, routed
  agent validation, and all three sample smoke runs pass.

### Phase 3: Accept Feasible Deterministic Party Output - Done

Why last:

Repeated full optimizer-to-final runs are required to accept the revised ER
targets, three-Burst cadence, total, and duration after the legal rotation
change.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run at least two fresh `FlinsParty` invocations and compare normalized logs.
- Record ER targets, Sucrose Burst count, warning count, duration, total, DPS,
  and normalized hash.
- Remove the temporary B-033 warning note from README and replace it with the
  accepted legal-cadence statement.

Acceptance criteria:

- Repeated complete payloads match and contain three successful Sucrose Bursts.
- No artifact ER infeasibility exception or insufficient-energy warning occurs.
- README and ledger describe the accepted behavior without changing audited
  FlinsParty2 baseline; any set-aware RaidenParty baseline change is explicitly
  re-accepted rather than hidden.
- Preflight passes and no generated report is staged.

Test cases to add or update:

- No further production test; Phase 2 owns invariant coverage and this phase
  owns full integration acceptance.

Verification:

- at least two fresh `./gradlew FlinsParty` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh FlinsParty payloads match after excluding Gradle's elapsed-time
  line, with normalized SHA-256
  `acbb84038b3846771d1af195410e3d11daad7f152ea4ea450c37c2d05ee2dd85`.
- Both report Sucrose/Flins/Ineffa/Columbina ER of 109%/100%/100%/180%, exactly
  three successful Sucrose Bursts, zero warnings, and 18,765,805 damage /
  188,601 DPS over 99.5 seconds.
- Two set-aware RaidenParty payloads likewise match with 1,317,080 damage /
  62,718 DPS over 21.0 seconds and normalized SHA-256
  `ff1adfd3b3705f1cc34a32036af0950aa8a1246a6412589eb214966b3f3c33dc`.
- FlinsParty2 retains its accepted 14,077,198 damage / 203,722 DPS baseline.

## Implementation Order: Wandering Evenstar Timed EM Snapshot

Status:

- Implemented; Phases 1-3 are complete.
- Requirement: Wandering Evenstar must derive both R5 ATK bonuses from one
  equipped-owner EM snapshot at the sourced activation cadence instead of
  splitting its calculation across incompatible stat-assembly stages.

Scope:

- Add a narrow lifecycle capability for a weapon that must register simulator
  events when its owner joins a party.
- Trigger Wildling Nightstar after 64 frames and every ten seconds thereafter,
  including while the owner is off-field.
- Capture the owner's effective EM once per trigger, then apply 48% as owner
  flat ATK and 14.4% as flat ATK to each other party member for the shared
  twelve-second window.
- Cover activation, expiry/refresh timing, snapshot stability, off-field use,
  and independent stacking from multiple equipped copies.

Out of scope for this pass:

- Changing refinement level, weapon base stats, artifact allocation, Sucrose
  talents, generic stat-assembly order, or other Tulaytullah-series weapons.
- Modeling party distance; the single-target party simulator treats all other
  members as nearby for this team buff.
- Changing the intentionally excluded timer-event portion of simulator/RL
  snapshots, RL observations or actions, generated reports, or committed docs.
- Inferring percentage-based EM conversion chaining beyond the effective flat
  EM visible to the simulator at each snapshot.

Definitions:

- `SimulatorInitializedWeaponEffect`: focused capability invoked once after an
  equipped owner is added to a simulator, allowing a weapon to register its own
  time-driven behavior without coupling generic `Weapon.applyPassive` to the
  simulator.
- Wildling Nightstar snapshot: one captured owner EM value used to derive both
  the 48% owner bonus and 30%-of-that ally bonus until the next ten-second
  refresh.

Design boundaries:

- `CombatSimulator` owns lifecycle dispatch after party insertion.
- Wandering Evenstar owns its trigger cadence, captured value, and buff objects.
- Buff application remains typed and source-attributed; multiple equipped
  copies use independent instances and stack without display-name matching.
- The default `Weapon` and unrelated weapon capabilities remain unchanged.

### Phase 1: Record Wildling Nightstar Evidence and Pre-Fix Defect - Done

Why first:

The timed snapshot and shared EM basis must be sourced before changing a weapon
that affects an accepted optimizer-driven party baseline.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the R5 48%, 30% team share, 10-second trigger, 12-second duration, and
  off-field wording from HoYoWiki.
- Record KQM's 64-frame first activation and ten-second series resnapshot test.
- Trace the pre-fix owner calculation through `StatAssembler`, where weapon
  passive evaluation precedes artifact merging, while the team provider reads
  artifact EM separately.

Acceptance criteria:

- Material values, timing, source dates, simulator classification, and URLs are
  recorded in the durable ledger.
- The observable pre-fix defect and bounded regression design are explicit.
- No production source changes occur in this phase.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 introduces the failing
  timing and shared-snapshot cases.

Verification:

- inspect `WanderingEvenstar`, `StatAssembler`, `BuffManager`, `CombatSimulator.addCharacter`, and timer events
- `python scripts/preflight.py --run`

### Phase 2: Register and Apply One Timed EM Snapshot - Done

Why second:

The lifecycle seam and weapon implementation land together so the new interface
has an immediate production caller and executable behavior.

Target files:

- new `src/java/model/entity/SimulatorInitializedWeaponEffect.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/model/weapon/WanderingEvenstar.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/model/entity/AGENTS.md`
- `src/java/model/weapon/AGENTS.md`
- `TASKS.md`

Tasks:

- Dispatch the initialization capability once after an owner is inserted into
  the party.
- Replace permanent split-basis provider/passive logic with one 64-frame-start,
  ten-second periodic event that captures full effective owner EM including
  applicable simulator buffs.
- Maintain separate source-attributed owner and ally flat-ATK buffs from the
  same captured value, refreshing their twelve-second windows without stacking
  one equipped instance with itself.
- Preserve independent stacking for multiple equipped copies and off-field
  activation.

Acceptance criteria:

- No Evenstar ATK exists before 64/60 seconds; both bonuses appear at the exact
  boundary and use one captured EM value.
- EM changes inside a ten-second interval do not alter either bonus; the next
  trigger updates both together and maintains continuous uptime.
- The owner receives 48% and each other member 14.4% of captured EM as flat ATK.
- Two equipped copies contribute independent ally shares without duplicate
  lifecycle registration from one weapon.

Test cases to add or update:

- Boundary: 64-frame first activation and the next trigger at +10 seconds.
- Normal: owner and ally R5 values from one known EM snapshot.
- Snapshot: mid-window EM change is held until the next trigger.
- Off-field: activation occurs while another party member is active.
- Multi-instance: two owners' ally shares stack once each.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew PartyCatalogRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/weapon/WanderingEvenstar.java --path src/java/model/entity/SimulatorInitializedWeaponEffect.java --path src/java/simulation/CombatSimulator.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Focused regression proves zero ATK immediately before 64/60 seconds, then
  owner 48% and ally 14.4% from one 865 EM snapshot at activation.
- A +100 EM mid-window buff leaves both ATK values unchanged until the exact
  +10-second trigger, where both update from the new 965 EM snapshot.
- The owner remains off-field during activation, and two independent equipped
  copies stack one ally share each.
- FlinsParty smoke completes with unchanged 109%/100%/100%/180% ER, three
  Sucrose Bursts, no warnings, and 18,843,690 damage / 189,384 DPS over 99.5s.
- ReactionRegressionTest, PartyCatalogRegressionTest, build, Javadoc, routed
  validation, and preflight pass.

### Phase 3: Accept the Timed FlinsParty Baseline - Done

Why last:

The optimizer and full rotation must demonstrate that the corrected periodic
buff remains deterministic and does not reintroduce energy warnings.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh complete FlinsParty payloads and compare normalized logs.
- Record ER targets, total, DPS, duration, warning count, and normalized hash.
- Document the timed Evenstar assumption and close B-035.

Acceptance criteria:

- Repeated complete payloads match with no insufficient-energy or optimizer
  warning.
- The numeric delta is attributable to the corrected owner/ally shared snapshot.
- Documentation and ledger contain the accepted baseline and evidence.

Test cases to add or update:

- No further production test; Phase 2 owns weapon boundaries and Phase 3 owns
  full-party acceptance.

Verification:

- two fresh `./gradlew FlinsParty` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two complete FlinsParty payloads match after excluding Gradle's elapsed-time
  line, with normalized SHA-256
  `5fcfe756770e925d4afde9f5e1bc9a23ba9cd86b2620aa99af3cab4e61234744`.
- Both retain Sucrose/Flins/Ineffa/Columbina ER of 109%/100%/100%/180%, execute
  three Sucrose Bursts, emit zero warnings, and report 18,843,690 damage /
  189,384 DPS over 99.5 seconds.
- Sucrose contribution rises from 400,869 to 417,667 damage; the remaining
  increase is distributed across allies receiving the linked snapshot share.

## Implementation Order: Party-Size Particle Energy Multipliers

Status:

- Implemented; Phases 1-3 are complete.
- Requirement: off-field particle energy must scale with the current party size
  instead of applying the four-character multiplier to every party.

Scope:

- Resolve the off-field particle multiplier from the simulator party size.
- Use 0.8 for two characters, 0.7 for three characters, and 0.6 for four or
  more characters; the active recipient remains at 1.0.
- Cover elemental and neutral particle distribution through the shared
  `EnergyDistributor` path.
- Preserve ER scaling, element matching, flat-energy behavior, particle
  notifications, and accepted four-character sample baselines.

Out of scope for this pass:

- Changing particle or orb base values, ER calculation, energy caps, Burst
  costs, generated particle counts, enemy drop timing, or optimizer policy.
- Introducing distance or collection-range simulation.
- Changing four-character rotations, party definitions, RL contracts, report
  schemas, or generated output.
- Defining unsupported behavior above four party members beyond retaining the
  current 0.6 minimum multiplier.

Definitions:

- Active multiplier: the 1.0 collection factor used by the on-field character.
- Off-field multiplier: the party-size-dependent factor applied before ER:
  0.8 for two characters, 0.7 for three, and 0.6 for four or more.
- Party size: the number of characters registered in the simulator when the
  particles are distributed.

Design boundaries:

- `EnergyDistributor` owns collection-range multiplication and derives it from
  `CombatSimulator.getPartyMembers()` without exposing party-size rules to
  characters or party definitions.
- `EnergyState` continues to own ER-scaled accounting and energy caps; it does
  not gain party knowledge.
- Regression fixtures create distinct typed character IDs and inspect
  pre-ER particle totals, keeping tests independent from Burst costs and caps.
- Existing static `EnergyManager` entry points remain source-compatible.

### Phase 1: Record Energy Evidence and Freeze Contracts - Done

Why first:

The party-size values are game-mechanic inputs and must be sourced before the
shared energy path changes.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record maintained KQM energy evidence demonstrating a 0.8 off-field factor
  in a two-character party and the standard 0.6 four-character factor.
- Record the community energy reference's explicit 0.8/0.7/0.6 mapping for
  two-, three-, and four-character parties.
- Trace the current constant 0.6 through `EnergyDistributor` and freeze the
  active, ER, element, flat-energy, and four-character invariants.

Acceptance criteria:

- The values, source classifications, access date, and URLs are present in the
  durable ledger.
- The owning class and bounded test matrix are explicit.
- No production source changes occur in this phase.

Normal tests:

- Planning validation confirms the item has one active plan and does not enter
  the paused RL plan.

Abnormal tests:

- Source review rejects any inference that changes particle base values or
  makes flat energy party-size-dependent.

Verification:

- inspect `EnergyDistributor`, `EnergyManager`, `EnergyState`, and
  `EnergyAnalyzer`
- `python scripts/preflight.py --run`

Completion evidence:

- KQM and the community Energy reference were accessed 2026-08-02 and recorded
  in B-038 with the exact mapping and simulator adaptation.
- The pre-fix implementation has one unconditional
  `OFF_FIELD_PENALTY = 0.6`; active collection, ER scaling, and flat energy are
  separate and remain out of the correction.

### Phase 2: Implement and Regress Party-Size Distribution - Done

Why second:

The shared distributor can implement the sourced rule locally while executable
regression fixes every supported party-size boundary.

Target files:

- `src/java/mechanics/energy/EnergyDistributor.java`
- `src/java/mechanics/energy/EnergyManager.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Replace the constant off-field factor with a private party-size resolver.
- Apply the resolved factor only to off-field particle and orb recipients.
- Add two-, three-, and four-character regression fixtures using distinct
  `CharacterId` values and neutral particles so element matching is isolated.
- Retain active 1.0 collection and add a same-element elemental case to prove
  base-value and party-size multiplication compose correctly.

Acceptance criteria:

- At 100% ER, one neutral particle gives the active character 2.0 energy and
  off-field characters 1.6, 1.4, or 1.2 for party sizes two, three, or four.
- One same-element particle gives a three-character off-field recipient 2.1
  pre-ER energy, while an active same-element recipient receives 3.0.
- Flat energy remains equal for all recipients and no-active distribution still
  returns without notifying or mutating characters.
- Production code depends only on simulator party membership and retains one
  reason to change for particle distribution policy.

Normal tests:

- Neutral particle distribution for two-, three-, and four-character parties.
- Same-element particle distribution in a three-character party.
- Existing four-character energy and reaction regressions.

Abnormal tests:

- Empty simulator particle distribution is a no-op.
- Flat-energy distribution bypasses every party-size multiplier.
- More than four registered characters retain the minimum 0.6 factor rather
  than producing a negative or unsupported extrapolation.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew PartyCatalogRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/mechanics/energy/EnergyDistributor.java --path src/java/mechanics/energy/EnergyManager.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Focused regression proves neutral-particle totals of 2.0 for the active
  recipient and 1.6/1.4/1.2 for off-field recipients in two-/three-/four-member
  parties; a five-member defensive fixture retains 1.2.
- Same-element three-member distribution produces 3.0 active and 2.1 off-field
  energy, while a neutral orb produces 6.0 active and 4.8 off-field energy in a
  two-member party.
- Flat energy remains exactly 3.0 for every three-member recipient, and an
  empty simulator neither distributes energy nor emits a particle notification.
- ReactionRegressionTest, PartyCatalogRegressionTest, build, Javadoc, routed
  validation, and preflight pass.

### Phase 3: Re-Accept Four-Character Energy Baselines - Done

Why last:

Full party runs must prove that deriving the multiplier from party size does not
move established four-character optimizer or rotation results.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run deterministic `RaidenParty` and `FlinsParty2` samples after the change.
- Compare totals, DPS, duration, Burst warnings, and ER output with accepted
  four-character baselines.
- Close B-038 with focused and integration evidence.

Acceptance criteria:

- `RaidenParty` retains 1,317,080 damage / 62,718 DPS over 21.0 seconds.
- `FlinsParty2` retains 14,077,198 damage / 203,722 DPS over 69.1 seconds.
- Neither run gains a new insufficient-energy or optimizer warning.
- Preflight passes and no generated report is staged.

Normal tests:

- One fresh complete run of each deterministic four-character sample.

Abnormal tests:

- Any baseline movement or energy warning keeps the phase open and is traced
  before documentation is accepted.

Verification:

- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
- `python scripts/preflight.py --run`

Completion evidence:

- RaidenParty retains Bennett/Raiden Shogun/Xingqiu/Xiangling ER of
  100%/175%/179%/174% and reports 1,317,080 damage / 62,718 DPS over 21.0
  seconds with no new energy or optimizer warning.
- FlinsParty2 retains Sucrose/Flins/Ineffa/Columbina ER of
  141%/132%/105%/193% and reports 14,077,198 damage / 203,722 DPS over 69.1
  seconds with zero warning matches.
- Both full-party results exactly match their accepted pre-change baselines;
  the derived four-member factor therefore preserves integration behavior.
- The generated `docs/simulation_report.html` update was restored before
  staging, and final preflight passes without artifact leakage.

## Implementation Order: Cooldown Reduction Snapshot and Impetuous Winds

Status:

- Implemented; Phases 1-3 are complete.
- Requirement: Impetuous Winds' existing 5% cooldown-reduction stat must affect
  Skill and Burst readiness using cast-time snapshot semantics.

Scope:

- Resolve percentage cooldown reduction from a character's effective stats
  when a Skill or Burst enters cooldown.
- Store effective single-charge Skill and Burst cooldown end times in
  `CooldownState`.
- Snapshot one cooldown duration while a multi-charge Skill restore queue is
  active, and preserve all cooldown state across simulator save/restore.
- Cover actual Anemo resonance activation, exact readiness boundaries,
  no-resonance behavior, multi-charge scheduling, and snapshot restoration.

Out of scope for this pass:

- Modeling Impetuous Winds' movement-speed or stamina-consumption effects;
  movement and exploration stamina are not simulator DPS state.
- Adding characters, other cooldown-reduction/reset sources, slowing-water
  debuffs, held-Skill timing variants, or cooldown display UI.
- Changing action durations, swap cooldown, internal elemental ICD, particle
  generation ICD, or periodic-event cadence.
- Changing RL actions, observations, privileged tensor layouts, binary
  protocol, training code, or generated capability profiles.
- Editing generated reports or committed `docs/` output.

Definitions:

- Effective cooldown duration: base Skill or Burst cooldown multiplied by
  `1 - CD_REDUCTION`, bounded to a non-negative duration at cast time.
- Cooldown end time: immutable readiness boundary captured when a
  single-charge Skill or Burst is used.
- Active charge duration: the effective duration captured when the first
  pending restore enters a multi-charge queue and reused until that queue is
  empty.

Design boundaries:

- `Character` owns effective-stat lookup and converts typed cooldown reduction
  into an effective duration at action use.
- `CooldownState` owns timestamps, readiness, charge queues, and restoration;
  it does not assemble stats or know elemental resonance.
- `ResonanceManager` remains the source of the permanent typed
  `CD_REDUCTION = 0.05` buff and needs no cooldown-state dependency.
- `SimulatorSnapshot` carries state values only; no cooldown rule or stat
  calculation enters the snapshot container.
- Existing base cooldown getters remain unchanged for metadata, reporting, and
  normalized observations.

### Phase 1: Record Resonance and Cooldown Snapshot Evidence - Done

Why first:

Cooldown scope and snapshot timing are separate mechanic claims and both must
be sourced before shared runtime state changes.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record KQM's maintained Impetuous Winds contract: 5% reduction for all
  Skills and Bursts.
- Record KQM's cooldown rule that duration is calculated when the ability is
  cast and its v1.4 multi-charge experiment showing the first pending charge
  snapshots cooldown reduction for the queue.
- Trace the current dead `CD_REDUCTION` stat from `ResonanceManager` through
  effective stat assembly and prove no cooldown consumer reads it.

Acceptance criteria:

- Material values, source title/date or maintained status, access date, URLs,
  classification, and simulator adaptation are in B-039.
- The state ownership and bounded regression matrix are explicit.
- No production source changes occur in this phase.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds the pre-fix failing
  readiness and restoration cases.

Verification:

- inspect `ResonanceManager`, `Character`, `CooldownState`,
  `SimulatorSnapshot`, and `CombatSimulator.saveSnapshot/restoreSnapshot`
- `python scripts/preflight.py --run`

Completion evidence:

- KQM Elemental Resonance and Cooldowns references were accessed 2026-08-02;
  the snapshot experiment is identified as v1.4, added and last tested
  2021-04-18.
- Repository search finds `CD_REDUCTION` only in `StatType` and the Impetuous
  Winds buff, while readiness currently compares base cooldowns directly.

### Phase 2: Capture Effective Cooldowns in Runtime State - Done

Why second:

The state model must preserve cast-time values before the resonance can be
accepted as executable behavior or restored in branched simulation.

Target files:

- `src/java/model/entity/state/CooldownState.java`
- `src/java/model/entity/Character.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/mechanics/rl/CapabilityProfiler.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add `Character.markSkillUsed` and `markBurstUsed` overloads that merge
  simulator-applicable buffs and pass an effective duration into
  `CooldownState`; retain the existing overloads for isolated fixtures.
- Record exact end times for single-charge Skill and Burst uses; readiness and
  remaining-time queries read those end times.
- For multi-charge Skills, capture one effective duration when an empty queue
  receives its first pending restore and reuse it for later charges until all
  pending restores complete.
- Extend character snapshots with cooldown end times and active charge
  duration, then restore them without recomputation.
- Preserve those fields when the existing capability profiler composes a
  simulator snapshot; do not change profile behavior or generated data.
- Add actual-resonance, exact boundary, no-resonance, multi-charge snapshot,
  and simulator save/restore regressions.

Acceptance criteria:

- A 10-second Skill under Impetuous Winds is unavailable at 9.499 seconds and
  ready at 9.5; a 20-second Burst is unavailable at 18.999 and ready at 19.0
  after energy is restored.
- Without the resonance, the same abilities retain exact 10- and 20-second
  readiness boundaries.
- A multi-charge queue started under 5% reduction retains 9.5-second restore
  durations for later charges even after a temporary source expires.
- Snapshot restore reproduces remaining Skill/Burst times and charge restore
  times exactly.
- Cooldown reduction cannot produce a negative effective duration, and base
  cooldown metadata remains unchanged.

Test cases to add or update:

- Normal: two distinct Anemo members activate the actual resonance buff.
- Boundary: 9.499/9.5-second Skill and 18.999/19.0-second Burst checks.
- No-trigger: one Anemo member retains base cooldowns.
- Snapshot: a temporary reduction expires after cast without extending the
  captured cooldown, including a second pending charge.
- Restore: save, advance, restore, and compare all cooldown state values.
- Defensive: reduction above 100% clamps to an immediate, non-negative end.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew PartyCatalogRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/entity/state/CooldownState.java --path src/java/model/entity/Character.java --path src/java/simulation/SimulatorSnapshot.java --path src/java/simulation/CombatSimulator.java --path src/java/mechanics/rl/CapabilityProfiler.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Actual two-Anemo resonance produces 5% cooldown reduction: a 10-second Skill
  is ready at 9.5 seconds and a 20-second Burst at 19.0, with pre-boundary
  checks remaining unavailable and base metadata unchanged.
- The one-Anemo no-trigger case retains exact 10- and 20-second boundaries.
- A temporary 5% source starts a two-charge queue at restore times 9.5 and
  10.5; the second use after buff expiry reuses the captured 9.5-second
  duration.
- Simulator restore recovers single Skill/Burst end times, both charge restore
  times, and the active charge duration; an over-100% defensive input clamps to
  immediate readiness without negative time.
- ReactionRegressionTest, PartyCatalogRegressionTest, build, Javadoc, routed
  validation, and preflight pass.

### Phase 3: Accept Simulator Integration and Close B-039 - Done

Why last:

Catalog and sample runs must show the shared cooldown representation remains
compatible where no current party activates Anemo resonance.

Target files:

- `src/java/model/character/Bennett.java`
- `src/java/model/character/Columbina.java`
- `src/java/model/character/Flins.java`
- `src/java/model/character/Ineffa.java`
- `src/java/model/character/RaidenShogun.java`
- `src/java/model/character/Sucrose.java`
- `src/java/model/character/Xiangling.java`
- `src/java/model/character/Xingqiu.java`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Migrate every production Skill/Burst cooldown mark to pass the simulator's
  applicable buffs into the Phase 2 overload.
- Run focused regression, catalog validation, and deterministic `RaidenParty`
  and `FlinsParty2` samples after the caller migration.
- Confirm current catalog parties contain at most one Anemo member and retain
  their accepted cooldown, ER, warning, and damage behavior.
- Close B-039 with focused state and full-simulator evidence.

Acceptance criteria:

- Party catalog validation passes without party-order or RL-registry changes.
- `RaidenParty` retains 1,317,080 damage / 62,718 DPS over 21.0 seconds.
- `FlinsParty2` retains 14,077,198 damage / 203,722 DPS over 69.1 seconds.
- No sample gains a cooldown, energy, or optimizer warning; no generated report
  is staged.

Test cases to add or update:

- No further production test; Phase 2 owns active Anemo-resonance and snapshot
  boundaries, while Phase 3 owns unaffected catalog integration.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew PartyCatalogRegressionTest`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
- `python scripts/preflight.py --run`

Completion evidence:

- Bennett, Columbina, Flins, Ineffa, Raiden Shogun, Sucrose, Xiangling, and
  Xingqiu pass simulator-applicable buffs into every production Skill/Burst
  cooldown start; Flins's form Skill branch that intentionally starts no new
  cooldown remains unchanged.
- ReactionRegressionTest, PartyCatalogRegressionTest, and build pass after the
  caller migration.
- RaidenParty retains Bennett/Raiden Shogun/Xingqiu/Xiangling ER of
  100%/175%/179%/174% and 1,317,080 damage / 62,718 DPS over 21.0 seconds with
  zero warning matches.
- FlinsParty2 retains Sucrose/Flins/Ineffa/Columbina ER of
  141%/132%/105%/193% and 14,077,198 damage / 203,722 DPS over 69.1 seconds
  with zero warning matches.
- The generated `docs/simulation_report.html` update was restored before
  staging, and final preflight passes without artifact leakage.

## Implementation Order: Sacrificial Sword Composed Passive

Status:

- Implemented; Phases 1-2 are complete.
- Requirement: R5 Sacrificial Sword must have an 80% chance to end its wielder's
  Skill cooldown after Skill damage, no more than once every sixteen seconds.

Scope:

- Add a focused Skill-cooldown reset operation to `CooldownState` and expose it
  through `Character`.
- Implement Sacrificial Sword as a damage-triggered weapon with an injectable
  draw source, R5 probability, and internal cooldown.
- Reset the single Skill end time or the earliest pending multi-charge restore,
  matching the currently displayed cooldown timer.
- Cover probability and cooldown boundaries, action typing, multi-hit retry,
  ready-Skill behavior, and multi-charge reset scope.

Out of scope for this pass:

- Adding Sacrificial Sword to a catalog party or changing any party loadout,
  optimizer configuration, rotation, or accepted damage baseline.
- Modeling enemy shields; the simulator has no enemy shield state, so the
  sourced shielded-target non-proc exception cannot be represented here.
- Modeling multiple enemies or one probability trial per enemy.
- Implementing the Sacrificial Bow, Greatsword, or Fragments.
- Adding reset effects for other weapons, artifacts, characters, Burst
  cooldowns, elemental application ICD, or particle-generation ICD.
- Changing RL tensors, protocol, training, capability profiles, generated
  reports, or committed `docs/` output.

Definitions:

- Composed proc: one successful R5 draw after positive direct Skill damage that
  resets the wielder's applicable Skill cooldown and starts a sixteen-second
  weapon cooldown.
- Displayed charge timer: the earliest pending restore in a multi-charge Skill
  queue; resetting it leaves later pending restores intact.

Design boundaries:

- `CooldownState` owns reset mutation and charge-queue ordering; it does not
  know weapons, damage, or randomness.
- `Character` exposes reset intent without exposing mutable cooldown internals.
- Sacrificial Sword owns eligibility, probability, injected randomness, and
  its weapon cooldown through `DamageTriggeredWeaponEffect`.
- Generic damage dispatch remains unchanged and invokes only capability-bearing
  weapons.

### Phase 1: Record Composed and Reset-Scope Evidence - Done

Why first:

The passive wording, R5 values, per-hit behavior, and multi-charge reset scope
must be sourced before adding a shared cooldown mutation.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the maintained weapon description and community weapon page for R5
  80% probability, sixteen-second cooldown, Lv90 stats, and Skill-damage
  trigger wording.
- Record KQM evidence that multi-hit Skills can retry, a proc may occur when the
  Skill is already ready, and multi-charge users reset only the displayed
  earliest cooldown.
- Trace the current stat-only `SacrificialSword` and confirm no generic Skill
  reset operation exists.

Acceptance criteria:

- Source titles/versions or maintained status, experiment dates, access date,
  URLs, classification, and simulator limitations are recorded in B-040.
- The owning state and weapon boundaries and exact test matrix are explicit.
- No production source changes occur in this phase.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds the failing reset and
  weapon cases.

Verification:

- inspect `SacrificialSword`, `DamageTriggeredWeaponEffect`,
  `CombatActionResolver`, `Character`, and `CooldownState`
- `python scripts/preflight.py --run`

Completion evidence:

- KQM Swords and Sacrificial-series evidence plus the community Sacrificial
  Sword page were accessed 2026-08-02 and recorded in B-040.
- The pre-fix weapon has only 454 base ATK and 61.3% ER; repository search finds
  no Skill-cooldown reset API or Composed dispatch.

### Phase 2: Implement, Regress, and Close Composed - Done

Why second:

The narrow state reset and the only caller land together so no unused mutable
API is introduced.

Target files:

- `src/java/model/entity/state/CooldownState.java`
- `src/java/model/entity/Character.java`
- `src/java/model/weapon/SacrificialSword.java`
- `src/java/model/weapon/AGENTS.md`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Add a Skill reset that sets a single-charge end to the reset time or removes
  only the earliest pending multi-charge restore.
- Implement R5 Composed through `DamageTriggeredWeaponEffect` with a default
  stochastic source and a non-null injected `DoubleSupplier` constructor.
- Draw only for positive direct Skill damage when the weapon cooldown is ready;
  use a strict draw-below-0.8 success boundary and start cooldown on success.
- Add actual-weapon regression for trigger, no-trigger, multi-hit retry,
  internal cooldown, exact boundary, ready Skill, and multi-charge scope.
- Update the weapon catalog description and close B-040 after full validation.

Acceptance criteria:

- Draw 0.799999 resets a pending Skill; draw 0.8 does not.
- A failed first Skill hit may succeed on a later hit, while a successful hit
  suppresses every trial before sixteen seconds and allows one at exactly
  sixteen seconds.
- Non-Skill or zero direct damage never draws or resets.
- A proc while Skill is already ready consumes the weapon cooldown without
  creating negative or future Skill state.
- Multi-charge reset removes only the earliest restore and preserves later
  entries and their captured duration.
- Existing weapon stats remain 454 base ATK and 61.3% ER; catalog parties and
  accepted outputs remain unaffected because none equips the weapon.

Test cases to add or update:

- Normal: successful single-charge R5 reset and second Skill availability.
- Probability: exact failure at 0.8 and success just below it.
- Multi-hit: failed first hit, successful second hit, then no extra draw.
- Cooldown: 15.999-second suppression and exact 16.0-second eligibility.
- Type/no-damage: Normal/Burst and zero-damage Skill do not trigger.
- Ready state: eligible success consumes weapon cooldown with a no-op reset.
- Charges: only earliest pending restore is removed.
- Constructor: null draw source fails immediately.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew PartyCatalogRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/entity/state/CooldownState.java --path src/java/model/entity/Character.java --path src/java/model/weapon/SacrificialSword.java --path src/java/model/weapon/AGENTS.md --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- An actual damage-dispatch Skill hit with draw 0.799999 resets a pending
  cooldown; draw 0.8 fails and leaves it pending. The weapon retains 454 base
  ATK and 61.3% ER.
- A failed first Skill hit retries and succeeds on the second, after which
  same-time and 15.999-second events consume no draw; exact 16.0 seconds is
  eligible and resets again.
- Normal and zero-motion-value Skill actions consume no draw. A proc while the
  Skill is ready starts weapon cooldown and suppresses a newly pending Skill at
  one second.
- A two-charge proc removes only restore time 10.0 while preserving 11.0 and
  the captured ten-second duration; null draw injection fails immediately.
- ReactionRegressionTest, PartyCatalogRegressionTest, build, Javadoc, routed
  validation, and preflight pass. No catalog party equips Sacrificial Sword, so
  no accepted sample baseline changes.

## Implementation Order: Viridescent Venerer Shred Refresh

Status:

- In progress; Phase 1 is complete and Phases 2-3 remain.
- Requirement: each Swirled element must have one independently refreshed 40%
  Viridescent Venerer RES shred, applied only by its on-field equipping trigger.

Scope:

- Require the VV owner to be both the Swirl trigger and active character.
- Store VV shred as simulator-managed typed team buffs rather than duplicate
  character-owned instances.
- Replace the same element's typed buff on reapplication while preserving
  independent Pyro, Hydro, Cryo, and Electro durations.
- Cover owner/field eligibility, same-element refresh, exact expiry, different
  elements, and accepted Flins party outputs.

Out of scope for this pass:

- Changing reaction damage/formula ordering so the first single-target Swirl
  benefits from the newly applied shred; that separately forbidden change is
  recorded as deferred B-042.
- Modeling multiple enemies, Swirl AoE propagation, ping, hitlag extension, or
  enemy-specific debuff state.
- Changing the 2pc Anemo bonus, 4pc 60% Swirl bonus, resistance formula, aura
  rules, artifact allocation, rotations, or party loadouts.
- Changing RL tensors, protocol, training, capability profiles, generated
  reports, or committed `docs/` output.

Definitions:

- Element-specific VV buff: one typed `BuffId.VV_SHRED_<ELEMENT>` simulator
  team buff that contributes 0.40 matching resistance shred for ten seconds.
- Refresh: remove the prior buff with the same typed ID and insert one new
  ten-second window without summing their stat values.

Design boundaries:

- `ViridescentVenerer` owns reaction eligibility, element mapping, source
  attribution, and buff construction.
- `BuffManager.applyTeamBuffNoStack` owns typed replacement; the artifact does
  not mutate each character's active-buff list directly.
- `ResistanceCalculator` and reaction formula ordering remain unchanged.
- Different element IDs remain independently active and expire on their own
  timelines.

### Phase 1: Record VV Eligibility and Duration Evidence - Done

Why first:

Same-element refresh and different-element coexistence must be distinguished
before replacing currently stacked buffs.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the maintained KQM VV description: 40% matching RES shred for ten
  seconds, multiple elements active with independent durations.
- Record KQM v1.5 trigger tests requiring the equipping character to trigger
  the Swirl while on field.
- Trace current per-character `addBuff` calls and prove repeated same-element
  Swirls leave multiple active buffs with the same typed ID.
- Record the immediate-first-Swirl formula-order gap separately as B-042.

Acceptance criteria:

- Values, eligibility, independent-element rule, test dates, access date,
  source URLs, classification, and simulator adaptation are recorded.
- B-041 has a bounded artifact/buff-manager solution and B-042 preserves the
  explicitly excluded formula-order issue.
- No production source changes occur in this phase.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds the pre-fix failing
  stack, eligibility, and refresh cases.

Verification:

- inspect `ViridescentVenerer`, `ReactionAwareArtifact`, `BuffManager`,
  `SimulationEventDispatcher`, and `ResistanceCalculator`
- `python scripts/preflight.py --run`

Completion evidence:

- KQM Artifacts and artifact evidence were accessed 2026-08-02. Trigger tests
  are recorded as v1.5, added and last tested 2021-05-22.
- Current code creates a new same-ID buff on every eligible Swirl and appends it
  to every character without removal, making stat assembly additive.

### Phase 2: Replace Same-Element Shred and Regress Boundaries

Why second:

The typed replacement behavior and all local boundaries must be proven before
optimizer-driven party baselines are re-accepted.

Target files:

- `src/java/model/artifact/ViridescentVenerer.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Require `triggerCh == owner` in addition to the existing on-field condition.
- Route the source-attributed element buff through
  `CombatSimulator.applyTeamBuffNoStack`.
- Add actual-artifact regressions for same-element no-stack refresh, exact
  expiry, different-element coexistence, off-field owner, and different trigger.

Acceptance criteria:

- Repeated same-element Swirls produce exactly 0.40 shred, never 0.80 or more.
- A second Swirl at five seconds refreshes the element through 14.999 seconds
  and it is absent at exactly 15.0 seconds.
- Pyro and Hydro shreds coexist at 0.40 each with independent typed IDs.
- Off-field owner or a different trigger produces no VV shred.
- One non-VV subsequent elemental hit observes the refreshed 0.40 shred.

Test cases to add or update:

- Normal: on-field owner-triggered Pyro Swirl and subsequent Pyro hit.
- Refresh: same element at 0 and 5 seconds, exact 15-second expiry.
- Multi-element: simultaneous Pyro and Hydro typed values remain independent.
- No-trigger: owner off field and active owner who did not trigger the Swirl.
- Defensive: unsupported non-Swirl/non-swirlable elements add no buff.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew PartyCatalogRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/artifact/ViridescentVenerer.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

### Phase 3: Re-Accept VV Party Baselines

Why last:

Both deterministic Flins parties equip VV and must expose the full numerical
effect of removing illegal same-element stacking.

Target files:

- `README.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh complete `FlinsParty` payloads and compare normalized logs.
- Run two fresh complete `FlinsParty2` payloads and compare normalized logs.
- Record ER, warnings, duration, total, DPS, and normalized hashes; replace
  accepted README baselines if the sourced correction moves them.
- Close B-041 without claiming resolution of deferred B-042.

Acceptance criteria:

- Each party's repeated normalized payloads match and contain no new energy or
  optimizer warning.
- Numerical deltas are attributed to removing same-element VV stacking, not
  hidden rotation or loadout changes.
- README, plan, and ledger agree on both accepted baselines and B-042 remains
  visibly deferred.
- Preflight passes and no generated report is staged.

Test cases to add or update:

- No further production test; Phase 2 owns mechanic boundaries and Phase 3 owns
  deterministic full-party acceptance.

Verification:

- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/preflight.py --run`

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
