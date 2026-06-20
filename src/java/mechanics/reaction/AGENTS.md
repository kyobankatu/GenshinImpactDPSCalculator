# AGENTS.md

## Scope
- This file applies to `src/java/mechanics/reaction/`.

## Directory role
- This package models elemental reaction outcomes and the data object used to transport them through the simulator.

## Java files in this directory
- `ReactionCalculator.java`: computes typed reaction outcomes for amplifying, transformative, additive, stateful, Bloom-family, Quicken-family, Shatter, Crystallize, Burning, and Lunar-converted reactions.
- `ReactionEffectScheduler.java`: schedules delayed or follow-up reaction effects that should not live in formula code.
- `ReactionResult.java`: typed result object describing reaction type, kind, Lunar type, related element, damage element, formula behavior, multiplier or damage, stateful flags, and presentation label.

## Coupling and dependencies
- `ReactionCalculator` depends on `model.type.Element`.
- `simulation.runtime.CombatActionResolver` uses `ReactionCalculator` and then forwards `ReactionResult` through simulator reaction listeners, artifact hooks, resonance logic, and RL or weapon logic that listens for reactions.
- `ReactionEffectScheduler` owns delayed Bloom core explosions, Burning ticks, Electro-Charged or Lunar-Charged tick effects, and Lunar-Crystallize Harmony-style follow-ups.
- `model.artifact.ViridescentVenerer`, `model.artifact.NightOfTheSkysUnveiling`, `mechanics.element.ResonanceManager`, and `model.weapon.SunnyMorningSleepIn` should depend on `ReactionResult.Kind`, `LunarType`, related element, or helper methods before falling back to labels.

## Agent guidance
- Reaction kind and Lunar metadata are the behavioral API here. Reaction names are presentation labels and legacy bridges only.
- Keep the boundary clear between reaction detection and reaction aftermath. Transformative/additive base values and metadata are computed here, but aura updates, state mutation, scheduling, and listener dispatch live in runtime code.
- If you change reaction behavior, update or run `sample.ReactionRegressionTest` before relying on broad sample output.
- If you add a new reaction type, update `ReactionResult.Kind` or `LunarType` first, then audit artifact, weapon, resonance, RL, and report consumers.
