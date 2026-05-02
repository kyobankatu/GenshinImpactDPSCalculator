# Recurrent PPO Alignment Plan

## Goal

Bring the current RL learner closer to a standard recurrent PPO implementation.

The main gap today is not the presence of a recurrent policy itself. The policy
already carries hidden state across rollout steps. The gap is in the PPO update
path: training treats collected samples mostly as independent one-step examples
with cached recurrent input, instead of replaying contiguous sequences and
backpropagating through time across those sequences.

This plan focuses on fixing that gap with minimal protocol churn and minimal
risk to the Java rollout service.

---

## Current State

### What already exists

- Policy inference is recurrent.
- Rollout collection carries `hidden_states` forward per environment.
- Hidden state is reset on `done`.
- PPO uses action masking and a value head correctly at the step level.

### What is missing compared with standard recurrent PPO

- Collected data is flattened into per-step samples before optimization.
- PPO minibatches are shuffled as independent samples, not as sequences.
- Training reuses cached `recurrent_input` instead of replaying sequence steps
  through the recurrent core.
- No explicit sequence padding or sequence mask exists for PPO updates.
- No truncated BPTT style training window exists.

---

## Non-Goals

- Do not change the Java observation layout.
- Do not change action IDs or action-mask semantics.
- Do not change the rollout binary protocol unless a later phase proves it is
  necessary.
- Do not introduce Transformer-specific architecture changes in this task.

---

## Target End State

After this work, PPO updates should operate on contiguous sequence chunks.

Each training minibatch should contain:

- `observations`: shape `(batch, seq, obs_dim)`
- `action_masks`: shape `(batch, seq, action_dim)`
- `actions`: shape `(batch, seq)`
- `old_log_probs`: shape `(batch, seq)`
- `advantages`: shape `(batch, seq)`
- `returns`: shape `(batch, seq)`
- `initial_hidden`: shape `(batch, hidden_dim)`
- `loss_mask`: shape `(batch, seq)`

The recurrent model should be unrolled over the sequence during optimization,
with hidden state advanced internally step by step. Loss terms should only be
applied where `loss_mask == 1`.

---

## Implementation Plan

### Phase 1: Restructure rollout storage around sequences

**Files**
- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/recurrent_ppo.py`

**Changes**

- Replace the current flattened `samples` representation with a sequence-aware
  representation.
- Preserve per-environment temporal order throughout rollout collection.
- Store the initial hidden state for each contiguous training segment.
- Split segments on episode boundaries.
- Add a configurable sequence length for truncated BPTT, for example
  `seq_len` or `bptt_steps`.
- Chunk long per-environment segments into fixed-length windows while keeping
  order intact.

**Notes**

- This phase should not yet change model architecture.
- Keep GAE computation on full rollout segments before chunking into sequence
  windows.

**Acceptance criteria**

- Rollout collection still runs end to end.
- Sequence chunks are produced with stable shapes.
- Episode boundaries do not leak hidden state into the next episode.

---

### Phase 2: Add sequence forward pass to the policy

**Files**
- `src/python/rl/recurrent_ppo.py`

**Changes**

- Keep `forward_step()` for inference and evaluation.
- Add a sequence-oriented method such as `forward_sequence()`:
  - input: `(batch, seq, obs_dim)`, `(batch, hidden_dim)`,
    `(batch, seq, action_dim)`, optional reset mask
  - output: logits `(batch, seq, action_dim)`, values `(batch, seq)`,
    final hidden `(batch, hidden_dim)`, optional attention summaries
- Internally unroll the GRU step by step across the sequence dimension.
- Support per-step reset masking so that padded positions or explicit reset
  boundaries do not contaminate following states.

**Notes**

- The current character attention encoder can remain unchanged.
- `forward_step()` should be implemented in terms of the same core logic where
  practical to avoid divergence.

**Acceptance criteria**

- `forward_step()` behavior remains unchanged for inference use.
- `forward_sequence()` reproduces the same stepwise outputs when run on a known
  short sequence without shuffling.

---

### Phase 3: Change PPO optimization from step minibatches to sequence minibatches

**Files**
- `src/python/rl/train_recurrent_ppo.py`

**Changes**

- Replace `random.shuffle(samples)` with shuffling at the sequence-chunk level.
- Build minibatches from whole sequence chunks, not individual timesteps.
- Pad variable-length chunks within a minibatch to the local max sequence length.
- Add `loss_mask` so PPO loss, value loss, entropy, KL, and metrics ignore padded
  timesteps.
- Recompute logits and values by calling `policy.forward_sequence(...)`.
- Compute action log-probabilities over `(batch, seq)`.
- Reduce masked losses by valid timestep count, not by padded tensor size.

**Notes**

- This is the core phase that makes the training path actually recurrent.
- PPO clipping logic stays the same conceptually; only tensor layout changes.

**Acceptance criteria**

- Training completes with sequence minibatches and no shape mismatches.
- Loss values remain finite.
- Masked padding does not contribute to the objective.

---

### Phase 4: Make recurrent boundaries explicit

**Files**
- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/recurrent_ppo.py`

**Changes**

- Define exactly when hidden state is reset:
  - on environment `done`
  - at explicit sequence starts
  - optionally via a `sequence_reset_mask`
- Ensure bootstrap value computation uses the correct terminal handling.
- Audit evaluation code so deterministic and stochastic evaluation still reset
  hidden state correctly.

**Notes**

- This phase is mainly correctness and maintainability.
- It should remove ambiguity around whether a cached hidden state belongs to the
  current episode or an already-terminated one.

**Acceptance criteria**

- No hidden-state carryover across episodes.
- Evaluation path remains behaviorally consistent.

---

### Phase 5: Add observability for recurrent training quality

**Files**
- `src/python/rl/train_recurrent_ppo.py`

**Changes**

- Log sequence-specific metrics:
  - mean sequence length
  - max sequence length
  - valid timestep count
  - padding fraction
- Keep existing PPO metrics.
- Keep existing attention summaries if inexpensive.
- Optionally log gradient norm before clipping for diagnosis.

**Acceptance criteria**

- Training logs make it obvious whether sequence batching is working as intended.

---

## Proposed Config Additions

Add the following learner config fields:

- `sequence_length`
- `sequence_minibatch_size`
- `carry_bootstrap_across_rollout` if needed for clarity

Notes:

- `minibatch_size` currently means step count. After this change it should either
  be redefined clearly or replaced with a sequence-based name to avoid confusion.
- Keep defaults conservative for the debug preset.

---

## Verification Plan

### Minimum verification

1. Run `./gradlew build`
2. Run the Java rollout service through the existing local path.
3. Run a short learner smoke test with the debug preset and small update count.
4. Run `src/python/rl/evaluate_policy.py` against the produced checkpoint.

### Suggested smoke test shape

- small `envs`
- small `updates`
- short `sequence_length`
- CPU is acceptable

### What to watch

- no NaNs in policy/value loss
- no tensor shape errors
- invalid-action rate comparable to current baseline
- evaluation completes without hidden-state shape/reset issues

---

## Risks

- The biggest implementation risk is silent shape correctness with wrong masking.
- A second risk is making PPO numerically noisier if sequence batching becomes
  too small.
- Another risk is changing checkpoint compatibility if model or config metadata
  is renamed without a migration path.

---

## Follow-Up After This Plan

Only after the recurrent PPO update path is sequence-correct should we evaluate:

1. richer observation state
2. GRU vs LSTM comparison
3. lightweight causal Transformer or GTrXL style replacement

Without this groundwork, a Transformer comparison would be noisy and hard to
interpret because the training pipeline itself would still be underpowered.
