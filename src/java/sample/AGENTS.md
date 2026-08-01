# AGENTS.md

## Scope
- This file applies to `src/java/sample/`.

## Directory role
- This package contains executable entry points and concrete party scripts used for simulation, optimization, and RL startup.
- These files are the quickest way to understand how the engine is expected to be used.

## Java files in this directory
- `RunPartySimulation.java`: generic catalog-backed party simulation runner. It resolves a party name, runs optimization, executes that party's scripted rotation, records stats, and generates HTML reports.
- `SampleLauncher.java`: Gradle dynamic-task launcher. It resolves `./gradlew <PartyName>` through the shared party catalog and falls back to legacy sample classes for non-party utilities.
- `ReactionRegressionTest.java`: executable regression suite for reaction metadata, Superconduct, Freeze/Shatter, Crystallize, Burning, Bloom-family, Quicken-family, Lunar reactions, aura decay, ICD, and selected character/item accuracy checks.
- `PartyCatalogRegressionTest.java`: executable regression suite for shared party catalog and generic RL parity.
- `ServeRLJava.java`: local Java rollout service used by the Python learner.
- `BenchmarkRLJava.java`: vectorized Java rollout throughput benchmark.
- `ProfileCharacterCapabilities.java`: regenerates RL capability profiles for one or more registered RL parties.

## Coupling and dependencies
- Party-specific character, weapon, artifact, and rotation setup lives in `simulation.party` definitions, not in sample entry points.
- `RunPartySimulation` depends on `simulation.CombatSimulator`, `simulation.party`, `mechanics.optimization`, `mechanics.analysis.StatsRecorder`, and `visualization.HtmlReportGenerator`.
- `ServeRLJava`, `BenchmarkRLJava`, and `ProfileCharacterCapabilities` additionally depend on `mechanics.rl` and should stay aligned with the RL party registry.
- `ReactionRegressionTest` depends on low-level reaction APIs plus simulator execution and is the first validation target for reaction, aura, ICD, Lunar, and item-trigger behavior.
- Rotation scripts in party definitions are boundary adapters: they may use action labels, but simulator internals should resolve them to typed character IDs and action keys.

## Agent guidance
- When validating a gameplay change, run the smallest affected sample entry point first.
- For reaction, aura, ICD, Lunar, or formula behavior, run `./gradlew ReactionRegressionTest` before broader sample runs.
- If you change action keys, boundary labels, cooldown expectations, or team composition assumptions, update these scripts as needed.
- Keep these files explicit and readable. They serve as integration tests and usage documentation more than reusable library code.
- When adding a new party, prefer adding one `simulation.party.PartyDefinition` and registering it in `PartyCatalog`; do not add party-specific sample wrappers or RL factories.
- Let fatal setup, optimization, simulation, and report failures propagate so command-line and Gradle callers receive a failing process status.
- Do not push sample display-name conventions deeper into runtime logic; adapt them at the sample or profile boundary.
