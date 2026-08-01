---
name: validate-genshin-reports
description: Develop and validate simulation HTML reporting, visual logs, report data aggregation, Chart.js rendering, timelines, damage attribution, aura and energy charts, buff uptime, stat snapshots, assets, and generated report files. Use for visualization, report schema, labels, or HTML output changes.
---

# Validate Genshin reports

1. Read root and visualization `AGENTS.md`, plus analysis or simulator instructions for changed input data.
2. Read [report-contract.md](references/report-contract.md). Trace each changed field from the runtime/logger write through `SimulationRecord`, data builder, view adapter, renderer, and file writer.
3. Keep visualization downstream-only. Do not create combat behavior or control-flow keys from presentation labels.
4. Preserve the distinction between standalone reaction damage and reaction-labeled direct damage.
5. Add or extend `ReportRegressionTest` for structure and data invariants. Run the smallest affected report-generating party.
6. Inspect the actual generated HTML for populated sections, safe escaping, readable charts, timeline filtering, and missing-data behavior.
7. Treat `output/` and root reports as generated artifacts. Update committed `docs/` output only when explicitly requested.
8. Report automated checks, party used, generated file inspected, browser/manual inspection performed or skipped, and intentional visual changes.
