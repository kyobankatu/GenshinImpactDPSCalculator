# AGENTS.md

## Scope
- This file applies to `src/mechanics/optimization/`.

## Directory role
- This package contains artifact optimization, ER convergence, profile loading, and brute-force rotation search.
- These classes repeatedly execute full simulations and are tightly coupled to the runtime engine.

## Java files in this directory
- `ArtifactOptimizer.java`: generates KQMS-style artifact stat blocks and liquid roll allocations for one character.
- `IterativeSimulator.java`: runs the ER convergence loop and the joint party hill-climbing loop for DPS rolls.
- `OptimizerPipeline.java`: orchestrates two-phase optimization by combining ER calibration with DPS optimization and merging ER rolls back into the final result.
- `ProfileLoader.java`: loads per-character action profiles from plain-text files for rotation search or RL guidance.
- `RotationSearcher.java`: evaluates permutations of party order and action profiles to find a high-damage macro rotation.
- `TotalOptimizationResult.java`: simple result container holding final ER targets and merged party roll maps.

## Coupling and dependencies
- `ArtifactOptimizer` depends on `model.stats.StatsContainer`, `model.type.StatType`, and `model.standards.KQMSConstants`.
- `IterativeSimulator` depends on `simulation.CombatSimulator` factories and `mechanics.analysis.EnergyAnalyzer`.
- `OptimizerPipeline` depends on `IterativeSimulator` and inspects `model.entity.Character` artifact roll state from a generated simulator.
- `ProfileLoader` is consumed by `RotationSearcher` and `mechanics.rl.RLServer`.
- `RotationSearcher` depends on `simulation.CombatSimulator`, `model.entity.Character`, and `ProfileLoader.ActionProfile`.

## Agent guidance
- Any change here should be validated with at least one real sample simulation, not only compilation.
- Keep the distinction clear between heuristic artifact generation, ER reservation, and hill-climbing roll swaps.
- If optimizer results become unstable, inspect simulator determinism first, especially random or time-sensitive weapon effects.
