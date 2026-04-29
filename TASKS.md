# Generalizable Rotation Learning Tasks

## Goal

Build an RL agent that learns optimal rotations for arbitrary party compositions.
Characters are represented by capability vectors derived automatically from
structured simulation runs, not by identity or manual labels. The pipeline is
split into two independent learning phases:

1. **Capability Discovery**: characterize each character's role from simulation data.
2. **Rotation Optimization**: train an RL agent using the discovered capability vectors.

---

## Observation Layout (target: OBSERVATION_SIZE = 79)

This layout must be agreed upon and locked before any implementation begins.
Any deviation will silently break the Java↔Python protocol.

```
Total dims = NUM_CHARS * CHAR_FEATURE_SIZE + GLOBAL_SIZE
           = 4 * 18 + 7
           = 79

Per character block (18 dims, repeated for each slot in partyOrder):
  [0]     energy ratio            = currentEnergy / energyCost              (dynamic)
  [1]     isActive                = 1.0 if on field else 0.0                (dynamic)
  [2]     skill readiness         = 1 - clamp01(skillCDRemaining / skillCD) (dynamic)
  [3]     burst readiness         = 1 - clamp01(burstCDRemaining / burstCD) (dynamic)
  [4]     isBurstActive           = 1.0 if burst mode on else 0.0           (dynamic)
  [5]     active buff ratio       = clamp01(activeBuffCount / 6.0)          (dynamic)
  [6]     element: PYRO           = 1.0 if element == PYRO else 0.0         (static)
  [7]     element: HYDRO                                                     (static)
  [8]     element: ANEMO                                                     (static)
  [9]     element: ELECTRO                                                   (static)
  [10]    element: DENDRO                                                    (static)
  [11]    element: CRYO                                                      (static)
  [12]    element: GEO                                                       (static)
  [13]    element: PHYSICAL                                                  (static)
  [14]    off-field DPS ratio     (static, from capability profile)
  [15]    team buff score         (static, from capability profile)
  [16]    self-enhancement score  (static, from capability profile)
  [17]    energy generation score (static, from capability profile)

Global block (7 dims):
  [72]    swap readiness          = clamp01((now - lastSwapTime) / swapCooldown)
  [73]    time remaining ratio    = clamp01((maxTime - now) / maxTime)
  [74]    PYRO aura               = clamp01(auraUnits(PYRO) / 2.0)
  [75]    HYDRO aura
  [76]    ELECTRO aura
  [77]    ANEMO aura
  [78]    thundercloud active     = 1.0 if thundercloudEndTime > now else 0.0
```

Dynamic features [0-5] update every step.
Static features [6-17] are computed once at episode reset and held constant.

---

## Action Space (target: SIZE = 7, slot-based)

```
ID 0: ATTACK     - normal attack with active character
ID 1: SKILL      - elemental skill with active character
ID 2: BURST      - elemental burst with active character
ID 3: SWAP_SLOT_0 - swap to character at partyOrder[0]
ID 4: SWAP_SLOT_1 - swap to character at partyOrder[1]
ID 5: SWAP_SLOT_2 - swap to character at partyOrder[2]
ID 6: SWAP_SLOT_3 - swap to character at partyOrder[3]
```

SWAP_SLOT_X where partyOrder[X] == activeCharacter is masked invalid by ActionSpace.

---

## Policy Architecture

