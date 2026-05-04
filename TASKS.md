# General Rotation RL Upgrade Plan v2

## Goal

Build a fully generic rotation-optimization RL stack that can learn an optimal
rotation from scratch for any party composition, without character-specific
reward shaping, observation features, or imitation targets.

The previous plan delivered a strong representational backbone:

- asymmetric actor-critic with privileged critic-only state
- auxiliary latent-value prediction heads
- value-curve based capability profiles
- recurrent (GRU) policy with cross-character attention

Despite this, the trained policy on the FlinsParty2 fixed configuration still
fails to discover the obvious "main carry stays on field" pattern, instead
collapsing into an off-field driver local optimum. Diagnosis points to three
remaining root causes that are *all* generic in nature:

1. The training distribution is a single fixed party, so the policy has no
   pressure to actually exploit the generic capability profile abstraction.
2. Step-wise damage attribution fails to credit "stay on field for several
   seconds, harvest payoff later" actions, especially under off-field DoT and
   delayed-trigger payloads.
3. Exploration is weak: fixed entropy 0.01, no curiosity, no self-imitation.
   The policy collapses early into the easiest-to-find local optimum.

This plan adds the missing generic mechanisms so that the learner becomes a
true multi-party rotation discoverer, building on top of (not replacing) the
existing asymmetric / auxiliary / capability-profile machinery.

---

## Current State

### What already exists (from previous plan, now merged)

- Asymmetric actor-critic with privileged critic-only state.
- Auxiliary latent-value prediction heads predicting privileged targets from
  public history alone.
- Value-curve capability profiles (entry value, sustain windows, exit/reentry
  costs).
- Cross-character attention over 4 party slots.
- Public observation includes per-character static profile, energy/CD readiness,
  is-active flag, burst-active flag, and global aura state.

### What is still missing for true generic rotation learning

- The training environment uses a single fixed party
  (`FlinsParty2` in `evaluate.sh`, fixed `partyOrder` in `EpisodeConfig`).
- Reward attribution remains step-local: damage that originates from a past
  setup action is credited to whichever step it lands in.
- No self-imitation, no curiosity, no entropy schedule. Exploration collapses.
- Public observation lacks generic temporal features such as buff-timer
  distributions and recent on-field history.
- The policy network has not been verified for slot permutation invariance.
- No evaluation harness compares behavior across multiple party archetypes.

### Symptom motivating this plan

- On `FlinsParty2`, the trained policy keeps the main carry off-field most of
  the time, while a hand-designed reference rotation
  (`output/simulation_report.html`) keeps the main carry on-field as expected.
- Further per-architecture tweaks alone are unlikely to escape this local
  optimum; the gap is in training distribution, credit assignment, and
  exploration, not in network capacity.

---

## Non-Goals

- Do not add character-specific reward bonuses or observations.
- Do not hardcode role rules such as "slot 0 must be the on-field carry".
- Do not imitate character-specific human-designed rotations as supervised
  targets.
- Do not introduce long fixed macro-actions that lock the agent for many
  seconds.
- Do not abandon the existing asymmetric / auxiliary / capability-profile
  machinery.

---

## Target End State

After this work, the system should train a single policy that:

- learns party-specific optimal rotations from scratch for arbitrary
  4-character compositions
- exploits generic capability-profile features and learns to attend to the
  right slot for each composition
- properly credits delayed payoffs from off-field DoT, setup-then-cashout, and
  on-field commitment windows
- escapes the off-field local optimum via active exploration and self-imitation
- evaluates on a battery of party archetypes without any reward redesign

The intended abstraction boundary remains:

- `actor`: generic public observation only, slot-permutation invariant
- `critic`: public plus privileged simulator state during training
- `auxiliary heads`: predict generic latent combat-value signals
- `credit assignment`: uses simulator-resettable Monte Carlo lookahead for
  accurate per-action advantage
- `imitation`: self-only, drawn from the agent's own top-K trajectories per
  party archetype

---

## Execution Strategy

