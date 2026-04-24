# AGENTS.md

## Scope
- This file applies to `src/java/model/stats/`.

## Directory role
- This package provides the generic stat container used across the whole project.

## Java files in this directory
- `StatsContainer.java`: mutable enum-keyed stat map with additive updates, overwrite support, merge support, and total ATK/HP/DEF convenience calculations.

## Coupling and dependencies
- Almost every package depends on `StatsContainer`.
- `model.entity.Character`, `model.entity.Weapon`, `model.entity.ArtifactSet`, and `mechanics.formula.DamageCalculator` all rely on its merge and total-stat semantics.
- Buff application modifies these containers in place.

## Agent guidance
- Changes here are global. A merge or total-stat bug will affect all damage, buffs, optimization, and reports.
- Keep the container generic. Gameplay rules belong in callers, not in this class.
- Be careful not to blur the difference between stored components and derived totals.
