# Elemental Reaction Implementation Plan

## Scope

This simulator targets a single enemy that does not attack back. The goal is to measure a party's maximum offensive output over time, not to fully emulate open-world combat, enemy AI, exploration, or survival pressure.

Therefore reaction work should prioritize:

- damage-affecting reactions and buffs
- aura state, gauge consumption, ICD interaction, and timing
- reaction ownership and stat snapshot rules
- report and RL compatibility

Lower priority:

- enemy immobilization value, unless it changes damage through Shatter or aura state
- player damage taken, healing prevention, or defensive shield durability
- exploration systems such as Phlogiston movement
- multi-target geometry and target positioning

## Current Implementation Summary

Implemented or partially implemented:

- Vaporize and Melt amplifying multipliers
- Swirl damage in a simplified form
- Overload in a simplified form
- Electro-Charged in a simplified form
- Lunar-Charged Thundercloud in a custom simplified form
- Lunar reaction labels and custom Lunar damage stats

Known gaps:

- Dendro reaction family is mostly absent.
- Freeze, Shatter, Superconduct, and Crystallize are absent or enum-only.
- Transformative reaction base multipliers are outdated or incomplete.
- Aura state is too simple for Quicken, Burning, Freeze, Dendro Cores, and Lunar variants.
- Reaction damage ownership and reaction-specific bonuses need a consistent model before expanding RL parties.

## Implementation Order

### Phase 1: Reaction Core Cleanup

Target files:

- `src/java/mechanics/reaction/ReactionCalculator.java`
- `src/java/mechanics/reaction/ReactionResult.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/model/entity/Enemy.java`

Tasks:

- Define the supported reaction list explicitly.
- Remove ambiguity between enum-only reactions and actually computed reactions.
- Add reaction metadata needed by the resolver:
  - reaction element
  - aura consumption element(s)
  - whether damage is immediate, periodic, delayed, or state-only
  - whether the reaction can crit
  - whether the reaction uses transformative, additive, amplifying, or Lunar formula rules
- Centralize transformative reaction base multipliers and update existing values.
- Make `CombatActionResolver#getTransformativeReactionElement` reaction-specific instead of returning Pyro for most non-EC reactions.

Acceptance criteria:

- Existing `RaidenParty` and `FlinsParty2` still run.
- `ReactionResult.Kind` only implies behavior when the calculator or scheduler supports it.
- No string reaction names are used as control-flow keys.

Verification:

