# AGENTS.md

## Scope
- This file applies to `src/java/mechanics/element/`.

## Directory role
- This package contains elemental rule helpers that are adjacent to, but separate from, direct reaction damage calculation.

## Java files in this directory
- `ICDManager.java`: tracks elemental application internal cooldown state by character identity and ICD tag.
- `ResonanceManager.java`: inspects party composition and applies elemental resonance buffs or resonance-triggered listeners.

## Coupling and dependencies
- `ICDManager` depends on `model.type.CharacterId`, `model.type.ICDType`, and `model.type.ICDTag` and is used by runtime action resolution when deciding whether a hit applies aura.
- `ResonanceManager` depends on `simulation.CombatSimulator`, `model.entity.Character`, `model.type.Element`, `model.type.StatType`, `mechanics.buff.SimpleBuff`, `mechanics.energy.EnergyManager`, `mechanics.energy.ParticleType`, and `mechanics.reaction.ReactionResult`.
- Electro resonance uses a simulator reaction listener rather than a static stat buff.

## Agent guidance
- If you change aura application behavior, inspect both `ICDManager` and `simulation.CombatSimulator`.
- If you add or change resonance effects, verify whether the effect belongs as a permanent team buff, a field buff, an enemy shred proxy, or a reaction listener.
- Keep in mind that some resonance implementations are simplified approximations rather than full game-accurate condition checks.
