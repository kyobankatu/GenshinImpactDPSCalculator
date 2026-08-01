# AGENTS.md

## Scope
- This file applies to `src/java/simulation/` and its subpackages unless a deeper file overrides it.

## Directory role
- `simulation` contains the runtime engine, observer interfaces, party state, action payloads, and time-event contracts.
- `CombatSimulator` remains the public façade, while `simulation.runtime` owns extracted policies for action sequencing, action resolution, switch handling, buff aggregation, event dispatch, damage reporting, Moonsign state, and reaction-state access.

## Java files in this directory
- `ActionListener.java`: observer interface for executed attack actions.
- `CombatLogSink.java`: logging abstraction used by runtime code to write combat events without depending directly on visualization internals.
- `CombatSimulator.java`: public runtime façade that coordinates collaborators and exposes simulator APIs for samples, mechanics, characters, and reports.
- `DamageListener.java`: observer interface for resolved direct damage from timeline and no-time-advance attacks.
- `DamageTracker.java`: damage-accounting interface implemented by runtime reporting.
- `ParticleListener.java`: observer interface for particle-generation events.
- `Party.java`: current-party container that tracks members by `CharacterId`, preserves a name-to-id adapter for boundary calls, and maintains the active character.
- `SimulationEventBus.java`: event-dispatch abstraction for action, particle, and reaction listener registration.
- `SimulatorSnapshot.java`: immutable-ish snapshot of simulator runtime state used for rollback/restore flows, including reaction-state counters and core state.
- Subpackages `action`, `event`, and `runtime` hold action payloads, timer-event implementations, and extracted runtime collaborators.

## Coupling and dependencies
- `CombatSimulator` depends on nearly every major subsystem through a façade role: `model.entity`, `mechanics.buff`, `mechanics.formula`, `mechanics.reaction`, `mechanics.element`, `mechanics.energy`, `visualization`, runtime collaborators, and event classes.
- Character, weapon, artifact, optimizer, RL, and visualization code all depend back on `CombatSimulator`.
- Party definitions may inject fixed random streams when optimizer candidates and final sample runs require common random scenarios; generic content constructors remain stochastic.
- The observer interfaces are implemented or consumed by reaction systems, weapon passives, RL code, and character logic.
- `SimulatorSnapshot` must stay aligned with `CombatSimulator`, `Party`, `Enemy`, and `simulation.runtime.ReactionState` whenever new runtime state becomes rollback-relevant.
- `ReactionState.DendroCoreState` stores pre-resistance damage so delayed core
  impacts can resolve current enemy-facing reduction after snapshot restore.
- Logic-bearing party lookup and damage attribution should use `CharacterId`; string overloads are compatibility or boundary adapters.

## Agent guidance
- Treat `CombatSimulator` as the public coordination center, not the default place for new policy. Edit it only after checking whether the change belongs in a narrower runtime collaborator.
- If you touch simulator sequencing, verify swaps, event timing, reactions, buffs, and logging together.
- Keep observer interfaces stable when possible. Multiple subsystems use them as extension points.
- Preserve typed internal routes for character identity and action dispatch; keep display names for logs, reports, and compatibility wrappers.
- When adding simulator state that can change during action trialing or RL rollout, verify snapshot/restore coverage.