- `./gradlew build`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`

### Phase 2: Superconduct

Why second:

Superconduct is simple and damage-relevant for Physical teams. It also exercises Cryo/Electro reaction support without requiring new persistent reaction objects.

Tasks:

- Add Cryo + Electro detection.
- Add transformative Cryo reaction damage.
- Apply Physical RES shred debuff with correct duration and stacking behavior.
- Add aura consumption rules.
- Add report labeling.

Acceptance criteria:

- Superconduct deals reaction damage.
- Enemy Physical RES is reduced for subsequent Physical damage.
- Re-triggering refreshes duration without stacking incorrectly.

### Phase 3: Freeze and Shatter

Why here:

Freeze itself does not directly increase damage, but it changes aura state and enables Shatter. It should be added before Dendro because it requires explicit non-element aura state handling.

Tasks:

- Add Freeze Aura state to enemy or runtime reaction state.
- Add Hydro + Cryo reaction handling.
- Model Freeze duration from applied gauge in a simplified but documented way.
- Add Shatter trigger support for Geo and blunt attacks.
- Add Shatter Physical transformative damage.
- Remove Freeze Aura on Shatter.

Simplification allowed:

- Since the enemy does not attack, Freeze immobilization has no tactical effect. Only aura state and Shatter matter.

Acceptance criteria:

- Hydro/Cryo creates Freeze state.
- Geo or blunt-tagged attacks can trigger Shatter.
- Freeze state appears in reports/debug output where useful.

### Phase 4: Standard Crystallize

Why here:

Crystallize is low offensive priority for this simulator, but it is a prerequisite for Lunar-Crystallize and Geo/Hydro Lunar teams.

Tasks:

- Add Geo + Pyro/Hydro/Electro/Cryo reaction handling.
- Add related element metadata for generated shard type.
- Model shard generation as a reaction event.
- Do not prioritize shield HP unless a future offensive mechanic depends on active Crystallize shields.
- Add same-target Crystallize cooldown if it affects repeated reaction triggers.

Simplification allowed:

- For maximum DPS on a non-attacking enemy, Crystallize shield absorption can be omitted initially.

Acceptance criteria:

- Standard Crystallize triggers and logs correctly.
- Hydro Crystallize can be distinguished for Lunar-Crystallize conversion later.

### Phase 5: Burning

Why before Bloom/Quicken:

Burning introduces persistent aura-like reaction state and periodic damage. It is the simplest Dendro persistent reaction.

Tasks:

- Add Dendro + Pyro detection.
- Add Burning Aura state.
- Schedule Pyro periodic damage.
- Consume and refresh Dendro/Pyro aura according to a simplified documented rule.
- Assign damage ownership to the trigger character.

Acceptance criteria:

- Burning starts, ticks, refreshes, and expires.
- It coexists predictably with later Dendro reactions.

### Phase 6: Bloom Core System - Done

Why before Hyperbloom/Burgeon/Lunar-Bloom:

Bloom creates Dendro Cores, and both Hyperbloom and Burgeon depend on those cores. Lunar-Bloom also starts from this path.

Tasks:

- Add Hydro + Dendro detection.
- Create Dendro Core entities with:
  - owner
  - creation time
  - expiry time
  - max active core count policy
- Schedule delayed Bloom explosions.
- Add Bloom transformative Dendro damage.
- Add core replacement/explosion behavior when the core limit is exceeded.

Simplification allowed:

- Single enemy means core position can be abstracted away.

Acceptance criteria:

- Bloom creates delayed damage.
- Damage ownership and EM scaling use the trigger character.
- Reports show Bloom core explosions separately from direct hits.

Implementation status:

- Implemented Dendro Core runtime state, snapshot/restore support, 6 s expiry, and 5-core cap with oldest-core explosion.
- Added Bloom Dendro transformative damage and regression coverage.

### Phase 7: Hyperbloom and Burgeon - Done

Tasks:

- Add Electro interaction with existing Dendro Cores.
- Add Hyperbloom single-target Dendro damage.
- Add Pyro interaction with existing Dendro Cores.
- Add Burgeon AoE Dendro damage.
- Remove consumed cores.
- Add reaction-specific bonuses.

Simplification allowed:

- For a single enemy, Hyperbloom projectile travel and Burgeon AoE radius can be treated as immediate damage to the enemy.

Acceptance criteria:

- Electro and Pyro can consume cores before normal expiry.
- Hyperbloom and Burgeon damage use the consuming trigger character's level/EM/bonuses.

Implementation status:

- Implemented Electro/Pyro Dendro Core consumption as immediate Hyperbloom/Burgeon Dendro transformative damage in the single-target abstraction.
- Added regression coverage for both reactions.

### Phase 8: Quicken, Aggravate, and Spread - Done

Why after Bloom:

Quicken is a persistent target state with additive damage follow-ups. It needs more careful aura handling than immediate reactions.

Tasks:

- Add Quicken Aura state from Dendro + Electro.
- Add Quicken duration/decay.
- Add Aggravate for Electro hits against Quickened enemies.
- Add Spread for Dendro hits against Quickened enemies.
- Implement additive reaction damage bonus that enters the normal damage formula before DMG Bonus/Crit/RES.
- Ensure Aggravate/Spread can crit and benefit from DMG bonuses.

Acceptance criteria:

- Quicken itself does no direct damage.
- Aggravate and Spread increase the triggering hit's damage, not a separate transformative tick.
- Existing direct hit logging can show the additive reaction contribution.

Implementation status:

- Implemented Quicken as persistent target state using `min(origin gauge, trigger gauge) * 5 + 6` duration.
- Implemented Aggravate and Spread as additive base damage inserted before DMG Bonus/Crit/DEF/RES.
- Added direct-hit log reaction labels and regression coverage for Quicken creation, additive scaling, and expiry.

### Phase 9: Lunar Reaction Generalization - Done

Why after standard reactions:

Lunar reactions are conversions or overlays on standard reaction families. They should use the same base state model rather than separate ad hoc paths.

Tasks:

- Generalize Moonsign Benediction checks.
- Convert eligible reactions:
  - Electro-Charged -> Lunar-Charged
  - Bloom -> Lunar-Bloom
  - Hydro Crystallize -> Lunar-Crystallize
- Move Lunar conversion logic out of character-specific shortcuts where possible.
- Revisit custom stats:
  - `LUNAR_CHARGED_DMG_BONUS`
  - `LUNAR_BLOOM_DMG_BONUS`
  - `LUNAR_CRYSTALLIZE_DMG_BONUS`
  - `LUNAR_REACTION_DMG_BONUS_ALL`
  - `LUNAR_BASE_BONUS`
  - `LUNAR_MULTIPLIER`
  - `LUNAR_REACTION_CRIT_DMG`

Acceptance criteria:

- Lunar conversion follows party state, not hard-coded character scripts.
- Current `FlinsParty2` behavior is preserved or intentionally updated with documented damage changes.

Implementation status:

- Added simulator-level Lunar conversion gating based on party Lunar characters and non-`NONE` Moonsign.
- Converted Electro-Charged, Bloom, and Hydro Crystallize into Lunar-Charged, Lunar-Bloom, and Lunar-Crystallize events.
- Revisited Lunar damage stat routing so direct Lunar actions use their matching reaction bonus stat.

### Phase 10: Lunar-Charged Accuracy Pass - Done

Tasks:

- Recheck Thundercloud creation and refresh duration.
- Ensure 2s tick cadence.
- Consume 0.4 GU Hydro and Electro on tick.
- Model immediate Thundercloud damage when the other aura is applied after one aura is already present, if relevant to single-target rotations.
- Improve damage formula to reflect all contributing element appliers where practical.
- Keep only one Thundercloud active.

Acceptance criteria:

- `FlinsParty2` remains the primary regression sample.
- Damage changes are explained in final notes when implemented.

Implementation status:

- Preserved the existing single Thundercloud timer with 6 s refresh, 2 s tick cadence, and 0.4 GU Hydro/Electro consumption.
- Kept immediate Lunar-Charged setup damage and weighted party contribution formula.
- Added regression coverage for immediate damage, tick timing, aura consumption, and active Thundercloud state.

### Phase 11: Lunar-Bloom - Done

Tasks:

- Convert eligible Bloom triggers to Lunar-Bloom when party conditions are met.
- Add Verdant Dew state if current or future characters consume it.
- Preserve Dendro Core / Bountiful Core behavior where applicable.
- Add direct Lunar-Bloom damage hooks for characters that explicitly deal it.

Simplification allowed:

- Lunar-Bloom itself does no damage unless a talent/constellation/weapon effect converts it into damage.

Acceptance criteria:

- Lunar-Bloom can trigger as an event.
- Columbina or future Lunar-Bloom characters can listen to and consume the event/state.

Implementation status:

- Lunar-Bloom now triggers as a Lunar event while preserving Dendro Core creation and expiry behavior.
- The reaction itself remains non-damaging unless character hooks/direct Lunar actions consume the event.

### Phase 12: Lunar-Crystallize - Done

Tasks:

- Convert Hydro Crystallize to Lunar-Crystallize when party conditions are met.
- Add Moondrift state.
- Count Lunar-Crystallize triggers.
- Trigger Moondrift Harmony every third trigger.
- Model Moondrift Harmony as immediate Geo damage in the single-target abstraction.

Simplification allowed:

- Geo construct placement and projectile tracking can be abstracted for this simulator.

Acceptance criteria:

- Lunar-Crystallize has separate event logging and damage accounting.
- Standard Crystallize remains available when Lunar conditions are not met.

Implementation status:

- Added Moondrift state and Lunar-Crystallize trigger counting.
- Every third Lunar-Crystallize trigger fires immediate Geo Moondrift Harmony damage in the single-target abstraction.
- Standard Crystallize remains unchanged when Lunar conversion conditions are not met.

## Cross-Cutting Work

### Aura State Model

Current enemy aura storage is not sufficient for all planned reactions. Before or during Phases 5-8, add explicit state for:

- Freeze Aura
- Burning Aura
- Quicken Aura
- Dendro Cores
- Lunar Thundercloud
- Lunar Moondrifts

Prefer a small reaction-state holder over widening `Enemy` with too many unrelated fields.

### Damage Formula Support

Add or verify formula paths for:

- amplifying multiplier
- transformative damage
- additive reaction damage
- Lunar reaction damage
- reaction crit exceptions
- reaction-specific DMG bonuses

### Reporting

Each new reaction should appear clearly in HTML/debug logs:

- direct hit damage
- reaction damage
- reaction name
- trigger character
- relevant aura state
- delayed tick/core/Harmony ownership

### RL Compatibility

Reaction changes alter training distribution and rewards. For every major reaction phase:

- run Java build
- run the relevant scripted sample
- refresh capability profiles only if character role behavior changes materially
- treat existing checkpoints as potentially incompatible in behavior even if tensor shapes match

## Deferred Systems

These are real Genshin systems but are not immediate priorities for this simulator's stated purpose:

- Bond of Life
- Arkhe Pneuma/Ousia enemy interactions
- Nightsoul and Phlogiston exploration mechanics
- player damage intake and healing prevention
- enemy attacks and defensive play
- multi-target positioning and AoE geometry
- full shield absorption model

They should be added only when a target party's offensive kit depends on them.

## Validation Checklist

Minimum validation after each phase:

- `./gradlew build`
- most relevant sample:
  - `./gradlew RaidenParty` for standard reaction regressions
  - `./gradlew FlinsParty2` for Lunar regressions
- inspect generated report for reaction labels and damage attribution

Additional validation for RL-impacting phases:

- `./gradlew ProfileCapabilities` when role profiles are affected
- start `ServeRLJava` with the affected party
- run `python3 src/python/rl/evaluate_policy.py --mode both --summary` against a current checkpoint, if one exists

## Initial Recommended Milestones

1. Core cleanup + current multiplier update.
2. Superconduct.
3. Freeze/Shatter.
4. Standard Crystallize.
5. Burning.
6. Bloom core system.
7. Hyperbloom/Burgeon.
8. Quicken/Aggravate/Spread.
9. Lunar conversion generalization.
10. Lunar-Charged, Lunar-Bloom, Lunar-Crystallize accuracy passes.

## Next High-Priority Accuracy Plan

The Phase 1-12 work establishes the reaction surface needed by the simulator. The next work should improve accuracy for the highest-impact combat systems while preserving the current single-target, non-attacking enemy scope.

These phases are ordered so each phase can be tested independently before later phases depend on it.

### Accuracy Phase A: Elemental Gauge and Aura Decay

Why first:

Most remaining reaction inaccuracies come from aura behavior. Bloom, Quicken, Electro-Charged, Swirl, Crystallize, Freeze, and Burning all depend on reliable aura quantity, decay, and consumption.

Target files:

- `src/java/model/entity/Enemy.java`
- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionCalculator.java`