```
Input obs (79) is split:
  char_obs   : shape (batch, 4, 18)  -- first 72 dims reshaped
  global_obs : shape (batch, 7)      -- last 7 dims

--- CharacterEncoder (shared weights across all 4 slots) ---
  Linear(18, H) -> Tanh -> Linear(H, H) -> Tanh
  Input:  (batch * 4, 18)
  Output: (batch, 4, H)       [reshaped after forward]

--- GlobalEncoder ---
  Linear(7, H) -> Tanh
  Input:  (batch, 7)
  Output: (batch, H)

--- Attention (global queries character encodings) ---
  query  = Linear(H, H)(global_enc)          shape: (batch, 1, H)
  scores = softmax(query @ char_encs.T / sqrt(H))  shape: (batch, 1, 4)
  context = scores @ char_encs               shape: (batch, H)  [squeezed]

--- GRUCell ---
  input  = concat(context, global_enc)       shape: (batch, H*2)
  GRUCell(H*2, H)
  output = h_new                             shape: (batch, H)

--- Heads ---
  policy_head : Linear(H, 7) + action mask (-1e9 for invalid)
  value_head  : Linear(H, 1) -> squeeze

Constants:
  CHAR_FEATURE_SIZE  = 18
  GLOBAL_FEATURE_SIZE = 7
  NUM_CHARS          = 4
  H                  = hidden_size (hyperparameter, default 256)
  ACTION_SIZE        = 7
```

Checkpoint fields: `observation_size=79`, `hidden_size`, `action_size=7`,
`char_feature_size=18`, `global_feature_size=7`, `num_chars=4`, `state_dict`.

---

## Capability Profile Format

Profiles are stored in `config/capability_profiles/profiles.json`:

```json
{
  "FLINS": {
    "off_field_dps_ratio": 0.0,
    "team_buff_score": 0.0,
    "self_enhancement_score": 0.0,
    "energy_generation_score": 0.0
  },
  "INEFFA": { ... },
  "COLUMBINA": { ... },
  "SUCROSE": { ... }
}
```

All four scores are in [0.0, 1.0].
`CharacterId.name()` is used as the key.
Java loads this file at `ObservationEncoder` construction time.
If a character has no entry, all four scores default to 0.0 with a warning log.

---

## Plan

### Phase 0: Slot-based Action Space (Java prerequisite)

All subsequent phases require party-agnostic actions.
This phase has no dependencies and should be implemented first.

**Files changed:**
- `src/java/mechanics/rl/RLAction.java`
- `src/java/mechanics/rl/ActionSpace.java`
- `src/java/mechanics/rl/BattleEnvironment.java`
- `src/java/mechanics/rl/bridge/BatchProtocol.java` (version bump)

**Changes:**

`RLAction.java`:
- Remove: `SWAP_FLINS(3,...), SWAP_INEFFA(4,...), SWAP_COLUMBINA(5,...), SWAP_SUCROSE(6,...)`
- Add: `SWAP_SLOT_0(3,0), SWAP_SLOT_1(4,1), SWAP_SLOT_2(5,2), SWAP_SLOT_3(6,3)`
  where the second constructor arg is the slot index (int).
- Replace `getTargetCharacterId()` with `getTargetSlot()` returning int.
- `isSwap()` returns true for IDs 3-6.

`ActionSpace.java`:
- `fillMask()`: for each SWAP_SLOT_X action, resolve target via
  `sim.getCharacter(config.partyOrder[action.getTargetSlot()])`.
- Logic is otherwise identical to current implementation.

`BattleEnvironment.java`:
- `execute()`: resolve target CharacterId via
  `config.partyOrder[action.getTargetSlot()]` then call `sim.switchCharacter(id)`.

`BatchProtocol.java`:
- Increment `VERSION` by 1.

**Acceptance criteria:**
- `./gradlew build` passes.
- `./gradlew BenchmarkRLJava` produces valid output.
- Swapping behavior is identical to the current implementation when FlinsParty2 is used.

---

### Phase 1: Capability Discovery (Java + Python)

Automatically characterize each character's role by running structured simulation
experiments and deriving normalized capability scores.

#### 1a: Capability Profiler (Java)

Add `src/java/mechanics/rl/CapabilityProfiler.java`.

The profiler runs four types of controlled experiment per character,
each repeated `N_RUNS = 50` times and averaged for stability.

**Experiment templates** (each runs one 20-second episode):

