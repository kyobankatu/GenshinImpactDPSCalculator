# AGENTS.md

## Scope
- This file applies to `src/model/artifact/`.

## Directory role
- This package implements concrete artifact set behavior on top of `model.entity.ArtifactSet`.
- These classes combine static stats with event-driven set effects.

## Java files in this directory
- `AubadeOfMorningstarAndMoon.java`: custom Lunar set that changes off-field and switch-in Lunar reaction bonuses based on Moonsign state.
- `EmblemOfSeveredFate.java`: ER-focused set that adds ER stats and converts ER into burst damage bonus.
- `NightOfTheSkysUnveiling.java`: Lunar reaction set that grants temporary crit-rate buffs when its owner is on field during Lunar reactions.
- `NoblesseOblige.java`: burst-focused set that applies a team ATK buff on burst use.
- `SilkenMoonsSerenade.java`: Lunar-oriented set that grants party EM after elemental damage and refreshes Gleaming Moon synergy.
- `ViridescentVenerer.java`: Anemo support set that boosts swirl damage and applies element-specific resistance shred when on-field swirl occurs.

## Coupling and dependencies
- All classes extend `model.entity.ArtifactSet`.
- Most set effects depend on `simulation.CombatSimulator`, `mechanics.buff.Buff` or `SimpleBuff`, and `model.type.StatType`.
- Lunar sets depend on `simulation.CombatSimulator.Moonsign` and custom Lunar reaction naming.
- `ViridescentVenerer` depends on `mechanics.reaction.ReactionResult` naming conventions for swirl elements.

## Agent guidance
- Before changing a set bonus, verify which hook it uses: passive stat application, burst hook, reaction hook, switch hook, or damage hook.
- Pay attention to refresh versus stacking semantics. Several sets manage uniqueness manually through buff names.
- If you add a new set, prefer fitting it into existing `ArtifactSet` hooks instead of expanding the base API unless required.
