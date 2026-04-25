# RL Rollout Parallelism Tasks

## Goal

Find a better `envs x workers` operating point for rollout throughput without making the production path more complex than the current single-node setup.

## Current Findings

- Single-node training is still the preferred production path.
- `rollout_duration_sec` is still the dominant cost.
- Java benchmark and JFR show that `execute(...)` remains the hot path.
- Cluster metrics also suggest low effective CPU utilization, so parallelism configuration is likely leaving performance on the table.
- Simply raising `envs` from `16` to `24` made production slower, so the next step is controlled diagnosis rather than blind scaling.

## Plan

### Phase 1: Make Java Rollout Workers Configurable

- Allow `VectorizedEnvironment` worker count to be set from the rollout service entry point.
- Keep auto-detection available, but support an explicit worker override for diagnosis runs.
- Surface the configured worker count in rollout metrics and batch logs.

### Phase 2: Measure Parallel Wait Costs

- Extend rollout metrics so a run can distinguish:
  - scheduling / dispatch overhead
  - waiting for worker completion
  - total reset / step timing
- Keep the added metrics local to `VectorizedEnvironment`.
- Do not introduce broad tracing across unrelated subsystems.

### Phase 3: Make Single-Node Grid Runs Easy

- Let `execute.sh` accept environment overrides for:
  - `TRAIN_PROFILE`
  - `TRAIN_ENVS`
  - `TRAIN_ROLLOUT_LENGTH`
  - `JAVA_ROLLOUT_WORKERS`
  - optional W&B grouping metadata
- Default the production profile back to the last known-good `envs=16`.
- Keep a short diagnosis profile suitable for multiple comparison runs.

### Phase 4: Compare `envs x workers` in W&B

- Use native W&B sweep configuration for short diagnosis runs:
  - `sweeps/rollout_parallelism.yaml`
  - `execute_sweep_agent.sh`
- Compare:
  - `perf/rollout_duration_sec`
  - `perf/env_steps_per_second`
  - `perf/optimization_duration_sec`
  - rollout-side wait metrics from Java logs
- Only after parallelism is tuned should deeper simulator hot-path work continue.

## Acceptance Criteria

- Java rollout workers can be overridden without editing Java source.
- Rollout metrics expose enough detail to reason about idle / wait time.
- `execute.sh` supports short diagnosis runs with batch-level overrides.
- W&B runs can be grouped and named clearly enough to compare `envs x workers`.
