# AGENTS.md

## Scope
- This file applies to `src/java/sample/`.

## Directory role
- This package contains executable entry points and concrete party scripts used for simulation, optimization, and RL startup.
- These files are the quickest way to understand how the engine is expected to be used.

## Java files in this directory
- `FlinsParty.java`: sample simulation for a custom Lunar team using one artifact and weapon configuration set, optimizer pipeline, fixed scripted rotation, stat recording, and HTML reporting.
- `FlinsParty2.java`: alternate custom Lunar team sample with a different gear configuration and longer Flins burst sequencing.
- `RaidenParty.java`: Raiden National sample used as a conventional benchmark team with optimizer pipeline and scripted 21-second rotation.
- `ServeRLJava.java`: local Java rollout service used by the Python learner.
- `BenchmarkRLJava.java`: vectorized Java rollout throughput benchmark.

## Coupling and dependencies
- These entry points depend on `simulation.CombatSimulator`, concrete `model.character`, `model.weapon`, and `model.artifact` classes, `mechanics.optimization`, `mechanics.analysis.StatsRecorder`, and `visualization.HtmlReportGenerator`.
- `ServeRLJava` and `BenchmarkRLJava` additionally depend on `mechanics.rl`.
- Rotation scripts in these files are boundary adapters: they may use display names or action labels, but simulator internals should resolve them to typed character IDs and action keys.

## Agent guidance
- When validating a gameplay change, run the smallest affected sample entry point first.
- If you change action keys, boundary labels, cooldown expectations, or team composition assumptions, update these scripts as needed.
- Keep these files explicit and readable. They serve as integration tests and usage documentation more than reusable library code.
- Do not push sample display-name conventions deeper into runtime logic; adapt them at the sample or profile boundary.
