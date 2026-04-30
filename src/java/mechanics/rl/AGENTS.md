# AGENTS.md

## Scope
- This file applies to `src/java/mechanics/rl/`.

## Directory role
- This package contains the Java-side RL environment, observation encoding, reward logic, party registry, and rollout integration for the Python learner.

## Java files in this directory
- `RLPartyRegistry.java`: central registry for RL-available parties and selection parsing used by training, evaluation, profiling, and benchmarks.
- `RLPartySpec.java`: declarative metadata for one RL-usable party.
- `FlinsParty2RLSimulatorFactory.java`: creates the fixed Flins/Ineffa/Columbina/Sucrose simulator and exposes a registry-ready party spec.
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
- `sample.ProfileCharacterCapabilities` uses the same registry to rebuild capability profiles across registered parties.
- Observation layout, action ID mapping, action-mask semantics, and batch protocol framing are contract-level behavior shared by Java and Python.

## Agent guidance
- Treat observation layout, action IDs, and action-mask semantics as protocol-level behavior. Changing them requires coordinated rollout and learner updates.
- Treat the party registry as the primary extension point for new RL teams. Adding a new RL party should mean adding a factory/spec and registering it once.
- Keep RL boundary translation explicit. Do not leak RL action IDs into core simulator control flow.
- If you change reward, observation, action validation, or protocol framing, verify `ServeRLJava`, the Python learner scripts, and `BenchmarkRLJava`.
- If you change party selection or report naming semantics, verify `evaluate_policy.py`, `execute.sh`, and `evaluate.sh` still match the Java-side behavior.
- Keep the hot path allocation-light. Rollout collection is performance-sensitive by design.
