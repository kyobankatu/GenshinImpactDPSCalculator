# RL Deterministic Improvement Tasks

## Goal

Raise final policy quality with emphasis on deterministic evaluation.

The current system already trains successfully, but the main failure mode is now clear:

- stochastic evaluation can reach strong damage
- deterministic evaluation collapses to a weaker fixed action pattern
- the learned policy underuses important swap actions when evaluated with argmax

So the next work is focused on converting "good stochastic behavior" into
"good deterministic behavior".

## Current Diagnosis

Based on the latest runs:

- deterministic evaluation overuses `ATTACK`
- deterministic evaluation almost never swaps to `INEFFA` or `SUCROSE`
- stochastic evaluation uses those swaps and reaches much higher damage
- rollout throughput is stable enough for experimentation
- the immediate bottleneck for quality is not basic system functionality
- the immediate bottleneck for quality is policy collapse toward a weak deterministic branch

## Phase 1: Preserve The Diagnosis Tooling

- Keep deterministic and stochastic evaluation separated.
- Keep action-fraction metrics in `wandb` for:
  - deterministic evaluation
  - stochastic evaluation
- Keep Java-side rollout timing visible.
- Keep all major training knobs controllable from `execute.sh`.

## Phase 2: Add Exploration-To-Exploitation Scheduling

- Add explicit entropy scheduling to training.
- Early training should keep exploration strong enough to discover support rotations.
- Later training should reduce exploration so the policy sharpens into a stronger deterministic path.
- The schedule must be controllable from CLI and therefore from `execute.sh`.
- Minimum support:
  - entropy start coefficient
  - entropy final coefficient
  - schedule progress tracked by update index

## Phase 3: Expose The Active Training Schedule In Metrics

- Log the currently applied entropy coefficient every update.
- Make it visible in:
  - stdout
  - CSV training log
  - `wandb`
- Goal:
  - correlate deterministic improvement or collapse with schedule progress

## Phase 4: Make Batch Profiles Match The New Strategy

- Keep at least two batch profiles:
  - `diagnosis`
  - `full`
- Both profiles should use:
  - smaller rollout chunks than the original long-rollout setting
  - more updates overall
  - stronger early exploration than before
  - lower effective exploitation noise later in training
- The profile must not require Python source edits.

## Phase 5: Validate That Deterministic Policy Actually Improves

- Success is not:
  - train damage going up alone
  - stochastic evaluation going up alone
- Success is:
  - deterministic damage rising materially
  - deterministic action fractions becoming less attack-dominated
  - deterministic swaps to key support characters appearing when beneficial

## Key Metrics To Watch

- `eval_det/damage`
- `eval_det/steps`
- `eval_det/action_fraction_0`
- `eval_det/action_fraction_4`
- `eval_det/action_fraction_6`
- `eval_stochastic/damage`
- `train/entropy`
- `train/entropy_coefficient`
- `train/approx_kl`
- `train/clip_fraction`

## Acceptance Criteria

This improvement pass is complete only when all of the following are true:

- entropy scheduling is implemented
- entropy schedule is configurable from CLI
- `execute.sh` controls the schedule without Python edits
- `wandb` shows the active entropy coefficient over time
- deterministic action fractions are still logged
- at least one run shows deterministic damage improving beyond the current weak fixed branch

## Notes

- The main current problem is not lack of stochastic competence.
- The main current problem is failure to consolidate good behavior into deterministic action selection.
- Do not remove the existing diagnostic metrics while iterating on the schedule.
