# AGENTS.md

## Scope
- This file applies to `src/model/entity/`.

## Directory role
- This package defines the core abstract entities that the rest of the simulator builds on.

## Java files in this directory
- `ArtifactSet.java`: base class for artifact sets, including passive stat application and event hooks.
- `Character.java`: abstract playable-character base with stat assembly, buffs, snapshots, energy tracking, cooldown state, action hooks, and team-buff extension points.
- `Enemy.java`: target model containing level, resistances, and live elemental aura state.
- `Weapon.java`: base class for weapons with passive stat hooks, event hooks, team-buff hooks, and NA-energy category information.

## Coupling and dependencies
- `Character` depends on `model.stats.StatsContainer`, `model.type.StatType`, `model.entity.Weapon`, `model.entity.ArtifactSet`, and `mechanics.buff.Buff`.
- `Enemy` is consumed heavily by `simulation.CombatSimulator` and `mechanics.formula.DamageCalculator`.
- `Weapon` and `ArtifactSet` are called by `Character` during stat assembly and by `simulation.CombatSimulator` or `mechanics.formula.DamageCalculator` during event handling.
- Concrete character, weapon, and artifact packages all extend these types.

## Agent guidance
- Changes here are high-impact. Audit all subclasses and simulator call sites before modifying shared hooks or state fields.
- Preserve the distinction between structural stats, effective stats, and snapshots in `Character`.
- New shared hooks should only be added if an existing hook cannot model the needed behavior cleanly.