```
Template A — "Full presence"
  Character X stays on field for all 20s.
  Whenever skill CD allows: use SKILL.
  Whenever burst CD and energy allow: use BURST.
  Otherwise: ATTACK.
  Record: X's total damage dealt.

Template B — "Passive presence"
  At t=0: X uses SKILL then BURST (if available), then immediately swaps off field.
  A fixed "dummy" character (slot 0, always same character) stays on field.
  Record: X's total damage dealt while off field over 20s.

Template C — "Team with X buffing"
  Fixed primary attacker (slot 0) is the on-field attacker for almost the full run.
  At t=0:
    1. swap to X if needed
    2. X uses SKILL
    3. X uses BURST if available
    4. immediately swap back to primary attacker
  For the rest of the 20s episode:
    - primary attacker stays on field
    - primary attacker uses ATTACK repeatedly
    - X takes no further explicit actions
  Record: primary attacker's total damage over 20s.

Template D — "Team without X buffing"
  Same simulator, same primary attacker, same 20s attack script as Template C.
  Difference:
    - X performs no actions at t=0
    - if X is not the primary attacker, immediately ensure the primary attacker is on field
  Record: primary attacker's total damage over 20s.
  (Baseline for buff measurement)

Template E — "Self with burst"
  X stays on field for 20s, uses BURST immediately at start, then ATTACKs.
  Record: X's total damage over 20s.

Template F — "Self without burst"
  X stays on field for 20s, never uses BURST, only SKILLs and ATTACKs.
  Record: X's total damage over 20s.

Template G — "Energy counting"
  X uses SKILL once from full CD, then idles.
  Run for 5s only (to capture particle travel time).
  Record: particle energy received by X after ER multiplier and before burst spend.
  Do not include flat energy refunds, initial filled energy, or burst reset effects.
```

**Score computation** (averaged over N_RUNS each):

```
Let A  = mean(Template A total damage by X)
Let B  = mean(Template B total damage by X off field)
Let C  = mean(Template C total damage by primary)
Let D  = mean(Template D total damage by primary)
Let E  = mean(Template E total damage by X)
Let F  = mean(Template F total damage by X)
Let G  = mean(Template G ER-scaled particle energy received by X)
Let max_energy_cost = max(character.getEnergyCost()) across all party members

off_field_dps_ratio     = clamp01(B / max(A, 1.0))
team_buff_score         = clamp01((C - D) / max(D, 1.0))
self_enhancement_score  = clamp01((E - F) / max(F, 1.0))
energy_generation_score = clamp01(G / max(max_energy_cost, 1.0))
```

The profiler requires a `Supplier<CombatSimulator>` (same as `BattleEnvironment`)
and the `EpisodeConfig.partyOrder` to know which character is being profiled and
which is the fixed primary attacker (partyOrder[0] is the primary for Templates C/D).

The profiler also requires two small measurement hooks in the simulator/runtime:

1. Source-attributed damage lookup
   - The profiler must be able to query cumulative damage by source name
     in addition to total damage.
   - Minimum required API:
     - `DamageReport#getDamageBySource(String sourceName)` or equivalent
     - optional read-only map snapshot for debugging
   - Character source names must match the names used by normal simulation damage logging.

2. ER-scaled particle-energy lookup
   - The profiler must be able to query particle energy received after ER scaling,
     excluding flat energy.
   - Existing `getTotalParticleEnergy()` is not sufficient because it stores the
     pre-ER base particle amount.
   - Add a dedicated accessor such as
     `Character#getTotalScaledParticleEnergy()` or equivalent.

**Output:** writes `config/capability_profiles/profiles.json` using `CharacterId.name()` as keys.

Add entry point `src/java/sample/ProfileCharacterCapabilities.java`:
```java
public static void main(String[] args) throws Exception {
    CapabilityProfiler profiler = new CapabilityProfiler(
        FlinsParty2RLSimulatorFactory.supplier(), new EpisodeConfig());
    profiler.runAll();
    profiler.writeJson("config/capability_profiles/profiles.json");
}
```

Add Gradle task `ProfileCapabilities` following the same dynamic-task pattern.

