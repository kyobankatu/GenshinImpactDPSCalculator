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

The prior simulator content campaigns, including Skill-focused event weapons,
are complete; RL and generated docs remain excluded.

The B-151 target-state and Skill-hit artifact campaign is complete. It adds
Lavawalker, Thundersoother, and Tenacity of the Millelith without entering
player-damage, shield-absorption, RL, or generated-documentation systems.

The B-152 Black Sword campaign is complete. It adds the weapon's full
outgoing-damage contract while keeping player healing in Deferred Systems.

The B-153 static five-star weapon campaign is complete. It adds five exact
always-on passive branches while preserving explicit unreachable-trigger
boundaries.

The B-154 Millennial Movement campaign is complete. Elegy for the End,
Freedom-Sworn, and Song of Broken Pines now share typed sigil, same-effect
replacement, and snapshot-safe weapon state contracts.

The B-155 through B-157 parallel content campaigns are complete. The latest
wave added the Golden Majesty weapon family, six legacy boundary artifact sets,
and Razor through isolated implementation lanes; RL and generated
documentation remained excluded.

The B-162 through B-169 follow-on content and snapshot campaigns are complete.
The latest campaign adds Collei's stationary single-target reaction slice; RL,
generated docs, and deferred healing, defensive, player-damage, or geometry
systems remain excluded.

B-170 is the active classic Klee campaign. It first adds the shared enemy DEF
reduction formula, then a bounded stationary single-target character slice;
RL, generated docs, Hexerei, and Deferred Systems remain excluded.

The B-158 derived-stat equipment and Fischl wave is complete. It adds reusable
final-DEF/EM conversion, two five-star weapons, four asset-backed artifact sets,
and Fischl through bounded branch-isolated lanes.

The B-128 action-use artifact campaign is complete. Successful typed actions
now reach equipped artifacts, and Heart of Depth plus Martial Artist use the
shared callback without changing RL or generated documentation.

The B-129 Husk of Opulent Dreams campaign is complete. It adds the exact
field-aware Curiosity gain and decay cadence through existing simulator hooks.

The B-131 supported-character accuracy campaign is complete. It corrects
Bennett A1, adds Xiangling C2, and fixes Xingqiu C2/C4/C6 behavior; RL remains
excluded.

The B-132 Raiden constellation pass is complete. It adds C1 Resolve gain
modifiers and C4's ally-only ATK window at normal or early Musou Isshin end.

The B-133 Flins constellation campaign is complete. It corrects A1/C1,
implements C2-C6, fixes N4 per-hit multipliers, and resolves provider buffs in
weighted Lunar reaction damage.

The B-058 Burning fuel correction is complete. It replaces the fixed
two-second approximation with typed Dendro-fuel decay and refresh ownership
while retaining the repository's single-target boundary.

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

The B-041 Viridescent Venerer correction is complete. Same-element 40% RES
shred applications refresh one ten-second typed debuff instead of stacking, and
only an on-field equipping owner who triggered the Swirl may apply it. The
separate first-Swirl formula-order gap remains deferred as B-042.

The B-043 Noblesse Oblige correction is complete. Its teamwide 20% ATK buff
refreshes one twelve-second typed window instead of stacking repeated or
multi-wearer applications.

The B-044 Raiden Eye correction is complete. Recasting Transcendence: Baleful
Omen refreshes each member's one 25-second Burst DMG buff instead of adding
another same-ID value during the overlap.

The B-045 Silken Moon's Serenade correction is complete. Its Lunar Reaction
bonus is derived dynamically from the party's distinct active Gleaming Moon
effects through an artifact team-buff provider rather than the obsolete manager
scan that always resolved to zero.

The B-046 Ascendant Blessing expiry correction is complete. An expired stronger
Blessing no longer blocks a weaker non-Moonsign Skill or Burst from establishing
a new 20-second non-stacking window.

The B-047 Guoba C1 correction is complete. Each Guoba hit refreshes one
six-second 15% Pyro RES reduction visible to every attacker's live stat
resolution instead of stacking active-character-only field buffs.

The B-048 resistance correction is complete. Standard, Lunar, immediate
reaction, delayed reaction, and weighted party damage now resolve matching
enemy RES reduction at impact without retaining it in attacker snapshots.

The B-049 standard aura-tax and decay correction is complete. Ordinary source
application now uses taxed aura units, source-class decay, and sourced
same-element extension semantics through the enemy-owned aura model.

The B-050 Anemo/Geo reaction-consumption correction is complete. Swirl and both
Crystallize variants now consume half of the trigger source gauge and preserve
the decayed residual aura.

The B-051 Overload/Superconduct residual correction is complete. Both reactions
now retain positive decayed aura after their sourced 1.0 trigger-gauge
consumption and rely on the shared enemy state for full depletion.

The B-052 directional Bloom correction, B-053 Aubade initialization/stat
correction, B-054 Night-only Gleaming Moon provider, and B-055 deterministic
optimizer rendering are complete. The current B-056 pass addresses standard
Electro-Charged premature expiry ticks; Lunar-Charged behavior remains frozen.

The B-056 through B-063 reaction-state passes are complete. The current B-064
pass adds Overload's target-wide 0.1-second and owner-specific 0.5-second damage
limits while preserving every reaction notification and gauge transition.

The B-064 Overload, B-066 standard Crystallize, B-067 Superconduct, B-068
Shatter, and B-069 standard Electro-Charged refresh passes are complete. Active
standard reapplications now refresh the next tick's typed owner and damage
snapshot without dealing another immediate damage instance.

The B-070 pass is complete. Standard Electro-Charged immediate and periodic
damage now share a snapshot-safe 0.5-second target cooldown across sequences.

The B-071 pass is complete. Per-element target and owner Swirl damage sequences
now retain reaction notification and Aura consumption.

The B-072 pass is complete. The existing Dendro Core two-hit damage-cap history
is snapshot-safe, so rollback branches cannot retain future hit decisions.

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

- Complete; all three phases are verified and pushed.
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

### Phase 2: Replace Same-Element Shred and Regress Boundaries - Done

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

Completion evidence:

- `ViridescentVenerer` now requires the owner to be both active and the Swirl
  trigger, creates one source-attributed typed buff for the Swirled element, and
  delegates same-ID replacement to `applyTeamBuffNoStack`.
- The regression proves one 0.40 Pyro value across a five-second refresh, active
  state at 14.999 seconds, exact expiry at 15.0 seconds, independent Pyro/Hydro
  windows, off-field and wrong-trigger rejection, unsupported reaction defense,
  and the expected 1.15/0.90 resistance multiplier on a subsequent Pyro hit.
- Reaction and party-catalog regressions, build, Javadoc, routed validation, and
  preflight pass.

### Phase 3: Re-Accept VV Party Baselines - Done

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

Completion evidence:

- Two `FlinsParty` runs produced identical complete logs and normalized SHA-256
  `bb6bc281eeb54ad747502b4bc6259715b9d540e1e225735982ea8e30301c26fd`.
  ER remains Sucrose 109%, Flins 100%, Ineffa 100%, and Columbina 180%, with
  zero warning matches and 18,343,092 damage / 184,353 DPS over 99.5 seconds.
- Two `FlinsParty2` runs produced identical complete logs and normalized SHA-256
  `95d03747cc7e917445a2b840db0bb2cbad095ecb211064d895bc6ea4c68c798a`.
  ER remains Sucrose 141%, Flins 132%, Ineffa 105%, and Columbina 193%, with
  zero warning matches and 13,633,123 damage / 197,296 DPS over 69.1 seconds.
- Relative to the pre-fix baselines, the totals decrease by 500,598 (2.66%) and
  444,075 (3.15%), respectively, with no rotation, loadout, ER, or duration
  change. The deltas are therefore accepted as removal of illegal same-element
  VV stacking. The generated committed report was restored and is not staged.
- README, plan, and ledger retain B-042 as a separate deferred formula-order
  issue; this phase does not claim immediate first-Swirl shred.

## Implementation Order: Noblesse Oblige Non-Stack Refresh

Status:

- Complete; all three phases are verified and pushed.
- Requirement: 4pc Noblesse Oblige must contribute one teamwide 20% ATK window
  for twelve seconds, refreshed rather than added by another application.

Scope:

- Replace the existing typed Noblesse team buff through the simulator's
  no-stack API.
- Preserve the 20% value, twelve-second duration, 2pc Burst DMG bonus, teamwide
  targeting, and burst-trigger path.
- Cover repeated use, exact refresh expiry, multi-wearer behavior, typed scope,
  actual Bennett activation, and the accepted Raiden party output.

Out of scope for this pass:

- Changing Bennett's Burst damage, field ATK buff, healing, energy, cooldown,
  animation, rotation, artifact allocation, or constellation behavior.
- Changing other artifact set stacking rules or the
  `BurstTriggeredArtifactEffect` interface.
- Changing damage formula order, buff source-stack infrastructure, RL paths,
  generated reports, or committed `docs/` output.

Definitions:

- Noblesse window: one simulator-owned `BuffId.NOBLESSE_OBLIGE_4PC` team buff
  active over `[startTime, startTime + 12.0)` and contributing 0.20 ATK%.
- Refresh: remove the previous typed Noblesse window and add a new twelve-second
  window without summing their ATK values.

Design boundaries:

- `NoblesseOblige` owns construction and burst-trigger dispatch of its set
  effect.
- `BuffManager.applyTeamBuffNoStack` owns same-ID replacement and keeps
  unrelated typed or custom ATK buffs independent.
- Existing character action and source-attribution context remain unchanged.

### Phase 1: Record Noblesse Non-Stack Evidence - Done

Why first:

The explicit non-stack rule must be recorded before changing current additive
team-buff behavior.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the maintained set description: Burst use grants all party members 20%
  ATK for twelve seconds and the effect cannot stack.
- Record the maintained KQM artifact guidance that duplicate 4pc Noblesse buffs
  do not grant double effect.
- Trace the current normal `applyTeamBuff` call and prove same-ID applications
  remain simultaneously applicable and additive.

Acceptance criteria:

- Value, duration, non-stack rule, access date, source URLs, classification, and
  simulator adaptation are recorded.
- The implementation is bounded to the artifact's existing typed team buff and
  existing simulator replacement policy.
- No production source changes occur in this phase.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds the pre-fix failing
  duplicate and refresh cases.

Verification:

- inspect `NoblesseOblige`, `BuffManager`, `Bennett`, and
  `BurstTriggeredArtifactEffect`
- `python scripts/preflight.py --run`

Completion evidence:

- The maintained Genshin Impact Wiki Noblesse page and KQM Artifacts guide were
  accessed 2026-08-02. Both state that the 20% twelve-second 4pc effect does not
  stack; KQM explicitly uses duplicate Noblesse wearers as its example.
- Classification: adopt the non-stack rule and adapt it to one typed
  simulator-owned team buff. Current `applyTeamBuff` storage leaves duplicate
  `NOBLESSE_OBLIGE_4PC` instances additive during stat assembly.

### Phase 2: Replace Duplicate Noblesse Windows and Regress Boundaries - Done

Why second:

The artifact-local behavior and exact time window must be settled before
accepting the catalog party baseline.

Target files:

- `src/java/model/artifact/NoblesseOblige.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Route the typed 4pc buff through `CombatSimulator.applyTeamBuffNoStack`.
- Add an actual Bennett Burst regression for initial teamwide activation.
- Add repeated and multi-instance artifact applications proving one refreshed
  0.20 value and exact expiry.
- Prove an unrelated ATK% buff remains independently additive.

Acceptance criteria:

- One Burst applies exactly 0.20 ATK% to both owner and ally.
- A second Noblesse application at five seconds remains exactly 0.20, keeps one
  typed instance, is active at 16.999 seconds, and expires at exactly 17.0.
- Separate Noblesse artifact instances refresh rather than stack.
- An unrelated typed/custom ATK buff still combines with Noblesse normally.
- The 2pc Burst DMG bonus remains exactly 0.20.

Test cases to add or update:

- Normal: actual Bennett Burst with owner and ally stat resolution.
- Refresh: same artifact at zero and five seconds with exact expiry boundary.
- Multi-wearer proxy: a second Noblesse instance applies during the first
  window and leaves one 0.20 typed effect.
- Scope: an unrelated 0.10 ATK% buff plus Noblesse resolves to 0.30.
- Static: constructor retains 0.20 Burst DMG bonus.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew PartyCatalogRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/artifact/NoblesseOblige.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Noblesse now routes its existing typed 4pc team buff through
  `applyTeamBuffNoStack`; values, duration, targeting, constructor stats, and
  Burst dispatch remain unchanged.
- The regression proves actual Bennett owner/ally activation, one 0.20 value
  after same-instance and separate-instance reapplication, one live typed buff,
  coexistence with an unrelated 0.10 ATK buff, active state at 16.999 seconds,
  exact expiry at 17.0 seconds, and the unchanged 0.20 2pc Burst DMG bonus.
- Reaction and party-catalog regressions, build, Javadoc, routed validation, and
  preflight pass.

### Phase 3: Re-Accept the Noblesse Catalog Baseline - Done

Why last:

RaidenParty equips Noblesse on Bennett and is the smallest deterministic
catalog integration check for preserving ordinary one-wearer rotations.

Target files:

- `README.md` only if the accepted value changes
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh complete `RaidenParty` payloads and compare normalized logs.
- Record ER, warnings, duration, total, DPS, and normalized hash.
- Close B-043 and retain unrelated deferred systems unchanged.

Acceptance criteria:

- Repeated normalized payloads match and contain no new energy or optimizer
  warning.
- A normal single-Noblesse rotation remains unchanged unless the corrected
  overlap path is actually exercised.
- Plan and ledger agree on the accepted baseline and no generated output is
  staged.

## Implementation Order: Quicken-Fueled Burning

Objective: allow Pyro to start Burning from typed Quicken and make active
Burning replace natural decay for both coexisting Dendro-like gauges.

Scope:

- Quicken-only Pyro Burning in the single-target resolver.
- Shared special decay for typed Quicken and ordinary Dendro during Burning.
- Exact depletion, latest-owner damage, live resistance, and no-late-tick tests.
- Deterministic catalog-party controls.

Out of scope:

- Electro/Cryo plus Quicken simultaneous priority and trigger residual gauge.
- The separate 2U Burning Aura, Burning Pyro application ICD, AoE, and hitlag.
- RL learner/service/protocol changes and persistent jobs.

Cross-cutting rules:

- `CombatActionResolver` only selects a sourced reaction; the scheduler owns
  special decay and damage cadence; typed state APIs own gauge arithmetic.
- Derive Burning fuel from the larger current Dendro/Quicken gauge at initial
  creation; a Dendro refresh retains B-058 overwrite semantics.
- Replace natural decay, do not add 0.4U/s on top of it; synchronize both gauges
  once per event interval and emit one damage tick.
- Keep the Quicken-only synthetic path restricted when other reactive Auras are
  present; unresolved simultaneous priority remains explicit.
- Follow source style, explicit staging, and artifact safety rules.

### Phase 1: Record Quicken-Burning Evidence and Event Math - Done

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record Quicken-as-Dendro and simultaneous-consumption evidence.
- Inventory resolver visibility, scheduler fuel selection, event decay, and
  existing typed state operations.
- Define the reactive-coexistence exclusion.

Acceptance criteria:

- The plan distinguishes initial fuel selection from later Dendro overwrite.
- Special decay explicitly replaces each source's natural rate.
- No behavior changes occur in this phase.

Test cases to add or update:

- No production test; Phase 2 owns focused mechanics.

Verification:

- inspect Quicken/Burning resolver, scheduler, state, priority, and regression paths
- `python scripts/preflight.py --run`

Completion evidence:

- KQM and the maintained priority reference define Quicken as Dendro for Pyro
  and require simultaneous Quicken/Dendro consumption. gcsim independently
  applies one Burning-fuel decay rate to both typed gauges; sources are recorded
  in B-060.
- Inventory confirms typed Quicken is absent from resolver Aura iteration and
  the Burning scheduler currently exits whenever Dendro is absent. Its target-
  remaining sync also assumes Dendro is the only fuel.
- The replacement math subtracts only `special rate - natural rate` over each
  event interval, preventing natural and special decay from being added. Initial
  fuel uses the larger current gauge while a Dendro refresh retains B-058's
  overwrite rule.
- Electro/Cryo coexistence and trigger-residual priority remain explicit
  exclusions. Documentation preflight passes without routed checks or leaks.

### Phase 2: Consume Quicken as Burning Fuel - Done

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Trigger Burning for Pyro on typed Quicken when no ordinary Aura competes.
- Start initial fuel from max(current Dendro, current Quicken); retain Dendro
  overwrite behavior for Dendro refreshes.
- On each event wake, subtract only the extra amount needed beyond each gauge's
  natural decay so both follow the shared special rate.
- Stop when both fuels deplete and retain latest owner/live resistance.

Acceptance criteria:

- Pyro on 0.8U Quicken starts Burning without immediate damage and produces
  eight 0.25-second ticks over two seconds while Quicken reaches zero.
- Equal coexisting Dendro/Quicken gauges both reach zero together with one tick
  stream; unequal smaller gauge may deplete first while the larger sustains it.
- Dendro refresh overwrites shared fuel and subsequent dual decay remains
  deterministic.
- Exact expiry has no late tick and stale generations remain silent.
- Existing Dendro-only B-058 and Quicken Bloom/Additive contracts remain exact.

Test cases to add or update:

- Quicken-only: first boundary, eight ticks, exact clear, no ninth tick.
- Coexistence: equal and unequal gauge depletion under one event.
- Refresh: Dendro overwrite while Quicken coexists.
- Dynamic: latest owner, live RES activation/expiry, stale generation.
- Abnormal: exact-expired Quicken applies ordinary Pyro without Burning.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/mechanics/reaction/ReactionEffectScheduler.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- The resolver now exposes typed Quicken to Pyro only when no ordinary Aura
  competes. Dendro+Quicken continues through the ordinary Dendro reaction path,
  so both cases emit one Burning reaction and one timer generation.
- Initial shared fuel is the larger current Dendro/Quicken gauge. Each event
  subtracts only the difference between the shared special rate and each
  gauge's existing natural rate; epsilon cleanup removes exact floating-point
  residue without deleting meaningful remaining gauge.
- Focused regressions prove eight 0.25-second ticks from 0.8U Quicken, exact
  no-ninth-tick cleanup, equal and unequal coexistence, Dendro overwrite while
  Quicken coexists, and ordinary Pyro application at exact Quicken expiry.
- Existing latest-owner, live-RES, stale-generation, Dendro-only Burning,
  Quicken Bloom, and Additive contracts pass unchanged.
- `ReactionRegressionTest`, `build`, `javadoc`, routed validation, and full
  preflight pass. No persistent service, RL process, or HPC job was started.

### Phase 3: Re-Accept Quicken-Burning-Neutral Baselines - Done

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls for each catalog party against B-059.
- Record hashes, values, ER, cadence, warnings, and absence of affected reactions.
- Update the documented Dendro boundary and close B-060.

Acceptance criteria:

- All six runs match B-059 exactly with no Quicken/Burning events or warnings.
- Focused and full validation pass; tracked generated report is restored.
- Plan, ledger, README, and checkpoint agree.

Test cases to add or update:

- Normal: pairwise exact catalog runs.
- No-change: accepted totals/ER/cadence.
- Abnormal: no warning or artifact leak.

Verification:

- two fresh `./gradlew RaidenParty` runs
- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh no-daemon runs per catalog party exactly match B-059 after removing
  only Gradle's elapsed-success line: Raiden
  `e52e586cca64148195ad8dc9ab9f0827922a7f01f931faedb0a6ecbab7100dda`,
  Flins `6338bcc75a29a52f3245cb4573823ba1245724d3be60064dcebefa7b38aa03ab`,
  and Flins2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
- Totals/DPS remain 1,365,787/65,037, 22,675,823/227,898, and
  15,817,125/228,902. ER and timed/reaction/delayed/ICD counts remain exactly
  100/175/179/174 with 152/55/11/38, 109/100/100/180 with 613/230/48/88, and
  130/128/100/196 with 468/140/33/71.
- Every run contains zero Quicken/Aggravate/Spread/Bloom/Burning reaction lines
  and zero warning/error/failed-action/insufficient-energy lines.
- README records Quicken as shared Burning fuel and removes that item from known
  differences. The tracked generated report was restored and no output is
  staged.

## Implementation Order: Consumable Quicken Aura

Objective: replace Quicken's unconditional expiry timestamp with a typed Aura
gauge that decays, refreshes by gauge strength, and participates in Hydro Bloom
without being consumed by Aggravate or Spread.

Scope:

- Quicken gauge, decay rate, last update, exact expiry, and snapshot payload.
- Creation from the smaller existing/trigger gauge and stronger-only refresh.
- Hydro Bloom on Quicken-only targets and simultaneous Dendro/Quicken
  consumption with one core/reaction notification.
- Aggravate/Spread non-consumption and catalog-party no-change controls.

Out of scope:

- Pyro consuming Quicken into Burning fuel and its simultaneous reaction order.
- Quicken as B-058 Burning fuel, enemy shields, multi-target reactions, and
  hitlag.
- RL learner/service/protocol changes and persistent jobs.

Cross-cutting rules:

- `ReactionState` owns Quicken gauge; the resolver owns typed reaction routing;
  `ReactionEffectScheduler` remains the sole Dendro Core owner.
- Reuse typed `ReactionResult`, `Element`, and existing directional Bloom
  consumption; do not dispatch on labels.
- One Hydro application creates at most one core while consuming every
  coexisting Dendro-like Aura required by the single-target priority contract.
- Preserve compatibility wrappers only for existing tests/callers that set an
  explicit Quicken end; new production behavior must use typed state.
- Follow source style, explicit staging, and the no-generated-artifact boundary.

### Phase 1: Record Quicken Gauge Evidence and Resolver Boundaries - Done

Why first:

Quicken creation, refresh, additive use, Bloom use, and Burning use have distinct
consumption rules. The typed boundary must be fixed before replacing the shared
timestamp.

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record maintained gauge, duration, refresh, coexistence, and consumption
  evidence plus the independent implementation reference.
- Inventory state/snapshot, creation, additive, Bloom, and Dendro Core paths.
- Exclude Pyro/Burning priority rather than partially implementing it.

Acceptance criteria:

- The plan distinguishes Quicken gauge decay from ordinary elemental Auras and
  from additive reaction damage.
- Every phase names files, normal/abnormal tests, and commands.
- No production behavior or generated output changes in this phase.

Test cases to add or update:

- No production test; Phases 2 and 3 own state and reaction checks.

Verification:

- inspect `ReactionState`, `SimulatorSnapshot`, `CombatActionResolver`,
  `ReactionEffectScheduler`, `ReactionCalculator`, and Quicken/Bloom regressions
- `python scripts/preflight.py --run`

Completion evidence:

- Maintained KQM and the advanced gauge reference agree on smaller-gauge
  creation, `gauge * 5 + 6` duration, Dendro/Electro non-consumption, and
  Hydro/Pyro consumption. The advanced reference explicitly defines weaker
  retriggers as no-ops and stronger/equal retriggers as replacements.
- The simultaneous-priority reference and gcsim independently agree that one
  Hydro Bloom consumes coexisting Dendro and Quicken while creating one core.
  Sources are recorded in B-059.
- Inventory confirms the current end-only state cannot be consumed, every
  retrigger refreshes, and the resolver only inspects ordinary `Enemy` Auras.
  The planned typed state and one-core routing isolate those responsibilities.
- Pyro/Burning priority is explicitly excluded for a later sourced item. The
  documentation preflight passes without checks or leaks.

### Phase 2: Add Typed Quicken Gauge and Snapshot State - Done

Why second:

Creation and Bloom consumption require a single tested gauge API instead of
editing an expiry timestamp from multiple resolver branches.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/mechanics/rl/CapabilityProfiler.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add immutable Quicken units/rate/update state with exact remaining/end queries.
- Add stronger-or-equal replacement, weaker no-op, typed consumption, expiry,
  clear, and restore operations.
- Preserve the explicit-end compatibility path while production migrates.

Acceptance criteria:

- 0.8U Quicken derives ten seconds and decays continuously to zero.
- Weaker replacement leaves gauge/rate/end unchanged; stronger/equal refresh
  replaces and recalculates from current time.
- Consumption rebases remaining gauge and exact end; invalid input cannot create
  stale state.
- Snapshot restores all typed fields and pending events remain excluded.

Test cases to add or update:

- Normal: 0.8U state, mid-duration gauge, and exact expiry.
- Refresh: weaker no-op, equal refresh, stronger replacement.
- Consumption: partial, exact clear, over-consumption, invalid amount.
- Snapshot: mutate/clear then exact restore.
- Compatibility: explicit end still enables existing additive fixtures.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/ReactionState.java --path src/java/simulation/SimulatorSnapshot.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- `ReactionState.QuickenState` stores immutable units, decay rate, and update
  time with exact remaining/end queries. Typed apply, consume, clear, and restore
  transitions leave timer arithmetic inside the state owner.
- Regression proves 0.8U lasts ten seconds at 0.08U/s, decays to 0.6U after 2.5
  seconds, rejects a 0.5U weaker refresh, and accepts floating-equal 0.6U and
  stronger 0.8U replacements from the current time.
- Partial 0.3U consumption rebases to 0.5U with the original decay rate and an
  8.75-second exact end. Invalid application is non-mutating, over-consumption
  clears state, and snapshot restore recovers units/rate/update/end exactly.
- Explicit `setQuickenEndTime` remains active-before/exclusive-at-end for legacy
  fixtures. `CapabilityProfiler` only forwards the immutable snapshot field.
- ReactionRegressionTest, build, Javadoc, party-catalog regression, local Java
  rollout smoke benchmark, routed validation, and preflight pass.

### Phase 3: Route Quicken Creation and Hydro Bloom Consumption - Done

Why third:

The resolver can now use tested typed operations for creation, refresh, and
coexisting Dendro-like Aura consumption without owning timer arithmetic.

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Create/refresh typed Quicken from the smaller current Aura/trigger gauge.
- Trigger one standard/Lunar Bloom and one core when Hydro meets Quicken alone.
- When Dendro and Quicken coexist, retain one Bloom/core while consuming 0.5
  times Hydro source gauge from both.
- Prove Aggravate/Spread read Quicken without consuming it and expiry blocks all
  three reaction families.

Acceptance criteria:

- A 1U trigger against taxed 1U Aura creates 0.8U Quicken for ten seconds.
- Weaker Quicken does not shorten/extend stronger remaining state; stronger or
  equal state refresh follows the typed contract.
- Hydro on Quicken alone emits one Bloom/Lunar-Bloom, consumes typed gauge, and
  creates one owned core/dew update as applicable.
- Hydro on coexisting Dendro+Quicken consumes both gauges but emits only one
  reaction/core.
- Aggravate and Spread leave Quicken units/end unchanged; exact expiry prevents
  additive and Bloom reactions.

Test cases to add or update:

- Normal: both Quicken trigger directions and exact 0.8U/10s state.
- Refresh: weaker no-op and stronger/equal replacement through real actions.
- Quicken-only: standard and Lunar Hydro Bloom/core ownership.
- Coexistence: one core plus dual 0.5U consumption.
- Non-consumption: Aggravate/Spread before expiry.
- Abnormal: no core/reaction at exact expiry and no duplicate notification.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Both real trigger directions now create typed 0.8U Quicken for exactly ten
  seconds from taxed 1U Aura. A real 0.5U retrigger after one second leaves the
  original end unchanged; a 2U retrigger replaces gauge and ends sixteen seconds
  after the refresh.
- Aggravate and Spread preserve stored units and exact end while retaining their
  existing additive damage. An exact ten-second expiry suppresses Spread and a
  separate exact-expiry Hydro hit applies ordinary Hydro without Bloom/core.
- Hydro on Quicken alone emits one Bloom, creates one core owned by the Hydro
  character, consumes 0.5U Quicken, and deals no immediate damage. Lunar
  conversion emits one Lunar-Bloom/core and increments both Dew counters once.
- Hydro on coexisting 2U fixture Dendro and 1U Quicken emits one Bloom/core and
  leaves 1.5U Dendro plus 0.5U Quicken, proving simultaneous dual consumption
  without duplicate notification.
- Bloom creation/dew handling remains in one helper and Dendro Core scheduling
  remains in `ReactionEffectScheduler`. ReactionRegressionTest, build, Javadoc,
  routed validation, and preflight pass.

### Phase 4: Re-Accept Quicken-Neutral Party Baselines - Done

Why last:

The catalog parties do not use Dendro/Quicken, so exact repeats isolate
cross-system regressions from the intended focused behavior.

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two fresh no-daemon payloads each for all three catalog parties.
- Compare totals, ER, allocations, cadence, warnings, and hashes with B-058.
- Document typed Quicken/Bloom behavior and retain explicit Pyro/Burning limits.

Acceptance criteria:

- All pairs match B-058 values and contain no Quicken/Bloom changes.
- No warning, failed action, duplicate reaction/core, or artifact leak occurs.
- README, plan, ledger, and checkpoint agree.

Test cases to add or update:

- Normal integration: all catalog runs complete and pairwise match.
- No-change integration: values, ER, allocation, and cadence remain exact.
- Abnormal integration: no warning/error/generated-report staging.

Verification:

- two fresh `./gradlew RaidenParty` runs
- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh no-daemon runs per catalog party match B-058 pairwise hashes exactly:
  Raiden `e52e586cca64148195ad8dc9ab9f0827922a7f01f931faedb0a6ecbab7100dda`,
  Flins `6338bcc75a29a52f3245cb4573823ba1245724d3be60064dcebefa7b38aa03ab`,
  and Flins2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
- Totals/DPS remain 1,365,787/65,037, 22,675,823/227,898, and
  15,817,125/228,902. ER and timed/reaction/delayed/ICD counts remain exactly
  100/175/179/174 with 152/55/11/38, 109/100/100/180 with 613/230/48/88, and
  130/128/100/196 with 468/140/33/71.
- Every run contains zero Quicken/Aggravate/Spread/Bloom lines and zero
  warning/error/failed-action/insufficient-energy lines.
- README records typed Quicken creation, refresh, additive non-consumption, and
  Hydro dual-consumption. Pyro/Burning priority remains explicit. The tracked
  generated report was restored and no output is staged.

### B-043 Phase 3 Acceptance Appendix

The following retained test matrix and completion evidence belongs to the
preceding Noblesse catalog plan.

Test cases to add or update:

- No further production test; Phase 2 owns mechanic boundaries and Phase 3 owns
  deterministic catalog acceptance.

Verification:

- two fresh `./gradlew RaidenParty` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two complete `RaidenParty` runs produced identical logs and normalized
  SHA-256 `ff1adfd3b3705f1cc34a32036af0950aa8a1246a6412589eb214966b3f3c33dc`.
- ER remains Bennett 100%, Raiden Shogun 175%, Xingqiu 179%, and Xiangling
  174%, with zero warning matches and the unchanged accepted 1,317,080 damage /
  62,718 DPS over 21.0 seconds.
- The ordinary catalog rotation has no overlapping Noblesse reapplication, so
  its unchanged result isolates the correction to the newly regressed overlap
  path. No generated report or output is staged.

## Implementation Order: Raiden Eye Buff Refresh

Status:

- Complete; all three phases are verified and pushed.
- Requirement: each party member may have one Eye of Stormy Judgment Burst DMG
  buff, refreshed to 25 seconds when Raiden recasts her Skill.

Scope:

- Replace each recipient's existing typed Eye buff before adding the newly
  calculated recipient-specific value.
- Preserve the 0.3% per Energy Cost scaling, 25-second duration, partywide
  targeting, Raiden source attribution, Skill damage, coordinated attacks, and
  ten-second Skill cooldown.
- Cover recipient scaling, recast overlap, exact expiry, typed instance count,
  source attribution, and accepted RaidenParty output.

Out of scope for this pass:

- Changing Raiden talent levels or multipliers, Resolve, energy restoration,
  coordinated-attack cadence, particle chance, ICD, Burst stance, or C6.
- Changing generic character-buff storage or introducing a team buff whose
  value must vary by recipient.
- Changing rotations, optimizer allocation, damage formula order, RL paths,
  generated reports, or committed `docs/` output.

Definitions:

- Eye recipient buff: one character-owned
  `BuffId.RAIDEN_EYE_OF_STORMY_JUDGMENT` window sourced by Raiden, active over
  `[applicationTime, applicationTime + 25.0)`, with Burst DMG Bonus equal to
  `recipientEnergyCost * 0.003`.
- Refresh: remove the recipient's prior typed Eye buff and add one newly timed
  instance without summing values.

Design boundaries:

- `RaidenShogun.skill` owns per-recipient scaling, replacement, and creation.
- Character `removeBuff(BuffId)` owns typed replacement for character-local
  values; simulator team buffs are unsuitable because values differ by energy
  cost.
- Cooldown, action resolution, and coordinated-attack lifetime remain
  unchanged.

### Phase 1: Record Eye Recast Semantics - Done

Why first:

The singular buff and refresh behavior must be distinguished from intentionally
stacked status effects before changing a long-overlap Skill.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the maintained KQM TCL values: one Eye granted to nearby party members,
  0.3% Burst DMG per Energy at Talent 9, 25-second duration, and ten-second CD.
- Record the maintained KQM guide's instruction to refresh Raiden's Skill in
  subsequent rotations.
- Trace the current per-recipient `addBuff` loop and prove legal recasts leave
  duplicate same-ID values additive for fifteen seconds.

Acceptance criteria:

- Scaling, duration, cooldown, refresh semantics, access date, source URLs,
  classification, and simulator adaptation are recorded.
- The correction remains inside Raiden's existing per-recipient buff loop.
- No production source changes occur in this phase.

Test cases to add or update:

- No production test in this evidence phase; Phase 2 adds the pre-fix failing
  legal-recast case.

Verification:

- inspect `RaidenShogun`, `Character` buff replacement, `CooldownState`, and
  `CombatActionResolver`
- `python scripts/preflight.py --run`

Completion evidence:

- KQM's maintained Raiden TCL and Raiden guide were accessed 2026-08-02. The
  TCL records one Eye, 0.3% per Energy, 25 seconds, and ten-second CD; the guide
  calls subsequent Skill use a refresh.
- Classification: adopt one refreshed recipient status and adapt it through
  typed character-buff replacement. Current code appends a new same-ID value on
  every legal recast, so stat assembly adds both throughout their overlap.

### Phase 2: Refresh Recipient Eye Buffs and Regress Recasts - Done

Why second:

The exact overlap and recipient-specific values must be proven before catalog
acceptance.

Target files:

- `src/java/model/character/RaidenShogun.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Remove each recipient's existing typed Eye buff immediately before adding the
  refreshed value.
- Add an actual Skill/recast regression at the ten-second cooldown boundary.
- Assert separate Raiden and ally Energy Cost scaling, one typed instance,
  Raiden source attribution, and exact refreshed expiry.

Acceptance criteria:

- An initial Skill grants Raiden 0.27 and a 60-cost ally 0.18 Burst DMG Bonus.
- A legal recast at exactly ten seconds leaves those same values, not 0.54 and
  0.36, and leaves one typed buff per recipient.
- A recast started at the exact 10.0-second cooldown boundary applies its
  refreshed buff after the existing 0.5-second Skill action; it remains active
  at 35.499 seconds and is absent at exactly 35.5 seconds.
- Every refreshed instance remains sourced by Raiden.
- Skill cast and coordinated-attack behavior remain covered by existing tests.

Test cases to add or update:

- Normal: actual initial Skill with 90- and 60-cost recipients.
- Recast: actual second Skill at the exact cooldown boundary.
- Identity: one typed Eye instance per character and Raiden source ID.
- Expiry: active immediately before and absent exactly 25 seconds after recast.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew PartyCatalogRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/character/RaidenShogun.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- `RaidenShogun.skill` now removes each recipient's prior typed Eye before
  adding its newly timed, recipient-scaled instance; coordinated attacks,
  cooldown, source, and all values remain in their existing owner boundary.
- The actual Skill regression proves Raiden 0.27 and a 60-cost ally 0.18 after
  the initial cast and exact-CD recast, one typed Raiden-sourced buff each,
  refreshed expiration at 35.5 seconds, active stats at 35.499, exact half-open
  expiry exclusion at 35.5, and absence after crossing the boundary.
- Reaction and party-catalog regressions, build, Javadoc, routed validation, and
  preflight pass.

### Phase 3: Re-Accept the Raiden Eye Baseline - Done

Why last:

RaidenParty exercises the full Eye, Burst, optimizer, and energy path, including
a second Skill while the first 25-second Eye window is active.

Target files:

- `README.md` only if the accepted value changes
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh complete `RaidenParty` payloads and compare normalized logs.
- Record ER, warnings, duration, total, DPS, and normalized hash.
- Update current baseline references if removing the duplicate Eye changes the
  accepted value.
- Close B-044 without changing unrelated deferred work.

Acceptance criteria:

- Repeated normalized payloads match and contain no new energy or optimizer
  warning.
- Any numerical delta is isolated to Burst damage after the second Skill and
  agrees with removing one duplicate recipient Eye value.
- Plan and ledger agree on the accepted baseline and no generated output is
  staged.

Test cases to add or update:

- No further production test; Phase 2 owns recast mechanics and Phase 3 owns
  deterministic catalog acceptance.

Verification:

- two fresh `./gradlew RaidenParty` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two complete `RaidenParty` runs produced identical logs and normalized
  SHA-256 `10df2aa5678cb8697eb6de92437c329ade06f0d06f155513c4c76bad64cabec8`.
- ER remains Bennett 100%, Raiden Shogun 175%, Xingqiu 179%, and Xiangling
  174%, with zero warning matches and 1,312,883 damage / 62,518 DPS over 21.0
  seconds.
- Comparison with the prior accepted payload isolates all 4,197 removed damage
  to seven Xingqiu Raincutter hits after Raiden's second Skill at 14.2 seconds;
  Bennett, Raiden, Xiangling, and Thundercloud totals are byte-for-byte
  unchanged. This supersedes the prior 1,317,080 / 62,718 baseline with no ER,
  optimizer allocation, rotation, or duration change.
- README and the verification skill reference carry the new accepted value;
  generated output is not staged.

## Implementation Order: Silken Gleaming Moon Dynamic Bonus

Status:

- Complete; all four phases are verified and pushed.
- Requirement: while Silken Moon's Serenade is equipped, all party members gain
  10% Lunar Reaction DMG for each distinct active Gleaming Moon effect, with
  duplicate effects never stacking.

Scope:

- Add a narrow artifact team-buff provider capability routed by `BuffManager`.
- Move the Silken-derived Lunar Reaction bonus into one dynamic typed provider
  buff owned by the artifact.
- Count active, unexpired Intent and Devotion status types from character-owned
  buffs at damage-resolution time.
- Preserve Devotion's 60/120 EM, eight-second refresh, off-field trigger, and
  2pc 20% ER.
- Cover no-effect, one-effect, two-effect, duplicate, expiry, off-field,
  multi-Silken, source, and accepted Flins party outputs.

Out of scope for this pass:

- Adding new Gleaming Moon effect types not represented by current `BuffId`
  values.
- Changing Night of the Sky's Unveiling's Intent trigger, crit value, duration,
  or on-field condition.
- Changing Lunar reaction formulas, Moonsign qualification, character kits,
  rotations, optimizer allocation, RL paths, generated reports, or committed
  `docs/` output.

Definitions:

- Distinct active effects: boolean presence of unexpired
  `GLEAMING_MOON_DEVOTION` and `GLEAMING_MOON_INTENT` anywhere in the party;
  duplicate copies of either ID count once.
- Dynamic bonus: 0.10 multiplied by the distinct active-effect count, evaluated
  at the damage/stat resolution time rather than snapshotted on trigger.
- Canonical provider: the first Silken-equipped party member supplies the one
  typed `GLEAMING_MOON_SYNERGY` provider buff when multiple copies are equipped.

Design boundaries:

- `ArtifactTeamBuffProvider` exposes only artifact-owned team buffs and does
  not add Silken-specific policy to simulator core.
- `BuffManager` discovers and source-attributes provider buffs alongside its
  existing weapon and character provider paths.
- `SilkenMoonsSerenade` owns distinct-effect counting, duplicate-set
  suppression, and dynamic Lunar stat construction.
- `MoonsignManager` returns to Moonsign state and Ascendant Blessing policy; it
  no longer owns one artifact's derived stat.

### Phase 1: Record Silken Distinct-Effect Evidence and Failure - Done

Why first:

The official wording distinguishes different Gleaming Moon effect types from
duplicate applications, so both source and storage layers must be identified
before changing the derived bonus.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the Luna I Silken description: elemental damage grants Devotion for
  eight seconds and 60/120 team EM; each different Gleaming Moon effect grants
  10% Lunar Reaction DMG; generated effects cannot stack; off-field triggering
  is allowed.
- Trace Intent and Devotion to character-owned `activeBuffs`.
- Trace `MoonsignManager.updateGleamingMoonSynergy` to
  `sim.getApplicableBuffs`, which excludes character-owned buffs, proving its
  active-effect count remains zero.
- Define a capability-based provider rather than adding a concrete artifact
  dependency to simulator runtime.

Acceptance criteria:

- Values, duration, off-field behavior, distinct/non-stack rule, release
  version, publication/access dates, source URLs, classification, and simulator
  adaptation are recorded.
- The zero-bonus defect and its wrong-source cause are concrete and bounded.
- No production source changes occur in this phase.

Test cases to add or update:

- No production test in this evidence phase; Phases 2-3 add provider routing and
  pre-fix failing Silken behavior.

Verification:

- inspect `SilkenMoonsSerenade`, `NightOfTheSkysUnveiling`, `BuffManager`,
  `MoonsignManager`, `Character`, and `DamageCalculator`
- `python scripts/preflight.py --run`

Completion evidence:

- The HoYoLAB Silken guide published 2025-09-19 for Luna I and maintained
  artifact database descriptions were accessed 2026-08-02. They agree on 20%
  ER, eight-second 60/120 EM Devotion, off-field triggering, 10% per different
  Gleaming Moon effect, and non-stacking generated effects.
- Sources: https://www.hoyolab.com/article/41239522 and
  https://gi.gachabase.net/artifacts/15042/silken-moons-serenade/beta?lang=en.
  Classification: adopt the distinct dynamic count and adapt it to typed
  character statuses plus one artifact-provided team buff.
- Both Gleaming statuses are stored in `Character.activeBuffs`, while the
  current manager scans only team/field/provider buffs. Its count is therefore
  zero and the intended Synergy buff is unreachable.

### Phase 2: Route Artifact-Owned Team Buff Providers - Done

Why second:

The generic capability boundary must be proven before moving Silken policy out
of the runtime manager.

Target files:

- `src/java/model/entity/ArtifactTeamBuffProvider.java`
- `src/java/model/entity/AGENTS.md`
- `src/java/simulation/runtime/BuffManager.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add a focused artifact team-buff provider interface receiving owner and
  simulator context.
- Extend `BuffManager.getApplicableBuffs` to collect provider output from
  equipped artifacts and attribute unknown sources to the owner.
- Add a fixture artifact proving owner and ally routing, targeting, source
  attribution, and coexistence with existing provider categories.

Acceptance criteria:

- One fixture artifact contributes its team stat to owner and ally through
  normal stat resolution.
- Explicit and fallback source attribution are preserved.
- Artifact provider routing does not alter weapon, character, field, team, or
  character-owned buff behavior.
- No Silken-specific class or `BuffId` appears in `BuffManager`.

Test cases to add or update:

- Normal: owner and ally receive one fixture artifact team stat.
- Source: an unknown source is attributed to the artifact owner.
- Targeting: standard `Buff` element/character filters still apply.
- Coexistence: fixture artifact and an existing team buff add independently.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/entity/ArtifactTeamBuffProvider.java --path src/java/simulation/runtime/BuffManager.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- The new narrow `ArtifactTeamBuffProvider` returns artifact-owned team buffs
  from owner plus simulator context; `BuffManager` performs only capability
  discovery, fallback source attribution, targeting, and aggregation.
- The fixture regression proves owner/ally routing, unknown-source attribution
  to Sucrose, preservation of an explicit Columbina source, Pyro-only targeting,
  and coexistence with an independent simulator team buff.
- Reaction regression, build, Javadoc, routed validation, and preflight pass;
  no artifact-specific class or ID entered runtime policy.

### Phase 3: Provide Dynamic Non-Stacking Silken Bonus - Done

Why third:

Once provider routing is stable, the artifact can own its complete derived
effect without simulator-specific artifact policy.

Target files:

- `src/java/model/artifact/SilkenMoonsSerenade.java`
- `src/java/model/artifact/AGENTS.md`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/runtime/MoonsignManager.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Implement `ArtifactTeamBuffProvider` on Silken and return one canonical typed
  dynamic Lunar bonus across duplicate set wearers.
- Count distinct unexpired Intent/Devotion IDs directly from party character
  buffs at the resolution time.
- Remove the obsolete trigger-time Synergy update call and runtime-manager
  method/delegator.
- Add actual Silken damage-hook regressions for Devotion and all dynamic bonus
  boundaries.

Acceptance criteria:

- No Gleaming effect resolves 0.00 Lunar bonus; Devotion alone resolves 0.10;
  Devotion plus Intent resolves 0.20.
- Repeated Devotion or Intent instances do not exceed their one distinct count.
- Exact Intent expiry drops 0.20 to 0.10 without another hit, and exact Devotion
  expiry drops the remaining bonus to zero.
- Ascendant Devotion remains 120 EM, refreshes for eight seconds, and triggers
  while its owner is off field.
- Two Silken wearers still provide one dynamic synergy buff and no doubled EM
  or Lunar bonus.

Test cases to add or update:

- Empty: equipped Silken with no active Gleaming status gives zero.
- Normal: actual off-field elemental hit grants 120 EM and 10% Lunar bonus.
- Two-effect: active Intent raises the dynamic result to 20%.
- Duplicate: repeated hits/status copies remain at one count per ID.
- Expiry: exact four- and eight-second boundaries reduce the live count without
  explicit refresh callbacks.
- Multi-wearer: two Silken sets expose one provider and one Devotion value.
- Static: 2pc ER remains 20%.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew PartyCatalogRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/artifact/SilkenMoonsSerenade.java --path src/java/simulation/CombatSimulator.java --path src/java/simulation/runtime/MoonsignManager.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Silken now supplies one canonical typed team buff through
  `ArtifactTeamBuffProvider`; its stat application counts unexpired Intent and
  Devotion IDs directly from character-owned buffs at the requested resolution
  time. The obsolete simulator façade and Moonsign-manager trigger-time policy
  are removed.
- The actual artifact regression proves no-effect 0%, Intent-only 10%,
  Intent-plus-Devotion 20% for all three Lunar reaction stats, off-field
  Ascendant 120 EM, repeated/duplicate non-stacking, exact 4-second reduction to
  10%, exact 8-second reduction to zero, one canonical provider across two sets,
  and unchanged 20% 2pc ER.
- Reaction and party-catalog regressions, build, Javadoc, routed validation, and
  preflight pass.

### Phase 4: Re-Accept Silken Party Baselines - Done

Why last:

Both deterministic Flins parties equip Silken, and FlinsParty2 also equips Night
of the Sky's Unveiling, so the full 10%/20% dynamic effect must be accepted in
optimizer and final-rotation outputs.

Target files:

- `README.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh complete `FlinsParty` payloads and compare normalized logs.
- Run two fresh complete `FlinsParty2` payloads and compare normalized logs.
- Record ER, warnings, duration, total, DPS, normalized hashes, and damage deltas
  attributable to enabling the sourced dynamic Lunar bonus.
- Update current baseline references and close B-045.

Acceptance criteria:

- Each party's repeated normalized payloads match and contain no new energy or
  optimizer warning.
- Numerical changes are limited to dynamic Gleaming Moon Lunar bonus effects;
  rotations, loadouts, ER, and durations remain unchanged.
- README, verification skill, plan, and ledger agree on current values.
- Agent assets and preflight pass and no generated report is staged.

Test cases to add or update:

- No further production test; Phase 3 owns mechanic boundaries and Phase 4 owns
  deterministic full-party acceptance.

Verification:

- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Two `FlinsParty` runs produced identical complete logs and normalized SHA-256
  `f6d276fde49b6677c928545e689f530c6d7cac492a45f2b952c668bb644b32f6`.
  ER remains Sucrose 109%, Flins 100%, Ineffa 100%, and Columbina 180%, with
  zero warning matches and 18,930,343 damage / 190,255 DPS over 99.5 seconds.
- Two `FlinsParty2` runs match after excluding only Gradle's elapsed-time line,
  at normalized SHA-256
  `491cd43e7077114acbe4f00e38c02030426141331d05a921e598573d82347c40`.
  ER remains Sucrose 141%, Flins 132%, Ineffa 105%, and Columbina 193%, with
  zero warning matches and 14,194,732 damage / 205,423 DPS over 69.1 seconds.
- Relative to the pre-fix values, totals increase by 587,251 (3.20%) and 561,609
  (4.12%). In both payloads, Sucrose and Thundercloud totals are unchanged;
  every increase is confined to Columbina, Flins, and Ineffa Lunar-classified
  damage. Rotations, loadouts, ER, and durations are unchanged.
- README and the verification skill carry the new accepted values. The generated
  committed report was restored, agent assets and preflight pass, and no output
  artifact is staged.

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

## Implementation Order: Ascendant Blessing Expiry Replacement

Status:

- Complete; all three phases are verified and pushed.
- Requirement: only active Ascendant Blessing instances may participate in the
  non-stacking strength comparison; an exactly expired instance must not block
  the next eligible activation.

Scope:

- Preserve the sourced 20-second duration, 36% cap, elemental stat scaling,
  non-stacking behavior, and active stronger-value precedence.
- Exclude expired typed Blessings from the pre-insertion strength comparison.
- Cover active stronger/weaker ordering, same-value refresh, exact expiry,
  replacement timing, and deterministic party output.

Out of scope for this pass:

- Changing Lunar reaction formulas, the 36% cap, stat conversion coefficients,
  Moonsign qualification, party composition, rotations, or optimizer policy.
- Changing the active stronger-value precedence without direct behavioral
  evidence.
- RL code, training, rollout services, GPU jobs, report UI, or generated output.

Definitions:

- Active Blessing: a typed `MOONSIGN_ASCENDANT_BLESSING` whose half-open window
  contains the simulator's current time.
- Replacement: removal of stale typed instances followed by one newly timed
  Blessing calculated from the triggering non-Lunar character.

Design boundaries:

- `MoonsignManager` owns Blessing calculation and replacement policy.
- `Buff` remains the single owner of the repository-wide half-open expiry
  contract; this change consumes `isExpired` rather than duplicating timing
  arithmetic.
- `ReactionRegressionTest` exercises the public simulator action path and
  inspects typed state; no test-only production hook is added.

### Phase 1: Record Ascendant Blessing Expiry Evidence - Done

Why first:

The game contract and simulator lifetime semantics must be fixed before changing
the stronger-value guard.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the maintained team-bonus description: 20 seconds, elemental
  stat-based Lunar Reaction DMG, 36% cap, and non-stacking effect.
- Trace the live team-buff list and the `Buff` half-open expiry contract.
- Record that `applyAscendantBlessing` compares every retained typed instance
  without checking whether its 20-second window has ended.
- Bound the correction to active-state filtering before the existing strength
  comparison.

Acceptance criteria:

- Duration, cap, scaling categories, non-stack rule, source URLs, access date,
  defect cause, and simulator adaptation are recorded.
- Production source is unchanged in this evidence phase.
- The plan contains normal and abnormal boundary tests for each later phase.

Test cases to add or update:

- No production test in this phase; Phase 2 owns the failing exact-expiry case.

Verification:

- inspect `Buff`, `BuffManager`, `MoonsignManager`,
  `ActionTimelineExecutor`, and `ReactionRegressionTest`
- `python scripts/preflight.py --run`

Completion evidence:

- The maintained Genshin Impact Wiki team-bonus page and Icy Veins Moonsign
  guide were accessed 2026-08-02. Both record a 20-second team Lunar Reaction
  DMG bonus after a non-Moonsign Skill/Burst, elemental stat scaling, and a 36%
  cap; the maintained team-bonus page explicitly records non-stacking.
- Sources: https://genshin-impact.fandom.com/wiki/Team_Bonus and
  https://www.icy-veins.com/genshin-impact/nod-krai-moonsign.
- `Buff.isExpired` closes a timed window at `currentTime >= expirationTime`,
  while simulator team buffs remain in the live list. The manager's stronger
  guard omits that predicate, so an expired larger value can reject every later
  smaller activation indefinitely.

### Phase 2: Filter Expired Blessings and Add Boundaries - Done

Why second:

The smallest production correction can be proven directly against the public
activation path before accepting an integration baseline.

Target files:

- `src/java/simulation/runtime/MoonsignManager.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Restrict stronger-value rejection to active typed `MoonsignBuff` instances.
- Add a focused regression using non-Lunar characters with distinct elemental
  scaling values and Skill/Burst actions under Ascendant Gleam.
- Assert typed count, value, active stronger precedence, same-value refresh,
  exact-expiry replacement, and post-expiry application.

Acceptance criteria:

- A stronger active Blessing remains selected when a weaker source acts.
- An equal activation refreshes one 20-second typed window.
- At the exact expiration timestamp, a weaker activation replaces stale state
  and grants its own value for a new 20-second window.
- No duplicate typed Blessings remain after any accepted activation.

Test cases to add or update:

- Normal: an initial eligible action creates one correctly scaled Blessing.
- Active conflict: a weaker action before expiry leaves the stronger window
  and value unchanged.
- Refresh: the same value before expiry replaces and extends one window.
- Boundary: a weaker action exactly at expiry creates a new lower-value window.
- Abnormal: non-Skill/Burst and Lunar-character actions do not trigger the
  Blessing path.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/MoonsignManager.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- The stronger-value guard now considers only typed Blessings active at the
  simulator's current time through the shared `Buff.isExpired` contract.
- The public action-path regression proves initial capped 36% application,
  active rejection of a 9% value without extending the strong window, equal
  refresh from 5.0 to 25.0 seconds, and exact-expiry replacement by a 9% window
  from 25.0 to 45.0 seconds with one typed instance throughout.
- Normal actions and Lunar-character Skills remain ineligible. Reaction
  regression, build, Javadoc, routed validation, and preflight pass.

### Phase 3: Confirm Deterministic Flins Integration - Done

Why last:

The Flins sample exercises Ascendant Gleam through normal action sequencing and
must remain deterministic when no expired stronger-to-weaker transition occurs.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh complete `FlinsParty` payloads and compare normalized logs.
- Record ER, warnings, duration, total, DPS, and normalized hash.
- Close B-046 only after focused, build, Javadoc, routed, and preflight checks
  pass with no generated artifact staged.

Acceptance criteria:

- Repeated normalized payloads match and contain no new warnings.
- Existing party damage, DPS, duration, and ER baseline remain unchanged unless
  the trace proves an expired stronger-to-weaker transition.
- Plan and ledger carry the focused regression and integration evidence.

Test cases to add or update:

- Normal integration: the complete party uses the public action path and
  completes with its established deterministic payload.
- Abnormal integration: no warning, energy failure, or generated tracked output
  is introduced.

Verification:

- two fresh `./gradlew FlinsParty` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two complete `FlinsParty` runs produced identical logs after excluding only
  Gradle's elapsed-time line, at normalized SHA-256
  `f6d276fde49b6677c928545e689f530c6d7cac492a45f2b952c668bb644b32f6`.
- ER remains Sucrose 109%, Flins 100%, Ineffa 100%, and Columbina 180%, with
  zero warning matches and unchanged 18,930,343 damage / 190,255 DPS over 99.5
  seconds. The trace contains only Sucrose Blessing refreshes and therefore
  correctly has no integration delta from the exact-expiry correction.
- Focused regression, build, Javadoc, routed validation, and final preflight
  pass; generated `output/simulation_report.html` remains untracked.


## Implementation Order: Guoba C1 Enemy Shred Refresh

Status:

- Complete; all three phases are verified and pushed.
- Requirement: a Guoba hit at C1 or above establishes one 15% Pyro RES reduction
  for six seconds; later Guoba hits refresh that one enemy-facing status.

Scope:

- Replace Guoba's active-character-only field insertion with one typed
  team-visible simulator buff representing the enemy debuff.
- Refresh the typed six-second window on every actual Guoba hit.
- Attribute the delayed status explicitly to Xiangling.
- Cover actual four-hit cadence, owner/ally visibility, non-stacking, refresh,
  exact expiry, and deterministic RaidenParty acceptance.

Out of scope for this pass:

- Changing Guoba damage, four-hit timing, gauge, ICD, particles, targeting,
  chili pickup, Skill cooldown, talent multipliers, or constellation data.
- Changing Pyronado, other resistance effects, reaction formula ordering,
  rotations, artifact optimization, or enemy-specific debuff containers.
- RL code, training, rollout services, GPU jobs, reports, or generated output.

Definitions:

- Enemy-facing team visibility: the shred is available when resolving any party
  member's Pyro damage, whether that attacker is active or off field.
- Refresh: remove every existing `XIANGLING_GUOBA_C1_SHRED` simulator team buff
  and add one new instance starting at the current Guoba hit time.

Design boundaries:

- `Xiangling` owns the constellation trigger, value, source, and duration.
- `BuffManager.applyTeamBuffNoStack` owns typed replacement; no Guoba-specific
  branch enters simulator core.
- `ReactionRegressionTest` drives the actual character and periodic event path.

### Phase 1: Record Guoba C1 Status Evidence and Failure - Done

Why first:

The opponent status wording and current field/team semantics determine both the
replacement API and the required visibility tests.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the maintained KQM TCL, KQM guide, and constellation description for
  15% Pyro RES reduction lasting six seconds on opponents hit by Guoba.
- Trace Guoba's sourced four-hit times and callback ordering.
- Record that normal field insertion appends four same-ID values and exposes
  them only to the active character.
- Preserve the pre-fix deterministic RaidenParty payload for delta analysis.

Acceptance criteria:

- Trigger, value, duration, status target, source URLs, access date, simulator
  adaptation, and failure cause are recorded.
- The planned tests distinguish active/off-field visibility from stacking.
- No production source changes occur in this evidence phase.

Test cases to add or update:

- No production test in this phase; Phase 2 extends the existing actual-Guoba
  regression with the pre-fix failing state boundaries.

Verification:

- inspect `Xiangling`, `PeriodicDamageEvent`, `BuffManager`, `Buff`, and the
  existing Guoba regression
- one pre-fix `./gradlew RaidenParty` trace
- `python scripts/preflight.py --run`

Completion evidence:

- The KQM Xiangling TCL, maintained KQM guide, and maintained constellation page
  were accessed 2026-08-02. Each describes one 15% Pyro RES reduction for six
  seconds on opponents hit by Guoba. Sources:
  https://library.keqingmains.com/characters/pyro/xiangling,
  https://keqingmains.com/xiangling/, and
  https://genshin-impact.fandom.com/wiki/Crispy_Outside%2C_Tender_Inside.
- `PeriodicDamageEvent` invokes Guoba's callback after each actual damage hit at
  +2.0, +3.5, +5.0, and +6.5 seconds. The callback appends a normal field buff,
  producing up to 60% for the active character while excluding off-field Pyro
  attackers from an opponent debuff.
- The pre-fix RaidenParty trace retains normalized SHA-256
  `10df2aa5678cb8697eb6de92437c329ade06f0d06f155513c4c76bad64cabec8`,
  1,312,883 damage / 62,518 DPS over 21.0 seconds, and Guoba hits at 8.4, 9.9,
  11.4, and 12.9 seconds.

### Phase 2: Refresh One Team-Visible Guoba C1 Status - Done

Why second:

The actual periodic path can prove replacement, targeting, source, and timing in
one bounded character-local change.

Target files:

- `src/java/model/character/Xiangling.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Apply Guoba C1 through typed team-buff replacement after each hit.
- Explicitly source the delayed buff from Xiangling.
- Extend the actual Guoba regression to inspect owner and ally values, typed
  count, start/expiry times, every refresh, and exact expiry.

Acceptance criteria:

- Before the first hit, no Guoba C1 status is present.
- Each of four hit callbacks leaves exactly one 15% typed status.
- Final status starts at +6.5 and expires at +12.5 seconds.
- Active ally and off-field Xiangling both resolve the same 15% Pyro shred.
- Source is Xiangling and the exact expiry resolves zero.

Test cases to add or update:

- Normal: first actual Guoba hit creates one 15% status for owner and ally.
- Refresh: second through fourth hits remain 15% and move one expiry forward.
- Targeting: switching the ally active before ticks does not exclude off-field
  Xiangling or the active ally.
- Boundary: +12.499 remains active and +12.5 is inactive.
- Abnormal: before +2.0 there is no status and no duplicate typed instance is
  retained at any hit.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/character/Xiangling.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Guoba's actual delayed callback now replaces one typed simulator team buff
  after each damage hit and explicitly attributes it to Xiangling.
- The actual-character regression proves no pre-hit status; one 15% value at
  +2.0, +3.5, +5.0, and +6.5 seconds; one typed instance throughout; active
  Bennett and off-field Xiangling visibility; final +12.5 exact expiry; and
  unchanged four-hit, no-ICD, 1U, reaction, and no-aura contracts.
- Reaction regression, build, Javadoc, routed validation, and preflight pass.

### Phase 3: Accept the RaidenParty Guoba C1 Delta - Done

Why last:

RaidenParty uses C6 Xiangling and exercises both on-field Raiden damage and
off-field Xiangling Pyronado during all four Guoba refreshes.

Target files:

- `README.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh complete RaidenParty payloads and compare normalized logs.
- Isolate damage changes to the 15% refreshed enemy status and affected Pyro
  actions after the first Guoba hit.
- Record ER, warnings, duration, total, DPS, normalized hash, and accepted delta.
- Update current baseline references and close B-047.

Acceptance criteria:

- Repeated normalized payloads match and contain no new warning.
- Changed damage is limited to Pyro actions inside sourced Guoba C1 windows;
  rotations, ER, duration, and unrelated categories remain unchanged.
- README, verification skill, plan, and ledger agree on the accepted baseline.
- Agent assets and preflight pass and no generated output is staged.

Test cases to add or update:

- Normal integration: complete C6 Xiangling rotation resolves the refreshed
  status through normal periodic callbacks.
- Abnormal integration: no energy, optimizer, warning, or generated-artifact
  regression is introduced.

Verification:

- two fresh `./gradlew RaidenParty` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Two complete RaidenParty runs produced identical logs after excluding only
  Gradle's elapsed-time line, at normalized SHA-256
  `d7fc2de0e9ece10da808a9fbde36e594f759ddbb6532d654de637f3da6be9c76`.
- ER remains Bennett 100%, Raiden Shogun 175%, Xingqiu 179%, and Xiangling 174%,
  with zero warning matches and 1,310,839 damage / 62,421 DPS over 21.0 seconds.
- The 2,044 reduction is isolated to Bennett at 14.8 seconds while the old four
  C1 instances overlapped: Passion Overload 8,183 to 7,139, N2 1,700 to 1,483,
  and associated Overload 6,129 to 5,346. Every other character total and all
  action times are unchanged.
- Pyronado remains unchanged because the existing formula path snapshots RES
  shred with attacker stats instead of resolving enemy debuffs at impact. This
  separate architecture defect is recorded as B-048 and is not claimed solved
  by B-047.
- README and the verification skill carry the accepted baseline. Agent assets
  and final preflight pass; generated output is not staged.


## Implementation Order: Live Resistance Reduction Resolution

Status:

- Complete; all five phases are implemented and accepted.
- Requirement: elemental and Physical RES reduction is enemy-facing state and
  must be evaluated at each damage impact, never captured with attacker stats.

Scope:

- Centralize live RES-reduction extraction from the enemy-effect list captured
  at the start of an immediate hit.
- Route standard, Lunar, Shatter, transformative, stateful, and core-trigger
  immediate damage through that resolver.
- Store delayed Electro-Charged, Burning, and Dendro Core damage before RES and
  apply current RES reduction at each tick or explosion.
- Include current team RES reduction in weighted Lunar reaction damage.
- Preserve all attacker-side snapshot stats, reaction ownership, timing, hit
  caps, aura behavior, and deferred first-Swirl ordering.

Out of scope for this pass:

- Changing base resistance values, the three-region RES formula, DEF reduction,
  vulnerability, aura/reaction ordering, trigger ownership, or multi-target
  enemy state.
- Making the first Swirl benefit immediately from the VV status it creates;
  B-042 remains deferred and the start-of-hit buff list preserves that boundary.
- Migrating buffs into per-enemy storage, adding new shred producers, changing
  snapshot eligibility of attacker stats, RL paths, reports, or generated output.

Definitions:

- Impact-time RES reduction: generic plus damage-element-specific reduction
  reconstructed by applying unexpired captured/team buffs to an empty stat
  container at the damage timestamp.
- Pre-RES damage: reaction damage after its owner stats and reaction bonuses but
  before the target's resistance multiplier.
- Immediate hit boundary: the immutable team-effect list captured before
  reaction callbacks for one `CombatActionResolver` invocation. Attacker stat
  targeting remains separate from this enemy-state list.

Design boundaries:

- `ResistanceCalculator` owns extraction and multiplier construction; formula
  strategies and runtime paths do not reimplement typed-stat selection.
- `DamageCalculator` continues to own attacker stat snapshot selection and
  damage-hook dispatch.
- `CombatActionResolver` passes its pre-reaction context to all immediate
  reaction resistance calculations.
- `ReactionEffectScheduler` owns delayed pre-RES payloads and applies current
  team reduction only when damage is recorded.

### Phase 1: Record Non-Snapshot RES Evidence and Path Inventory - Done

Why first:

This high-risk formula change must enumerate every producer and consumer and
preserve B-042's deferred ordering before APIs change.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record maintained KQM snapshot and enemy-resistance evidence.
- Inventory RES producers: VV, Guoba C1, Xingqiu C2, Superconduct, and Enduring
  Rock.
- Inventory consumers in standard/Lunar strategies, immediate reaction paths,
  and delayed reaction scheduling.
- Define start-of-hit versus delayed-impact buff timing and the B-042 boundary.

Acceptance criteria:

- Source URLs, access date, adopted rule, simulator adaptation, all producers,
  all consumers, and excluded ordering work are recorded.
- Each later phase has normal, expiry, snapshot, and abnormal test cases.
- Production source is unchanged in this evidence phase.

Test cases to add or update:

- No production test in this phase; Phases 2-4 add pre-fix failing boundaries.

Verification:

- inspect `DamageCalculator`, both damage strategies, `ResistanceCalculator`,
  `CombatActionResolver`, `ReactionEffectScheduler`, `ReactionState`, all
  `RES_SHRED` writers, and existing reaction/VV/Guoba regressions
- `python scripts/preflight.py --run`

Completion evidence:

- KQM's maintained Xiangling guide states that RES, DEF, and enemy-state
  conditions cannot snapshot; its maintained team-building guide explicitly
  says VV resistance reduction cannot snapshot. The KQM enemy-resistance page
  defines current resistance as base resistance minus reduction. Sources
  accessed 2026-08-02: https://keqingmains.com/xiangling/,
  https://keqingmains.com/misc/team-building/, and
  https://library.keqingmains.com/combat-mechanics/enemy-mechanics/enemy-resistances.
- All current RES writers are simulator team buffs. Immediate formulas consume
  attacker `StatsContainer`; snapshot actions therefore retain stale reduction
  and miss newly active reduction. Standard Electro-Charged, Burning, and Bloom
  store post-RES values for later impacts, while weighted Lunar damage hardcodes
  zero reduction.
- The resolver captures team effects before reaction notification. Reusing that
  immutable enemy-state list for immediate damage prevents B-048 from changing
  B-042's separately deferred first-Swirl ordering without filtering a Pyro,
  Hydro, Cryo, or Electro debuff by an Anemo reaction owner's element.

### Phase 2: Centralize Live RES for Standard and Lunar Hits - Done

Why second:

The shared extraction contract must be proven before migrating custom reaction
callers.

Target files:

- `src/java/mechanics/formula/ResistanceCalculator.java`
- `src/java/mechanics/formula/StandardDamageStrategy.java`
- `src/java/mechanics/formula/LunarDamageStrategy.java`
- `src/java/mechanics/formula/DamageCalculator.java`
- `src/java/mechanics/formula/AGENTS.md`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add one live-buff RES extraction API using generic and element-specific stats.
- Make both damage strategies ignore snapshot-stored RES fields and use their
  captured applicable buffs at the impact time.
- Update formula documentation and add standard/Lunar snapshot boundaries.

Acceptance criteria:

- Snapshot before shred then hit during it uses current reduction.
- Snapshot during shred then hit at/after expiry does not retain reduction.
- Non-snapshot and snapshot hits at the same time use identical RES multipliers.
- Generic and matching elemental reductions add once; unrelated elements do not.
- Standard and Lunar strategies share the same central extraction.

Test cases to add or update:

- Normal: live 15% Pyro reduction increases a snapshotted Pyro hit.
- Expiry: a snapshot containing 15% returns to baseline at exact buff expiry.
- Parity: live and snapshot standard hits match under one active reduction.
- Lunar: a snapshot Lunar hit observes the same current reduction.
- Abnormal: Hydro-only reduction does not affect Pyro and duplicate application
  is not introduced by formula resolution.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/mechanics/formula --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- `ResistanceCalculator` now reconstructs generic and matching element-specific
  reduction from the unexpired impact-time buff list without reading or mutating
  attacker snapshots. Standard and Lunar strategies share that API.
- Regression proves post-snapshot 15% activation, exact-expiry removal of a
  snapshot-contained 15%, live/snapshot parity, generic plus matching elemental
  addition, unrelated Hydro exclusion from Pyro, and Lunar Electro parity.
- Formula documentation and nearest-package guidance identify RES reduction as
  impact-time enemy state. Reaction regression, build, Javadoc, routed
  validation, and preflight pass.

### Phase 3: Route Immediate Reaction RES Through Captured Buffs - Done

Why third:

Shatter and transformative/stateful reactions bypass the strategy classes and
must adopt the same pre-reaction context without changing reaction order.

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Route Shatter, Hyperbloom/Burgeon, transformative, Burning creation, and Bloom
  creation through the central live RES multiplier.
- Pass the start-of-hit enemy-effect list through existing context and handlers.
- Add snapshot/live parity and first-Swirl non-immediacy regressions.

Acceptance criteria:

- Immediate reactions use current captured reduction regardless of attacker
  snapshot state.
- Superconduct damage does not benefit from the Physical reduction it creates,
  while the next Physical hit does.
- The first VV-triggering Swirl retains the established pre-VV result and a
  later eligible reaction sees already-active reduction.
- Aura consumption, ownership, ICD, and notification counts remain unchanged.

Test cases to add or update:

- Normal: snapshotted trigger and live trigger produce equal transformed damage
  under pre-existing matching reduction.
- Superconduct: same-hit exclusion and subsequent Physical inclusion.
- Ordering: first Swirl does not read its newly emitted VV buff.
- Element mismatch: unrelated reduction leaves reaction damage unchanged.
- Abnormal: no reduction and expired reduction retain baseline damage.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Shatter, core triggers, transformative reactions, Burning creation, and Bloom
  creation now use the central multiplier with the immutable start-of-hit enemy
  effect list. This list is independent of attacker targeting, while existing
  owner-stat snapshots remain limited to EM/reaction bonuses.
- Regression proves post-snapshot live Overload reduction, exact-expiry stale
  exclusion, first-Swirl non-immediacy including VV's independent 60% Swirl
  bonus, later Anemo-triggered Swirl use of already-active cross-element VV
  reduction, and later Pyro-reaction use of that reduction. Existing Superconduct
  tests retain same-hit exclusion and next-hit Physical inclusion.
- Reaction regression, build, routed validation, and preflight pass with aura,
  ownership, ICD, and notification tests unchanged.

### Phase 4: Apply RES at Delayed Reaction Impact - Done

Why fourth:

Timer and core state currently retain post-RES values, so later ticks cannot
respond to reduction activation or expiry.

Target files:

- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/AGENTS.md`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Store standard Electro-Charged, Burning, and Dendro Core payloads before RES.
- Apply current team reduction at each tick, overflow/expiry explosion, and core
  consumption impact.
- Apply current reduction once to weighted Lunar-Charged and Lunar-Crystallize
  damage after party weighting.
- Clarify state-field semantics and cover activation/expiry between scheduling
  and damage.

Acceptance criteria:

- A reduction activated after scheduling affects later damage; one expired
  before impact does not.
- Electro-Charged and Burning ticks can change across one scheduled series as
  reduction windows change.
- Bloom expiry and Hyperbloom/Burgeon consumption use impact-time Dendro RES.
- Weighted Lunar damage uses one current matching multiplier and no per-member
  duplication.
- Existing cadence, duration, hit caps, ownership, and aura transitions remain.

Test cases to add or update:

- Electro-Charged: baseline first tick, reduced second tick, baseline after
  expiry.
- Burning: activation and exact-expiry transitions inside one timer.
- Bloom: reduction added after core creation affects explosion; expired creation
  reduction does not persist.
- Hyperbloom/Burgeon: consumption uses current Dendro reduction.
- Lunar: matching reduction changes weighted current damage; unrelated reduction
  does not.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/mechanics/reaction/ReactionEffectScheduler.java --path src/java/simulation/runtime/ReactionState.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Electro-Charged, Burning, and Dendro Core state now retain pre-RES damage;
  current matching reduction is applied only when each tick, explosion, or core
  consumption is recorded. Dendro Core snapshot state documents that contract.
- Weighted Lunar damage applies one current matching multiplier after party
  weighting instead of hardcoding zero reduction per member.
- Regression proves 900/1,025/900 transitions inside one Electro-Charged and
  Burning series; post-creation and expired-at-impact Bloom boundaries;
  Hyperbloom/Burgeon current Dendro reduction; and matching/unrelated weighted
  Lunar elements. Existing cadence, duration, ownership, aura, snapshot, and
  core hit-cap regressions pass.
- Reaction regression, build, Javadoc, routed validation, and preflight pass.

### Phase 5: Re-Accept Affected Party Baselines - Done

Why last:

RaidenParty contains snapshotted Pyronado plus Guoba/Xingqiu reduction, while
both Flins parties contain VV and weighted Lunar reaction damage.

Target files:

- `README.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh payloads each for RaidenParty, FlinsParty, and FlinsParty2.
- Compare normalized hashes, totals, DPS, duration, ER, warnings, and per-source
  deltas against B-047/B-046 accepted values.
- Update current baseline references and close B-048 only after each delta is
  attributable to impact-time matching RES reduction.

Acceptance criteria:

- Each party's repeated normalized payloads match with no new warning.
- Timings, rotations, and ER remain unchanged; any optimizer reallocation and
  resulting secondary category delta is deterministic and explicitly attributed.
- Numerical changes align with active reduction element/time windows and no
  first-Swirl immediate benefit appears.
- README, verification skill, plan, and ledger agree; no generated output is
  staged.

Test cases to add or update:

- Normal integration: all three parties complete with deterministic payloads.
- Abnormal integration: no energy, optimizer, warning, asset, or generated-file
  regression is introduced.

Verification:

- two fresh `./gradlew RaidenParty` runs
- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Two normalized payloads per party match exactly: RaidenParty SHA-256
  `0b437864068daf8d903a7bd755e2428d96962dbce2df1bee527f909221fc79f0`,
  FlinsParty `0afbc2fd6f3b3d2319a3ff224cf2f1117ace1024240e1cbc4c5dd9185408159e`,
  and FlinsParty2
  `f3d7a0bdfbfe6f56837135a538d20fe08659df2bba22d8444972968781526f26`.
  All six runs complete without warning, error, failed action, or insufficient
  ER output.
- RaidenParty is 1,358,959 damage / 64,712 DPS over 21.0 seconds, an intended
  +48,120 total delta isolated to Xiangling's Pyronado/Guoba direct damage and
  matching Overloads during Guoba C1. Bennett, Thundercloud, Xingqiu, Raiden,
  optimizer rolls, and ER remain unchanged (100/175/179/174%).
- FlinsParty is 20,460,639 damage / 205,635 DPS over 99.5 seconds, +1,530,296
  from impact-time VV on Electro/Hydro direct, delayed, Thundercloud, and
  weighted Lunar damage. Sucrose damage, optimizer rolls, duration, rotation,
  and ER remain unchanged (109/100/100/180%).
- FlinsParty2 is 14,794,978 damage / 214,110 DPS over 69.1 seconds, +600,246.
  Matching VV windows account for the material gains. The corrected objective
  moves one Ineffa optimizer roll from ATK% to CRIT_RATE; the resulting weighted
  reaction redistribution includes a documented 55-damage Sucrose decrease.
  Duration, rotation, and ER remain unchanged (141/132/105/193%).
- Focused regression proves that a first VV-triggering Swirl does not read the
  reduction it emits, while a later Anemo-owned Swirl reads the already-active
  cross-element reduction. The tracked `docs/simulation_report.html` generated
  by FlinsParty2 was restored, and no generated output is staged.

## Implementation Order: Standard Aura Tax and Decay Rates

Status:

- Complete; all four phases are implemented and accepted.
- Requirement: a standard elemental source that establishes or extends an aura
  must apply the sourced 0.8 Aura Tax and source-gauge decay rate instead of the
  simulator's generic `6 + 5U` replacement model.

Scope:

- Add one enemy-owned API for applying a source gauge as a finite aura.
- Represent current taxed aura units separately from the source gauge that
  selects the decay rate.
- Preserve the first non-Pyro aura's decay rate across same-element extensions.
- Apply the documented Pyro rule: update to the new source decay rate only when
  the Pyro application changes the current aura amount.
- Route ordinary no-reaction application and the second Electro-Charged element
  through the same API.
- Preserve exact snapshot/restore of current units and decay rate.

Out of scope for this pass:

- Freeze coexisting auras and duration, Quicken/Burning internal gauge state,
  Bloom-family directional consumption, or Electro-Charged premature terminal
  ticks.
- Swirl application to additional targets, multi-target geometry, per-enemy
  aura stores, and innate/self auras.
- Changing reaction consumption modifiers, including the separate Swirl and
  Crystallize 0.5 multiplier gap, or changing character action gauge metadata.
- RL tensors/protocol/training, report layout, Gradle structure, dependencies,
  and generated `docs/` or `output/` files.

Definitions:

- Source gauge: the action's typed 1U/1.5U/2U/4U application value before Aura
  Tax; reaction trigger consumption continues to use this value.
- Taxed aura gauge: `source gauge * 0.8`, stored on the enemy when that element
  becomes an aura.
- Source decay rate: linear units-per-second derived from the maintained KQM
  seconds-per-unit table (1U 11.875, 1.5U 8.9583, 2U 7.5, 4U 5.3125).
- Same-element extension: replace the decayed current amount with the greater of
  current and newly taxed gauge; never add the two amounts.

### Phase 1: Record Aura Application Evidence and Runtime Boundaries - Done

Why first:

Aura units and action source units currently share one `double`; implementation
must establish their different meanings and special Pyro rule before changing
the shared enemy model.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record maintained Aura Tax, decay-rate, same-element extension, and Pyro
  update evidence with access date and simulator adaptation.
- Inventory finite `Enemy.setAura` production callers, test fixtures,
  consumption paths, snapshot fields, and report/RL readers.
- Define excluded special-reaction work so it is not inferred into this pass.

Acceptance criteria:

- Evidence distinguishes source gauge from taxed aura gauge and gives expected
  initial amount/duration for 1U, 1.5U, 2U, and 4U.
- Ordinary no-reaction and Electro-Charged application callers are identified;
  Burning's maintained reaction state is not misclassified as a fresh source.
- Snapshot/restore and all current-time readers are explicitly covered by later
  phases without changing public observation shapes.

Test cases to add or update:

- No production test in this phase; Phases 2 and 3 add pre-fix failing unit and
  runtime boundaries.

Verification:

- inspect `Enemy`, `CombatActionResolver`, `ReactionEffectScheduler`, all
  `setAura` callers, aura regressions, and snapshot consumers
- `python scripts/preflight.py --run`

Completion evidence:

- The maintained KQM Elemental Gauge Theory states a 0.8 Aura Tax, source-class
  rates of 11.875/8.9583/7.5/5.3125 seconds per unit, first-aura rate retention,
  max-style same-element extension, and Pyro's conditional rate update. The
  maintained Elemental Gauge Database independently lists 0.8/1.2/1.6/3.2
  taxed gauges and 9.5/10.75/12/17-second durations. Sources accessed
  2026-08-02: https://library.keqingmains.com/combat-mechanics/elemental-effects/elemental-gauge-theory
  and https://library.keqingmains.com/resources/compendiums/elemental-gauges.
- Adopt the standard source application and extension rules exactly within the
  single-target aura model. Existing `setAura` fixtures remain explicit state
  setup; the new production API owns tax and extension policy.
- Runtime finite applications occur in ordinary no-reaction resolution and
  Electro-Charged scheduling. Burning writes maintained reaction state and is
  excluded. Report, log, resonance, target-dependent stats, and RL observation
  code already consume `Enemy` current-time readers.

### Phase 2: Implement Taxed Aura State and Same-Element Extension - Done

Why second:

The enemy model must own gauge conversion, decay class, extension, and snapshot
semantics before runtime reaction paths can call the new contract.

Target files:

- `src/java/model/entity/Enemy.java`
- `src/java/model/entity/AGENTS.md`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add a source-application API that taxes supported positive source gauges and
  selects the matching decay rate.
- Preserve exact current units/rate across consumption and snapshot restore.
- Implement non-Pyro first-rate retention and conditional Pyro rate updates.
- Keep explicit infinite/raw fixture setup separate from production policy.

Acceptance criteria:

- Fresh 1U/1.5U/2U/4U sources store 0.8/1.2/1.6/3.2 and expire at
  9.5/10.75/12/17 seconds respectively.
- A stronger same-element non-Pyro source replaces the current amount while
  retaining the first source's decay rate; a weaker non-extending source changes
  neither amount nor rate.
- Pyro changes rate when its amount changes and retains rate when it does not.
- Consumption and snapshot restore preserve the selected rate exactly.
- Zero, negative, Physical, Anemo, Geo, NaN, infinite, and unsupported source
  values cannot create malformed finite aura state.

Test cases to add or update:

- Normal: all four supported source classes have exact taxed start and expiry.
- Extension: 1U Electro then 2U becomes 1.6U at the original 1U rate.
- No-op: a weaker Electro source under a stronger remaining gauge changes none.
- Pyro: amount-changing 2U refresh adopts D(2); non-changing 1U keeps D(2).
- Snapshot: consume, capture, advance, restore, and continue at the same rate.
- Abnormal: invalid elements/values are rejected or ignored by explicit policy.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/entity/Enemy.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- `Enemy.applyAura` is the sole source-application policy: it applies 0.8 Tax,
  derives linear rate from `2.5 * U + 7`, performs max-style extension, retains
  the first non-Pyro rate, and updates Pyro rate only when amount changes.
- Aura snapshots now persist current units, application time, and actual decay
  rate. Consumption and restore therefore preserve rates selected by source
  application or same-element extension; raw finite/infinite setters remain
  explicit fixture/state APIs.
- Regression proves exact 0.8/1.2/1.6/3.2 start values and
  9.5/10.75/12/17-second expiries, stronger/weaker Electro extension, both Pyro
  update branches, consumed-state restoration, and invalid value/element no-op
  policy. Reaction regression, build, Javadoc, and routed validation pass.

### Phase 3: Route Standard Runtime Aura Applications - Done

Why third:

Only after the state contract is proven can ordinary actions and coexistence
scheduling stop calling the raw fixture setter.

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/mechanics/reaction/AGENTS.md`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Route persistent no-reaction trigger elements through source application.
- Route the newly introduced Electro/Hydro side of Electro-Charged through the
  same source contract without taxing existing aura consumption.
- Leave Burning-maintained Pyro state on the explicit raw-state path.
- Add actual action and EC scheduling regressions, including repeated aura
  extension and exact expiry.

Acceptance criteria:

- A real 1U action against no aura establishes 0.8U with D(1) expiry.
- Repeated same-element actions extend by max replacement and do not add gauge.
- EC's introduced element is taxed once while the existing element remains
  unchanged until tick consumption/decay.
- Anemo/Geo/Physical and 0U actions never establish a persistent aura.
- Reaction ownership, damage, ICD, and 0.4U EC tick consumption are unchanged.

Test cases to add or update:

- Normal: actual 1U action initial application and 2U stronger extension.
- EC: Hydro aura plus 1U Electro produces 0.8U Electro before scheduled ticks.
- No-op: same-element weaker reapplication below current gauge does not shorten
  the existing aura.
- Abnormal: 0U, Anemo, Geo, Physical, and ICD-blocked actions do not add aura.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/mechanics/reaction/ReactionEffectScheduler.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Ordinary no-reaction application and the Electro/Hydro element introduced by
  Electro-Charged now pass pre-tax action gauge to `Enemy.applyAura`. Burning
  retains explicit reaction-state assignment and is not taxed twice.
- Actual runtime regression proves 1U to 0.8U, stronger 2U max extension, weaker
  no-shortening behavior, EC's taxed introduced side plus unchanged 0.4U tick
  consumption, and rejection of 0U/Physical/Anemo/Geo state and false aura logs.
- Existing standard/no/shared ICD, Lunar-Charged cadence, orbital Hydro, aura
  ownership, reaction damage, and notification regressions pass with their
  expected taxed runtime gauges. Build and routed validation pass.

### Phase 4: Re-Accept Aura-Sensitive Party Baselines - Done

Why last:

The corrected initial amounts, expiry times, and extensions can alter reaction
ownership throughout every audited rotation and therefore require full trace
attribution after focused contracts pass.

Target files:

- `README.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh payloads each for RaidenParty, FlinsParty, and FlinsParty2.
- Compare normalized hashes, totals, DPS, duration, ER, warnings, reaction
  sequence, aura ownership, and optimizer allocations with B-048 baselines.
- Update current accuracy notes and close B-049 only after all deltas are
  attributable to taxed application, sourced decay, or deterministic optimizer
  response.

Acceptance criteria:

- Each party's two normalized payloads match without warning or energy failure.
- Durations and rotations remain unchanged; reaction/aura deltas agree with the
  focused source-application model.
- README, verification skill, plan, and ledger agree and no generated output is
  staged.

Test cases to add or update:

- Normal integration: all three parties complete deterministically.
- Abnormal integration: no warning, optimizer failure, invalid aura, asset, or
  generated-file regression is introduced.

Verification:

- two fresh `./gradlew RaidenParty` runs
- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Each party's two normalized payloads match: RaidenParty SHA-256
  `1565f197fe7813ef53bc7ee4107a6b68aae7337fdf1efb9e5d865502f2813d80`,
  FlinsParty `b374971bc2ee6237bf0eb9eada25c13b0b6976168a40354cca9b4d050fb77da8`,
  and FlinsParty2
  `44712083d51e77ed23637f94b48f390db7414572c14e61123f0085378691968c`.
  All six runs complete without warning, error, failed action, or insufficient
  ER output.
- RaidenParty is 1,348,716 damage / 64,225 DPS over 21.0 seconds, -10,243 from
  B-048. Taxed EC gauges remove exactly two 3,414 immediate EC reactions at
  10.6 and 18.1 seconds and one 3,414 EC tick at 18.1 seconds (display-rounded
  components account for the one-point total difference). Bennett, Xingqiu,
  Xiangling, all other reactions, optimizer rolls, rotation, and
  100/175/179/174% ER are unchanged.
- FlinsParty retains 20,460,639 / 205,635 over 99.5 seconds and
  109/100/100/180% ER. Aura-sensitive intermediate optimizer scores require an
  additional coordinate pass, but converge to the same rolls; the final timed
  damage/reaction trace is unchanged.
- FlinsParty2 retains 14,794,978 / 214,110 over 69.1 seconds, the same optimizer
  rolls, timed damage/reaction trace, and 141/132/105/193% ER. Its normalized
  payload changes only with the source-aura log wording.
- README's aura contract and current baselines, the verification reference, plan,
  and ledger agree. The tracked FlinsParty2 HTML report was restored and no
  generated output is staged.

## Implementation Order: Anemo and Geo Aura Consumption

Status:

- Complete; all three phases are implemented and accepted.
- Requirement: Swirl and Crystallize triggers consume half of their pre-tax
  Anemo/Geo source gauge from the existing aura, rather than the full source
  gauge used by ordinary transformative reactions.

Scope:

- Apply a 0.5 source-gauge multiplier to single-target Swirl consumption.
- Apply the same multiplier to standard and Lunar Crystallize consumption.
- Preserve reaction damage, listeners, VV timing, Moondrift policy, ownership,
  and current aura decay rate after partial consumption.
- Re-accept all three aura-sensitive deterministic party baselines.

Out of scope for this pass:

- Swirl's propagated elemental application to additional targets, absorption,
  multi-target geometry, or simultaneous-reaction priority.
- Crystallize shield generation/absorption, enemy attacks, Freeze, Dendro-special
  consumption, Electro-Charged terminal ticks, or same-element source extension.
- The deferred B-042 question of whether first-Swirl damage receives same-hit VV
  reduction; this pass changes only aura consumption after reaction detection.
- Action gauge metadata, damage formulas, RL contracts/training, reports,
  dependencies, and generated output.

Definitions:

- Anemo/Geo consumption: `trigger source gauge * 0.5`, subtracted from the
  current decayed aura at the reaction timestamp.
- Residual aura: the current aura after scaled discrete consumption, continuing
  at its already-selected decay rate.

### Phase 1: Record Consumption Evidence and Resolver Inventory - Done

Why first:

The existing resolver shares full-gauge consumption across reaction families;
the source-specific multiplier and Lunar conversion boundary must be fixed
before changing those branches.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record maintained Swirl/Crystallize 0.5 multiplier evidence and access date.
- Inventory standard Swirl, standard Crystallize, and Lunar-Crystallize consumers
  plus existing aura/damage/listener regressions.
- Define no-change boundaries for all other reaction consumption and B-042.

Acceptance criteria:

- Evidence gives exact 1U and 2U trigger consumption expectations.
- All three runtime branches and affected party scenarios are named.
- Later phases include residual, full-depletion, unrelated-reaction, and
  integration tests.

Test cases to add or update:

- No production test in this phase; Phase 2 adds failing consumption boundaries.

Verification:

- inspect `CombatActionResolver`, `ReactionCalculator`, `ReactionResult`, VV,
  Lunar-Crystallize handling, and current aura regressions
- `python scripts/preflight.py --run`

Completion evidence:

- The maintained KQM Elemental Gauge Theory states that all Anemo and Geo
  triggers have a 0.5 unit modifier: 1U consumes 0.5U, 2U consumes 1U, and 4U
  consumes 2U. Source accessed 2026-08-02:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/elemental-gauge-theory.
- Adopt the multiplier for single-target standard/Lunar consumption. Swirl
  propagation is multi-target and remains excluded.
- Current full-gauge consumers are transformative Swirl and the standard/Lunar
  Crystallize stateful branches in `CombatActionResolver`; damage and reaction
  notification are already separate from aura subtraction.

### Phase 2: Scale Swirl and Crystallize Aura Consumption - Done

Why second:

The three resolver branches can share one typed consumption helper after the
sourced boundary is fixed.

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Centralize reaction-kind-based aura consumption without using display labels.
- Apply 0.5 only to Swirl, Crystallize, and Lunar-Crystallize.
- Add actual action regressions over taxed finite auras and preserve exact
  current-time decay/rebase behavior.

Acceptance criteria:

- 1U Anemo/Geo against fresh 0.8U leaves 0.3U; 2U against 1.6U leaves 0.6U.
- Standard and Lunar Crystallize leave the same residual gauge.
- A residual below or equal to scaled consumption is removed without negatives.
- Overload/Superconduct, amplifying reactions, Bloom/Quicken, EC ticks, damage,
  notifications, VV first-Swirl ordering, and Moondrift cadence are unchanged.

Test cases to add or update:

- Normal: 1U Swirl and standard Crystallize each leave 0.3U from a fresh 1U
  source aura.
- Strong: 2U Swirl consumes 1U from a fresh 2U source aura.
- Lunar: Lunar-Crystallize matches standard Geo consumption while retaining
  conversion and Moondrift state.
- Boundary: exactly 0.5U and less are fully removed; no negative aura appears.
- No-change: 1U Overload still consumes 1U and first VV Swirl damage remains on
  the pre-VV resistance multiplier.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- `CombatActionResolver` now selects aura consumption from typed reaction kind;
  Swirl, Crystallize, and Lunar-Crystallize use `source gauge * 0.5`, while all
  other reaction families retain their existing policy.
- Actual finite-aura regression proves 1U Swirl/Geo 0.8 to 0.3, 2U Swirl 1.6 to
  0.6, standard/Lunar Crystallize parity with retained Moondrift creation,
  exact/below 0.5 depletion, and unchanged full-consumption Overload. Existing
  first-VV damage ordering, reaction notification, damage, and Lunar cadence
  tests pass.
- Swirl's former post-reduction unconditional clear is skipped so the scaled
  residual persists. The separate unconditional-clear defect for
  Overload/Superconduct residuals is recorded as B-051 rather than expanded into
  this phase. Build, reaction regression, and routed validation pass.

### Phase 3: Re-Accept Swirl-Sensitive Party Baselines - Done

Why last:

Residual Hydro/Electro after Sucrose hits can change later EC, Lunar, and aura
ownership, so full deterministic traces must be attributed after focused tests.

Target files:

- `README.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh payloads each for RaidenParty, FlinsParty, and FlinsParty2.
- Compare normalized hashes, totals, DPS, ER, optimizer allocation, reaction
  sequence, and per-source changes against B-049.
- Update current baselines and close B-050 only with complete attribution.

Acceptance criteria:

- Each repeated payload matches without warning or energy failure.
- Any party delta follows residual-aura windows; RaidenParty remains unchanged if
  its rotation has no Anemo/Geo trigger.
- README, verification skill, plan, and ledger agree; generated output is not
  staged.

Test cases to add or update:

- Normal integration: all three audited parties complete deterministically.
- Abnormal integration: no warning, optimizer, ER, invalid-aura, or generated
  output regression appears.

Verification:

- two fresh `./gradlew RaidenParty` runs
- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Two normalized payloads per party match exactly: unchanged RaidenParty
  `1565f197fe7813ef53bc7ee4107a6b68aae7337fdf1efb9e5d865502f2813d80`,
  FlinsParty `4ad65138b5288f4c627194509fc24be69b8d21771efc09a66a1bd79dd92a2b96`,
  and FlinsParty2
  `118edbd3665d167d31e9bbbbffc97ffc499a40db5199a573a5e25ed8eea023a6`.
  All six runs complete without warning, error, failed action, or insufficient
  energy output.
- RaidenParty remains 1,348,716 / 64,225 over 21.0 seconds with identical full
  payload, optimizer rolls, and 100/175/179/174% ER because it contains no
  Anemo/Geo trigger.
- FlinsParty is 22,620,467 / 227,341 over 99.5 seconds, +2,159,828. Residual
  auras increase final-run Swirls from 47 to 58 and immediate Lunar-Charged
  reactions from 127 to 172, while 48 scheduled Lunar ticks, optimizer rolls,
  rotation, and 109/100/100/180% ER remain unchanged. Per-source deltas are
  Columbina +260,031, Sucrose +683,401, Thundercloud +12,855, Flins +718,738,
  and Ineffa +484,803.
- FlinsParty2 is 15,482,126 / 224,054 over 69.1 seconds, +687,148. Swirls rise
  from 31 to 35 and immediate Lunar-Charged reactions from 80 to 105 while 33
  scheduled ticks remain. The ER optimizer selects 130/128/100/196% and adjusts
  Sucrose to 16 EM rolls, Ineffa to 10 CD/6 CR/4 ATK rolls, and Columbina to
  5 CD/0 HP/8 CR rolls; Flins rolls remain unchanged. Per-source deltas are
  Columbina +51,659, Sucrose +162,214, Thundercloud -16,709, Flins +383,654,
  and Ineffa +106,331.
- README, verification reference, plan, and ledger agree. Generated FlinsParty2
  HTML was restored and no output artifact is staged.

## Implementation Order: Bloom Directional Aura Consumption

Status:

- Complete; all three phases are implemented and accepted.
- Requirement: standard Bloom and Lunar-Bloom must consume existing aura with
  the sourced Hydro:Dendro 2:1 directional ratio instead of a shared 1.0
  trigger-gauge multiplier.

Scope:

- Consume `0.5 * source gauge` when Hydro triggers Bloom on Dendro.
- Consume `2.0 * source gauge` when Dendro triggers Bloom on Hydro.
- Apply the same core-creation consumption policy after Lunar conversion.
- Preserve Dendro Core ownership, damage, cap/expiry policy, reaction
  notification, and Lunar Dew increments.
- Re-accept the three deterministic audited party baselines as no-Dendro
  controls.

Out of scope for this pass:

- Treating Quicken as a coexisting Dendro aura for Bloom, Quicken gauge state,
  Burning gauge internals, Hyperbloom/Burgeon consumption, and Freeze.
- Multi-target cores, simultaneous-reaction priority, source action gauge
  metadata, and changes to core damage or ownership.
- RL contracts/training, reports, dependencies, build structure, and generated
  output.

Definitions:

- Weak Hydro direction: Hydro source gauge multiplied by 0.5 and subtracted
  from the current Dendro aura.
- Strong Dendro direction: Dendro source gauge multiplied by 2.0 and subtracted
  from the current Hydro aura.
- Directional consumption policy: a typed resolver decision based on reaction
  kind, trigger element, and aura element; display labels do not participate.

Cross-cutting design rules:

- Keep the resolver responsible only for orchestration and delegate the
  directional arithmetic to one side-effect-free typed helper.
- Extend the existing aura-consumption policy instead of duplicating Bloom and
  Lunar-Bloom branches, preserving single responsibility and open extension by
  reaction kind.
- Tests exercise public simulator actions and observable state; they do not
  couple to private helper implementation.

### Phase 1: Record Bloom Evidence and Runtime Boundary - Done

Why first:

The existing stateful branch uses one full-gauge subtraction in both trigger
directions, so the asymmetric rule and Lunar conversion boundary must be fixed
before changing shared resolver policy.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record maintained Bloom consumption-ratio evidence and access date.
- Inventory standard/Lunar core creation, typed reaction conversion, current
  aura subtraction, Dew counters, ownership, and existing core regressions.
- Define Quicken coexistence and other Dendro-special state as separate work.

Acceptance criteria:

- Evidence identifies Hydro as the weak element and gives the 2:1
  Hydro:Dendro ratio.
- Expected residuals are explicit for both trigger directions and exact/full
  depletion boundaries.
- Later phases cover standard/Lunar parity, side effects, and no-Dendro party
  controls without expanding into excluded state models.

Test cases to add or update:

- No production test in this phase; Phase 2 adds pre-fix failing directional
  action boundaries.

Verification:

- inspect `CombatActionResolver`, `ReactionCalculator`, `ReactionResult`,
  `ReactionEffectScheduler`, Dendro Core state, Dew counters, and Bloom tests
- `python scripts/preflight.py --run`

Completion evidence:

- The maintained KQM Transformative Reactions reference states that Bloom
  gauge consumption has a 2:1 Hydro:Dendro ratio, identifies Hydro as the weak
  element, and says application order changes gauge consumption rather than
  damage. Source accessed 2026-08-02:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/transformative-reactions.
- Adapt the ratio as 0.5x Hydro-on-Dendro and 2.0x Dendro-on-Hydro consumption,
  matching the repository's pre-tax trigger-gauge convention.
- Standard and Lunar-Bloom share the same stateful core branch after typed Lunar
  conversion. Core creation, ownership, Dew counters, delayed damage, and
  notifications are already separate from the one aura subtraction in scope.

### Phase 2: Implement Directional Standard and Lunar-Bloom Consumption - Done

Why second:

The runtime can adopt the sourced asymmetry through the existing typed
consumption policy once both directions and no-change side effects are
executable.

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Extend the typed aura-consumption helper with trigger and aura elements.
- Apply 0.5 only to Hydro-on-Dendro Bloom/Lunar-Bloom and 2.0 only to
  Dendro-on-Hydro Bloom/Lunar-Bloom.
- Route the stateful Bloom branch through that helper and add actual action
  regressions for residual, depletion, notification, core, ownership, and Dew
  behavior.

Acceptance criteria:

- 1U Hydro on fresh taxed 2U Dendro leaves 1.1U; 2U Hydro leaves 0.6U.
- 1U Dendro on fresh taxed 4U Hydro leaves 1.2U; fresh taxed 2U Hydro fully
  depletes without exposing negative gauge.
- Lunar-Bloom matches both directional consumptions while retaining one core
  and one increment to each Dew counter.
- Core damage/ownership, reaction listener count, cap/expiry, Swirl,
  Crystallize, Overload, and Superconduct behavior remain unchanged.

Test cases to add or update:

- Weak direction: 1U and 2U Hydro triggers leave 1.1U and 0.6U Dendro.
- Strong direction: 1U Dendro leaves 1.2U from taxed 4U Hydro and removes taxed
  2U Hydro completely.
- Lunar: both directions use the same multipliers and preserve core plus Dew
  side effects.
- Boundary: exactly or less than directional consumption removes the aura with
  no negative units.
- No-change: one reaction notification and core owner remain the triggering
  character; established non-Bloom consumption regressions still pass.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- The existing typed aura-consumption helper now accepts trigger and aura
  elements. It applies 0.5x only to Hydro-on-Dendro Bloom/Lunar-Bloom and 2.0x
  only to Dendro-on-Hydro Bloom/Lunar-Bloom; established Anemo/Geo and default
  policies remain in the same side-effect-free switch.
- Public action regressions prove weak-direction 1U/2U residuals of 1.1U/0.6U,
  a strong-direction 1U residual of 1.2U from taxed 4U Hydro, and complete
  removal from taxed 2U and 1U Hydro without negative state.
- Standard Bloom emits one typed notification, creates one core owned by the
  trigger character, and deals no immediate damage. Both Lunar directions
  retain directional consumption, one core, and one Verdant/Moonridge Dew
  increment. Existing core expiry/cap, Swirl, Crystallize, Overload, and
  Superconduct regressions pass.
- Reaction regression, build, Javadoc, and routed build/reaction validation all
  pass.

### Phase 3: Re-Accept Bloom-Neutral Party Baselines - Done

Why last:

None of the three audited parties contains Dendro, so exact repeated payloads
provide a broad no-change control over shared reaction orchestration.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh payloads each for RaidenParty, FlinsParty, and FlinsParty2.
- Compare normalized hashes, totals, DPS, ER, optimizer allocation, warnings,
  and durations against B-051.
- Close B-052 only when all three payloads are exact no-change controls.

Acceptance criteria:

- Each repeated payload matches its pair and the corresponding B-051 payload.
- No warning, energy, optimizer, action, aura, or generated-output regression is
  introduced.
- Plan and ledger agree; tracked generated HTML is restored and no output is
  staged.

Test cases to add or update:

- Normal integration: all three audited parties complete deterministically.
- Abnormal integration: any non-Bloom payload delta blocks acceptance and is
  investigated rather than normalized away.

Verification:

- two fresh `./gradlew RaidenParty` runs
- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Each pair and its B-051 baseline has the same normalized SHA-256:
  RaidenParty
  `985f95d5c7779b81013dad0cf6232557b453ea44034c224f1b3ec1795a3b8614`,
  FlinsParty
  `4ad65138b5288f4c627194509fc24be69b8d21771efc09a66a1bd79dd92a2b96`,
  and FlinsParty2
  `118edbd3665d167d31e9bbbbffc97ffc499a40db5199a573a5e25ed8eea023a6`.
- RaidenParty remains 1,363,709 / 64,939 over 21.0 seconds at
  100/175/179/174% ER. FlinsParty remains 22,620,467 / 227,341 over 99.5
  seconds at 109/100/100/180% ER. FlinsParty2 remains 15,482,126 / 224,054
  over 69.1 seconds at 130/128/100/196% ER.
- All six runs complete without warning, error, failed action, or insufficient
  energy output. Their optimizer allocations and complete payloads are exact
  no-change controls because none of the parties contains Dendro.
- The tracked FlinsParty2 HTML report was clean before execution and restored
  afterward; no generated output is staged.

## Implementation Order: Aubade Static Stats and Initial Off-Field State

Status:

- Complete; all three phases are implemented and accepted.
- Requirement: Aubade of Morningstar and Moon must provide 80 Elemental Mastery
  as its 2-piece bonus and make its owner-only Lunar Reaction bonus available
  from the owner's initial off-field state.

Scope:

- Replace the incorrect 18% ATK 2-piece stat with 80 Elemental Mastery.
- Add a narrow simulator-initialized artifact capability that receives the
  owner and whether that owner starts active.
- Initialize Aubade for owners who join the completed runtime as off-field,
  while granting no 4-piece state to an owner who starts active.
- Preserve 20% owner Lunar Reaction DMG off-field, an additional 40% at
  Ascendant Gleam, and the three-second switch-in linger.
- Re-accept RaidenParty as an unrelated control and both Aubade-equipped Flins
  party baselines.

Out of scope for this pass:

- Artifact inventory/piece-count modeling, alternate set selection, rarity,
  substat farming, or changing optimizer KQMS roll constraints.
- Turning Aubade into a team buff, changing Lunar damage formulas, Moonsign
  derivation, Silken Moon, or Night of the Sky's Unveiling.
- EC/Freeze/Dendro state, RL contracts/training, reports, dependencies, and
  generated output.

Definitions:

- Initial off-field owner: a character added after another party member is
  already active; Aubade's owner bonus is live immediately at simulator time 0.
- Initial active owner: the first party member, who has not yet satisfied the
  off-field activation condition and receives no Aubade 4-piece bonus.
- Lingering state: after an activated owner becomes active, the owner-only
  bonus remains in `[switch-in, switch-in + 3.0)` and is absent at exact expiry.

Cross-cutting design rules:

- Depend on a capability interface from simulator orchestration; do not add an
  artifact-name conditional or optional lifecycle method to `ArtifactSet`.
- Keep fixed stat data and switch-state policy inside Aubade; keep party-active
  discovery in `CombatSimulator`.
- Reuse one typed `AUBADE_BONUS` owner buff and mutate only its expiry, avoiding
  duplicate instances across repeated swaps.
- Exercise behavior through public simulator, switch, stat-resolution, and
  snapshot APIs rather than concrete private buff internals.

### Phase 1: Record Aubade Evidence and Lifecycle Inventory - Done

Why first:

The current class has both incorrect static data and a runtime activation gap;
the owner-only boundary and initial state must be explicit before adding a
shared artifact lifecycle capability.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record maintained 2-piece, 4-piece, owner-only, Moonsign, and three-second
  wording with access date.
- Inventory artifact construction in both Flins parties, optimizer static-stat
  inputs, party insertion/active selection, switch callbacks, owner buff
  resolution, and snapshot behavior.
- Define no-change boundaries for unrelated artifacts and party systems.

Acceptance criteria:

- Evidence distinguishes 80 EM from the current 18% ATK and confirms that the
  20%/60% Lunar bonus applies only to the wearer.
- Initial active versus initial off-field behavior and exact three-second expiry
  are stated without assuming a synthetic switch at time 0.
- Later phases name fixed stats, lifecycle initialization, no-stack behavior,
  snapshot continuity, and all affected party baselines.

Test cases to add or update:

- No production test in this phase; Phase 2 adds pre-fix failing fixed-stat and
  lifecycle cases.

Verification:

- inspect Aubade, artifact capabilities, `CombatSimulator.addCharacter`,
  `Party`, `SwitchManager`, stat assembly, snapshots, both Flins party factories,
  and current artifact regressions
- `python scripts/preflight.py --run`

Completion evidence:

- The maintained Genshin Impact Wiki set entry records 80 Elemental Mastery for
  2 pieces; while off-field, the wearer gains 20% Lunar Reaction DMG plus 40%
  more at Ascendant Gleam, and the effect disappears after three active seconds.
  Its gameplay notes explicitly classify the bonus as owner-only. Icy Veins
  independently reproduces the same set values. Sources accessed 2026-08-02:
  https://genshin-impact.fandom.com/wiki/Aubade_of_Morningstar_and_Moon and
  https://www.icy-veins.com/genshin-impact/artifacts/15043.
- The implementation currently adds 18% ATK in both constructors. Both party
  optimizers read that incorrect static block before constructing the equipped
  set, so allocation and final stats are affected without double-counting.
- `Party` makes its first member active. Aubade only creates its owner buff from
  switch callbacks, leaving initially off-field wearers uninitialized until
  their first field entry. Character snapshot restore preserves an initialized
  buff reference, so initializing once at party insertion closes that gap
  without a separate snapshot format.

### Phase 2: Implement Aubade Stats and Initialization Contract - Done

Why second:

The concrete set can be corrected safely once runtime initialization is exposed
through one opt-in interface and proven against active/off-field transitions.

Target files:

- `src/java/model/entity/SimulatorInitializedArtifactEffect.java`
- `src/java/model/entity/AGENTS.md`
- `src/java/model/artifact/AubadeOfMorningstarAndMoon.java`
- `src/java/model/artifact/AGENTS.md`
- `src/java/simulation/CombatSimulator.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add the narrow initialization capability and dispatch it once after party
  insertion with the actual initial-active flag.
- Correct both Aubade constructors to 80 EM and remove the obsolete exploratory
  comments without changing unrelated artifact code.
- Initialize one off-field owner buff, retain no initial active-owner bonus, and
  preserve dynamic Moonsign and switch linger behavior.
- Add fixed-stat, owner-only, active/off-field, exact expiry, repeated switch,
  and snapshot regressions.

Acceptance criteria:

- Both constructors add exactly 80 EM and zero ATK%; supplied main/sub stats are
  retained without adding the set bonus twice.
- An initially off-field owner resolves 20% at Nascent and 60% at Ascendant;
  an ally resolves no Aubade bonus.
- An initially active owner resolves no bonus until it first switches out.
- Activated switch-in retains the bonus through 2.999 seconds and loses it at
  exact 3.0; switching out reactivates it immediately.
- Repeated transitions retain one typed owner buff, and save/restore preserves
  the initialized state and expiry.
- Characters with null/plain artifacts and all unrelated artifact dispatch
  remain unchanged.

Test cases to add or update:

- Static normal: empty and supplied-stat constructors each add exactly 80 EM.
- Static abnormal: no 18% ATK remains and supplied input is not double-counted.
- Initial state: off-field owner gets 20%/60%; active owner gets zero; ally gets
  zero.
- Switch boundary: +2.999 active retains, exact +3.0 loses, switch-out revives.
- Idempotence: repeated in/out transitions leave one `AUBADE_BONUS` instance.
- Snapshot: restore returns the owner, activation mode, and exact remaining
  expiry behavior.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/entity/SimulatorInitializedArtifactEffect.java --path src/java/model/artifact/AubadeOfMorningstarAndMoon.java --path src/java/simulation/CombatSimulator.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Both Aubade constructors now add exactly 80 Elemental Mastery and no ATK%.
  Supplied artifact stats retain their existing EM/CR values and receive the set
  bonus once.
- `SimulatorInitializedArtifactEffect` is a narrow opt-in capability dispatched
  once after party insertion. `CombatSimulator` supplies the actual
  initial-active flag without naming Aubade or expanding `ArtifactSet`.
- Aubade initializes one typed owner buff only for an initially off-field
  wearer. Regression proves 20% Nascent and 60% Ascendant values across all
  Lunar types, zero ally bonus, and zero initial bonus for an active wearer.
- Public switch regression proves activity at +2.999 seconds, exclusion at exact
  +3.000, immediate switch-out reactivation, and one `AUBADE_BONUS` instance.
  Snapshot restore recovers the active owner and exact remaining linger expiry.
- Reaction regression, build, Javadoc, and routed build/reaction validation all
  pass. Null/plain artifact paths continue through the unchanged no-op branch.

### Phase 3: Re-Accept Aubade Party Baselines - Done

Why last:

Correcting the fixed stat changes optimizer objectives and direct/reaction
damage for one wearer in each Flins party, while initialization may change the
first off-field window.

Target files:

- `README.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh payloads each for RaidenParty, FlinsParty, and FlinsParty2.
- Compare normalized hashes, totals, DPS, ER, optimizer rolls, per-source
  damage, and timelines against B-052.
- Attribute Flins deltas to corrected Aubade stats/state and accept Raiden only
  as an exact unrelated control.

Acceptance criteria:

- Each pair matches without warning, energy, optimizer, or action failure.
- Raiden is exact no-change; Flins changes are deterministic and confined to
  Aubade wearer stats, resulting reaction damage, and optimizer response.
- README, verification skill, plan, and ledger agree; generated output is not
  staged.

Test cases to add or update:

- Normal integration: all three audited parties complete deterministically.
- Abnormal integration: unexplained non-Aubade deltas, warnings, ER failures,
  or generated-file leakage block acceptance.

Verification:

- two fresh `./gradlew RaidenParty` runs
- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Two normalized payloads per party match exactly: unchanged RaidenParty
  `985f95d5c7779b81013dad0cf6232557b453ea44034c224f1b3ec1795a3b8614`,
  FlinsParty
  `1a514b75a60f384c56a577e84a82af3bce4ef652e5304132c184f01c94f2a81f`,
  and FlinsParty2
  `3077dba03531db0d61f1de2f0d8ae7e8a38fa389edca87855d2860aa965a6c82`.
  All six runs complete without warning, error, failed action, or insufficient
  energy output.
- RaidenParty is the exact B-052 control at 1,363,709 / 64,939 over 21.0 seconds,
  the same optimizer payload, and 100/175/179/174% ER.
- FlinsParty is 22,675,823 / 227,898 over 99.5 seconds, +55,356. Optimizer rolls
  and 109/100/100/180% ER are unchanged. Correcting Ineffa's 18% ATK to 80 EM
  lowers Ineffa by 43,539 while weighted Lunar contributions raise Columbina by
  24,855, Sucrose by 6,323, Thundercloud by 40,777, and Flins by 26,941
  (display-rounded components differ from the exact total by one).
- FlinsParty2 is 15,817,125 / 228,902 over 69.1 seconds, +334,999 at unchanged
  130/128/100/196% ER. Columbina gains 167,855, Sucrose 6,989, Thundercloud
  62,248, Flins 59,729, and Ineffa 38,178. Sucrose, Flins, and Columbina rolls
  are unchanged; the corrected global objective moves one Ineffa roll from ATK%
  to CRIT_RATE, yielding 10 CD/7 CR/3 ATK rolls.
- Both Flins scenarios retain exactly 613/468 timed actions, 230/140 reaction
  logs, 48/33 delayed Lunar ticks, 172/105 immediate Lunar-Charged reactions,
  and 88/71 ICD blocks respectively. The initial-state capability changes no
  current rotation event because each Aubade owner enters before acting; the
  integration deltas are therefore fixed-stat and deterministic optimizer
  effects.
- README, verification reference, plan, and ledger agree. The tracked
  FlinsParty2 HTML report was clean before execution and restored afterward; no
  generated output is staged.

## Implementation Order: Overload and Superconduct Residual Aura

Status:

- Complete; all three phases are implemented and accepted.
- Requirement: Overload and Superconduct subtract their 1.0-modified trigger
  source gauge and retain any positive decayed aura remainder.

Scope:

- Remove the post-subtraction unconditional clear for Overload and Superconduct.
- Preserve full depletion when trigger consumption equals or exceeds current
  aura and retain the aura's selected decay rate otherwise.
- Preserve reaction damage, Superconduct Physical RES ordering, notifications,
  ownership, and all other reaction-family consumption.
- Re-accept the three deterministic aura-sensitive party baselines.

Out of scope for this pass:

- Swirl/Crystallize consumption (B-050), Melt/Vaporize modifiers, Freeze,
  Electro-Charged, Dendro-family gauges, and reaction damage formulas.
- Simultaneous reaction priority, aura transition to an excess trigger, innate
  aura, multi-target state, enemy attacks, and shields.
- RL contracts/training, report layout, dependencies, build structure, and
  generated output.

Definitions:

- Transformative residual: `current decayed aura - trigger source gauge` after
  the sourced 1.0 Overload/Superconduct unit modifier.
- Full depletion: residual at or below zero, removed by `Enemy.reduceAura`
  without a separate clear operation.

### Phase 1: Record Residual Evidence and Clear Path - Done

Why first:

The multiplier is already numerically correct; evidence must distinguish the
post-subtraction clear defect from B-050 and unrelated trigger-transition work.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record maintained 1.0 consumption and strong-aura residual examples.
- Inventory the shared Transformative reduction/clear path and existing
  Superconduct/Overload regressions.
- Define follow-up ownership, formula, and integration no-change boundaries.

Acceptance criteria:

- Evidence gives a concrete fresh 2U aura plus 1U trigger result of 0.6U.
- The redundant clear after `reduceAura` is identified as the only production
  mutation in scope.
- Later tests cover both element directions, exact depletion, decay, and
  Superconduct's next-hit Physical reduction.

Test cases to add or update:

- No production test in this phase; Phase 2 adds failing residual boundaries.

Verification:

- inspect `CombatActionResolver`, `ReactionCalculator`, Superconduct regressions,
  and B-050 consumption tests
- `python scripts/preflight.py --run`

Completion evidence:

- KQM Elemental Gauge Theory states Overload/Superconduct use a 1x unit modifier
  and gives 1.6U taxed Cryo followed by 1U Electro leaving 0.6U. It also states
  triggers greater than the current aura fully consume it without creating
  negative gauge. Source accessed 2026-08-02:
  https://library.keqingmains.com/combat-mechanics/elemental-effects/elemental-gauge-theory.
- The resolver already subtracts the full action source gauge, then erases the
  result with a shared unconditional `setAura(..., 0)`. Removing that clear lets
  `Enemy.reduceAura` own both residual and full-depletion boundaries.

### Phase 2: Preserve Transformative Residual Aura - Done

Why second:

The shared mutation can be removed only after both residual and full-depletion
contracts are executable.

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Remove the redundant non-EC Transformative aura clear.
- Add actual Overload/Superconduct direction, residual decay, exact depletion,
  and damage/status-order regressions.
- Confirm Swirl's 0.5 residual and all non-target reaction tests remain stable.

Acceptance criteria:

- 1U Pyro on fresh taxed 2U Electro and 1U Electro on fresh taxed 2U Pyro each
  leave 0.6U at D(2).
- Both Superconduct directions retain 0.6U and still emit one 40% Physical RES
  reduction after reaction damage.
- Fresh 1U taxed aura is fully removed by a 1U trigger, and no negative gauge is
  exposed.
- Reaction damage, ownership, listener count, first-hit Superconduct exclusion,
  next-Physical-hit inclusion, and B-050 consumption remain unchanged.

Test cases to add or update:

- Normal: both Overload directions over strong auras leave 0.6U.
- Superconduct: both directions leave 0.6U and preserve status timing.
- Decay: 0.6U residual continues at D(2) and expires 4.5 seconds later.
- Boundary: equal/weaker current auras fully deplete without negatives.
- No-change: 1U Swirl still leaves 0.3U and subsequent Physical damage observes
  Superconduct only after the triggering hit.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Non-EC Transformative handling now relies solely on the preceding typed
  `reduceAura` call; the redundant unconditional clear is removed.
- Actual reaction regressions prove both Overload directions and both
  Superconduct directions leave 0.6U from a fresh taxed 2U aura, emit one typed
  reaction, and retain D(2) until exact 4.5-second residual expiry. Fresh 1U and
  0.5U source auras fully deplete without negative state.
- Existing Superconduct reaction damage and subsequent Physical RES benefit,
  B-050 Swirl residuals, reaction ownership/listeners, build, reaction regression,
  and routed validation all pass.

### Phase 3: Re-Accept Transformative-Reaction Party Baselines - Done

Why last:

Residual Pyro/Electro can change later Overload and EC ownership in RaidenParty;
the Flins parties provide no-change controls for a non-Pyro/Cryo rotation.

Target files:

- `README.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh payloads each for RaidenParty, FlinsParty, and FlinsParty2.
- Attribute hashes, totals, reaction counts, optimizer rolls, ER, and per-source
  deltas against B-050.
- Update current baselines and close B-051 only after deterministic agreement.

Acceptance criteria:

- Each pair matches without warning or energy failure.
- Raiden deltas align with retained Overload residuals; both Flins scenarios are
  unchanged unless the trace proves an eligible reaction path.
- README, verification skill, plan, and ledger agree; generated output is not
  staged.

Test cases to add or update:

- Normal integration: all three parties complete deterministically.
- Abnormal integration: no warning, ER, optimizer, invalid-aura, or output leak
  is introduced.

Verification:

- two fresh `./gradlew RaidenParty` runs
- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Each party's two normalized payloads match exactly: RaidenParty
  `985f95d5c7779b81013dad0cf6232557b453ea44034c224f1b3ec1795a3b8614`,
  unchanged FlinsParty
  `4ad65138b5288f4c627194509fc24be69b8d21771efc09a66a1bd79dd92a2b96`,
  and unchanged FlinsParty2
  `118edbd3665d167d31e9bbbbffc97ffc499a40db5199a573a5e25ed8eea023a6`.
  All six runs complete without warning, error, failed action, or insufficient
  energy output.
- RaidenParty is 1,363,709 / 64,939 over 21.0 seconds, +14,993 from B-050.
  Immediate EC count remains 22 but one reaction moves from 8.2 to 10.6 seconds;
  EC ticks decrease from five to four and Pyro-on-Hydro Vaporizes increase from
  sixteen to seventeen. The added Pyronado Vaporize raises Xiangling by 18,407
  while one removed tick lowers Thundercloud by 3,414. Bennett, Xingqiu, Raiden,
  every other reaction count, optimizer rolls, rotation, and 100/175/179/174%
  ER are unchanged.
- FlinsParty retains 22,620,467 / 227,341, its complete B-050 payload,
  optimizer, 99.5-second rotation, and 109/100/100/180% ER. FlinsParty2 retains
  15,482,126 / 224,054, its complete B-050 payload, optimizer, 69.1-second
  rotation, and 130/128/100/196% ER.
- README, verification reference, plan, and ledger agree. Generated FlinsParty2
  HTML was restored and no output artifact is staged.

## Implementation Order: Night-Only Gleaming Moon Synergy

Status:

- Complete; all three phases are implemented and accepted.
- Requirement: Night of the Sky's Unveiling must supply its 10% party-wide
  Lunar Reaction bonus while Intent is active even when no Silken Moon's
  Serenade set is equipped.

Scope:

- Share one dynamic Gleaming Moon synergy policy between Night and Silken.
- Preserve one canonical provider: the first Silken wearer when any Silken set
  exists, otherwise the first Night wearer.
- Count unexpired Intent and Devotion as distinct effects, at 10% each and 20%
  maximum, for all three typed Lunar Reaction damage bonuses.
- Preserve existing Night Intent and Silken Devotion trigger, duration, stat,
  refresh, source, and party-routing behavior.
- Re-accept the three deterministic party baselines as exact no-change controls.

Out of scope for this pass:

- Changing whether Thundercloud Strike activates Intent, Night or Silken
  trigger conditions, Moonsign calculation, reaction formulas, or buff duration.
- Duplicate-set trigger ownership beyond the existing typed buff uniqueness
  policy, new artifacts, characters, parties, or report layout.
- RL contracts/training, HPC jobs, dependencies, build structure, and generated
  output.

Definitions:

- Canonical provider: exactly one equipped artifact instance that exposes the
  dynamic synergy buff to `BuffManager`.
- Distinct effect: at least one unexpired `GLEAMING_MOON_INTENT` or
  `GLEAMING_MOON_DEVOTION` status anywhere in the party; duplicate IDs do not
  increase the count.

### Phase 1: Record Night Provider Evidence and Ownership - Done

Why first:

The current Silken implementation produces correct mixed-set results, so the
fix must explicitly preserve that ownership while adding the missing Night-only
case.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record maintained Night and Silken wording, distinct-effect stacking, and the
  current provider gap.
- Define the canonical-provider precedence and exact no-change boundary.
- Separate Night's existing reaction trigger classification from this provider
  repair.

Acceptance criteria:

- Evidence supports 10% with Intent alone and 20% with Intent plus Devotion.
- One policy owns provider election and effect counting; artifacts only adapt
  their typed capability to that policy.
- The plan names normal, duplicate, expiry, source, and unaffected integration
  tests before production code changes.

Test cases to add or update:

- No production test in this phase; Phase 2 adds the missing Night-only and
  mixed-set boundaries.

Verification:

- inspect `NightOfTheSkysUnveiling`, `SilkenMoonsSerenade`,
  `ArtifactTeamBuffProvider`, `BuffManager`, and existing Phase F regressions
- `python scripts/preflight.py --run`

Completion evidence:

- KQM's maintained Nod-Krai guide independently states Night's 10% party-wide
  Lunar Reaction bonus while Intent is active, Silken's corresponding 10% while
  Devotion is active, and their distinct-effect 20% combination. Source accessed
  2026-08-02: https://keqingmains.com/misc/nod-krai-guide/.
- Runtime inspection proves `BuffManager` routes generic
  `ArtifactTeamBuffProvider` capabilities correctly, but only Silken implements
  that capability. Night therefore creates Intent without any Night-only
  provider.
- The implementation boundary retains Silken precedence for every existing
  mixed party, falls back to Night only when Silken is absent, and leaves the
  separate Thundercloud trigger classification unchanged.
- The documentation-only preflight passes with no checks or artifact leaks.

### Phase 2: Share Canonical Gleaming Moon Synergy - Done

Why second:

The shared policy and both artifact adapters can be changed together only after
the provider precedence and regression boundaries are durable.

Target files:

- `src/java/model/artifact/GleamingMoonSynergy.java`
- `src/java/model/artifact/NightOfTheSkysUnveiling.java`
- `src/java/model/artifact/SilkenMoonsSerenade.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add a focused package policy for canonical provider election, distinct active
  effect counting, and typed dynamic buff construction.
- Make Night implement `ArtifactTeamBuffProvider` and delegate to the policy.
- Replace Silken's private provider/count implementation with the same policy.
- Add actual Night-only, duplicate-Night, Silken-only, and mixed-set tests.

Acceptance criteria:

- Night-only Intent grants 10% Lunar-Charged, Lunar-Bloom, and
  Lunar-Crystallize DMG to the wearer and allies.
- Exact Intent expiry removes the Night-only bonus.
- Duplicate Night sets expose one synergy buff and still grant only 10%.
- Mixed Night plus Silken exposes one Silken-sourced synergy buff, grants 20%
  with both distinct effects, and falls back to 10% as either expires.
- Existing Silken-only, duplicate-Silken, Devotion EM, and unrelated stats are
  unchanged.

Test cases to add or update:

- Normal: actual on-field Night Lunar reaction creates Intent and a team-wide
  10% bonus for all Lunar types.
- Mixed: Intent plus Devotion gives 20%, with the first Silken wearer retained
  as the source.
- Boundary: exact four/eight-second expiries exclude the corresponding effect.
- Abnormal: no active effect gives zero; duplicate Night/Silken providers and
  duplicate status IDs cannot stack beyond distinct-effect count.
- No-change: Night CRIT Rate and Silken team EM values remain exact.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/artifact/GleamingMoonSynergy.java --path src/java/model/artifact/NightOfTheSkysUnveiling.java --path src/java/model/artifact/SilkenMoonsSerenade.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- `GleamingMoonSynergy` now exclusively owns provider election, distinct active
  effect counting, and construction of the three typed dynamic bonuses. Night
  and Silken each depend only on the existing generic provider capability and
  delegate their result, while all trigger behavior remains in its owning set.
- An actual on-field Night Lunar-Charged reaction proves 15% Nascent CRIT Rate,
  one Intent status, and team-wide 10% Lunar-Charged, Lunar-Bloom, and
  Lunar-Crystallize bonuses. The dynamic bonus is zero before activation and at
  exact Intent expiry.
- Two Night wearers expose one first-Night-sourced synergy and one 10% distinct
  Intent bonus. Existing mixed Night/Silken checks expose one Silken-sourced
  synergy, reach 20% with Intent plus Devotion, and fall to 10%/0% at exact
  effect expiries. Duplicate IDs, duplicate Silken sets, team EM, and artifact
  fixed stats remain covered.
- ReactionRegressionTest, build, Javadoc, routed validation, and preflight all
  pass without an artifact leak.

### Phase 3: Re-Accept Artifact-Provider Party Baselines - Done

Why last:

The catalog Flins parties already equip Night and Silken together, making exact
payload equality the strongest guard that provider extraction changed no
runtime order, attribution, optimizer input, or report data.

Target files:

- `README.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh payloads each for RaidenParty, FlinsParty, and FlinsParty2.
- Compare normalized hashes, totals, duration, ER, optimizer rolls, action and
  reaction cadence, warnings, and artifact-source attribution.
- Update current references only if behavior legitimately changes; otherwise
  record exact equality and close B-054.

Acceptance criteria:

- Each repeated pair matches exactly without warning or energy failure.
- All simulation payload content matches B-053 because each affected catalog
  party already had a canonical Silken provider. JVM-dependent unordered
  optimizer-map presentation is excluded from the semantic payload hash.
- README, verification reference, plan, and ledger agree; generated HTML and
  output artifacts are not staged.

Test cases to add or update:

- Normal integration: all three parties complete deterministically with their
  accepted totals and hashes.
- Abnormal integration: no duplicate synergy, source-attribution, warning, ER,
  optimizer, report, or untracked-artifact regression appears.

Verification:

- two fresh `./gradlew RaidenParty` runs
- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Each party's two semantic payloads match exactly and also match B-053 after
  excluding Gradle's elapsed-time line and unordered optimizer `Result` map
  presentation: RaidenParty
  `dae58b38c4c64fba719885cfdf1facb0ac229187a712ab9a41ace16bd2dceed2`,
  FlinsParty
  `5d4cd7f4577704c541438bfa4b00525071d9b3183a77a2243fd0b47944d1dd18`,
  and FlinsParty2
  `d8732dfb6f34dcd80d910553525e622178eca55da249895562ac4570158337d5`.
- RaidenParty retains 1,363,709 / 64,939 over 21.0 seconds and
  100/175/179/174% ER. FlinsParty retains 22,675,823 / 227,898 over 99.5
  seconds and 109/100/100/180% ER. FlinsParty2 retains 15,817,125 / 228,902
  over 69.1 seconds and 130/128/100/196% ER; every optimizer allocation value
  is unchanged.
- FlinsParty and FlinsParty2 retain exactly 613/468 timed actions, 230/140
  reaction logs, 48/33 delayed Lunar ticks, 172/105 immediate Lunar-Charged
  reactions, and 88/71 ICD blocks. All six runs contain zero warning, error,
  failed-action, or insufficient-energy matches.
- Raw Flins hashes differ from B-053 only because Java's unordered optimizer
  map prints identical entries in a different order; the two fresh runs agree
  with each other. This pre-existing baseline weakness is not a simulation
  delta and is retained as separate follow-up work.
- README and the verification reference require no value update. The tracked
  FlinsParty2 HTML report was restored to HEAD and no generated output is staged.

## Implementation Order: Deterministic Optimizer Result Rendering

Status:

- Complete; all three phases are implemented and accepted.
- Requirement: identical joint optimizer allocations must render in one stable
  stat order across fresh JVM processes so full sample payload hashes remain
  valid regression evidence.

Scope:

- Preserve the caller's typed `statsToOptimize` order in each hill-climber result
  map and its console representation.
- Preserve optimizer candidate enumeration, comparisons, caps, tie behavior,
  returned values, DPS, ER, and final allocations.
- Add a focused executable output/returned-order regression.
- Re-run affected Flins samples in independent JVMs and retain Raiden as a
  no-change control.

Out of scope for this pass:

- Changing optimizer strategy, performance, budgets, stat priorities, tie
  breaking, ER convergence, artifact values, or party definitions.
- General canonical serialization of every map in the application, report
  rendering, dependencies, build structure, and generated output.
- Reaction/mechanic behavior, RL contracts/training, and HPC jobs.

Definitions:

- Stable result order: insertion order exactly matching the caller-provided
  `statsToOptimize` list throughout initialization, hill-climbing mutation,
  return, and `Result:` logging.
- Independent JVM evidence: sample invocations with Gradle daemon reuse disabled,
  normalized only by removing Gradle's elapsed-time line.

### Phase 1: Record Unordered Result Reproduction - Done

Why first:

The allocation values are already deterministic; the plan must prevent a local
map replacement from being mistaken for an optimizer algorithm change.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record the B-053/B-054 payload difference and isolate every changed line.
- Define caller stat order as the rendering contract and exclude all search
  behavior changes.
- Define focused and fresh-process verification before changing production code.

Acceptance criteria:

- The observed diff contains only `Result:` key permutations with identical
  keys, values, and DPS.
- One collection choice can preserve order without changing lookup or mutation
  semantics.
- Tests prove both returned key order and exact rendered order.

Test cases to add or update:

- No production test in this phase; Phase 2 adds a three-stat equal-DPS fixture.

Verification:

- compare B-053 and B-054 RaidenParty, FlinsParty, and FlinsParty2 payloads
- inspect `IterativeSimulator.optimizeSubstatsNDim`
- `python scripts/preflight.py --run`

Completion evidence:

- B-053/B-054 semantic payloads match for all three parties. The complete
  affected diffs are six FlinsParty and five FlinsParty2 `Result:` lines whose
  `HashMap` keys are permuted; values and adjacent DPS are identical. Raiden has
  no differing line.
- `optimizeSubstatsNDim` initializes keys from the ordered
  `statsToOptimize` list, then mutates only existing entries. An insertion-
  ordered map therefore fixes return/render order without altering enumeration,
  lookup, candidate evaluation, or tie behavior.
- The focused test will independently inspect returned key order and captured
  output. Documentation preflight passes without checks or artifact leaks.

### Phase 2: Preserve Typed Optimization Order - Done

Why second:

The implementation is a collection-level correction only after its output and
return contracts are explicit.

Target files:

- `src/java/mechanics/optimization/IterativeSimulator.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Initialize per-character hill-climber results with insertion-order semantics.
- Add a zero-damage fixture that leaves the balanced allocation unchanged and
  captures its exact three-stat result line.
- Assert the returned key order independently from console string formatting.

Acceptance criteria:

- `[CRIT_RATE, CRIT_DMG, ATK_PERCENT]` returns and renders in that exact order.
- Equal-DPS candidate evaluation terminates with the existing balanced values.
- Existing optimizer feasibility, reaction, build, and Javadoc gates pass.

Test cases to add or update:

- Normal: six rolls over three stats return two each in requested order.
- Tie: every candidate remains at zero DPS and preserves current no-improvement
  behavior.
- Output: captured line is exactly ordered and includes unchanged rounded DPS.
- No-change: unreachable ER still throws its typed feasibility error.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/mechanics/optimization/IterativeSimulator.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- The hill-climber now initializes `currentRolls` as a `LinkedHashMap`; all
  reads, swaps, caps, comparisons, and iteration over the separate typed stat
  list remain unchanged.
- A zero-damage three-stat fixture proves the equal-DPS path keeps two rolls per
  stat, returns keys as CRIT_RATE/CRIT_DMG/ATK_PERCENT, and renders exactly
  `{CRIT_RATE=2, CRIT_DMG=2, ATK_PERCENT=2} => DPS: 0`.
- Existing unreachable/exact/manual ER feasibility coverage remains green.
  ReactionRegressionTest, build, Javadoc, routed build/RaidenParty/FlinsParty2,
  and preflight pass; generated tracked HTML was restored afterward.

### Phase 3: Accept Fresh-JVM Payload Determinism - Done

Why last:

Same-process repetition cannot settle the reproduced identity-hash ordering
failure; acceptance requires fresh JVM boundaries after the focused test passes.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two no-daemon payloads each for FlinsParty and FlinsParty2.
- Run one RaidenParty control and compare all values against B-054.
- Compare raw normalized hashes, allocations, totals, ER, cadence, warnings,
  and tracked/untracked output state.

Acceptance criteria:

- Each affected no-daemon pair has one raw normalized hash without excluding
  optimizer result lines.
- The ordered result lines follow each party's requested stat lists and all
  allocation values remain unchanged.
- Totals, DPS, durations, ER, event cadence, and warnings match B-054.
- The tracked report is restored and no generated artifact is staged.

Test cases to add or update:

- Normal integration: fresh JVM Flins samples are byte-stable after elapsed-time
  normalization.
- No-change integration: Raiden values and every simulator event count remain
  unchanged.
- Abnormal integration: no warning, failed action, insufficient energy, report,
  or artifact leak appears.

Verification:

- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- one fresh `./gradlew --no-daemon RaidenParty` run
- `python scripts/preflight.py --run`

Completion evidence:

- Two independent no-daemon FlinsParty payloads match raw normalized SHA-256
  `6338bcc75a29a52f3245cb4573823ba1245724d3be60064dcebefa7b38aa03ab`;
  two FlinsParty2 payloads match
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
  Normalization removes only Gradle's elapsed-time line and retains every
  optimizer `Result:` line.
- FlinsParty result keys now consistently follow
  CRIT_RATE/CRIT_DMG/ATK_PERCENT or CRIT_RATE/CRIT_DMG/HP_PERCENT; FlinsParty2
  follows the same caller orders. Every allocation value and intermediate DPS
  matches B-054.
- FlinsParty retains 22,675,823 / 227,898, 109/100/100/180% ER, and
  613/230/48/172/88 timed/reaction/delayed/immediate/ICD counts. FlinsParty2
  retains 15,817,125 / 228,902, 130/128/100/196% ER, and
  468/140/33/105/71 counts. The fresh Raiden control retains 1,363,709 / 64,939,
  100/175/179/174% ER, and 152/57/4/38 timed/reaction/DoT/ICD counts.
- All five runs contain zero warning, error, failed-action, or
  insufficient-energy matches. The tracked HTML report was restored and no
  generated output is staged.

## Implementation Order: Electro-Charged Premature Expiry Ticks

Status:

- Complete; all four phases are implemented and accepted.
- Requirement: standard Electro-Charged must wake at the first coexisting Aura's
  natural expiry, deal a premature tick only when more than 0.5 seconds elapsed
  since the previous EC damage tick, and retain its ordinary one-second cadence.

Scope:

- Expose the finite/infinite natural expiry of one typed enemy Aura as read-only
  state.
- Schedule standard EC at the earlier of its next nominal tick or either Aura's
  natural expiry.
- Suppress terminal damage when the first Aura expires within 0.5 seconds of the
  previous EC damage instance.
- Re-evaluate Aura state when an earlier expiry event wakes so intervening
  same-element extension cancels obsolete premature timing.
- Preserve 0.4U dual consumption only on damage ticks and re-accept deterministic
  party baselines.

Out of scope for this pass:

- EC ownership/EM refresh, EC damage ICD or multi-target arcs, hitlag effects,
  simultaneous-event priority, innate/self Aura, or server-frame delay.
- Lunar-Charged/Thundercloud cadence, duration, weighted damage, notifications,
  and Aura policy.
- Other reaction families, damage formulas, optimizer behavior, reports, RL
  contracts/training, HPC jobs, dependencies, and generated output.

Definitions:

- Nominal tick: standard EC damage exactly one second after the prior EC damage
  instance.
- Premature tick: standard EC damage at the first coexisting Aura's natural
  expiry when that expiry is more than 0.5 seconds after the prior EC damage.
- Suppressed terminal expiry: Aura expiry at most 0.5 seconds after the prior EC
  damage, which ends the timer without another damage or 0.4U consumption.

### Phase 1: Record EC Expiry Evidence and Event Policy - Done

Why first:

The current integer-second scheduler is correct for nominal ticks; evidence and
strict exclusions must distinguish the missing wake time from ownership, ICD,
and Lunar mechanics.

Target files:

- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Record maintained EGT and Evidence Vault timing/consumption rules.
- Inventory scheduler, Aura state, clock, snapshot, resistance, and Lunar paths.
- Define finite, infinite, early, suppressed, extended, and no-change tests.

Acceptance criteria:

- Maintained evidence independently supports ordinary, premature, suppressed,
  and 0.4U consumption behavior.
- `Enemy` remains the sole owner of expiry math; the scheduler only reads it.
- Standard and Lunar event policies remain explicitly separate.

Test cases to add or update:

- No production test in this phase; Phase 2 covers expiry queries and Phase 3
  covers event behavior.

Verification:

- inspect `Enemy`, `ReactionEffectScheduler`, `SimulationClock`, EC regressions,
  snapshots, and B-049 source application
- `python scripts/preflight.py --run`

Completion evidence:

- Maintained KQM EGT specifies ordinary one-second EC ticks, a premature tick
  when either Aura completely decays before the next interval except within 0.5
  seconds of the prior tick, and 0.4U consumption from both gauges per damage
  tick. The Evidence Vault independently records 0.8-second early damage and
  0.4-second no-damage examples. Sources accessed 2026-08-02 are recorded in
  B-056.
- `Enemy.AuraState` already owns exact finite/infinite expiry and rebases it
  after discrete consumption. The scheduler currently cannot read that value
  and always adds one/two seconds; `SimulationClock` can safely requeue a
  `TimerEvent` at a mutable absolute time and expires Auras before dispatch.
- Standard EC may therefore use the read-only expiry while Lunar retains its
  existing fixed two-second path. Snapshot restore clears all timer events as a
  general clock contract; changing timer snapshot architecture is excluded.
- The documentation preflight passes without checks or artifact leaks.

### Phase 2: Expose Typed Aura Expiry - Done

Why second:

The scheduler cannot choose a premature wake time without a stable read-only API,
and it must not duplicate private decay arithmetic.

Target files:

- `src/java/model/entity/Enemy.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add a typed current-time-aware natural expiry query to `Enemy`.
- Define absent/expired Aura as no future expiry and preserve positive finite and
  compatibility infinite states.
- Cover source application, discrete rebase, clear, natural expiry, and snapshot
  restore.

Acceptance criteria:

- A fresh 1U source reports 9.5 seconds and retains that expiry until consumed.
- A 0.4U consumption at one second rebases expiry from current units at the
  original decay rate.
- Infinite fixture Aura reports positive infinity; absent/expired Aura reports
  no future expiry without exposing mutable state.
- Snapshot restore recovers the exact queried expiry.

Test cases to add or update:

- Normal: finite source before and after consumption.
- Boundary: exact natural expiry and explicit clear return no future expiry.
- Compatibility: non-decaying fixture returns infinity.
- Snapshot: mutate then restore recovers original expiry.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/entity/Enemy.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- `Enemy.getAuraExpiryTime` exposes only an absolute typed deadline and keeps
  `AuraState` fields private. A fresh taxed 1U source reports 9.5 seconds; after
  one second of decay and 0.4U consumption it reports 4.75 seconds at the
  unchanged source decay rate.
- Exact expiry is excluded by the stored absolute deadline before floating-point
  residual gauge is considered. Non-decaying and absent Auras report positive
  infinity, allowing callers to treat both as no finite wake deadline.
- Snapshot mutation/restore recovers the original 9.5-second expiry exactly.
  ReactionRegressionTest, build, Javadoc, routed validation, and preflight pass.

### Phase 3: Schedule Standard EC at Aura Expiry - Done

Why third:

Event policy can depend on the tested expiry API while keeping damage recording,
live resistance, and Aura consumption in the existing scheduler owner.

Target files:

- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Compute each next standard EC wake from nominal and current Aura expiries.
- Distinguish nominal damage, eligible premature damage, suppressed expiry, and
  stale expiry after Aura extension.
- Retain existing live-resistance and 0.4U consumption paths for every damage
  tick; leave Lunar event code behaviorally unchanged.

Acceptance criteria:

- Normal EC still damages at one-second intervals and consumes 0.4U from both.
- A first Aura expiring between 0.5 and 1.0 seconds after the previous tick deals
  one early terminal tick at exact expiry.
- An Aura expiring below 0.5 seconds ends EC with no extra damage or consumption.
- Extending the threatened Aura before its old expiry cancels that early tick and
  retains the nominal schedule.
- Lunar-Charged still first ticks at two seconds independent of standard Aura
  expiry, and existing impact-time resistance checks remain exact.

Test cases to add or update:

- Normal: strong dual Aura nominal tick and 0.4U consumption.
- Premature: finite 0.5U fixture produces a tick between one and two seconds.
- Suppressed: finite 0.48U fixture ends below the half-second threshold.
- Extension: stronger same-element application before the obsolete expiry
  reschedules without damage.
- No-change: infinite Aura, live RES activation/expiry, and Lunar two-second
  cadence.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/mechanics/reaction/ReactionEffectScheduler.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Standard and Lunar timers now have separate event implementations. Standard
  EC chooses the earliest nominal/Hydro/Electro deadline, while Lunar retains
  its fixed two-second/Thundercloud-end policy and typed notifications.
- A 0.5U finite Hydro fixture ticks normally at 1.0 seconds, then exactly at its
  1.7-second expiry and consumes another 0.4U from residual Electro. A 0.48U
  fixture expires 0.4 seconds after the nominal tick with no damage or second
  consumption and clears the running flag.
- Applying stronger Hydro before the obsolete 1.7-second wake produces no early
  damage and reschedules the next hit to the original 2.0-second nominal time.
  A Lunar fixture remains silent through 1.999 seconds and first ticks at 2.0.
- Existing nominal 0.4U dual consumption and impact-time RES activation/expiry
  tests remain exact. ReactionRegressionTest, build, Javadoc, routed validation,
  and preflight all pass.

### Phase 4: Re-Accept EC-Sensitive Party Baselines - Done

Why last:

RaidenParty contains standard EC while both Flins parties exercise the excluded
Lunar event path, so all three are required to attribute changes and prove the
boundary.

Target files:

- `README.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`
- `TASKS.md`
- `BACKLOG.md`

Tasks:

- Run two fresh payloads each for RaidenParty, FlinsParty, and FlinsParty2.
- Compare hashes, totals, ER, allocations, reaction/tick/action cadence, source
  attribution, and warnings against B-055.
- Update accepted references only for traced standard-EC terminal differences.

Acceptance criteria:

- Every repeated pair matches without warning or energy failure.
- Any Raiden delta is exactly explained by premature/suppressed standard EC
  events; both Lunar parties remain byte-stable.
- README, verification reference, plan, and ledger agree; tracked HTML is
  restored and no generated output is staged.

## Implementation Order: Burning Fuel and Refresh State

Objective: replace the fixed two-second Burning approximation with one typed,
single-target Burning state whose lifetime follows the consumed Dendro fuel and
whose owner/damage payload follows the latest Pyro or Dendro application.

Scope:

- Source-direction setup for Pyro-on-Dendro and Dendro-on-Pyro.
- Burning fuel units and special decay at
  `max(0.4 U/s, 2 * natural Dendro decay rate)`.
- Dendro refresh overwrite and Pyro refresh ownership semantics.
- Existing 0.25-second damage cadence, live impact resistance, logs, and
  snapshot payloads.
- Focused regression and deterministic catalog-party controls.

Out of scope:

- A separately reactable 2U Burning Aura and its consumption by
  Hydro/Cryo/Electro/Anemo/Geo.
- Burning's separate 1U Pyro application with two-second ICD.
- Quicken as Burning fuel, AoE spread/self-damage, target switching, and hitlag.
- RL learner/service changes, persistent jobs, and multi-target architecture.

Cross-cutting rules:

- `ReactionState` owns mutable Burning state; the resolver selects source
  direction; the scheduler owns time and damage policy; `Enemy` owns Aura math.
- Keep typed elements and `CharacterId`; do not dispatch on display strings.
- Use one state transition per refresh and one timer event per active Burning
  generation; stale events must terminate without damage.
- Preserve current-time Aura reads, live resistance at tick impact, explicit
  staging, and the no-generated-artifact boundary.
- Follow repository Java notation/Javadoc rules and avoid unrelated refactors.

### Phase 1: Record Burning Evidence and Runtime Contract - Done

Why first:

The current fixed window conflates Burning Aura, Dendro fuel, and Pyro
application. The replacement requires an explicit source-backed boundary before
state or event code changes.

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record maintained tick, fuel, decay, overwrite, and ownership evidence.
- Inventory resolver, state, snapshot, timer, and regression ownership.
- Define the single-target exclusions that require a future dedicated Burning
  Aura model.

Acceptance criteria:

- The plan distinguishes damage cadence, Burning fuel, Burning Aura, and Pyro
  application rather than representing all four with one expiry timestamp.
- Every implementation phase names target files, normal/abnormal tests, and
  verification commands.
- No behavior changes or generated outputs occur in this phase.

Test cases to add or update:

- No production test in this phase; Phases 2 and 3 own state and event tests.

Verification:

- inspect `CombatActionResolver`, `ReactionEffectScheduler`, `ReactionState`,
  `SimulatorSnapshot`, `Enemy`, and all Burning regressions
- `python scripts/preflight.py --run`

Completion evidence:

- Maintained KQM separates 0.25-second Burning damage from the 1U Pyro
  application's two-second ICD. The maintained gauge reference specifies a
  distinct Dendro fuel that replaces natural decay with at least 0.4U/s and
  overwrites on Dendro reapplication; gcsim independently implements the same
  fuel boundary and latest-applier damage ownership. Sources are recorded in
  B-058.
- The runtime inventory confirms that `CombatActionResolver` currently consumes
  the opposite Aura immediately, `ReactionEffectScheduler` captures stale owner
  and damage values, and `ReactionState`/`SimulatorSnapshot` retain only a fixed
  end timestamp. The planned ownership boundaries remove those three causes
  without adding multi-target or RL scope.
- Burning Aura consumption, its separate Pyro application, Quicken fuel, AoE,
  and hitlag remain explicit exclusions rather than inferred behavior.
- The documentation preflight passes with no routed checks or artifact leaks.

### Phase 2: Add Typed Burning Fuel State and Snapshot Payload - Done

Why second:

The event must read refreshable owner, damage, fuel, decay, and generation data
from one simulator-owned state rather than capture stale values in its closure.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/model/entity/Enemy.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Replace the Burning end-only payload with typed fuel units, decay rate,
  owner, pre-resistance damage, generation, and last-update time.
- Expose the current natural Aura decay rate without leaking mutable Aura state.
- Save and restore the complete Burning payload under the existing contract
  that pending timer events are intentionally cleared.

Acceptance criteria:

- Active state computes a finite end from remaining fuel and decay rate; clear
  state is inactive and cannot expose a stale owner or damage value.
- Natural Aura decay-rate queries return exact finite rates, zero for
  non-decaying fixtures, and zero for absent/expired Auras.
- Snapshot mutation/restore recovers the full Burning payload exactly while the
  event queue remains empty by design.

Test cases to add or update:

- Normal: typed 0.8U fuel at 0.4U/s reports a two-second end.
- Boundary: zero/negative/non-finite fuel or decay clears/rejects state.
- Refresh: owner, damage, generation, and timing update atomically.
- Aura: finite, infinite, absent, and exact-expiry decay-rate queries.
- Snapshot: mutate/clear then restore every Burning field.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/ReactionState.java --path src/java/simulation/SimulatorSnapshot.java --path src/java/model/entity/Enemy.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- `ReactionState.BurningState` is an immutable typed payload for owner,
  pre-resistance damage, fuel, special decay rate, last update, and event
  generation. Start, fuel replacement, owner refresh, continuous rebase, clear,
  and restore transitions preserve one ownership boundary.
- `Enemy.getAuraDecayRate` returns the private source-class rate without
  exposing mutable Aura state. Regression covers a finite 1U Dendro source,
  exact expiry, a non-decaying fixture, and absence.
- A 0.8U/0.4U-per-second payload reports an exact two-second end. Owner refresh
  after 0.5 seconds retains 0.6U and the same end/generation; 0.4U replacement
  derives a 1.5-second end. Snapshot restore recovers every field and timer flag,
  while invalid input clears stale state.
- `CapabilityProfiler` only forwards the added immutable fields when rebuilding
  its existing snapshot. No learner, service, protocol, or training behavior
  changed.
- ReactionRegressionTest, build, Javadoc, routed party-catalog validation, the
  local Java rollout smoke benchmark, and preflight pass without artifact leaks.

### Phase 3: Consume and Refresh Burning Fuel - Done

Why third:

The resolver and scheduler can now depend on one tested state contract and keep
source selection separate from timer policy.

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/model/entity/Enemy.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Preserve existing Dendro for Pyro-on-Dendro; establish taxed Dendro fuel for
  Dendro-on-Pyro instead of consuming the existing Pyro Aura.
- Replace active Burning fuel when Dendro reapplies, even when the new source is
  weaker; leave fuel unchanged for Pyro refreshes.
- At each 0.25-second tick, advance fuel and underlying Dendro at the special
  rate, read the latest owner/damage, apply live resistance, and stop exactly at
  depletion. Superseded event generations terminate silently.

Acceptance criteria:

- A 1U Dendro source supplies taxed 0.8U fuel and exactly eight 0.25-second
  damage ticks over two seconds; 2U supplies 1.6U and sixteen ticks over four.
- Neither trigger direction immediately consumes its opposite underlying Aura.
- A weaker Dendro refresh overwrites fuel and owner/damage; a Pyro refresh
  updates owner/damage without replacing fuel.
- Exact depletion clears timer/state and Dendro without a ninth/late tick;
  stale timer generations do no damage.
- Resistance is evaluated at every impact and existing non-Burning reaction
  behavior remains unchanged.

Test cases to add or update:

- Normal: 1U and 2U source direction fixtures with exact fuel/tick counts.
- Direction: Pyro-on-Dendro preserves fuel; Dendro-on-Pyro preserves Pyro and
  establishes taxed Dendro.
- Refresh: weaker Dendro overwrite and Pyro owner-only refresh.
- Boundary: exact depletion, invalid/absent fuel, and superseded event.
- Dynamic: resistance activation/expiry across ticks and no immediate damage.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/mechanics/reaction/ReactionEffectScheduler.java --path src/java/model/entity/Enemy.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Pyro-on-Dendro now preserves the existing taxed Dendro as fuel.
  Dendro-on-Pyro preserves underlying Pyro and force-replaces Dendro from the
  trigger source, allowing weaker refreshes to overwrite stronger old fuel.
- The scheduler derives special decay as the maximum of 0.4U/s and twice the
  Aura's private natural rate. Its single event reads immutable current state at
  each impact, synchronizes underlying Dendro to fuel, applies live Pyro RES,
  and clears both state and Aura at exact depletion.
- Focused regression proves no immediate damage, the first 0.25-second boundary,
  eight ticks over two seconds for 1U Dendro, sixteen over four seconds for 2U,
  no late tick, and both trigger directions' underlying Aura preservation.
- A Pyro refresh changes the next tick from Sucrose's 1000 pre-RES payload to
  Xiangling's 2000 payload without extending fuel. A weaker 0.5U Dendro source
  overwrites fuel to taxed 0.4U and ends one second later under the latest owner.
  Clearing/restarting before the first tick leaves two queued generations but
  records exactly one 900-damage tick.
- Existing impact-time Burning RES activation/expiry remains exact.
  ReactionRegressionTest, build, Javadoc, routed validation, and preflight pass.

### Phase 4: Re-Accept Burning-Neutral Party Baselines - Done

Why last:

The three catalog parties do not intentionally use Burning, so repeated exact
payloads provide strong cross-system no-change controls after runtime-state and
snapshot edits.

Target files:

- `README.md`
- `.agents/skills/verify-genshin-changes/references/verification-gate.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two fresh payloads each for RaidenParty, FlinsParty, and FlinsParty2.
- Compare hashes, totals, ER, optimizer allocation, cadence, warnings, and
  generated-report status against B-056.
- Narrow README's Dendro-special difference to the explicitly excluded Burning
  Aura/Pyro-application and Quicken interactions.

Acceptance criteria:

- Every repeated pair is deterministic and retains its accepted B-056 values.
- No Burning events occur in any control party, and no warning, energy failure,
  duplicate timer, or generated artifact leaks into the commit.
- README, verification reference, plan, ledger, and session checkpoint agree.

Test cases to add or update:

- Normal integration: all three parties complete with exact repeated payloads.
- No-change integration: accepted totals, ER, allocations, and cadence remain.
- Abnormal integration: no warning/error/failed-action/generated-file leakage.

Verification:

- two fresh `./gradlew RaidenParty` runs
- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh no-daemon runs of every catalog party match pairwise with zero
  Burning lines and zero warning/error/failed-action/insufficient-energy lines.
  Accepted totals remain RaidenParty 1,365,787 / 65,037, FlinsParty 22,675,823 /
  227,898, and FlinsParty2 15,817,125 / 228,902.
- ER remains 100/175/179/174% for Raiden, 109/100/100/180% for Flins, and
  130/128/100/196% for Flins2. Timed/reaction/delayed/ICD counts remain
  152/55/11/38, 613/230/48/88, and 468/140/33/71 respectively.
- Pair hashes after removing only Gradle's elapsed-success line are Raiden
  `e52e586cca64148195ad8dc9ab9f0827922a7f01f931faedb0a6ecbab7100dda`,
  Flins `6338bcc75a29a52f3245cb4573823ba1245724d3be60064dcebefa7b38aa03ab`,
  and Flins2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
  The latter two exactly match the existing B-055 no-daemon references.
- README now records the implemented Burning fuel contract and narrows remaining
  differences to the separate Burning Aura/Pyro application, Quicken fuel, and
  other already listed systems. The verification gate includes all three
  accepted totals.
- The generated tracked HTML report was restored to HEAD; no generated output
  is staged.

### B-056 Phase 4 Acceptance Appendix

The following test matrix and completion evidence belongs to the preceding
Electro-Charged plan and is retained here because B-058 was appended before the
existing acceptance appendix.

Test cases to add or update:

- Normal integration: all three parties complete deterministically.
- No-change integration: both Lunar parties retain exact B-055 payloads.
- Abnormal integration: no duplicate timer, event-loop, warning, report, ER,
  optimizer, or artifact leak occurs.

Verification:

- two fresh `./gradlew RaidenParty` runs
- two fresh `./gradlew FlinsParty` runs
- two fresh `./gradlew FlinsParty2` runs
- `python scripts/validate_agent_assets.py`
- `python scripts/preflight.py --run`

Completion evidence:

- Each pair's normalized payload matches exactly: RaidenParty
  `d5dd65169069937a30bf8b8be0c32765dc26309e6132b58f29e9b28bb3cde7c3`,
  unchanged FlinsParty
  `8271526ca511bcb8c49f2a3d15fc22114c2044124ed7bf2f61a2255fc9a45d67`,
  and unchanged FlinsParty2
  `b28a4a831f4e91ea687ac6c0f3df542fc06364e48514f1e3ef7460111257b27d`.
  All six runs contain zero warning, error, failed-action, or
  insufficient-energy matches.
- RaidenParty accepts 1,365,787 / 65,037 over 21.0 seconds, +2,078 from B-055.
  Standard delayed EC ticks increase from four to eleven and Thundercloud gains
  23,899; additional 0.4U consumption removes one later immediate EC and one
  Vaporize, lowering Raiden by 3,415 and Xiangling by 18,407. Bennett, Xingqiu,
  Overload count, 152 timed actions, 38 ICD blocks, every optimizer roll, and
  100/175/179/174% ER are unchanged.
- FlinsParty retains 22,675,823 / 227,898, 109/100/100/180% ER, and exact
  613/230/48/172/88 timed/reaction/delayed/immediate/ICD counts. FlinsParty2
  retains 15,817,125 / 228,902, 130/128/100/196% ER, and exact
  468/140/33/105/71 counts, proving the Lunar event path is byte-stable.
- README and the verification reference now carry the accepted Raiden value and
  narrow the remaining EC simplifications to ownership, damage ICD, and hitlag.
  The tracked FlinsParty2 HTML report was restored and no generated output is
  staged.

## Implementation Order: Finite Typed Freeze Aura

Objective: replace the permanent scalar Freeze flag with a typed target Aura
whose gauge accelerates to natural expiry, extends without resetting active
decay, and survives simulator snapshot/restore.

Scope:

- Initial Frozen gauge and accelerating current-time-aware decay.
- Active refreeze extension and inactive decay-rate recovery.
- Time-aware Shatter checks/clear and Cryo resonance checks.
- Full simulator snapshot payload and catalog-party no-change controls.

Out of scope:

- Dual underlying Hydro/Cryo reaction priority and trigger residual attachment.
- Freeze resistance, hitlag, poise damage, innate Aura, and enemy-specific rules.
- Shatter damage ICD/gauge by attack, multi-target reactions, RL behavior, and
  persistent jobs.

Cross-cutting rules:

- `Enemy` owns immutable Freeze Aura state because it is target Aura data;
  `CombatActionResolver` selects Freeze and Shatter transitions only.
- Use current-time-aware APIs for production decisions. Keep no-time wrappers
  solely for source compatibility and migrate all known runtime consumers.
- Initial gauge is `2 * min(current origin, trigger source)`; active refreeze
  adds gauge and preserves instantaneous decay rate.
- Active decay uses `units - rate * elapsed - 0.05 * elapsed^2`, with rate
  increasing by 0.1U/s^2. After depletion or Shatter, rate recovers by 0.2U/s^2
  toward 0.4U/s and is retained for a later Freeze.
- Preserve ordinary Aura state and snapshot contracts; explicit staging and
  generated-artifact safety remain mandatory.

### Phase 1: Record Freeze Gauge Evidence and State Math - Done

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record maintained initial-gauge, duration, coexistence, and extension evidence.
- Confirm the independent accelerating-decay and thaw-recovery implementation.
- Inventory scalar Freeze, resolver, Shatter, resonance, and snapshot callers.

Acceptance criteria:

- Initial, active-decay, extension, depletion, and inactive-recovery equations
  are explicit and dimensionally consistent.
- Unresolved coexistence/priority behavior is excluded rather than inferred.
- No production behavior changes in this phase.

Test cases to add or update:

- No production test; Phases 2 and 3 own typed state and resolver behavior.

Verification:

- inspect `Enemy`, resolver, Shatter, resonance, snapshot, and regression paths
- `python scripts/preflight.py --run`

Completion evidence:

- KQM's duration equation rearranges exactly to
  `F0 = 0.4 * duration + 0.05 * duration^2`, establishing the continuous decay
  function and exact positive-root expiry used by the plan.
- KQM confirms hidden Hydro/Cryo continues ordinary decay and refreeze extends
  Frozen Aura. gcsim independently uses twice the smaller gauge, preserves
  active decay rate on extension, accelerates by 0.1U/s^2, and thaws at
  0.2U/s^2 toward the 0.4U/s floor.
- Inventory confirms Freeze is a permanent scalar in `Enemy`; Shatter and Cryo
  resonance use no-time checks, and `SimulatorSnapshot` captures ordinary Auras
  but omits Freeze. The exclusions isolate the finite lifecycle from unresolved
  dual-Aura reaction order.

### Phase 2: Add Typed Freeze State and Snapshot Continuity - Done

Target files:

- `src/java/model/entity/Enemy.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/mechanics/rl/CapabilityProfiler.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add immutable units/rate/update Freeze payload with remaining, rate, and exact
  end queries across active decay and inactive recovery.
- Add initial/apply, extension, reduction, clear, current-time query, capture,
  and restore APIs while retaining compatibility wrappers.
- Carry the typed payload through simulator snapshots; the RL profiler change is
  constructor forwarding only and must not alter RL behavior.

Acceptance criteria:

- 1U source interaction creates 1.6U Frozen gauge and expires at
  `2 * sqrt(12) - 4` seconds with nonlinear midpoint gauge.
- Extension adds to current gauge without resetting instantaneous active rate.
- Shatter clear retains thawing rate; later application uses recovered rate,
  bounded below by 0.4U/s.
- Snapshot restore recovers gauge, rate, update time, and exact future expiry.
- Invalid/non-positive updates cannot create active Freeze.

Test cases to add or update:

- Normal: 1.6U initial gauge, midpoint residual/rate, exact expiry.
- Extension: active add preserves rate and lengthens exact end.
- Recovery: clear, partial thaw, full floor recovery, then reapply.
- Consumption: partial, exact/over clear, invalid amount.
- Snapshot: mutate/clear then exact payload restore.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/model/entity/Enemy.java --path src/java/simulation/SimulatorSnapshot.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- `Enemy.FreezeAuraState` stores immutable gauge, instantaneous rate, and update
  time. Its analytic remaining/rate/end queries implement active 0.1U/s^2
  acceleration and inactive 0.2U/s^2 recovery to the 0.4U/s floor.
- Typed replace, additive apply, partial reduction, clear, capture, and restore
  operations keep transition arithmetic inside `Enemy`; compatibility wrappers
  remain for the two fixture callers pending Phase 3 runtime migration.
- Regression proves 1.6U from equal 1U sources, exact
  `2 * sqrt(12) - 4` expiry, nonlinear midpoint gauge/rate, active extension
  without rate reset, partial/full recovery, partial/exact consumption, invalid
  input stability, and exact snapshot restoration.
- `SimulatorSnapshot` now carries the immutable Freeze payload. The profiler
  edit only forwards the existing active snapshot value and does not change RL
  observations, protocol, actions, or training.
- `ReactionRegressionTest`, `build`, `javadoc`, routed build, party catalog, the
  preflight-selected short Java rollout benchmark, and full preflight pass. No
  persistent service, training process, or HPC job was started.

### Phase 3: Route Freeze and Shatter Through Current Time - Done

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/element/ResonanceManager.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Create/extend Freeze from exact current origin and trigger gauges.
- Make Shatter eligibility/clear and Cryo resonance query current Freeze state.
- Preserve underlying ordinary Aura decay and the existing reaction order.

Acceptance criteria:

- Both Hydro-on-Cryo and Cryo-on-Hydro create the sourced finite gauge.
- A matching refreeze while an opposite Aura remains extends the current state.
- At exact expiry, Shatter cannot trigger and Cryo resonance loses Frozen status.
- Before expiry, Shatter deals existing damage and clears only Frozen state.
- Existing ordinary Aura consumption and regression behavior remain stable.

Test cases to add or update:

- Directional: Hydro/Cryo source order with equal gauges.
- Timing: before/exact/after expiry Shatter and resonance checks.
- Extension: refreeze with coexisting opposite Aura.
- Abnormal: expired Freeze hit does not notify or damage Shatter.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/mechanics/element/ResonanceManager.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Both Hydro-on-Cryo and Cryo-on-Hydro now create
  `2 * min(current origin, trigger source)` gauge through the typed additive API;
  equal 1U sources produce exact 1.6U Freeze.
- An active refreeze against remaining opposite Aura adds its exact sourced
  gauge and preserves instantaneous decay rate, extending expiry without
  resetting thaw progression.
- Shatter eligibility and clear use simulator time. Regression proves a blunt
  hit before expiry retains existing Physical damage/clear behavior, while a
  hit at exact expiry emits no Shatter notification or damage.
- Shattering Ice now queries Freeze at buff-resolution time and loses its 15%
  CRIT Rate exactly at Frozen expiry. Ordinary Cryo Aura timing remains exact.
- `ReactionRegressionTest`, `build`, `javadoc`, routed validation, and full
  preflight pass with no persistent service or external job.

### Phase 4: Re-Accept Freeze-Neutral Catalog Baselines - Done

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls for each catalog party against B-060.
- Record hashes, values, ER, cadence, warnings, and absence of Freeze/Shatter.
- Document finite Freeze while retaining explicit coexistence limitations.

Acceptance criteria:

- All six runs are pairwise exact with unchanged B-060 values, ER, cadence, and
  no Freeze/Shatter events or warnings; ordering-only differences are explained.
- Focused/full validation pass and the tracked generated report is restored.
- Plan, ledger, README, and durable checkpoint agree.

Test cases to add or update:

- Normal: pairwise exact catalog runs.
- No-change: accepted totals/ER/cadence.
- Abnormal: no warning or generated-artifact leak.

Verification:

- two fresh `./gradlew --no-daemon RaidenParty` runs
- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- `python scripts/preflight.py --run`

## Implementation Order: Pyro Melt Against Frozen Aura

Objective: resolve non-blunt Pyro against typed Frozen Aura as Melt before
ordinary hidden Auras, while preserving the existing Shatter-first heavy-hit
order.

Scope:

- Typed Frozen eligibility for non-blunt Pyro.
- 2x Melt consumption from Frozen and coexisting ordinary Cryo.
- Hidden Hydro preservation and one Melt notification/multiplier.
- Shatter-first blunt behavior, exact expiry fallback, and catalog controls.

Out of scope:

- Electro, Anemo, or Geo against Frozen and unrelated coexisting Aura priority.
- Trigger residual attachment, Freeze resistance, Shatter damage ICD/gauge,
  hitlag, poise, multi-target behavior, RL changes, and persistent jobs.

Cross-cutting rules:

- The resolver selects synthetic Frozen Melt; `Enemy` remains the typed gauge
  owner and `ReactionCalculator` remains the formula/metadata owner.
- Reuse the existing amplifying handler for multiplier, logging, and ordinary
  Cryo reduction; consume Frozen through its time-aware typed API.
- Return after Frozen Melt so hidden Hydro cannot react with the same trigger.
- Preserve `tryTriggerShatter` before gauge resolution; blunt Pyro sees exposed
  ordinary Aura only after Shatter clears Frozen.
- Keep explicit staging and generated-artifact safety.

### Phase 1: Record Frozen Melt Evidence and Priority Boundary - Done

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record Frozen-as-Cryo, hidden-Aura, Melt consumption, and heavy-hit evidence.
- Inventory Shatter-before-gauge order and typed Freeze operations.
- Exclude other trigger families and unrelated coexisting Auras.

Acceptance criteria:

- Non-blunt and blunt order are distinguished explicitly.
- Frozen and ordinary Cryo consumption are specified separately from Hydro.
- No production behavior changes occur in this phase.

Test cases to add or update:

- No production test; Phase 2 owns focused behavior.

Verification:

- inspect resolver, typed Freeze, Melt, Shatter, and regression paths
- `python scripts/preflight.py --run`

Completion evidence:

- KQM defines Frozen as Cryo-reactable, confirms hidden origin Aura persistence,
  and records Melt-only behavior over hidden Hydro plus Shatter-first heavy hits.
- Maintained gcsim applies Pyro's 2x Melt reduction to both ordinary Cryo and
  Frozen while taking the larger consumed amount for trigger handling.
- Inventory confirms Shatter already runs before gauge resolution, but typed
  Frozen is absent from the ordinary resolver and can survive an incorrect
  Vaporize against hidden Hydro. The focused early-return path isolates the fix.

### Phase 2: Resolve Typed Frozen Melt - Done

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Detect active typed Frozen for non-blunt Pyro before ordinary Aura iteration.
- Emit one Melt, apply its multiplier, consume 2x from Frozen/ordinary Cryo, and
  suppress hidden ordinary reactions for that trigger.
- Regress partial/exact depletion, hidden Hydro/Cryo, blunt, and expiry paths.

Acceptance criteria:

- Non-blunt Pyro on Frozen+Hydro emits Melt only, leaves Hydro exact, and clears
  2.0U Frozen with a 1U trigger.
- Coexisting ordinary Cryo loses the same 2x amount as Frozen.
- A 0.5U trigger partially consumes stronger Frozen and still emits one Melt.
- Blunt Pyro emits Shatter first then reacts with exposed Hydro normally.
- At exact Frozen expiry, Pyro follows ordinary Aura resolution.

Test cases to add or update:

- Hidden Hydro: Melt count/multiplier, no Vaporize, unchanged Hydro.
- Hidden Cryo: simultaneous exact/partial Frozen and Cryo reduction.
- Partial Frozen: stronger gauge remains current-time-aware.
- Blunt: Shatter notification precedes exposed ordinary reaction.
- Expiry: no Frozen Melt at exact end.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- Non-blunt 1U Pyro now emits one forward Melt, consumes 2.0U typed Frozen,
  preserves 0.6U hidden Hydro, and applies the exact 2x damage multiplier.
- A 0.25U trigger reduces both typed Frozen and hidden ordinary Cryo by 0.5U;
  a separate 0.5U trigger leaves exactly 2.0U from a 3.0U Frozen gauge.
- Blunt Pyro emits Shatter then Vaporize against exposed Hydro, while exact-expiry
  Pyro skips Melt and follows ordinary Vaporize resolution.
- Reaction regression, build, Javadoc, routed validation, and preflight all pass.

### Phase 3: Re-Accept Frozen-Melt-Neutral Baselines - Done

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls per catalog party against B-062.
- Record hashes, values, ER, cadence, warnings, and Freeze/Melt absence.
- Document Pyro/Frozen order and close B-063.

Acceptance criteria:

- All six runs match B-062 exactly with no Frozen/Melt path activation.
- Focused/full validation pass; tracked generated report is restored.
- Plan, ledger, README, and checkpoint agree.

Test cases to add or update:

- Normal: pairwise exact catalog controls.
- No-change: accepted values/ER/cadence and priority hashes.
- Abnormal: zero warning/generated-artifact leak.

Verification:

- two fresh `./gradlew --no-daemon RaidenParty` runs
- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh no-daemon runs per catalog party match pairwise after removing only
  Gradle's elapsed-success line: Raiden
  `bbd1c4f61f00024213e1c6f49519fe7abe0ad568c8dacf4c562437e5e6b59abc`,
  Flins `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`,
  and Flins2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
- Totals/DPS remain 1,365,787/65,037, 22,675,823/227,898, and
  15,817,125/228,902. ER and timed/reaction/delayed/ICD counts retain the B-062
  contracts because every normalized payload is byte-identical.
- All six runs contain zero Melt/Frozen/Shatter and zero
  warning/error/failed-action/insufficient-energy lines.
- README documents the Pyro/Frozen order and narrows the remaining Frozen
  exclusions. The tracked generated report was restored.

## Implementation Order: Deterministic Simultaneous Reaction Priority

Objective: make ordinary multi-Aura reaction resolution follow one explicit
trigger-specific priority instead of `HashSet` iteration identity.

Scope:

- Pure priority ordering for every elemental trigger supported by the resolver.
- Resolver routing through that order without changing formulas or consumption.
- Pyro-on-EC and Anemo-on-EC ordering regressions.
- Deterministic catalog baselines across fresh JVM processes.

Out of scope:

- Trigger residual gauge, reaction cancellation after earlier consumption, and
  hidden/innate/self Aura behavior.
- Typed Frozen, Burning, Quicken, or Dendro Core synthetic-state priority.
- Multi-target reaction spread, reaction damage ICD, hitlag, RL behavior, and
  persistent jobs.

Cross-cutting rules:

- `ReactionPriority` is a pure mechanics policy; `Enemy` remains a target state
  store and `CombatActionResolver` remains the transition orchestrator.
- Priority uses typed `Element` values only. Unknown/nonreactive leftovers use
  stable enum declaration order so same-element extension still executes.
- Match maintained gcsim ordinary dispatcher order for each trigger, but retain
  this simulator's existing reaction calculations and consumption semantics.
- Do not make storage containers ordered merely to influence mechanics.
- Preserve explicit staging and generated-artifact safety.

### Phase 1: Record Priority Evidence and HashSet Failure - Done

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record the B-061 before/after class-layout control evidence.
- Inventory resolver iteration and maintained trigger dispatch order.
- Define ordinary-Aura and synthetic-state boundaries.

Acceptance criteria:

- The plan gives an explicit ordered Aura list for every elemental trigger.
- Storage order is not treated as reaction policy.
- No production behavior changes occur in this phase.

Test cases to add or update:

- No production test; Phase 2 owns pure and integrated ordering.

Verification:

- compare normalized B-060/B-061 controls
- inspect `Enemy.getActiveAuras`, resolver, calculator, and gcsim dispatcher
- `python scripts/preflight.py --run`

Completion evidence:

- B-061 controls proved that adding unrelated `Enemy` fields reversed adjacent
  Hydro/Electro reaction log lines while every value, count, and result remained
  unchanged. B-060 itself used opposite orders in FlinsParty and FlinsParty2.
- `Enemy.getActiveAuras` returns `HashSet`; the resolver directly iterates it.
  Maintained gcsim dispatches typed attempts explicitly and puts Overload before
  Vaporize for Pyro and Electro Swirl before Hydro Swirl for Anemo.
- Planned ordinary priorities are Electro `[Pyro, Hydro, Cryo, Dendro]`, Pyro
  `[Electro, Hydro, Cryo, Dendro]`, Cryo `[Electro, Pyro, Hydro]`, Hydro
  `[Pyro, Cryo, Dendro, Electro]`, Anemo `[Electro, Pyro, Hydro, Cryo]`, Geo
  `[Electro, Hydro, Cryo, Pyro]`, and Dendro `[Electro, Pyro, Hydro]`.
- Frozen/Burning/Quicken synthetic state and residual behavior remain explicit
  exclusions. Documentation preflight passes without routed checks or leaks.

### Phase 2: Implement Typed Ordinary Aura Priority - Done

Target files:

- `src/java/mechanics/reaction/ReactionPriority.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `TASKS.md`

Tasks:

- Add a pure trigger-to-ordered-Aura policy with stable leftover ordering.
- Route ordinary resolver iteration through the policy.
- Regress every trigger list and integrated Pyro/Anemo Hydro+Electro behavior.

Acceptance criteria:

- Every documented list is exact regardless of input set implementation/order.
- Pyro notifies Overloaded before Vaporize on Hydro+Electro.
- Anemo notifies Electro Swirl before Hydro Swirl on Hydro+Electro.
- Unsupported Physical and same-element inputs remain stable and nonreactive.
- Existing full reaction regression remains exact.

Test cases to add or update:

- Pure: reverse-ordered EnumSet/HashSet inputs for all seven trigger lists.
- Integrated: Pyro dual Aura kind order and Anemo related-element order.
- Fallback: same element and Physical trigger.
- No-change: formulas, totals, gauge, and prior Freeze/Burning contracts.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/mechanics/reaction/ReactionPriority.java --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/sample/ReactionRegressionTest.java --run`
- `python scripts/preflight.py --run`

Completion evidence:

- New pure `ReactionPriority` owns trigger-specific ordinary-Aura ordering and
  returns an immutable list. Known reactive Auras precede leftovers, which use
  stable `Element` declaration order independent of input Set implementation.
- The resolver changes only its ordinary Aura iteration source; state storage,
  reaction formulas, consumption, synthetic-state routing, and notifications
  retain their existing owners.
- Regression supplies reverse insertion order for all seven elemental trigger
  policies, checks Physical/same-element fallback, and proves integrated Pyro
  emits Overload before Vaporize while Anemo emits Electro Swirl before Hydro.
- Full reaction regression retains Freeze, Burning, Quicken, EC, Lunar, formula,
  gauge, character, weapon, artifact, and resonance contracts.
- `ReactionRegressionTest`, `build`, `javadoc`, routed validation, and full
  preflight pass without persistent service or external job.

### Phase 3: Accept Deterministic Priority Baselines - Done

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls per catalog party against B-061 values.
- Record deterministic hashes, values, ER, cadence, warnings, and order changes.
- Document ordinary simultaneous priority and close B-062.

Acceptance criteria:

- Each fresh-JVM pair is byte-identical and follows the sourced order.
- Totals, DPS, ER, allocations, and event counts remain B-061-exact.
- Focused/full validation pass; tracked generated report is restored.

Test cases to add or update:

- Normal: all three catalog parties complete pairwise exactly.
- No-change: accepted values/ER/cadence.
- Abnormal: zero warnings and generated-artifact leak.

Verification:

- two fresh `./gradlew --no-daemon RaidenParty` runs
- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh no-daemon runs per catalog party are byte-identical after removing
  only Gradle's elapsed-success line: Raiden
  `bbd1c4f61f00024213e1c6f49519fe7abe0ad568c8dacf4c562437e5e6b59abc`,
  Flins `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`,
  and Flins2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
- Totals/DPS remain 1,365,787/65,037, 22,675,823/227,898, and
  15,817,125/228,902. ER and timed/reaction/delayed/ICD counts remain exactly
  100/175/179/174 with 152/55/11/38, 109/100/100/180 with 613/230/48/88, and
  130/128/100/196 with 468/140/33/71.
- Every run contains zero warning/error/failed-action/insufficient-energy lines.
  Raiden uses Overload before Vaporize; both Flins parties use Electro Swirl
  before Hydro Swirl, matching the explicit policy and maintained reference.
- README documents ordinary simultaneous priority and retains synthetic-state,
  residual, and multi-target exclusions. The tracked report was restored and no
  generated output is staged.

Completion evidence:

- Two fresh no-daemon runs per catalog party match pairwise after removing only
  Gradle's elapsed-success line: Raiden
  `bbd1c4f61f00024213e1c6f49519fe7abe0ad568c8dacf4c562437e5e6b59abc`,
  Flins `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`,
  and unchanged Flins2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
- Totals/DPS remain 1,365,787/65,037, 22,675,823/227,898, and
  15,817,125/228,902. ER and timed/reaction/delayed/ICD counts remain exactly
  100/175/179/174 with 152/55/11/38, 109/100/100/180 with 613/230/48/88, and
  130/128/100/196 with 468/140/33/71.
- Every run contains zero Freeze/Shatter reaction lines and zero
  warning/error/failed-action/insufficient-energy lines. Raiden and Flins differ
  from B-060 only by reversed adjacent Hydro/Electro reaction log lines; values,
  allocations, counts, and all other output are unchanged. The `HashSet` order
  issue is recorded separately as B-062 instead of being hidden in Freeze scope.
- README documents finite typed Freeze and retains the exact coexistence,
  residual, resistance, hitlag, poise, and Shatter-ICD exclusions. The tracked
  generated report was restored and no output is staged.

## Implementation Order: Overload Damage Sequence

Status: Phases 1-3 are complete. Snapshot-safe target and owner Overload damage
cooldowns are implemented and accepted against deterministic catalog baselines.

Scope:

- Single-target 0.1-second Overload damage GCD.
- Per-`CharacterId`, per-target 0.5-second Overload damage sequence.
- Reaction notification and gauge-consumption continuity while damage is blocked.
- Snapshot save/restore and deterministic catalog acceptance.

Out of scope for this pass:

- Multi-target AoE propagation, adjacent-target reaction limits, poise, and
  knockback.
- Superconduct, Swirl, Shatter, Bloom-family, Burning, or Crystallize damage
  sequences.
- Action elemental-application ICD, reaction formulas, target geometry, RL
  changes, and persistent jobs.

Definitions:

- **Overload target GCD**: the earliest time any owner may deal the next
  Overload damage instance to this simulator's one enemy.
- **Overload owner cooldown**: the earliest time one `CharacterId` may deal its
  next Overload damage instance to that enemy.

Cross-cutting rules:

- `ReactionState` owns mutable cooldown data, `ReactionStateController` exposes
  current-time policy, and `CombatActionResolver` only gates damage recording.
- A blocked damage instance still notifies Overload and consumes Aura exactly.
- Cooldowns start only when damage is accepted; exact 0.1/0.5-second boundaries
  are inclusive.
- Snapshot payloads use typed `CharacterId` keys and defensive copies.
- Preserve explicit staging and generated-artifact safety.

### Phase 1: Record Overload Damage-Sequence Evidence - Done

Why first:

- Damage suppression must be separated from reaction eligibility and ordinary
  elemental-application ICD before introducing runtime state.

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Reconcile KQM's per-character 0.5-second evidence with maintained gcsim's
  target-wide 0.1-second GCD and `ReactionB` damage group.
- Inventory resolver damage, notification, consumption, and snapshot boundaries.
- Record excluded AoE, poise, and other-reaction limits.

Acceptance criteria:

- The two independent cooldown dimensions and exact-boundary policy are explicit.
- Suppressed damage does not suppress reaction notification or gauge consumption.
- No production behavior changes occur in this phase.

Test cases to add or update:

- No production test; Phase 2 owns focused state and resolver behavior.

Verification:

- inspect KQM Overload ICD evidence and maintained gcsim overload/ICD policies
- inspect resolver, reaction state/controller, snapshot, and regression paths
- `python scripts/preflight.py --run`

Completion evidence:

- KQM's v1.5 reproducible finding records one Overload damage instance per
  character/target in 0.5 seconds while later reactions still consume gauge.
- Maintained gcsim independently applies a target-wide 0.1-second `overloadGCD`
  before queuing damage and a per-character `ReactionB` sequence of one damage
  hit per 0.5 seconds.
- The current resolver always records every notified Overload's damage. Existing
  action ICD cannot express the separate reaction-damage limits, and reaction
  state is already the snapshot-aligned owner for transient reaction policy.

### Phase 2: Implement Snapshot-Safe Overload Damage Limits - Done

Why second:

- Phase 1 fixes ownership and boundary semantics before state is added across the
  runtime and snapshot contract.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/mechanics/rl/CapabilityProfiler.java` (snapshot forwarding only)
- `TASKS.md`

Tasks:

- Store target-wide and per-owner next-damage times in `ReactionState`.
- Expose one current-time decision through the controller/facade and preserve it
  through defensive snapshot save/restore.
- Gate only Overload damage/log recording after reaction notification and Aura
  consumption.
- Regress target, owner, exact-boundary, cross-owner, side-effect, and restore
  behavior.

Acceptance criteria:

- The first Overload deals damage and starts both limits.
- Any owner is blocked before 0.1 seconds; the same owner remains blocked before
  0.5 seconds; a different owner may deal damage at exactly 0.1 seconds.
- The original owner may deal damage at exactly 0.5 seconds.
- Every blocked hit still emits one Overload and consumes exactly 1x Aura.
- Snapshot restore reproduces both active cooldown boundaries.

Test cases to add or update:

- Normal: first damage, different owner at 0.1 seconds, original at 0.5 seconds.
- Abnormal: same/different owner before each boundary deals no reaction damage.
- Side effect: blocked hits retain notification count and exact residual Aura.
- Snapshot: save active limits, cross boundaries, restore, and replay exact gates.
- No-change: non-Overload transformative damage remains immediate.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/agent_validate.py --path src/java/simulation/runtime/ReactionState.java --path src/java/simulation/runtime/ReactionStateController.java --path src/java/simulation/CombatSimulator.java --path src/java/simulation/SimulatorSnapshot.java --path src/java/simulation/runtime/CombatActionResolver.java --path src/java/sample/ReactionRegressionTest.java --path src/java/mechanics/rl/CapabilityProfiler.java`
- `python scripts/preflight.py`

Completion evidence:

- The first Overload starts target 0.1-second and owner 0.5-second limits;
  different-owner 0.05-second damage is blocked and exact 0.1-second damage is
  accepted without the blocked attempt starting a cooldown.
- The original owner remains blocked at 0.49 seconds and is accepted at exactly
  0.5 seconds. All six reactions notify and consume 1U Aura while only three
  record damage; immediate Superconduct remains unaffected.
- Snapshot save/restore preserves both typed cooldown dimensions and exact replay
  boundaries through defensive `CharacterId` map copies. Capability profiling
  only forwards the enlarged snapshot payload; no RL algorithm or tensor changed.
- `ReactionRegressionTest`, `build`, and `javadoc` pass. Routed validation and
  preflight report no leaks; `PartyCatalogRegressionTest` and `BenchmarkRLJava`
  were not run because this autonomous pass explicitly excludes RL execution.

### Phase 3: Accept Overload-Limited Catalog Baselines - Done

Why third:

- Catalog deltas can be interpreted only after focused damage-sequence and
  snapshot behavior pass.

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls per catalog party against B-063.
- Attribute every changed Overload damage instance, or prove exact no-change.
- Record hashes, totals, ER/cadence, warnings, and close B-064.

Acceptance criteria:

- Each catalog pair is byte-identical and every delta is attributable to a
  blocked Overload damage instance.
- ER, action cadence, reaction notifications, and Aura behavior remain stable.
- Focused/full validation pass and tracked generated report is restored.

Test cases to add or update:

- Normal: pairwise exact catalog controls.
- No-change: ER/action/reaction cadence outside damage suppression.
- Abnormal: zero warning/generated-artifact leak.

Verification:

- two fresh `./gradlew --no-daemon RaidenParty` runs
- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh no-daemon runs per catalog party match pairwise after removing only
  Gradle's elapsed-success line: Raiden
  `03301ef5a3d650a91cfa07660cb0077d8c1585da04d79803a97f36e8249ba85a`,
  unchanged Flins
  `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`,
  and unchanged Flins2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
- Raiden changes from 1,365,787/65,037 to 1,352,375/64,399. The exact 13,412
  delta is one Xiangling-owned Overload blocked at displayed T=11.4 after her
  T=10.9 Pyronado Overload; all other final reaction lines are identical.
- Timed/reaction/DoT/ordinary-ICD counts remain 152/55/11/38, 613/230/48/88,
  and 468/140/33/71. Both Flins totals remain 22,675,823/227,898 and
  15,817,125/228,902; all six runs contain zero warning/error/failed-action/
  insufficient-energy lines.
- README documents both damage limits. The tracked generated report was restored
  and no generated output is staged.

## Implementation Order: Standard Crystallize Global Cooldown

Status: Phases 1-3 are complete. Snapshot-safe standard Crystallize gating and
its deterministic catalog baselines are accepted.

Scope:

- One-second standard Crystallize cooldown for the simulator's single enemy.
- Shared cooldown across owners, ordinary Aura elements, and hits.
- Suppressed reaction notification, Aura consumption, and shard side effects.
- Snapshot save/restore and deterministic catalog acceptance.

Out of scope for this pass:

- Lunar-Crystallize, Moondrift, or Harmony cadence.
- Shard pickup, shield absorption, Archaic Petra, multi-target state, and crystal
  spawn geometry.
- Action elemental-application ICD, other reaction damage sequences, RL changes,
  and persistent jobs.

Definitions:

- **Standard Crystallize cooldown**: the earliest time any owner/element may
  trigger the next non-Lunar Crystallize on the one modeled enemy.

Cross-cutting rules:

- `ReactionState` owns the cooldown timestamp; the controller/facade expose one
  current-time decision and snapshots preserve it.
- Gate before `notifyReaction` and stateful handling so blocked attempts do not
  consume Aura or create side effects.
- Exact one-second boundary is inclusive and blocked attempts do not refresh it.
- Lunar conversion occurs before the gate and bypasses standard Crystallize GCD.
- Preserve explicit staging and generated-artifact safety.

### Phase 1: Record Standard Crystallize GCD Evidence - Done

Why first:

- Standard and Lunar behavior plus no-consumption semantics must be separated
  before inserting a gate into shared reaction iteration.

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record the sourced one-second per-target/shared-owner/shared-element contract.
- Confirm suppressed standard Crystallize does not consume Aura.
- Inventory conversion, notification, resolver priority, and snapshot boundaries.

Acceptance criteria:

- Standard-only eligibility and the exact inclusive boundary are explicit.
- Same-hit multi-Aura behavior selects at most one ordinary Aura by B-062 order.
- No production behavior changes occur in this phase.

Test cases to add or update:

- No production test; Phase 2 owns focused cooldown behavior.

Verification:

- inspect KQM Crystallize ICD/correction and maintained gcsim standard/Lunar paths
- inspect resolver, reaction state/controller, snapshot, and regression paths
- `python scripts/preflight.py --run`

Completion evidence:

- KQM v2.7 measures a one-second per-monster cooldown shared across attacks,
  characters, and Aura elements; its v2.8 correction proves blocked attempts do
  not consume gauge.
- Maintained gcsim independently stores one target `crystallizeGCD`, checks it
  before standard reduction/event emission, and sets it for 60 frames. Its
  Lunar-Crystallize path bypasses that method and has no standard GCD.
- The current ordinary resolver notifies and consumes every compatible Aura in
  the same Geo hit and has no cross-hit cooldown. B-062 already defines the
  deterministic Aura winner order needed after gating.

### Phase 2: Implement Snapshot-Safe Standard Crystallize GCD - Done

Why second:

- Phase 1 establishes that the gate precedes all reaction side effects and does
  not apply after Lunar conversion.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/mechanics/rl/CapabilityProfiler.java` (snapshot forwarding only)
- `TASKS.md`

Tasks:

- Add one typed standard Crystallize cooldown timestamp and snapshot round trip.
- Gate converted reaction results before notification/handling.
- Regress same-hit dual Aura, pre/exact boundary, shared owner/element, blocked
  no-consumption, snapshot replay, and Lunar bypass.

Acceptance criteria:

- A dual-Aura Geo hit notifies/consumes only B-062's first ordinary Aura.
- Any owner/element before one second is blocked without notification or
  consumption; exact one second is accepted.
- Blocked attempts do not refresh the original boundary.
- Snapshot restore reproduces the active boundary.
- Rapid Lunar-Crystallize remains outside the standard cooldown.

Test cases to add or update:

- Normal: first standard reaction and exact one-second retry.
- Abnormal: same-hit second Aura and 0.999-second cross-owner/element retry.
- Side effect: listener count and both Aura residuals prove no blocked handling.
- Snapshot: save active GCD, pass boundary, restore, and replay.
- No-change: three immediate Lunar triggers retain current Harmony behavior.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- routed validation/preflight planning without RL execution

Completion evidence:

- `ReactionState` owns one finite standard-only boundary; controller, simulator,
  and snapshot APIs preserve it without coupling the policy to resolver state.
- The converted result is gated before `reactionTriggered`, listener dispatch,
  Aura reduction, or stateful handling. Lunar-Crystallize never calls the gate.
- Focused regression proves one Electro winner from a same-hit Electro/Hydro
  fixture, shared cross-owner/Hydro suppression at 0.999 seconds, acceptance at
  exactly 1.000 seconds, no blocked consumption/refresh, and snapshot replay.
- Three immediate Lunar triggers still increment cadence three times and retain
  the existing fourth Harmony notification.
- `./gradlew ReactionRegressionTest`, `./gradlew build`, and `./gradlew javadoc`
  pass. Routed validation reports no leaks; RL-routed catalog/rollout checks were
  not run under this session's explicit simulator-only boundary.

### Phase 3: Accept Crystallize-GCD-Neutral Catalog Baselines - Done

Why third:

- Catalog acceptance follows only after focused standard/Lunar separation and
  snapshot behavior pass.

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls per catalog party against B-064.
- Record hashes, totals, ER/cadence, Crystallize path counts, and warnings.
- Document standard-only GCD and close B-066.

Acceptance criteria:

- All pairs are deterministic and every delta is attributable, or exact
  no-change is proven.
- Lunar behavior remains stable; tracked generated report is restored.
- Plan, ledger, README, and checkpoint agree.

Test cases to add or update:

- Normal: pairwise exact catalog controls.
- No-change: accepted totals/ER/cadence and Lunar paths.
- Abnormal: zero warning/generated-artifact leak.

Verification:

- two fresh `./gradlew --no-daemon RaidenParty` runs
- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh no-daemon runs per party are pairwise exact after removing only the
  Gradle elapsed-success line. Normalized SHA-256 remains
  `03301ef5a3d650a91cfa07660cb0077d8c1585da04d79803a97f36e8249ba85a`
  for Raiden, `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`
  for Flins, and `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`
  for Flins2.
- Totals/DPS remain 1,352,375/64,399, 22,675,823/227,898, and
  15,817,125/228,902. Action/reaction/DoT/ordinary-ICD counts remain
  152/55/11/38, 613/230/48/88, and 468/140/33/71.
- None of the catalog parties enters standard Crystallize. All six logs contain
  zero warning/error/failed-action/insufficient-energy matches, so the current
  Lunar catalog remains byte-identical to B-064.
- README documents the standard-only cooldown. The tracked generated report was
  restored and no generated output is staged.

## Implementation Order: Superconduct Damage Sequence

Status: Phases 1-3 are complete. Snapshot-safe Superconduct target/owner damage
sequences and their deterministic catalog baselines are accepted.

Scope:

- One-enemy 0.1-second target-wide Superconduct damage GCD.
- Per-`CharacterId` fixed 0.5-second damage window accepting two hits.
- Continued notification, Aura consumption, and physical RES shred refresh.
- Snapshot save/restore and deterministic catalog acceptance.

Out of scope for this pass:

- Frozen-Superconduct trigger residuals, adjacent-target AoE, multi-target
  damage caps, stagger/poise simulation, and hitlag extension.
- Overload, Shatter, Swirl, action application ICD, RL changes, and persistent
  jobs.

Definitions:

- **Target GCD**: the earliest time any owner may enqueue the next Superconduct
  damage attempt on the one modeled enemy.
- **Owner sequence**: a fixed window started by the first target-accepted damage
  attempt; entries one and two deal damage, later entries do not until reset.

Cross-cutting rules:

- `ReactionState` owns typed target/owner policy and no resolver dependencies.
- The controller/facade inject current simulator time; snapshots preserve the
  immutable owner states.
- Notify and consume before damage gating. Refresh physical shred for every
  valid reaction, including target- and owner-blocked damage attempts.
- Apply target GCD before owner sequence. Target-blocked attempts do not mutate
  owner state; owner-blocked attempts still start the next target GCD.
- Exact 0.1/0.5-second boundaries are inclusive.
- Preserve explicit staging and generated-artifact safety.

### Phase 1: Record Superconduct Damage-Sequence Evidence - Done

Why first:

- A whole-reaction gate would incorrectly suppress gauge and shred, while a
  sliding owner window would diverge from the maintained fixed counter.

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record KQM's owner-specific two-hit/0.5-second finding.
- Confirm maintained target GCD ordering and fixed owner counter semantics.
- Inventory current notification, consumption, shred, damage, and snapshot order.

Acceptance criteria:

- Damage-only suppression and both policy dimensions are explicit.
- Target-blocked and owner-blocked state transitions are distinguished.
- No production behavior changes occur in this phase.

Test cases to add or update:

- No production test; Phase 2 owns focused sequence behavior.

Verification:

- inspect KQM Superconduct mechanic update and maintained gcsim paths
- inspect resolver, reaction state/controller, snapshot, and regression paths
- `python scripts/preflight.py --run`

Completion evidence:

- KQM v2.5 records at most two Superconduct damage instances from one character
  in 0.5 seconds while further reactions retain gauge reduction and stagger.
- Maintained gcsim independently emits the reaction before a target-wide
  0.1-second attack GCD, then routes accepted attacks through owner-specific
  `ReactionA` entries one/two followed by zeros until its fixed timer resets.
- The current resolver notifies and consumes before damage, but applies physical
  shred after damage. Phase 2 must move shred ahead of the damage-only gate.

### Phase 2: Implement Snapshot-Safe Superconduct Damage Sequence - Done

Why second:

- Phase 1 defines one reusable policy call and the required side-effect order.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/mechanics/rl/CapabilityProfiler.java` (snapshot forwarding only)
- `TASKS.md`

Tasks:

- Add typed target and immutable per-owner fixed-window state with snapshot round
  trip.
- Refresh physical shred before the Superconduct damage-only decision.
- Regress target/owner boundaries, cross-owner state, all side effects, and
  restore replay.

Acceptance criteria:

- Target attempts before 0.1 seconds deal no damage and start no owner window.
- One owner deals its first two target-accepted hits, not its third; another
  owner has an independent two-hit sequence.
- Exact 0.1 and 0.5 seconds are accepted.
- Every blocked attempt still notifies, consumes Aura, and refreshes shred.
- Snapshot restore reproduces target and owner sequence decisions.

Test cases to add or update:

- Normal: owner hits one/two and exact 0.5-second reset hit.
- Abnormal: 0.05-second cross-owner target block and same-owner third hit.
- Side effect: listener/Aura counts plus post-expiry physical damage prove
  blocked reactions refresh shred.
- Snapshot: restore after owner hit two and replay blocked/accepted decisions.
- No-change: immediate Overload damage behavior remains isolated.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- routed validation/preflight planning without RL execution

Completion evidence:

- `ReactionState` owns one target boundary and immutable owner fixed-window
  payloads. Controller/facade inject time and snapshots round-trip both policy
  dimensions without exposing resolver details.
- Resolver notification and Aura reduction remain ahead of gating. Physical
  shred now refreshes before both target- and owner-damage decisions.
- Focused regression covers 0.05/0.10-second target boundaries, owner entries
  one/two/three, independent owner state, exact 0.5-second reset, an owner block
  starting the next target GCD, seven unchanged notifications/consumptions, and
  snapshot replay after owner entry two.
- A separate physical hit at 12.15 seconds proves the damage-blocked 0.20-second
  reaction refreshed the 12-second shred after the last damaging hit's expiry.
- `./gradlew ReactionRegressionTest`, `./gradlew build`, and `./gradlew javadoc`
  pass. Routed validation reports no leaks; RL-routed catalog/rollout checks were
  not run under this session's explicit simulator-only boundary.

### Phase 3: Accept Superconduct-Sequence Catalog Baselines - Done

Why third:

- Catalog acceptance follows focused damage/effect and snapshot verification.

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls per catalog party against B-066.
- Record hashes, totals, ER/cadence, Superconduct counts, and warnings.
- Document the damage-only sequence and close B-067.

Acceptance criteria:

- All pairs are deterministic and every delta is attributable, or exact
  no-change is proven.
- Tracked generated report is restored and no artifact is staged.
- Plan, ledger, README, and checkpoint agree.

Test cases to add or update:

- Normal: pairwise exact catalog controls.
- No-change: accepted totals/ER/cadence and non-Superconduct paths.
- Abnormal: zero warning/generated-artifact leak.

Verification:

- two fresh `./gradlew --no-daemon RaidenParty` runs
- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh no-daemon runs per party are pairwise exact after removing only the
  Gradle elapsed-success line. Normalized SHA-256 remains
  `03301ef5a3d650a91cfa07660cb0077d8c1585da04d79803a97f36e8249ba85a`
  for Raiden, `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`
  for Flins, and `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`
  for Flins2.
- Totals/DPS remain 1,352,375/64,399, 22,675,823/227,898, and
  15,817,125/228,902. Action/reaction/DoT/ordinary-ICD counts remain
  152/55/11/38, 613/230/48/88, and 468/140/33/71.
- None of the catalog parties can trigger Superconduct. All six logs contain
  zero Superconduct and warning/error/failed-action/insufficient-energy matches.
- README documents damage-only sequence behavior. The tracked generated report
  was restored and no generated output is staged.

## Implementation Order: Dendro Core Damage-Cap Snapshot State

Status: Phases 1-3 are complete. Snapshot-safe Dendro Core damage-cap history
and deterministic catalog baselines are accepted.

Scope:

- Existing single-target two-hit/0.5-second Dendro Core damage-cap history.
- One reaction-state owner for decisions, defensive copies, and restore.
- Snapshot branch replay and deterministic catalog acceptance.

Out of scope for this pass:

- Core creation, consumption count, expiry, damage, ownership, or resistance
  formulas.
- Multi-target hit caps, geometry, self-damage, or reaction policy changes.
- RL behavior, protocol/tensor changes, training, and persistent jobs.

Definitions:

- **Core damage history**: timestamps of accepted Bloom, Hyperbloom, or Burgeon
  damage instances still active in the target's fixed 0.5-second window.

Cross-cutting rules:

- `ReactionState` owns snapshot-relevant reaction policy; the scheduler only
  requests a current-time decision.
- Copy and restore expose no mutable internal list. Invalid, future, and expired
  timestamps are rejected at the snapshot boundary.
- The exact 0.5-second boundary remains inclusive. Damage-capped core
  consumption still removes the configured cores.
- Preserve explicit staging and generated-artifact safety.

### Phase 1: Record Core-Cap Snapshot Gap - Done

Why first:

- The existing cap policy is correct during a linear run; only rollback state
  ownership is defective, so the no-change boundary must precede code edits.

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Inventory core payload, cap-history, save/restore, and profiler forwarding.
- Define a replay that distinguishes one saved hit from two future hits.
- Freeze core formulas, consumption behavior, and multi-target exclusions.

Acceptance criteria:

- The missing snapshot field and its observable replay error are explicit.
- One role owns pruning, hit acceptance, copies, and restore validation.
- No production behavior changes occur in this phase.

Test cases to add or update:

- No production test; Phase 2 adds the failing branch-replay boundaries.

Verification:

- inspect `ReactionEffectScheduler`, `ReactionState`, `CombatSimulator`,
  `SimulatorSnapshot`, and existing core regressions
- `python scripts/preflight.py --run`

Completion evidence:

- Active core payloads and identifiers round-trip through `ReactionState`, but
  accepted damage timestamps are a scheduler-local mutable list omitted from
  both save and restore.
- Restoring a snapshot rewinds time, damage, and cores without rebuilding the
  scheduler, so post-save timestamps remain and incorrectly consume replay cap.
- The fix will preserve the existing shared two-hit/0.5-second target policy;
  it changes only branch determinism and snapshot completeness.

### Phase 2: Implement Snapshot-Safe Core Damage History - Done

Why second:

- Phase 1 fixes ownership and replay semantics before the snapshot constructor
  and scheduler call site change together.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/mechanics/rl/CapabilityProfiler.java` (snapshot forwarding only)
- `TASKS.md`

Tasks:

- Move cap constants/history/decision into `ReactionState`.
- Add defensive active-history copy/restore through controller and snapshot.
- Add actual core-consumption replay and exact-boundary regressions.

Acceptance criteria:

- A snapshot saved after one accepted hit restores exactly one active timestamp.
- Future branch hits do not survive restore: replayed hit two damages and hit
  three is capped while every configured core is consumed.
- At exactly 0.5 seconds old hits expire and two new hits can damage.
- Existing core overflow, Hyperbloom/Burgeon, resistance, and snapshot tests pass.

Test cases to add or update:

- Normal: save one accepted Hyperbloom and accept replayed second damage.
- Abnormal: mutate two future attempts, restore, then cap only replayed third.
- Boundary: exact 0.5-second expiry accepts two fresh instances.
- Snapshot: copied history is size one and replay damage/core counts rewind.
- No-change: current same-time Burgeon two-hit cap and core removal pass.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- routed validation/preflight planning without RL execution

Completion evidence:

- `ReactionState` now owns the two-hit/0.5-second constants, active timestamp
  list, pruning, acceptance, defensive copy, and validated restore. The
  scheduler retains only its damage-recording role and requests one facade
  decision per consumed core.
- Snapshot save/restore and profiler merge forwarding carry the active history;
  restore rejects null, non-finite, future, expired, and over-limit entries.
- Focused real-consumption coverage saves one accepted Hyperbloom, mutates two
  Burgeon attempts on a discarded branch, restores three cores and one hit,
  accepts replayed hit two, caps only hit three, and proves capped attempts
  still consume cores. At exactly 0.5 seconds two fresh hits are accepted.
- `ReactionRegressionTest`, `build`, and `javadoc` pass. Routed preflight and
  agent validation report the expected RL checks, which were not executed under
  the simulator-only session boundary.

### Phase 3: Accept Core-Snapshot-Neutral Catalog Baselines - Done

Why third:

- Catalog no-change is accepted only after focused rollback behavior passes.

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls per catalog party against B-071.
- Record hashes, totals, ER/cadence, Dendro Core activity, and warnings.
- Close B-072 and document snapshot completeness.

Acceptance criteria:

- All pairs are deterministic and byte-identical to B-071 because no catalog
  party creates Dendro Cores.
- Tracked generated report is restored and no artifact is staged.
- Plan, ledger, README, and checkpoint agree.

Test cases to add or update:

- Normal: pairwise exact catalog controls.
- No-change: zero Dendro Core output and accepted totals/ER/cadence.
- Abnormal: zero warning/generated-artifact leak.

Verification:

- two fresh `./gradlew --no-daemon RaidenParty` runs
- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two normalized payloads per party match B-071 exactly: RaidenParty
  `86a70a9357148363fcc465e648accb749cc774a3a3adf8c0aac35a583c37e601`,
  FlinsParty
  `2d530f72e3cf4d0d6ee6209ef68dff6cf1454707fd3b5e43fb21e249a682ed68`,
  and FlinsParty2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
- Totals/DPS remain 1,304,576/62,123, 22,639,410/227,532, and
  15,817,125/228,902. All six logs contain zero Dendro Core, Bloom-family,
  warning, error, failed-action, or insufficient-energy matches.
- README documents rollback-complete cap state. The tracked generated report
  was restored and no generated output is staged.

## Implementation Order: Swirl Damage Sequences

Status: Phases 1-3 are complete. Snapshot-safe Swirl damage sequences and their
deterministic catalog baselines are accepted.

Scope:

- Per-Swirled-Element 0.1-second target damage GCD.
- Per-`CharacterId` and Swirled Element fixed 0.5-second two-hit sequence.
- Continued reaction notification and sourced half-gauge Aura consumption.
- Snapshot save/restore and deterministic catalog acceptance.

Out of scope for this pass:

- Multi-target AoE, element spread/application, chain reactions, and geometry.
- Hydro AoE's special no-damage rule, poise, hitlag, and Frozen double Swirl.
- RL behavior, protocol/tensor changes, training, and persistent jobs.

Definitions:

- **Swirled Element**: Pyro, Hydro, Electro, or Cryo damage element derived from
  the consumed Aura; each has independent target and owner state.
- **Owner sequence**: first two target-passing attempts damage in a fixed
  0.5-second window; later attempts do not until the original window resets.

Cross-cutting rules:

- `ReactionState` owns nested per-element typed state; controller/facade inject
  current time and snapshots deep-copy both dimensions.
- Notification and Aura consumption precede damage decisions. Target-blocked
  attempts do not mutate owner state; owner-blocked attempts start target GCD.
- Exact 0.1/0.5-second boundaries are inclusive.
- Distinct elements and owners remain independent, preserving double Swirl.
- Preserve explicit staging and generated-artifact safety.

### Phase 1: Record Swirl Damage-Sequence Evidence - Done

Why first:

- Swirl combines a maintained target GCD and element-specific owner sequence;
  both must be modeled without crossing the multi-target boundary.

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record KQM's per-element two-hit/0.5-second damage sequence.
- Confirm maintained event/consumption, element GCD, and owner ICD ordering.
- Inventory resolver, reusable sequence helper, snapshot, and test boundaries.

Acceptance criteria:

- Both damage-only dimensions and owner/element independence are explicit.
- Multi-target spread and current double-Swirl boundaries are explicit.
- No production behavior changes occur in this phase.

Test cases to add or update:

- No production test; Phase 2 owns focused sequence behavior.

Verification:

- inspect KQM Swirl and internal-cooldown references
- inspect maintained gcsim `swirl.go`, ReactionA, and target ICD paths
- inspect resolver, reaction state/controller, snapshots, and regressions
- `python scripts/preflight.py --run`

Completion evidence:

- KQM records two Swirl damage instances per Element per enemy in 0.5 seconds.
- Maintained gcsim emits/consumes before one 0.1-second GCD per Element and uses
  separate Pyro/Hydro/Cryo/Electro tags with owner-specific ReactionA state.
- Current resolver consumes half source gauge and records every Swirl damage
  without either damage-only decision.

### Phase 2: Implement Snapshot-Safe Swirl Damage Sequences - Done

Why second:

- Phase 1 establishes nested state ownership and side-effect ordering before
  extending the reusable fixed-window helper.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/mechanics/rl/CapabilityProfiler.java` (snapshot forwarding only)
- `TASKS.md`

Tasks:

- Add per-element target boundaries and nested immutable owner sequence maps.
- Deep-copy/restore both dimensions and route resolver damage through policy.
- Regress timing, owner/element independence, side effects, and snapshots.

Acceptance criteria:

- Same-element target attempts before 0.1 seconds deal no damage and start no
  owner state; exact boundary is accepted.
- One owner/element deals first/two target-passing hits, not third; exact 0.5
  reset is accepted. Another owner and another Element are independent.
- Every blocked attempt still notifies and consumes the current Aura fixture.
- Snapshot restore reproduces active target and owner decisions.
- Existing dual-element Swirl and B-041 VV behavior remain unchanged.

Test cases to add or update:

- Normal: owner entries one/two and exact 0.5-second reset.
- Abnormal: same-element target block and same-owner third attempt.
- Independence: second owner and Hydro versus Pyro state.
- Side effect: listener count and half-gauge consumption on blocked paths.
- Snapshot: restore active target and fixed owner window.
- No-change: simultaneous Electro/Hydro priority and VV fixtures pass.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- routed validation/preflight planning without RL execution

Completion evidence:

- `ReactionState` now owns typed per-Element target GCD boundaries and nested
  per-Element/per-`CharacterId` fixed owner windows. Snapshot save, merge
  forwarding, restore, and all public access route through defensive copies.
- Resolver ordering preserves reaction notification and half-gauge Aura
  consumption before the damage-only policy. A target-blocked attempt leaves
  owner state untouched, while an owner-blocked attempt advances the target GCD.
- Focused actual-action coverage proves the 0.05-second target block, exact
  0.1-second acceptance, first/two/third owner behavior, exact 0.5-second reset,
  second-owner and Hydro/Pyro independence, nine retained notifications, blocked
  half-gauge consumption, and snapshot rewind/replay.
- The pre-existing same-Element VV fixture now performs its second Swirl at the
  exact target boundary; its first-hit resistance-order contract is unchanged.
  `ReactionRegressionTest`, `build`, and `javadoc` pass. Routed preflight and
  agent validation report the expected RL checks, which were not executed under
  the simulator-only session boundary.

### Phase 3: Accept Swirl-Sequence Catalog Baselines - Done

Why third:

- Both Flins catalogs use Sucrose Swirls, so acceptance follows focused timing
  and snapshot verification.

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls per catalog party against B-070.
- Record hashes, totals, ER/cadence, Swirl counts, and warning lines.
- Attribute every delta or prove exact no-change.

Acceptance criteria:

- All pairs are deterministic and every delta is attributable.
- Tracked generated report is restored and no artifact is staged.
- Plan, ledger, README, and checkpoint agree.

Test cases to add or update:

- Normal: pairwise exact catalog controls.
- No-change: accepted totals/ER/cadence and valid dual-element Swirls.
- Abnormal: zero warning/generated-artifact leak.

Verification:

- two fresh `./gradlew --no-daemon RaidenParty` runs
- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two normalized payloads per party match exactly: RaidenParty
  `86a70a9357148363fcc465e648accb749cc774a3a3adf8c0aac35a583c37e601`,
  FlinsParty
  `2d530f72e3cf4d0d6ee6209ef68dff6cf1454707fd3b5e43fb21e249a682ed68`,
  and FlinsParty2
  `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
- RaidenParty remains exactly 1,304,576 / 62,123 with B-070 event counts and
  100/175/179/174% ER. FlinsParty2 remains exactly 15,817,125 / 228,902 with 35
  accepted Swirls, 105 immediate Lunar reactions, 33 ticks, and
  130/128/100/196% ER.
- FlinsParty retains all 58 Swirl notifications while five damage instances are
  blocked by the new policy. The 36,413 delta is isolated to Sucrose, producing
  22,639,410 / 227,532; 172 immediate Lunar reactions, 48 ticks, artifact
  allocation, and 109/100/100/180% ER are unchanged.
- All six runs contain zero warning, error, failed-action, or insufficient-energy
  matches. The tracked generated report was restored and no generated output is
  staged.

## Implementation Order: Standard Electro-Charged Damage Cooldown

Status: Phases 1-3 are complete. The snapshot-safe standard Electro-Charged
target damage cooldown and deterministic catalog baselines are accepted.

Scope:

- One-enemy 0.5-second standard Electro-Charged damage cooldown.
- Successful-damage time shared by immediate, periodic, and premature ticks.
- Blocked damage retains reaction/Aura/timer effects and consumes no tick gauge.
- Snapshot save/restore and deterministic catalog acceptance.

Out of scope for this pass:

- Adjacent-target AoE, synchronization, ownership transfer, or per-enemy maps.
- Lunar-Charged behavior, hitlag, network-delay findings, and gauge-class wane.
- RL behavior, protocol/tensor changes, training, and persistent jobs.

Definitions:

- **Damage cooldown**: target-wide interval beginning only when standard
  Electro-Charged damage succeeds; exact 0.5 seconds is accepted.
- **Successful-damage time**: the last immediate or timer tick that passed the
  cooldown, used by B-056's premature terminal threshold across event restarts.

Cross-cutting rules:

- `ReactionState` owns cooldown/last-damage primitives; controller/facade inject
  current time and snapshots preserve both values.
- The resolver and scheduler gate damage only after reaction side effects that
  remain legal; blocked timer ticks do not consume either Aura.
- Standard and Lunar paths remain explicit and independent.
- Preserve explicit staging and generated-artifact safety.

### Phase 1: Record Standard EC Damage-Cooldown Evidence - Done

Why first:

- B-069 removes active-refresh damage, leaving sequence restarts as the bounded
  single-target path where the target cooldown is still observable.

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record target cooldown, exact reset, and no-consumption findings.
- Confirm maintained new-sequence/periodic attack tags and fixed timer group.
- Inventory B-056/B-069 timing, state, snapshot, and regression boundaries.

Acceptance criteria:

- Damage-only gating, cross-sequence persistence, successful-tick timing, and
  single-target limitations are explicit.
- No production behavior changes occur in this phase.

Test cases to add or update:

- No production test; Phase 2 owns focused cooldown behavior.

Verification:

- inspect KQM Electro-Charged ICD and gauge-consumption evidence
- inspect maintained gcsim EC attack tags and ReactionB reset timer
- inspect resolver, scheduler, reaction state/controller, and snapshots
- `python scripts/preflight.py --run`

Completion evidence:

- KQM v2.3 records one EC damage instance per enemy in about 0.5 seconds and
  confirms blocked ticks consume no gauge.
- Maintained gcsim assigns `ICDTagECDamage`/`ICDGroupReactionB` to the new
  sequence attack and reuses that attack for periodic ticks; ReactionB resets
  after 30 frames and gauge wane follows only nonzero damage.
- Current B-056 local `lastDamageTime` is discarded when its event finishes, so
  a new sequence can bypass the prior target damage boundary.

### Phase 2: Implement Snapshot-Safe Standard EC Damage Cooldown - Done

Why second:

- Phase 1 establishes one shared damage decision while preserving B-069's
  separate active-refresh suppression and typed ownership payload.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/mechanics/rl/CapabilityProfiler.java` (snapshot forwarding only)
- `TASKS.md`

Tasks:

- Store target cooldown end and last successful standard EC damage time.
- Gate new-sequence immediate, nominal, and premature damage through one policy.
- Consume tick gauge only on successful timer damage and preserve event timing.
- Round-trip both primitives and regress B-056/B-069 behavior.

Acceptance criteria:

- A sequence restart before 0.5 seconds notifies/applies Aura/starts its timer
  but deals no immediate damage; exact 0.5 seconds deals damage.
- Blocked timer damage consumes neither Hydro nor Electro.
- Premature threshold uses the last successful damage across event restarts.
- Snapshot restore reproduces pre/exact decisions.
- B-069 owner/EM attribution and Lunar behavior remain unchanged.

Test cases to add or update:

- Normal: new-sequence and exact 0.5-second acceptance.
- Abnormal: sequence restart just before reset has zero immediate damage.
- Side effect: blocked restart still notifies, reapplies Aura, and starts timer.
- Gauge: blocked timer attempt leaves both values unchanged except natural decay.
- Snapshot: active target boundary and successful-damage time restore exactly.
- No-change: B-056 expiry, B-069 ownership, live RES, and Lunar fixtures pass.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- routed validation/preflight planning without RL execution

Completion evidence:

- `ReactionState` owns a 0.5-second target cooldown end and last successful
  standard EC damage time. Controller/facade methods inject simulator time and
  snapshots round-trip both primitives independently of the typed owner payload.
- A new sequence checks the target cooldown before scheduling. Blocked damage
  still retains prior notification, source Aura application, latest owner/EM
  payload, and a timer starting one second from the new sequence.
- Nominal and premature timer ticks share the same target policy. Only accepted
  damage records a tick, advances last-success time, and consumes 0.4U from both
  Auras; blocked attempts leave the cooldown and both gauges unchanged.
- Focused regression covers a restart at 0.4 seconds after the prior tick,
  exact 0.5-second acceptance, unchanged notification/Aura/owner/timer effects,
  restart-relative next-tick timing, blocked nominal gauge consumption, and
  snapshot pre/exact replay.
- B-056 premature expiry, B-069 owner/EM attribution, B-048 live RES, and Lunar
  fixed-cadence regressions continue to pass.
- `./gradlew ReactionRegressionTest`, `./gradlew build`, and `./gradlew javadoc`
  pass. Routed validation reports no leaks; RL-routed catalog/rollout checks were
  not run under this session's explicit simulator-only boundary.

### Phase 3: Accept Standard EC Cooldown Catalog Baselines - Done

Why third:

- Catalog acceptance follows focused cross-sequence and snapshot verification.

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls per catalog party against B-069.
- Record hashes, totals, ER/cadence, event counts, and warning lines.
- Attribute every standard-party delta and prove Lunar parties unchanged.

Acceptance criteria:

- All pairs are deterministic and every delta is attributable, or exact
  no-change is proven.
- Tracked generated report is restored and no artifact is staged.
- Plan, ledger, README, and checkpoint agree.

Test cases to add or update:

- Normal: pairwise exact catalog controls.
- No-change: accepted Lunar totals/ER/cadence and event counts.
- Abnormal: zero warning/generated-artifact leak.

Verification:

- two fresh `./gradlew --no-daemon RaidenParty` runs
- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh no-daemon runs per party are pairwise exact after removing only the
  Gradle elapsed-success line. Normalized SHA-256 is
  `86a70a9357148363fcc465e648accb749cc774a3a3adf8c0aac35a583c37e601`
  for Raiden; Flins and Flins2 retain B-069 hashes
  `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`
  and `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
- Raiden changes from 1,307,990/62,285 to 1,304,576/62,123. One
  Xingqiu-owned new-sequence immediate hit is blocked by the target cooldown
  after a preceding periodic tick. Immediate counts change 8 to 7, target block
  counts 0 to 1, while all 13 active refresh deferrals and 11 ticks remain.
- Raiden ER remains Bennett/Raiden/Xingqiu/Xiangling 100/175/179/174%.
  Flins and Flins2 remain 22,675,823/227,898 and 15,817,125/228,902 with
  byte-identical ER/cadence and Lunar breakdowns.
- Action/reaction/DoT/ordinary-ICD counts remain 152/55/11/38,
  613/230/48/88, and 468/140/33/71. All six logs contain zero
  warning/error/failed-action/insufficient-energy matches.
- README documents the shared target cooldown and accepted baselines. The
  tracked generated report was restored and no generated output is staged.

## Implementation Order: Standard Electro-Charged Refresh Ownership

Status: Phases 1-3 are complete. Snapshot-safe standard Electro-Charged refresh
ownership and deterministic catalog baselines are accepted.

Scope:

- Active standard Electro-Charged reapplications refresh the next tick owner.
- The latest owner's pre-resistance damage snapshot supplies the next tick.
- Active reapplications continue reaction notification and coexisting Aura
  application without recording a second immediate damage instance.
- Snapshot save/restore and deterministic catalog acceptance.

Out of scope for this pass:

- Lunar-Charged/Thundercloud timing, ownership, weighting, or reporting.
- Multi-target Electro-Charged spread and target-wide reaction damage ICD.
- Hitlag behavior, adjacent-target ownership, RL behavior, and persistent jobs.

Definitions:

- **Active refresh**: a standard Electro-Charged reaction while the one-enemy
  standard Electro-Charged periodic event is already active.
- **Tick payload**: immutable latest-owner state containing `CharacterId` and
  pre-resistance damage, updated by every standard application.

Cross-cutting rules:

- `ReactionState` owns the typed payload; the scheduler owns periodic timing and
  reads the current payload only at impact.
- The resolver distinguishes reaction notification/Aura effects from immediate
  damage, and does not infer ownership from display strings.
- Controller/facade methods mediate mutable state; snapshots preserve payload
  independently of the intentionally omitted pending event queue.
- Standard and Lunar paths remain explicit rather than sharing payload policy.
- Preserve explicit staging and generated-artifact safety.

### Phase 1: Record Electro-Charged Refresh Evidence - Done

Why first:

- Current code captures the first trigger's damage in a timer closure and also
  damages on every refresh, so timing and ownership must be fixed together.

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record the maintained ownership, EM refresh, and single-active-sequence rules.
- Inventory resolver, scheduler, reaction-state, snapshot, and regression paths.
- Fix standard/Lunar and single-target/multi-target boundaries before code edits.

Acceptance criteria:

- Refresh side effects, suppressed immediate damage, next-tick ownership, and
  live impact resistance are explicit.
- No production behavior changes occur in this phase.

Test cases to add or update:

- No production test; Phase 2 owns focused timing and ownership behavior.

Verification:

- inspect KQM Electro-Charged ownership, EM snapshot, and ICD evidence
- inspect maintained gcsim Electro-Charged timer and payload refresh path
- inspect resolver, scheduler, snapshot, controller, and regression paths
- `python scripts/preflight.py --run`

Completion evidence:

- KQM records the initial tick under the triggering character and later ticks
  under the latest character to apply an elemental source before that tick.
- KQM's EM snapshot finding shows that reapplication updates subsequent damage.
- Maintained gcsim updates the active Electro-Charged attack snapshot on every
  reaction but starts immediate/timer damage only for a newly created sequence.
- The current timer closure instead retains the first pre-resistance value and
  records periodic damage under the untyped `Thundercloud` display source.

### Phase 2: Implement Snapshot-Safe Refresh Ownership - Done

Why second:

- Phase 1 establishes one coherent owner/damage payload without changing Lunar
  behavior or the B-056 premature-expiry timing contract.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/mechanics/rl/CapabilityProfiler.java` (snapshot forwarding only)
- `TASKS.md`

Tasks:

- Add immutable standard Electro-Charged owner/pre-resistance payload state.
- Update it on every standard reaction and read it at each periodic impact.
- Suppress active-refresh immediate damage while preserving notification/Aura.
- Attribute standard ticks to typed owner identity and round-trip the payload.

Acceptance criteria:

- A new standard sequence deals its existing immediate hit and first timed tick.
- Active refreshes deal no immediate damage even after 0.5 seconds.
- The next tick uses the latest owner's EM snapshot and damage attribution.
- Live Electro RES is still resolved at impact and B-056 expiry behavior passes.
- Snapshot restore reproduces the typed payload; Lunar behavior is unchanged.

Test cases to add or update:

- Normal: low-EM initial owner followed by high-EM refresh owner before tick.
- Normal: a later refresh replaces owner and pre-resistance damage again.
- Abnormal: repeated active refreshes do not add immediate damage.
- Side effect: every refresh still notifies and applies the triggering Aura.
- Snapshot: owner and pre-resistance payload survive save/mutate/restore.
- No-change: premature Aura expiry, live RES, and Lunar cadence fixtures pass.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- routed validation/preflight planning without RL execution

Completion evidence:

- `ReactionState.StandardElectroChargedState` immutably holds the latest typed
  owner and pre-resistance damage; controller/facade and snapshot paths preserve
  it without coupling standard ownership to Lunar Thundercloud state.
- The scheduler updates the payload on every standard application, reads it at
  periodic impact, applies live Electro RES, and clears it when the sequence
  finishes. Standard periodic damage is credited to `CharacterId` rather than
  the untyped `Thundercloud` display source.
- The resolver defers only active standard refresh damage after notification,
  while the scheduler still reapplies the triggering Aura and updates payload.
  New standard sequences and all Lunar immediate damage retain existing paths.
- Focused regression covers low-to-high EM ownership replacement, active
  refreshes at 0.2 and 0.6 seconds, three notifications, continuing Aura,
  owner-specific damage attribution, and payload save/mutate/restore.
- Existing B-056 premature-expiry and B-048 live-RES fixtures pass, as does the
  existing Lunar fixed two-second fixture.
- `./gradlew ReactionRegressionTest`, `./gradlew build`, and `./gradlew javadoc`
  pass. Routed validation reports no leaks; RL-routed catalog/rollout checks were
  not run under this session's explicit simulator-only boundary.

### Phase 3: Accept Electro-Charged Catalog Baselines - Done

Why third:

- `RaidenParty` exercises standard Electro-Charged frequently, so focused tests
  must pass before accepting its attribution and total changes.

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls per catalog party against B-068.
- Record hashes, totals, ER/cadence, reaction counts, and warning lines.
- Attribute every standard-party delta and prove Lunar parties unchanged.

Acceptance criteria:

- All pairs are deterministic and every delta is explained by active refreshes
  or standard tick ownership; Lunar catalog payloads remain byte-identical.
- Tracked generated report is restored and no artifact is staged.
- Plan, ledger, README, and checkpoint agree.

Test cases to add or update:

- Normal: pairwise exact catalog controls and expected Raiden attribution delta.
- No-change: exact Flins/Flins2 Lunar totals, cadence, and event counts.
- Abnormal: zero warning/generated-artifact leak.

Verification:

- two fresh `./gradlew --no-daemon RaidenParty` runs
- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh no-daemon runs per party are pairwise exact after removing only the
  Gradle elapsed-success line. Normalized SHA-256 is
  `dc46bf544a8c07c2db8177bf1f9f4b8114bd7bd6e4f29fdd35230823694b2ac0`
  for Raiden; Flins and Flins2 exactly retain B-068 hashes
  `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`
  and `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`.
- Raiden changes from 1,352,375/64,399 to 1,307,990/62,285. Its 21 standard
  immediate/reapplication damage lines become eight new-sequence immediate hits
  plus thirteen deferred active refreshes; all eleven periodic ticks remain.
  Removing untyped `Thundercloud` attribution and crediting those ticks to the
  latest owner fully explains the per-character breakdown. ER remains
  Bennett/Raiden/Xingqiu/Xiangling 100/175/179/174%.
- Flins and Flins2 remain 22,675,823/227,898 and 15,817,125/228,902. Their
  ER/cadence and Lunar breakdowns are byte-identical to B-068.
- Action/reaction/DoT/ordinary-ICD counts remain 152/55/11/38,
  613/230/48/88, and 468/140/33/71. All six logs contain zero
  warning/error/failed-action/insufficient-energy matches.
- README documents standard refresh ownership and accepted baselines. The
  tracked generated report was restored and no generated output is staged.

## Implementation Order: Shatter Damage Sequence

Status: Phases 1-3 are complete. Snapshot-safe Shatter target/owner damage
sequences and their deterministic catalog baselines are accepted.

Scope:

- One-enemy 0.2-second target-wide Shatter damage GCD.
- Per-`CharacterId` fixed 0.5-second damage window accepting two hits.
- Continued notification and the simulator's existing whole-Freeze clear.
- Snapshot save/restore and deterministic catalog acceptance.

Out of scope for this pass:

- Partial Frozen durability, trigger residuals, poise-scaled reduction, Freeze
  resistance, hitlag extension, adjacent targets, and multi-target damage caps.
- Superconduct behavior changes, other reaction sequences, RL changes, and
  persistent jobs.

Definitions:

- **Target GCD**: the earliest time any owner may enqueue the next Shatter
  damage attempt on the one modeled enemy.
- **Owner sequence**: the same generic fixed-window policy used by B-067;
  entries one and two damage, later target-passing entries do not until reset.

Cross-cutting rules:

- `ReactionState` owns reaction-specific target fields and reusable immutable
  owner payloads; resolver state does not leak into the policy.
- Controller/facade inject current time; snapshots preserve Shatter separately
  from Superconduct.
- Notify and clear Freeze before damage gating. Target-blocked attempts do not
  mutate owner state; owner-blocked attempts still start the next target GCD.
- Exact 0.2/0.5-second boundaries are inclusive.
- Preserve explicit staging and generated-artifact safety.

### Phase 1: Record Shatter Damage-Sequence Evidence - Done

Why first:

- Shatter's target GCD differs from Superconduct and its existing Freeze clear
  must remain an effect rather than becoming damage-conditional.

Target files:

- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Record KQM's two-hit/0.5-second finding and maintained target GCD.
- Confirm maintained event/reduction-before-damage order and owner sequence.
- Inventory the current resolver and snapshot boundaries.

Acceptance criteria:

- Damage-only suppression, both timing dimensions, and current Freeze-clear
  simplification are explicit.
- No production behavior changes occur in this phase.

Test cases to add or update:

- No production test; Phase 2 owns focused sequence behavior.

Verification:

- inspect KQM Shatter Damage ICD and maintained gcsim Freeze/ICD paths
- inspect resolver, reaction state/controller, snapshot, and regression paths
- `python scripts/preflight.py --run`

Completion evidence:

- KQM v1.5 records at most two Shatter damage instances within 0.5 seconds.
- Maintained gcsim emits the reaction and reduces Frozen before one target
  `shatterGCD` of 0.2 seconds, then uses owner-specific `ReactionA` entries
  one/two followed by zeros until its fixed timer reset.
- The current resolver notifies, damages, and only then clears all typed Freeze.
  Phase 2 must move the existing clear before a damage-only policy decision.

### Phase 2: Implement Snapshot-Safe Shatter Damage Sequence - Done

Why second:

- Phase 1 establishes reuse of the fixed owner-window state without coupling
  Shatter and Superconduct target clocks.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/sample/ReactionRegressionTest.java`
- `src/java/mechanics/rl/CapabilityProfiler.java` (snapshot forwarding only)
- `TASKS.md`

Tasks:

- Generalize B-067's immutable owner payload name and shared fixed-window helper.
- Add separate Shatter target/owner state with snapshot round trip.
- Clear Freeze before the Shatter damage-only decision and regress boundaries.

Acceptance criteria:

- Target attempts before 0.2 seconds deal no damage and start no owner window.
- One owner deals first/two target-passing hits, not third; another owner is
  independent, and exact owner reset is accepted.
- Every blocked attempt still notifies and clears the reapplied Freeze fixture.
- Snapshot restore reproduces target and owner decisions.
- Superconduct sequence behavior remains unchanged.

Test cases to add or update:

- Normal: owner hits one/two and exact 0.5-second reset from a snapshot.
- Abnormal: 0.1-second cross-owner target block and same-owner third hit.
- Side effect: listener count and `isFrozen` prove blocked Shatter clears state.
- Snapshot: restore after owner hit two and exercise exact reset.
- No-change: B-067 focused sequence continues to pass.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- routed validation/preflight planning without RL execution

Completion evidence:

- B-067's immutable owner payload is now reaction-neutral
  `FixedDamageSequenceState`; one private helper advances and restores each
  reaction's separate owner map while target clocks remain explicit.
- Shatter owns a snapshot-safe 0.2-second target boundary and owner fixed-window
  map. Notification and the existing whole-Freeze clear precede damage gating.
- Focused regression covers 0.1/0.2-second target timing, owner entries
  one/two/three, independent owner state, owner-blocked target-GCD advancement,
  six unchanged notifications, and Freeze clear after every blocked path.
- Restore after owner entry two reproduces the active target block and accepts
  the original owner's reset at exactly 0.5 seconds. B-067 Superconduct tests
  continue to pass through the generic payload.
- `./gradlew ReactionRegressionTest`, `./gradlew build`, and `./gradlew javadoc`
  pass. Routed validation reports no leaks; RL-routed catalog/rollout checks were
  not run under this session's explicit simulator-only boundary.

### Phase 3: Accept Shatter-Sequence Catalog Baselines - Done

Why third:

- Catalog acceptance follows focused sequence and snapshot verification.

Target files:

- `README.md`
- `BACKLOG.md`
- `TASKS.md`

Tasks:

- Run two no-daemon controls per catalog party against B-067.
- Record hashes, totals, ER/cadence, Shatter counts, and warnings.
- Document the damage-only sequence and close B-068.

Acceptance criteria:

- All pairs are deterministic and every delta is attributable, or exact
  no-change is proven.
- Tracked generated report is restored and no artifact is staged.
- Plan, ledger, README, and checkpoint agree.

Test cases to add or update:

- Normal: pairwise exact catalog controls.
- No-change: accepted totals/ER/cadence and non-Shatter paths.
- Abnormal: zero warning/generated-artifact leak.

Verification:

- two fresh `./gradlew --no-daemon RaidenParty` runs
- two fresh `./gradlew --no-daemon FlinsParty` runs
- two fresh `./gradlew --no-daemon FlinsParty2` runs
- `python scripts/preflight.py --run`

Completion evidence:

- Two fresh no-daemon runs per party are pairwise exact after removing only the
  Gradle elapsed-success line. Normalized SHA-256 remains
  `03301ef5a3d650a91cfa07660cb0077d8c1585da04d79803a97f36e8249ba85a`
  for Raiden, `9b0b3556ca8f4eb799e6965156aab3bc70e512c7056cdf7e0202572c3996e464`
  for Flins, and `23dc585acc02d3bd7bca7fe3f5b65db62b3e1489fcedb12a02b9725b774b7dd4`
  for Flins2.
- Totals/DPS remain 1,352,375/64,399, 22,675,823/227,898, and
  15,817,125/228,902. Action/reaction/DoT/ordinary-ICD counts remain
  152/55/11/38, 613/230/48/88, and 468/140/33/71.
- None of the catalog parties can trigger Shatter. All six logs contain zero
  Shatter and warning/error/failed-action/insufficient-energy matches.
- README documents damage-only sequence behavior. The tracked generated report
  was restored and no generated output is staged.

## Implementation Order: Favonius Weapon Family Content Campaign

Status: Complete. Shared R1-R5 Windfall and all five Favonius family members are
verified and pushed.

Scope:

- Preserve `FavoniusCodex` behavior while moving its duplicated Windfall state
  and proc policy into one weapon-owned base class.
- Add Lv. 90 Favonius Sword, Greatsword, Lance, and Warbow with typed weapon
  categories, R1-R5 passive values, injectable random draws, and focused tests.
- Use the existing one-enemy, expected-damage simulator and neutral-particle
  distribution contract without changing energy formulas.

Out of scope for this pass:

- New characters or parties solely to equip every weapon category.
- Incoming-damage, multi-target, report, optimizer, capability-profile, and RL
  contract changes.
- Generated `docs/` output.

Definitions:

- **FavoniusWeapon**: shared abstract weapon implementation that owns validated
  refinement scaling, deterministic-testable CRIT draws, Windfall cooldown, and
  neutral particle generation.
- **Content unit**: one independently revertible weapon class plus its focused
  static-stat and passive-boundary regression.

### Phase 1: Add the Complete Favonius Weapon Family - Done

Why first:

- Existing Codex behavior provides a proven runtime hook, and extracting it
  before adding variants prevents four copies of mutable cooldown logic.

Target files:

- `src/java/model/weapon/FavoniusWeapon.java` (new)
- `src/java/model/weapon/FavoniusCodex.java`
- `src/java/model/weapon/FavoniusSword.java` (new)
- `src/java/model/weapon/FavoniusGreatsword.java` (new)
- `src/java/model/weapon/FavoniusLance.java` (new)
- `src/java/model/weapon/FavoniusWarbow.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Extract Codex Windfall behavior without changing its default R5 contract.
- Add refinement validation and R1/R5 chance/cooldown boundary coverage.
- Add each missing weapon with its canonical Lv. 90 stats and category.
- Verify every unit before its own implementation commit and reconcile this
  table after four completed content units or 60 minutes.

| Unit | Prerequisite | Focused verification | Status |
|---|---|---|---|
| Shared Windfall + Favonius Codex | Existing damage hook and energy distributor | Codex replay, null/refinement, R1/R5 cooldown | Done (`0b1bbd2`) |
| Favonius Sword | Shared Windfall | Lv. 90 stats, sword type, R1/R5 trigger | Done (`0b5167a`) |
| Favonius Greatsword | Shared Windfall | Lv. 90 stats, claymore type, inherited trigger | Done (`63f5802`) |
| Favonius Lance | Shared Windfall | Lv. 90 stats, polearm type, inherited trigger | Done (`5a4ef84`) |
| Favonius Warbow | Shared Windfall | Lv. 90 stats, bow type, inherited trigger | Done (`9d6d1e5`) |

Checkpoint 1 evidence:

- Shared refinement validation covers ranks 1-5, rejects 0/6 and null draws,
  and preserves deterministic R5 Codex replay.
- R1 equality/failure, success, pre-12-second suppression, and exact cooldown
  expiry pass alongside inherited Windfall checks for all three new weapons.
- `./gradlew ReactionRegressionTest`, `./gradlew build`, and explicit preflight
  pass for every implementation commit; shared and Warbow public Javadoc also
  passes.

Acceptance criteria:

- Existing no-argument and injected-draw Codex construction remains compatible.
- Refinements 1-5 map to 60-100% proc chance and 12-6 second cooldown; values
  outside that range and null draw sources are rejected.
- Every variant exposes its canonical Lv. 90 base ATK, Energy Recharge, display
  name, and `WeaponType`, and shares one Windfall implementation.
- Failed draws permit immediate retry, successful draws enforce the exact
  refinement cooldown, and identical injected sequences reproduce energy.
- No generated artifact is staged and the Java build remains green.

Test cases to add or update:

- Normal: successful R1/R5 CRIT draw generates neutral particles.
- Boundary: draw equality fails and exact cooldown expiry succeeds.
- Abnormal: null draw and refinement 0/6 are rejected.
- Static data: all five family members expose expected stats and weapon types.
- Regression: existing injected Codex sequence remains deterministic.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py`

## Implementation Order: Sacrificial Weapon Family Content Campaign

Status: Complete. Shared R1-R5 Composed behavior and all four Sacrificial family
members are verified and pushed.

Scope:

- Preserve existing R5 `SacrificialSword` constructors and reset behavior while
  extracting one validated refinement-aware Composed implementation.
- Add Lv. 90 Sacrificial Greatsword, Bow, and Fragments with typed categories,
  canonical substats, injectable draws, and focused regressions.

Out of scope for this pass:

- Multi-target hit ordering, non-damaging Skills, charges beyond the existing
  whole-Skill cooldown reset, new characters/parties, RL, and generated docs.

Definitions:

- **SacrificialWeapon**: shared abstract owner of refinement scaling, eligible
  Skill-damage filtering, cooldown-reset draws, and Composed internal cooldown.

### Phase 1: Add the Complete Sacrificial Weapon Family - Done

Why first:

- Existing Sword behavior is already routed through the correct damage hook;
  extraction avoids duplicating mutable cooldown state across three variants.

Target files:

- `src/java/model/weapon/SacrificialWeapon.java` (new)
- `src/java/model/weapon/SacrificialSword.java`
- `src/java/model/weapon/SacrificialGreatsword.java` (new)
- `src/java/model/weapon/SacrificialBow.java` (new)
- `src/java/model/weapon/SacrificialFragments.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Extract Composed and add refinement 1-5 validation without changing R5 Sword.
- Add each missing variant and verify it in an independent implementation commit.

| Unit | Prerequisite | Focused verification | Status |
|---|---|---|---|
| Shared Composed + Sacrificial Sword | Existing damage hook and cooldown state | eligibility, null/refinement, R1/R5 chance and CT | Done (`ba6a19d`) |
| Sacrificial Greatsword | Shared Composed | 565 ATK, 30.6% ER, claymore, inherited reset | Done (`0773001`) |
| Sacrificial Bow | Shared Composed | 565 ATK, 30.6% ER, bow, inherited reset | Done (`ecda078`) |
| Sacrificial Fragments | Shared Composed | 454 ATK, 221 EM, catalyst, inherited reset | Done (`496a79b`) |

Completion evidence:

- Existing R5 positive-Skill, retry, exact 16-second CT, ineligible-hit,
  already-ready, and multi-charge behavior remains green.
- R1 40% equality/success and exact 30-second CT pass; all variants expose
  sourced Lv. 90 metadata and inherit the same reset path.
- Every unit passes `ReactionRegressionTest`, `build`, and preflight; shared and
  final public APIs pass Javadoc with no generated artifact staged.

Acceptance criteria:

- R1-R5 map to 40-80% reset chance and 30/26/22/19/16-second cooldowns;
  invalid refinements and null draws fail fast.
- Only positive Elemental Skill damage may reset the owner's applicable Skill
  cooldown; failed draws permit retry and exact cooldown expiry succeeds.
- All four family members expose canonical Lv. 90 metadata and one shared
  implementation; existing Sword callers remain source-compatible.

Test cases to add or update:

- Normal/boundary: R1 and R5 success, equality failure, pre-CT block, exact CT.
- Abnormal: non-Skill, zero damage, null draw, and refinement 0/6.
- Static/integration: each variant's metadata and inherited cooldown reset.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the batch boundary
- `python scripts/preflight.py`

## Implementation Order: Target-Aura Weapon Content Campaign

Status: Complete. The shared live-Aura base, refinement-aware Dragon's Bane,
Lion's Roar, Rainslasher, and Magic Guide are verified and pushed.

Scope:

- Preserve Dragon's Bane's per-hit pre-reaction Aura behavior while adding
  refinement ranks 1-5.
- Add Lion's Roar, Rainslasher, and Magic Guide with canonical Lv. 90 metadata,
  eligible Aura sets, R1-R5 damage bonuses, and focused direct-damage tests.

Out of scope for this pass:

- Aura changes, snapshot formula changes, multi-target state, new characters or
  parties, RL, optimizer baselines, and generated docs.

Definitions:

- **TargetAuraDamageWeapon**: shared abstract weapon that adds per-hit all-DMG
  bonus only when the current enemy has a live eligible elemental Aura.

### Phase 1: Add Refinement-Aware Target-Aura Weapons - Done

Why first:

- Dragon's Bane already proves the formula hook and pre-consumption lookup;
  extraction keeps all related weapons out of persistent attacker snapshots.

Target files:

- `src/java/model/weapon/TargetAuraDamageWeapon.java` (new)
- `src/java/model/weapon/DragonsBane.java`
- `src/java/model/weapon/LionsRoar.java` (new)
- `src/java/model/weapon/Rainslasher.java` (new)
- `src/java/model/weapon/MagicGuide.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Eligible Aura | Focused verification | Status |
|---|---|---|---|
| Shared base + Dragon's Bane | Hydro/Pyro | R1/R5 metadata, live/expired Aura, invalid refinement | Done (`10b9db9`) |
| Lion's Roar | Pyro/Electro | 510 ATK, 41.3% ATK, sword, eligible/ineligible damage | Done (`4c940ef`) |
| Rainslasher | Hydro/Electro | 510 ATK, 165 EM, claymore, eligible/ineligible damage | Done (`814f2b0`) |
| Magic Guide | Hydro/Electro | 354 ATK, 187 EM, catalyst, R1/R5 damage | Done (`96f48c7`) |

Completion evidence:

- Dragon's Bane preserves R5, pre-reaction lookup, 11-second Aura expiry,
  repeated snapshot hits, and snapshot/effective-stat exclusion; R1 20% passes.
- All new variants expose sourced Lv. 90 metadata and apply only their eligible
  R1/R5 live-Aura multiplier without mutating owner stats.
- Every unit passes `ReactionRegressionTest`, `build`, and preflight; shared and
  final public APIs pass Javadoc with no generated artifact staged.

Acceptance criteria:

- Bonuses resolve from enemy Aura at impact and never enter structural,
  effective, or snapshotted owner stats.
- Refinements 1-5 produce each weapon's sourced bonus progression; 0/6 fail.
- Eligible live Auras apply once, ineligible/expired/no Aura applies none, and
  existing Dragon's Bane R5 callers remain compatible.

Test cases to add or update:

- Normal: each eligible element and R1/R5 direct-damage multiplier.
- Boundary: Aura expiry at impact.
- Abnormal: no Aura, ineligible Aura, and refinement 0/6.
- Static: display name, base ATK, substat, and typed weapon category.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the batch boundary
- `python scripts/preflight.py`

## Implementation Order: Kaeya Character Vertical Slice

Status: Complete. One independently revertible character/data/regression unit
adds Kaeya through C6 within the simulator's one-enemy boundary.

Scope:

- Add stable `CharacterId.KAEYA`, Lv. 90 static data, talent-9 normals, and
  constellation-adjusted Skill/Burst multipliers.
- Implement typed Normal/Charged/Plunge, 2U no-ICD Frostgnaw with 2.67 expected
  particles and one single-target Frozen A4 particle, and snapshot Glacial Waltz.
- Implement C1 conditional Normal/Charged CRIT Rate, C3/C5 talent values, and C6
  fixed additional-icicle hit count plus 15 flat Energy refund.

Out of scope for this pass:

- C2 defeat-driven extension, C4 low-HP shield, A1 healing, sprint stamina,
  moving-target/backhanding optimization, multi-target A4, a catalog party, RL,
  capability profiles, and generated docs.

Definitions:

- **Stationary Burst stand-in**: 13 evenly scheduled hits over eight seconds at
  C0-C5 and 17 at C6, preserving standard ICD and one immutable stat snapshot.

### Phase 1: Add Kaeya Data, Actions, Constellations, and Regression - Done

Why first:

- Existing typed actions, periodic events, enemy Frozen state, and per-action
  bonus stats cover the complete allowed slice without a shared runtime change.

Target files:

- `src/java/model/type/CharacterId.java`
- `src/java/model/character/Kaeya.java` (new)
- `config/characters/Kaeya/Kaeya_Status.csv` (new)
- `config/characters/Kaeya/Kaeya_Multipliers.csv` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Commit `f891f32` adds stable ID 9, aligned Lv. 90/talent configuration,
  typed physical attacks, Frostgnaw, and snapshotted Glacial Waltz.
- Fixed-count Burst scheduling produces exactly 13 C0 or 17 C6 hits without
  duration-boundary drift; C1, C3, C5, C6, and single-target A4 regressions pass.
- `ReactionRegressionTest`, `build`, Javadoc, and preflight pass with no
  generated or deliberately untracked artifact staged.

Acceptance criteria:

- Config and Java identity/data agree; existing numeric IDs remain unchanged and
  Kaeya receives a new stable ID.
- Frostgnaw deals one 2U/no-ICD Skill hit, produces 2.67 Cryo particles plus one
  when the one modeled enemy is Frozen, and captures proper cooldown metadata.
- Glacial Waltz consumes 60 Energy, snapshots once, deals exactly 13 C0 or 17 C6
  standard-ICD Burst hits in eight seconds, and C6 refunds only Kaeya 15 Energy.
- C1 adds 15% CRIT Rate only to Normal/Charged actions against Cryo/Frozen state;
  C3/C5 use level-12 values while C0 uses level-9 defaults.

Test cases to add or update:

- Normal/static: identity round trip, Lv. 90 stats, legal action metadata.
- Skill: no-Aura particle baseline and Hydro-to-Frozen A4 increment.
- Burst/snapshot: C0/C6 hit counts, Energy spend/refund, immutable snapshot flag.
- Constellation boundary: C0/C1 action CRIT bonus and C3/C5 multiplier changes.
- Abnormal: unsupported action throws; excluded C2/C4 behavior is not fabricated.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Switch-Activated Weapon Campaign

Status: Complete. This campaign adds a backward-compatible typed incoming
weapon-switch callback and uses it to complete three switch-state weapons; RL
and generated documentation remain excluded.

Scope:

- Extend weapon switch notification with typed outgoing target and incoming
  owner callbacks while preserving existing two-argument implementations.
- Add The Widsith, Sacrificial Jade, and Thrilling Tales of Dragon Slayers with
  refinement-aware timing, deterministic test injection, and nonstacking buffs.

Out of scope for this pass:

- Player healing/current HP, real per-hit CRIT outcomes, shield state, region
  metadata, other blocked inventory, RL, and generated docs.

### Phase 1: Backward-Compatible Weapon Switch Contract - Done

Target files:

- `src/java/model/entity/SwitchAwareWeaponEffect.java`
- `src/java/simulation/runtime/SwitchManager.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Existing two-argument outgoing implementations continue to compile and are
  invoked exactly once through the compatibility default.
- Outgoing weapons receive the resolved incoming character before the party
  changes; incoming weapons receive exactly one callback after the party
  changes and before the swap delay advances.
- Missing targets and direct `setActiveCharacter` retain existing no-callback
  behavior.

Test cases to add or update:

- Normal: A-to-B swap records outgoing owner/target and incoming owner.
- Boundary: callback observes the expected active character before/after swap.
- Abnormal: missing target, null/no weapon, legacy callback, and direct setter.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Legacy and target-aware outgoing callbacks, incoming callbacks, active-owner
  ordering, missing targets, plain weapons, and direct setters are covered by
  the focused switch contract regression.
- `./gradlew ReactionRegressionTest`, `./gradlew build`, `./gradlew javadoc`,
  and `python scripts/preflight.py` passed on 2026-08-02.

## Implementation Order: Follow-on Legacy Character Campaign

Status: Complete. All three phases are implemented and verified.

Scope:

- Reserve stable typed identities and character-owned buff keys before branch
  isolation.
- Add Rosaria, Diluc, Keqing, Ningguang, and Ganyu as complete stationary
  single-target offensive vertical slices with sourced talent-9 values,
  actions, cooldowns, energy, elemental application, particles, passives, and
  representable constellations.
- Keep each character and focused regression independently revertible and run
  combined reaction/build gates after integration.

Out of scope for this pass:

- Enemy/player geometry, actual random CRIT outcomes, weak points, stamina,
  incoming damage, shields, healing, enemy defeat, multi-target selection, RL,
  generated docs, and every system listed under Deferred Systems.
- Effects whose defining trigger cannot be represented faithfully by current
  typed callbacks; these remain explicit class-level boundaries rather than
  unconditional approximations.

Definitions:

- `RosariaRegressionTest`, `DilucRegressionTest`, `KeqingRegressionTest`,
  `NingguangRegressionTest`, and `GanyuRegressionTest`: focused executable
  regressions covering each new character without extending the shared
  reaction test file.

### Phase 1: Stable Character and Buff Identities - Done

Why first:

- Isolated character branches require immutable typed identities before their
  source and tests can be implemented without central-file conflicts.

Target files:

- `src/java/model/type/CharacterId.java`
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/LegacyCharacterIdentityRegressionTest.java`

Tasks:

- Reserve stable numeric IDs 21-25 for the five B-162 characters.
- Add only the typed buff keys required by representable passives and
  constellations.
- Extend identity regression coverage for display/numeric round trips and
  invalid boundaries.

Acceptance criteria:

- Every new identity resolves bidirectionally without changing IDs 1-20 or the
  UNKNOWN fallback.
- Character branches can express all included timed bonuses and resistance
  reductions without display-string control flow.

Test cases to add or update:

- Normal: display and numeric round trips for IDs 21-25.
- Boundary: existing IDs remain stable and UNKNOWN handles null, unmatched,
  negative, zero, and out-of-range input.

Verification:

- `./gradlew LegacyCharacterIdentityRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py`

Completion evidence:

- Commit `514f7a2` reserves IDs 21-25 and eleven typed buff identities;
  identity, reaction, build, and executable preflight gates pass.

### Phase 2: Rosaria, Diluc, and Keqing Vertical Slices - Done

Why second:

- These independent melee kits exercise reusable Normal/Charged chains,
  infusion windows, periodic Burst damage, party buffs, and resistance debuffs
  against the stable Phase 1 baseline.

Target files:

- `src/java/model/character/Rosaria.java` (new)
- `src/java/model/character/Diluc.java` (new)
- `src/java/model/character/Keqing.java` (new)
- `src/java/sample/RosariaRegressionTest.java` (new)
- `src/java/sample/DilucRegressionTest.java` (new)
- `src/java/sample/KeqingRegressionTest.java` (new)

Tasks:

- Implement each base kit with talent-9 multipliers, sourced action timing,
  cooldown/energy contracts, gauges, ICD, particle travel, and stationary
  single-target Burst cadence.
- Implement ascension passives and constellations whose offensive triggers are
  observable through current simulator hooks; document unsupported branches at
  each class boundary.
- Add focused normal, boundary, abnormal, constellation, switch-state, and
  duplicate-initialization regressions per character.

Acceptance criteria:

- Each character executes every offensive action with a complete sourced timing
  contract without null equipment, rejects constellations outside 0-6, and
  preserves independent runtime state; Diluc's unsourced Charged sequence is an
  explicit evidence boundary.
- Rosaria's Burst/team CRIT and Physical shred, Diluc's three-stage Skill and
  Burst infusion, and Keqing's Stellar Restoration/Burst bonuses obey their
  sourced windows and typed ownership rules within the stated boundaries.

Test cases to add or update:

- Normal: action multipliers/timing, element/gauge/ICD, energy spend, particles,
  periodic hits, infusion, passives, and supported constellations.
- Boundary: exact cooldown/expiry/stack cadence, switch behavior, C0/C6 talent
  levels, and independent simulator/character instances.
- Abnormal: invalid constellation, insufficient energy, action on cooldown,
  wrong owner/action/element callbacks, duplicate initialization, and expired
  state.

Verification:

- `./gradlew RosariaRegressionTest DilucRegressionTest KeqingRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `python scripts/preflight.py`

Completion evidence:

- Commits `259f87e`, `4258902`, `7a8592d`, and `aaff2bc` add the three
  independently revertible character slices and focused regressions.
- All three focused tests, `ReactionRegressionTest`, `build`, Javadoc, and the
  executable preflight pass on the combined tree; Diluc C4 is numerically
  covered as exactly +40%, not a duplicate +80% contribution.

### Phase 3: Ningguang and Ganyu Vertical Slices - Done

Why:

- The remaining ranged kits add owned projectile counters, delayed Skill
  damage, multi-pulse Bursts, and timed field bonuses after the campaign's
  common identity and listener patterns are proven.

Target files:

- `src/java/model/character/Ningguang.java` (new)
- `src/java/model/character/Ganyu.java` (new)
- `src/java/sample/NingguangRegressionTest.java` (new)
- `src/java/sample/GanyuRegressionTest.java` (new)

Tasks:

- Implement Ningguang's Normal/Charged Star Jade ownership, Jade Screen hit,
  and Starshatter projectile sequence with representable constellations.
- Implement Ganyu's Frostflake arrow/bloom, Trail of the Qilin hits, Celestial
  Shower stationary-target cadence, field bonuses, and representable
  constellations.
- Add focused state, timing, application, particle, and invalid-trigger
  regressions, then run the combined campaign gate.

Acceptance criteria:

- Star Jade and Frostflake state cannot leak across characters, simulators, or
  expired windows, and every delayed hit retains typed owner attribution.
- Included passives and constellations change only their documented action,
  element, target, or field windows; geometry and random targeting remain
  explicit boundaries.

Test cases to add or update:

- Normal: counters, charged/bloom multipliers, delayed/periodic hits, energy,
  particles, application, passives, and supported constellations.
- Boundary: counter caps/consumption, exact duration and cooldown expiry,
  switch behavior, projectile cadence, and independent instances.
- Abnormal: invalid constellation, insufficient energy, wrong callback,
  duplicate initialization, expired state, and zero/irrelevant damage events.

Verification:

- `./gradlew NingguangRegressionTest GanyuRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commits `1ca6d6d`, `9117319`, `4f265d2`, and `4555bb1` implement both
  characters and preserve independent Burst, deployable, and delayed-hit
  snapshots after independent review.
- All five campaign regressions, party catalog, reaction regression, build,
  Javadoc, and executable preflight pass on the pushed combined tree.

## Implementation Order: Follow-on Stateful Weapon Campaign

Status: Complete. All four phases and independent review are verified.

Scope:

- Add exact region metadata and action-specific derived-damage stats required
  by the remaining sourced weapon inventory.
- Add Crane's Echoing Call, Lumidouce Elegy, Peak Patrol Song, Sturdy Bone,
  Vivid Notions, Lithic Blade, Lithic Spear, and Chain Breaker with R1-R5
  coefficients and snapshot-safe mutable state.
- Use KQM TCL `80ba6241` and gcsim `ef41805d`, accessed 2026-08-03, while
  preferring the published 16-32% Sturdy Bone coefficient over gcsim's
  inconsistent implementation constant.

Out of scope:

- Stamina consumption, enemy geometry, multi-target selection, incoming
  damage, RL, generated docs, and Deferred Systems. Sturdy Bone's Sprint
  stamina reduction remains excluded because no stamina model exists.

### Phase 1: Region and Derived-Damage Primitives - Done

Requirements:

- Add fail-closed typed character regions without changing numeric identities.
- Route final-ATK Normal additive damage and Plunging-only CRIT DMG through the
  standard formula without affecting other action categories or Lunar damage.

Target files:

- `src/java/model/type/CharacterRegion.java` (new)
- `src/java/model/type/CharacterId.java`
- `src/java/model/type/StatType.java`
- `src/java/mechanics/formula/StandardDamageStrategy.java`
- focused identity and formula regression executables

Tests:

- Normal: Liyue, Natlan, other, and UNKNOWN region lookup; Normal final-ATK
  addition; Plunging CRIT DMG.
- Boundary: zero ratios and non-Normal/non-Plunging actions are unchanged.
- Abnormal: unknown identities fail closed and negative/invalid IDs retain the
  UNKNOWN contract.

### Phase 2: Crane's Echoing Call and Peak Patrol Song - Done

Requirements:

- Implement owner-Plunge team window and 0.7-second ally-Plunge Energy ICD.
- Implement 0.1-second Normal/Plunge stacks and trigger-time final-DEF team
  elemental bonus snapshot, excluding Physical damage.
- Capture and restore all mutable windows, stacks, and ICDs.

Target files:

- two new weapon classes under `src/java/model/weapon/`
- two focused regression executables under `src/java/sample/`

Tests:

- Normal: R1/R5 metadata, team scope, stack values, Energy, and DEF snapshot.
- Boundary: exact 0.1/0.7/6/15/20-second windows and stack caps.
- Abnormal: wrong owner/category, zero-damage contact, duplicate binding,
  cross-instance state, and foreign snapshot restore.

### Phase 3: Lumidouce Elegy and Vivid Notions - Done

Requirements:

- Implement Burning/Dendro-on-Burning stacks with same-hit de-duplication,
  two-stack Energy restore, and twelve-second Energy ICD.
- Implement the two independent Plunging CRIT DMG windows and cancellation
  exactly 0.1 seconds after a Plunging hit.
- Preserve all mutable state through simulator snapshot restore.

Target files:

- two new weapon classes under `src/java/model/weapon/`
- two focused regression executables under `src/java/sample/`

Tests:

- Normal: R1/R5 metadata, eligible trigger paths, stacking, Energy, and
  additive Plunging CRIT DMG windows.
- Boundary: 0.1/8/12/15-second expiry and cooldown behavior.
- Abnormal: duplicate same-hit trigger, derived reaction, wrong owner/element,
  irrelevant action, foreign simulator, and foreign snapshot state.

### Phase 4: Sturdy Bone and Region-Composition Weapons - Done

Requirements:

- Implement Sturdy Bone's 18-hit/seven-second Normal additive window after
  Dash, excluding only the unsupported stamina reduction.
- Share one Lithic composition implementation between Blade and Spear, and
  implement Chain Breaker's Natlan-or-different-element union without double
  counting.

Target files:

- new Sturdy Bone, Lithic base/Blade/Spear, and Chain Breaker classes
- focused regression executables for Sturdy, Lithic family, and Chain Breaker

Tests:

- Normal: R1-R5 metadata, final-ATK addition, Liyue stacks, and union counts.
- Boundary: 18th/19th hit, seven-second expiry, four-stack cap, and exactly
  three qualifying Chain Breaker members.
- Abnormal: wrong action/owner, UNKNOWN region fail-closed, duplicate union
  membership, invalid refinement, and independent instances.

Campaign verification:

- all new focused regressions
- `./gradlew ReactionRegressionTest PartyCatalogRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commits `c68df45` through `4c6df27` add the typed region/formula baseline,
  all eight weapons, exact R1-R5 state, and focused regression executables.
- Independent review corrections use final resolved damage, distinguish a
  reaction's direct hit from separate same-time hits, enforce exact half-open
  boundaries, and cover restored ICD/de-duplication state, R5 composition
  values, and Lunar isolation.
- All ten focused regressions, party catalog, reaction regression, build,
  Javadoc, and executable preflight pass on the pushed combined tree.

## Implementation Order: Legacy Support Character Campaign

Status: Complete. All three offensive vertical slices and both independent
review correction passes are pushed; B-166 owns the shared snapshot follow-up.

Scope:

- Add typed identities and complete loadable data/code slices for Jean,
  Chongyun, and Diona.
- Implement every sourced offensive action, particle, ICD/gauge, passive, and
  constellation branch representable through existing simulator contracts.
- Keep one focused executable per character and one independently revertible
  implementation commit per vertical slice.

Out of scope for this pass:

- Healing, shields and shield durability, incoming damage/counters, stamina,
  movement speed, enemy displacement, geometry/multi-target selection, RL,
  generated docs, and Deferred Systems.
- Approximation of an excluded branch through unrelated Energy, buff, or
  enemy-state APIs.

Definitions:

- `JEAN`, `CHONGYUN`, and `DIONA`: stable typed character identities with
  Mondstadt/Liyue regions and unchanged existing numeric IDs.
- An offensive vertical slice consists of aligned CSV data, one character
  runtime class, representable constellations, and a focused regression.

Campaign inventory:

| Unit | Type | Source readiness | Shared prerequisite | Verification | Status |
|---|---|---|---|---|---|
| Jean | character | pinned KQM/gcsim | typed identity | `JeanRegressionTest` | complete |
| Chongyun | character | pinned KQM/gcsim | typed identity | `ChongyunRegressionTest` | complete |
| Diona | character | pinned KQM/gcsim | typed identity | `DionaRegressionTest` | complete |

### Phase 1: Reserve Typed Identities

Status: Done.

Why first:

- Every isolated class and CSV loader must compile against one published,
  immutable identity baseline.

Target files:

- `src/java/model/type/CharacterId.java`
- `src/java/sample/LegacyCharacterIdentityRegressionTest.java`

Tasks:

- Assign new stable numeric IDs without renumbering any existing identity.
- Record Jean/Diona as Mondstadt and Chongyun as Liyue.
- Extend name, numeric, region, and UNKNOWN fallback regression coverage.

Acceptance criteria:

- All three names and numeric IDs round-trip through `CharacterId` while old
  IDs remain unchanged and invalid input remains `UNKNOWN`.

Test cases to add or update:

- Normal: display-name, numeric-ID, and region lookup for all three identities.
- Boundary: existing Ganyu and UNKNOWN IDs remain stable.
- Abnormal: null name and adjacent unassigned numeric IDs fail closed.

Verification:

- `./gradlew LegacyCharacterIdentityRegressionTest build`

### Phase 2: Jean and Chongyun Isolated Vertical Slices

Status: Done.

Why second:

- Their disjoint character/config/test paths can proceed concurrently after
  Phase 1, while shared runtime remains unchanged.

Target files:

- `src/java/model/character/Jean.java` (new)
- `config/characters/Jean/Jean_Status.csv` (new)
- `config/characters/Jean/Jean_Multipliers.csv` (new)
- `src/java/sample/JeanRegressionTest.java` (new)
- `src/java/model/character/Chongyun.java` (new)
- `config/characters/Chongyun/Chongyun_Status.csv` (new)
- `config/characters/Chongyun/Chongyun_Multipliers.csv` (new)
- `src/java/sample/ChongyunRegressionTest.java` (new)

Tasks:

- Add exact level-90 status and talent-level multipliers with typed Normal,
  Charged, Plunging, Skill, and Burst actions.
- Implement representable particles, fields, infusions, action-speed or
  resistance effects, passives, and constellations without fabricating
  healing, displacement, stamina, incoming hits, or geometry.
- Preserve periodic/delayed state and cooldowns through simulator snapshots
  whenever the selected mechanics create mutable runtime state.

Acceptance criteria:

- Both characters load without fallback data and every included action reports
  the sourced element, category, timing, gauge, ICD, Energy, and cooldown.
- Unsupported defensive/spatial branches remain inert and explicitly covered.

Test cases to add or update:

- Normal: metadata, attack chains, Skill/Burst, particles, passives, and every
  representable constellation.
- Boundary: exact cooldown/duration/cadence, infusion or field ownership,
  switch behavior, and snapshot replay.
- Abnormal: invalid constellation, insufficient Energy, wrong callback,
  duplicate binding, excluded healing/displacement/stamina, and independent
  instances.

Verification:

- `./gradlew JeanRegressionTest ChongyunRegressionTest`
- `./gradlew ReactionRegressionTest PartyCatalogRegressionTest build javadoc`
- `python scripts/preflight.py --run`

### Phase 3: Diona Offensive Vertical Slice

Status: Done.

Why:

- Diona is independent of Phase 2 code but shares the published identity and
  the campaign's explicit shield/healing exclusions.

Target files:

- `src/java/model/character/Diona.java` (new)
- `config/characters/Diona/Diona_Status.csv` (new)
- `config/characters/Diona/Diona_Multipliers.csv` (new)
- `src/java/sample/DionaRegressionTest.java` (new)

Tasks:

- Add exact level-90 status/talent data and typed bow, Skill-paw, and Burst
  impact/periodic offensive actions.
- Implement representable particles, ICD/gauge, field debuffs or buffs,
  passives, and constellations while excluding shields, healing, stamina, and
  projectile geometry.
- Capture every mutable offensive field/cooldown state needed by snapshot
  rollback.

Acceptance criteria:

- Diona's included action sequence, Energy/cooldown gates, periodic cadence,
  particles, and representable constellation effects are deterministic and
  data-aligned.
- Shield/healing/player-state branches do not synthesize unrelated stats or
  callbacks.

Test cases to add or update:

- Normal: metadata, bow chain, Skill variants, Burst impact/ticks, particles,
  passives, and representable constellations.
- Boundary: exact cooldown, duration, periodic cadence, field expiry, and
  snapshot replay.
- Abnormal: insufficient Energy, invalid constellation, excluded shield/heal/
  stamina paths, wrong callback, and independent instances.

Verification:

- `./gradlew DionaRegressionTest`
- `./gradlew ReactionRegressionTest PartyCatalogRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commits `1fbe410` through `3633f1d` add all three identities and offensive
  slices, then correct sourced talent levels, projectile/hitmark timing,
  cast snapshots, particles, Energy/cooldown frames, infusion-at-hit, A1
  integer frame adjustment, exact multipliers, and field recast boundaries.
- Both independent audits converge on one shared limitation: simulator
  snapshot restore clears pending timer events and does not capture mutable
  Normal-chain counters. That cross-character contract is promoted as B-166
  rather than hidden behind character-local marker tests.
- All three focused regressions, identity, party catalog, reaction regression,
  build, Javadoc, and executable preflight pass on the pushed combined tree.

## Implementation Order: Character Snapshot Continuity

Status: Complete. B-166 is implemented and independently audited.

Scope:

- Add one narrow character-state snapshot contract without serializing or
  cloning arbitrary timer-event objects.
- Restore Jean, Chongyun, and Diona Normal-chain counters and reconstruct only
  their sourced future offensive events from immutable character-owned state.
- Keep weapon, reaction, RL, generated docs, and unrelated character behavior
  unchanged.

### Phase 1: Publish Character Snapshot State Contract - Done

Status: Done.

Target files:

- `src/java/model/entity/SnapshotAwareCharacterEffect.java` (new)
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/model/entity/state/EnergyState.java`
- `src/java/sample/CharacterSnapshotContractRegressionTest.java`

Acceptance criteria:

- Snapshot-aware characters capture immutable opaque state and receive it only
  after clock, cooldown, Energy, and buff windows are restored.
- Non-participating characters retain byte-for-byte behavior and null state
  fails closed.
- Runtime and analyzer Energy totals, windows, and markers roll back with the
  current Energy bar; incompatible payloads fail before destructive restore.

Tests:

- Normal: one test character round-trips a counter and re-registers one future
  event.
- Boundary: restore before and at event expiry does not duplicate damage.
- Abnormal: state/type mismatch throws instead of silently corrupting state.

### Phase 2: Migrate B-165 Character State - Done

Status: Done.

Target files:

- `src/java/model/character/Jean.java`
- `src/java/model/character/Chongyun.java`
- `src/java/model/character/Diona.java`
- their three focused regression executables

Acceptance criteria:

- Saving after a Normal and restoring after another branch resumes the saved
  next Normal for all three characters.
- Future Jean exit damage, Diona Burst ticks/C1 refund, and Chongyun particles,
  field refreshes/A4 resume exactly once from the restored time.
- Restoring expired state schedules nothing and repeated restore is idempotent.

Tests:

- Normal: replay each pending event family and compare damage/Energy/timing.
- Boundary: exact half-open field/tick/A4 deadlines and snapshot at deadline.
- Abnormal: independent instances and cross-simulator reuse remain rejected.

Verification:

- `./gradlew JeanRegressionTest ChongyunRegressionTest DionaRegressionTest`
- `./gradlew ReactionRegressionTest PartyCatalogRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commits `b6b8f52` through `db603ba` add the opaque state contract, migrate
  all three characters, and resolve two independent audits covering delayed
  projectiles, multiple particle packets, Energy accounting, superseded Burst
  callbacks, exact-deadline events, explicit empty state, and pre-mutation
  payload validation.
- Character contract and all three focused regressions, reaction regression,
  party catalog, build, Javadoc, executable preflight, and the routed Java
  rollout benchmark pass on 2026-08-03.

## Implementation Order: Qiqi Offensive Vertical Slice

Status: Complete. B-167 is implemented and independently audited.

Scope:

- Add typed Qiqi identity, Lv. 90 status/talent CSV data, complete basic attack
  categories, Herald of Frost's initial and nine snapshot swipes, and Burst.
- Represent C1 Energy while the Burst talisman is active, C2's Cryo/Frozen
  Normal/Charged bonus, and C3/C5 talent levels.
- Reuse the character snapshot contract for combo, summon generation, pending
  swipes, and talisman timing.

Out of scope for this pass:

- Healing and incoming-healing bonuses, A4's random talisman, C4 enemy ATK
  reduction, C6 revival, Witch's Revelation/Polestar/Stellar-Conduct,
  multi-target geometry, RL, and generated docs.

### Phase 1: Add Qiqi Offensive Content Slice - Done

Why:

- Qiqi is the next source-ready missing character whose offensive behavior
  does not require a deferred player-damage, shield, or multi-target system.

Target files:

- `src/java/model/type/CharacterId.java`
- `config/characters/Qiqi/Qiqi_Status.csv` (new)
- `config/characters/Qiqi/Qiqi_Multipliers.csv` (new)
- `src/java/model/character/Qiqi.java` (new)
- `src/java/sample/QiqiRegressionTest.java` (new)

Tasks:

- Adapt pinned gcsim `ef41805d` frame/cadence data and pinned KQM TCL
  `80ba6241` talent/application contracts to the single-target simulator.
- Preserve N3/N4/Charged multi-hit identity, Skill snapshot ownership, recast
  cancellation, Burst talisman timing, and exact typed ICD/gauge metadata.
- Add focused normal, boundary, abnormal, and repeated-restore regressions.

Acceptance criteria:

- All supported actions load from aligned CSV keys and emit exact hit counts,
  multipliers, action categories, timings, elements, and cooldown/Energy state.
- One Skill cast emits one initial hit and nine snapshot swipes; recast and
  repeated restore leave exactly one current stream.
- C1, C2, C3, and C5 activate only at their sourced boundaries, while excluded
  healing/defensive/random effects are not fabricated.

Test cases to add or update:

- Normal: identity/data, full Normal chain, two-hit Charged, Plunge, Skill
  cadence/snapshot, Burst, C1/C2/C3/C5.
- Boundary: exact swipe/talisman expiry, Skill recast, single/double restore,
  Cryo/Frozen versus unrelated Aura.
- Abnormal: cooldown/Energy rejection, wrong state type, cross-simulator
  binding, and excluded effects causing no offensive mutation.

Verification:

- `./gradlew QiqiRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commit `5dda92f` adds Qiqi identity/data, seven-hit Normal chain, two-hit
  Charged Attack, Plunge, classic nine-swipe Skill, Burst talisman, and
  representable C1/C2/C3/C5 behavior.
- Independent review corrected Herald snapshot ownership from cast time to the
  frame-32 initial hit and added pending-initial plus repeated-restore proof.
- `QiqiRegressionTest`, reaction regression, build, Javadoc, and executable
  preflight pass on 2026-08-03.

## Implementation Order: Legacy Catalyst and Counter Character Campaign

Status: Complete. B-168 delivered the shared identities and both independently
revertible offensive character slices.

Scope:

- Add stable typed identities and aligned Lv. 90 status/talent data for Mona
  and Beidou.
- Implement Mona's stationary single-target catalyst actions, Phantom stream,
  Omen damage window, and representable passives/constellations.
- Implement Beidou's ordinary Tidecaller hit and single-target Stormbreaker
  discharge stream with representable passives/constellations.

Out of scope for this pass:

- Alternate sprint/dash, movement, stamina, healing, shields, incoming player
  damage, Tidecaller perfect-counter activation, multi-target bounce/geometry,
  enemy defeat, RL, generated docs, and Deferred Systems.
- Fabricating counter levels, shield state, or additional Stormbreaker bounces
  through unrelated action or damage callbacks.

Definitions:

- `MONA` and `BEIDOU`: stable typed identities using the next unassigned IDs
  and their canonical Mondstadt/Liyue regions.
- An offensive vertical slice consists of aligned CSV data, one character
  runtime class, representable constellations, and a focused executable.

Campaign inventory:

| Unit | Type | Source readiness | Shared prerequisite | Verification | Status |
|---|---|---|---|---|---|
| typed identities | shared | pinned KQM/gcsim | none | `LegacyCharacterIdentityRegressionTest` | done |
| Mona | character | pinned KQM/gcsim | typed identity | `MonaRegressionTest` | done |
| Beidou | character | pinned KQM/gcsim | typed identity | `BeidouRegressionTest` | done |

### Phase 1: Reserve Mona and Beidou Identities - Done

Why first:

- Both isolated content slices need one immutable typed identity baseline before
  their class, CSV, and snapshot state can be reviewed independently.

Target files:

- `src/java/model/type/CharacterId.java`
- `src/java/sample/LegacyCharacterIdentityRegressionTest.java`

Tasks:

- Assign the next stable numeric IDs without renumbering existing characters.
- Record Mona as Mondstadt and Beidou as Liyue.
- Extend display-name, numeric-ID, region, null, and unassigned-ID boundaries.

Acceptance criteria:

- Both identities round-trip by exact name and numeric ID while Qiqi and
  `UNKNOWN` behavior remain unchanged.

Test cases to add or update:

- Normal: name, numeric ID, and region for Mona and Beidou.
- Boundary: Qiqi remains ID 29 and the adjacent unassigned ID fails closed.
- Abnormal: null and case-mismatched names return `UNKNOWN`.

Verification:

- `./gradlew LegacyCharacterIdentityRegressionTest build`

### Phase 2: Mona Offensive Vertical Slice - Done

Why second:

- Mona is self-contained after Phase 1 and her Phantom/Omen state can reuse the
  existing typed action, buff, timer, reaction, and character snapshot APIs.

Target files:

- `config/characters/Mona/Mona_Status.csv` (new)
- `config/characters/Mona/Mona_Multipliers.csv` (new)
- `src/java/model/character/Mona.java` (new)
- `src/java/sample/MonaRegressionTest.java` (new)
- `src/java/model/entity/TargetDependentTeamEffect.java` (new)
- `src/java/mechanics/formula/DamageCalculator.java`
- `src/java/mechanics/formula/StandardDamageStrategy.java`
- `src/java/mechanics/formula/LunarDamageStrategy.java`
- `src/java/simulation/runtime/CombatActionResolver.java`

Tasks:

- Adapt pinned gcsim `ef41805d` timing/cadence data and pinned KQM TCL
  `80ba6241` application/talent contracts to stationary single-target combat.
- Add complete basic attack categories, Phantom damage/particles, Burst impact
  and Omen behavior, and every representable passive/constellation branch.
- Persist mutable combo, summon, and Omen state through repeated snapshot
  restore without duplicating future events.

Acceptance criteria:

- Included actions emit sourced hit counts, multipliers, categories, timing,
  gauge/ICD, particles, cooldown, and Energy behavior from aligned CSV keys.
- Phantom recast, Omen trigger/expiry, and repeated restore leave one current
  state machine; excluded sprint and geometry effects remain inert.

Test cases to add or update:

- Normal: data, attack chain, Charged/Plunge, Skill stream, Burst/Omen, particles,
  and representable passives/constellations.
- Boundary: exact summon/Omen deadlines, recast replacement, target-state
  conditions, and single/double restore.
- Abnormal: cooldown/Energy rejection, invalid constellation/state payload,
  cross-simulator reuse, and excluded sprint/geometry behavior.

Verification:

- `./gradlew MonaRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

### Phase 3: Beidou Offensive Vertical Slice - Done

Why:

- Beidou is independent of Mona code but shares the identity baseline and the
  campaign's explicit incoming-damage and multi-target exclusions.

Target files:

- `config/characters/Beidou/Beidou_Status.csv` (new)
- `config/characters/Beidou/Beidou_Multipliers.csv` (new)
- `src/java/model/character/Beidou.java` (new)
- `src/java/sample/BeidouRegressionTest.java` (new)

Tasks:

- Add the complete N1-N5 Normal string, ordinary zero-counter Tidecaller,
  particles, and one single-target Stormbreaker discharge per eligible trigger.
- Add exact gauge/ICD, cadence, cooldown/Energy, snapshot ownership, and every
  representable passive/constellation without synthesizing incoming hits.
- Restore mutable combo and Burst state without duplicating discharges.

Acceptance criteria:

- Every included action is data-aligned and deterministic, and Stormbreaker
  enforces its sourced trigger cadence and owner snapshot in single-target use.
- Perfect-counter, shield, C2 bounce, and incoming-hit branches remain inactive
  rather than being approximated through unrelated simulator events.

Test cases to add or update:

- Normal: data, N1-N5 attack chain, ordinary Skill, particles, Burst
  cast/discharges, and representable passives/constellations.
- Boundary: exact discharge ICD, Burst expiry, recast, switch, and repeated
  snapshot restore.
- Abnormal: cooldown/Energy rejection, invalid constellation/state payload,
  cross-simulator reuse, and proof excluded counter/shield/bounces do not fire.

Verification:

- `./gradlew BeidouRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commits `117d62b`, `1cc6ec7`, and `d4e0efb` add stable identities, aligned
  data, Mona's bounded catalyst/Omen slice, and Beidou's bounded
  single-target counter-character slice.
- Independent review commit `457b3e3` resolves Omen at live impact time for
  snapshot and non-snapshot attacks, reconstructs Mona's delayed cooldown and
  Energy events, advances Skill event state before listeners, resets Beidou's
  Normal chain on switch-out, and accepts valid zero-damage discharge hits.
- `LegacyCharacterIdentityRegressionTest`, `MonaRegressionTest`,
  `BeidouRegressionTest`, `ReactionRegressionTest`, build, Javadoc, and
  executable preflight pass on 2026-08-03.

## Implementation Order: Collei Reaction Character Campaign

Status: Complete. Both the typed-identity prerequisite and bounded stationary
single-target Collei slice are verified and pushed.

Scope:

- Add Collei's stable typed identity and aligned Lv. 90/talent data.
- Implement the four-shot Normal string, fully charged shot, Plunge, two-pass
  Floral Ring, Cuilein-Anbar field, reaction-driven A1/A4, and representable
  constellations.
- Preserve Skill/Burst snapshots, reaction windows, extension counters, and
  pending damage/particle events across repeated simulator restore.

Out of scope for this pass:

- Projectile travel and collision geometry, weak points, movement, enemy
  grouping, multi-target selection, RL, generated docs, and Deferred Systems.
- Extending typed action inputs, formulas, or ICD runtime contracts when the
  fixed single-target cadence can express the sourced application sequence.

Definitions:

- `COLLEI`: stable Sumeru character identity using numeric ID 32.
- `Collei`: snapshot-aware reaction character whose fixed target model treats
  the active character as inside Cuilein-Anbar and Sprout areas.

### Phase 1: Reserve Collei Identity (Done)

Why first:

- The character class, CSV lookup, event attribution, and regressions require
  one stable identity before the content slice can compile independently.

Target files:

- `src/java/model/type/CharacterId.java`
- `src/java/sample/LegacyCharacterIdentityRegressionTest.java`

Tasks:

- Assign numeric ID 32 without renumbering existing identities and record
  Collei's Sumeru region.
- Extend exact display-name, numeric-ID, region, case, and adjacent-unassigned
  boundaries.

Acceptance criteria:

- Collei round-trips through exact name and numeric ID while Beidou remains ID
  31 and ID 33 still fails closed.

Test cases to add or update:

- Normal: exact name, ID, and Sumeru region.
- Boundary: Beidou ID 31 and unassigned ID 33.
- Abnormal: null and case-mismatched names return `UNKNOWN`.

Verification:

- `./gradlew LegacyCharacterIdentityRegressionTest build`

### Phase 2: Collei Offensive And Reaction Vertical Slice (Done)

Why second:

- Collei can use the Phase 1 identity with existing action, reaction-listener,
  team-buff, timer, particle, ICD metadata, and character-state contracts.

Target files:

- `config/characters/Collei/Collei_Status.csv` (new)
- `config/characters/Collei/Collei_Multipliers.csv` (new)
- `src/java/model/character/Collei.java` (new)
- `src/java/sample/ColleiRegressionTest.java` (new)

Tasks:

- Adapt pinned gcsim `ef41805d` timing/cadence and pinned KQM TCL `80ba6241`
  multiplier, gauge, ICD, snapshot, particle, passive, and constellation data.
- Model the two Skill passes, one particle packet, twelve base Burst leaps,
  exact shared Burst application cadence, A1/C2 Sprout, A4 extension cap, C1
  off-field ER, C3/C5 talent levels, C4 team EM, and one C6 follow-up per cast.
- Advance each pending state before observer notification and reconstruct all
  future Collei-owned events exactly once after repeated restore.

Acceptance criteria:

- Included attacks emit sourced categories, multipliers, hitmarks, durations,
  cooldown/Energy timing, particles, gauge/ICD sequence, and snapshots.
- Reaction-driven Sprout and field extension observe exact window/cap
  boundaries, and excluded geometry does not create extra hits.

Test cases to add or update:

- Normal: data, four-shot chain, Charged/Plunge, two-pass Skill, Burst cadence,
  particles, A1/A4, and C1-C6 representable effects.
- Boundary: shared Burst application sequence, reaction windows, three-second
  extension cap, off-field C1, switch reset, and repeated mid-event restore.
- Abnormal: cooldown/Energy rejection, invalid constellation/state payload,
  cross-simulator reuse, unsupported action, and unrelated reactions.

Verification:

- `./gradlew ColleiRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commits `ab6684f` and `a15a31e` add Collei ID 32/Sumeru, aligned talent
  data, the four-shot bow string, fixed two-pass Skill, particles, Burst field,
  Sprout, and representable C1-C6 effects.
- Independent review fix `9c9f732` aligns release-time snapshots, concurrent
  Skill casts, Sprout replacement/ICD, Burst activation order, listener-time
  restore, delayed C6 capture, exact Plunge data, and completed-state cleanup.
- `LegacyCharacterIdentityRegressionTest`, `ColleiRegressionTest`,
  `ReactionRegressionTest`, build, Javadoc, and executable preflight pass on
  2026-08-03; final independent re-review reports no correctness findings.

## Implementation Order: Classic Klee Character Campaign

Status: In progress. B-170 has typed-identity and shared DEF-reduction
prerequisites followed by one bounded classic Klee slice.

Scope:

- Add Klee's stable typed identity and aligned Lv. 90/talent data.
- Add sourced enemy DEF reduction, capped at 90% and multiplicative with DEF
  ignore, without changing the existing resistance path.
- Implement Klee's classic stationary single-target attacks, A1, Burst, and
  representable C1-C6 effects with deterministic injected random draws.

Out of scope for this pass:

- Hexerei/Witch's Homework changes, actual-CRIT A4 Energy, stamina, blunt
  poise/shatter, projectile/mine geometry, enemy grouping, RL, generated docs,
  and Deferred Systems.
- More than the fixed one-bounce/two-mine target model or unsupported action
  cancel inputs.

Definitions:

- `KLEE`: stable Mondstadt character identity using numeric ID 33.
- `ENEMY_DEF_REDUCTION`: additive enemy DEF reduction clamped to `[0, 0.90]`
  before multiplying the remaining DEF by `(1 - DEF_IGNORE)`.
- `Klee`: snapshot-aware classic kit with injectable random draws and fixed
  stationary one-target Skill/Burst hit selection.

### Phase 1: Reserve Klee Identity

Target files:

- `src/java/model/type/CharacterId.java`
- `src/java/sample/LegacyCharacterIdentityRegressionTest.java`

Requirements:

- Assign numeric ID 33/Mondstadt without renumbering existing identities.
- Move the adjacent unassigned boundary to ID 34 and preserve exact-name lookup.

Tests:

- Normal: exact name, ID, display name, and region round-trip.
- Boundary: Collei ID 32 remains stable and ID 34 fails closed.
- Abnormal: null and case-mismatched names return `UNKNOWN`.

Verification:

- `./gradlew LegacyCharacterIdentityRegressionTest build`

### Phase 2: Add Enemy DEF Reduction Formula

Target files:

- `src/java/model/type/StatType.java`
- `src/java/mechanics/formula/DamageCalculator.java`
- `src/java/mechanics/formula/StandardDamageStrategy.java`
- `src/java/sample/DefenseReductionRegressionTest.java` (new)

Requirements:

- Apply the sourced standard-defense formula with independent DEF reduction and
  DEF ignore factors; clamp reduction to 90% and ignore to 100%.
- Keep zero-reduction outputs bit-for-bit compatible with the existing path and
  do not apply standard DEF to custom Lunar damage.

Tests:

- Normal: 23.3% reduction increases standard damage by the exact formula.
- Boundary: 90% cap, zero reduction, and 60% ignore multiplied by 23.3% reduction.
- Abnormal: negative reduction cannot increase enemy DEF and excessive values
  cannot exceed the cap.

Verification:

- `./gradlew DefenseReductionRegressionTest ReactionRegressionTest build`

### Phase 3: Klee Offensive And Constellation Vertical Slice

Target files:

- `config/characters/Klee/Klee_Status.csv` (new)
- `config/characters/Klee/Klee_Multipliers.csv` (new)
- `src/java/model/character/Klee.java` (new)
- `src/java/sample/KleeRegressionTest.java` (new)

Requirements:

- Adapt pinned gcsim `ef41805d` and KQM TCL `80ba6241` multipliers, release and
  hit frames, gauge/ICD, snapshots, cooldown/Energy timing, and particles.
- Model N1-N3, Charged, High Plunge, two-charge Skill with one bounce/two mines,
  six Burst waves with deterministic injected 30%/50% extra-hit draws, A1's
  four-second Spark gate, and representable C1-C6 effects.
- Apply C2's 23.3% target DEF reduction for ten seconds, stop Burst on switch,
  reconstruct future owned events once after repeated restore, and prevent
  stale generations from emitting damage or Energy.

Tests:

- Normal: data, attacks, Skill charges/particles, Burst distribution, A1, and
  C1-C6 categories, multipliers, buffs, debuff, Energy, and timing.
- Boundary: release snapshots, A1 gate/consumption, C1 pity reset, C2 exact
  expiry, C4 switch, C6 cadence, Burst termination, and repeated restore.
- Abnormal: invalid constellation/random source/state payload, unsupported
  action, cross-simulator reuse, cooldown/Energy rejection, and stale events.

Verification:

- `./gradlew KleeRegressionTest`
- `./gradlew DefenseReductionRegressionTest ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

## Implementation Order: Parallel Foundational Content Campaign

Status: Complete. All inventory units and campaign verification are done.

Scope:

- Add fifteen exact artifact sets whose combat-relevant effects fit
  existing typed stats or whose remaining effects are exploration-only.
- Add all five Royal weapons with exact Lv. 90 metadata and the narrowest
  truthful representation of their Focus passive.
- Add Barbara as a complete representable offensive character slice with CSV
  alignment and explicit healing/defensive exclusions.

Out of scope for this pass:

- Player healing, current HP loss, incoming damage, self-applied Wet, chest or
  Mora pickup, enemy defeat state, stamina, multi-target geometry, RL, and
  generated docs.
- New shared runtime hooks merely to make an otherwise unsupported passive
  appear active.

Campaign inventory:

| Unit | Type | Source readiness | Shared prerequisite | Verification | Status |
|---|---|---|---|---|---|
| Adventurer | artifact | ready | none | `StaticArtifactRegressionTest` | done |
| Lucky Dog | artifact | ready | none | `StaticArtifactRegressionTest` | done |
| Gambler | artifact | ready | none | `StaticArtifactRegressionTest` | done |
| Resolution of Sojourner | artifact | ready | action CRIT routing audit | `StaticArtifactRegressionTest` | done |
| Royal family | weapon batch | delegated evidence | isolated family base | `RoyalWeaponRegressionTest` | done |
| Barbara | character | delegated evidence | typed `CharacterId` and CSV | `BarbaraRegressionTest` | done |
| Static combat-boundary sets | artifact batch | ready | none | `StaticArtifactRegressionTest` | done |
| Static elemental/support sets | artifact batch | ready | none | `StaticArtifactRegressionTest` | done |
| The Exile | artifact | ready | typed sequence marker | `TheExileRegressionTest` | done |

### Phase 1: Low-Rarity Artifact Sets - Done

Why first:

- These independent sets give the primary lane useful work while delegated
  character and weapon branches proceed without shared-file contention.

Target files:

- `src/java/model/artifact/Adventurer.java` (new)
- `src/java/model/artifact/LuckyDog.java` (new)
- `src/java/model/artifact/Gambler.java` (new)
- `src/java/model/artifact/ResolutionOfSojourner.java` (new)
- `src/java/model/type/StatType.java`
- `src/java/mechanics/formula/StandardDamageStrategy.java`
- `src/java/sample/StaticArtifactRegressionTest.java` (new)

Acceptance criteria:

- Adventurer grants flat HP +1,000 and Lucky Dog grants flat DEF +100 without
  fabricating exploration-triggered healing.
- Gambler grants Skill DMG +20% without fabricating enemy-defeat cooldown reset.
- Resolution grants ATK +18% and Charged Attack CRIT Rate +30% through an
  action-category stat that does not affect other attacks.
- Fresh and supplied stat containers, independent instances, canonical names,
  and unrelated-stat isolation are preserved.

Test cases to add or update:

- Normal: canonical names and exact fixed bonuses for every set.
- Boundary: arbitrary negative/positive times and supplied-stat preservation.
- Abnormal: null supplied stats and proof that unsupported exploration/defeat
  effects do not mutate unrelated combat stats.

Verification:

- `./gradlew StaticArtifactRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Four canonical sets preserve fresh/supplied stats and expose exact flat HP,
  flat DEF, Skill DMG, ATK, and Charged-only CRIT values without fabricating
  exploration healing or enemy-defeat cooldown resets.
- Resolution's new typed CRIT stat affects only standard Charged Attack damage;
  Normal, Skill, and Burst formula probes remain unchanged.
- `./gradlew StaticArtifactRegressionTest`, `./gradlew
  ReactionRegressionTest build javadoc`, and `python scripts/preflight.py
  --run` passed on 2026-08-03.

### Phase 2: Royal Weapon Family - Done

Target files:

- `src/java/model/weapon/RoyalWeapon.java` (new)
- `src/java/model/weapon/RoyalLongsword.java` (new)
- `src/java/model/weapon/RoyalGreatsword.java` (new)
- `src/java/model/weapon/RoyalSpear.java` (new)
- `src/java/model/weapon/RoyalGrimoire.java` (new)
- `src/java/model/weapon/RoyalBow.java` (new)
- `src/java/sample/RoyalWeaponRegressionTest.java` (new)

Acceptance criteria:

- All five weapons expose exact names, weapon types, base ATK, ATK secondary
  stat, R5 defaults, independent instances, and R1-R5 validation.
- Focus stacking and reset behavior is implemented only if the current damage
  pipeline exposes the required critical-hit result; otherwise its inactive or
  expected-value boundary is explicit and regression-tested.

Test cases to add or update:

- Normal: table-driven R1/R5 metadata and any representable Focus state.
- Boundary: refinement endpoints, stack cap, and snapshot state when active.
- Abnormal: refinement 0/6, wrong owner/simulator, and unsupported critical-hit
  observation must not invent deterministic reset behavior.

Verification:

- `./gradlew RoyalWeaponRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- All five Royal weapons expose exact Lv. 90 metadata, R1-R5 Focus values,
  five-stack cap, invalid-refinement rejection, and independent family types.
- Focus remains explicitly inactive because average-CRIT damage resolution
  does not expose realized critical hits; regression rejects adding partial,
  unresettable CRIT state through a damage callback.
- `./gradlew RoyalWeaponRegressionTest ReactionRegressionTest build javadoc`
  and the delegated executable preflight passed on 2026-08-03.

### Phase 3: Barbara Offensive Vertical Slice - Done

Target files:

- `src/java/model/character/Barbara.java` (new)
- `config/characters/Barbara/Barbara_Status.csv` (new)
- `config/characters/Barbara/Barbara_Multipliers.csv` (new)
- `src/java/model/type/CharacterId.java`
- `src/java/sample/BarbaraRegressionTest.java` (new)

Acceptance criteria:

- Barbara loads exact base status/talent data and exposes typed Normal,
  Charged, Plunging, Skill, and Burst actions to the representable offensive
  boundary.
- Representable offensive passives and constellations use exact values,
  timing, ICD, gauge, and Energy behavior with CSV/runtime alignment.
- Healing, player damage/Wet state, defensive behavior, stamina, and geometry
  are explicitly inactive rather than approximated through unrelated state.

Test cases to add or update:

- Normal: metadata, action multipliers, element/category, gauge/ICD, Energy,
  and every implemented passive/constellation branch.
- Boundary: cooldown, periodic or duration boundaries, multi-hit ordering, and
  snapshot replay for any stateful offensive effect.
- Abnormal: unsupported action, insufficient Energy, excluded healing/self-Wet
  paths, missing target, and independent instances.

Verification:

- `./gradlew BarbaraRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc PartyCatalogRegressionTest`
- `python scripts/preflight.py --run`

Completion evidence:

- Barbara now has typed identity, aligned Lv. 90/status and talent-level CSV
  data, four Normal steps, Charged and high Plunge attacks, two shared-ICD
  Skill droplets, and a zero-damage 80-Energy Burst.
- C1/C2/C4/C5 and Encore's representable Energy, cooldown, Hydro bonus, and
  duration branches are covered; healing, stamina, self Wet, incoming damage,
  C6 revival, proximity contacts, and pending-event snapshot reconstruction
  remain explicitly outside the current simulator boundary.
- `./gradlew BarbaraRegressionTest PartyCatalogRegressionTest
  ReactionRegressionTest build javadoc` passed after integration on
  2026-08-03; the delegate's `python scripts/preflight.py --run` also passed.

### Phase 4: Static Combat-Boundary Artifact Sets - Done

Why now:

- These five sets are independent of delegated write sets and their active
  combat bonuses require no new runtime callback or mutable state.

Target files:

- `src/java/model/artifact/Berserker.java` (new)
- `src/java/model/artifact/BraveHeart.java` (new)
- `src/java/model/artifact/BloodstainedChivalry.java` (new)
- `src/java/model/artifact/MarechausseeHunter.java` (new)
- `src/java/model/artifact/VourukashasGlow.java` (new)
- `src/java/sample/StaticArtifactRegressionTest.java`

Acceptance criteria:

- Berserker grants CRIT Rate +12%, Brave Heart grants ATK +18%, and
  Bloodstained grants Physical DMG +25% without inventing player-HP,
  enemy-HP, or defeat state.
- Marechaussee grants Normal and Charged Attack DMG +15% without fabricating
  HP-change CRIT stacks.
- Vourukasha grants HP +20% plus Skill/Burst DMG +10% without fabricating
  incoming-damage stacks.
- Canonical names, supplied-stat preservation, independent instances, and
  unrelated-stat isolation remain exact.

Test cases to add or update:

- Normal: all exact fixed bonuses and canonical names.
- Boundary: arbitrary time, fresh/supplied containers, and action-category
  isolation for Marechaussee and Vourukasha.
- Abnormal: null supplied stats and proof that unsupported conditional bonuses
  remain zero.

Verification:

- `./gradlew StaticArtifactRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Five canonical sets expose exact CRIT, ATK, Physical, action-DMG, and HP
  branches while unsupported player-HP, enemy-HP, defeat, stamina, and incoming
  damage conditions remain inactive.
- Formula probes prove Marechaussee affects only Normal/Charged damage and
  Vourukasha affects only Skill/Burst damage; fresh/supplied/null and
  arbitrary-time cases pass.
- `./gradlew StaticArtifactRegressionTest`, `./gradlew
  ReactionRegressionTest build javadoc`, and `python scripts/preflight.py
  --run` passed on 2026-08-03.

### Phase 5: Static Elemental and Support Artifact Sets - Done

Target files:

- `src/java/model/artifact/ArchaicPetra.java` (new)
- `src/java/model/artifact/DefendersWill.java` (new)
- `src/java/model/artifact/CelestialGift.java` (new)
- `src/java/model/artifact/FragmentOfHarmonicWhimsy.java` (new)
- `src/java/model/artifact/MaidenBeloved.java` (new)
- `src/java/sample/StaticArtifactRegressionTest.java`

Acceptance criteria:

- Archaic Petra grants Geo DMG +15%, Defender's Will grants DEF +30%,
  Celestial Gift grants Energy Recharge +20%, Fragment grants ATK +18%, and
  Maiden grants Healing Bonus +15%.
- Crystal pickup, player elemental resistance, Bond of Life, and incoming
  healing effects remain inactive because their required state is outside the
  current simulator contract.
- Canonical names, supplied-stat preservation, independent instances, null
  rejection, and unrelated-stat isolation remain exact.

Test cases to add or update:

- Normal: canonical names and all exact fixed stats.
- Boundary: arbitrary time and fresh/supplied container behavior.
- Abnormal: null supplied stats and zero unsupported conditional branches.

Verification:

- `./gradlew StaticArtifactRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Five canonical sets expose exact Geo DMG, DEF, Energy Recharge, ATK, and
  Healing Bonus values while unsupported pickup, resistance, Bond of Life,
  and incoming-healing branches remain inactive.
- Fresh, supplied, null, negative/positive-time, and unrelated-stat isolation
  checks pass in the shared static artifact regression.
- `./gradlew StaticArtifactRegressionTest ReactionRegressionTest build
  javadoc` and `python scripts/preflight.py --run` passed on 2026-08-03.

### Phase 6: The Exile Energy Sequence - Done

Target files:

- `src/java/model/artifact/TheExile.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/TheExileRegressionTest.java` (new)

Acceptance criteria:

- The Exile grants Energy Recharge +20%; an accepted owner Burst schedules
  exactly three flat-Energy ticks at two, four, and six seconds.
- Every tick grants two Energy to each current party member except the
  equipping owner, bypassing Energy Recharge and respecting Energy caps.
- One typed latest-source marker plus exact sequence-marker identity prevents
  overlapping ticks across refreshes and multiple set holders.

Test cases to add or update:

- Normal: one owner, multiple allies, exact tick values, and owner exclusion.
- Boundary: immediately before/at 2/4/6 seconds, Energy cap, and same-owner
  refresh invalidating old ticks.
- Abnormal: insufficient-Energy Burst, direct unbound/wrong simulator callback,
  multiple wearers, supplied/null stats, and no unrelated particle accounting.

Verification:

- `./gradlew TheExileRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Accepted Bursts schedule exact two, four, and six-second flat-Energy ticks;
  the owner is excluded, allies respect their Energy caps, and no particle
  Energy accounting is fabricated.
- Exact typed-marker identity invalidates stale ticks after same-owner refresh
  or replacement by another wearer, while unbound, wrong-simulator, and
  insufficient-Energy callbacks remain inert.
- `./gradlew TheExileRegressionTest ReactionRegressionTest build javadoc` and
  `python scripts/preflight.py --run` passed on 2026-08-03.

## Implementation Order: Parallel Offensive Content Wave

Status: Complete. Nine artifact sets, three five-star weapons, and Noelle are
integrated on `dev_0` with focused and cross-system verification.

Scope:

- Add nine artifact sets across static boundaries, action windows, and
  reaction mechanics without inventing unsupported healing or HP state.
- Add Primordial Jade Cutter, Staff of Homa, and Engulfing Lightning with
  late-resolved primary-stat conversion.
- Add Noelle's representable offensive character slice.

Out of scope:

- Healing events, current/player HP loss, shield durability, incoming damage,
  stamina, enemy defeat, geometry, exploration, Witch's Homework, RL, and
  generated docs.

### Phase 1: Static Boundary Artifact Batch - Done

Target files:

- `src/java/model/artifact/EchoesOfAnOffering.java` (new)
- `src/java/model/artifact/OceanHuedClam.java` (new)
- `src/java/model/artifact/SongOfDaysPast.java` (new)
- `src/java/model/artifact/UnfinishedReverie.java` (new)
- `src/java/sample/StaticArtifactRegressionTest.java`

Acceptance criteria:

- Echoes and Unfinished Reverie grant ATK +18%; Ocean-Hued Clam and Song of
  Days Past grant Healing Bonus +15%.
- Echoes probability/ping behavior, healing-derived effects, combat-state
  ramping, and Burning proximity remain inactive until their required state is
  modeled; no unrelated bonus is fabricated.
- Names, fresh/supplied containers, null rejection, independent instances,
  arbitrary-time stability, and unrelated-stat isolation are exact.

Test cases:

- Normal: canonical names and exact fixed values for all four sets.
- Boundary: fresh/supplied containers and negative/large simulation times.
- Abnormal: null stats and zero unsupported conditional bonuses.

Verification:

- `./gradlew StaticArtifactRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Echoes of an Offering and Unfinished Reverie expose exact ATK +18%; Ocean-
  Hued Clam and Song of Days Past expose exact Healing Bonus +15%.
- Probability/ping, healing accumulation, combat departure, and Burning
  proximity effects remain zero; fresh/supplied/null, isolation, and
  arbitrary-time checks pass.
- `./gradlew StaticArtifactRegressionTest ReactionRegressionTest build
  javadoc` and `python scripts/preflight.py --run` passed on 2026-08-03.

### Phase 2: HP-Scaling Five-Star Weapons - Done

Target files:

- `src/java/model/weapon/MaxHpScalingWeapon.java` (new)
- `src/java/model/weapon/PrimordialJadeCutter.java` (new)
- `src/java/model/weapon/StaffOfHoma.java` (new)
- `src/java/model/type/StatType.java`
- `src/java/model/stats/StatsContainer.java`
- `src/java/sample/MaxHpScalingWeaponRegressionTest.java` (new)

Acceptance criteria:

- Both weapons expose exact Lv. 90 metadata, R1-R5 HP and conversion values,
  R5 defaults, and invalid-refinement rejection.
- Max-HP ATK conversion resolves from the final stat view, including weapon,
  artifact, flat, and subsequently merged team HP bonuses.
- Staff of Homa's below-half-HP coefficient remains explicit metadata and
  inactive while current player HP is unavailable.

Test cases:

- Normal: table-driven metadata/refinement and final Max-HP conversion.
- Boundary: artifact/team HP merged after weapon stats and arbitrary time.
- Abnormal: refinement 0/6, unrelated stats, independent instances, and no
  fabricated low-HP branch.

Verification:

- `./gradlew MaxHpScalingWeaponRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Primordial Jade Cutter and Staff of Homa expose exact Lv. 90 metadata,
  R1-R5 HP/conversion coefficients, R5 defaults, and validation.
- A typed derived ratio is resolved by final `StatsContainer#getTotalAtk`, so
  weapon, artifact, flat, and later-merged team HP all contribute without
  ordering-dependent mutation; Homa's current-HP branch remains inactive.
- `./gradlew MaxHpScalingWeaponRegressionTest ReactionRegressionTest build
  javadoc` and `python scripts/preflight.py --run` passed on 2026-08-03.

### Phase 3: Noelle Offensive Vertical Slice - Done

Target files:

- `src/java/model/character/Noelle.java` (new)
- `config/characters/Noelle/Noelle_Status.csv` (new)
- `config/characters/Noelle/Noelle_Multipliers.csv` (new)
- `src/java/model/type/CharacterId.java`
- `src/java/sample/NoelleRegressionTest.java` (new)

Acceptance criteria:

- Noelle loads exact base/talent data and exposes typed Normal, Charged,
  Plunging, Skill, and Burst actions with sourced timing, gauge, and ICD.
- Sweeping Time's cast, Geo infusion, duration, and DEF-to-ATK conversion plus
  representable offensive constellations are exact.
- Healing, shield durability, incoming damage, stamina, enemy-defeat duration,
  and geometry remain inactive instead of approximated.

Test cases:

- Normal: metadata, all action categories, Skill/Burst, infusion/conversion,
  and implemented constellation branches.
- Boundary: Energy/cooldown gates, exact form expiry, talent-level and snapshot
  behavior that the current runtime can restore.
- Abnormal: invalid constellation, unsupported paths, independent instances,
  and cross-simulator reuse for bound runtime state.

Verification:

- `./gradlew NoelleRegressionTest PartyCatalogRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Noelle exposes sourced Normal, Charged, high-Plunge, Breastplate, and both
  Sweeping Time cast hits with exact offensive talent data, timing, gauge,
  ICD, Energy, and cooldown boundaries.
- Sweeping Time snapshots final DEF into its timed ATK conversion and grants
  unoverrideable Geo infusion; C2-C6 representable offensive branches and the
  persistent A4 four-hit cooldown counter are covered.
- Healing, shield absorption/destruction, incoming damage, stamina, enemy-
  defeat extension, and geometry remain inactive.
- `./gradlew NoelleRegressionTest PartyCatalogRegressionTest
  ReactionRegressionTest build javadoc` and `python scripts/preflight.py
  --run` passed after integration on 2026-08-03.

### Phase 4: Skill and Burst Window Artifact Batch - Done

Target files:

- `src/java/model/artifact/ADayCarvedFromRisingWinds.java` (new)
- `src/java/model/artifact/NighttimeWhispersInTheEchoingWoods.java` (new)
- `src/java/model/artifact/VermillionHereafter.java` (new)
- focused artifact regression executables
- `src/java/mechanics/buff/BuffId.java` only when typed mutable state is needed

Acceptance criteria:

- Exact two-piece stats and representable owner hit/Skill/Burst windows use
  post-gate typed callbacks, half-open expiry, refresh, and source isolation.
- Witch's Homework, Crystallize shielding, current-HP decrease stacks, and
  other unavailable enhancements remain inactive.

Test cases:

- Normal: accepted triggers and exact values/durations.
- Boundary: trigger ordering, expiry/refresh, swap behavior, and snapshot state.
- Abnormal: rejected actions, wrong owner/simulator, unsupported enhancements,
  and independent instances.

Verification:

- `./gradlew ActionWindowArtifactRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- A Day Carved From Rising Winds, Nighttime Whispers in the Echoing Woods,
  and Vermillion Hereafter expose exact fixed ATK and representable owner hit,
  Skill-use, and Burst-use windows through typed non-stacking buffs.
- Trigger ordering, half-open expiry, refresh, off-field ownership, switch-out
  dispel, snapshot rollback, rejected callbacks, binding, and independent
  instances pass; unavailable progression, shield, Moondrift, and HP-loss
  enhancements remain inactive.
- `./gradlew ActionWindowArtifactRegressionTest ReactionRegressionTest build
  javadoc` passed after integration on 2026-08-03; the delegated executable
  preflight also passed before the clean cherry-pick.

### Phase 5: Reaction Artifact Batch - Done

Target files:

- `src/java/model/artifact/CrimsonWitchOfFlames.java` (new)
- `src/java/model/artifact/ThunderingFury.java` (new)
- `src/java/sample/ReactionArtifactRegressionTest.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/model/type/StatType.java`
- `src/java/mechanics/reaction/ReactionCalculator.java`
- `src/java/simulation/runtime/CombatActionResolver.java`

Acceptance criteria:

- Exact elemental and reaction bonuses are routed to existing typed reaction
  stats; Crimson Witch Skill stacks and Thundering Fury's 0.8-second cooldown
  reduction gate are owner/simulator scoped.
- Post-reaction ordering, off-field eligibility, refresh, and snapshot state
  match the current reaction callback contract.

Test cases:

- Normal: every supported reaction family and Skill trigger.
- Boundary: 10-second/0.8-second windows, stack cap/refresh, off-field owner,
  and exact cooldown reduction.
- Abnormal: NONE/unrelated reactions, wrong owner/simulator, null callbacks,
  and independent instances.

Verification:

- focused artifact regressions
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Crimson Witch exposes exact Pyro/Overload/Burning/Burgeon/Vaporize/Melt
  bonuses and three refreshed 10-second Skill stacks; Thundering Fury exposes
  exact Electro/reaction bonuses and on-field one-second Skill reduction behind
  a half-open 0.8-second gate.
- The shared resolver now routes typed amp and transformative bonuses, and the
  calculator consumes them for Vaporize, Melt, Burning, Overload, and
  Superconduct instead of discarding its existing bonus argument.
- Formula ratios, full Overload resolution, off-field damage eligibility,
  on-field CDR, stack/gate expiry, snapshot rollback, null/wrong binding, and
  independent state pass in `ReactionArtifactRegressionTest`.
- `./gradlew ReactionArtifactRegressionTest ReactionRegressionTest build
  javadoc` and `python scripts/preflight.py --run` passed on 2026-08-03.

### Phase 6: Engulfing Lightning ER Conversion - Done

Target files:

- `src/java/model/weapon/EngulfingLightning.java` (new)
- `src/java/model/type/StatType.java`
- `src/java/model/stats/StatsContainer.java`
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/EngulfingLightningRegressionTest.java` (new)

Acceptance criteria:

- Exact Lv. 90 metadata, R1-R5 conversion/cap/Burst ER values, R5 default,
  and invalid-refinement rejection are exposed.
- ATK% resolves from final ordinary ER above 100%, including ER merged after
  weapon stats, and respects each refinement cap.
- Accepted owner Burst grants ordinary ER for half-open 12 seconds, including
  off-field use and contribution to Emblem/Raiden conversion paths.

Test cases:

- Normal: table-driven metadata, conversion, cap, and accepted Burst ER.
- Boundary: pre/at 12 seconds, refresh, late-merged ER, snapshot rollback, and
  off-field persistence.
- Abnormal: insufficient Burst, refinement 0/6, wrong owner/simulator,
  cross-binding, unrelated stats, and independent instances.

Verification:

- `./gradlew EngulfingLightningRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Engulfing Lightning exposes exact Lv. 90 metadata, every R1-R5 conversion,
  cap, and Burst ER value, R5 default, and invalid-refinement rejection.
- Final ordinary ER drives capped ATK% after artifact/team merges; accepted
  Burst ER lasts on the half-open 12-second window and contributes to both
  Engulfing conversion and Emblem, while non-converting ER remains excluded.
- Refresh, insufficient gate, snapshot rollback, wrong simulator,
  cross-binding, unrelated stats, and independent instances pass.
- `./gradlew EngulfingLightningRegressionTest ReactionRegressionTest build
  javadoc` and `python scripts/preflight.py --run` passed on 2026-08-03.

## Implementation Order: Golden Majesty and Razor Content Wave

Status: Complete. The shared weapon family, legacy artifact boundaries, and
Razor offensive slice are integrated and verified.

Scope:

- Add Summit Shaper, The Unforged, Memory of Dust, and Vortex Vanquisher through
  one snapshot-safe Golden Majesty stack contract.
- Add the four Prayers tiaras, Tiny Miracle, and Traveling Doctor at the exact
  simulator boundary their player-state effects permit.
- Add Razor's sourced offensive actions, form, Energy state, and representable
  passives and constellations.

Out of scope:

- Shield strength and shield-presence doubling, player incoming damage and
  healing, player elemental-status duration, target current HP, enemy DEF
  shred, Witch's Homework, geometry, hitlag extension, RL, and generated docs.

### Phase 1: Golden Majesty Weapon Family - Done (`3f5d5a9`, `3527360`)

Why first:

- All four weapons share metadata, hit categories, stack cadence, duration,
  and refinement tables, so one typed owner-buff implementation prevents four
  divergent state machines.

Target files:

- `src/java/model/weapon/GoldenMajestyWeapon.java` (new)
- `src/java/model/weapon/SummitShaper.java` (new)
- `src/java/model/weapon/TheUnforged.java` (new)
- `src/java/model/weapon/MemoryOfDust.java` (new)
- `src/java/model/weapon/VortexVanquisher.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/simulation/action/AttackAction.java`
- `src/java/mechanics/formula/DamageCalculator.java`
- `src/java/model/character/Xingqiu.java`
- `src/java/model/artifact/ADayCarvedFromRisingWinds.java`
- `src/java/sample/GoldenMajestyWeaponRegressionTest.java` (new)
- `src/java/sample/ActionWindowArtifactRegressionTest.java`

Tasks:

- Expose exact Lv. 90 metadata and R1-R5 Golden Majesty coefficients.
- Gain up to five refreshed ATK stacks from Normal, Charged, Skill, or Burst
  hits, including zero-damage hits, behind the half-open 0.3-second gate.
- Store stacks and trigger cooldown as typed owner buffs so simulator snapshot
  restore reconstructs effective state; leave shield-only effects inactive.
- Distinguish true zero-damage hit events from animation-only zero-multiplier
  actions so post-hit item callbacks cannot fire from dummy casts.

Acceptance criteria:

- All four weapon types and refinement tables are exact, R5 is the default,
  invalid refinement is rejected, and stacks neither leak nor stack past five.
- Pre-trigger damage excludes the new stack; post-trigger stats include it at
  exact gate/expiry boundaries and after snapshot rollback.
- Xingqiu's sourced orbital Hydro contact remains a true zero-damage hit, while
  animation-only character casts do not trigger damage-hit equipment effects.

Test cases to add or update:

- Normal: table-driven metadata, eligible hit classes, zero-damage hits, cap.
- Boundary: 0.299/0.300 seconds, 7.999/8.000 seconds, refresh, rollback.
- Abnormal: Plunge/Other, wrong binding, invalid refinement, independent state,
  and zero fabricated shield doubling.

Verification:

- `./gradlew GoldenMajestyWeaponRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- All four weapons share exact R1-R5 metadata, owner-local five-stack state,
  half-open cadence/expiry boundaries, and snapshot restoration.
- Typed hit-effect metadata accepts sourced zero-damage contacts while
  rejecting animation-only zero-multiplier casts; independent audit's only
  remaining limitation is the repository-wide absence of hitlag extension.
- Focused weapon/artifact regressions, reaction regression, build, Javadoc,
  and executable preflight pass on the combined tree.

### Phase 2: Legacy Player-State Artifact Boundaries - Done (`1e0f49e`)

Why second:

- These six asset-backed sets are independent of shared combat callbacks and
  can close canonical catalog gaps without inventing player-state mechanics.

Target files:

- `src/java/model/artifact/PrayersForDestiny.java` (new)
- `src/java/model/artifact/PrayersForIllumination.java` (new)
- `src/java/model/artifact/PrayersToSpringtime.java` (new)
- `src/java/model/artifact/PrayersForWisdom.java` (new)
- `src/java/model/artifact/TinyMiracle.java` (new)
- `src/java/model/artifact/TravelingDoctor.java` (new)
- `src/java/sample/LegacyBoundaryArtifactRegressionTest.java` (new)

Tasks:

- Add canonical names and preserve supplied artifact stats.
- Keep status-duration reduction, player elemental resistance, incoming
  healing, Burst healing, and incoming-damage triggers explicitly inactive.

Acceptance criteria:

- Every set is loadable, has exact zero representable fixed offensive stats,
  rejects null stats, and retains supplied main/substats without mutation.

Test cases to add or update:

- Normal: canonical names, fresh/supplied containers, arbitrary times.
- Boundary: independent instances and no cross-stat mutation.
- Abnormal: null containers and all unsupported effects remain zero.

Verification:

- `./gradlew LegacyBoundaryArtifactRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Four Prayers tiaras, Tiny Miracle, and Traveling Doctor expose canonical
  loadable identities without fabricating unavailable player-state effects.
- Fresh and supplied stat containers, null rejection, arbitrary times, and
  independent instances pass the focused regression and full local gates.

### Phase 3: Razor Offensive Vertical Slice - Done (`43771dd`, `bd61f12`)

Why third:

- Razor is independent of both equipment batches but requires a complete
  character/config identity slice and focused form-state validation.

Target files:

- `src/java/model/character/Razor.java` (new)
- `config/characters/Razor/Razor_Status.csv` (new)
- `config/characters/Razor/Razor_Multipliers.csv` (new)
- `src/java/model/type/CharacterId.java`
- `src/java/sample/RazorRegressionTest.java` (new)

Tasks:

- Add sourced Normal, cyclic Charged, high-Plunge, Press Skill, retained Hold
  data, Sigils, Burst cast, Normal echoes, attack speed, form duration, and
  switch expiry; the typed request currently has no Press/Hold discriminator.
- Implement A1/A4 and representable C1/C3/C5/C6 branches through typed events;
  keep target-HP C2, enemy-DEF C4, and Witch's Homework inactive.

Acceptance criteria:

- Talent data, timing, gauge, ICD, particles, Energy, cooldown, form, echoes,
  and implemented constellation boundaries are exact and instance-isolated.
- Unsupported defensive, target-state, and progression effects remain absent.

Test cases to add or update:

- Normal: every action category, Sigil gain/consume, Burst echo and C branches.
- Boundary: cooldown/Energy gates, 15-second form, switch-out, C6 cadence,
  snapshot-supported buffs, and attack-speed timing.
- Abnormal: invalid constellation, unsupported actions, cross-simulator reuse,
  independent instances, and excluded branches.

Verification:

- `./gradlew RazorRegressionTest PartyCatalogRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Razor now has typed identity/config, sourced offensive actions, Sigils,
  Lightning Fang, Normal echoes, A1/A4, and representable C1/C3/C5/C6 effects.
- Focused tests cover timing, cooldown/Energy gates, particles, ICD/gauge,
  form/switch boundaries, C6 cadence, invalid inputs, and instance isolation.
- Razor, party catalog, equipment boundary, reaction, build, Javadoc, and
  executable preflight checks pass after integration review.

## Implementation Order: Legacy Reaction Characters and Stateful Weapons Wave

Status: Complete. Typed identities, four stateful weapons, Venti, Yoimiya, and
Yanfei are integrated on `dev_0`; RL, generated docs, and Deferred Systems
remained excluded.

Evidence:

- Maintained KQM character pages and current gcsim implementations for Venti,
  Yoimiya, and Yanfei, accessed 2026-08-03.
- Maintained KQM claymore/catalyst catalogs and current gcsim implementations
  for Fruitful Hook, Serpent Spine, Tulaytullah's Remembrance, and Fang of the
  Mountain King, accessed 2026-08-03.

Scope:

- Add three owner-local old-base-kit offensive character slices with sourced
  actions, periodic/coordinated effects, Energy, ICD, and representable C1-C6.
- Add four exact R1-R5 stateful weapons using existing action, damage,
  reaction, switch, timer, speed, and snapshot contracts.

Out of scope:

- Suction, placement/multi-target geometry, actual enemy HP or random crit
  outcomes, stamina, incoming player damage, enemy defeats, shields, Hexerei,
  RL, parties, and generated docs.

### Phase 1: Reserve Character and Buff Identities - Done

Target files:

- `src/java/model/type/CharacterId.java`
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/LegacyCharacterIdentityRegressionTest.java` (new)

Acceptance criteria:

- `VENTI`, `YOIMIYA`, and `YANFEI` receive stable numeric IDs 18-20.
- Typed Venti resistance and Yoimiya/Yanfei buff identities are reserved once
  before isolated branches; all existing numeric IDs remain stable.
- No behavior or display-string dispatch is introduced in shared code.

Test cases:

- Normal: name/numeric round trips and typed buff identity uniqueness.
- Boundary/abnormal: prior IDs unchanged and unknown numeric fallback.

Verification:

- `./gradlew LegacyCharacterIdentityRegressionTest ReactionRegressionTest build`
- `python scripts/preflight.py --run`

### Phase 2: Four Stateful Weapons - Done

Target files:

- `src/java/model/weapon/FruitfulHook.java` (new)
- `src/java/model/weapon/SerpentSpine.java` (new)
- `src/java/model/weapon/TulaytullahsRemembrance.java` (new)
- `src/java/model/weapon/FangOfTheMountainKing.java` (new)
- one focused regression per weapon under `src/java/sample/`

Acceptance criteria:

- Exact Lv. 90 metadata, R5 defaults, and R1-R5 coefficients are exposed.
- Fruitful Hook grants unconditional Plunge CRIT Rate and a post-Plunge-hit
  ten-second Normal/Charged/Plunge DMG window.
- Serpent Spine gains one persistent all-DMG stack per four on-field seconds,
  up to five; incoming-damage removal is inactive because that deferred event
  does not exist, while switching and snapshots preserve exact timer state.
- Tulaytullah grants static Normal speed and a Skill-opened 14-second Normal
  DMG window with sourced passive-time, 0.3-second hit-stack, cap, and switch
  cancellation behavior.
- Fang gains independently expiring six-second stacks from owner Skill hits
  and sourced Burning/Burgeon reactions, capped at six, with Skill/Burst DMG.

Test cases:

- Normal: R1/R5 metadata, stack acquisition, live eligible damage, off-field
  reaction ownership, timers, speed, and snapshots.
- Boundary: exact 0.3/4/6/10/14-second edges, max stacks, refresh/expiry order.
- Abnormal: dummy/wrong actions, non-owner reactions, switch cancellation,
  invalid refinement/binding/state, and independent instances.

Verification:

- all four focused weapon regressions
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

### Phase 3: Venti Offensive Vertical Slice - Done

Target files:

- `src/java/model/character/Venti.java` (new)
- `config/characters/Venti/Venti_Status.csv` (new)
- `config/characters/Venti/Venti_Multipliers.csv` (new)
- `src/java/sample/VentiRegressionTest.java` (new)

Acceptance criteria:

- Typed basic attacks, Skill, generation-safe Burst ticks, first-aura elemental
  absorption, A4 Energy, cooldowns, ICD/gauge, and switching follow sourced
  stationary single-target timing.
- Representable C2-C6 offensive RES/talent branches are covered; suction,
  airborne state, C1 geometry, defeat Energy, and Hexerei remain excluded.
- Burst/absorption state is owner-local and stale timers cannot cross reuse.

Test cases:

- Normal: actions, Burst cadence/absorption, reactions, Energy, C2-C6.
- Boundary: hitmarks, duration, absorption priority, cooldown and expiry.
- Abnormal: physical/no aura, stale generation, cross reuse, invalid
  constellation/action, and independent instances.

Verification:

- `./gradlew VentiRegressionTest PartyCatalogRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

### Phase 4: Yoimiya Offensive Vertical Slice - Done

Target files:

- `src/java/model/character/Yoimiya.java` (new)
- `config/characters/Yoimiya/Yoimiya_Status.csv` (new)
- `config/characters/Yoimiya/Yoimiya_Multipliers.csv` (new)
- `src/java/sample/YoimiyaRegressionTest.java` (new)

Acceptance criteria:

- Five-step Normal sequence, Niwabi infusion/modifiers, Aurous Blaze party-hit
  trigger/cooldown, A1/A4, Energy, ICD/gauge, cooldowns, and switching follow
  sourced single-target behavior.
- Representable C1 and C3-C5 branches are covered; actual-crit C2, C6 random
  arrows, enemy-defeat transfer, aimed geometry, and hitlag remain excluded.
- Mark and listener state is owner-local, non-recursive, and generation-safe.

Test cases:

- Normal: action sequence, Skill window, mark detonation, A1/A4, C1/C3-C5.
- Boundary: infusion/mark duration, two-second trigger CT, swap and recast.
- Abnormal: self/dummy/zero/wrong-owner hits, stale mark, cross reuse, invalid
  constellation/action, and independent instances.

Verification:

- `./gradlew YoimiyaRegressionTest PartyCatalogRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

### Phase 5: Yanfei Offensive Vertical Slice - Done

Target files:

- `src/java/model/character/Yanfei.java` (new)
- `config/characters/Yanfei/Yanfei_Status.csv` (new)
- `config/characters/Yanfei/Yanfei_Multipliers.csv` (new)
- `src/java/sample/YanfeiRegressionTest.java` (new)

Acceptance criteria:

- Typed attacks, Scarlet Seal generation/consumption, seal-scaled Charged
  damage, Skill/Burst max seals, Brilliance cadence/window, Energy,
  cooldowns, ICD/gauge, and switching follow sourced behavior.
- A1 and representable C1/C3/C5/C6 branches are covered; stamina, actual-crit
  A4, enemy-HP C2, and C4 shield remain excluded.
- Seal/Burst timer state is owner-local and stale events reject cross reuse.

Test cases:

- Normal: action sequence, 0-4 seals, Skill/Burst, Brilliance, A1, C1/C3/C5/C6.
- Boundary: seal consumption, one-second gain cadence, Burst duration/expiry.
- Abnormal: dummy/unsupported action, cross reuse, invalid constellation,
  stale timer, and independent instances.

Verification:

- `./gradlew YanfeiRegressionTest PartyCatalogRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Stable IDs 18-20 and typed buff identities support Venti, Yoimiya, and
  Yanfei without display-string dispatch or prior-ID movement.
- Fruitful Hook, Serpent Spine, Tulaytullah's Remembrance, and Fang of the
  Mountain King cover R1-R5 metadata, exact stack windows, switch behavior,
  and snapshot-safe mutable state; incoming player damage remains excluded.
- Venti, Yoimiya, and Yanfei cover sourced single-target offensive actions,
  projectiles, delayed particle pickup, Energy, ICD/gauge, cooldowns,
  owner-local periodic state, and representable constellations.
- Independent review corrections added Venti bow release/travel timing,
  100-frame particle travel for all three characters, Tulaytullah's persistent
  hit cooldown, and Yoimiya's standard Burst-initial ICD. Action-specific
  cancel windows remain outside the current request payload contract.
- All seven focused content regressions, PartyCatalogRegressionTest,
  ReactionRegressionTest, build, Javadoc, and executable preflight pass on the
  combined tree with no staged-artifact leakage.

## Implementation Order: Derived-Damage Weapons and Summon Characters Wave

Status: Complete. Shared typed formula/identity primitives, four weapons, Yae
Miko, and Albedo are integrated on `dev_0`. RL, generated docs, and Deferred
Systems remained excluded.

Evidence:

- Maintained KQM sword and bow catalogs, accessed 2026-08-03:
  https://library.keqingmains.com/equipment/weapons/swords
  https://library.keqingmains.com/equipment/weapons/bows
- Maintained KQM character and evidence pages, accessed 2026-08-03:
  https://library.keqingmains.com/characters/electro/yae-miko
  https://library.keqingmains.com/evidence/characters/electro/yae-miko
  https://library.keqingmains.com/characters/geo/albedo
  https://library.keqingmains.com/evidence/characters/geo/albedo
- Current `genshinsim/gcsim` character and weapon implementations are the
  timing/state cross-check before each production edit.

Scope:

- Add reusable true-hit derived damage for final DEF/EM and Yae's dynamic
  EM-to-Skill-DMG conversion.
- Add Cinnabar Spindle, Light of Foliar Incision, Hunter's Path, and Key of
  Khaj-Nisut with exact R1-R5 single-target state contracts.
- Add Yae Miko and Albedo offensive vertical slices with owner-local summon,
  periodic, snapshot, Energy, and representable constellation behavior.

Out of scope:

- Witch/Hexerei/Stellar-Conduct additions, enemy/player HP, shields, healing,
  construct durability, hitlag, projectile/weak-point behavior, placement,
  multi-target geometry, RL, parties, and generated docs.

### Phase 1: Shared Derived-Damage and Identity Baseline - Done

Target files:

- `src/java/model/type/StatType.java`
- `src/java/model/stats/StatsContainer.java`
- `src/java/mechanics/formula/StandardDamageStrategy.java`
- `src/java/model/type/CharacterId.java`
- `src/java/sample/DerivedActionDamageRegressionTest.java` (new)

Acceptance criteria:

- Typed ratios add final DEF to Skill base damage, final EM to Normal/Skill or
  Charged base damage, and final EM to Skill DMG Bonus at formula time.
- Every additive base branch requires a true hit, runs before DMG Bonus/CRIT/
  defense/resistance, and cannot mutate source stat containers.
- `YAE_MIKO` and `ALBEDO` receive stable numeric IDs 16 and 17 before branch
  isolation; lookup behavior and existing IDs remain unchanged.

Test cases:

- Normal: late team/field-like DEF/EM, Normal/Skill/Charged routing, combined
  additive and percentage conversions.
- Boundary: zero/negative inputs, explicit zero-multiplier hits, ratio sums.
- Abnormal: dummy casts, unrelated action types/stats, copied-container and ID
  isolation.

Verification:

- `./gradlew DerivedActionDamageRegressionTest ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

### Phase 2: Cinnabar, Foliar, and Hunter Derived-Damage Weapons - Done

Target files:

- `src/java/model/weapon/CinnabarSpindle.java` (new)
- `src/java/model/weapon/LightOfFoliarIncision.java` (new)
- `src/java/model/weapon/HuntersPath.java` (new)
- `src/java/sample/CinnabarSpindleRegressionTest.java` (new)
- `src/java/sample/LightOfFoliarIncisionRegressionTest.java` (new)
- `src/java/sample/HuntersPathRegressionTest.java` (new)

Acceptance criteria:

- All three expose exact Lv. 90 metadata, R5 defaults, and R1-R5 coefficients.
- Cinnabar applies dynamic final-DEF Skill additive damage with sourced
  1.5-second readiness and 0.1-second post-hit clearing behavior.
- Foliar activates after an Elemental Normal hit, snapshots no EM, buffs only
  true Normal/Skill damage, and expires at 28 damage instances or 12 seconds
  with a 12-second acquisition cooldown.
- Hunter grants all-element damage and a post-Charged-hit dynamic EM additive
  window that expires at 12 Charged instances or ten seconds; reacquisition
  has the sourced 12-second cooldown.
- Mutable state is owner/simulator-local and round-trips through snapshots.

Test cases:

- Normal: R1/R5 metadata, trigger ordering, late EM/DEF, eligible damage, cap.
- Boundary: same-time/0.1/1.5/10/12-second edges and exact instance exhaustion.
- Abnormal: dummy/wrong/off-field hits, unrelated actions, invalid refinement,
  cross binding, independent instances, mismatched snapshot state.

Verification:

- `./gradlew CinnabarSpindleRegressionTest LightOfFoliarIncisionRegressionTest HuntersPathRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

### Phase 3: Key of Khaj-Nisut - Done

Target files:

- `src/java/mechanics/buff/BuffId.java`
- `src/java/model/weapon/KeyOfKhajNisut.java` (new)
- `src/java/sample/KeyOfKhajNisutRegressionTest.java` (new)

Acceptance criteria:

- Exact Lv. 90 Sword metadata, R5 default, R1-R5 HP, owner EM, and team EM
  coefficients are exposed.
- On-field true Skill hits gain at most one stack per 0.3 seconds, up to three;
  stacks share one refreshed 20-second expiry and survive switching.
- Each valid acquisition or cap refresh recalculates all owner-stack EM from
  current final Max HP. Reaching or refreshing stack three creates one typed,
  replace-not-stack, 20-second team EM buff from current final Max HP; the
  owner receives both owner and team portions.
- Weapon state and team buff behavior are deterministic and snapshot-safe.

Test cases:

- Normal: R1/R5 metadata, one/three stacks, owner/team EM, off-field retention.
- Boundary: 0.3/20-second half-open edges, cap refresh, late Max HP, rollback.
- Abnormal: off-field/dummy/wrong hits, duplicate weapon owners, invalid
  refinement/binding/state, independent instances.

Verification:

- `./gradlew KeyOfKhajNisutRegressionTest ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

### Phase 4: Yae Miko Offensive Vertical Slice - Done

Target files:

- `src/java/model/character/YaeMiko.java` (new)
- `config/characters/YaeMiko/YaeMiko_Status.csv` (new)
- `config/characters/YaeMiko/YaeMiko_Multipliers.csv` (new)
- `src/java/sample/YaeMikoRegressionTest.java` (new)

Acceptance criteria:

- Typed Normal/Charged/Plunge, three-charge Sesshou Sakura placement,
  generation-safe periodic strikes, Burst cast/Tenko consumption, cooldown,
  Energy, ICD/gauge, and switch behavior follow maintained single-target data.
- Base A4 uses Phase 1's live final-EM Skill-DMG ratio. C1-C6 old-base-kit
  offensive branches cover Energy, Sakura level, team Electro bonus, talent
  levels, and Sakura DEF ignore without Witch/Stellar additions.
- State/listeners are owner-local; stale timers cannot survive replacement,
  Burst consumption, or independent simulator construction.

Test cases:

- Normal: action metadata, one/three Sakura cadence/levels, Burst sequence,
  particles, A4, and representable C1-C6 branches.
- Boundary: Skill charges, summon/Burst timing, duration, Energy, ICD, switch.
- Abnormal: placement/multi-target exclusions, invalid constellation/action,
  cross-simulator reuse, stale generation and independent instances.

Verification:

- `./gradlew YaeMikoRegressionTest PartyCatalogRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

### Phase 5: Albedo Offensive Vertical Slice - Done

Target files:

- `src/java/model/character/Albedo.java` (new)
- `config/characters/Albedo/Albedo_Status.csv` (new)
- `config/characters/Albedo/Albedo_Multipliers.csv` (new)
- `src/java/sample/AlbedoRegressionTest.java` (new)

Acceptance criteria:

- Typed Normal/Charged/Plunge, Solar Isotoma cast, generation-safe 30-second
  field, two-second Transient Blossom trigger, expected particles, Burst/Fatal
  Blossom sequence, cooldown, Energy, ICD/gauge, and switch behavior follow
  maintained single-target policy.
- Isotoma-derived damage uses its cast snapshot. Base A4 applies one sourced
  team EM window; representable old-base-kit C1-C5 Energy/DEF/plunge/talent
  branches are covered while C6 shield and Hexerei additions remain inactive.
- Any-party damage may trigger one blossom per CT without recursive retrigger;
  state/listeners are owner-local and stale generations are rejected.
- Periodic reaction damage uses a separate indirect-damage listener contract;
  Burst/Fatal Blossom remain excluded, and C1/C2 apply at trigger time.

Test cases:

- Normal: actions, field cast/trigger cadence, snapshot, particles, Burst,
  team EM, and representable constellations.
- Boundary: exact 2/30-second edges, replacement, switch, Energy/cooldowns.
- Abnormal: recursive/wrong/stale triggers, geometry/construct exclusions,
  invalid constellation/action, cross reuse and independent instances.

Verification:

- `./gradlew AlbedoRegressionTest PartyCatalogRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

### B-159 Closure Verification

- Focused derived-action, four-weapon, Yae Miko, and Albedo regressions pass.
- Party catalog, reaction regression, build, Javadoc, and executable preflight
  pass on the combined tree.
- Independent reviews resolved low-constellation Yae CSV routing, C4 target
  scope, Sakura lifetime, Albedo Burst/Fatal exclusions, indirect reaction
  triggers, C1/C2 timing, Burst ICD, and exact C2 DEF conversion coverage.

## Implementation Order: Derived-Stat Equipment and Fischl Content Wave

Status: Complete. Shared formula primitives, equipment, artifact sets, and
Fischl are integrated; RL and generated documentation remained excluded.

Scope:

- Add typed final-DEF-to-Normal/Charged base damage and dynamic
  Elemental-Mastery-to-flat-ATK conversion primitives.
- Add Redhorn Stonethresher and Staff of the Scarlet Sands at exact R1-R5
  offensive boundaries.
- Add Disenchantment in Deep Shadow, Scroll of the Hero of Cinder City,
  Obsidian Codex, and Retracing Bolide without inventing unavailable state.
- Add Fischl's sourced offensive actions, Oz lifecycle, reactions, and
  representable passives and constellations.

Out of scope:

- Shield presence/strength, Nightsoul state or points, Stellar-Conduct,
  healing/current player HP, weak points and geometry, Witch's Homework and
  Hexerei, hitlag extension, multi-enemy behavior, RL, and generated docs.

### Phase 1: Derived Offensive Stat Primitives - Done

Target files:

- `src/java/model/type/StatType.java`
- `src/java/model/stats/StatsContainer.java`
- `src/java/mechanics/formula/StandardDamageStrategy.java`
- `src/java/sample/DerivedOffensiveStatRegressionTest.java` (new)

Acceptance criteria:

- One typed ratio converts final Elemental Mastery into flat ATK after every
  ordinary stat source is assembled.
- One typed ratio adds final DEF to only Normal and Charged base damage before
  DMG Bonus, CRIT, defense, and resistance multipliers.
- Zero/negative values, unrelated action types, independent stat containers,
  and source-container immutability remain exact.

Test cases:

- Normal: late-added EM/DEF, Normal/Charged damage, and combined conversions.
- Boundary: zero ratio/value and additive ratios from multiple sources.
- Abnormal: Skill/Burst/Plunge exclusion and unrelated stat preservation.

Verification:

- `./gradlew DerivedOffensiveStatRegressionTest ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commit `a2db2cb` added both typed derived-stat paths; `b637a84` restricted
  final-DEF additive damage to true Normal/Charged hits.
- Focused regression, reaction regression, build, Javadoc, and executable
  preflight passed on 2026-08-03.

### Phase 2: Redhorn Stonethresher - Done

Target files:

- `src/java/model/weapon/RedhornStonethresher.java` (new)
- `src/java/sample/RedhornStonethresherRegressionTest.java` (new)

Acceptance criteria:

- Exact Lv. 90 Claymore metadata, R5 default, R1-R5 DEF%, and final-DEF
  Normal/Charged additive damage coefficients are exposed.
- The additive branch uses Phase 1's typed formula path and cannot affect
  Skill, Burst, Plunge, reactions, or unrelated stats.

Test cases:

- Normal: R1/R5 metadata, late DEF sources, Normal and Charged damage.
- Boundary: every refinement and arbitrary simulation times.
- Abnormal: refinement 0/6, unrelated actions, and independent instances.

Verification:

- `./gradlew RedhornStonethresherRegressionTest ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commit `fc95433` added exact R1-R5 metadata and final-DEF conversion;
  `b637a84` added explicit dummy-cast and zero-multiplier-hit boundaries.
- Focused regression, reaction regression, build, Javadoc, and executable
  preflight passed on 2026-08-03.

### Phase 3: Staff of the Scarlet Sands - Done

Target files:

- `src/java/model/weapon/StaffOfTheScarletSands.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/StaffOfTheScarletSandsRegressionTest.java` (new)

Acceptance criteria:

- Exact Lv. 90 Polearm metadata and R1-R5 dynamic EM-to-ATK conversion apply.
- On-field Skill hits gain up to three flat ATK stacks, each snapshotting
  current EM after the triggering hit; all stacks share one refreshed
  ten-second expiry.
- Off-field/wrong-owner hits, dummy casts, and non-Skill damage cannot stack;
  mutable state is owner-local and snapshot-safe.

Test cases:

- Normal: dynamic base conversion, one/three stacks, EM snapshots, retention.
- Boundary: exact ten-second expiry, shared refresh at/below cap, rollback.
- Abnormal: off-field/wrong hit, invalid refinement/binding, independent state.

Verification:

- `./gradlew StaffOfTheScarletSandsRegressionTest ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commit `13304a4` added exact R1-R5 conversion, post-hit EM snapshots, shared
  refresh, owner binding, and snapshot restore.
- Commit `dee8812` covers simulator-managed team/field EM without double
  application; all local gates passed on 2026-08-03.

### Phase 4: Remaining Asset-Backed Artifact Sets - Done

Target files:

- `src/java/mechanics/buff/BuffId.java`
- `src/java/model/artifact/DisenchantmentInDeepShadow.java` (new)
- `src/java/model/artifact/ScrollOfTheHeroOfCinderCity.java` (new)
- `src/java/model/artifact/ObsidianCodex.java` (new)
- `src/java/model/artifact/RetracingBolide.java` (new)
- `src/java/sample/RemainingArtifactSetRegressionTest.java` (new)

Acceptance criteria:

- Disenchantment grants ATK +18%, Superconduct DMG +80%, and live
  Superconduct-status CRIT Rate +16%; Stellar-Conduct remains inactive.
- Scroll grants sourced non-Nightsoul reaction-element team bonuses with
  off-field triggering, exact duration/replacement, and no fabricated
  Nightsoul Energy or enhanced branch.
- Obsidian and Bolide remain loadable and stat-preserving while all effects
  requiring absent Nightsoul or shield state stay inactive.

Test cases:

- Normal: canonical names, representable bonuses, reaction elements/off-field.
- Boundary: status/buff expiry, same-name replacement, supplied stats.
- Abnormal: wrong owner/reaction, unavailable state, null/cross binding.

Verification:

- `./gradlew RemainingArtifactSetRegressionTest ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commit `a5f39ae` added all four sets, typed Scroll per-element team buffs,
  and explicit inactive Nightsoul/shield/Stellar-Conduct boundaries.
- Focused regression, reaction regression, build, Javadoc, and executable
  preflight passed on 2026-08-03.

### Phase 5: Fischl Offensive Vertical Slice - Done

Target files:

- `src/java/model/character/Fischl.java` (new)
- `config/characters/Fischl/Fischl_Status.csv` (new)
- `config/characters/Fischl/Fischl_Multipliers.csv` (new)
- `src/java/model/type/CharacterId.java`
- `src/java/sample/FischlRegressionTest.java` (new)

Acceptance criteria:

- Typed Normal/Charged/Plunge, Nightrider, Oz periodic attacks, Burst recast,
  particle, cooldown, Energy, snapshot, and switch behavior match sourced
  single-target timing and ICD/gauge contracts.
- A4 and representable C1-C6 offensive branches use owner-local callbacks;
  C6 triggers once per Normal action and shares Oz's sourced ICD.
- Geometry, weak-point A1, healing, Witch/Hexerei, hitlag, and multi-enemy
  behavior remain explicit exclusions.

Test cases:

- Normal: action metadata, Oz summon/recast/ticks, particles, A4, C branches.
- Boundary: Oz/Burst duration, cooldown/Energy gates, ICD, switch and snapshot.
- Abnormal: invalid constellation, unsupported actions, cross reuse/isolation.

Verification:

- `./gradlew FischlRegressionTest PartyCatalogRegressionTest`
- `./gradlew ReactionRegressionTest build javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Commits `3970d39` and `5dd0b34` added Fischl's typed actions, Oz lifecycle,
  sourced initial-recast delay, A4, C1-C6, shared ICD, and focused data/tests.
- Fischl, party catalog, reaction regression, build, Javadoc, and executable
  preflight passed on 2026-08-03.

## Implementation Order: Black Sword Campaign

Status: Complete. This campaign adds one complete offensive weapon passive
through existing typed Normal and Charged Attack stats; RL and generated
documentation remain excluded.

Scope:

- Add The Black Sword with Lv. 90 metadata, refinement-aware Normal and Charged
  Attack DMG bonuses, and focused regression coverage.

Out of scope for this pass:

- The passive's owner healing, player current HP and damage intake, optimizer
  defaults, RL, and generated docs.

### Phase 1: Add The Black Sword - Done

Target files:

- `src/java/model/weapon/TheBlackSword.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- The default constructor represents R5 while explicit R1-R5 construction
  exposes canonical name, Sword type, 510 Base ATK, and 27.6% CRIT Rate.
- Justice adds 20%/25%/30%/35%/40% to both Normal and Charged Attack DMG and
  does not alter Skill, Burst, Plunging, all-Damage, or unrelated base stats.
- Refinement values outside 1-5 fail before creating a usable weapon.

Test cases to add or update:

- Normal: R5 metadata and both typed damage bonuses.
- Boundary: R1 and R5 refinement values at arbitrary negative and positive
  simulation times.
- Abnormal: refinement 0/6, no unrelated stat mutation, and independent
  instances.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc PartyCatalogRegressionTest`
- `python scripts/preflight.py --run`

Completion evidence:

- Focused regressions cover R1/R5 metadata and bonuses, arbitrary-time
  behavior, unrelated-stat preservation, independent instances, and invalid
  refinement.
- `./gradlew ReactionRegressionTest`, `./gradlew build javadoc
  PartyCatalogRegressionTest`, and `python scripts/preflight.py --run` passed
  on 2026-08-02.

## Implementation Order: Millennial Movement Weapon Campaign

Status: Complete. This campaign replaces one isolated Elegy state machine
with a snapshot-aware shared contract, then adds Freedom-Sworn and Song of
Broken Pines; RL and generated documentation remain excluded.

Scope:

- Add generic weapon-state capture/restore to `SimulatorSnapshot` through one
  narrow optional capability.
- Share sigil CT/count, 12-second movement window, 20-second acquisition lock,
  owner binding, and ATK-effect replacement across all three weapons.
- Add complete Freedom-Sworn and Song of Broken Pines offensive passives.

Out of scope for this pass:

- Timer-event queue snapshots, movement speed, player defensive state,
  optimizer defaults, RL protocol/training, and generated docs.

Definitions:

- `SnapshotAwareWeaponEffect`: optional weapon capability with an immutable
  typed state marker used by simulator save/restore.
- `MillennialMovementWeapon`: package-local abstract weapon base owning sigil
  acquisition, lockout, owner binding, snapshot state, shared ATK movement
  buff, and one concrete weapon's unique movement buff.
- `NORMAL_ATTACK_SPD`: Normal-only animation speed stat used where a passive
  must not accelerate Charged Attacks.

### Phase 1: Snapshot State and Shared Millennial Contract - Done

Target files:

- `src/java/model/entity/SnapshotAwareWeaponEffect.java` (new)
- `src/java/simulation/SimulatorSnapshot.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/model/weapon/MillennialMovementWeapon.java` (new)
- `src/java/model/weapon/ElegyForTheEnd.java`
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Simulator snapshots capture and restore state only for weapons implementing
  the narrow capability; ordinary weapons remain unchanged and mismatched
  state types fail clearly.
- Elegy retains exact 0.2-second CT, four sigils, off-field Skill/Burst hits,
  12-second R1-R5 EM/ATK song, and 20-second lock through the shared base.
- Millennial ATK and Elegy's unique EM use separate typed IDs so same-effect
  replacement does not erase unique effects from a different movement weapon.

Test cases to add or update:

- Normal: existing Elegy metadata/trigger/order/value cases and state capture
  before activation, during sigils, and during lock.
- Boundary: exact 0.2/12/20 seconds and restore/replay at each state.
- Abnormal: ordinary weapon snapshot, foreign owner/simulator, wrong hit,
  invalid refinement, and wrong concrete state restore.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc PartyCatalogRegressionTest`
- `python scripts/preflight.py --run`

Completion evidence:

- Elegy retains its sourced trigger/value behavior while shared ATK and unique
  EM now use separate typed replacement identities.
- Focused regressions restore one-sigil, active-song, and lockout states,
  replay exact 0.2/12/20-second boundaries, and reject unrelated state types.
- `./gradlew ReactionRegressionTest`, `./gradlew build javadoc
  PartyCatalogRegressionTest`, and `python scripts/preflight.py --run` passed
  on 2026-08-02.

### Phase 2: Add Freedom-Sworn - Done

Target files:

- `src/java/model/weapon/FreedomSworn.java` (new)
- `src/java/model/entity/ElementalReactionTriggeredWeaponEffect.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/simulation/CombatSimulator.java`
- `src/java/simulation/SimulationEventBus.java`
- `src/java/simulation/runtime/SimulationEventDispatcher.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Freedom-Sworn exposes exact Lv. 90 metadata and unconditional
  10%/12.5%/15%/17.5%/20% all-DMG bonus.
- Owner-attributed non-NONE reactions, including off-field reactions, gain two
  sigils at 0.5-second CT and grant the 12-second R1-R5 Normal/Charged/Plunging
  DMG and shared ATK movement effects, followed by the 20-second lock.
- Thundercloud ticks, Aura-independent Lunar action classification, and
  Moondrift Harmony follow-ups continue reaching existing observers but cannot
  grant equipment reaction sigils.
- Its unique action-DMG buff stacks with Elegy's unique EM while the shared ATK
  effect uses latest typed replacement; snapshot restore replays state exactly.

Test cases to add or update:

- Normal: metadata, off-field reactions, two-sigil activation, three action
  bonuses, shared ATK, and coexistence with Elegy.
- Boundary: exact 0.5/12/20 seconds, post-reaction ordering, and snapshot
  rollback before/after activation.
- Abnormal: NONE/foreign reactions, duplicate/cross-simulator init, refinement
  0/6, Physical inclusion in unconditional all-DMG, and independent instances.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc PartyCatalogRegressionTest`
- `python scripts/preflight.py --run`

Completion evidence:

- Focused regressions cover actual versus derived reaction dispatch,
  off-field ownership, R1/R5 values, exact 0.5/12/20-second boundaries,
  snapshot replay, invalid inputs, and cross-simulator binding.
- Elegy and Freedom-Sworn preserve both unique effects while a later lower
  shared ATK value replaces the earlier higher value.
- `./gradlew ReactionRegressionTest`, `./gradlew build javadoc
  PartyCatalogRegressionTest`, and `python scripts/preflight.py --run` passed
  on 2026-08-02.

### Phase 3: Add Song of Broken Pines - Done

Target files:

- `src/java/model/weapon/SongOfBrokenPines.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/model/type/StatType.java`
- `src/java/simulation/runtime/ActionTimelineExecutor.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Song exposes exact Lv. 90 metadata, unconditional 16%/20%/24%/28%/32% ATK,
  and four positive Normal/Charged hit sigils at 0.3-second CT.
- Four sigils grant 12-second R1-R5 Normal-only attack speed and shared ATK,
  followed by the 20-second acquisition lock; the fourth hit is unbuffed.
- Normal-only speed shortens Normal actions without changing Charged, Skill,
  Burst, or Plunging durations, respects the combined 60% speed cap, and
  snapshot restore preserves sigil/lock state.

Test cases to add or update:

- Normal: metadata, Normal/Charged sigils, R1/R5 ATK and speed, action timing,
  and coexistence with both other Millennial weapons.
- Boundary: exact 0.3/12/20 seconds, fourth-hit ordering, and snapshot replay.
- Abnormal: zero/wrong/foreign hits, no Charged speed, invalid refinement,
  duplicate/cross-simulator init, and independent instances.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc PartyCatalogRegressionTest`
- `python scripts/preflight.py --run`

Completion evidence:

- Song of Broken Pines now exposes exact R1-R5 static ATK, four-hit sigils,
  shared ATK, and Normal-only Banner-Hymn speed through the shared movement
  state machine.
- Focused regressions cover fourth-hit ordering, exact 0.3/12/20-second
  boundaries, snapshots, the 60% speed cap, invalid inputs, and three-weapon
  unique-effect coexistence with latest shared-ATK replacement.
- `./gradlew ReactionRegressionTest`, `./gradlew build javadoc
  PartyCatalogRegressionTest`, and `python scripts/preflight.py --run` passed
  on 2026-08-02.

## Implementation Order: Static Five-Star Weapon Branch Campaign

Status: Complete. This campaign adds five missing five-star weapons whose
always-on outgoing bonuses are exact in the current simulator boundary; RL and
generated documentation remain excluded.

Scope:

- Add Aquila Favonia, Wolf's Gravestone, Amos' Bow, Haran Geppaku Futsu, and
  Skyward Atlas with Lv. 90 metadata and refinement-aware always-on bonuses.
- Share the all-element stat initialization used by Haran and Skyward Atlas
  through `StaticElementalDamageWeapon`.

Out of scope for this pass:

- Incoming player damage/healing, enemy HP thresholds, projectile travel time,
  cross-party Skill-use Wavespike state, autonomous cloud attacks, optimizer
  defaults, RL, and generated docs.

Definitions:

- `StaticElementalDamageWeapon`: package-local abstract weapon base that stores
  refinement metadata and adds one refinement-aware bonus to all seven
  elemental DMG stats while excluding Physical DMG.

### Phase 1: Add Aquila, Wolf's Gravestone, and Amos' Bow - Done

Target files:

- `src/java/model/weapon/AquilaFavonia.java` (new)
- `src/java/model/weapon/WolfsGravestone.java` (new)
- `src/java/model/weapon/AmosBow.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Every class exposes canonical name/type, exact Lv. 90 Base ATK and substat,
  an R5 default, selected refinement, and refinement 0/6 rejection.
- Aquila and Wolf's Gravestone add 20%/25%/30%/35%/40% ATK without inventing
  incoming-damage or enemy-low-HP triggers.
- Amos adds 12%/15%/18%/21%/24% Normal and Charged Attack DMG without
  inventing projectile travel duration.

Test cases to add or update:

- Normal: R5 metadata and all exact active bonuses.
- Boundary: R1/R5 and arbitrary negative/positive times.
- Abnormal: invalid refinement, unrelated-stat preservation, inactive trigger
  branches, and independent instances.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc PartyCatalogRegressionTest`
- `python scripts/preflight.py --run`

Completion evidence:

- Focused regressions cover all Lv. 90 metadata, R1/R5 active values,
  arbitrary-time behavior, unrelated-stat preservation, unreachable trigger
  branches, independent instances, and invalid refinement.
- `./gradlew ReactionRegressionTest`, `./gradlew build javadoc
  PartyCatalogRegressionTest`, and `python scripts/preflight.py --run` passed
  on 2026-08-02.

### Phase 2: Add Haran and Skyward Atlas Elemental Branches - Done

Target files:

- `src/java/model/weapon/StaticElementalDamageWeapon.java` (new)
- `src/java/model/weapon/HaranGeppakuFutsu.java` (new)
- `src/java/model/weapon/SkywardAtlas.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Both classes expose canonical names/types, exact Lv. 90 metadata, R5
  defaults, selected refinement, and refinement 0/6 rejection.
- Both add 12%/15%/18%/21%/24% to every elemental DMG stat at all times while
  leaving Physical and generic all-DMG unchanged.
- The shared base remains package-local and validates all constructor inputs
  needed by its two concrete classes.

Test cases to add or update:

- Normal: R5 metadata and all seven elemental bonuses for both classes.
- Boundary: R1/R5, Physical exclusion, arbitrary time, and supplied stats.
- Abnormal: invalid refinement, no generic all-DMG leakage, inactive trigger
  branches, and independent instances.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc PartyCatalogRegressionTest`
- `python scripts/preflight.py --run`

Completion evidence:

- Focused regressions cover all seven elemental stats, R1/R5 metadata and
  values, supplied-stat composition, arbitrary time, Physical/generic
  exclusion, unreachable branches, independent instances, and invalid
  refinement.
- `./gradlew ReactionRegressionTest`, `./gradlew build javadoc
  PartyCatalogRegressionTest`, and `python scripts/preflight.py --run` passed
  on 2026-08-02.

## Implementation Order: Missing Artifact Runtime Coverage Campaign

Status: Complete. B-149 adds three locally asset-backed artifact sets through
existing action, damage, reaction, buff, and snapshot contracts. Piece-count
modeling, equipment removal, new simulator hooks, RL, and generated docs are
excluded.

Evidence:

- Maintained KQM artifact catalog and evidence vault, accessed 2026-08-02:
  https://library.keqingmains.com/equipment/artifacts
  https://library.keqingmains.com/evidence/equipment/artifacts

### Phase 1: Shimenawa's Reminiscence - Done

Target files:

- `src/java/model/artifact/ShimenawasReminiscence.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria and tests:

- Preserve supplied stats and add ATK +18%; an owner Skill cast at 15 or more
  Energy opens one non-refreshable ten-second 50% Normal/Charged/Plunging window.
- Spend 15 Energy after the sourced seven-frame delay; no Energy or second buff
  is consumed during the active window, and exact expiry allows reactivation.
- Cover 14.999/15 Energy, 6/7-frame drain, three isolated categories, active
  recast, exact expiry, invalid callbacks/binding, and post-drain snapshot restore.

Completion evidence:

- A typed non-refreshable buff provides all three attack bonuses immediately;
  one seven-frame event spends Energy only after an eligible inactive cast.
- Threshold, delay, recast, exact expiry, source/binding, post-drain snapshot,
  reaction regression, build, Javadoc, party catalog, and preflight pass.

### Phase 2: Flower of Paradise Lost - Done

Target files:

- `src/java/model/artifact/FlowerOfParadiseLost.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria and tests:

- Preserve supplied stats and add EM +80; base bonuses are 40% Bloom/Hyperbloom/
  Burgeon and 10% Lunar-Bloom, with each eligible owner reaction adding 25% of
  those effects for ten seconds, up to four stacks at one-second trigger CT.
- Off-field ownership works; unrelated, NONE, wrong-owner, and wrong-simulator
  callbacks are inert; the triggering reaction does not retroactively gain its stack.
- Cover all four reactions, 0.999/1.000 CT, cap, independent expiry, trigger
  ordering, invalid callbacks/binding, and snapshot restore.

Completion evidence:

- Fixed EM and four typed reaction bonuses are preserved separately from four
  independently expiring owner stacks and the snapshot-safe one-second CT.
- All reaction kinds, off-field ownership, cap/expiry boundaries, invalid
  callbacks, rollback, real Bloom ordering, reaction regression, build,
  Javadoc, party catalog, and preflight pass.

### Phase 3: Long Night's Oath - Done

Target files:

- `src/java/model/artifact/LongNightsOath.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria and tests:

- Preserve supplied stats and add Plunging DMG +25%; positive owner Plunge,
  Charged, and Skill hits add 1/2/2 Radiance stacks after damage, up to five.
- Each trigger category has an independent one-second CT; every stack grants
  15% Plunging DMG and expires independently six seconds after its gain.
- Cover trigger-hit ordering, all categories, CT independence, cap/expiry,
  5.999/6.000 expiry, invalid callbacks/binding, and snapshot restore.

Completion evidence:

- Typed owner buffs model independent Plunge/Charged/Skill cooldowns and five
  Radiance stacks with separately retained six-second expiry windows.
- Post-hit ordering, 1/2/2 category gains, exact CT/expiry, cap behavior,
  off-field and fallback Skill metadata, invalid callbacks, rollback, reaction
  regression, build, Javadoc, party catalog, and preflight pass.

Verification for every phase:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew PartyCatalogRegressionTest`
- representative samples when affected
- `python scripts/preflight.py --run`

## Implementation Order: Target-State and Skill-Hit Artifact Campaign

Status: Complete. B-151 adds three locally asset-backed sets using one
per-hit target-state capability and the existing typed damage/team-buff hooks.

Scope:

- Evaluate artifact-owned target Aura conditions against the live enemy state
  for each impact without mutating character snapshots.
- Add Lavawalker and Thundersoother's offensive 35% all-DMG conditions.
- Add Tenacity of the Millelith's HP +20% and non-stacking three-second team
  ATK window with owner-local 0.5-second trigger CT.

Out of scope for this pass:

- Player elemental RES, Shield Strength, shield absorption, enemy attacks, or
  other defensive systems listed under Deferred Systems.
- Piece-count/equipment-removal modeling, formula reordering, RL contracts,
  reports, generated `docs/`, or party loadout changes.

Definitions:

- `TargetDependentArtifactEffect`: artifact capability that mutates only one
  per-hit stats copy from the current target and timestamp.
- `TargetAuraDamageArtifactSet`: shared immutable implementation for one Aura
  element and one outgoing all-DMG bonus.

### Phase 1: Live-Aura Damage Artifact Sets - Done

Why first:

- Aura conditions must be evaluated outside snapshotted structural stats before
  the two concrete artifact sets can be represented accurately.

Target files:

- `src/java/model/entity/TargetDependentArtifactEffect.java` (new)
- `src/java/mechanics/formula/DamageCalculator.java`
- `src/java/model/artifact/TargetAuraDamageArtifactSet.java` (new)
- `src/java/model/artifact/Lavawalker.java` (new)
- `src/java/model/artifact/Thundersoother.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Extend target-dependent stat resolution to copy once and apply eligible
  weapon and artifact effects in deterministic equipment order.
- Add pure Pyro/Electro Aura artifact implementations granting 35% all-DMG.
- Preserve supplied artifact stats while leaving defensive 2pc RES outside the
  offensive simulator stat model.

Acceptance criteria:

- Existing Aura grants exactly 35% all-DMG for live and snapshotted actions;
  the hit that first applies the Aura remains unboosted.
- Aura expiry, unrelated/coexisting Aura, null target, plain artifacts, and
  weapon-plus-artifact composition are deterministic and non-mutating.

Test cases to add or update:

- Pyro/Electro active, first-application ordering, exact Aura expiry,
  snapshot/live parity, unrelated Aura, combined weapon/artifact bonus,
  supplied/null stats, and independent instances.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc PartyCatalogRegressionTest`
- `python scripts/preflight.py --run`

Completion evidence:

- Target-dependent weapon and artifact effects compose on one per-hit copy;
  Lavawalker and Thundersoother never mutate effective or snapshot stats.
- First-application ordering, live/snapshot Aura, coexisting and exact-expiry
  states, equipment composition, null boundaries, reaction regression, build,
  Javadoc, party catalog, and preflight pass.

### Phase 2: Tenacity of the Millelith Team Window - Done

Why second:

- Tenacity is independent of the target-state capability and can reuse the
  established typed damage callback and team no-stack gateway.

Target files:

- `src/java/model/artifact/TenacityOfTheMillelith.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Add fixed HP +20% and owner-local 0.5-second Skill-hit CT.
- Apply or refresh one typed three-second team ATK +20% window after an owner
  Skill hit, including zero damage and off-field hits.
- Keep Shield Strength explicitly outside the current offensive stat surface.

Acceptance criteria:

- The triggering Skill hit is unbuffed; subsequent owner and ally hits receive
  20% ATK for `[trigger, trigger + 3)`.
- 0.499/0.500 CT, refresh/non-stack, exact expiry, multiple wearers, off-field,
  zero damage, binding, invalid callbacks, and snapshot restore are covered.

Test cases to add or update:

- Real gateway ordering, zero-damage Skill metadata, fallback Skill flag,
  non-Skill rejection, owner/simulator rejection, team source attribution,
  CT/expiry boundaries, refresh, snapshot, rollback, and null stats.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build javadoc PartyCatalogRegressionTest`
- `python scripts/preflight.py --run`

Completion evidence:

- One owner-local typed CT drives a single team no-stack ATK window while HP
  remains fixed and defensive Shield Strength stays outside this pass.
- Trigger ordering, zero damage, fallback metadata, multiple wearers,
  off-field ownership, CT/expiry, snapshot/rollback, invalid callbacks,
  reaction regression, build, Javadoc, party catalog, and preflight pass.

## Implementation Order: Remaining Basic Action Coverage Campaign

Status: Complete. B-148 adds sourced Charged and high-Plunging inputs to
three already-supported characters. Low/collision Plunge hits, stamina,
measured animation retiming, new rotations, RL, and generated docs are excluded.

Evidence:

- Maintained KQM talent tables and current combat data, accessed 2026-08-02:
  https://library.keqingmains.com/characters/anemo/sucrose
  https://library.keqingmains.com/characters/electro/ineffa
  https://library.keqingmains.com/characters/electro/flins
- The current Flins quick guide states that he cannot Plunge during Manifest
  Flame: https://keqingmains.com/q/flins-quickguide/

### Phase 1: Sucrose High Plunge - Done

Target files:

- `config/characters/Sucrose/Sucrose_Multipliers.csv`
- `src/java/model/character/Sucrose.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria and tests:

- `PLUNGE` resolves one sourced level-9 2.6076 Anemo high-Plunge hit with
  Plunging bonus, typed Plunge category, no ICD, 1U, and the existing one-second
  high-Plunge duration approximation.
- Plunge and other non-Normal inputs reset the Normal combo; unsupported Dash
  fails explicitly without damage or time advancement.
- Regressions cover metadata, category-specific bonuses, combo interruption,
  unsupported input, and CSV/runtime alignment.

Completion evidence:

- Sucrose now dispatches a sourced high Anemo Plunge and resets its combo on
  every non-Normal input; unsupported Dash is rejected before time or damage.
- Reaction regression, build, Javadoc, party catalog, and preflight pass.

### Phase 2: Ineffa Charged and High Plunge - Done

Target files:

- `config/characters/Ineffa/Ineffa_Multipliers.csv`
- `src/java/model/character/Ineffa.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria and tests:

- `CHARGE` and `PLUNGE` resolve sourced level-9 1.7440 and 2.9336 Physical hits
  with their dedicated bonus/action categories, standard Charged 1U metadata,
  no-ICD Plunge 1U metadata, and established 0.8/1.0-second approximations.
- Both inputs reset the four-step Normal combo; unsupported Dash fails
  explicitly without mutating simulation time or damage state.
- Regressions cover both metadata paths, isolated Physical/action bonuses,
  combo interruption, unsupported input, and CSV/runtime alignment.

Completion evidence:

- Ineffa now dispatches sourced Physical Charged and high-Plunge actions with
  independent action categories and resets its combo on non-Normal input.
- Reaction regression, build, Javadoc, party catalog, and preflight pass.

### Phase 3: Flins High Plunge Form Boundary - Done

Target files:

- `config/characters/Flins/Flins_Multipliers.csv`
- `src/java/model/character/Flins.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria and tests:

- Outside Manifest Flame, `PLUNGE` resolves one sourced level-9 2.9336 Physical
  high-Plunge hit with Plunging bonus, typed Plunge category, no ICD, 1U, and
  one-second duration; it resets the Normal combo.
- During Manifest Flame, Plunge is rejected before damage or time advancement;
  it becomes available at exact form expiry. Other unsupported inputs fail
  explicitly while the existing Dash action remains supported.
- Regressions cover metadata, isolated bonuses, combo interruption, active and
  exact-expiry form boundaries, unsupported input, and CSV/runtime alignment.

Completion evidence:

- Flins dispatches a sourced Physical high Plunge outside Manifest Flame and
  rejects it during the active half-open form window before damage or time.
- Reaction regression, build, Javadoc, party catalog, and preflight pass. Two
  sample runs each reproduce 32,020,327 / 322,136 (`FlinsParty`) and
  22,174,896 / 320,910 (`FlinsParty2`).

Verification for every phase:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew PartyCatalogRegressionTest`
- representative party samples when affected
- `python scripts/preflight.py --run`

## Implementation Order: Sucrose Reaction Lifecycle Accuracy

Status: Complete. Implemented B-141 as one character-owned reaction-listener
and fixed absorption window pass; Hexerei additions, environment-object Swirls,
multi-target simulation, RL, and generated docs are excluded.

Evidence:

- The maintained KQM Sucrose page and evidence vault, accessed 2026-08-02,
  specify that Sucrose-triggered Swirl grants matching-element party members
  EM +50 for eight seconds and that C6 lasts ten seconds from absorption:
  https://library.keqingmains.com/characters/anemo/sucrose
  https://library.keqingmains.com/evidence/characters/anemo/sucrose

### Phase 1: Correct A1 Dispatch and C6 Duration - Done

Target files:

- `src/java/model/character/Sucrose.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Sucrose registers one reaction listener per bound simulator and rejects
  cross-simulator reuse without duplicating either the A1 or C4 listener.
- Only a Swirl dispatched with Sucrose as source and a supported Pyro, Hydro,
  Electro, or Cryo result element refreshes one typed eight-second A1 buff.
- A1 grants EM +50 only to matching-element party members, excludes Sucrose,
  works while she is off-field, and does not infer from residual enemy aura.
- C6 absorption grants one owner-sourced corresponding elemental DMG +20%
  team buff for ten seconds from the actual absorption time; C5 does not.
- A1/C6 membership and remaining duration survive simulator snapshots.

Test cases:

- Normal: four supported Swirl elements, matching/nonmatching/Sucrose targets,
  off-field Sucrose, A1 refresh, late C6 absorption, and source attribution.
- Boundary: A1 at 7.999/8.000 seconds, C6 at 9.999/10.000 seconds, and active
  snapshot rollback for both windows.
- Abnormal: foreign source, NONE/non-Swirl/null-element/unsupported-element,
  wrong simulator, duplicate initialization, cross-binding, and C5 absorption.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- representative party samples twice
- `python scripts/preflight.py --run`

Completion evidence:

- Sucrose now uses the shared reaction-aware character contract and registers
  A1 once per simulator; post-action residual-aura inference was removed.
- Focused regressions cover four Swirl elements, matching and nonmatching
  recipients, an actual 0.5U aura-consuming Charged Swirl, off-field Burst
  Swirl, coexisting/refreshing windows, invalid callbacks, and snapshots.
- C6 starts a typed owner-sourced ten-second buff at late absorption, applies
  the absorbed-element bonus to all recipients, and remains absent at C5.
- Reaction regression, build, Javadoc, and representative samples pass. Two
  runs each reproduce 1,275,070 / 60,718 (`RaidenParty`), 32,047,365 / 322,084
  (`FlinsParty`), and 20,999,900 / 303,906 (`FlinsParty2`); the Sucrose teams
  increase because A1 now activates on their real Normal/Burst Swirls.

## Implementation Order: Sucrose Burst Absorption Ordering

Status: Complete. Implemented B-142 as a backward-compatible periodic-event
pre-tick hook and Sucrose-owned aura capture; generic reaction ordering, new
absorption priorities, event snapshotting, RL, and generated docs are excluded.

Evidence:

- `PeriodicDamageEvent` currently resolves its action before the character
  callback can inspect aura, while the maintained KQM Sucrose evidence page,
  accessed 2026-08-02, records Burst absorption and Anemo damage as simultaneous:
  https://library.keqingmains.com/evidence/characters/anemo/sucrose

### Phase 1: Capture Absorption Aura Before Burst Damage - Done

Target files:

- `src/java/simulation/event/PeriodicDamageEvent.java`
- `src/java/model/character/Sucrose.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Periodic events optionally run one pre-tick callback immediately before the
  action and retain the existing action-then-post-callback behavior by default.
- Sucrose captures the first supported Pyro/Hydro/Electro/Cryo aura before a
  Burst tick, then resolves Anemo damage, absorption damage, C6, and A4 in the
  established order without recapturing on later ticks.
- A weak aura that the Anemo tick fully consumes still becomes the absorbed
  element, while no aura produces no absorption and later ticks may retry.
- Existing periodic-event users and Sucrose A1/C6 lifecycle behavior remain
  unchanged outside the corrected absorption timing.

Test cases:

- Normal: generic pre/action/post ordering, 0.5U weak-aura absorption, absorbed
  damage element, A1 Swirl, C6 source/duration, and later absorbed ticks.
- Boundary: no-aura first tick then second-tick absorption, supported-element
  priority under multiple auras, and first-only capture after aura changes.
- Abnormal: null callbacks, unsupported-only aura, C5 exclusion, and legacy
  periodic constructor ordering; the combat resolver's required-enemy
  precondition remains outside this character-scoped pass.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- representative party samples twice
- `python scripts/preflight.py --run`

Completion evidence:

- `PeriodicDamageEvent` keeps both legacy constructors and adds an optional
  pre-tick callback; regressions prove pre/action/post, legacy action/post,
  cancellation, cadence, and null-callback behavior.
- Sucrose captures a supported aura before Anemo damage, preserves the existing
  Pyro/Hydro/Electro/Cryo priority, retries after an aura-less tick, and never
  replaces the first absorbed element on later ticks.
- Focused regressions cover 0.5U aura capture, actual A1 Swirl, C6 activation,
  repeated absorbed damage, dual aura, and unsupported-only aura behavior.
- Reaction regression, build, Javadoc, party catalog, and representative
  samples pass. Two runs each reproduce 1,275,070 / 60,718 (`RaidenParty`),
  32,047,365 / 322,084 (`FlinsParty`), and 22,146,093 / 320,493
  (`FlinsParty2`); the final increase reflects corrected pre-damage absorption.

## Implementation Order: Raiden C6 Wishbearer Lifecycle

Status: Complete. Implemented B-143 as a shared flat Burst cooldown operation
followed by character-owned hit markers; cooldown carries, non-Burst cooldowns,
enemy-count geometry, RL, and generated docs are excluded.

Evidence:

- The maintained KQM Raiden page and cooldown table, accessed 2026-08-02,
  specify one second of ally Burst cooldown reduction per qualifying Musou hit,
  a one-second trigger cooldown, at most five triggers, and Raiden exclusion:
  https://library.keqingmains.com/characters/electro/raiden-shogun
  https://library.keqingmains.com/combat-mechanics/cooldowns

### Phase 1: Add Flat Burst Cooldown Reduction - Done

Target files:

- `src/java/model/entity/state/CooldownState.java`
- `src/java/model/entity/Character.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- A finite non-negative flat amount shortens only the pending Burst cooldown,
  clamps at the current time, discards excess, and reports actual reduction.
- Ready and zero reductions are inert, invalid values are rejected without
  mutation, and last-use/configured-duration metadata remain unchanged.
- Existing simulator snapshots restore a divergent Burst reduction exactly.

Test cases:

- Normal: partial reduction and returned amount.
- Boundary: ready, zero, exact/full clamp, excess discard, and snapshot restore.
- Abnormal: negative, NaN, and positive/negative infinity with state retention.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py --run`

Completion evidence:

- Partial and full reductions report the applied amount, clamp at readiness,
  preserve last-use/configured cooldown metadata, and discard excess.
- Ready/zero inputs, invalid values with state retention, and divergent
  snapshot rollback pass the focused regression, build, and Javadoc gates.

### Phase 2: Add Wishbearer Hit Lifecycle - Done

Target files:

- `src/java/mechanics/buff/BuffId.java`
- `src/java/model/character/RaidenShogun.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- C6 positive resolved Musou Normal/Charged/Plunging hits reduce every other
  party member's pending Burst cooldown by one second and never reduce Raiden's.
- One typed cooldown marker enforces a half-open one-second interval and typed
  trigger markers enforce five activations per Musou state across multi-hits.
- Initial Burst cast, pre/post-form attacks, C5, wrong actor/category, zero or
  negative damage, and missing target are inert.
- Switching/end clears the C6 markers; a new Burst state receives a fresh five
  triggers, and in-form marker/cooldown state survives snapshot rollback.

Test cases:

- Normal: ally partial cooldown, multiple allies, Raiden exclusion, all three
  Musou input categories, and fresh-state reset.
- Boundary: N4/Charged same-time multi-hits, 0.999/1.000 seconds, five/six
  triggers, ready ally, switch end, and active snapshot replay.
- Abnormal: C5, initial cast, physical/post-form hit, wrong actor, non-positive
  damage, missing enemy, and cross-simulator reuse assumptions.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- representative party samples twice
- `python scripts/preflight.py --run`

Completion evidence:

- C6 registers one resolved-damage listener and accepts only positive Raiden
  hits carrying Musou Burst classification while the form and enemy are active.
- Typed character markers enforce the half-open one-second interval and five-hit
  state cap, survive in-form snapshot rollback, and clear on end/switch/recast.
- Regressions cover multiple pending/ready allies, Raiden exclusion, an actual
  Musou Normal, all three input categories, same-time multi-hit, 0.999/1.000
  seconds, five/six triggers, snapshot replay, invalid callbacks, and C5.
- Reaction regression, build, Javadoc, party catalog, and representative
  samples pass. Two runs each reproduce 1,275,070 / 60,718 (`RaidenParty`),
  32,047,365 / 322,084 (`FlinsParty`), and 22,146,093 / 320,493
  (`FlinsParty2`).

## Implementation Order: Flins Charged Attack Coverage

Status: Complete. Implemented B-144 as one sourced character action; stamina,
Plunging input, new rotation scripts, RL, and generated docs are excluded.

Evidence:

- `config/characters/Flins/Flins_Multipliers.csv` contains level-9 Charge
  1.8928. The current KQM quick guide and HoYoLAB talent table, accessed
  2026-08-02, confirm Physical baseline and unoverrideable Manifest Electro:
  https://keqingmains.com/q/flins-quickguide/
  https://www.hoyolab.com/article/41458758

### Phase 1: Add Typed Charged Dispatch - Done

Target files:

- `src/java/model/character/Flins.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- `CHARGE` dispatch resolves one sourced 1.8928 hit, resets the Normal combo,
  and advances the established polearm approximation by 0.8 seconds.
- The hit is Physical outside Manifest Flame and unoverrideable Electro inside,
  with Charged DMG Bonus, typed Charged category, standard Charged ICD, and 1U.
- A Charged hit does not consume C2's next-Normal follow-up, while an in-form
  positive hit still participates in existing C2 Electro shred dispatch.
- C5 does not alter the Normal-talent Charged multiplier, and snapshoted form
  timing continues to select the same element after rollback.

Test cases:

- Normal: out/in-form metadata, duration, sourced multiplier, and C2 shred.
- Boundary: form expiry element switch, combo reset, C2 Charge-then-Normal,
  C5 multiplier, and active-form snapshot rollback.
- Abnormal: unsupported form-independent stats and null/missing target remain
  governed by existing combat resolver preconditions.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- representative party samples twice
- `python scripts/preflight.py --run`

Completion evidence:

- Flins now accepts typed `CHARGE` input with sourced Physical/Electro routing,
  Charged bonus/ICD/gauge metadata, combo reset, and the established 0.8-second
  polearm action duration.
- Focused regression covers C2 shred and next-Normal preservation, C5, exact
  Manifest expiry, and snapshot replay.
- Reaction regression, build, Javadoc, party catalog, and representative
  samples pass. Two runs each reproduce 1,275,070 / 60,718 (`RaidenParty`),
  32,047,365 / 322,084 (`FlinsParty`), and 22,146,093 / 320,493
  (`FlinsParty2`).

## Implementation Order: Columbina Basic Action Coverage

Status: Complete. Implemented B-145 as one character-local phase; stamina,
low Plunge and collision hits, measured animation frames, Lunar engine changes,
new rotations, RL, and generated docs are excluded.

Evidence:

- `config/characters/Columbina/Columbina_Multipliers.csv` contains the level-9
  N1-N3, standard Charged, Plunge, Skill, Burst, and Moondew values. Current
  Genshin Wiki combat data and the KQM quick guide, accessed 2026-08-02, specify
  their action categories, elemental application, and Lunar-Bloom replacement:
  https://genshin-impact.fandom.com/wiki/Columbina
  https://keqingmains.com/q/columbina-quickguide/

### Phase 1: Add Typed Basic Action Dispatch - Done

Target files:

- `src/java/model/character/Columbina.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Repeated `NORMAL` input advances N1-N3 and wraps, using the corresponding
  sourced multiplier, Hydro Normal metadata, standard shared ICD, and 1U.
- Standard Charged and high Plunge inputs expose sourced multipliers with
  Charged/Plunging bonus categories, typed actions, no ICD, and 1U; any
  non-Normal input resets the combo.
- Skill and Burst use Skill/Burst bonus categories and action types while
  preserving their existing scaling, durations, application, and lifecycle.
- Moondew Cleanse resolves three Lunar-Bloom direct-damage hits under one typed
  Charged input at one timestamp, excludes ordinary attack bonuses and hit
  hooks, applies 0U/no ICD, consumes one Dew, and advances one 1.5-second action
  duration.
- Unsupported typed input fails explicitly instead of silently doing nothing.

Test cases:

- Normal: N1-N3/wrap, standard Charged, high Plunge, Skill, Burst, and Moondew.
- Boundary: combo interruption, three same-time Moondew hits, one duration,
  one Dew consumption, and category-specific resolved bonuses.
- Abnormal: unsupported Dash, no-Dew Charged fallback, and 0U Moondew Aura
  preservation.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew PartyCatalogRegressionTest`
- representative party samples twice
- `python scripts/preflight.py --run`

Completion evidence:

- Columbina now exposes sourced N1-N3, standard Charged, high Plunge, Skill,
  and Burst multipliers with correct typed bonus, ICD, gauge, scaling, combo,
  and duration metadata.
- Moondew Cleanse resolves three same-time 0U Lunar-Bloom damage instances,
  emits one logical action, advances one duration, consumes one Dew, excludes
  ordinary attack categories, and cannot regenerate Dew from its own direct
  Lunar notification; zero-damage stateful Lunar-Bloom still grants Dew.
- Independent review found the stateful-Bloom and ordinary-Charged category
  risks; both were corrected before closure.
- Reaction regression, build, Javadoc, party catalog, and representative
  samples pass. Two runs each reproduce 1,275,070 / 60,718 (`RaidenParty`),
  32,047,365 / 322,084 (`FlinsParty`), and 22,146,093 / 320,493
  (`FlinsParty2`).

## Implementation Order: Global Noblesse Burst Dispatch

Status: Complete. Implemented B-146 as one backward-compatible action-gateway
phase; character-specific self-snapshot exceptions, new artifact sets, RL, and
generated docs are excluded.

Evidence:

- Current KQM artifact documentation and evidence vault, accessed 2026-08-02,
  specify that Noblesse activates on Burst use, shares one refreshable duration,
  and applies to most triggering Bursts before their damage snapshots:
  https://library.keqingmains.com/equipment/artifacts
  https://library.keqingmains.com/evidence/equipment/artifacts

### Phase 1: Route Burst Artifact Effects Through Action Gateway - Done

Target files:

- `src/java/simulation/runtime/ActionGateway.java`
- `src/java/model/character/Amber.java`
- `src/java/model/character/Bennett.java`
- `src/java/model/character/Kaeya.java`
- `src/java/model/character/Lisa.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- After Burst cooldown and Energy gates pass, every equipped
  `BurstTriggeredArtifactEffect` receives one callback before character Burst
  logic, direct damage, snapshots, and animation advancement.
- Amber, Bennett, Kaeya, and Lisa retain one callback after their manual loops
  are removed; all other supported characters gain the same route.
- Insufficient-Energy Burst requests, Skill/Normal input, missing/null artifact
  arrays, and plain artifacts do not invoke the capability.
- Noblesse retains one typed nonstacking 20% ATK team buff, refreshes its
  twelve-second duration, and keeps source ownership through the gateway.

Test cases:

- Normal: legacy four characters plus Xingqiu and Columbina invoke once.
- Boundary: callback-before-character ordering and 11.999/12.000 refresh.
- Abnormal: insufficient Energy, non-Burst, null/plain artifacts, and no
  duplicate callback on Bennett.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew PartyCatalogRegressionTest`
- representative party samples twice
- `python scripts/preflight.py --run`

Completion evidence:

- The post-gate action gateway now invokes each Burst artifact capability once
  before character Burst logic; four character-local loops were removed.
- Focused regression covers Amber, Bennett, Kaeya, Lisa, Xingqiu, Columbina,
  callback-before-character ordering, Energy rejection, non-Burst input,
  null/plain artifacts, Noblesse source ownership, refresh, and exact expiry.
- Reaction regression, build, Javadoc, party catalog, and representative
  samples pass. Two runs each reproduce 1,273,211 / 60,629 (`RaidenParty`),
  32,047,365 / 322,084 (`FlinsParty`), and 22,146,093 / 320,493
  (`FlinsParty2`). Raiden decreases by 1,859 damage because Noblesse now starts
  at Bennett Burst input rather than after its 0.8-second animation.

## Implementation Order: Physical Attack Category Corrections

Status: Complete. Implemented B-147 as one four-character metadata correction;
new infusion persistence systems, stamina, animation retiming, weapon changes,
RL, and generated docs are excluded.

Evidence:

- `StandardDamageStrategy` already resolves the hit element's Physical/Pyro
  bonus before the action-specific bonus, so passing `PHYSICAL_DMG_BONUS` as
  `bonusStat` doubles Physical bonus and drops Normal/Charged bonus.
- Current KQM Bennett data and weapon-infusion documentation, accessed
  2026-08-02, confirm separate Normal/Charged categories and C6-only Pyro
  infusion for Normal, Charged, and Plunging attacks:
  https://library.keqingmains.com/characters/pyro/bennett
  https://library.keqingmains.com/combat-mechanics/elemental-effects/weapon-infusion

### Phase 1: Separate Element and Action Damage Bonuses - Done

Target files:

- `src/java/model/character/Bennett.java`
- `src/java/model/character/Xiangling.java`
- `src/java/model/character/Xingqiu.java`
- `src/java/model/character/RaidenShogun.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Every physical Normal uses `NORMAL_ATTACK_DMG_BONUS`; every physical Charged
  hit uses `CHARGED_ATTACK_DMG_BONUS`, while the Physical element continues to
  contribute Physical DMG Bonus exactly once through the standard formula.
- Bennett C0-C5 remains Physical inside Fantastic Voyage; C6 converts Normal,
  Charged, and Plunging damage to Pyro without replacing their action bonus.
- Bennett/Xingqiu two-hit Charged attacks retain one logical duration, shared
  typed ICD/gauge, and exact multipliers; Xiangling and Raiden retain existing
  timing and combo reset behavior.
- Musou Isshin attacks remain Burst damage and are not changed by the physical
  routing correction.

Test cases:

- Normal: metadata matrix for all four characters and C0/C6 Bennett field.
- Boundary: two-hit ordering/duration, Raiden physical versus Musou category,
  and Bennett pre-field/in-field element transition.
- Abnormal: Physical bonus is never supplied as the action bonus; unrelated
  Skill/Burst/Plunge categories remain unchanged.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew PartyCatalogRegressionTest`
- representative party samples twice
- `python scripts/preflight.py --run`

Completion evidence:

- Bennett, Xiangling, Xingqiu, and Raiden physical attacks now obtain Physical
  bonus once from their element and Normal/Charged bonus from their action
  category; no character still passes Physical bonus for a typed Normal/Charged
  hit.
- Bennett C0 field Normal/Charged/Plunge stays Physical while C6 converts all
  three to Pyro without replacing action bonuses. Two-hit metadata, ICD, gauge,
  same-time resolution, and one logical duration are covered for Bennett and
  Xingqiu; Raiden Musou remains Burst damage.
- Reaction regression, build, Javadoc, party catalog, and representative
  samples pass. Two runs each reproduce 1,273,211 / 60,629 (`RaidenParty`),
  32,047,365 / 322,084 (`FlinsParty`), and 22,146,093 / 320,493
  (`FlinsParty2`).

## Implementation Order: Expanded Artifact Coverage Campaign

Status: Complete. This campaign adds six missing four-piece artifact sets
whose complete combat effects fit existing stat, energy, reaction, damage,
party-buff, and enemy-aura contracts; RL and generated docs remain excluded.

Scope:

- Add Wanderer's Troupe and Finale of the Deep Galleries for weapon- and
  energy-state conditionals.
- Add Instructor and Deepwood Memories for reaction and team-debuff support.
- Add Blizzard Strayer and Nymph's Dream for live target state and independent
  action-category stacks.

Out of scope for this pass:

- Healing/current HP, shields, enemy defeat, incoming damage, Nightsoul,
  multi-target geometry, action-cast contract expansion, RL, and generated docs.

Definitions:

- Each class represents an equipped four-piece set and includes its two-piece
  fixed stats, matching the existing artifact abstraction.

### Phase 1: Weapon and Energy-State Sets - Done

Why first:

- These owner-local sets need no team-global typed buff identity and establish
  metadata/constructor patterns for the batch.

Target files:

- `src/java/model/artifact/WanderersTroupe.java` (new)
- `src/java/model/artifact/FinaleOfTheDeepGalleries.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Implement supplied/fresh stats, canonical names, strict binding, and exact
  weapon or current-energy conditionals.
- Track Finale's six-second cross-category lockouts after Normal/Burst damage.

Acceptance criteria:

- Wanderer's Troupe grants EM +80 and Charged Attack DMG +35% only to Bow or
  Catalyst owners.
- Finale grants Cryo DMG +15%; at zero Energy it grants Normal and Burst DMG
  +60%, while Normal/Burst hits suppress the opposite bonus for six seconds.

Test cases to add or update:

- Normal: metadata, supplied stats, all weapon gates, zero/nonzero Energy, and
  both Finale hit directions.
- Boundary: exact six-second lock expiry and off-field Burst damage.
- Abnormal: null stats, wrong callback binding, duplicate/cross-simulator init,
  non-Normal/Burst and zero-damage hit classifications.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Table-driven weapon gates plus Finale energy, category, off-field, exact
  six-second lock, invalid callback, binding, metadata, and supplied-stat
  regressions pass.

### Phase 2: Reaction and Dendro Support Sets - Done

Why second:

- These sets share post-resolution ordering and typed nonstacking team effects.

Target files:

- `src/java/model/artifact/Instructor.java` (new)
- `src/java/model/artifact/DeepwoodMemories.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Implement Instructor's on-field owner reaction refresh and team EM window.
- Implement Deepwood's Skill/Burst hit-triggered party Dendro RES shred.

Acceptance criteria:

- Instructor grants EM +80 and refreshes one typed party EM +120 window for
  eight seconds after an on-field owner reaction, after the triggering reaction.
- Deepwood grants Dendro DMG +15% and applies one typed Dendro RES shred +30%
  for eight seconds after owner Skill/Burst hits, including off-field hits.

Test cases to add or update:

- Normal: owner/on-field reaction, off-field Skill/Burst hit, all-party scope,
  and fixed two-piece stats.
- Boundary: exact eight-second expiry/refresh and trigger-hit exclusion.
- Abnormal: NONE/wrong/off-field Instructor reaction, wrong/non-Skill/Burst
  Deepwood hit, same-id replacement, and cross binding.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Instructor owner/field/NONE, typed refresh, and exact expiry plus Deepwood
  off-field/zero-damage categories, party scope, typed refresh, and exact
  expiry regressions pass.
- Both phases pass `./gradlew ReactionRegressionTest`, `./gradlew build`,
  `./gradlew javadoc`, and `python scripts/preflight.py` on 2026-08-02.

### Phase 3: Target State and Independent Hit Categories - Done

Why third:

- Live pre-hit aura/freeze reads and independent category windows carry the
  highest ordering risk in this batch.

Target files:

- `src/java/model/artifact/BlizzardStrayer.java` (new)
- `src/java/model/artifact/NymphsDream.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Resolve Blizzard bonuses from live pre-hit Cryo aura and literal Freeze state.
- Track Nymph Normal, Charged, Plunging, Skill, and Burst eight-second windows
  independently and map active-category count to exact ATK/Hydro tiers.

Acceptance criteria:

- Blizzard grants Cryo DMG +15%, CRIT Rate +20% against Cryo, and +40% against
  literally Frozen targets without snapshotting or buffing the Freeze-applying hit.
- Nymph grants Hydro DMG +15%; one/two/three-or-more active categories grant
  ATK +7/16/25% and Hydro DMG +4/9/15%, with same-category refresh only.

Test cases to add or update:

- Normal: Cryo/Frozen/clear target states and every Nymph category.
- Boundary: pre-hit Freeze/removal ordering and exact eight-second independent
  refresh/expiry tiers.
- Abnormal: null enemy/simulator, wrong owner/simulator, unclassified/zero hit,
  duplicate binding, and independent instances.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Blizzard clear/Cryo/Frozen/applying-hit/null-enemy and binding regressions
  pass; Nymph metadata, all categories, tier cap, same-category refresh,
  staggered exact expiry, invalid callback, and independent-state regressions
  pass with reaction regression, build, Javadoc, and preflight.

## Implementation Order: Action-Use Artifact Campaign

Status: Complete. This campaign adds the missing artifact action-use
capability and two complete Skill-activated sets; RL and generated docs remain
excluded.

Scope:

- Dispatch successful typed character actions to equipped artifact sets.
- Add Heart of Depth and Martial Artist through the shared capability.

Out of scope for this pass:

- Plunging-specific stats, current HP, healing, shields, enemy defeat,
  Nightsoul, RL, and generated docs.

Definitions:

- `ActionTriggeredArtifactEffect`: owner-local artifact capability called
  after action gates pass and before the character resolves the action.

### Phase 1: Shared Artifact Action Contract - Done

Why first:

- Skill-use sets need one runtime-owned callback instead of artifact-specific
  branches in character classes.

Target files:

- `src/java/model/entity/ActionTriggeredArtifactEffect.java` (new)
- `src/java/model/entity/ArtifactSet.java`
- `src/java/simulation/runtime/ActionGateway.java`
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Dispatch every equipped implementing artifact in array order after action
  cooldown/energy gates and the weapon callback, before character resolution.
- Preserve no-op behavior for null arrays, null entries, and plain sets.

Acceptance criteria:

- One accepted request produces exactly one owner/request/simulator callback
  per implementing set; a rejected Burst produces none.
- Existing weapon-before-character ordering remains intact and artifact
  callbacks precede character resolution.

Test cases to add or update:

- Normal: owner/request identity, multiple-set order, and weapon/artifact/
  character ordering.
- Boundary: all typed action keys dispatch through the same contract.
- Abnormal: insufficient-Energy Burst, null entry, plain set, and no artifacts.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Accepted requests dispatch weapon, artifact sets in array order, then the
  character; all action keys, rejected Burst, null/plain/no-set cases pass.
- Reaction regression, build, Javadoc, and executable preflight passed on
  2026-08-02.

## Implementation Order: Husk Curiosity Campaign

Status: Complete. This campaign adds one complete stateful artifact set;
RL and generated docs remain excluded.

Scope:

- Add Husk of Opulent Dreams fixed stats, field-aware Curiosity gains, stack
  bonuses, and no-gain decay.

Out of scope for this pass:

- Artifact unequip/party removal, multi-target identity, shields, incoming
  damage, RL, and generated docs.

Definitions:

- `Curiosity`: at most four owner-local stacks, each granting DEF +6% and Geo
  DMG +6% in addition to the fixed DEF +30% two-piece bonus.

### Phase 1: Field-Aware Gain and Decay - Done

Why:

- Gain, refresh, switch, and decay timers form one inseparable state machine.

Target files:

- `src/java/model/artifact/HuskOfOpulentDreams.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Gain or refresh once per 0.3 seconds from owner Geo hits while on field,
  including zero-damage hits.
- Gain or refresh every three seconds off field, restarting that cadence after
  field transitions.
- Lose the first stack after six seconds without gain and further stacks at
  the sourced three-second decay cadence; invalidate stale scheduled events
  with generation tokens.

Acceptance criteria:

- Fixed and per-stack stats reach exact one-to-four-stack values and cap.
- On-field, off-field, switch-reset, refresh, and decay state remain owner- and
  simulator-local at exact time boundaries.
- Event rescheduling never depends on mutating a queued event's sort key.

Test cases to add or update:

- Normal: fixed stats, Geo-hit gain, off-field 3/6/9/12-second gains, cap, and
  one-to-four-stack stat values.
- Boundary: 0.3-second CT, six-second first decay, three-second later decay,
  on/off-field timer restart, and gain at a coincident boundary.
- Abnormal: zero damage, non-Geo/wrong owner/simulator, null stats, duplicate
  binding, stale events after switch/refresh, and independent instances.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Fixed/per-stack stats, zero-damage Geo hits, 0.3-second CT, off-field
  cadence, cap refresh, switch reset, and exact decay regressions pass.
- Coincident gain supersedes decay through immutable one-shot events and stale
  token rejection; reaction regression, build, Javadoc, and preflight pass.

## Implementation Order: Supported Character Passive Campaign

Status: Complete. This campaign adds or corrects sourced passives and
constellations in three already-supported characters; RL and generated docs
remain excluded.

Scope:

- Apply Bennett's unconditional A1 Skill cooldown reduction.
- Add Xiangling C2's delayed live-stat Implode damage.
- Correct Xingqiu's constellation gates, durations, hit-applied C2 shred, C4
  multiplier, C6 wave cycle, and owner-only Energy.

Out of scope for this pass:

- Healing, shield/interruption, player HP, enemy defeat, multi-target state,
  Bennett A4/C6 infusion redesign, RL, and generated docs.

Definitions:

- `Implode`: one unsnapshotted Pyro `OTHER` hit with no ability-type bonus,
  scheduled two seconds from Xiangling's N5 hit time.
- `Raincutter C2 shred`: one typed, nonstacking four-second Hydro RES reduction
  applied after a sword-rain wave hits.

### Phase 1: Independent Bennett and Xiangling Passives - Done

Why first:

- These disjoint character-local changes have no shared runtime prerequisite.

Target files:

- `src/java/model/character/Bennett.java`
- `src/java/model/character/Xiangling.java`
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Set Bennett's ascended Tap Skill base cooldown to four seconds.
- On Xiangling C2+, schedule one 75% current-ATK Pyro Implode exactly two
  seconds from each N5 hit, with no snapshot or ability-type bonus.

Acceptance criteria:

- Bennett's Skill becomes ready at four seconds and remains gated before it.
- C0/C1 Xiangling never schedules Implode; C2+ schedules exactly one per N5,
  resolves at hit+2, and reads buffs active at resolution.

Test cases to add or update:

- Normal: Bennett t=4 reuse; Xiangling C2 N1-N5 sequence and Pyro damage.
- Boundary: t=3.999/4 and Implode t=1.999/2 from captured hit time.
- Abnormal: Xiangling C0, early combo hits, no Normal/Skill/Burst bonus,
  non-snapshot stat change, and repeated attack strings.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Bennett's A1 cooldown boundary and repeated cast pass at t=3.999/4.000.
- Xiangling C2 schedules one live-stat, no-ability-tag Implode per N5 at
  hit+2 seconds; early hits, C0, timing, and repeated strings are covered.
- Reaction regression, build, Javadoc, and preflight pass.

### Phase 2: Xingqiu Constellation Ordering - Done

Why second:

- Xingqiu's form duration, wave listener, typed shred, and Energy ownership
  must be corrected as one ordering contract.

Target files:

- `src/java/model/character/Xingqiu.java`
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Use 15-second C0/C1 and 18-second C2+ Burst/orbital/form windows.
- Apply C2 Hydro RES -15% for four seconds after each sword-rain wave, not on
  cast; refresh one typed effect.
- Gate C4's multiplicative Skill x1.5 and C6's 2-3-5 cycle plus owner-only
  three flat Energy to their actual constellation levels.

Acceptance criteria:

- C0 uses 2-3 waves, 15 seconds, no shred, no C4 multiplier, and no C6 Energy.
- C2 uses 18 seconds and applies shred only after a wave; C4 multiplies both
  Skill hits only during form; C6 third-wave hit grants only Xingqiu 3 Energy.
- Recasts restart wave/cooldown state without duplicate active effects.

Test cases to add or update:

- Normal: C0/C2/C4/C6 matrices, wave sizes, duration, Skill damage, shred,
  owner/ally Energy, and repeated Burst cycles.
- Boundary: exact 15/18-second form and four-second shred expiry/refresh.
- Abnormal: pre-wave C2, C3 no C4, C5 no C6, non-Normal triggers, and no
  party-wide Energy.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- C0-C6 duration, wave, C2 post-wave shred/refresh, C4 multiplier, C6
  post-hit owner-only Energy, listener generation, and orbital replacement
  regressions pass at their normal, exact-boundary, and rejected-trigger cases.
- `RaidenParty` changed deterministically from 1,272,998 damage / 60,619 DPS
  at `6f8177c` to 1,227,785 damage / 58,466 DPS. The decrease includes removal
  of the erroneous C6 party-wide Energy that had sustained Xiangling damage.
- Reaction regression, build, Javadoc, sample simulation, and preflight pass.

## Implementation Order: Flins Constellation Campaign

Status: Complete. This campaign implements sourced, single-target Flins
passives and constellations while retaining the documented weighted Lunar
reaction simplification; RL and generated docs remain excluded.

Evidence:

- KQM Flins character reference and Luna VI Quick Guide, accessed 2026-08-02,
  provide A1/C1-C6 values, trigger categories, windows, caps, and talent tables:
  https://library.keqingmains.com/characters/electro/flins
  https://keqingmains.com/q/flins-quickguide/
- The maintained KQM Flins evidence vault is empty. C2's direct follow-up uses
  the Genshin Wiki advanced-property 0U/no-ICD record, accessed 2026-08-02:
  https://genshin-impact.fandom.com/wiki/The_Devil%27s_Wall

Out of scope:

- exact multi-target ownership, positioning, shield/damage intake, RL,
  generated docs, and unsupported ordering not established by the sources.

### Phase 1: Simulator Binding, A1, and C1 Lifecycle - Done

Target files:

- `src/java/model/entity/SimulatorInitializedCharacterEffect.java` (new)
- `src/java/simulation/CombatSimulator.java`
- `src/java/model/character/Flins.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Character initialization binds Flins before any party reaction; duplicate
  initialization is idempotent and cross-simulator reuse fails explicitly.
- C1 restores 8 flat Energy for actual Lunar-Charged reactions before Flins's
  first action at 5.5-second CT; C0, standard Electro-Charged, NONE, and direct
  synthetic Lunar damage do not trigger it.
- A1 grants Flins 20% Lunar-Charged reaction bonus only at Ascendant Gleam and
  no longer uses the separate Lunar base-section unique category.

Test cases:

- Normal: pre-action C1 reaction and A1 direct Lunar stat resolution.
- Boundary: C1 5.499/5.500, duplicate binding, and Moonsign transitions.
- Abnormal: C0, standard/synthetic/wrong reactions, foreign simulator reuse.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew FlinsParty2`
- `python scripts/preflight.py`

Completion evidence:

- Pre-action C1 registration, C0 and wrong/synthetic reaction rejection,
  5.499/5.500-second Energy CT, idempotent binding, cross-simulator rejection,
  and live Nascent/Ascendant A1 category regressions pass.
- `FlinsParty2` changed from 15,817,125 damage / 228,902 DPS at `c2f876b`
  to 15,468,205 / 223,852 after removing the erroneous Nascent unique bonus.
- Reaction regression, build, Javadoc, sample simulation, and preflight pass.

### Phase 2: C2 Follow-Up and C4 Passive

Status: Done.

Target files:

- `src/java/model/character/Flins.java`
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- C2 opens one six-second Normal-hit follow-up per Spearstorm; the 50% ATK
  direct Lunar-Charged Electro hit is 0U/no-ICD, consumes once, and cannot
  recurse or change Aura.
- At Ascendant Gleam, an on-field Flins Electro hit refreshes one seven-second
  25% Electro RES reduction after that triggering hit; Nascent/off-field/wrong
  elements never apply it.
- C4 adds ATK +20% and changes Whispering Flame to 10% ATK capped at 220 EM;
  C3 retains 8%/160 with no C4 ATK.

Test cases:

- Normal: C2 proc/order/shred and C4 uncapped/capped stats.
- Boundary: C2 t=5.999/6, shred t=6.999/7, and EM 219.9/220 cap.
- Abnormal: C0/C1, Physical/non-Normal, off-field/Nascent, zero/reentrant hits.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew FlinsParty2`
- `python scripts/preflight.py`

Evidence:

- C2 one-shot follow-up, 5.999/6-second consumption, typed shred refresh,
  Nascent/off-field rejection, 6.999/7-second expiry, and C4 219.9/220 EM cap
  regressions pass.
- `FlinsParty2` remains deterministic at 15,468,205 damage / 223,852 DPS in
  two runs because its current Flins configuration does not enable C2/C4.
- Reaction regression, build, Javadoc, sample simulation, and preflight pass.

### Phase 3: C3/C5 Talent Levels and C6 Elevation

Status: Done.

Target files:

- `src/java/model/character/Flins.java`
- `src/java/mechanics/buff/BuffId.java`
- `src/java/mechanics/formula/DamageCalculator.java`
- `src/java/mechanics/formula/LunarDamageStrategy.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `config/characters/Flins/Flins_Multipliers.csv`
- `src/java/model/type/StatType.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- C3 selects level-12 Burst/Symphony values without changing Skill/form values;
  C5 selects level-12 Skill/form values without changing Burst values.
- Manifest N4 represents two separate level-table hit multipliers rather than
  executing a summed two-hit value twice.
- C6 adds 35% personal Lunar-Charged elevation and, only at Ascendant Gleam,
  another 10% party elevation through the existing Lunar multiplier model;
  ordinary Electro damage is unchanged.

Test cases:

- Normal: exact C3/C5 action multipliers and C6 personal/team resolution.
- Boundary: each constellation threshold and N4 two-hit sum.
- Abnormal: C3 Skill, C5 Burst, Nascent party C6, and ordinary damage category.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew FlinsParty2`
- `python scripts/preflight.py`

Evidence:

- Exact C2/C3 Burst, C4/C5 Skill, physical/Manifest N4 per-hit, C6
  Nascent/Ascendant/return, typed ownership, weighted reaction, and ordinary
  Electro regressions pass.
- Weighted Lunar reaction candidates now resolve applicable character, weapon,
  artifact, team, and field buffs before ranking; expired buffs remain excluded.
- Two `FlinsParty2` runs match at 20,805,526 damage / 301,093 DPS, up from
  15,468,205 / 223,852 after provider buffs became visible to weighted Lunar
  reaction damage.
- Reaction regression, build, Javadoc, sample simulation, and preflight pass.

## Implementation Order: Ineffa Representable Constellation Campaign

Status: Complete. Implemented C1, the independently representable C2 shield,
C3-C6, and exact talent data; C2 Punishment Edict damage remains in blocked
backlog B-135 rather than inventing its unspecified delay.

Scope:

- Preserve the existing single-target and weighted Lunar reaction model.
- Reuse simulator initialization and reaction events for pre-action C4 and
  post-Thundercloud C6 behavior.
- Keep RL and generated documentation excluded.

### Phase 1: C1/C2 Shield and C4 Lifecycle

Status: Done.

Target files:

- `src/java/mechanics/buff/BuffId.java`
- `config/characters/Ineffa/Ineffa_Status.csv`
- `src/java/model/character/Ineffa.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Ineffa reads typed constellation data and binds idempotently before actions,
  rejecting cross-simulator reuse.
- Skill at C1+ and Burst at C2+ activate the shield and refresh one sourced,
  typed 20-second Carrier Flow Composite team buff using activation-time ATK;
  Lunar-Charged bonus scales at 2.5% per 100 ATK and caps at 50%.
- C4 restores exactly 5 flat Energy on a real party Lunar-Charged trigger with
  a four-second cooldown; direct/synthetic/wrong reactions do not restore.

Test cases:

- Normal: Skill/Burst Carrier activation, refresh, source, and C4 party trigger.
- Boundary: C1 49.9/50% cap, buff 19.999/20, C4 3.999/4 seconds.
- Abnormal: C0/C1 Burst, wrong/zero/direct reactions, duplicate initialization,
  and cross-simulator reuse.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew FlinsParty2`
- `python scripts/preflight.py`

Evidence:

- Typed C0-C4 configuration, C1 skill/C2 Burst activation and refresh,
  49.9/50% cap, 19.999/20-second expiry, C4 wrong/synthetic rejection,
  3.999/4-second cooldown, snapshot restore, and cross-simulator regressions
  pass.
- C1 uses activation-time ATK because maintained sources do not yet establish
  dynamic ATK following; B-135 retains only the unsourced C2 delayed damage.
- Two C0 `FlinsParty2` runs remain 20,805,526 damage / 301,093 DPS.
- Reaction regression, build, Javadoc, sample simulation, and preflight pass.

### Phase 2: C3/C5 Talent Levels

Status: Done.

Target files:

- `config/characters/Ineffa/Ineffa_Multipliers.csv`
- `src/java/model/character/Ineffa.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- C3 selects level-12 Skill, shield, and Birgitta values while C2 retains
  level 9; C5 selects level-12 Burst while C4 retains level 9.
- Distinct CSV keys avoid the current level-column overwrite limitation.
- Skill, Burst, Birgitta cadence, gauge, ICD, and shield duration remain
  unchanged apart from sourced multipliers.

Test cases:

- Normal: exact C2/C3 Skill/shield/Birgitta and C4/C5 Burst values.
- Boundary: each constellation threshold and ten-hit Birgitta stream.
- Abnormal: C3 Burst and C5 Skill do not gain an extra talent level.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew FlinsParty2`
- `python scripts/preflight.py`

Evidence:

- Exact C2/C3 Skill, shield, and Birgitta values plus C3/C4/C5 Burst threshold
  regressions pass; C3 preserves ten Birgitta hits at two-second cadence and C5
  does not raise Skill beyond C3.
- Two C0 `FlinsParty2` runs match at 20,805,520 damage / 301,093 DPS; the
  six-damage change is the sourced Lv9 decimal correction.
- Reaction regression, build, Javadoc, sample simulation, and preflight pass.

### Phase 3: C6 Thundercloud Follow-Up

Status: Done.

Target files:

- `src/java/model/character/Ineffa.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- While Carrier Flow Composite is active, a resolved Thundercloud strike emits
  one 135% ATK direct Lunar-Charged Electro hit near the active character.
- The hit is owned by Ineffa, 0U/no-ICD, cannot recurse, and uses a 3.5-second
  cooldown independent from C4.
- C5, expired Carrier, ordinary Lunar-Charged triggers, and synthetic zero
  events do not emit the C6 follow-up.

Test cases:

- Normal: real Thundercloud tick and off-field Ineffa ownership.
- Boundary: 3.499/3.5-second cooldown and Carrier exact expiry.
- Abnormal: C5, no/expired Carrier, wrong event kind, zero-gauge Aura safety,
  and no recursive second action.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew FlinsParty2`
- `python scripts/preflight.py`

Evidence:

- A real scheduled Thundercloud tick triggers one off-field Ineffa follow-up
  while Carrier Flow Composite is active; C5, absent or expired Carrier,
  ordinary Lunar-Charged, and zero-damage synthetic events remain inert.
- The follow-up is an Ineffa-owned 135% ATK direct Lunar-Charged Electro hit
  with 0U/no-ICD metadata and Aura preservation. Its typed 3.5-second cooldown
  rejects the immediate and 3.499-second cases, refreshes at 3.5 seconds,
  survives snapshot restore, and does not recurse.
- Two C0 `FlinsParty2` runs match at 20,805,520 damage / 301,093 DPS.
- Reaction regression, build, Javadoc, sample simulation, and preflight pass.

## Implementation Order: Ineffa Normal Attack 3 Multi-Hit Accuracy

Status: Complete. Corrected B-136 as one content-local phase without changing
shared action, ICD, rotation, RL, or report contracts.

### Phase 1: Split N3 Damage Events

Status: Done.

Target files:

- `config/characters/Ineffa/Ineffa_Multipliers.csv`
- `src/java/model/character/Ineffa.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Ineffa normal attack 3 resolves two independently named 41.8% Physical
  normal hits while other combo steps retain one hit each.
- Both hits retain standard normal-attack ICD metadata and 1U application;
  only the second hit advances the timeline and emits the logical normal-action
  notification, preserving one 0.3-second action duration.
- The combo advances to N4 once after the pair and wraps normally after N4.

Test cases:

- Normal: exact N1/N2/N3x2/N4 hit names, multipliers, elements, and sequence.
- Boundary: N3 advances 0.3 seconds once and then selects N4 exactly once.
- Abnormal: N1, N2, and N4 are not duplicated; both N3 hits preserve the
  shared standard NormalAttack ICD contract.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew FlinsParty2`
- `python scripts/preflight.py`

Evidence:

- The four-step combo now resolves N1, N2, two independently named 41.8% N3
  hits, and N4 in order, then wraps exactly to N1.
- Both N3 hits resolve at the same action-start timestamp with Physical normal,
  standard NormalAttack ICD, and 1U metadata; only the second emits the logical
  action event and advances the timeline, preserving one 0.3-second duration.
- Two `FlinsParty2` runs remain 20,805,520 damage / 301,093 DPS.
- Reaction regression, build, Javadoc, sample simulation, and preflight pass.

## Implementation Order: Raiden Constellation Lifecycle

Status: Complete. This pass implements sourced, representable C1 and C4
behavior without changing RL or generated documentation.

Scope:

- Multiply Chakra Desiderata Resolve from Electro ally Bursts by 1.8 and all
  other elements by 1.2 at C1+ without rounding fractional stacks.
- At C4+, apply one typed, nonstacking 10-second ATK +30% team buff excluding
  Raiden when Musou Isshin ends normally or through a standard switch.
- Start Musou Isshin's sourced seven-second duration after the Burst cast
  animation resolves, so normal-end C4 timing shares the same lifecycle.

Out of scope:

- C6 Burst cooldown reduction, interruption resistance, immunity, player
  damage, direct active-character setters, RL, and generated docs.

Evidence:

- KQM Raiden Shogun character reference and evidence vault, accessed
  2026-08-02, specify C1's 80% Electro/20% other Resolve increases, decimal
  stack preservation, and C4's 30% ATK for other party members for 10 seconds
  when Musou Isshin expires:
  https://library.keqingmains.com/characters/electro/raiden-shogun
  https://library.keqingmains.com/evidence/characters/electro/raiden-shogun

### Phase 1: C1 Resolve and C4 End-State Buff - Done

Target files:

- `src/java/model/character/RaidenShogun.java`
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- C0 gains the existing base Resolve; C1+ gains x1.8 from Electro and x1.2
  from non-Electro ally Burst casts, preserving decimals and the 60-stack cap.
- C4 applies exactly once on normal expiry or early standard switch, excludes
  Raiden, refreshes rather than stacks, remains active before 10 seconds, and
  is absent at exact expiry; C3 never applies it.
- Normal Musou Isshin expiry occurs seven seconds after the initial Burst
  action finishes; a stale timer from a replaced or early-ended state is inert.

Test cases:

- Normal: C0/C1 elemental Resolve matrix and post-cast C4 normal expiry.
- Boundary: fractional Resolve, 60-stack cap, and C4 t=9.999/10.
- Abnormal: own Burst ignored, multi-hit Burst credited once, C3, early switch,
  stale timer after early end, exclusion, and repeated windows.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew RaidenParty`
- `python scripts/preflight.py`

Completion evidence:

- C0/C1 element multipliers, decimal preservation, 60-stack cap, own and
  repeated-hit rejection, C3 exclusion, C4 normal/early/replacement end paths,
  ally targeting, one typed window, and exact expiry regressions pass.
- Two post-fix `RaidenParty` runs both produced 1,271,521 damage / 60,549 DPS,
  versus 1,227,785 / 58,466 before the sourced post-cast timer correction.
- Reaction regression, build, Javadoc, sample simulation, and preflight pass.

## Implementation Order: Raiden Multi-Hit Accuracy

Status: Complete. Corrected B-137 in one character-local phase; retained the
single-target model and excluded C6 cooldown reduction, RL, and generated docs.

Evidence:

- The maintained KQM Raiden table, accessed 2026-08-02, lists physical N4 as
  53.25% + 53.25%, Musou N4 as 51.95% + 52.10%, Musou Charged as 103.6% +
  125.06%, and 1.23% Resolve scaling on each Musou hit:
  https://library.keqingmains.com/characters/electro/raiden-shogun

### Phase 1: Split N4 and Musou Charged Damage Events

Status: Done.

Target files:

- `config/characters/RaidenShogun/RaidenShogun_Multipliers.csv`
- `src/java/model/character/RaidenShogun.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Physical N4, Musou N4, and Musou Charged each resolve their two sourced
  multipliers as independent same-action damage events.
- Every Musou hit receives Resolve scaling independently, retains Burst damage
  classification and C2 DEF ignore, and shares the Musou standard ICD group.
- Only each pair's final hit advances time and emits the logical action event;
  other normal steps and physical Charged remain single-hit.

Test cases:

- Normal: exact per-hit physical N4, Musou N4, and Musou Charged values with
  zero and nonzero Resolve.
- Boundary: one animation duration per pair and third-hit standard ICD behavior
  across N1 plus the two Charged hits.
- Abnormal: N1/N2/N3/N5 and physical Charged are not duplicated; both Musou
  hits retain C2 DEF ignore and Burst classification.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew RaidenParty`
- `python scripts/preflight.py`

Evidence:

- Physical N4 resolves two 53.25% hits; Musou N4 resolves 51.95% and 52.10%,
  and Musou Charged resolves 103.6% and 125.06% as independent same-timestamp
  damage events with only the final hit advancing the action duration.
- A 14.4-Resolve C2 fixture applies 1.23% per stack to every Musou hit; both
  hits retain Burst classification, 60% DEF ignore, and shared Musou standard
  ICD. N1 plus the two Charged hits reaches the standard third-hit application.
- N1/N2/N3/N5 and physical Charged remain single-hit and logical action
  listeners still receive one event per player action.
- Two `RaidenParty` runs match at 1,275,070 damage / 60,718 DPS, up from
  1,271,521 / 60,549 after per-hit Resolve and trigger resolution.
- Reaction regression, build, Javadoc, sample simulation, and preflight pass.

## Implementation Order: Lisa C6 Switch-In Lifecycle

Status: Complete. B-138 uses a narrow shared switch capability followed by
Lisa-owned typed state; the stationary single-target model remains intact and
co-op, out-of-combat party setup, RL, and generated docs remain excluded.

Evidence:

- The maintained KQM Lisa page and evidence vault, accessed 2026-08-02, state
  that C6 switch-in applies three simultaneous Conductive stacks, refreshes all
  current stacks to 15 seconds, and has a five-second in-combat cooldown:
  https://library.keqingmains.com/characters/electro/lisa
  https://library.keqingmains.com/evidence/characters/electro/lisa

### Phase 1: Character Switch-In Capability

Status: Done.

Target files:

- `src/java/model/entity/SwitchAwareCharacter.java`
- `src/java/simulation/runtime/SwitchManager.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Switch-aware characters receive one default-compatible incoming callback
  after party mutation and before incoming weapon/artifact callbacks and delay.
- Existing character, weapon, and artifact outgoing order remains unchanged.
- Missing targets, same-active targets, and the direct active setter consume no
  time and emit no character, weapon, or artifact switch callbacks.

Test cases:

- Normal: outgoing character then incoming character/weapon/artifact ordering.
- Boundary: callbacks observe pre-delay time and correct old/new active member.
- Abnormal: legacy Raiden out-only implementation, missing/same target, direct
  setter, and characters without the capability.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew RaidenParty`
- `python scripts/preflight.py`

Evidence:

- Standard switches dispatch character-out, weapon-out, artifact-out, party
  mutation, character-in, weapon-in, and artifact-in in that exact order;
  callbacks observe the expected active member at the pre-delay timestamp.
- The incoming callback is a default no-op for existing out-only characters.
  Missing targets, same-active targets, and direct active setters remain
  callback-free and consume no added time.
- Two `RaidenParty` runs remain 1,275,070 damage / 60,718 DPS.
- Reaction regression, build, Javadoc, sample simulation, and preflight pass.

### Phase 2: Typed Conductive and Pulsating Witch - Done

Target files:

- `src/java/mechanics/buff/BuffId.java`
- `src/java/model/character/Lisa.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Conductive stacks use three independently expiring typed Lisa-owned markers
  and retain existing Charged, cap, consume, and exact-expiry behavior.
- In combat, C6 standard switch-in replaces current stacks with three markers
  expiring together after 15 seconds and starts one typed five-second cooldown.
- C5, no enemy, initial active insertion, direct setter, same-target switch,
  and switch-in before cooldown expiry do not fabricate C6 stacks or cooldown.
- Conductive membership/times and the C6 cooldown survive snapshot restore.

Test cases:

- Normal: ally-to-C6 switch, existing-stack refresh, and three-stack Hold.
- Boundary: stack 14.999/15 and C6 4.999/5.000 seconds.
- Abnormal: C5, no enemy, direct/same switch, pre-CD repeated switch, cap,
  consumption, and snapshot rollback after divergent state.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew RaidenParty`
- `python scripts/preflight.py`

Evidence:

- Charged attacks now create independent typed, Lisa-owned Conductive markers;
  cap replacement, Hold consumption, and half-open 15-second expiry retain the
  prior behavior while becoming snapshot-safe.
- Eligible C6 switches replace current stacks with exactly three aligned
  markers and one typed five-second cooldown. C5, missing enemy, initial,
  direct, same-target, and 4.999-second paths remain inert; exact 5.000-second
  reactivation and 14.999/15-second stack boundaries pass.
- Pre- and post-proc snapshots restore active membership, marker timing, and
  cooldown without replaying character switch callbacks.
- Two `RaidenParty` runs remain 1,275,070 damage / 60,718 DPS. Reaction
  regression, build, Javadoc, sample simulation, and preflight pass.

## Implementation Order: Sucrose C4 Alchemania Lifecycle

Status: Complete. B-139 uses one bounded shared cooldown operation followed by
Sucrose-owned typed counter state; RL, generated docs, stamina, multi-target
hits, and the unknown counter inactivity cap remain excluded.

Evidence:

- The maintained KQM Sucrose page and cooldown reference, accessed 2026-08-02,
  specify one counted Normal/Charged hit per 0.1 seconds, a seven-hit threshold,
  an injected 1-7-second reduction, persistent counter state, and a hard cap at
  the currently earliest Skill charge:
  https://library.keqingmains.com/characters/anemo/sucrose
  https://library.keqingmains.com/combat-mechanics/cooldowns

### Phase 1: Partial Earliest-Charge Cooldown API - Done

Target files:

- `src/java/model/entity/state/CooldownState.java`
- `src/java/model/entity/Character.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- A finite non-negative reduction shortens only the earliest pending Skill
  charge, clamps at current time, returns the amount applied, and never carries
  excess into a later charge.
- Single-charge and multi-charge schedules preserve last-use metadata, captured
  cooldown duration, later charge times, readiness ordering, and snapshots.
- Negative, NaN, and infinite reductions are rejected without state mutation;
  zero and ready-state reductions are inert.

Test cases:

- Normal: partial single-charge and earliest two-charge reductions.
- Boundary: exact-ready clamp, reduction larger than remaining cooldown, and
  reduction after one queued charge naturally restores.
- Abnormal: zero/ready state, negative/NaN/infinite input, later-charge
  non-carry, and snapshot restore after divergent reduction.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew RaidenParty`
- `python scripts/preflight.py`

Evidence:

- The shared operation applies and reports a bounded flat reduction to only
  the earliest single- or multi-charge cooldown while preserving last-use,
  captured duration, and all later restore times.
- Exact/full/oversized reductions, natural restore pruning, ready and zero
  no-ops, invalid finite bounds, and divergent snapshot rollback pass focused
  regression coverage.
- Two `RaidenParty` runs remain 1,275,070 damage / 60,718 DPS. Reaction
  regression, build, Javadoc, sample simulation, and preflight pass.

### Phase 2: Typed Alchemania Counter and Charged Attack - Done

Target files:

- `src/java/mechanics/buff/BuffId.java`
- `src/java/model/character/Sucrose.java`
- `config/characters/Sucrose/Sucrose_Multipliers.csv`
- `src/java/simulation/party/FlinsPartyDefinition.java`
- `src/java/simulation/party/FlinsParty2Definition.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Sucrose supports the sourced level-9 Charged Attack as one 204.27% Anemo
  Charged hit with standard Normal/Charged ICD, 1U gauge, and 69-frame action.
- C4 counts only positive resolved Sucrose Normal/Charged hits, at most one per
  0.1 seconds; the seventh removes the counter and applies an injected integer
  1-7-second reduction to only the earliest pending Skill charge.
- Counter and 0.1-second gate use typed Sucrose-owned markers and survive snapshot
  rollback; C0-C3, Skill/Burst/Plunge, and invalid draws do not alter cooldowns.

Test cases:

- Normal: seven Normals, mixed Normal/Charged, reductions of one/four/seven,
  and counting while both Skill charges are ready.
- Boundary: 0.099/0.100 seconds, six/seven hits, partial/full charge clamp,
  no overflow into charge two, persistent counter across switch, and snapshot.
- Abnormal: C3, wrong categories/owner, rejected draw below one/above seven/
  non-finite, and divergent post-snapshot counter/cooldown state.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `./gradlew RaidenParty`
- `python scripts/preflight.py`

Evidence:

- Sucrose's sourced level-9 Charged Attack resolves as one 204.27% Anemo,
  Charged-category, standard-ICD 1U hit over 69 frames and enters C4 through
  the shared resolved-damage listener.
- Typed six-hit progress and 0.1-second markers cover simultaneous, 0.099/
  0.100-second, C3, target, owner, category, positive-damage, switch, exact
  seven-hit, invalid draw, and cross-simulator boundaries.
- Injected 1/4/7-second draws reduce only the earliest charge; ready state,
  partial/full clamp, no carry, and six-hit snapshot rollback/replay pass.
- Production Flins party definitions use a fixed four-second draw. Two runs
  each match at 31,443,262 damage / 316,013 DPS for `FlinsParty` and 20,805,520
  / 301,093 for `FlinsParty2`; two `RaidenParty` runs remain 1,275,070 / 60,718.
- Reaction regression, party catalog regression, build, Javadoc, samples, and
  preflight pass.

## Implementation Order: Desert Pavilion Chronicle Campaign

Status: Complete. Implemented B-140 as one artifact-owned resolved-hit window
plus its dedicated Plunging DMG stat prerequisite; RL, generated docs, stamina,
hitlag, and multi-target behavior are excluded.

Evidence:

- The maintained KQM artifact catalog, accessed 2026-08-02, specifies Anemo
  DMG +15%, activation after a Charged Attack hits, ATK SPD +10%, Normal/
  Charged/Plunging DMG +40%, and a 15-second duration:
  https://library.keqingmains.com/equipment/artifacts#desert-pavilion-chronicle

### Phase 1: Add the Complete Outgoing-Damage Set - Done

Target files:

- `src/java/mechanics/buff/BuffId.java`
- `src/java/model/type/StatType.java`
- `src/java/mechanics/formula/StandardDamageStrategy.java`
- supported character files with Plunging actions
- `src/java/model/artifact/DesertPavilionChronicle.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Plunging DMG Bonus is added once alongside elemental and Burst bonuses;
  physical Plunging attacks no longer double-count Physical DMG and infused/
  Burst Plunging attacks retain their independent element/ability bonuses.
- Supplied artifact stats are preserved and fixed Anemo DMG +15% is always
  present.
- A positive resolved Charged hit by the bound owner opens or refreshes one
  typed, owner-sourced half-open 15-second buff with ATK SPD +10% and Normal,
  Charged, and Plunging DMG +40%; the triggering hit remains unbuffed.
- Null/unbound/cross-owner/cross-simulator, Normal/Skill/Burst/Other, and
  non-positive callbacks are inert or rejected without state mutation.
- Buff membership/timing and pre-trigger state survive snapshot rollback.

Test cases:

- Normal: metadata/fixed stats, Charged activation, all four dynamic stats,
  action-duration speed effect, refresh, and source ownership.
- Boundary: triggering-hit order, 14.999/15.000-second expiry, refresh start,
  post-trigger and pre-trigger snapshot rollback.
- Abnormal: null stats, wrong owner/simulator/category, zero/negative damage,
  duplicate initialization, and cross-binding reuse.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Dedicated Plunging DMG routing adds the category once for physical attacks
  and combines it with elemental and Burst bonuses for Raiden's Burst plunge.
- Desert Pavilion preserves supplied stats, grants fixed Anemo DMG +15%, and
  opens one owner-sourced post-hit window with the four required dynamic stats.
- Trigger ordering, subsequent action speed, refresh, 14.999/15.000-second
  expiry, invalid callbacks, cross-binding, and snapshot rollback pass.
- Reaction regression, build, Javadoc, representative samples, and preflight
  pass; two runs each retain 1,275,070 / 60,718 (`RaidenParty`), 31,443,262 /
  316,013 (`FlinsParty`), and 20,805,520 / 301,093 (`FlinsParty2`).

### Phase 2: Skill-Activated Damage Sets - Done

Why second:

- Both sets consume the Phase 1 callback and differ only in weapon gating and
  fixed/dynamic bonus values.

Target files:

- `src/java/model/artifact/HeartOfDepth.java` (new)
- `src/java/model/artifact/MartialArtist.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Add canonical metadata, supplied-stat preservation, owner binding, and
  half-open Skill-use windows.
- Gate Heart of Depth's four-piece bonus to Normal/Charged stats and Martial
  Artist's fixed and Skill-activated Normal/Charged bonuses.

Acceptance criteria:

- Heart of Depth grants Hydro DMG +15% and Normal/Charged DMG +30% for 15
  seconds after the owner uses a Skill.
- Martial Artist grants Normal/Charged DMG +15%, then a further +25% for eight
  seconds after the owner uses a Skill.

Test cases to add or update:

- Normal: fixed stats, Skill activation, refresh, off-field owner use, and
  supplied-stat preservation.
- Boundary: exact 8/15-second expiry and immediate activation.
- Abnormal: non-Skill/wrong owner/simulator callbacks, pre-init use, null
  stats, duplicate binding, and independent instances.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Both sets pass supplied/fixed-stat, off-field Skill, immediate activation,
  refresh, exact expiry, invalid callback, binding, and independence checks.
- Reaction regression, build, Javadoc, and executable preflight passed on
  2026-08-02.

### Phase 2: Switch-Activated Weapons - Done

Target files:

- `src/java/model/weapon/TheWidsith.java` (new)
- `src/java/model/weapon/SacrificialJade.java` (new)
- `src/java/model/weapon/ThrillingTalesOfDragonSlayers.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- All three expose exact Lv. 90 metadata, supported R1-R5 behavior, defaults,
  and constructor validation; Widsith accepts injected reproducible draws.
- Widsith activates one of Recitative/Aria/Interlude for 10 seconds when its
  owner starts active or switches in, with a 30-second cooldown and exact
  R1-R5 ATK, all-elemental-DMG, or EM values.
- Sacrificial Jade grants R1-R5 HP/EM after five seconds off-field, retains it
  for the first ten seconds after switching in, and removes it at exact expiry.
- TTDS applies a typed nonstacking R1-R5 ATK buff to the incoming character for
  10 seconds on eligible owner switch-out, with a 20-second cooldown.

Test cases to add or update:

- Normal: metadata, all three Widsith outcomes, off/on-field Jade timing, and
  TTDS target buff.
- Boundary: exact 5/10/20/30-second transitions, re-entry, cooldown retry, and
  same-effect replacement.
- Abnormal: refinement 0/6, invalid draw, wrong owner/simulator, missing target,
  duplicate binding, physical exclusion from Aria, and no callback via setter.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Focused regressions cover all three Widsith themes, physical exclusion,
  injected-draw rejection, exact effect/cooldown boundaries, Sacrificial Jade
  off-/on-field timing and direct setters, and TTDS target, expiry, cooldown,
  replacement, metadata, and refinement behavior.
- `./gradlew ReactionRegressionTest`, `./gradlew build`, `./gradlew javadoc`,
  and `python scripts/preflight.py` passed on 2026-08-02.

## Implementation Order: Stateful Craftable Weapon Campaign

Status: Complete. This campaign adds four missing craftable/event weapons
whose passives fit existing action, reaction, particle, and damage hooks; RL
and generated documentation remain excluded.

Scope:

- Add Ring of Yaxche and Cloudforged with refinement-aware timed stat windows.
- Add Hakushin Ring with reaction-element party buffs and Crescent Pike with
  active-owner particle collection plus nonrecursive Physical follow-up damage.
- Reuse existing simulator capabilities without introducing weapon-specific
  branches into the combat runtime.

Out of scope for this pass:

- Generic energy-decrease and particle-pickup event redesign, enemy targeting,
  multi-target damage, RL, and generated docs.

### Phase 1: Skill and Energy Windows - Done

Target files:

- `src/java/model/weapon/RingOfYaxche.java` (new)
- `src/java/model/weapon/Cloudforged.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Branch-isolated source commit `49450dc` adds owner-bound Skill and observable
  Burst-energy windows without changing shared runtime code.
- Focused regressions cover metadata, R1/R5, HP quantization and cap,
  resnapshot/expiry, two-stack refresh, foreign dispatch, rebinding, and invalid
  refinement; reaction regression, build, Javadoc, and preflight gates pass.

Acceptance criteria:

- Both classes expose exact Lv. 90 metadata, R5 defaults, selected refinement,
  and reject refinement 0/6.
- Ring of Yaxche snapshots whole 1,000-Max-HP units on Skill use, grants only
  the capped R1-R5 Normal Attack bonus for 10 seconds, and refreshes cleanly.
- Cloudforged treats a successful owner Burst as the currently observable
  Energy-decrease event, grants up to two R1-R5 EM stacks for 18 seconds, and
  refreshes the shared duration when another stack is gained.

Test cases to add or update:

- Normal: metadata, Skill/Burst activation, R1/R5 bonus values, and two stacks.
- Boundary: exact 10/18-second expiry, refresh, HP quantization, and caps.
- Abnormal: refinement 0/6, wrong action/user, unbound use, and no wrong stat.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

### Phase 2: Reaction and Particle Follow-Ups - Done

Target files:

- `src/java/model/weapon/HakushinRing.java` (new)
- `src/java/model/weapon/CrescentPike.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Branch-isolated source commit `5bc8c47` adds element-targeted non-refreshing
  team windows and an active-collector Physical follow-up without shared
  runtime changes.
- Focused regressions cover R1/R5 metadata, holder/party filtering, independent
  exact expiries, Hyperbloom and Swirl elements, off-field rejection,
  nonrecursive Normal/Charged procs, zero/wrong actions, rebinding, and invalid
  refinement; reaction regression, build, Javadoc, and preflight gates pass.

Acceptance criteria:

- Both classes expose exact Lv. 90 metadata, R5 defaults, selected refinement,
  and reject refinement 0/6.
- Hakushin Ring activates only for an on-field owner-triggered Electro-related
  reaction, gives each involved elemental party member the R1-R5 bonus for six
  seconds, includes the holder, and does not refresh an already-active element.
- Crescent Pike treats a particle notification while its owner is active as
  collection, opens a five-second window, and adds one nonrecursive Physical
  R1-R5 ATK-scaled hit for each positive Normal or Charged hit.

Test cases to add or update:

- Normal: metadata, eligible Electro reactions, party filtering, particle
  activation, Normal/Charged follow-ups, and R1/R5 values.
- Boundary: off-field rejection, exact six/five-second expiry, non-refreshing
  Hakushin elements, and Crescent window refresh.
- Abnormal: refinement 0/6, NONE/ineligible reactions, zero/wrong/recursive
  damage, inactive-owner particles, and duplicate binding.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Five-Star EM Support Weapon Campaign

Status: Complete. Two five-star EM support weapons now use an off-field hit
state machine and live party composition.

Scope:

- Add Elegy for the End and A Thousand Floating Dreams with sourced metadata,
  R1-R5 values, exact buff targeting, timing, stacking, and focused regressions.

Out of scope for this pass:

- Freedom-Sworn's unsupported Plunging DMG stat, characters, formulas, RL,
  generated docs, and unrelated support weapons.

### Phase 1: Add Elegy for the End and A Thousand Floating Dreams - Done (`47a0650`)

Target files:

- `src/java/model/weapon/ElegyForTheEnd.java` (new)
- `src/java/model/weapon/AThousandFloatingDreams.java` (new)
- `src/java/mechanics/buff/BuffId.java`
- `src/java/simulation/runtime/BuffManager.java`
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Elegy proves off-field Skill/Burst sigils, exact 0.2/12/20-second boundaries,
  party-wide R1/R5 values, typed replacement, and invalid-source rejection.
- Floating Dreams proves live mixed-party tiers, both three-stack caps,
  owner exclusion, multiple-provider stacking, and binding; all gates pass.

Acceptance criteria:

- Elegy grants 60-120 EM and records Skill/Burst hit sigils at exact 0.2-second
  CT while off-field; four sigils produce a half-open 12-second party 100-200
  EM and 20-40% ATK buff, then reject sigils for exactly 20 seconds.
- Floating Dreams dynamically grants 32-64 EM per same-element ally and
  10-26% owner-element DMG per different-element ally, capped at three of each;
  every other party member receives stackable 40-48 EM from each provider.
- Metadata, post-hit order, owner/simulator binding, live party changes,
  provider targeting, same-type replacement, R1-R5 defaults, and validation
  are explicit.

Test cases to add or update:

- Normal: metadata/static EM, Elegy four-hit trigger, Floating Dreams mixed
  party tiers and ally-only EM, R5.
- Boundary: exact 0.2/12/20 seconds, three-stack composition caps, R1, multiple
  Floating Dreams providers and same-type Elegy replacement.
- Abnormal: Elegy Normal hits and foreign owner, Floating Dreams owner
  exclusion, cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Remaining 3-Star Bane Weapon Campaign

Status: Complete. Three independent weapon classes reuse the verified live
target-Aura implementation without changing formula or Aura behavior.

Scope:

- Add Cool Steel, Bloodtainted Greatsword, and Raven Bow at Lv. 90 with R1-R5
  Bane values and typed categories.
- Reuse impact-time Aura eligibility and keep the passive outside owner stats
  and snapshots.

Out of scope for this pass:

- Black Tassel's slime enemy type, formula/Aura changes, transformative reaction
  bonuses, characters, parties, RL, and generated docs.

### Phase 1: Add the Remaining Supported 3-Star Bane Weapons - Done

Target files:

- `src/java/model/weapon/CoolSteel.java` (new)
- `src/java/model/weapon/BloodtaintedGreatsword.java` (new)
- `src/java/model/weapon/RavenBow.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Eligible Aura | Focused verification | Status |
|---|---|---|---|
| Cool Steel | Hydro/Cryo | 401 ATK, 35.2% ATK, sword, R1/R5 damage | Done (`45eae33`) |
| Bloodtainted Greatsword | Pyro/Electro | 354 ATK, 187 EM, claymore, R1/R5 damage | Done (`53abaaf`) |
| Raven Bow | Hydro/Pyro | 448 ATK, 94 EM, bow, R1/R5 damage | Done (`9749303`) |

Completion evidence:

- Each weapon exposes sourced Lv. 90 metadata and both R1/R5 passive values;
  each eligible Aura pair and one ineligible element are covered.
- Existing no-Aura, expiry, snapshot/effective-stat exclusion, and invalid
  refinement coverage remains green through the shared base regression.
- Every unit passes reaction regression, build, and preflight; the final public
  API boundary passes Javadoc with no generated artifact staged.

Acceptance criteria:

- All variants expose sourced Lv. 90 metadata and 12/15/18/21/24% refinement
  progression through the existing shared base.
- Eligible live Auras apply once at impact; absent, expired, or ineligible Auras
  apply no bonus and never mutate owner effective or snapshotted stats.
- Refinements 0/6 fail through the existing shared validation and all existing
  target-Aura weapon regressions remain green.

Test cases to add or update:

- Normal/static: each name, base ATK, substat, category, and R5 eligible Aura.
- Boundary: each R1 bonus and alternative eligible element.
- Abnormal: one ineligible Aura per variant and shared invalid refinement.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the batch boundary
- `python scripts/preflight.py`

## Implementation Order: Static Action-Bonus Weapon Campaign

Status: Complete. Three independently revertible weapons map sourced passives
directly onto existing action-specific damage stats.

Scope:

- Add The Stringless, Rust, and White Tassel with Lv. 90 metadata, R1-R5
  validation, and exact action-specific bonuses.
- Keep all bonuses additive with existing Skill, Burst, Normal, and Charged
  damage stats so formula and snapshot behavior remain unchanged.

Out of scope for this pass:

- Projectile travel, weak points, attack speed, new action categories, formula
  changes, characters, parties, RL, and generated docs.

### Phase 1: Add Static Action-Bonus Weapons - Done

Target files:

- `src/java/model/weapon/TheStringless.java` (new)
- `src/java/model/weapon/Rust.java` (new)
- `src/java/model/weapon/WhiteTassel.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Passive | Focused verification | Status |
|---|---|---|---|
| The Stringless | Skill/Burst +24-48% | 510 ATK, 165 EM, bow, R1/R5 | Done (`e65e673`) |
| Rust | Normal +40-80%, Charged -10% | 510 ATK, 41.3% ATK, bow, R1/R5 | Done (`31fd61e`) |
| White Tassel | Normal +24-48% | 401 ATK, 23.4% CR, polearm, R1/R5 | Done (`b814dd2`) |

Completion evidence:

- Each weapon passes sourced R1/R5 action bonuses, Lv. 90 metadata, typed
  category, unrelated-action non-interference, and refinement 0/6 rejection.
- Rust preserves the additive -10% Charged damage modifier at both refinement
  boundaries while only its Normal bonus scales.
- Every unit passes reaction regression, build, and preflight; the final public
  API boundary passes Javadoc with no generated artifact staged.

Acceptance criteria:

- Refinements 1-5 produce each sourced action bonus and 0/6 fail fast.
- Each passive changes only its named action stats; unrelated action categories
  remain zero and Rust's charged penalty remains -10% at every refinement.
- Static Lv. 90 stats and typed categories match sources; existing build and
  combat regressions remain green.

Test cases to add or update:

- Normal/static: names, base ATK, substats, categories, and R5 action bonuses.
- Boundary: each R1 value and Rust's refinement-independent charged penalty.
- Abnormal: refinement 0/6 and unrelated action-stat non-interference.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the batch boundary
- `python scripts/preflight.py`

## Implementation Order: Amber Character Vertical Slice

Status: Complete. One character/data/regression unit adds Amber through C6
within a stationary one-enemy combat boundary.

Scope:

- Add stable `CharacterId.AMBER`, Lv. 90 data, typed Normal/fully Charged/
  Plunge attacks, delayed Baron Bunny, and fixed-center Fiery Rain.
- Implement Burst A1 CRIT Rate, C1 second Charged arrow, C3/C5 talent values,
  C4 Skill cooldown/charge changes, and C6 party ATK.

Out of scope for this pass:

- Weak-point A4, manual C2 foot detonation, Baron Bunny HP/taunt/destruction,
  random outer-AoE placement, movement speed, stamina, parties, RL, and docs.

Definitions:

- **Center-hit Burst stand-in**: all 18 sourced Fiery Rain waves hit the one
  modeled enemy over two seconds and share standard 1U Burst ICD.

### Phase 1: Add Amber Data, Delayed Actions, Constellations, and Regression - Done

Target files:

- `src/java/model/type/CharacterId.java`
- `src/java/model/character/Amber.java` (new)
- `config/characters/Amber/Amber_Status.csv` (new)
- `config/characters/Amber/Amber_Multipliers.csv` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Commit `90f7f8c` adds stable ID 10, aligned Lv. 90/talent configuration,
  typed bow attacks, delayed Baron Bunny, and center-position Fiery Rain.
- Exact pre-/at-eight-second Skill, fixed 18-wave Burst, C0/C1 Charged, C0/C4
  charge recovery, C5/C3 multipliers, A1, and C6 buff expiry regressions pass.
- `ReactionRegressionTest`, build, Javadoc, and preflight pass with no generated
  or deliberately untracked artifact staged.

Acceptance criteria:

- Amber has a stable additive ID and sourced Lv. 90 base stats/config without
  changing existing IDs.
- Baron Bunny snapshots at cast, explodes exactly once after eight seconds as
  a 2U/no-ICD Skill hit, then generates four Pyro particles; C4 uses two charges
  and a 12-second cooldown, while C5 selects level-12 damage.
- Fiery Rain consumes 40 Energy, snapshots, schedules exactly 18 standard-ICD
  1U Burst waves over two seconds, receives A1 +10% Burst CRIT Rate, and C3 uses
  level-12 damage; C6 grants the party 15% ATK for ten seconds.
- C1 adds one 20%-strength Charged-damage arrow sharing Charged ICD; unsupported
  C2/A4 and target-placement behavior is not fabricated.

Test cases to add or update:

- Static/normal: ID round trip, Lv. 90 stats, typed action metadata.
- Skill: no early hit/particle, exact delayed explosion, gauge/ICD/snapshot,
  C0/C4 charge readiness, and C0/C5 multiplier.
- Burst: exact wave count, Energy, metadata, A1 CRIT, C0/C3 multiplier, and C6
  owner/ally ATK before and at expiry.
- Boundary/abnormal: C0/C1 Charged hit count and explicit excluded-state scope.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Legacy Weapon Refinement Campaign

Status: Complete. Existing fixed-refinement weapons have R1-R5 selection
without changing their current no-argument behavior.

Scope:

- Add refinement-aware Alley Flash, Deathmatch, and The Catch constructors,
  exact sourced passive scaling, exposed refinement rank, and regressions.
- Preserve Alley Flash/Deathmatch default R1 and The Catch default R5.

Out of scope for this pass:

- Incoming-damage disable state for Alley Flash, dynamic enemy-count discovery,
  off-field Deathmatch transition delay, formulas, characters, RL, and docs.

### Phase 1: Complete Existing Weapon Refinement Contracts - Done

Target files:

- `src/java/model/weapon/AlleyFlash.java`
- `src/java/model/weapon/Deathmatch.java`
- `src/java/model/weapon/TheCatch.java`
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Compatibility default | Focused verification | Status |
|---|---|---|---|
| Alley Flash | R1 | 12-24% all-DMG, metadata, R1/R5, invalid rank | Done (`e587803`) |
| Deathmatch | R1 | single/multi ATK/DEF R1/R5, invalid rank | Done (`ca085a2`) |
| The Catch | R5 | Burst DMG/CRIT R1/R5, metadata, invalid rank | Done (`d29055f`) |

Completion evidence:

- All no-argument constructors preserve their former refinement and passive
  values while explicit R1/R5 and refinement 0/6 regressions pass.
- Deathmatch covers both battlefield branches and no single-target DEF leak;
  The Catch remains Burst-only and Alley Flash retains structural all-DMG.
- Every unit passes reaction regression, build, and preflight; the final public
  API boundary passes Javadoc with no generated artifact staged.

Acceptance criteria:

- No-argument constructors retain exact current output and source compatibility.
- R1-R5 values match KQM; refinement 0/6 fails before mutable state is exposed.
- Deathmatch changes only its selected enemy-count branch, Alley Flash remains
  always active under no incoming damage, and The Catch modifies Burst only.

Test cases to add or update:

- Compatibility/static: existing defaults, names, Lv. 90 stats, and categories.
- Boundary: R1/R5 for every passive branch and unchanged unrelated stats.
- Abnormal: refinement 0/6 for every weapon.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the batch boundary
- `python scripts/preflight.py`

## Implementation Order: Skill-Focused Event Weapon Campaign

Status: Complete. Three event weapons add complete R1-R5 Skill-related passives
using one shared Skill-use window policy.

Scope:

- Add shared refinement validation and refresh-only Skill-use stat windows.
- Add Oathsworn Eye, Windblume Ode, and Festering Desire with sourced Lv. 90
  metadata, R1-R5 values, typed categories, and focused regressions.

Out of scope for this pass:

- Healing, incoming damage, external events, new formulas, characters, parties,
  RL, and generated docs.

Definitions:

- **Skill-use stat window**: one non-stacking owner bonus activated before the
  Skill resolves and refreshed to `cast time + duration` on each later cast.

### Phase 1: Add Skill-Focused Event Weapons - Done

Target files:

- `src/java/model/weapon/SkillUseStatWeapon.java` (new)
- `src/java/model/weapon/OathswornEye.java` (new)
- `src/java/model/weapon/WindblumeOde.java` (new)
- `src/java/model/weapon/FesteringDesire.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Passive | Focused verification | Status |
|---|---|---|---|
| Shared base + Oathsworn Eye | Skill use: ER +24-48% for 10s | activation, refresh, exact expiry, invalid rank | Done (`139657b`) |
| Windblume Ode | Skill use: ATK +16-32% for 6s | activation, refresh, exact expiry, metadata | Done (`340c244`) |
| Festering Desire | Skill DMG +16-32%, CRIT +6-12% | R1/R5, action isolation, metadata | Done (`4074bb1`) |

Completion evidence:

- The shared half-open Skill-use window ignores non-Skill actions, activates
  before Skill resolution, refreshes without stacking, and expires exactly.
- All weapons pass sourced metadata, R1/R5 values, refinement 0/6 rejection,
  and unrelated-stat isolation.
- Every unit passes reaction regression, build, and preflight; shared/final
  public APIs pass Javadoc with no generated artifact staged.

Acceptance criteria:

- Oathsworn Eye and Windblume Ode activate from typed Skill use before damage,
  never stack, refresh exactly, and are inactive at exact expiry.
- Festering Desire changes only Skill DMG and Skill CRIT Rate; all refinements
  and static metadata match KQM.
- Refinements 0/6 fail, no-argument constructors default to R5 for event reward
  weapons, and existing action dispatch/build regressions remain green.

Test cases to add or update:

- Normal/static: names, base ATK, substats, categories, and R5 bonuses.
- Boundary: R1 values, pre-trigger state, immediately active state, refresh,
  immediately before expiry, and exact expiry.
- Abnormal: refinement 0/6 and non-Skill action/stat non-interference.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the batch boundary
- `python scripts/preflight.py`

## Implementation Order: Watatsumi Wavewalker Weapon Campaign

Status: Complete. The complete three-weapon family shares one verified
party-energy policy and independently reversible metadata variants.

Scope:

- Add a shared R1-R5 Burst bonus computed from the equipped party's combined
  maximum Energy and capped at the sourced refinement limit.
- Add Akuoumaru, Mouun's Moon, and Wavebreaker's Fin with sourced Lv. 90 stats,
  typed categories, compatibility R5 defaults, and focused regressions.

Out of scope for this pass:

- Party mutation after setup, alternate energy-cost mechanics beyond the
  existing `getMaxEnergy()` contract, characters, formulas, RL, and docs.

### Phase 1: Add Watatsumi Wavewalker Weapons - Done

Target files:

- `src/java/model/weapon/PartyEnergyBurstWeapon.java` (new)
- `src/java/model/weapon/Akuoumaru.java` (new)
- `src/java/model/weapon/MouunsMoon.java` (new)
- `src/java/model/weapon/WavebreakersFin.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Passive | Focused verification | Status |
|---|---|---|---|
| Shared base + Akuoumaru | party max Energy to Burst DMG | pre-init, uncapped/capped, R1/R5, metadata | Done (`f111166`) |
| Mouun's Moon | inherited Watatsumi Wavewalker | bow metadata and shared behavior | Done (`3951ccc`) |
| Wavebreaker's Fin | inherited Watatsumi Wavewalker | polearm metadata and shared behavior | Done (`3c562e6`) |

Completion evidence:

- A four-member 240-Energy party produces the uncapped R5 57.6% bonus, an
  owner-only 60-Energy party produces R1 7.2%, and R1 caps at 40% above 333.3.
- Pre-initialization and unrelated stats remain unchanged; repeated effective
  stat compilation is stable and all three variants reject refinement 0/6.
- Every unit passes reaction regression, build, and preflight; the shared/final
  public API boundaries pass Javadoc with no generated artifact staged.

Acceptance criteria:

- The passive sums each configured party member's `getMaxEnergy()`, applies
  0.12-0.24% Burst DMG per point, and caps the result at 40-80% for R1-R5.
- Applying the passive before simulator initialization is a safe no-op; only
  Burst DMG changes and repeated stat compilation never accumulates state.
- Refinements 0/6 fail, no-argument constructors default to R5, and exact names,
  base ATK, ATK substats, and weapon categories match KQM TCL.

Test cases to add or update:

- Normal/static: full-party uncapped total, every variant's metadata, and R5.
- Boundary: owner-only party, R1, cap threshold/above-cap, repeated application.
- Abnormal: pre-initialization use, refinement 0/6, and unrelated stat isolation.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the shared/final API boundary
- `python scripts/preflight.py`

## Implementation Order: Reciprocal Hit Weapon Campaign

Status: Complete. Three weapons now use one verified typed policy for direct
hits that open refresh-only windows for a different action or stat group.

Scope:

- Add a shared dual-direction hit-triggered stat-window implementation with
  typed action groups, independent expirations, and refinement validation.
- Add Solar Pearl, Mitternachts Waltz, and Dodoco Tales with sourced Lv. 90
  metadata, R1-R5 values, R5 defaults, and focused regressions.

Out of scope for this pass:

- Indirect reaction hits, charged attacks for Solar Pearl/Mitternachts Waltz,
  new action dispatch, formulas, characters, RL, and generated docs.

### Phase 1: Add Reciprocal Hit Weapons - Done

Target files:

- `src/java/model/weapon/ReciprocalHitStatWeapon.java` (new)
- `src/java/model/weapon/SolarPearl.java` (new)
- `src/java/model/weapon/MitternachtsWaltz.java` (new)
- `src/java/model/weapon/DodocoTales.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Reciprocal windows | Focused verification | Status |
|---|---|---|---|
| Shared base + Solar Pearl | Normal -> Skill/Burst; Skill/Burst -> Normal | ordering, independent refresh/expiry, R1/R5 | Done (`a0e1368`) |
| Mitternachts Waltz | Normal -> Skill; Skill -> Normal | bow metadata, 5s windows, exclusions | Done (`696fb36`) |
| Dodoco Tales | Normal -> Charged; Charged -> ATK | catalyst metadata, unequal bonuses, 6s windows | Done (`a3fc809`) |

Completion evidence:

- Solar Pearl proves independent six-second bidirectional windows and one-side
  refresh; Mitternachts Waltz proves its narrower five-second action mapping.
- Dodoco Tales proves unequal Charged-DMG/ATK values and correct composition
  with its static ATK substat; unrelated and zero-damage hits remain inert.
- Every unit passes reaction regression, build, and preflight; shared/final
  public APIs pass Javadoc with no generated artifact staged.

Acceptance criteria:

- Only positive direct hits in the configured typed action groups activate the
  opposite window after that hit; zero-damage and unrelated actions do nothing.
- Each direction refreshes without stacking, expires at exact duration, and can
  coexist independently with the other direction.
- Refinements 0/6 fail; metadata and R1-R5 values match KQM TCL; repeated stat
  compilation is stable and unrelated stats remain unchanged.

Test cases to add or update:

- Normal: both directions, simultaneous active windows, refresh, and metadata.
- Boundary: R1/R5, immediately before/exact expiry, triggering-hit ordering.
- Abnormal: zero-damage, unrelated/Charged action exclusions, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the shared/final API boundary
- `python scripts/preflight.py`

## Implementation Order: Reaction-Window Weapon Campaign

Status: Complete. Three weapons now use one verified simulator-initialized
listener policy for owner-triggered reaction windows and stacks.

Scope:

- Add shared source/on-field gating, reaction predicates, shared-duration stack
  handling, refinement validation, and exact expiry.
- Add Mappa Mare, Emerald Orb, and Dark Iron Sword with sourced Lv. 90 metadata,
  current reaction lists, refinement contracts, and focused regressions.

Out of scope for this pass:

- Stellar-Conduct, which has no simulator reaction kind; off-field activation,
  new reactions/formulas, characters, RL, and generated docs.

Definitions:

- **Shared-duration stacks**: each eligible reaction increments up to the cap
  and refreshes one expiration time for every currently held stack.

### Phase 1: Add Reaction-Window Weapons - Done

Target files:

- `src/java/model/weapon/ReactionWindowWeapon.java` (new)
- `src/java/model/weapon/MappaMare.java` (new)
- `src/java/model/weapon/EmeraldOrb.java` (new)
- `src/java/model/weapon/DarkIronSword.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Reaction window | Focused verification | Status |
|---|---|---|---|
| Shared base + Mappa Mare | any reaction, 1-2 Elemental DMG stacks for 10s | source/field gating, stack cap/refresh/expiry, R1/R5 | Done (`73a6661`) |
| Emerald Orb | Hydro reaction set, ATK for 12s | exact kinds/Swirl element, metadata, R1/R5 | Done (`74d3f5d`) |
| Dark Iron Sword | Electro reaction set, fixed 20% ATK for 12s | exact kinds/Swirl element, fixed R1, metadata | Done (`2f780f8`) |

Completion evidence:

- Mappa Mare applies one/two shared-duration stacks to all seven elemental
  stats, excludes Physical, caps, refreshes, and resets after exact expiry.
- Emerald Orb and Dark Iron Sword cover every modeled sourced reaction kind and
  element-specific Swirl while rejecting wrong kinds/elements and sources.
- Every unit passes reaction regression, build, and preflight; shared/final
  public APIs pass Javadoc with no generated artifact staged.

Acceptance criteria:

- Only an eligible reaction attributed to the active weapon owner activates the
  passive; ally, off-field owner, `NONE`, and wrong-element Swirl events do not.
- Mappa Mare grants 8-16% to all seven elemental DMG stats per stack, caps at
  two stacks, refreshes their shared ten-second duration, and never buffs Physical.
- Emerald Orb scales 20-40% at R1-R5; Dark Iron Sword remains its sourced fixed
  R1 20%; all windows are half-open and metadata is exact.

Test cases to add or update:

- Normal: each eligible reaction family, Mappa one/two stacks, refresh, metadata.
- Boundary: R1/R5, before/exact expiry, stack cap, Hydro/Electro Swirl relation.
- Abnormal: `NONE`, wrong reaction/Swirl element, ally/off-field source, rank 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the shared/final API boundary
- `python scripts/preflight.py`

## Implementation Order: Hit-Stack Weapon Campaign

Status: Complete. Three positive-hit stacking weapons now use one verified
typed, cooldown-aware, shared-duration stack policy.

Scope:

- Add shared action eligibility, positive-hit gating, internal cooldown,
  shared-duration stack cap/refresh, refinement validation, and exact expiry.
- Add Ballad of the Boundless Blue, Compound Bow, and Ibis Piercer with sourced
  Lv. 90 metadata, R1-R5 values, R5 defaults, and focused regressions.

Out of scope for this pass:

- Projectile travel, hitlag/ping variation, new action dispatch or formulas,
  characters, RL, and generated docs.

### Phase 1: Add Hit-Stack Weapons - Done

Target files:

- `src/java/model/weapon/HitStackStatWeapon.java` (new)
- `src/java/model/weapon/BalladOfTheBoundlessBlue.java` (new)
- `src/java/model/weapon/CompoundBow.java` (new)
- `src/java/model/weapon/IbisPiercer.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Hit stack | Focused verification | Status |
|---|---|---|---|
| Shared base + Ballad | Normal/Charged, 0.3s CT, 3 stacks/6s | dual unequal bonuses, cap/refresh/expiry, R1/R5 | Done (`10c88a6`) |
| Compound Bow | Normal/Charged, 0.3s CT, 4 stacks/6s | ATK/Normal SPD, metadata, exclusions | Done (`f0de472`) |
| Ibis Piercer | Charged, 0.5s CT, 2 stacks/6s | EM, Normal exclusion, metadata | Done (`0185589`) |

Completion evidence:

- Ballad proves just-before/exact 0.3-second CT, unequal dual bonuses, three
  stacks, cap refresh, exact expiry, and off-field persistence.
- Compound Bow proves four ATK/Normal-SPD stacks; Ibis proves Charged-only two
  EM stacks and exact 0.5-second CT while unrelated/zero hits stay inert.
- Every unit passes reaction regression, build, and preflight; shared/final
  public APIs pass Javadoc with no generated artifact staged.

Acceptance criteria:

- Only positive direct hits of configured typed actions gain a stack; hits
  before exact CT, zero damage, and unrelated action types do nothing.
- Gaining or refreshing at cap sets one six-second expiration for all stacks;
  existing stacks remain effective while the owner is off-field.
- Each weapon's max stacks, R1-R5 values, names, Lv. 90 stats, categories, and
  no-argument R5 default match KQM TCL.

Test cases to add or update:

- Normal: sequential stack gain, cap refresh, unequal multi-stat values, metadata.
- Boundary: exact/just-before CT, before/exact expiry, off-field persistence, R1/R5.
- Abnormal: zero-damage, wrong action types, refinement 0/6, repeated stat reads.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the shared/final API boundary
- `python scripts/preflight.py`

## Implementation Order: Action-Use Window Weapon Campaign

Status: Complete. The verified Skill-use API now delegates to a generic typed
action window, with Skill-, Dash-, and Burst-triggered weapon variants added.

Scope:

- Extract a generic typed action-use, refresh-only, half-open stat window and
  retain `SkillUseStatWeapon` as a source-compatible Skill specialization.
- Add Etherlight Spindlelute, Wine and Song, and Skyrider Sword with sourced
  Lv. 90 metadata, R1-R5 values, R5 defaults, and focused regressions.

Out of scope for this pass:

- Stamina consumption and movement speed, which have no simulator combat stat;
  formulas, characters, RL, and generated docs.

### Phase 1: Add Action-Use Window Weapons - Done

Target files:

- `src/java/model/weapon/ActionUseStatWeapon.java` (new)
- `src/java/model/weapon/SkillUseStatWeapon.java`
- `src/java/model/weapon/EtherlightSpindlelute.java` (new)
- `src/java/model/weapon/WineAndSong.java` (new)
- `src/java/model/weapon/SkyriderSword.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Action window | Focused verification | Status |
|---|---|---|---|
| Generic base + Skill compatibility + Etherlight | Skill -> EM 100-200 for 20s | old event weapons unchanged, refresh/expiry, R1/R5 | Done (`c672bbf`) |
| Wine and Song | Dash -> ATK 20-40% for 5s | Normal non-trigger, metadata, exact expiry | Done (`a59b00c`) |
| Skyrider Sword | Burst -> ATK 12-24% for 15s | Skill non-trigger, metadata, exact expiry | Done (`dbf3b15`) |

Completion evidence:

- Oathsworn Eye and Windblume Ode retain their prior constructor, trigger,
  refresh, and expiry regressions through the Skill specialization.
- Etherlight, Wine and Song, and Skyrider Sword prove isolated Skill, Dash, and
  Burst keys with exact 20/5/15-second windows and no wrong-action leakage.
- Every unit passes reaction regression, build, and preflight; shared/final
  public APIs pass Javadoc with no generated artifact staged.

Acceptance criteria:

- Existing Oathsworn Eye and Windblume Ode retain exact source/API behavior and
  all prior tests while the generic base accepts only configured action keys.
- New windows activate before the selected action resolves, refresh without
  stacking, remain half-open, and leave unrelated stats/actions unchanged.
- Refinement 0/6 fails; all metadata/R1-R5 values match KQM TCL; unsupported
  movement-only effects remain explicitly outside combat-state scope.

Test cases to add or update:

- Normal: each configured trigger, refresh, metadata, R5 bonus.
- Boundary: R1, before/exact expiry, old Skill specialization regressions.
- Abnormal: wrong action keys, repeated reads, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the shared/final API boundary
- `python scripts/preflight.py`

## Implementation Order: Claymore Hit-Stack Weapon Campaign

Status: Complete. The verified typed hit-stack policy now covers both planned
claymores without adding runtime abstractions.

Scope:

- Add Skyrider Greatsword and Whiteblind with sourced Lv. 90 metadata,
  R1-R5 values, R5 defaults, and focused regressions.
- Reuse `HitStackStatWeapon` for positive Normal/Charged hits, exact 0.5-second
  CT, a four-stack cap, and a shared six-second duration.

Out of scope for this pass:

- Characters, formulas, RL, generated docs, and unrelated weapon families.

### Phase 1: Add Claymore Hit-Stack Weapons - Done

Target files:

- `src/java/model/weapon/SkyriderGreatsword.java` (new)
- `src/java/model/weapon/Whiteblind.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Passive | Focused verification | Status |
|---|---|---|---|
| Skyrider Greatsword | Normal/Charged hit -> ATK 6-10%, max four | CT, cap/refresh, metadata, R1/R5 | Done (`b718c14`) |
| Whiteblind | Normal/Charged hit -> ATK and DEF 6-12%, max four | dual stats, action gate, expiry, R1/R5 | Done (`7e4cba0`) |

Completion evidence:

- Skyrider Greatsword proves exact 0.5-second CT, four-stack cap refresh,
  wrong-action and zero-damage exclusion, and exact shared expiry.
- Whiteblind applies equal dynamic ATK/DEF stacks while preserving its static
  DEF substat after expiry; metadata, R1/R5, and invalid ranks are covered.
- Reaction regression, build, Javadoc, and preflight pass with no generated
  artifact staged.

Acceptance criteria:

- Only positive Normal/Charged direct hits gain stacks, exact 0.5-second CT is
  eligible, and wrong action or zero-damage events do not refresh the window.
- Every accepted hit refreshes the shared six-second half-open duration while
  preserving the four-stack cap.
- Refinement 0/6 fails; metadata and R1-R5 values match the cited KQM TCL pages.

Test cases to add or update:

- Normal: Normal/Charged triggers, four-stack cap, dual ATK/DEF application.
- Boundary: exact CT, before/exact expiry, R1 and R5 values.
- Abnormal: Skill and zero-damage exclusion, repeated reads, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the final public-class boundary
- `python scripts/preflight.py`

## Implementation Order: Direct Physical Proc Weapon Campaign

Status: Complete. Three weapon passives now share one deterministic-testable
direct-damage proc policy.

Scope:

- Add a shared positive-hit action gate, refinement-aware chance/multiplier,
  successful-proc cooldown, injected draw source, and physical proc action.
- Add Prototype Archaic, Fillet Blade, and Halberd with sourced Lv. 90 metadata,
  R1-R5 values, stochastic defaults where applicable, and focused regressions.

Out of scope for this pass:

- AoE target counts in the single-target simulator, characters, formulas, RL,
  generated docs, and unrelated weapon families.

### Phase 1: Add Shared Proc Policy and Three Weapons - Done

Target files:

- `src/java/model/weapon/DirectDamageProcWeapon.java` (new)
- `src/java/model/weapon/PrototypeArchaic.java` (new)
- `src/java/model/weapon/FilletBlade.java` (new)
- `src/java/model/weapon/Halberd.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Trigger and proc | Focused verification | Status |
|---|---|---|---|
| Shared base + Prototype Archaic | Normal/Charged, 50%, 15s, 240-480% ATK | draw order, cooldown, physical damage, R1/R5 | Done (`9833fa3`) |
| Fillet Blade | any positive hit, 50%, 15-11s, 240-400% ATK | OTHER action support, failed draw, exact cooldown | Done (`20cfb49`) |
| Halberd | Normal, 100%, 10s, 160-320% ATK | deterministic trigger, Charged exclusion, metadata | Done (`7695dde`) |

Completion evidence:

- Prototype Archaic proves draw ordering, strict 50% chance, failed-draw retry,
  Normal/Charged gating, exact 15-second CT, and 240/480% scaling.
- Fillet Blade accepts positive `OTHER` and Skill hits, rejects zero damage,
  and proves refinement-dependent 15/11-second CT and 240/400% scaling.
- Halberd proves deterministic Normal-only 160/320% Physical procs and exact
  ten-second CT; reaction regression, build, Javadoc, and preflight pass.

Acceptance criteria:

- Ineligible, zero-damage, and cooldown-blocked events consume no random draw;
  failed draws do not start cooldown; exact cooldown is eligible.
- Successful procs resolve immediately as non-recursive Physical `OTHER`
  actions through the normal damage pipeline and use the owner's live stats.
- Refinement 0/6 and null draw suppliers fail; metadata and R1-R5 values match
  the cited KQM TCL pages.

Test cases to add or update:

- Normal: successful proc damage, every weapon's action gate and metadata.
- Boundary: failed then successful draw, before/exact cooldown, R1/R5 scaling.
- Abnormal: zero damage, wrong action, null draw, refinement 0/6, recursion guard.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the shared/final API boundary
- `python scripts/preflight.py`

## Implementation Order: Lisa Character Vertical Slice

Status: Complete. A stationary single-target Lisa model now covers Hold Violet
Arc, Conductive, and Lightning Rose within the declared input/state boundary.

Scope:

- Add typed identity, Lv. 90 stats, EM ascension, catalyst attacks, Conductive
  stacks from Charged attacks, Hold Skill consumption, particles, and C1 energy.
- Add the 10% Burst summon hit and 29 half-second discharges over 15 seconds,
  with C3/C5 talent values represented through config.
- Add source/config alignment and focused character regressions.

Out of scope for this pass:

- Press/Hold input selection (typed input currently has one Skill key), enemy
  DEF-shred state for A4, incoming damage/interruption for C2, multiple-target
  random bolts for C4, C6 switch-in stacks, crafting, RL, and generated docs.

### Phase 1: Add Lisa Core Combat Model - Done (`dc358b7`)

Target files:

- `src/java/model/type/CharacterId.java`
- `src/java/model/character/Lisa.java` (new)
- `config/characters/Lisa/Lisa_Multipliers.csv` (new)
- `config/characters/Lisa/Lisa_Status.csv` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Lisa has a stable appended id, sourced config, four catalyst Normals, Charged
  and elemental Plunge attacks, independent Conductive expiry, and Hold consume.
- Hold Skill proves zero/one/three-stack level-9/12 values, 2U no-ICD damage,
  five particles, 16-second CD, and the one-target C1 flat-Energy refund.
- Lightning Rose proves its non-aura 10% summon, 29 half-second snapshot
  discharges, standard ICD/1U, C3 value, 80 Energy cost, and 20-second CD.
- Reaction regression, build, Javadoc, preflight, and diff checks pass.

Acceptance criteria:

- The stable Lisa id appends without changing prior numeric ids; configured Lv.
  90 stats, EM ascension, 80 Energy cost, 16/20-second cooldowns load correctly.
- Catalyst Normal/Charged/Plunge attacks retain Electro typing and sourced ICD,
  gauge, frame, and level-9 values; Charged stacks expire independently at 15s.
- Hold Skill consumes up to three live stacks, applies 2U without ICD, emits
  five particles, and C1 refunds two flat Energy in this one-target model.
- Burst snapshots one non-aura 10% cast hit and 29 standard-ICD 1U discharges;
  C3/C5 use level-12 values and unsupported effects are documented in source.

Test cases to add or update:

- Normal: identity/stats, four-hit chain, three Charged stacks, Skill scaling,
  five particles, Burst summon plus 29 discharges.
- Boundary: independent Conductive expiry/refresh, Skill/Burst cooldowns, C1,
  C3/C5 values, exact 15-second Burst cadence.
- Abnormal: stack cap and consumption, expired stacks, summon aura exclusion.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Samurai Conduct Weapon Campaign

Status: Complete. Both Skill-hit energy weapons now share a sourced delayed
drain and periodic recovery policy.

Scope:

- Add a clamped runtime Energy spend operation distinct from snapshot restore.
- Add a shared positive Skill-hit, 10-second CT policy that drains three Energy
  after 23 frames and restores refinement-scaled flat Energy at 2/4/6 seconds.
- Add Kitain Cross Spear and Katsuragikiri Nagamasa with sourced metadata,
  static Skill DMG, R1-R5 values, R5 defaults, and focused regressions.

Out of scope for this pass:

- Latency-dependent variation around the sourced 22-24-frame drain, multi-hit
  target counts, characters, RL, and generated docs.

### Phase 1: Add Energy Spend and Samurai Conduct Weapons - Done (`0a1c228`)

Target files:

- `src/java/model/entity/state/EnergyState.java`
- `src/java/model/entity/Character.java`
- `src/java/model/weapon/SkillHitEnergyWeapon.java` (new)
- `src/java/model/weapon/KitainCrossSpear.java` (new)
- `src/java/model/weapon/KatsuragikiriNagamasa.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Runtime Energy spending clamps at zero, rejects invalid values, and leaves
  gain and Burst-window accounting unchanged.
- Samurai Conduct rejects wrong/zero hits, starts at positive Skill damage even
  off-field, drains at 23 frames, restores at 2/4/6 seconds, and retriggers at
  exact ten-second CT without duplicate events.
- Both weapons prove exact metadata, static 6/12% Skill bonuses, R1/R5 recovery,
  defaults, and invalid ranks; reaction regression, build, Javadoc, and preflight pass.

Acceptance criteria:

- Runtime Energy spend clamps at zero and does not masquerade as gain or
  snapshot restoration; existing Burst and energy accounting remain unchanged.
- Only positive Skill damage starts Samurai Conduct; off-field hits work,
  wrong/zero hits and pre-10-second hits schedule nothing, exact CT is eligible.
- Drain occurs at 23/60 seconds without going negative; R1/R5 recovery occurs
  exactly at 2/4/6 seconds, bypasses ER, and respects the Energy cap.
- Metadata, static Skill DMG, default R5, refinement 1-5, and invalid ranks
  match the cited KQM TCL pages.

Test cases to add or update:

- Normal: both metadata sets, Skill bonus, drain and three recovery ticks.
- Boundary: zero Energy, cap, before/exact drain, before/exact CT, R1/R5.
- Abnormal: Normal/zero Skill exclusion, off-field owner, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the shared/final API boundary
- `python scripts/preflight.py`

## Implementation Order: Skill-Use Stat Weapon Expansion

Status: Complete. Three complete damage-relevant Skill-use windows now use the
existing verified action-use policy.

Scope:

- Add Flute of Ezpitzal, Footprint of the Rainbow, and Tamayuratei no Ohanashi
  with sourced metadata, Skill triggers, R1-R5 values, R5 defaults, and tests.

Out of scope for this pass:

- Tamayuratei's Movement SPD, which has no combat stat or timing path;
  characters, formulas, RL, generated docs, and unrelated weapon families.

### Phase 1: Add Three Skill-Use Stat Weapons - Done (`9232f8d`)

Target files:

- `src/java/model/weapon/FluteOfEzpitzal.java` (new)
- `src/java/model/weapon/FootprintOfTheRainbow.java` (new)
- `src/java/model/weapon/TamayurateiNoOhanashi.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Skill-use window | Focused verification | Status |
|---|---|---|---|
| Flute of Ezpitzal | DEF 16-32% for 15s | sword metadata, refresh/expiry, R1/R5 | Done |
| Footprint of the Rainbow | DEF 16-32% for 15s | polearm metadata, action isolation, R1/R5 | Done |
| Tamayuratei no Ohanashi | ATK 20-40% for 10s | unequal formula/duration, refresh/expiry | Done |

Completion evidence:

- Both DEF weapons prove distinct sword/polearm metadata, equal 16/32% values,
  Skill-only refresh, static-substat composition, and exact 15-second expiry.
- Tamayuratei proves its 20/40% ATK values and exact ten-second window; Movement
  SPD remains explicitly excluded.
- Reaction regression, build, Javadoc, preflight, and diff checks pass.

Acceptance criteria:

- Skill use activates before resolution, refreshes without stacking, remains
  active immediately before and absent at exact expiry.
- Normal/Burst use and repeated stat reads do not activate or accumulate state;
  unrelated stats remain unchanged.
- Metadata, R1-R5 values, R5 defaults, and invalid refinement behavior match
  KQM TCL; unsupported movement speed is documented rather than approximated.

Test cases to add or update:

- Normal: every Skill trigger, metadata, R5 DEF/ATK value, refresh.
- Boundary: R1 values and before/exact 15/10-second expiry.
- Abnormal: Normal/Burst exclusion, repeated reads, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Additional Hit-Stack Weapon Expansion

Status: Complete. Two multi-stat weapons now use the verified positive-hit
stack policy without changing shared runtime behavior.

Scope:

- Add Prototype Rancour and Sacrificer's Staff with sourced metadata, action
  gates, stack values/caps, R1-R5 values, R5 defaults, and regressions.

Out of scope for this pass:

- Multi-target hit multiplication, characters, formulas, RL, generated docs,
  and unrelated weapon families.

### Phase 1: Add Two Multi-Stat Hit-Stack Weapons - Done (`bf06373`)

Target files:

- `src/java/model/weapon/PrototypeRancour.java` (new)
- `src/java/model/weapon/SacrificersStaff.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

| Unit | Positive-hit stacks | Focused verification | Status |
|---|---|---|---|
| Prototype Rancour | Normal/Charged -> ATK and DEF 4-8%, 0.3s, max four | dual stats, CT/cap/expiry, R1/R5 | Done |
| Sacrificer's Staff | Skill -> ATK 8-16% and ER 6-12%, max three | Skill isolation, off-field, expiry, R1/R5 | Done |

Completion evidence:

- Rancour proves exact 0.3-second CT, four ATK/DEF stacks, cap, wrong/zero-hit
  exclusion, R1/R5 values, and exact shared expiry.
- Staff proves same-time three-stack Skill hits, ATK/ER composition, off-field
  persistence, wrong/zero-hit exclusion, R1/R5 values, and exact expiry.
- Reaction regression, build, Javadoc, preflight, and diff checks pass.

Acceptance criteria:

- Rancour accepts only positive Normal/Charged hits at exact 0.3-second CT;
  Staff accepts only positive Skill hits and remains triggerable off-field.
- Both cap and refresh one shared half-open six-second duration without state
  accumulation during repeated reads; wrong/zero hits do not refresh.
- Metadata, R1-R5 values, R5 defaults, and invalid ranks match KQM TCL.

Test cases to add or update:

- Normal: action triggers, caps, all dynamic stats, metadata, R5.
- Boundary: exact CT, before/exact expiry, off-field persistence, R1.
- Abnormal: wrong/zero hits, repeated reads, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Hybrid Reaction Window Weapon Campaign

Status: Complete. Two unequal ATK/EM windows now use explicit reaction-only and
Skill-hit-or-reaction trigger policies.

Scope:

- Add a shared active-owner window that listens to attributed reactions and can
  optionally refresh after positive Skill damage.
- Add Missive Windspear and Mailed Flower with sourced metadata, R1-R5 values,
  R5 defaults, exact durations, and focused regressions.

Out of scope for this pass:

- Multi-target hit counts, characters, formulas, RL, generated docs, and
  unrelated reaction equipment.

### Phase 1: Add Hybrid Reaction Windows - Done (`9a7e8ae`)

Target files:

- `src/java/model/weapon/SkillHitOrReactionWindowWeapon.java` (new)
- `src/java/model/weapon/MissiveWindspear.java` (new)
- `src/java/model/weapon/MailedFlower.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Missive proves attributed non-NONE reaction activation, refresh, exact
  ten-second expiry, and rejection of Skill, NONE, and foreign reactions.
- Mailed proves positive Skill and reaction activation, trigger-hit ordering,
  off-field persistence without off-field refresh, and exact eight-second expiry.
- Metadata, R1/R5 values, invalid ranks, reaction regression, build, Javadoc,
  preflight, and diff checks pass.

Acceptance criteria:

- Missive activates only after an attributed non-NONE owner reaction; Mailed
  also activates after positive Skill damage and rejects wrong/zero hits.
- Both require the owner on-field to trigger/refresh, persist after switching
  out, refresh without stacking, and use half-open 10/8-second windows.
- Mailed's triggering hit is calculated before activation through the existing
  post-damage hook; metadata, R1-R5 values/defaults, and invalid ranks match KQM.

Test cases to add or update:

- Normal: reaction trigger, Mailed Skill trigger, ATK/EM values, metadata.
- Boundary: R1/R5, refresh, before/exact 10/8-second expiry, off-field persistence.
- Abnormal: NONE/foreign/off-field reaction, Normal/zero Skill, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc` at the shared/final API boundary
- `python scripts/preflight.py`

## Implementation Order: Self-Contained Four-Star Weapon Expansion

Status: Complete. Three bounded passives now use Skill use, elemental damage,
or current party composition without extending simulator contracts.

Scope:

- Add Prototype Starglitter, Iron Sting, and Ballad of the Fjords with sourced
  metadata, R1-R5 values, R5 defaults, exact conditions, and regressions.

Out of scope for this pass:

- Enemy-death, healing, shield, or pickup events; characters, formulas, RL,
  generated docs, and unrelated weapon families.

### Phase 1: Add Three Self-Contained Four-Star Weapons - Done (`e0564b2`)

Target files:

- `src/java/model/weapon/PrototypeStarglitter.java` (new)
- `src/java/model/weapon/IronSting.java` (new)
- `src/java/model/weapon/BalladOfTheFjords.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Starglitter proves typed Skill-only stacks, cap, shared refresh/expiry, and
  independent Normal/Charged bonuses at R1/R5.
- Iron Sting proves positive elemental and active-owner gates, exact one-second
  CT, two-stack cap, off-field retention without refresh, and exact expiry.
- Fjords proves live two-to-three-element composition changes; metadata,
  invalid ranks, reaction regression, build, Javadoc, preflight, and diff pass.

Acceptance criteria:

- Starglitter gains one stack on each Skill use, caps at two, refreshes one
  shared half-open 12-second duration, and grants only Normal/Charged DMG.
- Iron Sting gains a stack only after positive non-Physical direct damage while
  on-field, respects an exact one-second CT, caps at two, and refreshes one
  shared half-open six-second duration retained off-field.
- Fjords grants EM only when the live party contains at least three distinct
  playable elements; metadata, R1-R5 values/defaults, and invalid ranks match KQM.

Test cases to add or update:

- Normal: valid triggers/composition, stack values and caps, metadata, R5.
- Boundary: R1, exact CT and expiry, shared refresh, live party composition.
- Abnormal: wrong/zero/Physical/off-field hit, two-element party, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Frost Burial Weapon Campaign

Status: Complete. The three Dragonspine Frost Burial weapons now share one
refinement-aware, target-Aura-aware direct proc policy.

Scope:

- Add Dragonspine Spear, Snow-Tombed Starsilver, and Frostbearer with sourced
  metadata, R1-R5 values, R5 defaults, injected draws, and regressions.

Out of scope for this pass:

- Multi-target multiplication, falling-projectile delay, characters, formulas,
  RL, generated docs, and unrelated proc families.

### Phase 1: Add Shared Frost Burial and Three Variants - Done (`cd34797`)

Target files:

- `src/java/model/weapon/FrostBurialWeapon.java` (new)
- `src/java/model/weapon/DragonspineSpear.java` (new)
- `src/java/model/weapon/SnowTombedStarsilver.java` (new)
- `src/java/model/weapon/Frostbearer.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Shared policy proves active-owner positive Normal/Charged gates, failed-roll
  retry, successful ten-second CT, recursion exclusion, and injected draws.
- R1 base/Cryo proc damage proves the 80%/200% 2.5 ratio and exact CT boundary;
  all three R5 variants produce damage with their sourced metadata.
- Refinement/null validation, reaction regression, build, Javadoc, preflight,
  and diff checks pass.

Acceptance criteria:

- Only positive Normal/Charged hits by the active owner roll; failed rolls do
  not start CT, successful rolls start an exact ten-second CT, and proc recursion
  is impossible.
- The generated Physical proc uses 80-140% ATK normally and 200-360% ATK when
  Cryo Aura is live at proc resolution, with deterministic injected draws.
- All three variants expose sourced metadata, R1-R5 values/defaults, and reject
  refinement 0/6 and null draw sources.

Test cases to add or update:

- Normal: each variant metadata/proc, Cryo enhanced ratio, R5 default.
- Boundary: failed-then-success draw, before/exact ten-second CT, R1 values.
- Abnormal: Skill/zero/off-field/recursive hit, refinement 0/6, null source.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Energy-Aware Action Weapon Campaign

Status: Complete. Two dynamic action-DMG weapons now use owner Energy and
maximum Energy reads without changing Energy runtime behavior.

Scope:

- Add Hamayumi and Moonweaver's Dawn with sourced metadata, R1-R5 values, R5
  defaults, exact Energy thresholds, and focused regressions.

Out of scope for this pass:

- Energy mutation, character behavior, formulas, RL, generated docs, and
  unrelated conditional weapons.

### Phase 1: Add Two Energy-Aware Action Weapons - Done (`8ff2ef7`)

Target files:

- `src/java/model/weapon/Hamayumi.java` (new)
- `src/java/model/weapon/MoonweaversDawn.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Hamayumi proves unbound base values, exact full-Energy doubling, immediate
  loss after spend, restoration at equality, and R1/R5 values.
- Moonweaver proves unbound base and exclusive 80/60/40-capacity behavior,
  including exact tier boundaries and R1/R5 values.
- Metadata, reuse/refinement rejection, reaction regression, build, Javadoc,
  preflight, and diff checks pass.

Acceptance criteria:

- Hamayumi always grants its Normal/Charged bonuses and doubles both only while
  current Energy is at least maximum Energy, updating immediately after spend/gain.
- Moonweaver always grants Burst DMG and adds exactly one capacity tier: none
  above 60, the 60 tier at 41-60, and the larger 40 tier at 40 or below.
- Both expose sourced metadata, R1-R5 values/defaults, bind to one simulator,
  and reject refinement 0/6 or cross-simulator reuse.

Test cases to add or update:

- Normal: base/full Hamayumi values and 80/60/40 Moonweaver tiers, metadata, R5.
- Boundary: exact Energy equality, immediate dynamic loss, R1 values.
- Abnormal: unbound passive, cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Deterministic Physical Proc Weapon Campaign

Status: Complete. Three deterministic proc state machines now reuse the
existing generated Physical action pipeline.

Scope:

- Add Kagotsurube Isshin, The Flute, and Debate Club with sourced metadata,
  refinement behavior, exact trigger windows/CTs, and focused regressions.

Out of scope for this pass:

- Multi-target multiplication, visual projectile delay, healing, characters,
  formulas, RL, generated docs, and unrelated proc weapons.

### Phase 1: Add Three Deterministic Physical Proc Weapons - Done (`2e69b5f`)

Target files:

- `src/java/model/weapon/KagotsurubeIsshin.java` (new)
- `src/java/model/weapon/TheFlute.java` (new)
- `src/java/model/weapon/DebateClub.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Isshin proves Normal/Charged/Plunge gates, 180% proc, 15% ATK window, and
  exact shared eight-second CT/refresh.
- The Flute proves exact 0.5-second Harmonic gains, five-stack consumption,
  independent exact 30-second expiry, and R1/R5 proc values.
- Debate proves Skill-only 15-second window, exact three-second proc CT, R1/R5,
  wrong/zero/off-field exclusions; build/Javadoc/preflight/diff pass.

Acceptance criteria:

- Isshin accepts positive Normal/Charged/Plunge hits, deals 180% ATK Physical,
  grants 15% ATK for eight seconds, and uses an exact eight-second CT.
- The Flute gains one 30-second Harmonic per positive Normal/Charged hit at exact
  0.5-second CT and consumes five to deal refinement-aware 100-200% ATK Physical.
- Debate Club opens a half-open 15-second window on Skill use and lets positive
  Normal/Charged hits deal 60-120% ATK Physical at exact three-second CT.
- Generated actions cannot recurse; all owner hits require active field; metadata,
  R1-R5 defaults/validation apply where refinements exist.

Test cases to add or update:

- Normal: each proc, Isshin ATK window, five Harmonics, Debate Skill window.
- Boundary: exact 8/0.5/30/3/15-second boundaries and R1/R5 multipliers.
- Abnormal: wrong/zero/off-field/recursive hits and refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Off-Field Hit Weapon Campaign

Status: Complete. Two bow passives now represent their explicit off-field hit
behavior through the existing post-damage hook.

Scope:

- Add Fading Twilight and Rainbow Serpent's Rain Bow with sourced metadata,
  R1-R5 values, R5 defaults, exact CT/windows, and focused regressions.

Out of scope for this pass:

- Multi-target hit multiplication, characters, formulas, RL, generated docs,
  and unrelated off-field equipment.

### Phase 1: Add Two Off-Field Hit Bows - Done (`aee7c8b`)

Target files:

- `src/java/model/weapon/FadingTwilight.java` (new)
- `src/java/model/weapon/RainbowSerpentsRainBow.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Fading proves initial state, zero-hit exclusion, exact seven-second CT,
  off-field cycling, full three-state wrap, and R1/R5 values.
- Rainbow proves on-field/zero-hit exclusion, off-field activation, on-field
  retention, exact eight-second expiry, and R1/R5 values.
- Metadata, invalid ranks, reaction regression, build, Javadoc, preflight, and
  diff checks pass.

Acceptance criteria:

- Fading Twilight starts in Evengleam and cycles Evengleam -> Afterglow ->
  Dawnblaze -> Evengleam after positive hits at exact seven-second CT, including
  while off-field; its triggering hit uses the prior state through post-damage order.
- Rainbow activates only after positive off-field damage, grants 28-56% ATK in
  one refresh-only half-open eight-second window, and retains it on-field.
- Both expose sourced metadata and R1-R5 values/defaults and reject rank 0/6.

Test cases to add or update:

- Normal: three-state cycle, off-field Rainbow trigger, metadata, R5.
- Boundary: before/exact seven-second CT, before/exact eight-second expiry, R1.
- Abnormal: zero damage, Rainbow on-field damage, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Skill/Burst Offensive Weapon Campaign

Status: Complete. One Skill stat-window sword and one active-owner Burst-proc
claymore now use existing typed action and generated damage contracts.

Scope:

- Add Fleuve Cendre Ferryman and Luxurious Sea-Lord with sourced metadata,
  R1-R5 values, R5 defaults, exact durations/CTs, and focused regressions.

Out of scope for this pass:

- Multi-target multiplication, visual proc delay, characters, formulas, RL,
  generated docs, and unrelated offensive weapons.

### Phase 1: Add Fleuve Cendre Ferryman and Luxurious Sea-Lord - Done (`7e88815`)

Target files:

- `src/java/model/weapon/FleuveCendreFerryman.java` (new)
- `src/java/model/weapon/LuxuriousSeaLord.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Fleuve proves static Skill CRIT, Skill-only refresh, exact five-second ER
  expiry, base-ER composition, and R1/R5 values.
- Sea-Lord proves Burst and counts-as-Burst classification, active-owner and
  zero/wrong-hit gates, exact 15-second CT, and R1/R5 proc/static values.
- Metadata, invalid ranks, reaction regression, build, Javadoc, preflight, and
  diff checks pass.

Acceptance criteria:

- Fleuve always grants 8-16% Skill CRIT and opens one refresh-only half-open
  five-second 16-32% ER window on Skill use only.
- Sea-Lord always grants 12-24% Burst DMG; positive Burst or counts-as-Burst
  damage by the active owner deals 100-200% ATK Physical at exact 15-second CT.
- Generated proc actions cannot recurse; metadata, R1-R5 defaults/validation,
  wrong/zero/off-field gates, and triggering-hit post-order are explicit.

Test cases to add or update:

- Normal: Fleuve static/window stats, Sea-Lord Burst and classified hit, metadata.
- Boundary: refresh and exact five-second expiry, exact 15-second CT, R1/R5.
- Abnormal: wrong/zero/off-field/recursive hits and refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Moonsign Reaction Weapon Campaign

Status: Complete. One shared off-field reaction window and two complete
Moonsign-sensitive weapons now use the existing simulator reaction contract.

Scope:

- Add Master Key and Serenity's Call with sourced metadata, R1-R5 values, R5
  defaults, exact 12-second windows, off-field activation, and live Moonsign
  scaling.

Out of scope for this pass:

- Reaction damage formulas, characters, shields, Plunging-only stats, RL,
  generated docs, and unrelated weapons.

### Phase 1: Add the shared window and both weapons - Done (`d0c59b0`)

Target files:

- `src/java/model/weapon/MoonsignReactionWindowWeapon.java` (new)
- `src/java/model/weapon/MasterKey.java` (new)
- `src/java/model/weapon/SerenitysCall.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Both weapons prove attributed off-field activation, live Ascendant doubling,
  exact refresh/expiry boundaries, and R1/R5 values.
- NONE and foreign reactions, cross-simulator reuse, and invalid refinements are
  rejected; reaction regression, build, Javadoc, preflight, and diff checks pass.

Acceptance criteria:

- An attributed non-NONE reaction from the equipped owner activates or refreshes
  one half-open 12-second window even while that owner is off-field.
- Master Key grants 60-120 EM and Serenity's Call grants 16-32% HP during the
  window; Ascendant Gleam doubles the current bonus and other Moonsigns do not.
- Moonsign changes take effect immediately, metadata and R1-R5 defaults are
  correct, and one weapon instance cannot silently bind to multiple simulators.

Test cases to add or update:

- Normal: off-field owner reaction, both weapon stats, metadata, R5.
- Boundary: live Moonsign transitions, refresh, exact 12-second expiry, R1.
- Abnormal: NONE/foreign-source reactions, cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Reaction Utility Claymore Campaign

Status: Complete. Two reaction-driven claymores now use existing party,
reaction attribution, action stat, and flat-Energy contracts.

Scope:

- Add Earth Shaker and Flame-Forged Insight with sourced metadata, R1-R5
  values, R5 defaults, exact windows/CT, and off-field reaction handling.

Out of scope for this pass:

- Pickup interactions, delayed visual effects, reaction formulas, characters,
  RL, generated docs, and unrelated weapons.

### Phase 1: Add both reaction utility claymores - Done (`8fbad3a`)

Target files:

- `src/java/model/weapon/EarthShaker.java` (new)
- `src/java/model/weapon/FlameForgedInsight.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Earth proves all typed Pyro families, related-element filtering, party-only
  attribution, off-field allies, refresh, exact expiry, and R1/R5 values.
- Flame proves all six listed families, owner-only off-field attribution, flat
  Energy/cap, exact CT/expiry, and R1/R5 values; all project gates pass.

Acceptance criteria:

- Any party member's Pyro-related reaction opens one refresh-only half-open
  eight-second 16-32% Skill DMG window for Earth Shaker, including off-field.
- Flame-Forged Insight accepts only its six listed owner-attributed reaction
  families, restores 12-24 flat Energy, and grants 60-120 EM for 15 seconds at
  an exact 15-second trigger CT, including while the owner is off-field.
- Typed reaction/related-element gates, party/source attribution, metadata,
  R1-R5 defaults, simulator binding, and invalid inputs are explicit.

Test cases to add or update:

- Normal: each eligible reaction family, ally/off-field Earth trigger, owner
  off-field Flame trigger, Energy restoration, metadata, R5.
- Boundary: Earth refresh/expiry, Flame exact CT/expiry, Energy cap, R1.
- Abnormal: unrelated reactions/elements, nonparty Earth source, ally Flame
  source, cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Timed EM Team Weapon Campaign

Status: Complete. One verified periodic EM snapshot now supports all three
weapon-family variants without changing timer or buff dispatch.

Scope:

- Preserve and refine Wandering Evenstar, then add Makhaira Aquamarine and
  Xiphos' Moonlight with sourced metadata, R1-R5 values, and R5 defaults.

Out of scope for this pass:

- Runtime equipment swaps, report assets, characters, formulas, RL, generated
  docs, and unrelated periodic effects.

### Phase 1: Generalize and complete the timed EM team-stat family - Done (`961825a`)

Target files:

- `src/java/mechanics/buff/BuffId.java`
- `src/java/model/type/StatType.java`
- `src/java/model/stats/StatsContainer.java`
- `src/java/mechanics/energy/EnergyDistributor.java`
- `src/java/mechanics/analysis/StatsRecorder.java`
- `src/java/visualization/ReportViewAdapter.java`
- `src/java/model/weapon/TimedElementalMasteryTeamStatWeapon.java` (new)
- `src/java/model/weapon/WanderingEvenstar.java`
- `src/java/model/weapon/MakhairaAquamarine.java` (new)
- `src/java/model/weapon/XiphosMoonlight.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Wandering retains its timing/snapshot/stack behavior with R1-R5 support;
  Makhaira and Xiphos prove owner/ally R1/R5 conversion and metadata.
- Xiphos total ER affects off-field particle recovery and reporting while its
  typed component stays out of Emblem/Raiden conversion; build, reaction/report
  regressions, Javadoc, preflight, and diff checks pass.

Acceptance criteria:

- Every weapon snapshots the owner's complete effective EM at 64 frames and
  every ten seconds thereafter into one refresh-only half-open 12-second buff.
- Wandering and Makhaira grant 24-48% of EM as flat ATK; Xiphos grants
  0.036-0.072% ER per EM; allies receive exactly 30% and the owner is excluded
  from that share.
- Xiphos ER contributes to particle recovery and displayed total ER but remains
  excluded from Raiden A4 and Emblem damage conversion for owner and allies.
- Independent instances stack, snapshots remain fixed between ticks, metadata
  and R1-R5 values/defaults are correct, and cross-simulator reuse is rejected.

Test cases to add or update:

- Normal: all three metadata/effects, owner versus ally share, R5, multiple stack.
- Boundary: before/exact 64 frames, ten-second resnapshot, 12-second duration, R1.
- Abnormal: owner ally-share exclusion, non-converting ER leakage into Raiden
  A4/Emblem, cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Self-Contained Five-Star Weapon Campaign

Status: Complete. Two complete five-star weapons now use existing typed Burst,
hit, generated Physical damage, Skill window, and Lunar stat contracts.

Scope:

- Add Skyward Pride and Lightbearing Moonshard with sourced metadata, R1-R5
  values, R5 defaults, exact limits/durations, and focused regressions.

Out of scope for this pass:

- Movement speed, multi-target multiplication, visual projectile travel,
  characters, formulas, RL, generated docs, and unrelated five-star weapons.

### Phase 1: Add Skyward Pride and Lightbearing Moonshard - Done (`4130ebc`)

Target files:

- `src/java/model/weapon/SkywardPride.java` (new)
- `src/java/model/weapon/LightbearingMoonshard.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Pride proves static all-DMG, R1/R5 blade values, typed/active gates, exactly
  eight nonrecursive blades, and the half-open 20-second state.
- Moonshard proves static DEF, R1/R5 Lunar-Crystallize values, refresh, and exact
  five-second expiry; build, reaction regression, Javadoc, and preflight pass.

Acceptance criteria:

- Skyward Pride always grants 8-16% all DMG; active-owner Burst use opens one
  half-open 20-second state where up to eight positive Normal/Charged hits each
  generate one 80-160% ATK Physical blade without recursion.
- Lightbearing Moonshard always grants 20-40% DEF and opens one refresh-only
  half-open five-second 64-128% Lunar-Crystallize DMG window on Skill use.
- Metadata, typed gates, triggering-hit post-order, R1-R5 defaults/validation,
  exact expiry, wrong/zero/off-field exclusions, and use limits are explicit.

Test cases to add or update:

- Normal: both metadata/static stats, Pride eight blades, Moonshard Skill window, R5.
- Boundary: Pride ninth hit and exact 20 seconds, Moonshard refresh/exact five seconds, R1.
- Abnormal: wrong/zero/off-field/recursive Pride hits and refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Energy and Proximity Five-Star Weapon Campaign

Status: Complete. One live-Energy Skill-window sword and one single-enemy
proximity bow now work without extending simulator dispatch.

Scope:

- Add Azurelight and Aqua Simulacra with sourced metadata, R1-R5 values, R5
  defaults, exact dynamic/static bonuses, and focused regressions.

Out of scope for this pass:

- Multi-target distance modeling, overworld no-enemy states, characters,
  formulas, RL, generated docs, and unrelated weapons.

### Phase 1: Add Azurelight and Aqua Simulacra - Done (`ab98b46`)

Target files:

- `src/java/model/weapon/Azurelight.java` (new)
- `src/java/model/weapon/AquaSimulacra.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Azure proves live positive/zero Energy transitions, both R1/R5 branches,
  active-owner gates, refresh, exact expiry, and simulator reuse rejection.
- Aqua proves the explicit nearby-enemy static HP/all-DMG model and R1/R5
  metadata; build, reaction regression, Javadoc, preflight, and diff checks pass.

Acceptance criteria:

- Azurelight opens one refresh-only half-open 12-second 24-48% ATK window on
  active-owner Skill use; while live Energy is exactly zero, it grants another
  24-48% ATK and 40-80% CRIT DMG, with immediate state transitions.
- Aqua Simulacra grants 16-32% HP and 20-40% all DMG under the simulator's
  explicit single-nearby-enemy combat assumption, including off-field damage.
- Metadata, hook order for the triggering Skill, owner/simulator binding,
  R1-R5 defaults/validation, refresh, and exact expiry are explicit.

Test cases to add or update:

- Normal: metadata, Azure full/zero Energy transitions, Aqua static stats, R5.
- Boundary: Azure refresh/exact 12 seconds, exact zero versus positive Energy, R1.
- Abnormal: wrong/off-field Azure action, cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Sumeru Action-Proc Bow Campaign

Status: Complete. Two action-opened bow states now use typed actions, hits,
switch-out, timers, and generated Physical damage.

Scope:

- Add End of the Line and King's Squire with sourced metadata, R1-R5 values,
  R5 defaults, exact limits/durations/CTs, and focused regressions.

Out of scope for this pass:

- Multi-target multiplication, visual projectile travel, characters, formulas,
  RL, generated docs, and unrelated Sumeru weapons.

### Phase 1: Add End of the Line and King's Squire - Done (`6e74d66`)

Target files:

- `src/java/model/weapon/EndOfTheLine.java` (new)
- `src/java/model/weapon/KingsSquire.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Flowrider proves three positive typed procs, two-second proc CT, 12-second
  activation CT, 15-second expiry, exclusions, and R1/R5 values.
- Squire proves Skill/Burst states, 20-second CT, natural/switch single end proc,
  stale-timer rejection, and R1/R5 values; all project gates pass.

Acceptance criteria:

- Active-owner Skill use opens End of the Line's half-open 15-second Flowrider
  at exact 12-second activation CT; up to three positive attack hits generate
  one 80-160% ATK Physical proc each at exact two-second proc CT.
- Active-owner Skill or Burst use opens King's Squire's half-open 12-second
  60-140 EM state at exact 20-second CT; natural expiry or switch-out ends it
  once and generates one 100-180% ATK Physical proc without stale-timer repeats.
- Metadata, R1-R5 defaults/validation, active/wrong/zero/recursive exclusions,
  triggering-action order, counters, and exact boundaries are explicit.

Test cases to add or update:

- Normal: both metadata, three Flowrider procs, Skill/Burst Squire states, R5.
- Boundary: exact two/12/15/20 seconds, natural versus switch end, R1.
- Abnormal: wrong/zero/off-field/recursive hits, stale timer, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Typed Five-Star Bow Campaign

Status: Complete. Two five-star bows now use typed hit stacks, owner Swirl
attribution, and live party element composition.

Scope:

- Add Polar Star and Astral Vulture's Crimson Plumage with sourced metadata,
  R1-R5 values, R5 defaults, exact windows, and focused regressions.

Out of scope for this pass:

- Movement speed, Hexerei effects, multi-target multiplication, characters,
  formulas, RL, generated docs, and unrelated five-star bows.

### Phase 1: Add Polar Star and Astral Vulture's Crimson Plumage - Done (`08637ba`)

Target files:

- `src/java/model/weapon/PolarStar.java` (new)
- `src/java/model/weapon/AstralVulturesCrimsonPlumage.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Polar proves static action bonuses, all four R1/R5 ATK tiers, independent
  refresh/expiry, and zero/wrong/off-field exclusions.
- Astral proves attributed active-owner Swirl, exact expiry, live same/one/two
  ally composition, R1/R5 tiers, and reuse rejection; all project gates pass.

Acceptance criteria:

- Polar Star always grants 12-24% Skill/Burst DMG; positive active-owner
  Normal, Charged, Skill, and Burst hits each own one independently refreshed
  half-open 12-second stack, granting 10/20/30/48%-20/40/60/96% ATK by count.
- Active-owner Swirl opens Astral Vulture's half-open 12-second 24-48% ATK
  window; one/two-or-more different-element allies dynamically grant the exact
  20/48%-40/96% Charged and 10/24%-20/48% Burst tiers.
- Metadata, typed/related-element/source/active gates, live party changes,
  R1-R5 defaults/validation, refresh, and exact expiry are explicit.

Test cases to add or update:

- Normal: four Polar types/tiers, Astral Swirl and one/two ally tiers, metadata, R5.
- Boundary: independent Polar expiry/refresh, Astral exact 12 seconds, R1.
- Abnormal: zero/wrong/off-field Polar hits, NONE/foreign/off-field Astral
  reactions, cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Energy-Conditional Emblem Weapon Campaign

Status: Complete. Two five-star emblem weapons now use typed actions,
post-damage stacks, and live Energy fullness.

Scope:

- Add Mistsplitter Reforged and Thundering Pulse with sourced metadata, R1-R5
  values, R5 defaults, exact independent windows, and focused regressions.

Out of scope for this pass:

- Projectile travel, infusion providers, characters, formulas, RL, generated
  docs, and unrelated emblem weapons.

### Phase 1: Add Mistsplitter Reforged and Thundering Pulse - Done (`a3935ae`)

Target files:

- `src/java/model/weapon/MistsplitterReforged.java` (new)
- `src/java/model/weapon/ThunderingPulse.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Mistsplitter proves every elemental static bonus, all three independent
  R1/R5 emblem tiers, exact Energy-full removal, expiry, and invalid hit gates.
- Thundering proves static ATK, Normal-only emblem tiers, Skill pre-order,
  exact expiry, off-field exclusions, and binding; all project gates pass.

Acceptance criteria:

- Mistsplitter always grants 12-24% all Elemental DMG; elemental Normal/Charged
  damage owns a five-second stack, Burst use owns a ten-second stack, and live
  Energy below full owns a third, yielding exact 8/16/28%-16/32/56% owner-element tiers.
- Thundering Pulse always grants 20-40% ATK; positive active-owner Normal damage
  owns a five-second stack, Skill use owns a ten-second stack, and live Energy
  below full owns a third, yielding exact 12/24/40%-24/48/80% Normal DMG tiers.
- Triggering-hit post-order, Burst/Skill pre-order, typed/element/active gates,
  live Energy transitions, R1-R5 defaults/validation, and binding are explicit.

Test cases to add or update:

- Normal: all metadata/static values, each stack/tier, full-to-low Energy, R5.
- Boundary: exact five/ten seconds, refresh independence, exact full Energy, R1.
- Abnormal: Physical/wrong/zero/off-field hits, cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Five-Star Catalyst Stack Weapon Campaign

Status: Complete. Two combat-complete five-star catalysts now use typed Skill
input, fixed timer cadence, and switch reset behavior.

Scope:

- Add Kagura's Verity and Lost Prayer to the Sacred Winds with sourced
  metadata, R1-R5 values, exact stack timing, and focused regressions.

Out of scope for this pass:

- Lost Prayer's non-DPS Movement SPD, defeat/combat-exit state, characters,
  formulas, RL, generated docs, and unrelated catalysts.

### Phase 1: Add Kagura's Verity and Lost Prayer to the Sacred Winds - Done (`ce792c0`)

Target files:

- `src/java/model/weapon/KagurasVerity.java` (new)
- `src/java/model/weapon/LostPrayerToTheSacredWinds.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Kagura proves pre-action R1/R5 Skill tiers, three-stack all-element bonus,
  shared refresh, exact expiry, cap, invalid actions, and binding rejection.
- Lost Prayer proves all-element R1/R5 tiers, exact four-second cadence, cap,
  off-field suppression, cadence-preserving return, and switch reset; all gates pass.

Acceptance criteria:

- Kagura Skill input applies 12-24% Skill DMG per stack before action
  resolution, refreshes one shared half-open 24-second window, caps at three,
  and grants 12-24% all Elemental DMG only while all three stacks remain.
- Lost Prayer gains one 8-16% all Elemental DMG stack at each fixed four-second
  combat tick only while active, caps at four, keeps global cadence while
  off-field, and clears all stacks immediately on switch-out.
- Metadata, pre-action order, exact expiry/ticks, active/owner/simulator gates,
  R1-R5 defaults/validation, and cross-simulator binding are explicit.

Test cases to add or update:

- Normal: metadata, all Kagura/Lost Prayer stack tiers and caps, R5.
- Boundary: exact 4/24 seconds, Skill refresh, off-field cadence and return, R1.
- Abnormal: wrong/foreign Kagura actions, Lost Prayer off-field ticks, switch
  reset, cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Injected Bow Proc Weapon Campaign

Status: Complete. Two proc bows now use injectable draws, typed hit gates,
immediate/periodic Physical damage, and exact cooldowns.

Scope:

- Add Skyward Harp and The Viridescent Hunt with sourced metadata, R1-R5
  values, deterministic constructors, exact proc timing, and regressions.

Out of scope for this pass:

- Multi-target pull/displacement, projectile travel, characters, formulas, RL,
  generated docs, and unrelated stochastic weapons.

### Phase 1: Add Skyward Harp and The Viridescent Hunt - Done (`877a526`)

Target files:

- `src/java/model/weapon/SkywardHarp.java` (new)
- `src/java/model/weapon/TheViridescentHunt.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Skyward Harp proves R1/R5 CRIT and chance values, immediate proc damage,
  exact cooldown, failed/zero draws, and nonrecursive behavior.
- Viridescent Hunt proves typed active-owner gates, eight equal half-second
  ticks, exact R1/R5 cooldowns/MVs, failed draws, and binding; all gates pass.

Acceptance criteria:

- Skyward Harp grants 20-40% CRIT DMG; positive hits draw against 60-100%,
  produce one immediate nonrecursive 125% ATK Physical action, and observe the
  exact 4-2-second refinement cooldown.
- Viridescent Hunt positive active-owner Normal/Charged hits draw at 50%; a
  success schedules exactly eight nonrecursive 40-80% ATK Physical hits at
  0.5-second cadence over four seconds with exact 14-10-second activation CT.
- Metadata, draw injection/null rejection, chance thresholds, typed/zero/
  off-field/recursive gates, R1-R5 defaults, and validation are explicit.

Test cases to add or update:

- Normal: metadata/static CRIT, successful immediate and eight-hit procs, R5.
- Boundary: exact 0.5/four-second ticks, exact R1/R5 CT, chance threshold, R1.
- Abnormal: zero/wrong/off-field/recursive hits, failed draw, null draw,
  refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Live Party Five-Star Weapon Campaign

Status: Complete. Two combat-complete five-star weapons now use live party
composition and attributed active-character Geo damage.

Scope:

- Add The First Great Magic and Uraku Misugiri with sourced metadata, R1-R5
  values, exact live tiers/windows, and focused regressions.

Out of scope for this pass:

- First Great Magic's non-DPS Movement SPD, characters, formulas, RL,
  generated docs, and unrelated party-state weapons.

### Phase 1: Add The First Great Magic and Uraku Misugiri - Done (`35c25d2`)

Target files:

- `src/java/model/weapon/TheFirstGreatMagic.java` (new)
- `src/java/model/weapon/UrakuMisugiri.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- First Great Magic proves R1/R5 Charged values, live one/two/three same-element
  ATK tiers, owner inclusion, different-element exclusion, cap, and binding.
- Uraku proves base Normal/Skill/DEF, active ally/owner Geo activation,
  zero/non-Geo/off-field gates, exact expiry, and R1/R5; all gates pass.

Acceptance criteria:

- First Great Magic always grants 16-32% Charged DMG and dynamically grants
  16/32/48%-32/64/96% ATK for one/two/three-or-more same-element party members,
  including the owner, with a strict three-stack cap.
- Uraku always grants 16-32% Normal DMG, 24-48% Skill DMG, and 20-40% DEF;
  positive Geo damage by the active party member doubles both action bonuses
  for a half-open 15-second window without changing DEF.
- Metadata, post-damage order, live party changes, positive/Geo/active/source
  gates, exact expiry, R1-R5 defaults/validation, and binding are explicit.

Test cases to add or update:

- Normal: metadata/static values, all First Magic tiers, ally/owner Uraku Geo,
  R5.
- Boundary: three-stack cap, live member addition, exact 15 seconds, R1.
- Abnormal: zero/non-Geo/off-field Uraku damage, cross-simulator reuse,
  refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Moonsign EM and Bloom Weapon Campaign

Status: Complete. Two Moonsign weapons now use the verified reaction window
and live Moonsign stat contracts.

Scope:

- Add Snare Hook and Blackmarrow Lantern with sourced metadata, R1-R5 values,
  exact reaction timing, live Ascendant bonuses, and focused regressions.

Out of scope for this pass:

- Characters, formulas, RL, generated docs, and unrelated Moonsign equipment.

### Phase 1: Add Snare Hook and Blackmarrow Lantern - Done (`8908f92`)

Target files:

- `src/java/model/weapon/SnareHook.java` (new)
- `src/java/model/weapon/BlackmarrowLantern.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Snare proves R1/R5 off-field owner reactions, NONE/foreign exclusion, live
  Ascendant doubling, exact expiry, metadata, and inherited binding rejection.
- Blackmarrow proves R1/R5 Bloom/Lunar values and reversible live Ascendant
  Lunar-only addition without altering Bloom; all project gates pass.

Acceptance criteria:

- Snare Hook owner reactions, including off-field reactions, open a half-open
  12-second 60-120 EM window; live Ascendant Gleam doubles it to 120-240 EM.
- Blackmarrow Lantern always grants 48-96% Bloom and 12-24% Lunar-Bloom DMG;
  live Ascendant Gleam adds another 12-24% Lunar-Bloom DMG.
- Metadata, NONE/foreign reaction gates, exact expiry, live Moonsign changes,
  R1-R5 defaults/validation, and owner/simulator binding are explicit.

Test cases to add or update:

- Normal: metadata, off-field reaction window, Bloom/Lunar values, R5.
- Boundary: exact 12 seconds, live Nascent/Ascendant changes, R1.
- Abnormal: NONE/foreign reactions, cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Catalyst Dual-Window Weapon Campaign

Status: Complete. Two catalysts now use independent typed action, damage, and
Lunar-Bloom windows.

Scope:

- Add Dawning Frost and Reliquary of Truth with sourced metadata, R1-R5 values,
  exact independent windows, intersection scaling, and focused regressions.

Out of scope for this pass:

- Characters, formulas, RL, generated docs, and unrelated catalysts.

### Phase 1: Add Dawning Frost and Reliquary of Truth - Done (`542fceb`)

Target files:

- `src/java/model/weapon/DawningFrost.java` (new)
- `src/java/model/weapon/ReliquaryOfTruth.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Dawning proves R1/R5 Charged/Skill windows, overlap addition, staggered exact
  expiry, typed/zero/off-field gates, metadata, and binding rejection.
- Reliquary proves static CRIT, pre-Skill EM, attributed Lunar-Bloom CRIT DMG,
  live 1.5x overlap, independent exact expiry, and abnormal gates; all gates pass.

Acceptance criteria:

- Positive active-owner Charged/Skill hits independently open half-open
  ten-second Dawning Frost windows granting 72-144 and 48-96 EM; both add.
- Reliquary always grants 8-16% CRIT Rate; active-owner Skill use opens a
  half-open 12-second 80-160 EM window and owner Lunar-Bloom opens a half-open
  four-second 24-48% CRIT DMG window; while both overlap each result is 1.5x.
- Metadata, pre-action/post-hit order, typed/positive/active/source gates,
  exact independent expiry, R1-R5 defaults/validation, and binding are explicit.

Test cases to add or update:

- Normal: metadata, each independent window, overlap totals, R5.
- Boundary: exact four/ten/12 seconds, one-window expiry while another remains, R1.
- Abnormal: zero/wrong/off-field/foreign/NONE events, cross-simulator reuse,
  refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Fruit of Fulfillment Campaign

Status: Complete. Fruit of Fulfillment now uses off-field reaction stacking and
generation-safe inactivity decay.

Scope:

- Add Fruit of Fulfillment with sourced metadata, R1-R5 values, exact stack
  gain/decay timing, stale-timer rejection, and focused regressions.

Out of scope for this pass:

- Hakushin reaction-element distribution, characters, formulas, RL, generated
  docs, and unrelated catalysts.

### Phase 1: Add Fruit of Fulfillment - Done (`c7ebd3a`)

Target files:

- `src/java/model/weapon/FruitOfFulfillment.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- R1/R5 metadata, EM and fixed ATK penalty, off-field owner attribution,
  0.3-second gain CT, five-stack cap, and NONE/foreign gates pass.
- Reaction refresh invalidates stale decay events; exact six-second repeated
  decay reaches and remains at zero; build/Javadoc/leak gates pass.

Acceptance criteria:

- Any non-NONE owner reaction, including off-field reactions, gains at most one
  Wax and Wane stack per 0.3 seconds, up to five; each stack grants 24-36 EM and
  removes exactly 5% ATK.
- Every owner reaction resets inactivity timing; after six seconds without one,
  exactly one stack is lost every six seconds until zero, with stale decay
  events unable to mutate refreshed state.
- Metadata, source/NONE gates, exact 0.3/6-second boundaries, cap/floor,
  R1-R5 defaults/validation, and binding are explicit.

Test cases to add or update:

- Normal: metadata, off-field five-stack R5 EM/ATK, R1.
- Boundary: exact 0.3 seconds, exact repeated six-second decay, cap/floor,
  reaction refresh invalidating stale timer.
- Abnormal: NONE/foreign reaction, cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Scion of the Blazing Sun Campaign

Status: Complete. Scion of the Blazing Sun now has a nonrecursive Physical proc
and single-target Heartsearer window.

Scope:

- Add Scion of the Blazing Sun with sourced metadata, R1-R5 values, exact proc/
  debuff timing, single-enemy targeting, and focused regressions.

Out of scope for this pass:

- Multi-target debuff partitioning, characters, formulas, RL, generated docs,
  and unrelated Battle Pass weapons.

### Phase 1: Add Scion of the Blazing Sun - Done (`a22e1ba`)

Target files:

- `src/java/model/weapon/ScionOfTheBlazingSun.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- R1/R5 metadata and proc ratios, immediate nonrecursive Physical damage,
  post-hit Heartsearer, exact expiry/reactivation, and all invalid gates pass.
- Single-target mapping and cross-simulator binding are explicit; build,
  reaction regression, Javadoc, and artifact-leak gates pass.

Acceptance criteria:

- Positive active-owner Charged hits at exact ten-second CT produce one
  immediate nonrecursive 60-120% ATK Physical action, then open a half-open
  ten-second owner Charged DMG window of 28-56% against the modeled target.
- Triggering Charged damage resolves before Heartsearer; generated proc damage
  cannot recurse or refresh the state.
- Metadata, positive/typed/active/source gates, exact expiry/reactivation,
  R1-R5 defaults/validation, and binding are explicit.

Test cases to add or update:

- Normal: metadata, immediate proc and Heartsearer, R5.
- Boundary: exact ten-second expiry/reactivation, R1 MV/bonus.
- Abnormal: zero/wrong/off-field/recursive hits, cross-simulator reuse,
  refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Alley Hunter Campaign

Status: Complete. Alley Hunter now has fixed-cadence off-field growth and
on-field decay after its four-second grace period.

Scope:

- Add Alley Hunter with sourced metadata, R1-R5 values, live field-state
  progression, cap/floor behavior, and focused regressions.

Out of scope for this pass:

- Other bows, characters, formulas, RL, generated docs, and changes to the
  simulator switch lifecycle.

### Phase 1: Add Alley Hunter - Done (`4aca5e4`)

Target files:

- `src/java/model/weapon/AlleyHunter.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- R1/R5 metadata, exact first tick, ten-stack cap, four-tick active grace,
  two-stack decay, return-field grace reset, and zero floor pass.
- Initial active/off-field state and cross-simulator binding are explicit;
  build, reaction regression, Javadoc, and artifact-leak gates pass.

Acceptance criteria:

- A bound off-field owner gains one 2-4% all-DMG stack on each fixed one-second
  combat tick, up to ten stacks and 20-40% total.
- After the owner remains active for four full seconds, each subsequent
  one-second tick removes two stacks, matching 4-8% per second, to zero.
- Metadata, initial active/off-field state, exact cadence/grace, return-field
  reset, cap/floor, R1-R5 defaults/validation, and binding are explicit.

Test cases to add or update:

- Normal: metadata, off-field growth to R5 cap, on-field delayed decay.
- Boundary: exact one/four/five-second ticks, cap/floor, switch-out grace reset,
  R1 values.
- Abnormal: cross-simulator reuse and refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Sequence of Solitude Campaign

Status: Complete. Sequence of Solitude now has a nonrecursive Max-HP Physical
proc on a fixed 15-second cooldown.

Scope:

- Add Sequence of Solitude with sourced metadata, R1-R5 values, immediate HP
  scaling damage, exact cooldown behavior, and focused regressions.

Out of scope for this pass:

- Multi-target AoE multiplication, other bows, characters, formulas, RL, and
  generated docs.

### Phase 1: Add Sequence of Solitude - Done (`09eb8ff`)

Target files:

- `src/java/model/weapon/SequenceOfSolitude.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- R1/R5 metadata and exact 1:2 proc ratio, total-HP scaling, immediate damage,
  nonrecursion, and before/exact 15-second cooldown behavior pass.
- Active/source/simulator/positive gates and cross-simulator binding are
  explicit; build, reaction regression, Javadoc, and artifact-leak gates pass.

Acceptance criteria:

- Any positive active-owner attack hit at exact 15-second CT produces one
  immediate nonrecursive Physical action scaling from 40-80% of Max HP.
- Metadata, HP scaling, positive/active/source/simulator gates, exact cooldown,
  R1-R5 defaults/validation, and binding are explicit.

Test cases to add or update:

- Normal: metadata, immediate R5 HP proc, arbitrary positive action type.
- Boundary: before/exact 15-second CT and R1/R5 damage ratio.
- Abnormal: zero/off-field/foreign/recursive/wrong-simulator hits,
  cross-simulator reuse, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Eye of Perception Campaign

Status: Complete. Eye of Perception now uses the deterministic direct-proc
policy.

Scope:

- Add Eye of Perception with sourced metadata, R1-R5 values, injectable chance,
  exact cooldown behavior, single-target Bolt damage, and focused regressions.

Out of scope for this pass:

- Multi-target bounce multiplication, other catalysts, characters, formulas,
  RL, and generated docs.

### Phase 1: Add Eye of Perception - Done (`ed9d3a8`)

Target files:

- `src/java/model/weapon/EyeOfPerception.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- R1/R5 metadata and damage ratio, Normal/Charged gates, 0.5 draw boundary,
  exact eight/12-second CTs, immediate damage, and nonrecursion pass.
- Zero/wrong/null/refinement rejection is explicit; build, reaction regression,
  Javadoc, and artifact-leak gates pass.

Acceptance criteria:

- Positive Normal/Charged hits with a draw below 0.5 produce one immediate
  nonrecursive 240-360% ATK Physical action at exact 12-8-second CT.
- Metadata, typed/positive/chance/recursive gates, exact cooldown, R1-R5
  defaults/validation, and null draw rejection are explicit.

Test cases to add or update:

- Normal: metadata and immediate R5 Normal/Charged proc.
- Boundary: draw immediately below/at 0.5, before/exact R5 CT, R1 MV/CT.
- Abnormal: zero/wrong/recursive hits, null draw, refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: One-Star Weapon Series Campaign

Status: Complete. The five passive-free starter weapons are represented as one
homogeneous content unit; RL and generated documentation remain excluded.

Scope:

- Add Dull Blade, Waster Greatsword, Beginner's Protector, Apprentice's Notes,
  and Hunter's Bow with their maximum-level base ATK and exact weapon types.
- Keep the no-substat, no-refinement, and no-passive contract explicit.

Out of scope for this pass:

- Higher-rarity weapons, inventory/equipment acquisition, level progression,
  characters, shared runtime changes, RL, and generated docs.

### Phase 1: Add the Complete One-Star Series - Done

Target files:

- `src/java/model/weapon/DullBlade.java` (new)
- `src/java/model/weapon/WasterGreatsword.java` (new)
- `src/java/model/weapon/BeginnersProtector.java` (new)
- `src/java/model/weapon/ApprenticesNotes.java` (new)
- `src/java/model/weapon/HuntersBow.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- All five maximum-level metadata contracts and every absent secondary stat
  pass in one table-driven regression.
- Passive application at negative and positive times leaves seeded stats
  unchanged; reaction regression, build, Javadoc, and preflight gates pass.

Acceptance criteria:

- Each class reports its exact display name, matching weapon type, and 185
  base ATK at its maximum supported level.
- Each weapon contributes no secondary stat and applies no passive mutation at
  arbitrary simulation times.
- The implementation adds no event interfaces, refinement input, random state,
  or simulator coupling.

Test cases to add or update:

- Normal: all five names, weapon types, and base ATK values.
- Boundary: passive application at negative and positive times leaves a seeded
  stat container unchanged.
- Abnormal: no event capability is registered and no absent secondary stat is
  synthesized.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Two-Star Weapon Series Campaign

Status: Complete. The five passive-free two-star weapons are represented as one
homogeneous content unit; RL and generated documentation remain excluded.

Scope:

- Add Silver Sword, Old Merc's Pal, Iron Point, Pocket Grimoire, and Seasoned
  Hunter's Bow with their maximum-level base ATK and exact weapon types.
- Keep the no-substat, no-refinement, and no-passive contract explicit.

Out of scope for this pass:

- Higher-rarity weapons, inventory/equipment acquisition, level progression,
  characters, shared runtime changes, RL, and generated docs.

### Phase 1: Add the Complete Two-Star Series - Done

Target files:

- `src/java/model/weapon/SilverSword.java` (new)
- `src/java/model/weapon/OldMercsPal.java` (new)
- `src/java/model/weapon/IronPoint.java` (new)
- `src/java/model/weapon/PocketGrimoire.java` (new)
- `src/java/model/weapon/SeasonedHuntersBow.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- All five maximum-level metadata contracts and every absent secondary stat
  pass through the shared passive-free weapon regression.
- Passive application at negative and positive times leaves seeded stats
  unchanged; reaction regression, build, Javadoc, and preflight gates pass.

Acceptance criteria:

- Each class reports its exact display name, matching weapon type, and 243
  base ATK at its maximum supported level.
- Each weapon contributes no secondary stat and applies no passive mutation at
  arbitrary simulation times.
- The implementation adds no event interfaces, refinement input, random state,
  or simulator coupling.

Test cases to add or update:

- Normal: all five names, weapon types, and base ATK values.
- Boundary: passive application at negative and positive times leaves a seeded
  stat container unchanged.
- Abnormal: no event capability is registered and no absent secondary stat is
  synthesized.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Isolated Runtime Weapon Expansion Campaign

Status: Complete. Two branch-isolated implementation lanes added four weapons
whose complete combat contracts fit existing narrow capability interfaces; RL
and generated documentation remain excluded.

Scope:

- Add "Ultimate Overlord's Mega Magic Sword" and Ash-Graven Drinking Horn in
  the first isolated integration phase.
- Add Toukabou Shigure and Waveriding Whirl in the second isolated integration
  phase.
- Keep quest assistance explicit, and preserve the simulator's immortal
  single-enemy and no-swimming boundaries.

Out of scope for this pass:

- Enemy defeat callbacks, multi-target proc multiplication, swimming stamina,
  shared runtime changes, characters, RL, and generated docs.

### Phase 1: Static Assistance and Max-HP Proc Weapons - Done

Target files:

- `src/java/model/weapon/UltimateOverlordsMegaMagicSword.java` (new)
- `src/java/model/weapon/AshGravenDrinkingHorn.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Ultimate Overlord metadata, 0/3/6 assistance, R1/R5 scaling, and invalid
  refinement/assistance boundaries pass.
- Ash-Graven metadata, immediate Max-HP damage, R1/R5 ratio, nonrecursion,
  ownership gates, exact cooldown, and binding rejection pass.
- Reaction regression, build, Javadoc, and preflight gates pass.

Acceptance criteria:

- Ultimate Overlord exposes Lv. 90 metadata, R1-R5 validation, and explicit
  zero-through-six Melusine assistance with linear additional ATK.
- Ash-Graven exposes Lv. 90 metadata and an immediate nonrecursive Max-HP
  Physical proc on any positive active-owner hit at the exact 15-second CT.
- Cross-simulator reuse, invalid refinement/assistance, zero/foreign/off-field
  hits, recursive hits, and cooldown boundaries are rejected or inert.

Test cases to add or update:

- Normal: metadata, R5/full assistance, and immediate R5 Max-HP proc.
- Boundary: zero/six assistance, R1/R5 ratios, and before/exact 15-second CT.
- Abnormal: assistance -1/7, refinement 0/6, zero/off-field/foreign/recursive
  hits, wrong simulator, and cross-simulator binding.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

### Phase 2: Single-Target Mark and Hydro-Party HP Window - Done

Target files:

- `src/java/model/weapon/ToukabouShigure.java` (new)
- `src/java/model/weapon/WaveridingWhirl.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Toukabou metadata, unbuffed triggering hit, R1/R5 marked-target bonuses,
  half-open mark duration, activation CT, owner gates, and binding pass.
- Waveriding metadata, zero-through-three Hydro counts, two-addition cap,
  R1/R5 bonuses, half-open duration, activation CT, action/owner gates, and
  binding pass.
- Reaction regression, build, Javadoc, and preflight gates pass.

Acceptance criteria:

- Toukabou exposes Lv. 90 metadata; its triggering hit remains unbuffed and
  subsequent owner damage receives the R1-R5 bonus during the half-open
  ten-second target window, with a 15-second activation CT.
- Waveriding exposes Lv. 90 metadata; eligible Skill use grants the R1-R5 base
  Max-HP bonus plus up to two live Hydro-party additions for ten seconds, with
  a 15-second activation CT.
- Foreign/off-field/zero/wrong-action/wrong-simulator triggers and
  cross-simulator reuse are inert or rejected; exact expiry and CT boundaries
  are explicit.

Test cases to add or update:

- Normal: metadata, R1/R5 target bonus, and zero/one/two/three Hydro counts.
- Boundary: triggering-hit order, exact ten-second expiry, and before/exact
  15-second reactivation.
- Abnormal: zero/off-field/foreign/wrong-action/wrong-simulator triggers,
  binding reuse, and refinement 0/6.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Blackcliff Weapon Family Campaign

Status: Complete. This campaign adds all five Blackcliff weapons behind one
family metadata boundary; RL and generated documentation remain excluded.

Scope:

- Add Blackcliff Longsword, Slasher, Pole, Agate, and Warbow with exact Lv. 90
  metadata and R1-R5 validation.
- Centralize the shared Press the Advantage boundary in one family base.

Out of scope for this pass:

- Enemy defeat and kill attribution, multi-enemy combat, other weapon series,
  shared runtime changes, characters, RL, and generated docs.

### Phase 1: Add the Complete Blackcliff Family - Done

Target files:

- `src/java/model/weapon/BlackcliffWeapon.java` (new)
- `src/java/model/weapon/BlackcliffLongsword.java` (new)
- `src/java/model/weapon/BlackcliffSlasher.java` (new)
- `src/java/model/weapon/BlackcliffPole.java` (new)
- `src/java/model/weapon/BlackcliffAgate.java` (new)
- `src/java/model/weapon/BlackcliffWarbow.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- A shared constructor-factory regression covers all five R5 metadata records,
  R1 static boundaries, and every refinement 0/6 rejection.
- Passive application at negative and positive times preserves seeded ATK
  because no defeat event exists; all local verification gates pass.

Acceptance criteria:

- All five classes expose exact display names, weapon types, base ATK, CRIT
  DMG, R5 defaults, and R1-R5 validation.
- Shared family code documents and enforces that Press the Advantage remains
  inactive because the simulator has no enemy defeat or kill-attribution event.
- Passive application at arbitrary times must not fabricate ATK stacks.

Test cases to add or update:

- Normal: all five R5 metadata records and no passive ATK mutation.
- Boundary: R1 and R5 retain identical static metadata while exposing the
  selected refinement.
- Abnormal: refinement 0/6 rejection for every concrete class.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Three-Star Runtime-Boundary Weapon Campaign

Status: Complete. This campaign adds all eleven representable missing
three-star weapons while leaving the unsupported incoming-switch TTDS contract
explicitly unimplemented; RL and generated documentation remain excluded.

Scope:

- Add Harbinger of Dawn and Ferrous Shadow using the simulator's full-player-HP
  boundary.
- Add Slingshot using immediate action resolution, plus eight weapons whose
  weak-point, enemy-type, defeat, or healing states are unreachable or have no
  gameplay state in the current runtime.
- Centralize intentionally inactive metadata/refinement behavior without
  fabricating triggers.

Out of scope for this pass:

- Thrilling Tales of Dragon Slayers, incoming switch callbacks, player current
  HP and healing, enemy defeat/type, weak points, projectile travel time,
  shared runtime changes, RL, and generated docs.

### Phase 1: Full-HP Conditional Weapons - Done

Target files:

- `src/java/model/weapon/HarbingerOfDawn.java` (new)
- `src/java/model/weapon/FerrousShadow.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Harbinger metadata and R1/R5 full-HP CRIT values pass at negative and
  positive times.
- Ferrous metadata, R1/R5 refinement, and absence of fabricated Charged bonus
  pass; refinement 0/6 rejection and all local gates pass.

Acceptance criteria:

- Harbinger exposes exact Lv. 90 metadata and applies R1-R5 Vigorous CRIT Rate
  continuously because player HP remains full in the supported runtime.
- Ferrous exposes exact Lv. 90 metadata and no Unbending bonus because player
  HP never falls below any R1-R5 threshold.
- Both expose R5 defaults, selected refinement, and reject refinement 0/6.

Test cases to add or update:

- Normal: R5 metadata and Vigorous CRIT Rate.
- Boundary: R1/R5 values and arbitrary-time behavior.
- Abnormal: refinement 0/6; Ferrous must not synthesize Charged bonus.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

### Phase 2: Immediate-Impact and Boundary-Inactive Weapons - Done

Target files:

- `src/java/model/weapon/BoundaryInactiveWeapon.java` (new)
- `src/java/model/weapon/TravelersHandySword.java` (new)
- `src/java/model/weapon/WhiteIronGreatsword.java` (new)
- `src/java/model/weapon/BlackTassel.java` (new)
- `src/java/model/weapon/Messenger.java` (new)
- `src/java/model/weapon/RecurveBow.java` (new)
- `src/java/model/weapon/SharpshootersOath.java` (new)
- `src/java/model/weapon/OtherworldlyStory.java` (new)
- `src/java/model/weapon/TwinNephrite.java` (new)
- `src/java/model/weapon/Slingshot.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Completion evidence:

- Branch-isolated source commit `d304855` adds the shared inactive boundary,
  eight metadata-complete weapons, and Slingshot's immediate-impact bonus.
- Table-driven R1/R5 metadata, no-op boundary, Slingshot category, and invalid
  refinement regressions pass together with reaction regression, build,
  Javadoc, and preflight gates.

Acceptance criteria:

- All nine classes expose exact Lv. 90 metadata, R5 defaults, selected
  refinement, and refinement validation.
- Slingshot grants only its R1-R5 close-impact Normal/Charged bonus under the
  simulator's immediate-resolution boundary.
- The other eight classes share an explicit no-op boundary and do not infer a
  slime, weak point, defeat, player HP change, healing, or movement effect.

Test cases to add or update:

- Normal: table-driven metadata for all nine and Slingshot NA/CA bonuses.
- Boundary: R1/R5 behavior and arbitrary negative/positive times.
- Abnormal: refinement 0/6 for every class, no Skill/Burst Slingshot bonus,
  and no stat mutation from the inactive family.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

## Implementation Order: Core Artifact Set Expansion Campaign

Status: Complete. This campaign adds four complete, commonly used artifact
sets through existing initialization, switch, reaction, and damage hooks; RL
and generated documentation remain excluded.

Scope:

- Add Gladiator's Finale and Golden Troupe with owner/field-aware static
  contracts.
- Add Gilded Dreams and Pale Flame with reaction/composition and Skill-hit
  stack windows.

Out of scope for this pass:

- Artifact piece-count modeling, healing/current HP, shields, Nightsoul,
  enemy defeat, optimizer defaults, RL, and generated docs.

### Phase 1: Weapon-Type and Field-State Sets - Done

Target files:

- `src/java/model/artifact/GladiatorsFinale.java` (new)
- `src/java/model/artifact/GoldenTroupe.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Both support empty and supplied main/sub-stat containers without mutating
  unrelated data and expose canonical names.
- Gladiator grants ATK +18% and Normal Attack DMG +35% only when the owner uses
  a Sword, Claymore, or Polearm.
- Golden Troupe grants Skill DMG +45% generally and an additional +25% while
  off-field and for the first two seconds after a standard switch-in.

Test cases to add or update:

- Normal: metadata/static values, all eligible Gladiator weapon types, initial
  active/off-field Golden states, and standard switches.
- Boundary: Golden exact two-second grace and repeated switch cycles.
- Abnormal: catalyst/bow/no weapon exclusion, duplicate/cross-simulator init,
  null stats, and direct setter boundary.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Focused regressions cover supplied-stat preservation, all Gladiator weapon
  gates including null, Golden Troupe initial field states, standard and direct
  switches, repeated grace windows, exact two-second expiry, and rebinding.
- `./gradlew ReactionRegressionTest`, `./gradlew build`, `./gradlew javadoc`,
  and `python scripts/preflight.py` passed on 2026-08-02.

### Phase 2: Reaction Composition and Skill-Hit Stacks - Done

Target files:

- `src/java/model/artifact/GildedDreams.java` (new)
- `src/java/model/artifact/PaleFlame.java` (new)
- `src/java/sample/ReactionRegressionTest.java`

Acceptance criteria:

- Gilded grants EM +80 and, once per eight seconds after an owner reaction,
  snapshots up to three allies as ATK +14% per same-element ally and EM +50 per
  different-element ally for eight seconds, including off-field triggers.
- Gilded does not affect an ordinary triggering reaction; Hyperbloom retains
  its documented immediate seed exception.
- Pale Flame grants Physical DMG +25%; eligible Skill hits add up to two shared
  seven-second ATK +9% stacks at 0.3-second CT, and two stacks add another 25%
  Physical DMG.

Test cases to add or update:

- Normal: composition matrices, off-field reaction, one/two Pale stacks, and
  two-piece values.
- Boundary: 0.3/7/8-second CT and expiry, composition snapshot, and Gilded
  triggering-reaction ordering.
- Abnormal: wrong trigger, NONE reaction, non-Skill/zero hit, duplicate init,
  and independent artifact instances.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew build`
- `./gradlew javadoc`
- `python scripts/preflight.py`

Completion evidence:

- Focused regressions cover Gilded Dreams static stats, off-field owner
  reactions, same/different composition snapshots, invalid triggers, exact
  eight-second resnapshot, and binding; Pale Flame covers trigger rejection,
  0.3-second CT, one/two stacks, exact seven-second expiry, supplied stats,
  wrong callbacks, and independent instances.
- `./gradlew ReactionRegressionTest`, `./gradlew build`, `./gradlew javadoc`,
  and `python scripts/preflight.py` passed on 2026-08-02.
