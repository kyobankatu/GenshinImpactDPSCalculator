# Refactoring Tasks

## Goal

This document outlines a staged refactoring plan for improving the project based on SOLID principles while preserving current simulator behavior.

The priorities are:

1. Reduce the responsibility concentration in `CombatSimulator`
2. Split the oversized `Character` base class into focused state holders
3. Replace string-based action dispatch with typed actions
4. Remove duplicated combat math and reaction branching
5. Narrow extension contracts for characters and weapons

## Constraints

- Keep changes local and incremental
- Preserve current package boundaries unless there is a clear benefit
- Avoid broad mechanic rewrites during structural refactoring
- Validate each stage with the smallest relevant sample simulation
- Do not edit generated files under `docs/`

## Phase 0: Safety Net

### Task 0.1

Document the current execution baseline for:

- `./gradlew build`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty`

Record any current warnings, known logging noise, and generated report outputs so regressions are easier to spot later.

### Task 0.2

Identify the smallest smoke scenarios for each subsystem:

- swap timing
- reaction handling
- buff application
- energy distribution
- RL entry path

These can remain sample-driven for now, but they should be written down before refactoring starts.

## Phase 1: Slim Down `CombatSimulator`

### Objective

Move orchestration details out of `CombatSimulator` so it becomes a coordinator instead of a god class.

### Task 1.1

Extract swap logic from `CombatSimulator.switchCharacter(...)` into a dedicated runtime collaborator.

Candidate: `simulation.runtime.SwitchManager`

Responsibilities:

- swap cooldown enforcement
- switch-out callbacks
- switch-in callbacks
- swap delay handling
- swap timeline logging

### Task 1.2

Extract action gating from `CombatSimulator.performAction(String, String)`.

Candidate: `simulation.runtime.ActionGateway`

Responsibilities:

- normalize action keys
- enforce skill cooldown
- enforce burst cooldown
- warn on insufficient energy
- invoke character and weapon action hooks

### Task 1.3

Extract event queue ownership from `CombatSimulator`.

Candidate: `simulation.runtime.SimulationClock` or `EventTimeline`

Responsibilities:

- own the timer priority queue
- register events
- advance time
- tick due events in chronological order

### Task 1.4

Move Moonsign and Thundercloud-related transient state behind a narrower subsystem boundary.

Candidate split:

- keep party-wide Moonsign derivation in `MoonsignManager`
- move EC and Thundercloud state into a dedicated reaction state holder

### Task 1.5

After the above extractions, reduce `CombatSimulator` to:

- party access
- enemy access
- top-level simulation coordination
- public extension hooks

Target outcome: easier reasoning about sequencing and lower risk when adding mechanics.

## Phase 2: Break Up `Character`

### Objective

Stop using `Character` as a catch-all holder for stats, buffs, energy, cooldowns, snapshots, and optimizer metadata.

### Task 2.1

Extract energy accounting into a focused component.

Candidate: `model.entity.state.EnergyState`

Responsibilities:

- current energy
- total energy gained
- particle vs flat energy tracking
- burst-window accounting
- reset behavior

### Task 2.2

Extract cooldown and charge tracking.

Candidate: `model.entity.state.CooldownState`

Responsibilities:

- last skill use
- last burst use
- skill cooldown
- burst cooldown
- charge restoration schedule

### Task 2.3

Extract snapshot storage and snapshot creation policy.

Candidate: `model.entity.state.SnapshotState` or `SnapshotManager`

Responsibilities:

- store latest snapshot
- build snapshot from structural/effective stats
- apply extra snapshot buffs

### Task 2.4

Extract optimizer-only artifact roll metadata away from runtime character behavior.

Candidate: `mechanics.optimization.ArtifactRollProfile` or similar

This data does not belong in the combat entity abstraction.

### Task 2.5

Review `getEffectiveStats(...)` and `getStructuralStats(...)` and move stat assembly into a dedicated collaborator.

Candidate: `mechanics.formula.StatAssembler`

Responsibilities:

- merge base stats
- merge equipment stats
- apply passives
- apply self buffs
- keep recursion-safe structural stat path

Target outcome: `Character` becomes a domain object with explicit capabilities instead of a state dump.

## Phase 3: Replace String-Based Actions

### Objective

Eliminate fragile string dispatch such as `"skill"`, `"E"`, `"burst"`, and `"Q"`.

### Task 3.1

Introduce a typed action request model.

Candidate:

- `enum CharacterActionKey`
- `CharacterActionRequest`

Minimum set:

- NORMAL
- CHARGE
- SKILL
- BURST
- DASH
- PLUNGE

### Task 3.2

Change `Character.onAction(...)` and `Weapon.onAction(...)` to use the typed action model.

### Task 3.3

Add a compatibility adapter so sample scripts can be migrated incrementally.

Example:

- keep a temporary parser from legacy strings to `CharacterActionKey`
- mark string overloads as transitional

### Task 3.4

Migrate representative characters first:

- Bennett
- Xingqiu
- Xiangling
- RaidenShogun

These cover common action patterns and burst/skill timing.

Target outcome: safer extension and fewer cross-file edits when adding new action semantics.

## Phase 4: Centralize Combat Math and Reaction Policies

### Objective

Remove duplicated element-switch logic and special-case combat math from multiple classes.

### Task 4.1

Centralize resistance shred accumulation.

Candidate: `mechanics.formula.ResistanceCalculator`

Current duplication exists across:

- `DamageCalculator`
- `CombatActionResolver`

### Task 4.2

Separate standard and lunar damage paths behind strategy classes.

Candidate:

- `DamageStrategy`
- `StandardDamageStrategy`
- `LunarDamageStrategy`

### Task 4.3

Move Electro-Charged and Lunar-Charged tick creation out of `CombatActionResolver`.

Candidate: `mechanics.reaction.ReactionEffectScheduler`

Responsibilities:

- create reaction follow-up events
- manage EC/Lunar-Charged scheduling policy
- isolate reaction state transitions

### Task 4.4

Reduce string matching on reaction names where possible.

Examples to replace:

- `"Electro-Charged"`
- `"Lunar-Charged"`
- `"Swirl-..."`

Prefer typed reaction identifiers or richer `ReactionResult` metadata.

Target outcome: adding a new reaction variant or damage path should not require updating multiple unrelated classes.

## Phase 5: Narrow Extension Interfaces

### Objective

Replace broad base-class hook surfaces with focused capabilities.

### Task 5.1

Split weapon extension points into narrower interfaces.

Candidate interfaces:

- `ActionTriggeredWeaponEffect`
- `DamageTriggeredWeaponEffect`
- `SwitchAwareWeaponEffect`
- `TeamBuffProvider`

### Task 5.2

Do the same for character-side optional behaviors where it improves clarity.

Candidate interfaces:

- `ReactionAwareCharacter`
- `BurstStateProvider`
- `TeamBuffProvider`

### Task 5.3

Update simulator/runtime code to depend on those interfaces instead of probing every object for every hook.

Target outcome: less no-op inheritance and cleaner dependency direction.

## Phase 6: Reduce Static Utility Coupling

### Objective

Improve dependency inversion in utility-heavy systems.

### Task 6.1

Refactor `EnergyManager` away from purely static orchestration.

Candidate:

- instance-based `EnergyDistributor`
- injected access to active character and current stats

### Task 6.2

Review singleton/global access patterns such as `TalentDataManager`.

Clarify whether they should remain process-wide caches or move behind explicit dependencies for testability.

## Recommended Execution Order

1. Phase 0
2. Phase 1
3. Phase 4.1
4. Phase 2
5. Phase 3
6. Phase 4.2 to 4.4
7. Phase 5
8. Phase 6

This order reduces risk by first stabilizing the simulator boundary, then tackling shared models and extension contracts.

## Verification Per Phase

- Run `./gradlew build`
- Run at least one affected sample simulation
- If action sequencing changed, verify swap timing and report generation
- If energy changed, verify burst availability and energy analyzer output
- If reaction code changed, verify aura consumption and periodic reaction ticks
- If RL-facing state changed, verify `./gradlew RunRL`

## Out of Scope for Initial Refactor

- rewriting sample rotations for style only
- balance changes to multipliers or reaction formulas
- migration of generated documentation
- introducing a full new test framework before structural cleanup

## Deliverables

- smaller runtime collaborators under `src/simulation/runtime/`
- slimmer domain entities under `src/model/entity/`
- typed action dispatch
- centralized combat math utilities
- narrower extension interfaces
- updated verification notes after each phase