**Acceptance criteria:**
- `./gradlew ProfileCapabilities` runs without error and writes `profiles.json`.
- All four scores for all four characters are in [0.0, 1.0].
- Sucrose `team_buff_score` is higher than Flins `team_buff_score`.
- Columbina `off_field_dps_ratio` is higher than Sucrose `off_field_dps_ratio`.
- `self_enhancement_score` is higher for burst-dependent characters.
- Profiler console output includes per-template mean and standard deviation for manual sanity checks.

#### 1b: ObservationEncoder update (Java)

Extend `ObservationEncoder` to load capability profiles and append static features.

**Files changed:**
- `src/java/mechanics/rl/ObservationEncoder.java`
- `src/java/mechanics/rl/bridge/BatchProtocol.java` (version bump for new obs size)

**Changes:**

Add inner class `CapabilityProfileStore`:
- Loads `profiles.json` from disk on construction.
- `getProfile(CharacterId id)` returns a `double[4]` of
  `[off_field_ratio, team_buff_score, self_enhancement_score, energy_gen_score]`,
  defaulting to `[0,0,0,0]` if not found.
 - Profile file path should be injectable, with
   `config/capability_profiles/profiles.json` as the default.

Update constants:
```java
public static final int CHAR_DYNAMIC_FEATURES = 6;
public static final int CHAR_STATIC_FEATURES  = 12;  // 8 element + 4 capability
public static final int FEATURES_PER_CHARACTER = CHAR_DYNAMIC_FEATURES + CHAR_STATIC_FEATURES; // 18
public static final int GLOBAL_FEATURES        = 7;
public static final int OBSERVATION_SIZE       = FEATURES_PER_CHARACTER * 4 + GLOBAL_FEATURES; // 79
```

`fillObservation()` per-character loop: after the existing 6 dynamic features,
append element one-hot (8 dims from `Element.values()` order) and the 4 capability
scores from `CapabilityProfileStore`.

`ObservationEncoder` constructor: accept optional `CapabilityProfileStore` parameter;
if null, use a zero-filled fallback (for tests and Phase 0 compatibility).

`BattleEnvironment`:
- Add constructor overload(s) so an `ObservationEncoder` can be injected explicitly.
- Keep the existing convenience constructor by wiring it to the default profiles path.

`RolloutService`:
- Construct and own one `CapabilityProfileStore` (or one preconfigured `ObservationEncoder`)
  and pass it into all created `BattleEnvironment` instances.
- Do not let each environment re-read `profiles.json` on every reset.

**Acceptance criteria:**
- `OBSERVATION_SIZE == 79` at compile time.
- `BatchProtocol.VERSION` is incremented.
- `./gradlew build` passes.
- Element one-hot sums to exactly 1.0 per character slot.
- Capability scores match `profiles.json` values when profiles file exists.
- Missing profile entries emit at most one warning per character ID per process.

---

### Phase 2: Policy Architecture (Python)

Replace the flat encoder with a character-slot-aware architecture.
This phase depends on Phase 1 (OBSERVATION_SIZE must be 79 before changing the model).

**Files changed:**
- `src/python/rl/recurrent_ppo.py`

**Architecture** (see detailed spec in the header section above).

Key implementation notes:

