# AGENTS.md

## Scope
- This file applies to `src/java/model/entity/`.

## Directory role
- This package defines the core abstract entities that the rest of the simulator builds on.

## Java files in this directory
- `ActionTriggeredWeaponEffect.java`: capability interface for weapons that react to character action dispatch.
- `ArtifactSet.java`: base class for artifact sets, limited to fixed stats, passive stat application, and display metadata.
- `ArtifactTeamBuffProvider.java`: capability interface for artifact sets that provide dynamically resolved team buffs.
- `BurstTriggeredArtifactEffect.java`: capability interface for artifacts that react to burst use.
- `Character.java`: abstract playable-character base with typed identity, stat assembly, buffs, snapshots, energy tracking, cooldown state, action hooks, and capability extension points.
- `CharacterTeamBuffProvider.java`: capability interface for characters that provide team buffs.
- `DamageTriggeredArtifactEffect.java`: capability interface for artifacts that react after damage is calculated.
- `DamageTriggeredWeaponEffect.java`: capability interface for weapons that react after damage is calculated.
- `Enemy.java`: target model containing level, resistances, time-aware elemental
  aura gauges with source application tax/decay policy, and simplified Freeze
  aura state.
- `FormStateProvider.java`: capability interface for characters that expose form or stance state to observation/reporting layers.
- `ReactionAwareArtifact.java`: capability interface for artifacts that listen to reaction results.
- `ReactionAwareCharacter.java`: capability interface for characters with reaction-listener behavior.
- `SimulatorInitializedWeaponEffect.java`: capability interface for weapons that register simulator-bound timed behavior after their owner joins a party.
- `SimulatorInitializedArtifactEffect.java`: capability interface for artifacts
  that initialize simulator-bound state after party insertion, including the
  owner's initial active/off-field state.
- `StatAssembler.java`: internal helper that assembles structural and effective character stats.
- `SwitchAwareArtifact.java`: capability interface for artifacts that react to switch events.
- `SwitchAwareCharacter.java`: capability interface for characters that react to switch events.
- `SwitchAwareWeaponEffect.java`: capability interface for weapons that react to switch events.
- `TargetDependentWeaponEffect.java`: capability interface for per-hit weapon stats that depend on live enemy state.
- `Weapon.java`: base class for weapons with fixed stats, passive stat application, display metadata, and NA-energy category information.
- `WeaponTeamBuffProvider.java`: capability interface for weapons that provide team buffs.

## Coupling and dependencies
- `Character` depends on `model.stats.StatsContainer`, `model.type.CharacterId`, `model.type.StatType`, `model.entity.Weapon`, `model.entity.ArtifactSet`, and `mechanics.buff.Buff`.
- `Enemy` is consumed heavily by `simulation.runtime.CombatActionResolver`, `simulation.CombatSimulator`, and `mechanics.formula.DamageCalculator`.
- Aura state in `Enemy` is time-aware for runtime-applied elemental gauges; persistent reaction states such as Burning, Quicken, Dendro Cores, Thundercloud, Dew, and Moondrifts live in `simulation.runtime.ReactionState`.
- Standard source application belongs to `Enemy.applyAura`; callers pass the
  pre-tax source gauge. Raw `setAura` methods are explicit state/fixture setup
  and must not silently acquire source-application policy.
- `Weapon` and `ArtifactSet` are called by `Character` during stat assembly;
  event behavior is dispatched only when a concrete item implements the
  relevant capability interface. Simulator-initialized weapon and artifact
  behavior is registered after party insertion.
- Concrete character, weapon, and artifact packages all extend these types.

## Agent guidance
- Changes here are high-impact. Audit all subclasses and simulator call sites before modifying shared hooks or state fields.
- Preserve the distinction between structural stats, effective stats, and snapshots in `Character`.
- Do not move persistent reaction-specific state into `Enemy` unless it is truly target aura state; use `ReactionState` for simulator-owned reaction objects and counters.
- New shared behavior should usually be a narrow capability interface. Do not add broad optional hooks to `Weapon`, `ArtifactSet`, or `Character` unless existing capabilities cannot model the mechanic cleanly.
- Keep `CharacterId` as the logic identity and `name` as display or data-lookup metadata.
