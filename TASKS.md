# Java-Native RL Migration Tasks

## Goal

Move RL training from the Python Gymnasium / Stable-Baselines3 socket loop into the Java process so training runs directly against `CombatSimulator`.

The migration removes the per-step overhead from:

- Python-Java TCP round trips
- JSON state/reward encoding and parsing
- blocking cross-process synchronization
- duplicated action/state protocol code in Python and Java

## Final State

- Java-native RL environment:
  - `src/mechanics/rl/RLEnvironment.java`
  - `src/mechanics/rl/RLAction.java`
  - `src/mechanics/rl/RLStepResult.java`
  - `src/mechanics/rl/RLTrainingConfig.java`
- Java-native policies:
  - `src/mechanics/rl/policy/RLPolicy.java`
  - `src/mechanics/rl/policy/RandomPolicy.java`
  - `src/mechanics/rl/policy/TeacherPolicy.java`
  - `src/mechanics/rl/policy/QLearningPolicy.java`
  - `src/mechanics/rl/policy/PolicyStore.java`
- Java-native trainer/evaluator:
  - `src/mechanics/rl/training/JavaRLTrainer.java`
  - `src/mechanics/rl/training/TrainingStats.java`
  - `src/sample/TrainRLJava.java`
  - `src/sample/EnjoyRLJava.java`
- Fixed RL simulator setup:
  - `src/mechanics/rl/FlinsParty2RLSimulatorFactory.java`
  - `src/mechanics/rl/FlinsParty2Rotation.java`
  - `src/mechanics/rl/RotationPhase.java`
- Gradle tasks:
  - `./gradlew TrainRLJava`
  - `./gradlew EnjoyRLJava`
- Removed Python/socket path:
  - `src/mechanics/rl/RLServer.java`
  - `src/sample/RunRL.java`
  - `rl_optimization/`

## RL Contract

- action space: 7 discrete actions
- state vector: 29 floats
- actions:
  - `0`: active character normal attack
  - `1`: active character skill
  - `2`: active character burst
  - `3`: switch to Flins
  - `4`: switch to Ineffa
  - `5`: switch to Columbina
  - `6`: switch to Sucrose
- state layout:
  - `[Energy, IsActive, CanSkill, CanBurst, IsBurstActive] * 4`
  - `GlobalSwapReady`
  - `TimeRemaining`
  - `TeacherTargetOneHot * 4`
  - `SuggestedActionOneHot * 3`

## Completed Phases

### Phase 1: Extract RL Environment Core

- Added `RLEnvironment` as the source of episode reset, action execution, validation, reward calculation, state vector generation, teacher-forcing progress, and optional report generation.
- Added typed action and result classes so policy code does not depend on socket command strings.
- Moved fixed simulator construction into `FlinsParty2RLSimulatorFactory`.

### Phase 2: Java-Native Policy Interfaces

- Added `RLPolicy` as the Java-side policy contract.
- Added `RandomPolicy` as a baseline.
- Added `TeacherPolicy` to follow the teacher guidance encoded in the state vector.
- Added `QLearningPolicy` as an inspectable Java-native learning baseline.
- Added `PolicyStore` for CSV save/load of the Q-table.

### Phase 3: Java-Native Trainer

- Added `JavaRLTrainer` for in-process training/evaluation episodes.
- Added `TrainRLJava` entry point.
- Added `EnjoyRLJava` entry point.
- Added Gradle tasks for Java-native training and evaluation.
- Java training writes:
  - `output/java_rl_policy.csv`
  - `output/java_rl_training_log.csv`
- Java evaluation writes:
  - `output/rl_report.html`

### Phase 4: Remove Python Socket Path

- Removed Python Gymnasium/Stable-Baselines3 files.
- Removed the Java TCP `RLServer`.
- Removed the `RunRL` Java entry point.
- Replaced Python Gradle tasks with Java-native tasks.

### Phase 5: Documentation Refresh

- Updated root `AGENTS.md`.
- Updated `README.md`.
- Updated `src/mechanics/rl/AGENTS.md`.
- Updated package-level agent notes that referenced `RLServer`, Python scripts, or `rl_optimization`.

## Verification

Required checks for handoff:

- `./gradlew build`
- `./gradlew TrainRLJava`
- `./gradlew EnjoyRLJava`

Expected outputs:

- `output/java_rl_policy.csv`
- `output/java_rl_training_log.csv`
- `output/rl_report.html`

## Future Work

- Compare Q-learning damage/reward against the teacher policy over many episodes.
- Tune reward scaling, invalid-action penalties, epsilon decay, and state discretization.
- If tabular Q-learning is insufficient, add a neural policy behind `RLPolicy` without changing `RLEnvironment`.
- Add regression checks for state vector length and action ID mapping.