```python
CHAR_FEATURE_SIZE   = 18
GLOBAL_FEATURE_SIZE = 7
NUM_CHARS           = 4
# OBSERVATION_SIZE  = CHAR_FEATURE_SIZE * NUM_CHARS + GLOBAL_FEATURE_SIZE = 79

class RecurrentPolicy(nn.Module):
    def __init__(self, observation_size, hidden_size, action_size,
                 char_feature_size=CHAR_FEATURE_SIZE,
                 global_feature_size=GLOBAL_FEATURE_SIZE,
                 num_chars=NUM_CHARS):
        ...
        # Shared encoder: processes one character slot at a time
        self.char_encoder = nn.Sequential(
            nn.Linear(char_feature_size, hidden_size),
            nn.Tanh(),
            nn.Linear(hidden_size, hidden_size),
            nn.Tanh(),
        )
        # Global encoder
        self.global_encoder = nn.Sequential(
            nn.Linear(global_feature_size, hidden_size),
            nn.Tanh(),
        )
        # Attention
        self.attention_query = nn.Linear(hidden_size, hidden_size)
        self.attention_scale = hidden_size ** -0.5
        # Recurrent: input is context(H) + global(H) = 2H
        self.recurrent = nn.GRUCell(hidden_size * 2, hidden_size)
        self.policy_head = nn.Linear(hidden_size, action_size)
        self.value_head  = nn.Linear(hidden_size, 1)

    def _split_obs(self, obs):
        char_flat  = obs[:, :self.num_chars * self.char_feature_size]
        global_obs = obs[:, self.num_chars * self.char_feature_size:]
        char_obs   = char_flat.view(-1, self.num_chars, self.char_feature_size)
        return char_obs, global_obs

    def forward_step(self, obs, h, mask):
        char_obs, global_obs = self._split_obs(obs)
        batch = char_obs.shape[0]

        # Shared encoder across all slots
        char_encs = self.char_encoder(
            char_obs.view(batch * self.num_chars, self.char_feature_size)
        ).view(batch, self.num_chars, -1)          # (batch, 4, H)

        global_enc = self.global_encoder(global_obs)   # (batch, H)

        # Attention: global queries characters
        query  = self.attention_query(global_enc).unsqueeze(1)   # (batch, 1, H)
        scores = torch.softmax(
            torch.bmm(query, char_encs.transpose(1, 2)) * self.attention_scale,
            dim=-1)                                               # (batch, 1, 4)
        context = torch.bmm(scores, char_encs).squeeze(1)        # (batch, H)

        gru_input = torch.cat([context, global_enc], dim=-1)     # (batch, 2H)
        h_new = self.recurrent(gru_input, h)                     # (batch, H)

        logits = self.policy_head(h_new)
        masked_logits = logits.masked_fill(mask < 0.5, -1.0e9)
        value  = self.value_head(h_new).squeeze(-1)
        return masked_logits, value, h_new
```

`save()` must persist `char_feature_size`, `global_feature_size`, `num_chars`
in the checkpoint payload so `load()` can reconstruct the architecture exactly.

`compute_advantages()` in `recurrent_ppo.py` is unchanged.

**Acceptance criteria:**
- A `--preset debug` training run completes without error.
- `policy.save()` / `RecurrentPolicy.load()` round-trips cleanly.
- `policy.hidden_size` attribute exists (used by training loop).
- Attention scores (4 values per step) sum to 1.0.

---

### Phase 3: Multi-Party Training Support (Java)

Enable training on multiple party compositions per run so the model cannot
overfit to FlinsParty-specific patterns.

**Files added:**
- `src/java/mechanics/rl/MultiPartyRLSimulatorFactory.java`
- Additional factory (e.g. `RaidenPartyRLSimulatorFactory.java`) for at least
  one second party.

**Files changed:**
- `src/java/mechanics/rl/bridge/RolloutService.java`
- `src/java/mechanics/rl/bridge/VectorizedEnvironment.java` (only if constructor wiring requires it)
- `src/java/sample/ServeRLJava.java` (new flag to enable multi-party mode)
- `execute.sh` (new env var `USE_MULTI_PARTY`, default false)

`MultiPartyRLSimulatorFactory`:
- Holds a list of `Supplier<CombatSimulator>` factories.
- Each call to `get()` randomly selects one factory uniformly.
- Uses `ThreadLocalRandom` for thread safety.

`RolloutService`:
- Must no longer hardcode `FlinsParty2RLSimulatorFactory.supplier()`.
- Accept a `Supplier<CombatSimulator>` in its constructor, or accept a higher-level
  environment factory that captures both simulator supplier and observation setup.
- `ServeRLJava` selects between single-party and multi-party suppliers and passes
  the chosen supplier into `RolloutService`.