Tasks:

- Replace simple aura storage with aura instances that can track:
  - element
  - gauge units
  - application time
  - decay duration
  - expiry time
  - source character
- Add standard aura decay for common gauges:
  - 1U
  - 2U
  - 4U
- Add explicit coexistence support for special states:
  - Electro-Charged Hydro/Electro coexistence
  - Quicken plus Dendro/Electro interaction
  - Freeze state plus Hydro/Cryo aura interaction
  - Burning state plus Pyro/Dendro handling
- Move reaction aura consumption into a small helper so every reaction uses the same gauge math.
- Preserve current simplified single-enemy behavior behind tests while improving aura correctness.

Acceptance criteria:

- Aura naturally expires when time advances.
- Reaction consumption removes the correct amount of aura.
- Electro-Charged can maintain Hydro/Electro coexistence until tick consumption or decay.
- Quicken can coexist predictably with Dendro/Electro follow-up hits.
- Freeze and Burning state transitions do not erase unrelated persistent state.

Test cases to add:

- `testAccuracyPhaseA_AuraDecayOneUnit`
  - Apply 1U Pyro aura.
  - Advance to just before expiry and assert aura remains.
  - Advance past expiry and assert aura is gone.
- `testAccuracyPhaseA_AuraDecayTwoUnitLongerThanOneUnit`
  - Apply 1U and 2U auras in separate simulators.
  - Assert 2U survives longer than 1U.
