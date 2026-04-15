# Refactoring Tasks

## Goal

This document resets the refactoring plan based on the current codebase state.

Completed work such as:

- runtime collaborator extraction
- typed action dispatch
- reaction metadata typing
- damage strategy split
- weapon / character capability narrowing
- energy distribution refactoring
- talent-data dependency injection

is treated as done and is no longer listed here.

The new priorities are:

1. Remove remaining broad hook surfaces that still violate interface segregation
2. Replace display-name based identity in runtime decisions with typed identifiers
3. Continue slimming orchestration logic inside `CombatSimulator`
4. Eliminate remaining fallback design paths that rely on display strings

## Constraints

- Keep changes local and incremental
- Preserve current package boundaries unless there is a clear benefit
- Avoid mechanic rewrites during structural cleanup
- Validate each stage with the smallest relevant sample simulation
- Do not edit generated files under `docs/`
- RL-related cleanup is explicitly out of scope for now

## Phase 0: Baseline Refresh

### Objective

Record the current post-refactor baseline before changing the next structural layer.

### Task 0.1

Document the current execution baseline for:

- `./gradlew build`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty`
- `./gradlew FlinsParty2`

Record:

- total rotation damage
- DPS
- known warning logs
- whether HTML report generation succeeds

### Task 0.2

Write down which scenario is the primary smoke test for each remaining subsystem:

- artifact-triggered behavior
- party identity / switching
- buff replacement and no-stack handling
- combat sequencing

### Phase 0 Notes (captured 2026-04-15 JST)

#### Task 0.1 Baseline Results

| Command | Result | Total Rotation Damage | DPS | Known warning logs | HTML report |
| --- | --- | ---: | ---: | --- | --- |
| `./gradlew build` | Success | N/A | N/A | None observed | N/A |
| `./gradlew RaidenParty` | Success | 1,693,561 | 80,646 | No `WARNING:` lines observed in the final run | Generated: `output/simulation_report.html` |
| `./gradlew FlinsParty` | Success | 22,463,116 | 196,356 | Repeated insufficient-energy warnings for `Flins` and `Sucrose` during calibration/final execution, including `Flins burst fired with insufficient energy (30/30)` | Generated: `output/simulation_report.html` |
| `./gradlew FlinsParty2` | Success | 27,063,409 | 230,916 | Repeated insufficient-energy warnings for `Flins`, `Sucrose`, and `Columbina`, including `Flins burst fired with insufficient energy (30/30)` | Generated: `output/simulation_report.html` |

Notes:

- The first `build` run downloaded Gradle `9.3.1`; subsequent runs completed against the cached distribution.
- The latest generated HTML report file exists at `output/simulation_report.html` after the baseline run set.

#### Task 0.2 Primary Smoke Tests

- artifact-triggered behavior: `./gradlew FlinsParty`
  - This scenario exercises the broadest remaining artifact hook surface in one run: reaction-triggered (`NightOfTheSkysUnveiling`), damage-triggered (`SilkenMoonsSerenade`), and switch-aware (`AubadeOfMorningstarAndMoon`) behavior.
- party identity / switching: `./gradlew FlinsParty`
  - It repeatedly cycles `Ineffa -> Columbina -> Sucrose -> Flins` and back, so name-based routing and switch state are stressed more than in the shorter Raiden sample.
- buff replacement and no-stack handling: `./gradlew FlinsParty`
  - It repeatedly refreshes/replaces stateful buffs tied to switch and Moonsign flow, which makes it the best regression check for buff replacement semantics.
- combat sequencing: `./gradlew RaidenParty`
  - It is the smallest deterministic sample that still covers burst entry, normal/charged sequencing, overlapping triggered attacks, and timing-sensitive follow-up ordering.

## Phase 1: Narrow `ArtifactSet`

### Objective

Remove the broad no-op hook surface from `ArtifactSet` just as was already done for `Weapon` and parts of `Character`.

### Task 1.1

Identify all artifact behaviors currently using optional hooks:

- reaction-triggered
- damage-triggered
- switch-in
- switch-out
- burst-triggered

### Task 1.2

Introduce focused artifact capability interfaces.

Candidate interfaces:

- `ReactionAwareArtifact`
- `DamageTriggeredArtifactEffect`
- `SwitchAwareArtifact`
- `BurstTriggeredArtifactEffect`

### Task 1.3

Update runtime code to depend on these interfaces instead of invoking broad hooks on every artifact.

Likely touchpoints:

- reaction dispatch
- damage resolution
- switch management
- burst execution path

### Task 1.4

Reduce `ArtifactSet` to:

- static stats
- passive stat contribution
- display metadata

Target outcome: no more no-op inheritance for artifact behaviors.

### Phase 1 Notes (completed 2026-04-15 JST)

- Task 1.1: identified current optional-hook artifact behaviors
  - reaction-triggered: `NightOfTheSkysUnveiling`, `ViridescentVenerer`
  - damage-triggered: `SilkenMoonsSerenade`
  - switch-triggered: `AubadeOfMorningstarAndMoon`
  - burst-triggered: `NoblesseOblige`
- Task 1.2: introduced focused artifact capability interfaces in `src/model/entity/`
  - `ReactionAwareArtifact`
  - `DamageTriggeredArtifactEffect`
  - `SwitchAwareArtifact`
  - `BurstTriggeredArtifactEffect`
- Task 1.3: updated runtime call sites to depend on focused interfaces instead of broad `ArtifactSet` hooks
  - reaction dispatch: `simulation/runtime/SimulationEventDispatcher.java`
  - damage resolution: `simulation/runtime/CombatActionResolver.java`, `mechanics/formula/DamageCalculator.java`
  - switch management: `simulation/runtime/SwitchManager.java`
  - burst execution path: `model/character/Bennett.java`
- Task 1.4: reduced `ArtifactSet` to fixed stats, passive stat application, and display metadata only
  - removed no-op event hook methods from the base class
  - concrete artifact sets now opt into behavior explicitly via capability interfaces

Verification:

- `./gradlew build`
- `./gradlew FlinsParty`
- `FlinsParty` baseline remained `22,463,116` total damage / `196,356` DPS and still generated `output/simulation_report.html`

## Phase 2: Replace Name-Based Runtime Identity

### Objective

Stop using display names as the internal identity key in simulation logic.

### Task 2.1

Audit places where `Character.getName()` is used for lookup, routing, or aggregation rather than display.

Priority areas:

- `Party`
- `CombatSimulator`
- optimizer pipeline
- damage recording
- event routing

### Task 2.2

Promote `CharacterId` to the primary internal identifier for runtime-owned party state.

Possible changes:

- `Party` stores members by `CharacterId`
- simulator APIs accept `CharacterId` internally
- display names remain only for logs and reports

### Task 2.3

Add adapters only at human-facing boundaries.

Examples:

- sample scripts
- profile loading
- report generation

### Task 2.4

Update damage and buff bookkeeping to use typed identity where the value is used for logic rather than presentation.

Target outcome: internal control flow no longer depends on mutable display strings.

## Phase 3: Slim `CombatSimulator` Further

### Objective

Reduce remaining policy logic in `CombatSimulator` so it becomes a coordinator again.

### Task 3.1

Extract attack sequencing policy from `CombatSimulator.performAction(AttackAction)`.

Candidate collaborator:

- `simulation.runtime.ActionTimelineExecutor`

Responsibilities:

- post-hit event dispatch ordering
- animation-duration handling
- attack-speed duration scaling
- moonsign follow-up blessing timing

### Task 3.2

Move remaining reaction-state convenience access behind a dedicated subsystem boundary.

Candidate:

- `simulation.runtime.ReactionStateController`

### Task 3.3

Review public methods on `CombatSimulator` and separate:

- true simulator API
- collaborator accessors
- temporary convenience wrappers

Target outcome: fewer reasons to edit `CombatSimulator` when adding a mechanic.

## Phase 4: Remove Remaining String-Based Fallback Paths

### Objective

Eliminate the remaining logic branches that still special-case strings even after the main typed refactors.

### Task 4.1

Reduce or eliminate `BuffId.CUSTOM` in logic-bearing code paths.

Priority:

- no-stack replacement
- buff existence checks
- buff removal policies

### Task 4.2

Distinguish clearly between:

- logic identity
- display label

for buffs and other runtime objects.

Candidate additions:

- stronger `BuffId` coverage
- separate display-name field usage rules

### Task 4.3

Audit reaction and combat logging paths to ensure string labels are presentation-only.

Target outcome: string values remain for UI/reporting only, not for control flow.

## Phase 5: Clarify Boundary Adapters

### Objective

Make boundary translation explicit so typed internals are not gradually polluted by external formats.

### Task 5.1

Identify boundary inputs that still translate external formats directly inside core classes.

Examples:

- profile loading
- sample action wiring
- report labels

### Task 5.2

Move parsing / translation into adapter classes where appropriate.

Candidate areas:

- profile-to-action translation
- report DTO generation
- simulator bootstrap wiring

### Task 5.3

Document which packages are allowed to depend on:

- display names
- file-format strings
- report labels

Target outcome: clearer dependency direction and less leakage of boundary concerns into combat logic.

## Recommended Execution Order

1. Phase 0
2. Phase 1
3. Phase 4
4. Phase 2
5. Phase 3
6. Phase 5

This order removes the broadest interface problems first, then cleans up identity and orchestration.

## Verification Per Phase

- Run `./gradlew build`
- Run at least one affected sample simulation
- If artifact behavior changed, verify the most relevant party sample
- If simulator sequencing changed, verify timing-sensitive samples
- If identity routing changed, verify switching, buff targeting, and report output

## Out of Scope for This Plan

- RL server cleanup
- RL reward refactoring
- network protocol redesign
- generated documentation updates
- pure balance tuning

## Deliverables

- narrower artifact extension contracts
- typed runtime identity for party members
- slimmer `CombatSimulator`
- reduced string fallback logic
- explicit boundary adapters
