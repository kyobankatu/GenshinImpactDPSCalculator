# RL Hot Path Optimization Tasks

## Goal

Improve rollout throughput without turning the combat engine into unreadable low-level code.

The optimization target is the RL hot path only. General simulation readability and maintainability still matter.

## Design Constraints

- Keep optimizations local to the confirmed hot path.
- Prefer explicit helper types and overloads over hidden mutable global state.
- Do not spread buffer reuse and cache plumbing across unrelated subsystems.
- Keep existing public APIs usable unless the hot-path-specific alternative is clearly better.
- Add a short explanation when a change exists primarily for performance.

## Profiling Summary

Current measurements show:

- `BattleEnvironment.execute(...)` dominates step cost
- `encode` and `mask` are secondary
- JFR points to:
  - `CombatActionResolver.resolveWithoutTimeAdvance(...)`
  - `DamageCalculator.calculateDamage(...)`
  - `DamageCalculator.resolveStats(...)`
  - `Character.getEffectiveStats(...)`
  - `StatAssembler.assembleEffectiveStats(...)`
  - buff assembly and map-heavy stat work

## Work Plan

### Phase 1: Remove Redundant Per-Action Buff Assembly

- Resolve applicable buffs once per action inside the action-resolution hot path
- Reuse that resolved buff list for:
  - reaction stat lookup
  - final damage calculation
- Avoid recomputing the same applicable buff set multiple times within one action
- Keep the optimization scoped to action resolution rather than changing the entire simulator API

### Phase 2: Remove Redundant Per-Action Stat Assembly

- Resolve live stats once per action when snapshot is not required
- Reuse the same resolved stats for:
  - reaction calculations
  - damage calculations
- Add optimized overloads where needed instead of forcing all callers through a new contract

### Phase 3: Re-measure Before Going Broader

- Re-run the local benchmark and compare:
  - `meanStepMs`
  - `executeShare`
  - `envSteps/s`
- Only if the gain is real, consider the next layer:
  - `BuffManager` internal reusable buffers
  - `StatsContainer` / `StatAssembler` allocation reduction
  - event-queue specialization

## Acceptance Criteria

This pass is complete only when:

- one-action repeated buff assembly is removed in the hot path
- one-action repeated stat assembly is reduced in the hot path
- code structure remains local and understandable
- benchmark results are re-collected after the change

## Notes

- Do not optimize everything at once.
- Do not trade away broad code clarity for tiny wins outside the measured hot path.
- Prefer “one optimized path for RL execution” over “global low-level rewrite.”
