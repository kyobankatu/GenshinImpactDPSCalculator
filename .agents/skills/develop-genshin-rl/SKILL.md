---
name: develop-genshin-rl
description: Develop and validate the hybrid Java/Python reinforcement-learning stack, including party registry, action masks, observations, rewards, snapshots, vectorized rollout service, binary protocol, recurrent PPO, checkpoints, evaluation, and multi-endpoint clients. Use for any RL-facing simulator, protocol, learner, training, or evaluation change.
---

# Develop the Genshin RL stack

1. Read root `AGENTS.md`, `src/java/mechanics/rl/AGENTS.md`, `src/python/rl/AGENTS.md`, and every closer instruction file for changed Java simulator state.
2. Read [contract-matrix.md](references/contract-matrix.md) and identify every Java/Python pair affected before editing.
3. Freeze protocol dimensions, action IDs, party ordering, observation shapes, privileged shapes, capability profile revision, reward semantics, checkpoint metadata, and a reproducible local debug command.
4. Preserve the local-only binary rollout path. Do not add per-step text protocols or external network dependencies.
5. Change both sides of a shared contract atomically. Add compatibility rejection when old checkpoints or clients cannot be interpreted safely.
6. Keep the rollout hot path allocation-light and retain single-endpoint debugging when adding multi-endpoint behavior.
7. Run the relevant Python tests, `PartyCatalogRegressionTest`, `BenchmarkRLJava`, and `build`. Start `ServeRLJava` plus a bounded train/evaluate smoke only when the changed contract requires live integration.
8. Report shapes, protocol version/compatibility, seed, party catalog, worker/env counts, throughput distribution, checkpoint impact, and skipped live checks.

Do not regenerate capability profiles unless capability semantics changed and the rewrite is intended.
