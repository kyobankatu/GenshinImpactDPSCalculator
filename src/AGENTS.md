# AGENTS.md

## Scope
- This file applies to the entire `src/` tree unless a deeper `AGENTS.md` overrides it.

## Directory role
- `src/` is the main Java source root for the simulator.
- The codebase is split into five main areas: `mechanics`, `model`, `sample`, `simulation`, and `visualization`.
- There are no direct Java files in `src/`; all implementation lives in subdirectories.

## Java files in this directory
- None. Read the nearest child package for implementation responsibilities.

## Coupling and dependencies
- `simulation` is the public runtime center, with narrow collaborators under `simulation.runtime` owning most sequencing, switch, buff, event, damage-report, Moonsign, and reaction-state policies.
- `model` defines characters, weapons, artifacts, stats, enums, and enemy state that the simulator consumes.
- `mechanics` contains formulas, buffs, reactions, energy logic, optimization, analysis, data loading, and RL support.
- `sample` contains executable entry points that assemble parties, run optimizers, drive rotations, and emit reports.
- `visualization` consumes simulator logs and stat snapshots to build HTML output.
- Runtime identity should use `model.type.CharacterId`; display names are boundary data for scripts, logs, and reports.

## Agent guidance
- Start with the narrowest package that contains the files you need to edit.
- For behavior changes, trace the flow through `sample` entry point -> `simulation.CombatSimulator` -> affected `model` and `mechanics` classes.
- Do not infer gameplay rules from generated docs under `docs/`; use the Java source in `src/`.
- Keep package boundaries intact. This codebase relies on explicit package responsibilities more than shared service abstractions.
- When adding extension behavior, prefer focused capability interfaces over widening abstract base classes with optional no-op hooks.
