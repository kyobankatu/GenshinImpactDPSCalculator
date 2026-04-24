# AGENTS.md

## Scope
- This file applies to `src/java/mechanics/analysis/`.

## Directory role
- This package contains post-simulation analysis and time-series stat capture utilities.
- It does not drive combat directly; it reads simulator state and packages it for optimization or reporting.

## Java files in this directory
- `EnergyAnalyzer.java`: derives per-character ER requirements from completed simulations using recorded energy windows and particle totals.
- `StatsRecorder.java`: registers a recurring simulator event that samples effective stats and active buff display labels over time, de-duplicated by logical key.
- `StatsSnapshot.java`: plain snapshot container used to move sampled stat data into the HTML report layer.

## Coupling and dependencies
- `EnergyAnalyzer` depends on `simulation.CombatSimulator` and `model.entity.Character` energy-tracking APIs.
- `StatsRecorder` depends on `simulation.event.TimerEvent`, `simulation.CombatSimulator`, `model.entity.Character`, `model.stats.StatsContainer`, `model.type.StatType`, and `mechanics.buff.Buff`.
- `StatsRecorder` uses buff logical keys for de-duplication and display names only for report-facing labels.
- `StatsSnapshot` is consumed by `visualization.HtmlReportGenerator`.
- `mechanics.optimization.IterativeSimulator` depends on `EnergyAnalyzer` for ER convergence.

## Agent guidance
- If you change stat sampling semantics, verify `visualization.HtmlReportGenerator` still matches the snapshot shape.
- If you change energy accounting fields on `Character`, audit `EnergyAnalyzer` immediately.
- Keep these classes read-oriented. They should observe simulator state, not redefine combat rules.
- Do not reintroduce display-label based grouping for buffs or characters; preserve typed/logical identity in sampling code.
