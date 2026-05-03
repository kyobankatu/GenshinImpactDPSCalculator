# General Rotation RL Upgrade Plan

## Goal

Build a more general rotation-optimization RL stack that can learn when to stay
on field, when to swap, and when to consume setup value, without relying on
character-specific reward shaping.

The core problem today is not only exploration. The larger issue is that the
policy must infer long-horizon combat value from a partially observed state,
while many decisive simulator facts remain implicit:

- active carry windows
- deployed off-field payloads
- follow-up opportunities
- swap timing value
- future payoff of current setup

This plan upgrades the learner in stages so that it remains a generic rotation
optimizer instead of turning into a hand-scripted character policy engine.

---

## Current State

### What already exists

- Java owns the combat simulation and rollout execution.
- Python owns recurrent PPO training and evaluation.
- The policy already receives recurrent history.
- Capability profiles already provide static role hints such as:
  - off-field DPS ratio
  - team buff score
  - self-enhancement score
  - energy generation score

### What is missing for robust generic rotation learning

- The actor cannot directly infer many temporally extended combat states.
- Critic training does not exploit simulator-internal privileged state.
- Static capability profiles do not capture current live payoff.
- The action space is still mostly one-step primitive control.
- The learner has no abstract notion of:
  - on-field commitment value
  - off-field payload remaining
  - swap opportunity value

---

## Non-Goals

- Do not add character-specific reward bonuses.
- Do not hardcode role rules such as "character X must drive".
- Do not replace the simulator with scripted rotation logic.
- Do not require inference-time access to character-specific hidden flags.
- Do not introduce long fixed macro-actions that lock the agent for too long.

---

## Target End State

After this work, the system should train a policy that:

- executes from generic observation and recurrent history alone
- benefits during training from simulator-internal privileged state
- reasons about generic combat abstractions rather than character names
- can optionally choose short temporally extended actions
- generalizes across party archetypes without reward redesign

The intended abstraction boundary is:

- `actor`: generic public observation only
- `critic`: public observation plus privileged simulator state during training
- `auxiliary heads`: predict generic latent combat-value signals
- `options / short skills`: reusable sequence abstractions with learned stopping

---

## Execution Strategy

Implementation and evaluation should proceed incrementally.

The default rollout plan is:

1. implement Phase 1 through Phase 3
2. run training and compare against the current flat PPO baseline
3. implement Phase 4 and repeat training/evaluation
4. implement Phase 5 and repeat training/evaluation
5. defer Phase 6 unless Phase 1 through Phase 5 still leave a clear flat-policy
   limitation

Phase 6 is intentionally not part of the first implementation wave.

Reasons:

- Phase 3 through Phase 5 improve learning signal and state representation
  without changing the control abstraction too aggressively.
- Phase 6 changes the action hierarchy and is the most invasive phase.
- If Phase 1 through Phase 5 already fix the main stay-vs-swap failures, then
  Phase 6 can remain optional.

The first concrete milestone is therefore:

- complete Phase 1 through Phase 5
- verify the learner runs end to end
- compare behavior before deciding whether options are still needed

---

## Implementation Plan

### Phase 1: Define generic privileged combat state

**Files**
- `src/java/mechanics/rl/`
- `src/python/rl/`

**Changes**

- Define a privileged state interface for training-time use only.
- Keep all features generic and slot-based rather than character-based.
- Start with compact abstract features such as:
  - `on_field_window_remaining[slot]`
  - `off_field_payload_remaining[slot]`
  - `followup_opportunity_score[slot]`
  - `major_action_ready_score[slot]`
  - `swap_in_value_score[slot]`
  - `reaction_potential_score`
  - `team_setup_value_score`

**Examples**

- A self-buff form with 8 s remaining contributes to
  `on_field_window_remaining` for that slot.
- A summoned turret or field with 12 s remaining contributes to
  `off_field_payload_remaining`.
- A temporary enhanced burst state contributes to
  `followup_opportunity_score`.

**Acceptance criteria**

- The feature list contains no character names.
- Every feature can be computed for any party member slot.
- The representation is meaningful for both time-limited and non-time-limited
  carries.

---

### Phase 2: Add a Java-side privileged state encoder

