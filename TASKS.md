# RL Improvement Plan: SIL removal + VinePPO

## Background

The current model collapses to `action_fraction_0 ≈ 0.995` (Flins attack spam,
`eval_det/damage = 911,441`) due to two structural problems:

1. **SIL v_loss scale mismatch**: SIL loss contribution ~13,492 vs PPO total ~12.
   SIL dominates 99.9% of the gradient, making PPO updates meaningless.
2. **Credit assignment gap**: GAE cannot properly credit buff-setup actions
   (swap → support skill/burst) because their immediate reward is negative and
   payoff is delayed 5–15 action steps. The value network learns to predict low
   value at these states, self-reinforcing the "always attack" local optimum.

---

## Phase 1: Remove SIL

**Files**
- `src/python/rl/train_recurrent_ppo.py`

**Changes**

- Set `sil_loss_weight` default to `0.0` in all presets.
- Remove `SILBuffer` instantiation and all `sil_buffer.*` call sites from
  `run_training`.
- Remove the `compute_sil_loss` call from `train_epoch`; set
  `total_sil_loss = 0.0` unconditionally.
- Keep `sil_buffer.py` in the repo but stop importing it.

**Acceptance criteria**
- `train/sil_loss` is `0.0` in every logged W&B update.
- PPO policy loss and value loss are the dominant terms in total loss.
- A debug run (`preset=debug`) completes without errors.

---

## Phase 2: VinePPO — Monte Carlo credit assignment

**Reference**: arXiv:2410.01679 (ICML 2025)

**Core idea**: Replace the learned value baseline with unbiased Monte Carlo
estimates. At selected steps, K independent rollouts are branched from a
simulator snapshot to estimate V(s) directly. Buff-setup actions get honest
credit rather than a biased baseline that underestimates their worth.

```
A_VinePPO(s_t, a_t) = Q_MC(s_t, a_t) - V_MC(s_t)
Q_MC = r_t + γ * mean(discounted returns from s_{t+1}, K rollouts)
V_MC =           mean(discounted returns from s_t,    K rollouts)
```

VinePPO advantage is used at sampled branch points; standard GAE is used at
all other steps.

---

### Phase 2-A: CombatSimulator snapshot/restore (Java)

**Files**
- `src/java/simulation/CombatSimulator.java`
- `src/java/mechanics/rl/SimulatorSnapshot.java` (new)

**Changes**

Implement `SimulatorSnapshot` as a deep copy of all mutable simulator state:

- Current time, pending action queue
- Per-character: HP, energy, skill/burst CD timers, active buffs with remaining
  durations
- Enemy: elemental aura gauges
- Any cached derived values that affect future damage output

Expose on `CombatSimulator`:
- `saveSnapshot() → SimulatorSnapshot`
- `restoreSnapshot(SimulatorSnapshot)`

**Acceptance criteria**
- Determinism test: `save → restore → step` produces byte-identical output to
  the original step from the same state.
- `save → modify sim → restore → save` produces identical bytes as the first
  snapshot.

---

### Phase 2-B: branchRollout endpoint (Java)

**Files**
- `src/java/mechanics/rl/BattleEnvironment.java`
- `src/java/mechanics/rl/RolloutService.java`
- `src/java/mechanics/rl/BatchProtocol.java`

**Changes**

Add `BRANCH_ROLLOUT` request type to the TCP protocol:

- Input: `(snapshot_bytes, action_id, horizon_steps, K)`
- For each of K branches:
  1. Restore snapshot.
  2. Execute `action_id`.
  3. Run `horizon_steps` steps under a uniform-random valid-action policy
     (or pass actions from Python; see Phase 2-C).
  4. Accumulate discounted reward.
- Output: array of K discounted returns.

**Acceptance criteria**
- Calling `BRANCH_ROLLOUT` twice with the same inputs returns identical results.
- Branching does not corrupt the main environment state.

---

### Phase 2-C: VinePPO advantage mixing (Python)

**Files**
- `src/python/rl/recurrent_ppo.py`
- `src/python/rl/train_recurrent_ppo.py`

**Changes**

1. `recurrent_ppo.py`: no structural change needed; advantage values are passed
   in from outside already.

2. `train_recurrent_ppo.py`:
   - After collecting a rollout, identify branch-sample points. Default strategy:
     sample steps where the taken action is SKILL, BURST, or any SWAP (these are
     the actions where credit assignment is most important).
   - For each sampled step, call `BRANCH_ROLLOUT` and receive K MC returns.
   - Compute `A_VinePPO` and replace the GAE advantage at that step.
   - Non-sampled steps retain standard GAE advantage unchanged.

3. Add to config / `PRESETS`:
   - `use_vine_ppo` (bool, default `False`)
   - `vine_branch_count` K (int, default `4`)
   - `vine_horizon` H (int, default `16`)
   - `vine_sample_actions` (list, default `["SKILL","BURST","SWAP"]`)

**Acceptance criteria**
- See party-agnostic success criteria below.

---

## Execution order

```
Phase 1 (SIL removal)
    └─► retrain baseline, confirm policy loss dominates and sil_loss = 0
Phase 2-A (snapshot/restore)
    └─► determinism unit test passes
Phase 2-B (branchRollout endpoint)
    └─► determinism test for branching
Phase 2-C (VinePPO mixing)
    └─► full training run with use_vine_ppo=True
```

Phase 2-A through 2-C must be sequential. Phase 1 can start immediately.

---

## Success criteria

Success is measured by metrics that are independent of party composition.
Absolute damage values are intentionally excluded because they vary by party.

### Phase 1: SIL removal

| Metric | Criterion | Rationale |
|---|---|---|
| `train/sil_loss` | 0.0 on every update | Confirms SIL is fully disabled |
| `train/policy_loss` dominates total loss | `abs(policy_loss) > 0.5 * value_loss` | PPO gradient must be the primary learning signal |

### Phase 2: VinePPO credit assignment

**Credit assignment quality** — measures whether VinePPO provides unbiased
advantage estimates at setup actions, regardless of which party is running:

| Metric | Criterion | Rationale |
|---|---|---|
| `vine/setup_action_advantage_mean` | > 0 | SKILL/BURST/SWAP actions receive positive credit on average; negative means the agent is being told setup is harmful |
| `vine/advantage_bias` | Non-zero at training start, converging over time | `mean(\|VinePPO_adv − GAE_adv\|)` at setup steps; a large initial gap confirms GAE was biased, convergence confirms VinePPO is stabilizing the baseline |
| `vine/mc_return_variance` | Decreasing trend over training | High variance early is expected; decreasing variance indicates the policy is becoming consistent enough for MC estimates to be reliable |

**Policy diversity** — measures whether the agent uses the full action space,
regardless of which actions are optimal for a given party:

| Metric | Criterion | Rationale |
|---|---|---|
| `train/entropy` | > 0.01 throughout all updates | Entropy near zero (current: ~1e-5) means the policy has collapsed and cannot recover |
| `train/non_attack_action_fraction` | > 0.15 | At least 15% of actions are SKILL, BURST, or SWAP; any rotation requires setup actions regardless of party |
| `eval/carry_on_field_fraction` | > 0.4 | The character slot with the highest `self_enhancement_score` in its capability profile stays on-field more than 40% of episode time; this is party-agnostic because the carry role is identified from the profile, not hardcoded |

**Learning stability** — party-agnostic sanity checks:

| Metric | Criterion |
|---|---|
| `train/approx_kl` | < 0.05 on every update |
| `train/clip_fraction` | < 0.3 on every update |
