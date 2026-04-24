# AGENTS.md

## Scope
- This file applies to `src/java/model/artifact/`.

## Directory role
- This package implements concrete artifact set behavior on top of `model.entity.ArtifactSet`.
- These classes combine static stats with explicitly opted-in event-driven set effects.

## Java files in this directory
- `AubadeOfMorningstarAndMoon.java`: custom Lunar set that changes off-field and switch-in Lunar reaction bonuses based on Moonsign state.
- `EmblemOfSeveredFate.java`: ER-focused set that adds ER stats and converts ER into burst damage bonus.
- `NightOfTheSkysUnveiling.java`: Lunar reaction set that grants temporary crit-rate buffs when its owner is on field during Lunar reactions.
- `NoblesseOblige.java`: burst-focused set that applies a team ATK buff on burst use.
- `SilkenMoonsSerenade.java`: Lunar-oriented set that grants party EM after elemental damage and refreshes Gleaming Moon synergy.
- `ViridescentVenerer.java`: Anemo support set that boosts swirl damage and applies element-specific resistance shred when on-field swirl occurs.

## Coupling and dependencies
- All classes extend `model.entity.ArtifactSet`.
- Event-driven behavior is exposed through focused capability interfaces such as `ReactionAwareArtifact`, `DamageTriggeredArtifactEffect`, `SwitchAwareArtifact`, and `BurstTriggeredArtifactEffect`.
- Most set effects depend on `simulation.CombatSimulator`, `mechanics.buff.Buff` or `SimpleBuff`, and `model.type.StatType`.
- Lunar sets depend on `simulation.CombatSimulator.Moonsign` and custom Lunar reaction naming.
- `ViridescentVenerer` should use typed `mechanics.reaction.ReactionResult` metadata for swirl behavior rather than parsing display labels.

## Agent guidance
- Before changing a set bonus, verify which capability it uses: passive stat application, burst-triggered, reaction-aware, switch-aware, or damage-triggered.
- Pay attention to refresh versus stacking semantics. Logic-bearing artifact buffs should use typed `BuffId` values for replacement and lookup.
- If you add a new event-driven set, implement the narrowest existing artifact capability interface instead of expanding `ArtifactSet`.
- If a set needs no runtime hook, keep it as plain `ArtifactSet` stats plus `applyPassive`.
