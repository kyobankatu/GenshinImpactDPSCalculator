# AGENTS.md

## Scope
- This file applies to `src/mechanics/reaction/`.

## Directory role
- This package models elemental reaction outcomes and the data object used to transport them through the simulator.

## Java files in this directory
- `ReactionCalculator.java`: computes amplifying and transformative reactions from trigger element, aura element, EM, level, and optional reaction bonus.
- `ReactionResult.java`: compact result object describing reaction type, multiplier or damage, and reaction name.

## Coupling and dependencies
- `ReactionCalculator` depends on `model.type.Element`.
- `simulation.CombatSimulator` uses `ReactionCalculator` and then forwards `ReactionResult` to reaction listeners, artifact hooks, resonance logic, and RL or weapon logic that listens for reactions.
- `model.artifact.ViridescentVenerer`, `model.artifact.NightOfTheSkysUnveiling`, `mechanics.element.ResonanceManager`, and `model.weapon.SunnyMorningSleepIn` depend on reaction names and categories.

## Agent guidance
- Reaction names are behavioral API here. Changing string formats can break artifact, weapon, resonance, or RL logic that matches on names.
- Keep the boundary clear between reaction detection and reaction aftermath. Transformative damage values are computed here, but simulator aura updates and listener dispatch live elsewhere.
- If you add a new reaction type, audit all name-based consumers immediately.