- `testAccuracyPhaseA_VaporizeConsumesExpectedAura`
  - Apply Hydro aura.
  - Trigger Pyro reverse Vaporize.
  - Assert consumed Hydro amount follows reaction gauge policy.
- `testAccuracyPhaseA_ElectroChargedCoexistence`
  - Apply Hydro then Electro.
  - Assert both auras coexist before tick.
  - Advance to tick and assert both reduce by expected amount.
- `testAccuracyPhaseA_QuickenCoexistsWithDendroFollowup`
  - Trigger Quicken.
  - Apply Dendro hit during Quicken.
  - Assert Spread can occur without deleting Quicken immediately.

Suggested command:

- `./gradlew ReactionRegressionTest`

### Accuracy Phase B: Full ICD and Elemental Application Model

Why after aura:

ICD only matters if elemental application and aura state are accurate. This phase makes reaction frequency realistic across multi-hit actions.

Target files:

- `src/java/mechanics/element/ICDManager.java`
- `src/java/simulation/action/AttackAction.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- character action definitions under `src/java/model/character/`

Tasks:

- Represent ICD groups explicitly:
  - standard 3-hit / 2.5s rule
  - no ICD
  - ability-specific ICD
  - shared ICD groups
- Separate damage hit from elemental application hit.
- Allow multi-hit actions to specify per-hit application policy.
- Add action metadata for:
  - application sequence index
  - source ICD group
  - elemental application strength
  - whether the hit can trigger reactions
- Audit existing sample parties for obvious ICD metadata gaps.

Acceptance criteria:

- Repeated hits in the same ICD group do not apply aura every hit.
- No-ICD actions still apply every hit.
- Shared ICD groups block application across related action instances.
- Damage can still occur even when elemental application is blocked.

Test cases to add:

- `testAccuracyPhaseB_StandardIcdThreeHitRule`
  - Perform three same-group elemental hits quickly.
  - Assert only the first and third apply aura if using 3-hit rule.
- `testAccuracyPhaseB_StandardIcdTimeRule`
  - Perform one elemental hit.
  - Advance past 2.5s.
  - Assert next same-group hit applies aura.
- `testAccuracyPhaseB_NoIcdAppliesEveryHit`
  - Perform repeated no-ICD hits.
  - Assert each can trigger a reaction.
- `testAccuracyPhaseB_SharedIcdBlocksRelatedHits`
  - Perform two different actions using the same ICD group.
  - Assert second action deals damage but does not apply aura.
- `testAccuracyPhaseB_DamageStillOccursWhenApplicationBlocked`
  - Trigger ICD block on an attack with motion value.
  - Assert damage is recorded even though no reaction occurs.

Suggested command:

- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`

