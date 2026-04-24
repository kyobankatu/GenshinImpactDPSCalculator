# AGENTS.md

## Scope
- This file applies to `src/java/visualization/`.

## Directory role
- This package records simulation output and renders it into interactive HTML reports.

## Java files in this directory
- `ElementColorPalette.java`: package-private chart color selector for actor/element series.
- `HtmlReportGenerator.java`: public report-generation entry point; orchestrates data building, HTML rendering, and file writing.
- `ReportData.java`: package-private immutable-ish report data bundle passed from builder to renderer.
- `ReportDataBuilder.java`: package-private adapter that aggregates simulation records, party state, artifact rolls, and optional stat snapshots into report-ready data.
- `ReportFileWriter.java`: package-private output writer for generated HTML files.
- `ReportHtmlRenderer.java`: package-private renderer that turns `ReportData` into a self-contained Chart.js-based HTML document.
- `ReportViewAdapter.java`: boundary adapter that translates runtime/model state into report-facing view data and labels.
- `SimulationRecord.java`: data holder for one logged event, including action, damage, reaction metadata, aura snapshot, and optional formula text.
- `VisualLogger.java`: singleton collector for `SimulationRecord` entries during a run.

## Coupling and dependencies
- `visualization` depends on `simulation.CombatSimulator`, `model.type.Element`, `model.type.StatType`, and optionally `mechanics.analysis.StatsSnapshot`.
- `simulation.CombatSimulator` writes timeline entries to `VisualLogger`.
- Sample entry points clear the logger and call `HtmlReportGenerator.generate(...)` after simulations.
- Display names, reaction labels, and action labels are appropriate in this package; they should not be treated as runtime control-flow keys.

## Agent guidance
- Keep this package downstream-only. It should reflect runtime state, not create new combat logic.
- If you change record shape or naming, verify both logger writes and report reads.
- HTML structure, chart data, and stat snapshot assumptions must remain aligned with `StatsRecorder` and `CombatSimulator`.
- Keep `HtmlReportGenerator` as the stable public entry point. Put report aggregation in `ReportDataBuilder`, markup generation in `ReportHtmlRenderer`, output concerns in `ReportFileWriter`, and report-facing translation in `ReportViewAdapter`.
- Do not push presentation concerns back into simulator or model classes.
