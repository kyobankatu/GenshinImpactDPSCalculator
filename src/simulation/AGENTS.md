# AGENTS.md

## Scope
- This file applies to `src/simulation/` and its subpackages unless a deeper file overrides it.

## Directory role
- `simulation` contains the runtime engine, observer interfaces, party state, action payloads, and time-event contracts.

## Java files in this directory
- `ActionListener.java`: observer interface for executed attack actions.
- `CombatSimulator.java`: central runtime engine that manages time, swaps, actions, reactions, buffs, event queue, damage accounting, and custom Lunar state.
- `ParticleListener.java`: observer interface for particle-generation events.
- `Party.java`: current-party container that tracks members and the active character.
- Subpackages `action` and `event` hold action payloads and timer-event implementations.

## Coupling and dependencies
- `CombatSimulator` depends on nearly every major subsystem: `model.entity`, `mechanics.buff`, `mechanics.formula`, `mechanics.reaction`, `mechanics.element`, `mechanics.energy`, `visualization`, and event classes.
- Character, weapon, artifact, optimizer, RL, and visualization code all depend back on `CombatSimulator`.
- The observer interfaces are implemented or consumed by reaction systems, weapon passives, RL code, and character logic.

## Agent guidance
- Treat `CombatSimulator` as the behavioral center of the repo. Edit it only after checking whether the change can live in a narrower class.
- If you touch simulator sequencing, verify swaps, event timing, reactions, buffs, and logging together.
- Keep observer interfaces stable when possible. Multiple subsystems use them as extension points.