### Accuracy Phase C: Bloom Family Detail Pass

Why after aura and ICD:

Bloom, Hyperbloom, and Burgeon are highly sensitive to core creation rate, application frequency, and core ownership.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionCalculator.java`

Tasks:

- Add per-core hit/ownership metadata:
  - owner
  - trigger element
  - creation reaction
  - expiry event id if needed
- Add Bloom self-limiting behavior:
  - max active cores
  - oldest-core explosion on overflow
  - optional same-target damage interval cap for repeated core explosions
- Add Hyperbloom/Burgeon core selection policy:
  - single-core consumption for narrow hits
  - multi-core consumption for explicit AoE hits
  - single-target abstraction remains deterministic
- Add Nilou-style Bountiful Core extension point without implementing Nilou-specific logic unless a party needs it.
- Ensure Lunar-Bloom conversion still preserves core behavior.

Acceptance criteria:

- Core overflow behavior is deterministic.
- Hyperbloom and Burgeon consume the intended number of cores.
- Repeated core explosions respect any configured same-target cap.
- Lunar-Bloom and standard Bloom share the same core infrastructure.

Test cases to add:

- `testAccuracyPhaseC_CoreOverflowExplodesOldest`
  - Create six Bloom cores.
  - Assert five remain and oldest dealt damage.
- `testAccuracyPhaseC_HyperbloomConsumesOneCoreForSingleProjectile`
  - Create multiple cores.
  - Trigger Electro single-projectile action.
  - Assert one core is consumed.
- `testAccuracyPhaseC_BurgeonConsumesAoECores`
  - Create multiple cores.
  - Trigger Pyro AoE action.
  - Assert configured AoE core count is consumed.
- `testAccuracyPhaseC_CoreExplosionHitCap`
  - Trigger multiple core explosions inside the configured hit window.
  - Assert damage count follows the cap.
- `testAccuracyPhaseC_LunarBloomUsesSameCorePolicy`
  - Trigger Lunar-Bloom.
  - Assert core metadata and expiry behavior match standard Bloom.

Suggested command:

- `./gradlew ReactionRegressionTest`

### Accuracy Phase D: Quicken Family Detail Pass

Why after aura:

Quicken accuracy depends on proper aura coexistence and decay. This phase should refine the simplified Phase8 implementation.

Target files:

- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionCalculator.java`
- `src/java/mechanics/formula/StandardDamageStrategy.java`