`ObservationEncoder` with `CapabilityProfileStore`:
- Must support all characters across all registered parties.
- `profiles.json` is re-generated by `ProfileCapabilities` before training
  if a new party is added.

**Acceptance criteria:**
- Two distinct parties produce different capability vectors in observation.
- `./gradlew BenchmarkRLJava` runs without error in multi-party mode.
- A short `--preset debug` training run cycles through both parties.
- Service logs identify which party was sampled for at least one debug/diagnostic path.

---

### Phase 4: Full Training Run and Evaluation

Run a full training session with all phases in place.

**Training configuration** (`execute.sh full` profile):
- `USE_MULTI_PARTY=true`
- `TRAIN_ENTROPY_COEFFICIENT=0.03` (start value; do not decay below 0.01)
- `TRAIN_ENTROPY_FINAL_COEFFICIENT=0.01`
- `TRAIN_LEARNING_RATE=0.0003`
- `TRAIN_UPDATES=4000`

**W&B metrics to track:**
- `train/entropy`: must stay above 0.01 for the first 1000 updates.
- `eval_det/damage` per party: compare against FlinsParty baseline (3.04M).
- `eval_det/action_fraction_1` (SKILL) + `eval_det/action_fraction_2` (BURST):
  combined target > 25%.
- Attention weight distribution (optional): log mean attention score per slot
  to verify the model is not ignoring some character slots.

Evaluation plumbing required before this phase:
- Java evaluation/reset path must expose which party factory was sampled.
- Python evaluation summary must aggregate metrics by `party_id` / `party_name`,
  not only as one merged scalar across all episodes.
- If deterministic evaluation uses a single episode, run one episode per registered party explicitly.

**Acceptance criteria:**
- Entropy stays above 0.01 for at least the first 1000 updates.
- FlinsParty eval damage meets or exceeds the previous peak of 3.04M / 20s.
- SKILL + BURST action fraction exceeds 25% in deterministic eval.
- At least one non-FlinsParty eval produces Skill + Burst usage > 20%.

---

## Implementation Order and Dependencies

```
Phase 0 (slot actions)
  └── Phase 1a (capability profiler, Java)
        └── Phase 1b (ObservationEncoder, Java)  ← OBSERVATION_SIZE locked at 79
              └── Phase 2 (architecture, Python)
                    └── Phase 3 (multi-party, Java)
                          └── Phase 4 (full training)
```

Phase 0 and Phase 1a can be worked on in parallel.
Phase 2 (Python) can be designed and unit-tested against a mock obs of size 79
before Phase 1b is complete, but cannot be run end-to-end until Phase 1b is done.

---

## Risk Register

| Risk | Impact | Mitigation |
|---|---|---|
| Capability profiler gives noisy scores (N=50 too few) | Wrong static features mislead agent | Increase N; print std dev alongside mean; inspect JSON manually |
| team_buff_score near 0 for all characters | Agent sees no buff signal | Check Templates C/D; ensure primary attacker deals significant damage |
| Template semantics drift between implementers | Inconsistent profiler output | Keep template scripts explicit and deterministic in code comments and sample output |
| Damage attribution names do not match character IDs | Profiler reads wrong source totals | Centralize source-name mapping and assert all profiled characters have a readable damage entry |
| Particle-energy metric accidentally includes flat energy or prefill | Wrong energy_generation_score | Add a dedicated ER-scaled particle-energy counter and verify it in Template G |
| OBSERVATION_SIZE mismatch between Java and Python | Protocol error at HELLO | Define OBSERVATION_SIZE as a compile-time constant; bump BatchProtocol.VERSION on every change |
| GRUCell input size change (H→2H) breaks old checkpoints | Cannot resume | Expected and documented; Phase 4 always starts from scratch |
| Shared char encoder learns party-specific quirks | No generalization | Verify with second party in Phase 3; check attention weights |
| Entropy collapses below 0.01 again | Normal spam resumes | Hard-floor entropy_final_coefficient at 0.01 in execute.sh |
