# AGENTS.md

## Scope
- This file applies to `src/mechanics/reaction/`.

## Directory role
- This package models elemental reaction outcomes and the data object used to transport them through the simulator.

## Java files in this directory
- `ReactionCalculator.java`: computes amplifying and transformative reactions from trigger element, aura element, EM, level, and optional reaction bonus.
- `ReactionEffectScheduler.java`: schedules delayed or follow-up reaction effects that should not live in formula code.
- `ReactionResult.java`: typed result object describing reaction type, kind, Lunar type, related element, multiplier or damage, and presentation label.

## Coupling and dependencies
- `ReactionCalculator` depends on `model.type.Element`.
- `simulation.CombatSimulator` uses `ReactionCalculator` and then forwards `ReactionResult` to reaction listeners, artifact hooks, resonance logic, and RL or weapon logic that listens for reactions.
- `model.artifact.ViridescentVenerer`, `model.artifact.NightOfTheSkysUnveiling`, `mechanics.element.ResonanceManager`, and `model.weapon.SunnyMorningSleepIn` should depend on `ReactionResult.Kind`, `LunarType`, related element, or helper methods before falling back to labels.

## Agent guidance
- Reaction kind and Lunar metadata are the behavioral API here. Reaction names are presentation labels and legacy bridges only.
- Keep the boundary clear between reaction detection and reaction aftermath. Transformative damage values are computed here, but simulator aura updates and listener dispatch live elsewhere.
- If you add a new reaction type, update `ReactionResult.Kind` or `LunarType` first, then audit artifact, weapon, resonance, RL, and report consumers.