Implementation proceeds in the following order, each phase being independently
testable:

1. Phase 1 (multi-party DR) is the foundation. Without it, all later phases
   overfit to a single party.
2. Phase 2 (MC credit assignment) is the core algorithmic improvement.
3. Phase 3 (self-imitation) is a low-cost local-optima breaker.
4. Phase 4 (generic observation enrichment) and Phase 5 (exploration) are
   independent and can run in parallel.
5. Phase 6 (architecture audit) is verification, not new functionality.
6. Phase 7 (cross-archetype evaluation) closes the loop and answers
   "did we generalize".

The first concrete milestone is therefore:

- complete Phase 1 through Phase 3
- verify behavior on `FlinsParty2` (does the main carry stay on field?) and on
  at least 2 other parties
- continue with Phase 4 through Phase 7 only after the first milestone shows
  clear progress

Phase 2 is the most invasive phase and may be deferred until after Phases 1, 3,
4, 5 if implementation cost dominates schedule.

---

## Implementation Plan

### Phase 1: Multi-party domain randomization

**Files**
- `src/java/mechanics/rl/EpisodeConfig.java`
- `src/java/mechanics/rl/BattleEnvironment.java`
- `src/java/mechanics/rl/PartySampler.java` (new)
- `src/java/mechanics/rl/RLPartyRegistry.java`
- `src/java/sample/ServeRLJava.java`

**Changes**

- Introduce a `PartySampler` interface returning a 4-character composition per
  `reset()`.
- Default sampler: uniform random over a curated pool of registered parties
  spanning multiple archetypes.
- `EpisodeConfig` receives the sampler instead of a fixed `partyOrder`.
- `forced_party_id` continues to work for evaluation by selecting a fixed
  sampler index.
- Optional curriculum: start with a smaller party pool, expand as training
  progresses.

**Examples**

- A pool of 6-8 registered parties spanning archetypes such as time-limited
  self-buff carry, frontloaded support, off-field driver, sustain carry.
- Each `reset()` picks one party uniformly, populates `EpisodeConfig.partyOrder`,
  and reinitializes the simulator with capability profiles loaded for the
  sampled composition.

**Acceptance criteria**

- A single training run sees at least 4 distinct party compositions.
- Evaluation can still target a specific party via `forced_party_id`.
- Capability profile lookup is correct for every sampled composition.

---

### Phase 2: Monte-Carlo credit assignment via simulator rollback

**Files**
- `src/java/simulation/CombatSimulator.java`
- `src/java/mechanics/rl/SimulatorSnapshot.java` (new)
- `src/java/mechanics/rl/BattleEnvironment.java`
- `src/java/mechanics/rl/RolloutService.java`
- `src/python/rl/recurrent_ppo.py`
- `src/python/rl/train_recurrent_ppo.py`

**Changes**

- Implement deep snapshot/restore of the simulator state
  (`SimulatorSnapshot.save(simulator) / restore(simulator)`).
- Add a Java-side rollout endpoint `branchRollout(state, action, K, horizon)`
  that:
  1. restores state
  2. takes a candidate action
  3. continues with the current policy for `horizon` steps
  4. returns the discounted return
- Python-side advantage estimation:
  - At selected training steps, sample K candidate actions from the current
    policy.
  - For each, request a branched MC return.
  - Use empirical baseline `mean(MC returns)` and per-action MC return as the
    advantage signal (VinePPO-style, ICML 2025).
- Keep GAE as the default; MC advantage is opt-in via config flag.

**Notes**

- This is the most invasive change, but the simulator is deterministic and
  resettable, which makes VinePPO-style estimation natural.
- Branch budget K should be small (4-8) per sampled step, applied only to a
  fraction of steps to keep wall-clock cost manageable.

**Acceptance criteria**

- `branchRollout` produces deterministic results when called twice from the
  same snapshot.
- PPO advantages computed from MC returns reduce variance vs GAE on a held-out
  check.
- Off-field-DoT-heavy scenarios show better attribution to the action that
  placed the DoT.