Tasks:

- Model Quicken Aura as a true aura-like state with duration and refresh policy.
- Define how Dendro/Electro applications interact with Quicken and remaining Dendro/Electro aura.
- Ensure Aggravate/Spread:
  - use trigger character EM
  - use trigger character reaction bonus
  - enter additive damage before DMG Bonus/Crit/DEF/RES
  - do not create separate reaction damage instances
- Add debug formula detail for additive reaction contribution.

Acceptance criteria:

- Quicken itself deals no damage.
- Quicken refreshes according to gauge policy.
- Aggravate/Spread only occur on eligible Electro/Dendro damage while Quicken is active.
- Additive contribution appears in report/debug output.

Test cases to add:

- `testAccuracyPhaseD_QuickenRefreshesDuration`
  - Trigger Quicken.
  - Re-trigger before expiry.
  - Assert expiry extends according to gauge policy.
- `testAccuracyPhaseD_AggravateUsesTriggerEm`
  - Create Quicken.
  - Hit with Electro trigger at known EM.
  - Assert additive value matches formula.
- `testAccuracyPhaseD_SpreadUsesTriggerReactionBonus`
  - Create Quicken.
  - Hit with Dendro trigger with `SPREAD_DMG_BONUS`.
  - Assert additive value includes bonus.
- `testAccuracyPhaseD_AdditivePassesThroughCritAndDmgBonus`
  - Use 100% crit and known Dendro/Electro DMG bonus.
  - Assert final direct hit includes additive contribution after multipliers.
- `testAccuracyPhaseD_NoCatalyzeAfterQuickenExpiry`
  - Let Quicken expire.
  - Assert Electro/Dendro hit does not Aggravate/Spread.

Suggested command:

- `./gradlew ReactionRegressionTest`

### Accuracy Phase E: Lunar Reaction Detail Pass

Why after standard reaction detail:

Lunar reactions are conversions or overlays on standard Hydro-related reactions. They should inherit the corrected aura, ICD, and core behavior.

Target files:

