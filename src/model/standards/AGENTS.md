# AGENTS.md

## Scope
- This file applies to `src/model/standards/`.

## Directory role
- This package stores benchmark constants and assumptions used by the optimizer and simulation standards.

## Java files in this directory
- `KQMSConstants.java`: central constants for average substat roll values, standard levels and resistances, and artifact liquid-roll budgeting.

## Coupling and dependencies
- `mechanics.optimization.ArtifactOptimizer` depends directly on these constants for roll values and budgets.
- Standard enemy and level assumptions documented here should match the expectations in sample setups and report interpretation, even if some sample classes override enemy level locally.

## Agent guidance
- Treat this file as shared baseline configuration, not a place for feature logic.
- If you change a constant, assume optimizer outputs and benchmark comparisons will shift globally.
