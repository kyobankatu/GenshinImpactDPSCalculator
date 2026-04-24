# Rollout Throughput Improvement Tasks

## Goal

Increase Java-side rollout throughput without breaking training quality.

The current learner is already fast enough relative to rollout collection.
The next major improvement target is the Java environment execution path.

## Current Situation

- Python optimization is much cheaper than rollout collection
- rollout timing is stable but still dominates update wall time
- deterministic policy quality has improved enough that throughput work is now worth prioritizing
- the main remaining systems question is how to make Java rollout significantly faster

## Work Plan

### Phase 1: Verify Whether `VectorizedEnvironment.step()` Is Effectively Sequential

- Inspect the current implementation of:
  - `VectorizedEnvironment.step()`
  - `VectorizedEnvironment.reset()`
- Confirm whether multiple environments are:
  - executed sequentially in one thread
  - or actually parallelized across worker threads
- If execution is sequential, document that as the first confirmed bottleneck.
- Deliverable:
  - a clear statement of whether vectorization is only batching or true parallelism

### Phase 2: Profile `BattleEnvironment.step()` Hot Paths

- Use Java profiling and timing to identify the dominant cost inside rollout steps.
- Focus profiling on:
  - `BattleEnvironment.step()`
  - action execution
  - observation encoding
  - action-mask generation
  - combat simulator stepping
- Preferred tools:
  - existing lightweight timing already in the repo
  - JFR if available in the runtime environment
  - async-profiler if available later
- Deliverable:
  - a ranked list of the hottest methods in step execution

### Phase 3: Reduce Allocation Pressure In Hot Loops

- Audit per-step allocation in:
  - observation creation
  - action mask creation
  - action result creation
  - intermediate collections or temporary objects
- Prioritize removing:
  - repeated `List` creation
  - repeated `Map` creation
  - avoidable array allocation
  - avoidable boxing/unboxing
- Prefer:
  - reusable primitive buffers
  - worker-local reusable arrays
  - fewer temporary wrapper objects in the hot path
- Deliverable:
  - lower allocation rate and lower GC pressure during rollout

### Phase 4: Add Multi-Service Rollout Scaling If Needed

- Only after Phases 1 to 3 are measured and partially optimized, evaluate process-level scaling.
- Candidate design:
  - multiple Java rollout service processes
  - separate ports per service
  - Python-side fan-out across services
- Use this only if:
  - a single service process remains CPU-bound
  - in-process optimizations are not enough
- Deliverable:
  - a scaling design that can exceed single-process rollout throughput

## Acceptance Criteria

This rollout-speed pass is complete only when:

- we know whether vectorization is sequential or parallel
- we have profiling evidence for the hottest rollout methods
- at least one meaningful allocation reduction has been implemented
- we have a decision on whether multi-service rollout is necessary

## Notes

- Do not optimize Python first; rollout is the dominant cost.
- Do not jump to multi-process rollout before measuring the single-process bottleneck properly.
- Keep deterministic/stochastic evaluation and current training diagnostics intact while optimizing throughput.
