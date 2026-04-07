# AGENTS.md

## Project overview
- This repository is a Java 11+ Genshin Impact DPS calculator and battle simulator.
- The project simulates combat over time rather than evaluating isolated formulas. Rotation order, animation time, buffs, elemental aura state, ICD, reactions, energy flow, and periodic effects all matter.
- Main Java source code lives directly under `src/` rather than the standard Gradle `src/main/java` layout. `build.gradle` is already configured for this.
- Representative runnable entry points are in `src/sample/` such as `RaidenParty`, `FlinsParty`, `FlinsParty2`, and `RunRL`.
- Static character multiplier and status data live under `config/characters/`.
- Generated or published artifacts may appear under `docs/` and simulation HTML reports may be written to the repository root.

## Key directories
- `src/simulation/`: combat loop, party management, actions, timer events.
- `src/mechanics/`: damage formulas, buffs, reactions, energy, optimization, RL hooks, analysis.
- `src/model/`: characters, weapons, artifacts, entity definitions, stats, enums.
- `src/visualization/`: visual logging and HTML report generation.
- `src/sample/`: executable sample rotations and party setups.
- `rl_optimization/`: Python environment and training scripts for the experimental RL flow.

## Build and run commands
- Build the Java project: `./gradlew build`
- Generate Javadoc: `./gradlew javadoc`
- Run a sample simulation through the dynamic Gradle rule: `./gradlew RaidenParty`
- Run another sample simulation: `./gradlew FlinsParty`
- Start the Java RL server: `./gradlew RunRL`
- Run Python RL training from the repo root: `cd rl_optimization && python train.py`
- Run a trained RL policy: `cd rl_optimization && python enjoy.py`

## Development workflow
- Read `README.md` and the relevant sample entry point before changing simulation behavior. Rotation scripts in `src/sample/` show how the engine is expected to be driven.
- Prefer minimal, local changes. This codebase has many tightly coupled mechanics, so broad refactors are risky unless they are required.
- When touching combat logic, inspect related systems before editing: buffs, reactions, energy, optimizer assumptions, and report generation often depend on one another.
- Preserve the existing package structure and naming style. New classes should be placed in the closest matching package instead of creating parallel abstractions without need.

## Code style guidelines
- Match the existing Java style in the file you are editing. The codebase mixes concise implementations with heavy Javadoc in core systems; follow local context.
- Do not reformat unrelated files.
- Keep public APIs and sample entry points straightforward and explicit. The project favors readable imperative setup code over clever indirection.
- Prefer adding small helper methods only when they reduce repeated simulation logic or clarify a mechanic.
- If a mechanic is non-obvious or game-specific, add a brief comment or Javadoc explaining the assumption.
- Preserve UTF-8 handling in Gradle and generated documentation.

## Testing and verification
- There is no established JUnit test suite in this repository at the moment.
- For Java changes, at minimum run `./gradlew build`.
- For simulation or optimization changes, also run the most relevant sample entry point, usually `./gradlew RaidenParty` or another affected `src/sample/` class.
- If your change affects HTML output, verify that report generation still succeeds and that the resulting file opens with expected sections populated.
- If your change affects RL integration, verify both the Java server side and the Python script path you touched.
- In final handoff notes, state exactly what you ran and what you did not run.

## Data and config guidance
- Character stats and talent data are partly data-driven from `config/characters/`. Keep CSV naming and directory conventions consistent with existing characters.
- When adding a new character or mechanic, make sure code and config stay aligned. A partial addition often compiles but fails at runtime when data is loaded.
- Avoid editing generated files in `docs/` unless the task is specifically about published documentation or report assets.

## Security and safety considerations
- RL communication uses a local TCP socket between Java and Python. Keep it local-only unless the task explicitly requires broader exposure.
- Do not introduce external network dependencies into the core simulation path.
- Treat generated reports as build artifacts unless the user explicitly asks to update committed output.

## Commit and PR guidance
- Keep commits focused on one mechanical change, bug fix, or documentation improvement.
- Summaries should mention the subsystem changed, for example: `simulation: fix swap timing for queued actions`
- Include verification notes in PR descriptions or handoff summaries, especially which sample simulations were executed.

## Agent-specific instructions
- Before editing, check whether the target file is source, generated documentation, or an output artifact.
- Prefer validating behavior with the smallest relevant sample simulation instead of changing multiple team scripts at once.
- If a requested change is ambiguous because game logic is underspecified, document the assumption in your final response.
