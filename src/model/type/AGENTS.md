# AGENTS.md

## Scope
- This file applies to `src/model/type/`.

## Directory role
- This package contains the enums that define shared identifiers and categories across the simulator.

## Java files in this directory
- `ActionType.java`: categorizes hits and abilities for damage bonuses, triggers, and CSV ability typing.
- `CharacterId.java`: stable typed identifiers for playable characters used by runtime party state and logic routing.
- `Element.java`: enumerates elements and maps each one to its associated damage-bonus stat key.
- `ICDTag.java`: identifies independent elemental application groups.
- `ICDType.java`: defines ICD rule families.
- `StatType.java`: master enumeration for every stat key used by the simulator, including custom Lunar stats.
- `WeaponType.java`: categorizes weapons for expected NA energy generation behavior.

## Coupling and dependencies
- `StatType` and `Element` are foundational across nearly every package.
- `simulation.Party`, `simulation.action.AttackAction`, `mechanics.element.ICDManager`, `mechanics.formula.DamageCalculator`, buffs, and all concrete content classes depend on these enums.
- Name and membership changes here can break CSV expectations, switch statements, or string-based assumptions throughout the repo.

## Agent guidance
- Treat enum names as stable API unless you are intentionally performing a coordinated repo-wide migration.
- If you add a new `StatType`, inspect `StatsContainer`, `DamageCalculator`, relevant content classes, and any reporting code that needs to display it.
- If you add a new character, add or verify its `CharacterId` and `fromName` mapping before depending on runtime lookup.
- Keep comments factual and implementation-neutral. The source files are the detail source.
