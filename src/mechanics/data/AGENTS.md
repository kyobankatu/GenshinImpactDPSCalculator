# AGENTS.md

## Scope
- This file applies to `src/mechanics/data/`.

## Directory role
- This package loads character and talent configuration data from CSV files under `config/characters/`.
- It is the main bridge between data files and hardcoded character logic.

## Java files in this directory
- `TalentDataManager.java`: singleton CSV loader and lookup service for per-character base stats, talent multipliers, constellation settings, and other keyed values.

## Coupling and dependencies
- Most concrete character classes call `TalentDataManager.getInstance().get(...)` during construction or action execution.
- Missing or renamed CSV keys will usually fail at runtime through incorrect defaults rather than compilation errors.
- The loader walks `config/characters/` recursively and falls back to `config/talent_data.csv` if the main directory is unavailable.

## Agent guidance
- When changing character data keys or adding a new CSV, audit all call sites in `model.character`.
- Keep key naming stable. The project uses string lookups rather than typed config models.
- If a mechanic appears wrong, inspect both the Java call site and the CSV data before editing either.