**Files**
- `src/java/mechanics/rl/PrivilegedStateEncoder.java` (new)
- `src/java/mechanics/rl/BattleEnvironment.java`
- related simulator/model classes as needed

**Changes**

- Implement a separate encoder for critic-only privileged state.
- Do not merge this into the actor observation path.
- Expose the privileged vector through reset/step results for training.
- Keep the encoder generic:
  - no `if character == Flins`
  - use interfaces, timers, deployed effects, and action-state metadata

**Examples**

- Characters with active on-field-enhancing states implement a shared capability
  that reports a remaining-value proxy.
- Persistent off-field effects register a generic remaining-duration or
  remaining-uses value.

**Acceptance criteria**

- The rollout environment can emit both:
  - public actor observation
  - privileged critic observation
- Existing evaluation can still run without privileged input if needed.

---

### Phase 3: Convert PPO to asymmetric actor-critic

**Files**
- `src/python/rl/recurrent_ppo.py`
- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/rollout_service_client.py`

**Changes**

- Keep the actor input unchanged: public observation only.
- Extend the critic to consume public observation plus privileged state.
- Store privileged observations in rollout buffers.
- Ensure checkpoint loading and evaluation paths remain backward-compatible
  where practical.

**Notes**

- This is the lowest-risk way to use simulator truth without leaking it into the
  deployed policy.
- This is the first major change likely to improve stay-vs-swap judgment.

**Acceptance criteria**

- Training runs end to end with asymmetric inputs.
- The actor still functions from public observation only.
- The critic loss remains numerically stable.

---

### Phase 4: Add auxiliary latent-value prediction tasks

**Files**
- `src/python/rl/recurrent_ppo.py`
- `src/python/rl/train_recurrent_ppo.py`

**Changes**

- Add auxiliary heads on top of the recurrent trunk.
- Train them to predict selected privileged targets from history alone.
- Initial targets should remain generic:
  - `pred_on_field_commitment_score`
  - `pred_off_field_payload_remaining`
  - `pred_swap_out_opportunity_score`
  - `pred_team_setup_value_score`

**Examples**

- Given public history only, the model learns that the active slot likely still
  has 3-4 high-value actions left.
- Given prior setup actions, the model predicts that a different slot now has
  high swap-in value.

**Acceptance criteria**

- Auxiliary losses decrease during training.
- PPO reward definition remains unchanged.
- Actor behavior becomes less dependent on lucky recurrent memorization.

---

### Phase 5: Refresh capability profiling around value curves

**Files**
- `src/java/mechanics/rl/CapabilityProfiler.java`
- `config/capability_profiles/`
- `src/java/mechanics/rl/ObservationEncoder.java`

**Changes**

- Keep existing role-style profile data if still useful.
- Add new generic profile dimensions that describe value over time rather than
  only coarse role identity.
- Candidate additions:
  - `entry_value_score`
  - `sustain_value_3_actions`
  - `sustain_value_6_actions`
  - `exit_cost_score`
  - `reentry_cost_score`

**Examples**

- A time-limited carry may have high entry value and high short-horizon sustain.
- A frontloaded support may have high entry value and low sustain.
- A setup-heavy carry may have high reentry cost, encouraging longer commitment.

**Acceptance criteria**

- New profile fields remain character-agnostic in meaning.
- Profiles can describe both:
  - time-window carries
  - resource/sequence-limited carries

---

### Phase 6: Introduce short generic options above primitive actions

**Files**
- `src/java/mechanics/rl/ActionSpace.java`
- `src/java/mechanics/rl/RLAction.java`
- `src/python/rl/recurrent_ppo.py`
- `src/python/rl/train_recurrent_ppo.py`

**Changes**

- Keep primitive actions available.
- Add a small set of generic short options with learned termination.
- Start with short horizon options only, roughly 2-6 primitive actions.
- Candidate generic options:
  - `SETUP_OPTION`
  - `DRIVE_OPTION`
  - `CASHOUT_OPTION`
  - `REFRESH_OPTION`

**Examples**

- `SETUP_OPTION` tends to prioritize available skill/burst setup actions.
- `DRIVE_OPTION` tends to continue high-value on-field action chains.
- `REFRESH_OPTION` tends to rotate into slots with high swap-in value.

**Notes**

- Do not introduce long fixed macro-actions.
- Do not remove primitive control during the first rollout.

**Acceptance criteria**

- The policy can still fall back to primitive actions.
- Learned options terminate adaptively.
- Option usage improves credit assignment without locking the agent into bad
  long commitments.

**Execution note**

- Do not start this phase until Phase 1 through Phase 5 have been implemented
  and evaluated.
- Treat this as a follow-up phase, not part of the initial rollout.

---

### Phase 7: Seed option discovery from scripted rotations

**Files**
- `src/java/sample/`
- `src/python/rl/`
- optional tooling under `scripts/` or `src/python/rl/`

**Changes**

- Parse existing scripted sample rotations into short action subsequences.
- Use frequent subsequences as initialization candidates for options or sequence
  tokens.
- Use demonstrations only as prior structure, not as mandatory imitation target.

**Examples**

- `swap -> skill -> swap`
- `skill -> burst`
- `normal x3 -> burst`

**Notes**

- This phase should improve sample efficiency without hardwiring any one party's
  rotation.
- Token extraction should remain generic across parties.

**Acceptance criteria**

- Option seeds come from reusable local motifs, not full-party scripts.
- The learner can deviate from seeded sequences when reward supports it.

---

### Phase 8: Evaluate generalization across party archetypes

**Files**
- `src/java/mechanics/rl/RLPartyRegistry.java`
- `src/python/rl/train_recurrent_ppo.py`
- evaluation/report tooling

**Changes**

- Test on multiple archetypes, not just one carry pattern.
- Compare:
  - flat PPO baseline
  - asymmetric critic only
  - asymmetric critic plus auxiliary heads
  - asymmetric critic plus auxiliary heads plus options

**Party archetypes to cover**

- time-limited self-buff carries
- frontloaded setup carries
- sustain carries with low explicit timers
- off-field driver teams

**Acceptance criteria**

- Improvements are not limited to one named character or one party.
- The final design does not require per-party reward changes.

---

## Proposed Config Additions

- `use_privileged_critic`
- `privileged_obs_dim`
- `auxiliary_prediction_weight`
- `use_value_curve_profiles`
- `use_options`
- `option_max_length`
- `option_termination_enabled`
- `option_seed_from_scripts`

Notes:

- Keep defaults conservative.
- Feature flags should allow ablations per phase.

---

## Verification Plan

### Minimum verification

1. Run `./gradlew build`
2. Run `./gradlew ProfileCapabilities`
3. Run a short single-party learner smoke test
4. Run `src/python/rl/evaluate_policy.py` on the produced checkpoint
5. Compare HTML report behavior before and after each phase

### Suggested experimental ladder

1. Baseline flat PPO
2. Add privileged critic only
3. Add auxiliary prediction heads
4. Add refreshed capability profiles
5. Add short options
6. Add script-seeded option initialization

### Recommended first rollout boundary

The first implementation wave should stop after:

1. Phase 1 through Phase 3, or
2. preferably Phase 1 through Phase 5

Then evaluate whether the remaining errors are truly due to lack of temporal
abstraction before starting Phase 6.

### What to watch

- reward and damage stability
- invalid-action rate
- swap frequency
- average on-field commitment length by slot
- entropy collapse after adding options
- whether improvements hold across multiple parties

---

## Risks

- Privileged features may accidentally leak character-specific logic if encoded
  carelessly.
- Too many abstract features may make critic training noisy rather than helpful.
- Poorly designed options may collapse into trivial action repetition.
- Script-seeded skills may overfit to existing samples if the seed vocabulary is
  too rigid.
- Backward compatibility for checkpoints and rollout protocol may need a
  migration path.

---

## Follow-Up After This Plan

Only after asymmetric training and short option support are working should we
evaluate:

1. stronger recurrent cores such as LSTM or GTrXL
2. learned termination regularization
3. more advanced skill discovery or tokenization pipelines
4. broader multi-party curriculum design

Without this groundwork, architecture upgrades alone will be difficult to
interpret because the learner will still be missing the right abstraction layer.
