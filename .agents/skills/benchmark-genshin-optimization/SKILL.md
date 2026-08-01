---
name: benchmark-genshin-optimization
description: Validate and benchmark artifact optimization, ER convergence, rotation search, Java rollout throughput, Python client throughput, or PPO training performance with matched correctness and reproducibility controls. Use for optimizer changes, performance claims, profiling, scaling, or regression thresholds.
---

# Benchmark Genshin optimization

1. Read root, optimization, analysis, sample, and RL `AGENTS.md` files matching the benchmark target.
2. Read [benchmark-contract.md](references/benchmark-contract.md). Freeze a correct baseline, representative party/workload, seed policy, acceptance tolerance, and exact command.
3. Identify randomness before measuring. Control or report random weapon/character procs, Python/NumPy/Torch seeds, deterministic versus stochastic evaluation, and party selection.
4. Profile before optimizing. State a falsifiable bottleneck hypothesis: simulation compute, allocation/GC, Java/Python transport, tensor work, synchronization, logging, or load imbalance.
5. Change one bounded factor. Keep reference behavior and run correctness checks before performance comparisons.
6. Use matched environments and repeated runs. Report distributions, not the best isolated value.
7. Include damage/reward deltas, ER convergence, invalid actions, memory when relevant, throughput/latency, worker/env scaling, and regression thresholds.
8. Reject a speedup that is not reproducible or violates the declared correctness tolerance.

Do not generalize a local microbenchmark to cluster or training performance.
