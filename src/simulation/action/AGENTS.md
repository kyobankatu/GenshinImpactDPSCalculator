# AGENTS.md

## Scope
- This file applies to `src/simulation/action/`.

## Directory role
- This package contains the payload object representing one damage-dealing action in the simulator.

## Java files in this directory
- `AttackAction.java`: action descriptor carrying damage multiplier, element, scaling stat, bonus-stat context, action type, animation duration, snapshot flags, ICD settings, extra bonuses, defense ignore, and custom Lunar metadata.

## Coupling and dependencies
- `simulation.CombatSimulator` consumes `AttackAction` for every hit.
- `mechanics.formula.DamageCalculator` reads most of its fields during damage resolution.
- Concrete character classes construct these objects directly for normals, skills, bursts, summons, weapon procs, and custom Lunar actions.
- ICD tags and action-type flags feed into reaction logic and item passive triggers.

## Agent guidance
- This class is effectively shared schema. Field or default changes can ripple through the whole simulator.
- If you add metadata here, audit constructor overloads and all major consumers.
- Keep a clear distinction between action classification, elemental application settings, and custom per-hit overrides.
