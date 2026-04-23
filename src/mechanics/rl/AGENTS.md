# AGENTS.md

## Scope
- This file applies to `src/mechanics/rl/`.

## Directory role
- This package contains the Java side of the experimental RL training loop and the fixed teacher rotation used for guided training.

## Java files in this directory
- `FlinsParty2Rotation.java`: builds the staged teacher rotation for the Flins/Ineffa/Columbina/Sucrose training team.
- `RLServer.java`: TCP server that resets simulations, accepts action IDs, executes actions, applies reward shaping, emits state vectors, and optionally generates RL reports.
- `RotationPhase.java`: simple container describing one teacher-rotation phase as character plus ordered actions.

## Coupling and dependencies
- `RLServer` depends on `simulation.CombatSimulator`, `model.entity.Character`, `visualization.HtmlReportGenerator`, `visualization.VisualLogger`, and optimization classes such as `RotationSearcher` and `ProfileLoader.ActionProfile`.
- `sample.RunRL` is the main entry point that creates the simulator factory and passes `FlinsParty2Rotation.build()` into `RLServer`.
- State vector layout, action ID mapping, and profile action mapping are effectively part of the Python-side contract in `rl_optimization/`.

## Agent guidance
- Treat state vector order, action IDs, and teacher-forcing assumptions as protocol-level behavior. Changing them requires coordinated Python-side updates.
- Keep RL-only shortcuts such as zero-cost initial alignment isolated from normal simulator flow.
- If you change reward or action validation logic, verify both training mode and `RESET_WITH_REPORT` behavior.
- Keep RL boundary translation explicit. Do not leak Python action IDs or profile-file labels into core simulator control flow.
