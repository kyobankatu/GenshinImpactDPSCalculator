# AGENTS.md

## Scope
- This file applies to `src/model/entity/`.

## Directory role
- This package defines the core abstract entities that the rest of the simulator builds on.

## Java files in this directory
- `ActionTriggeredWeaponEffect.java`: capability interface for weapons that react to character action dispatch.
- `ArtifactSet.java`: base class for artifact sets, limited to fixed stats, passive stat application, and display metadata.
- `BurstStateProvider.java`: capability interface for characters that expose active burst-state information.
- `BurstTriggeredArtifactEffect.java`: capability interface for artifacts that react to burst use.
- `Character.java`: abstract playable-character base with typed identity, stat assembly, buffs, snapshots, energy tracking, cooldown state, action hooks, and capability extension points.
- `CharacterTeamBuffProvider.java`: capability interface for characters that provide team buffs.
- `DamageTriggeredArtifactEffect.java`: capability interface for artifacts that react after damage is calculated.
- `DamageTriggeredWeaponEffect.java`: capability interface for weapons that react after damage is calculated.
- `Enemy.java`: target model containing level, resistances, and live elemental aura state.
- `ReactionAwareArtifact.java`: capability interface for artifacts that listen to reaction results.
- `ReactionAwareCharacter.java`: capability interface for characters with reaction-listener behavior.
- `StatAssembler.java`: internal helper that assembles structural and effective character stats.
- `SwitchAwareArtifact.java`: capability interface for artifacts that react to switch events.
- `SwitchAwareCharacter.java`: capability interface for characters that react to switch events.
- `SwitchAwareWeaponEffect.java`: capability interface for weapons that react to switch events.
- `Weapon.java`: base class for weapons with fixed stats, passive stat application, display metadata, and NA-energy category information.
- `WeaponTeamBuffProvider.java`: capability interface for weapons that provide team buffs.
- `state/`: state holders for artifact rolls, cooldowns, energy, and snapshots used by `Character`.

## Coupling and dependencies
- `Character` depends on `model.stats.StatsContainer`, `model.type.CharacterId`, `model.type.StatType`, `model.entity.Weapon`, `model.entity.ArtifactSet`, and `mechanics.buff.Buff`.
- `Enemy` is consumed heavily by `simulation.CombatSimulator` and `mechanics.formula.DamageCalculator`.
- `Weapon` and `ArtifactSet` are called by `Character` during stat assembly; event behavior is dispatched only when a concrete item implements the relevant capability interface.
- Concrete character, weapon, and artifact packages all extend these types.

## Agent guidance
- Changes here are high-impact. Audit all subclasses and simulator call sites before modifying shared hooks or state fields.
- Preserve the distinction between structural stats, effective stats, and snapshots in `Character`.
- New shared behavior should usually be a narrow capability interface. Do not add broad optional hooks to `Weapon`, `ArtifactSet`, or `Character` unless existing capabilities cannot model the mechanic cleanly.
- Keep `CharacterId` as the logic identity and `name` as display or data-lookup metadata.
