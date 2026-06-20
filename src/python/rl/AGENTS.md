# AGENTS.md

## Scope
- This file applies to `src/python/rl/`.

## Directory role
- This directory contains the Python-side learner for the hybrid RL stack.
- Java owns `CombatSimulator` and rollout execution.
- Python owns the recurrent PPO policy, optimizer, checkpointing, and evaluation control.

## Python files in this directory
- `train_recurrent_ppo.py`: PyTorch-based recurrent PPO training entry point against the Java rollout service. Use `--preset`, `--seed`, local `--host/--port`, or remote `--endpoints`, plus training hyperparameter overrides and optional `--wandb*` flags.
- `evaluate_policy.py`: evaluation entry point that loads a saved checkpoint and runs deterministic, stochastic, or both evaluation modes. Use `--checkpoint`, local `--host/--port`, or remote `--endpoints`, plus `--mode`.
- `evaluation.py`: shared evaluation helpers, policy/service compatibility checks, per-party aggregation, attention and role-alignment summary extraction.
- `benchmark_rollout.py`: Python-side rollout throughput benchmark against the Java rollout service. Use `--envs`, `--steps`, local `--host/--port`, or remote `--endpoints`.
- `rollout_service_client.py`: client for the Java rollout service and batched environment protocol, including multi-endpoint fan-out for split-node rollout.
- `binary_protocol.py`: binary framing constants and read/write helpers shared by Python transport code.
- `recurrent_ppo.py`: recurrent PPO model definition and GAE helper logic.
- `sil_buffer.py`: per-party top-K self-imitation learning buffer used by recurrent PPO training.
- `sweep_recurrent_ppo.py`: W&B sweep trial runner that launches a local Java rollout service and delegates to recurrent PPO training.
- `tests/`: focused Python tests, currently including party-permutation invariance coverage.

## Coupling and dependencies
- These scripts depend on the Java rollout service started by `sample.ServeRLJava`.
- `rollout_service_client.py` must stay consistent with `src/java/mechanics/rl/bridge/`.
- `recurrent_ppo.py` depends on `.venv` providing `torch` and `numpy`.
- `train_recurrent_ppo.py --wandb` additionally depends on `.venv` providing `wandb`.
- `sweep_recurrent_ppo.py` also depends on `wandb` and launches `./gradlew classes` before starting `sample.ServeRLJava`.
- Output artifacts are written under `output/recurrent_ppo_py/`; evaluation reports may also be written as `output/rl_report.html` and party-specific files such as `output/rl_report_flinsparty2.html`.
- Repository shell wrappers such as `execute.sh`, `evaluate.sh`, and `execute_sweep_agent.sh` may launch these scripts, but the Python entry points remain the tracked contract.
- Training and evaluation scripts select Java-side party catalogs through Java service metadata and optional party-selection arguments/env vars that must match `RLPartyRegistry` naming.

## Agent guidance
- Treat observation layout, action-mask semantics, batch protocol framing, and endpoint parsing as contract-level behavior shared with Java.
- Treat Java-provided party-name ordering as contract-level behavior too. Python logging and evaluation should derive per-party summaries from service metadata instead of assuming a fixed catalog.
- Keep script names explicit about role. Avoid generic names such as `train.py` or `model.py` when the responsibility is narrower.
- If you change protocol, learner inputs, or checkpoint format, verify both the Java rollout service and Python train/evaluate scripts.
- If you change per-party metadata, role vectors, privileged observations, or report generation flags, verify `evaluation.py`, `evaluate_policy.py`, and Java `mechanics.rl.bridge` together.
- Prefer vectorized tensor operations over per-sample Python loops when improving learner throughput.
- Keep local single-endpoint debugging available even when adding cluster-facing rollout orchestration.
