---
name: add-genshin-content
description: Add or modify Genshin characters, weapons, artifacts, party definitions, rotations, configuration data, capability profiles, and custom mechanics while preserving simulator, optimizer, RL, and report contracts. Use for new playable content, equipment, teams, data files, or content-specific triggers.
---

# Add Genshin content

1. Read root `AGENTS.md`, the closest model package instructions, sample and party instructions, and [content-routing.md](references/content-routing.md).
2. Establish whether the content is canonical, intentionally simplified, or custom. Record sources, game version, assumptions, and unsupported defensive/multi-target behavior.
3. Keep static multipliers and status data in the established `config/characters` layout when data-driven. Keep behavior in the narrowest character, weapon, artifact, buff, reaction, or party class.
4. Use typed `CharacterId`, action keys, stat types, buff IDs, and reaction kinds internally. Restrict display-name translation to adapters and presentation boundaries.
5. Add one `PartyDefinition` and catalog registration for a new party. Do not add party-specific sample wrappers or RL simulator factories.
6. Add regression coverage for normal trigger, no-trigger, cooldown/cap boundary, equipment/constellation/refinement interaction, and snapshot restore when stateful.
7. Run `ReactionRegressionTest`, `PartyCatalogRegressionTest` for party work, the affected party simulation, and `build`. Use RL/profile/report checks only when their contracts change.
8. Report data/code alignment, assumptions, known simplifications, generated artifacts, and whether capability profiles or existing RL checkpoints need regeneration.
