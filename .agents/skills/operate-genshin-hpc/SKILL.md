---
name: operate-genshin-hpc
description: Plan, launch, monitor, validate, and record Genshin Java rollout services and Python RL training or evaluation on native HPC schedulers and multi-endpoint environments. Use for Slurm, PBS, AGE, GPU, remote rollout, distributed endpoint, allocation, affinity, or cluster benchmark work.
---

# Operate Genshin HPC workloads

1. Read root and both RL `AGENTS.md` files, then [hpc-run-contract.md](references/hpc-run-contract.md). Compose an available native-HPC skill when the environment supplies one; this repository remains independently usable.
2. Resolve the exact target, scheduler, account, queue/partition, architecture, persistent/cache roots, environment, Java/Python versions, and network boundary. Stop if billing or project authority is missing.
3. Print and retain native commands. Do not conceal `sbatch`, `srun`, `qsub`, `ybatch`, module, uenv, container, or launcher semantics.
4. Freeze the source revision and relevant Java/Python files before submission. Reject duplicate job names, result collisions, incompatible service metadata, and ambiguous endpoints.
5. Keep rollout services bound to an explicitly approved interface; default to loopback for local work. Never expose the service publicly by accident.
6. Start with one service, one party, and a bounded debug learner/evaluator. Scale endpoints, workers, environments, devices, or nodes one dimension at a time.
7. Monitor exact job IDs and reconcile scheduler status with private terminal results. Queue delay does not authorize replacement.
8. Validate correctness before throughput. Record revision, commands, job IDs, environment, endpoint topology without credentials, cleanup, results, and retry safety.

Do not install a generic global framework to hide site differences. Do not store credentials, private endpoint details, or large job artifacts in Git.
