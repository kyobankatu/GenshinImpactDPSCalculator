# AGENTS.md

## Scope
- This file applies to `src/visualization/`.

## Directory role
- This package records simulation output and renders it into interactive HTML reports.

## Java files in this directory
- `HtmlReportGenerator.java`: converts simulation records, party state, artifact rolls, and optional stat snapshots into a self-contained Chart.js-based HTML report.
- `SimulationRecord.java`: data holder for one logged event, including action, damage, reaction metadata, aura snapshot, and optional formula text.
- `VisualLogger.java`: singleton collector for `SimulationRecord` entries during a run.

## Coupling and dependencies
- `visualization` depends on `simulation.CombatSimulator`, `model.type.Element`, `model.type.StatType`, and optionally `mechanics.analysis.StatsSnapshot`.
- `simulation.CombatSimulator` writes timeline entries to `VisualLogger`.
- Sample entry points clear the logger and call `HtmlReportGenerator.generate(...)` after simulations.

## Agent guidance
- Keep this package downstream-only. It should reflect runtime state, not create new combat logic.
- If you change record shape or naming, verify both logger writes and report reads.
- HTML structure, chart data, and stat snapshot assumptions must remain aligned with `StatsRecorder` and `CombatSimulator`.