---

### Phase 3: Self-imitation learning with per-archetype top-K buffer

**Files**
- `src/python/rl/recurrent_ppo.py`
- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/sil_buffer.py` (new)

**Changes**

- Maintain a per-party-id top-K trajectory buffer ranked by terminal return.
- During each PPO update, mix in mini-batches sampled from the SIL buffer at a
  small ratio (for example 10-20 percent).
- Apply a SIL loss on these samples: weighted policy gradient on actions from
  high-advantage states, optionally combined with value regression
  (Oh et al., ICML 2018; ICLR 2025 RPI for stability tweaks).
- Buffer K = 8-16 per party, refreshed when a new episode beats the worst
  stored return.

**Notes**

- No human demonstrations involved; the imitation source is always the agent's
  own past best.
- Buffer is partitioned by party id to avoid mixing optimal rotations across
  compositions.

**Acceptance criteria**

- SIL buffers fill within the first epoch.
- After integration, the policy improvement curve shows fewer plateaus.
- Best return per party is non-decreasing across training.

---

### Phase 4: Generic public observation enrichment

**Files**
- `src/java/mechanics/rl/ObservationEncoder.java`
- `src/java/model/character/Character.java` accessors
- `src/java/model/StatType.java` (only if a new generic stat key is needed)

**Changes**

Add character-agnostic temporal features to the public observation, exposed
via uniform interfaces on `Character`:

- `self_buff_max_remaining[slot]`: longest active self-buff remaining time,
  normalized.
- `self_buff_count[slot]`: number of active self-buffs, normalized.
- `team_buff_max_remaining`: longest active team-wide buff.
- `time_since_last_active[slot]`: seconds since the slot was last on-field,
  normalized.
- `recent_on_field_fraction[slot]`: fraction of the last N seconds spent
  on-field.
- `recent_damage_share[slot]`: fraction of the last N seconds' damage
  attributed to the slot.

**Notes**

- All features are computed from existing simulator state via uniform
  interfaces; no per-character branches.
- Publishing these in the public actor observation (not privileged) is
  intentional: they help all parties symmetrically and preserve the
  inference-time abstraction boundary.

**Acceptance criteria**

- New observation dimensions are populated for every character regardless of
  party.
- No `if character instanceof X` style branching is introduced.
- Observation size remains stable across compositions.

---

### Phase 5: Exploration enhancement

**Files**
- `src/python/rl/recurrent_ppo.py`
- `src/python/rl/train_recurrent_ppo.py`

**Changes**

- Entropy schedule: linear decay from 0.05 to 0.005 over training (currently
  fixed 0.01).
- Optional Random Network Distillation (RND) intrinsic reward:
  - Frozen target network maps observation to embedding.
  - Trained predictor network learns to match.
  - Prediction error as small intrinsic bonus, discounted separately from
    extrinsic reward.
- Optional Active PPO style adaptive clipping (clip range scaled by per-state
  advantage variance).
- Configuration flags: `entropy_initial`, `entropy_final`, `use_rnd`,
  `rnd_intrinsic_weight`, `use_active_clip`.

**Notes**

- Keep all flags off by default initially; turn them on one at a time during
  ablation.
- RND is character-agnostic — it rewards observation novelty, not character
  identity.

**Acceptance criteria**

- Entropy schedule produces measurably more action diversity early in
  training.
- RND with `rnd_intrinsic_weight = 0.01` does not destabilize PPO updates.
- Local-optimum collapse on `FlinsParty2` is reduced (verifiable via on-field
  fraction of the carry slot).

---

### Phase 6: Permutation-invariance audit

**Files**
- `src/python/rl/recurrent_ppo.py`
- `src/python/rl/tests/test_permutation_invariance.py` (new)

**Changes**

- Verify that `RecurrentPolicy.act()` is invariant to permutations of the 4
  character slots.
  - Unit test: shuffle slot order, remap actions accordingly, assert policy
    output equality within float tolerance.
- If non-invariance is found, replace any per-slot positional bias with Set
  Transformer / Deep Sets style aggregation.
- Ensure attention output is symmetric under slot relabeling.

**Notes**

- Permutation invariance is essential for true multi-party generalization. The
  existing attention layer is a good base, but slot-wise weights or positional
  encodings can quietly break invariance.

**Acceptance criteria**

- Permutation test passes within float tolerance.
- Attention scores reorder symmetrically when slot order is shuffled.

---

### Phase 7: Cross-archetype evaluation harness

**Files**
- `src/python/rl/evaluate_policy.py`
- `src/java/mechanics/rl/RLPartyRegistry.java`
- `evaluate.sh`

**Changes**

- Define an evaluation suite of 6-8 parties spanning archetypes:
  - time-limited self-buff carry
  - frontloaded burst support
  - off-field driver-heavy team
  - sustain carry
  - hybrid setup-and-cashout team
- Run deterministic evaluation on each party with the same checkpoint.
- Report per-party damage, on-field fractions, action distributions, and HTML
  reports (one report per party as already implemented).
- Summarize whether the policy generalizes (similar relative damage across
  all archetypes).

**Acceptance criteria**

- Evaluation produces one HTML report per party plus a comparison summary.
- The same checkpoint is evaluated across all parties without retraining.
- A reproducible script runs the full suite.

---

## Proposed Config Additions

- `party_sampler` (`fixed`, `uniform`, `curriculum`)
- `party_pool`
- `use_mc_credit_assignment`
- `mc_branch_count`
- `mc_branch_horizon`
- `use_self_imitation`
- `sil_buffer_size_per_party`
- `sil_loss_weight`
- `entropy_initial` / `entropy_final`
- `use_rnd`
- `rnd_intrinsic_weight`
- `use_active_clip`

Notes:

- Defaults should leave Phases 2, 3, 5 disabled until validated.
- Phase 1 should be enabled by default once the registered party pool
  stabilizes.

---

## Verification Plan

### Minimum verification

1. Run `./gradlew build`
2. Run a multi-party smoke training (≥ 2 parties, ≥ 10 updates).
3. Run `src/python/rl/evaluate_policy.py` across the evaluation suite.
4. Check that resulting HTML reports show non-trivial main-carry on-field time
   on `FlinsParty2`.
5. Compare against the previous fixed-party baseline.

### Suggested experimental ladder

1. Phase 1 only (multi-party DR baseline).
2. + Phase 3 (self-imitation).
3. + Phase 4 (enriched observations).
4. + Phase 5 (entropy schedule and RND).
5. + Phase 2 (MC credit assignment) — most expensive, last.
6. + Phase 6 / Phase 7 (audit and evaluation harness).

### What to watch

- per-archetype damage and on-field share
- main-carry on-field fraction (slot with highest `self_enhancement` profile
  score)
- training-time SIL buffer turnover rate
- MC vs GAE advantage variance ratio
- entropy across training
- whether improvements transfer to held-out parties

---

## Risks

- Multi-party DR may slow per-party convergence; needs more total samples.
- MC credit assignment requires deep simulator snapshots; subtle bugs could
  leak state across episodes.
- Self-imitation on early-suboptimal trajectories can lock in bad behavior.
  Mitigate by gating SIL until base PPO surpasses a return threshold.
- RND intrinsic reward can hijack the agent into pure novelty seeking. Keep
  weight low.
- Permutation invariance changes may break checkpoint compatibility. Provide a
  migration path.
- Evaluation across many parties multiplies wall-clock cost.

---

## Follow-Up After This Plan

Only after multi-party generalization is verified should we revisit:

1. Population-Based Training or league-style training for further robustness.
2. Transformer-based recurrent core (GTrXL, Decision Transformer) replacing GRU.
3. Hierarchical / option-based control (deferred from prior plan).
4. Automatic party-pool generation (random valid 4-character draws over the
   full character registry).

These are powerful but expensive. They will be much easier to evaluate cleanly
once the foundations in this plan are in place.
