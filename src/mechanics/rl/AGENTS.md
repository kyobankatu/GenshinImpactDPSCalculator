# AGENTS.md

## Scope
- This file applies to `src/mechanics/rl/`.

## Directory role
- This package contains the Java-native experimental RL environment, policies, trainer support, and the fixed teacher rotation used for guided training.

## Java files in this directory
- `FlinsParty2RLSimulatorFactory.java`: creates the fixed Flins/Ineffa/Columbina/Sucrose simulator used by Java-native RL.
- `FlinsParty2Rotation.java`: builds the staged teacher rotation for the Flins/Ineffa/Columbina/Sucrose training team.
- `RLAction.java`: typed seven-action RL action space.
- `RLEnvironment.java`: Java-native episode environment that resets simulations, applies action IDs, computes rewards, emits state vectors, and optionally generates reports.
- `RLStepResult.java`: result object for one environment step.
- `RLTrainingConfig.java`: configuration for episode limits, reward scaling, penalties, teacher forcing, and party order.
- `RotationPhase.java`: typed container describing one teacher-rotation phase as character plus ordered actions.
- `policy/`: Java-native policy interfaces and baseline policies.
- `training/`: Java-native trainer loop and episode statistics.

## Coupling and dependencies
- `RLEnvironment` depends on `simulation.CombatSimulator`, `model.entity.Character`, `visualization.HtmlReportGenerator`, and `visualization.VisualLogger`.
- `sample.TrainRLJava` and `sample.EnjoyRLJava` are the Java-native RL entry points.
- State vector layout, action ID mapping, and profile action mapping are protocol-level behavior shared by the Java-native environment and policies.

## Agent guidance
- Treat state vector order, action IDs, and teacher-forcing assumptions as protocol-level behavior. Changing them requires coordinated policy/trainer updates.
- Keep RL-only shortcuts such as zero-cost initial alignment isolated from normal simulator flow.
- If you change reward or action validation logic, verify both `TrainRLJava` and `EnjoyRLJava`.
- Keep RL boundary translation explicit. Do not leak RL action IDs into core simulator control flow.
