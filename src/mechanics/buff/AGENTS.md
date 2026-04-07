# AGENTS.md

## Scope
- This file applies to `src/mechanics/buff/`.

## Directory role
- This package defines the generic buff abstractions used across characters, weapons, artifacts, and the simulator.
- Most temporary or conditional stat effects in the project are built on these classes.

## Java files in this directory
- `Buff.java`: abstract base for time-windowed stat modifiers with optional character and element targeting filters.
- `SimpleBuff.java`: lambda-backed buff for straightforward stat additions without a dedicated subclass.
- `ActiveCharacterBuff.java`: buff variant that only applies while a specific character is on field.

## Coupling and dependencies
- `Buff` depends on `model.stats.StatsContainer` and `model.type.Element`.
- `ActiveCharacterBuff` additionally depends on `simulation.CombatSimulator` and `model.entity.Character` to test on-field ownership.
- `simulation.CombatSimulator` aggregates team buffs, field buffs, weapon team buffs, and character-provided buffs using these types.
- Many artifact, weapon, and character classes create `SimpleBuff` or anonymous `Buff` instances instead of new classes.

## Agent guidance
- Before adding a new buff subtype, check whether `SimpleBuff` or an anonymous `Buff` is already sufficient.
- Be careful with refresh versus stack behavior. Many callers rely on explicit `removeBuff` or `applyTeamBuffNoStack` instead of internal deduplication.
- If a buff needs simulation context such as active character, Moonsign, or time-based state, verify whether `ActiveCharacterBuff` or a stateful owner object is the right fit.
