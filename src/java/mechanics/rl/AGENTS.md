# AGENTS.md

## Scope
- This file applies to `src/java/mechanics/rl/`.

## Directory role
- This package contains the Java-side RL environment, observation encoding, reward logic, local rollout service, and fixed simulator factory for the Python learner.

## Java files in this directory
- `FlinsParty2RLSimulatorFactory.java`: creates the fixed Flins/Ineffa/Columbina/Sucrose simulator used by RL rollouts.
- `RLAction.java`: typed seven-action RL action space.
- `ActionSpace.java`: computes legal action masks for the current simulator state.
- `ObservationEncoder.java`: builds the teacher-free observation vector from live battle state.
- `RewardFunction.java`: computes reward from damage, timing cost, and invalid-action penalties.
- `BattleEnvironment.java`: Java-native episode environment with reset, step, masking, and optional report generation.
- `EpisodeConfig.java`: configuration for episode length, penalties, and reward scaling.
- `RecurrentState.java`: recurrent hidden-state container shared by Java-side RL utilities.
- `bridge/`: local binary rollout service and vectorized environment management for the Python learner.

## Coupling and dependencies
- `BattleEnvironment` depends on `simulation.CombatSimulator`, `model.entity.Character`, `visualization.HtmlReportGenerator`, and `visualization.VisualLogger`.
- `sample.ServeRLJava` and `sample.BenchmarkRLJava` are the Java-side RL entry points.
- Observation layout, action ID mapping, action-mask semantics, and batch protocol framing are contract-level behavior shared by Java and Python.

## Agent guidance
- Treat observation layout, action IDs, and action-mask semantics as protocol-level behavior. Changing them requires coordinated rollout and learner updates.
- Keep RL boundary translation explicit. Do not leak RL action IDs into core simulator control flow.
- If you change reward, observation, action validation, or protocol framing, verify `ServeRLJava`, the Python learner scripts, and `BenchmarkRLJava`.
- Keep the hot path allocation-light. Rollout collection is performance-sensitive by design.
