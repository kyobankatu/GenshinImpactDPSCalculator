# AGENTS.md

## Project overview
- This repository is a Java 11+ Genshin Impact DPS calculator and battle simulator.
- The project simulates combat over time rather than evaluating isolated formulas. Rotation order, animation time, buffs, elemental aura state, ICD, reactions, energy flow, and periodic effects all matter.
- Main Java source code lives under `src/java/` and Python RL code lives under `src/python/`. `build.gradle` is configured to compile from `src/java`.
- Representative runnable entry points are in `src/java/sample/` such as `RaidenParty`, `FlinsParty`, `FlinsParty2`, `ServeRLJava`, `BenchmarkRLJava`, and `ProfileCharacterCapabilities`.
- `src/java/sample/ReactionRegressionTest` is the lightweight regression executable for reaction, aura, ICD, Lunar, and selected character/item accuracy checks.
- Static character multiplier and status data live under `config/characters/`.
- Generated or published artifacts may appear under `docs/` and simulation HTML reports may be written to the repository root.

## Key directories
- `src/java/simulation/`: combat loop, party management, actions, timer events.
- `src/java/mechanics/`: damage formulas, buffs, reactions, energy, optimization, RL hooks, analysis.
- `src/java/model/`: characters, weapons, artifacts, entity definitions, stats, enums.
- `src/java/visualization/`: visual logging and HTML report generation.
- `src/java/sample/`: executable sample rotations and party setups.
- `src/java/mechanics/rl/`: RL environment, party registry, reward/observation logic, and rollout bridge.
- `src/python/rl/`: Python learner, local rollout client, protocol helpers, and evaluation scripts.

## Build and run commands
- Build the Java project: `./gradlew build`
- Generate Javadoc: `./gradlew javadoc`
- Run a sample simulation through the dynamic Gradle rule: `./gradlew RaidenParty`
- Run another sample simulation: `./gradlew FlinsParty`
- Run reaction and accuracy regressions: `./gradlew ReactionRegressionTest`
- Start the local Java rollout service: `./gradlew ServeRLJava`
- Benchmark vectorized Java rollout throughput: `./gradlew BenchmarkRLJava`
- Refresh capability profiles for registered RL parties: `./gradlew ProfileCapabilities`
- Run Python training from the repo root: `python3 src/python/rl/train_recurrent_ppo.py --preset debug`
- Run Python training with Weights & Biases logging: `python3 src/python/rl/train_recurrent_ppo.py --preset debug --wandb --wandb-project genshin-recurrent-ppo`
- Run Python evaluation from the repo root: `python3 src/python/rl/evaluate_policy.py`

## Development workflow
- Read `README.md` in each package and the relevant sample entry point before changing simulation behavior. Rotation scripts in `src/java/sample/` show how the engine is expected to be driven.
- Prefer minimal, local changes. This codebase has many tightly coupled mechanics, so broad refactors are risky unless they are required.
- When touching combat logic, inspect related systems before editing: buffs, reactions, energy, optimizer assumptions, and report generation often depend on one another.
- Preserve the existing package structure and naming style. New classes should be placed in the closest matching package instead of creating parallel abstractions without need.
- For new RL-trainable parties, prefer extending the shared RL party registry and registry-driven launch paths instead of adding more one-off script conditionals.

## Code style guidelines
- Match the existing Java style in the file you are editing. The codebase mixes concise implementations with heavy Javadoc in core systems; follow local context.
- Do not reformat unrelated files.
- Keep public APIs and sample entry points straightforward and explicit. The project favors readable imperative setup code over clever indirection.
- Prefer adding small helper methods only when they reduce repeated simulation logic or clarify a mechanic.
- If a mechanic is non-obvious or game-specific, add a brief comment or Javadoc explaining the assumption.
- Preserve UTF-8 handling in Gradle and generated documentation.

## Testing and verification
- There is no established JUnit test suite in this repository at the moment; regression checks are executable sample classes.
- For Java changes, at minimum run `./gradlew build`.
- For reaction, aura, ICD, Lunar, character/item trigger, or formula changes, run `./gradlew ReactionRegressionTest`.
- For simulation or optimization changes, also run the most relevant sample entry point, usually `./gradlew RaidenParty` or another affected `src/java/sample/` class.
- If your change affects HTML output, verify that report generation still succeeds and that the resulting file opens with expected sections populated.
- If your change affects RL integration, verify the Java rollout service or Python training/evaluation path you touched.
- In final handoff notes, state exactly what you ran and what you did not run.

## Data and config guidance
- Character stats and talent data are partly data-driven from `config/characters/`. Keep CSV naming and directory conventions consistent with existing characters.
- When adding a new character or mechanic, make sure code and config stay aligned. A partial addition often compiles but fails at runtime when data is loaded.
- Avoid editing generated files in `docs/` unless the task is specifically about published documentation or report assets.

## Security and safety considerations
- RL training uses a local-only Java rollout service plus a Python learner. Do not reintroduce external network dependencies or per-step text protocols into the RL path unless explicitly requested.
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

## Project skills and agent tools
- Canonical project skills live under `.agents/skills/`; Claude discovers thin project shims under `.claude/skills/` that route to the same canonical instructions. Read a selected skill completely before its action, including only the reference it routes for the current target.
- Use `develop-genshin-simulator` for cross-package runtime changes, `validate-genshin-mechanics` for accuracy work, `develop-genshin-rl` for Java/Python RL contracts, `benchmark-genshin-optimization` for performance claims, `validate-genshin-reports` for HTML output, `operate-genshin-hpc` for native cluster runs, and `add-genshin-content` for characters, equipment, parties, or configuration.
- Use `research-genshin-evidence` for sourced game research, `manage-genshin-experiments` for durable long-run state, `clean-genshin-artifacts` before expanding cleanup, `present-genshin-results` for technical communication, and `coordinate-genshin-agents` only for explicitly requested collaboration or independent review.
- Use `plan-genshin-implementation` before multi-step work to write or update the `TASKS.md` phase plan, `apply-genshin-code-style` when authoring source, `verify-genshin-changes` before commit or handoff, and `manage-genshin-git` for branch, commit, push, and stash decisions.
- Use `submit-genshin-gpu-job` for the concrete `ybatch` submission path on this machine, `diagnose-genshin-training` when a learning run behaves wrong, and `run-genshin-autonomous-session` for long unattended sessions that must not pause for approval.
- Run `python scripts/validate_agent_assets.py` after changing a skill, its metadata, a routed reference, or a Claude shim.
- Use `python scripts/agent_validate.py --path PATH` to print the focused checks implied by one or more changed paths. Add `--run` only when those checks are intended; the tool never submits HPC jobs or starts a persistent rollout service.
- Use `python scripts/preflight.py` as the pre-commit gate. It applies the same routing and additionally fails when a deliberately untracked artifact such as `*.sh`, `logs/`, `output/`, or `.claude/settings.local.json` has been staged. Add `--run` to execute the routed checks.
- These project skills supplement, but never override, root and nearest-package `AGENTS.md` instructions.
