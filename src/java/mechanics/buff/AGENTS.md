# AGENTS.md

## Scope
- This file applies to `src/java/mechanics/buff/`.

## Directory role
- This package defines the generic buff abstractions used across characters, weapons, artifacts, and the simulator.
- Most temporary or conditional stat effects in the project are built on these classes.

## Java files in this directory
- `Buff.java`: abstract base for time-windowed stat modifiers with typed `BuffId` identity, display-label accessors, logical de-duplication keys, and optional character or element targeting filters.
- `BuffId.java`: typed identifiers for buffs whose presence, replacement, or removal is used by simulator logic.
- `SimpleBuff.java`: lambda-backed buff for straightforward stat additions without a dedicated subclass.
- `ActiveCharacterBuff.java`: buff variant that only applies while a specific character is on field.

## Coupling and dependencies
- `Buff` depends on `model.stats.StatsContainer`, `model.type.CharacterId`, and `model.type.Element`.
- `ActiveCharacterBuff` additionally depends on `simulation.CombatSimulator` and `model.entity.Character` to test on-field ownership.
- `simulation.runtime.BuffManager` aggregates team buffs, field buffs, weapon team buffs, and character-provided buffs using these types.
- Many artifact, weapon, and character classes create `SimpleBuff` or anonymous `Buff` instances instead of new classes.

## Agent guidance
- Before adding a new buff subtype, check whether `SimpleBuff` or an anonymous `Buff` is already sufficient.
- Be careful with refresh versus stack behavior. Logic-bearing replacement through no-stack paths requires a typed `BuffId`; do not depend on display names.
- If a buff needs simulation context such as active character, Moonsign, or time-based state, verify whether `ActiveCharacterBuff` or a stateful owner object is the right fit.
- Use `getId()` for control flow, `getLogicKey()` for bookkeeping/de-duplication, and `getDisplayName()` or `getName()` only for logs and reports.
