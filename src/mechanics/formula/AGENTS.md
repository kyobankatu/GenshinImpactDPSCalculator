# AGENTS.md

## Scope
- This file applies to `src/mechanics/formula/`.

## Directory role
- This package contains the damage math used by the simulator.
- It is the single most sensitive package for DPS output changes.

## Java files in this directory
- `DamageCalculator.java`: strategy-selecting facade that resolves final damage for a single `AttackAction` and fires damage-trigger hooks after calculation.
- `DamageStrategy.java`: internal interface for damage formula implementations.
- `StandardDamageStrategy.java`: standard Genshin damage formula path.
- `LunarDamageStrategy.java`: custom Lunar damage formula path.
- `ResistanceCalculator.java`: shared resistance multiplier helper.

## Coupling and dependencies
- `DamageCalculator` depends on `model.stats.StatsContainer`, `model.type.StatType`, `model.entity.Character`, `model.entity.Enemy`, `mechanics.buff.Buff`, `simulation.action.AttackAction`, and `simulation.CombatSimulator`.
- Standard damage resolution and Lunar damage resolution are split into focused strategy classes.
- Weapon and artifact damage-trigger capability interfaces are fired here after final damage is computed.
- `simulation.CombatSimulator` depends on this class during every hit resolution.

## Agent guidance
- Treat this file as high-risk. Small formula changes can affect every simulation, optimizer result, and RL reward.
- When editing, verify which path you are touching: standard damage, Lunar damage, defense, resistance, or debug formula output.
- Do not move effect-trigger hooks casually. Weapon and artifact stacking behavior depends on their current placement after final damage computation.
- Keep formula selection in the facade and formula details in the strategy classes unless there is a clear reason to change that split.
