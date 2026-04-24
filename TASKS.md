# RL Improvement Tasks

## Goal

Improve the current hybrid RL stack so that:

- rollout throughput is high enough for long training runs
- training metrics and evaluation metrics are consistent
- deterministic evaluation quality actually improves
- experiment settings can be changed from batch scripts without editing Python code
- training behavior is observable enough to diagnose failure modes quickly

The current system already runs end-to-end, but the latest runs show an important gap:

- training damage improves substantially
- deterministic evaluation stays flat
- PPO update signals shrink over time
- rollout collection is still the dominant wall-clock cost

So the next work is not a migration anymore.
It is focused RL debugging, profiling, and iteration.

## Current Findings

These findings should be treated as the working assumptions until disproven:

1. Java rollout is the main time bottleneck.
   - rollout wall time is much larger than optimization wall time
   - learner-side GPU optimization is not the primary bottleneck right now

2. Heavy Java initialization does not appear to run every episode.
   - `Loaded Talent Data` and `Artifact Optimizer` logs appear at service startup
   - they do not currently appear to repeat inside every training episode

3. The major quality problem is train/eval mismatch.
   - train damage rises
   - deterministic eval damage stays flat
   - eval step count stays flat
   - this suggests policy collapse, argmax-path weakness, or evaluation mismatch

4. PPO is likely converging too early to a narrow policy region.
   - entropy declines
   - `approx_kl` declines
   - `clip_fraction` declines
   - eval stays unchanged

## Phase 1: Fix Evaluation Visibility

- Add explicit side-by-side evaluation modes:
  - deterministic evaluation
  - stochastic evaluation
- Ensure training logs and `wandb` clearly distinguish:
  - `eval_det/*`
  - `eval_stochastic/*`
- Required metrics for both modes:
  - reward
  - damage
  - steps
  - invalid actions
- Goal:
  - determine whether the policy is only good when sampled stochastically
  - determine whether argmax evaluation is the real failure mode

## Phase 2: Make Rollout Cost Measurable On The Java Side

- Add Java-side timing around:
  - runner creation
  - reset
  - step
  - observation encoding
  - action mask generation
  - protocol serialization
- Expose or log:
  - mean reset time
  - mean step time
  - episode completions per minute
  - Java-only env steps per second
- Keep these measurements lightweight enough to use during batch runs.
- Goal:
  - stop guessing where rollout time goes
  - identify whether reset, step, or transport is dominant

## Phase 3: Audit The Java Rollout Hot Path

- Verify that training rollouts never trigger:
  - HTML generation
  - `VisualLogger` writes
  - verbose string assembly
  - file output
- Audit `reset` and simulator setup:
  - reuse static character data
  - reuse simulator templates where possible
  - avoid rebuilding immutable configuration every episode
- Audit allocation pressure:
  - repeated `List` or `Map` creation
  - unnecessary copying of observations or masks
  - frequent temporary object allocation in hot loops
- Confirm whether `VectorizedEnvironment` is:
  - truly parallel
  - or just batched but sequential
- Goal:
  - remove obvious Java-side inefficiencies before changing PPO again

## Phase 4: Resolve Train/Eval Divergence

- Compare the current learned policy under:
  - deterministic eval
  - stochastic eval
- If stochastic eval is much better than deterministic eval:
  - inspect action probability distributions
  - inspect whether argmax consistently chooses a weak action branch
- Add diagnostics for action selection quality:
  - top action probability
  - action distribution histogram during eval
  - per-action frequency for deterministic and stochastic runs
- Goal:
  - determine whether the problem is policy quality itself
  - or just how evaluation selects actions

## Phase 5: Improve PPO Stability And Exploration

- Continue using CLI-overridable training parameters from `execute.sh`.
- Run controlled experiments on:
  - `entropy_coefficient`
  - `ppo_epochs`
  - `rollout_length`
  - `envs`
  - `minibatch_size`
- Primary hypothesis to test:
  - current settings cause early narrowing of the policy distribution
- First recommended experiment family:
  - lower `envs`
  - lower `rollout_length`
  - increase total `updates`
  - increase entropy bonus moderately
  - reduce PPO epochs if the same rollout is being overfit
- Goal:
  - keep the policy changing longer
  - improve deterministic eval rather than only train averages

## Phase 6: Strengthen RL Diagnostics

- Keep the current metrics and add missing ones when useful.
- Required ongoing metrics:
  - train reward
  - train damage
  - train episode steps
  - train completed episodes
  - train max/min damage
  - train max/min episode steps
  - invalid/valid action rate
  - PPO `approx_kl`
  - PPO `clip_fraction`
  - value mean
  - log-prob mean
  - rollout duration
  - optimization duration
- Add evaluation comparison panels in `wandb` for:
  - deterministic damage vs stochastic damage
  - deterministic steps vs stochastic steps
  - train mean damage vs eval damage
- Goal:
  - make failure modes immediately visible from charts

## Phase 7: Tune Batch Job Defaults For Real Training

- Keep all important training settings in `execute.sh`.
- Do not require Python source edits to change:
  - update count
  - rollout size
  - environment count
  - PPO epochs
  - entropy coefficient
  - checkpoint interval
  - evaluation interval
- Maintain at least two useful job profiles:
  - quick diagnosis run
  - full training run
- Goal:
  - make cluster experimentation cheap and repeatable

## Phase 8: Revisit Parallel Rollout Architecture If Needed

- If Java-side profiling shows the rollout service itself is saturated:
  - consider multiple rollout workers inside one process
  - consider multiple rollout service processes on separate ports
  - consider Python-side fan-out to multiple Java actors
- Only do this after Phase 2 and Phase 3 confirm that simpler fixes are not enough.
- Goal:
  - scale rollout throughput without redesigning the whole system prematurely

## Phase 9: Validate Real Progress With Acceptance Criteria

- A run should count as meaningful progress only if:
  - deterministic eval damage improves materially over baseline
  - deterministic eval does not stay flat while train damage rises
  - entropy does not collapse immediately
  - PPO `approx_kl` and `clip_fraction` remain non-trivial for a useful period
  - rollout throughput stays acceptable for long runs
- Track and compare runs by:
  - job settings
  - final deterministic eval damage
  - best deterministic eval damage
  - stochastic eval damage
  - total wall time
  - env steps per second

## Deliverables

This improvement pass is complete only when all of the following are true:

- deterministic and stochastic evaluation are both available
- train/eval divergence is understood well enough to explain current runs
- Java-side rollout cost is measured rather than guessed
- `execute.sh` fully controls training settings needed for experiments
- `wandb` shows enough metrics to diagnose PPO collapse and rollout bottlenecks
- at least one revised training configuration improves deterministic evaluation

## Verification

Minimum checks before calling a change complete:

- `./gradlew build`
- Java rollout service starts successfully
- Python training runs with CLI-specified overrides
- deterministic evaluation runs
- stochastic evaluation runs
- `wandb` receives both training and evaluation metrics
- batch job log clearly shows selected training parameters

## Notes

- The central current problem is not “make Python faster”.
- The central current problems are:
  - rollout cost
  - train/eval mismatch
  - premature policy narrowing
- Do not add new architectural complexity until the current failure mode is measured clearly.
