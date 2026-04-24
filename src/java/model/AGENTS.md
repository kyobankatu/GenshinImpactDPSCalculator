# AGENTS.md

## Scope
- This file applies to `src/java/model/` and its subpackages unless a deeper file overrides it.

## Directory role
- `model` contains the domain objects that describe characters, weapons, artifact sets, stats, enums, standards, and enemy state.
- There are no direct Java files in this directory.

## Java files in this directory
- None. Read the relevant child package for actual implementations.

## Coupling and dependencies
- `model.entity` provides the main abstract bases plus focused capability interfaces used by runtime dispatch.
- `model.character`, `model.weapon`, and `model.artifact` are the concrete game-content layers built on those bases.
- `model.stats.StatsContainer`, `model.type.StatType`, and `model.type.CharacterId` are foundational and widely referenced across the entire repo.
- `mechanics.formula`, `simulation.CombatSimulator`, and the sample entry points all consume these models directly.

## Agent guidance
- When changing a shared model class, audit downstream packages before editing.
- Do not add content-specific logic to `model.type` or `model.stats`.
- Prefer focused capability interfaces in `model.entity` over adding optional no-op hooks to abstract base classes.
- Keep display names for presentation and adapters; runtime-owned identity should use typed identifiers.
