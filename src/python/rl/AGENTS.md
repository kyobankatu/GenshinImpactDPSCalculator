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
- `benchmark_rollout.py`: Python-side rollout throughput benchmark against the Java rollout service. Use `--envs`, `--steps`, local `--host/--port`, or remote `--endpoints`.
- `rollout_service_client.py`: client for the Java rollout service and batched environment protocol, including multi-endpoint fan-out for split-node rollout.
- `binary_protocol.py`: binary framing constants and read/write helpers shared by Python transport code.
- `recurrent_ppo.py`: recurrent PPO model definition and GAE helper logic.

## Coupling and dependencies
- These scripts depend on the Java rollout service started by `sample.ServeRLJava`.
- `rollout_service_client.py` must stay consistent with `src/java/mechanics/rl/bridge/`.
- `recurrent_ppo.py` depends on `.venv` providing `torch` and `numpy`.
- `train_recurrent_ppo.py --wandb` additionally depends on `.venv` providing `wandb`.
- Output artifacts are written under `output/recurrent_ppo_py/`; evaluation reports may also be written as `output/rl_report.html` and party-specific files such as `output/rl_report_flinsparty2.html`.
- Single-node cluster runs use `execute.sh`, which can also be parameterized with `TRAIN_ENVS`, `JAVA_ROLLOUT_WORKERS`, and `WANDB_GROUP` for short diagnosis sweeps.
- Single-node training and evaluation scripts select Java-side party catalogs through `RL_PARTIES`, which should match the Java registry naming.
- Split-node cluster runs remain experimental and use `execute_rollout.sh` / `execute_learner.sh`.

## Agent guidance
- Treat observation layout, action-mask semantics, batch protocol framing, and endpoint parsing as contract-level behavior shared with Java.
- Treat Java-provided party-name ordering as contract-level behavior too. Python logging and evaluation should derive per-party summaries from service metadata instead of assuming a fixed catalog.
- Keep script names explicit about role. Avoid generic names such as `train.py` or `model.py` when the responsibility is narrower.
- If you change protocol, learner inputs, or checkpoint format, verify both the Java rollout service and Python train/evaluate scripts.
- Prefer vectorized tensor operations over per-sample Python loops when improving learner throughput.
- Keep local single-endpoint debugging available even when adding cluster-facing rollout orchestration.
