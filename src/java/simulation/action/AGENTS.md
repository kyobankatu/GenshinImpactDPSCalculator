# AGENTS.md

## Scope
- This file applies to `src/java/simulation/action/`.

## Directory role
- This package contains typed action payloads used by character dispatch and damage resolution.

## Java files in this directory
- `AttackAction.java`: action descriptor carrying damage multiplier, element, scaling stat, bonus-stat context, action type, animation duration, snapshot flags, ICD/application settings, elemental gauge strength, extra bonuses, additive reaction bonus, defense ignore, Shatter and Dendro Core interaction metadata, and custom Lunar metadata.
- `CharacterActionKey.java`: typed top-level character action categories such as normal, charged, skill, and burst.
- `CharacterActionRequest.java`: canonical dispatch payload for non-hit character actions, with log labels kept as presentation data.

## Coupling and dependencies
- `simulation.CombatSimulator` consumes `AttackAction` for every hit.
- `mechanics.formula.DamageCalculator` reads most of its fields during damage resolution.
- Concrete character classes consume `CharacterActionRequest` for top-level dispatch and construct `AttackAction` objects directly for normals, skills, bursts, summons, weapon procs, and custom Lunar actions.
- ICD tags, elemental application strength, Shatter flags, Dendro Core consumption settings, Lunar metadata, and action-type flags feed into reaction logic and item passive triggers.

## Agent guidance
- These classes are effectively shared schema. Field, enum, or default changes can ripple through the whole simulator.
- If you add metadata here, audit constructor overloads, dispatch adapters, and all major consumers.
- Keep a clear distinction between action classification, elemental application settings, and custom per-hit overrides.
- Preserve the separation between damage and elemental application: action metadata may allow a hit to deal damage even when ICD blocks aura application.
- Do not use display labels as new runtime action keys; extend `CharacterActionKey` or `AttackAction` metadata when logic needs a typed value.
