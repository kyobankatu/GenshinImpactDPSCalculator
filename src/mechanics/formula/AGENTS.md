# AGENTS.md

## Scope
- This file applies to `src/mechanics/formula/`.

## Directory role
- This package contains the damage math used by the simulator.
- It is the single most sensitive package for DPS output changes.

## Java files in this directory
- `DamageCalculator.java`: resolves final damage for a single `AttackAction`, including standard and custom Lunar paths, buff application, crit, defense, resistance, and artifact or weapon hit hooks.

## Coupling and dependencies
- `DamageCalculator` depends on `model.stats.StatsContainer`, `model.type.StatType`, `model.entity.Character`, `model.entity.Enemy`, `mechanics.buff.Buff`, `simulation.action.AttackAction`, and `simulation.CombatSimulator`.
- Standard damage resolution and Lunar damage resolution are both implemented here.
- Weapon and artifact `onDamage` hooks are fired here after final damage is computed.
- `simulation.CombatSimulator` depends on this class during every hit resolution.

## Agent guidance
- Treat this file as high-risk. Small formula changes can affect every simulation, optimizer result, and RL reward.
- When editing, verify which path you are touching: standard damage, Lunar damage, defense, resistance, or debug formula output.
- Do not move effect-trigger hooks casually. Weapon and artifact stacking behavior depends on their current placement after final damage computation.