- `src/java/simulation/runtime/CombatActionResolver.java`
- `src/java/mechanics/reaction/ReactionEffectScheduler.java`
- `src/java/simulation/runtime/ReactionState.java`
- `src/java/simulation/runtime/ReactionStateController.java`
- `src/java/mechanics/formula/LunarDamageStrategy.java`
- relevant Lunar character files under `src/java/model/character/`

Tasks:

- Make Moonsign Benediction conversion source explicit:
  - which party member enables conversion
  - which reaction families are converted
  - whether multiple converters stack or select one policy
- Refine Lunar-Charged:
  - Thundercloud creation/refresh
  - tick ownership
  - tick crit rules
  - contributing applier selection
- Refine Lunar-Bloom:
  - Verdant Dew state
  - Moonridge Dew state
  - conversion hooks for characters that turn Lunar-Bloom into direct damage
  - preserve Dendro Core compatibility when no direct conversion exists
- Refine Lunar-Crystallize:
  - Moondrift count/state
  - Harmony trigger cadence
  - crit rules
  - interaction with standard Crystallize shard-related effects
- Preserve current `FlinsParty2` behavior unless a documented formula correction intentionally changes it.

Acceptance criteria:

- Lunar conversion is party-state-driven and character-source-aware.
- Lunar reaction events are visible to Columbina, artifacts, and future characters.
- Thundercloud, Verdant Dew, Moonridge Dew, and Moondrift states are snapshot-safe.
- `FlinsParty2` continues to run and any DPS delta is explained.

Test cases to add:

- `testAccuracyPhaseE_LunarConversionRequiresBenedictionSource`
  - Use Moonsign without a converter and assert no conversion.
  - Add converter and assert conversion occurs.
- `testAccuracyPhaseE_LunarChargedTickOwnershipAndCrit`
  - Trigger Lunar-Charged with known stats.
  - Assert tick owner and expected crit scaling path.
- `testAccuracyPhaseE_LunarBloomDewState`
  - Trigger Lunar-Bloom near an active Lunar hook.
  - Assert Verdant/Moonridge Dew state increments.
- `testAccuracyPhaseE_LunarCrystallizeHarmonyCadence`
  - Trigger Lunar-Crystallize three times.
  - Assert one Harmony event and correct Geo damage attribution.
- `testAccuracyPhaseE_FlinsParty2Regression`
  - Run `FlinsParty2`.
  - Record total damage and DPS delta in final notes.

Suggested command:

- `./gradlew ReactionRegressionTest`
- `./gradlew FlinsParty2`

### Accuracy Phase F: Character, Weapon, and Artifact Coverage

Why after core mechanics:

Once reaction and formula paths are stable, missing character-specific effects become the main source of simulator error.

Target files:

- `src/java/model/character/`
- `src/java/model/weapon/`
- `src/java/model/artifact/`
- `config/characters/`
- `src/java/sample/`

Tasks:

- Audit currently used parties first:
  - `RaidenParty`
  - `FlinsParty2`
  - any registered RL parties
- Add missing passives, constellations, weapon effects, artifact effects, and talent data only when they affect offensive output.
- Prefer capability/profile-driven metadata for RL parties.
- Add focused regression tests for every newly modeled kit mechanic.

Acceptance criteria:

- Current sample parties document which character mechanics are exact and which are approximated.
- Missing offensive mechanics are tracked explicitly instead of hidden in code comments.
- New character mechanics have deterministic regression tests.

Test cases to add:

- `testAccuracyPhaseF_RaidenResolveAndEnergyRegression`
  - Check Resolve generation and burst damage contribution for a fixed script.
- `testAccuracyPhaseF_FlinsThundercloudConditionalHits`
  - Check extra hits appear only while Thundercloud is active.
- `testAccuracyPhaseF_ColumbinaGravityAndDewRegression`
  - Check Gravity accumulation and Dew consumption with deterministic timing.
- `testAccuracyPhaseF_ArtifactLunarReactionBuffRegression`
  - Check Lunar-reaction-aware artifact buffs trigger on Lunar events only.
- `testAccuracyPhaseF_WeaponReactionBonusRegression`
  - Check reaction-gated weapon bonuses apply only under their documented condition.

Suggested command:

- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
