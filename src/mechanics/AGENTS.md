# AGENTS.md

## Scope
- This file applies to `src/mechanics/` and its subpackages unless a deeper file overrides it.

## Directory role
- `mechanics` contains the rule systems layered on top of the core simulator.
- This is where combat math, buffs, reaction resolution, energy flow, optimization loops, data loading, analysis, and RL integration live.
- There are no direct Java files in this directory.

## Java files in this directory
- None. Read the relevant child package before editing a mechanic.

## Coupling and dependencies
- `mechanics.*` depends heavily on `simulation.CombatSimulator`, `simulation.action.AttackAction`, and `model.*`.
- `mechanics.formula.DamageCalculator` delegates standard and Lunar math to focused `DamageStrategy` implementations and notifies damage-trigger capability interfaces after damage is computed.
- `mechanics.optimization` repeatedly creates simulators and runs full rotations, so small behavior changes in `simulation` or `model.character` can change optimizer output.
- `mechanics.rl` is coupled to `sample.RunRL`, `simulation.CombatSimulator`, and `mechanics.optimization.ProfileLoader` and `RotationSearcher`.
- Logic-bearing identity should remain typed (`CharacterId`, `BuffId`, `ReactionResult.Kind` or `LunarType`) inside mechanics; strings should be treated as file-format or display-boundary data.

## Agent guidance
- When changing a mechanic, check whether it affects damage, reactions, energy, buffs, optimizer assumptions, and report output.
- Prefer editing the narrowest mechanic package instead of introducing new cross-cutting utilities here.
- If a change touches Lunar behavior, inspect `mechanics.formula`, `mechanics.reaction`, `simulation.CombatSimulator`, and the custom Lunar characters together.
- Keep boundary translation explicit. Profile text, sample action labels, and report labels should not become new control-flow keys inside core mechanics.
