---
name: add-genshin-content
description: Add or modify Genshin characters, constellations, weapons, artifacts, party definitions, rotations, configuration data, capability profiles, and custom mechanics, including high-throughput batches, while preserving simulator, optimizer, RL, and report contracts. Use for new playable content, equipment, teams, data files, content-specific triggers, or broad content-expansion sessions.
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

## Bulk content mode

When the user requests many additions rather than one named item:

1. Build one compact campaign inventory with target, content type, source readiness, shared prerequisites, verification route, and status. Treat the explicit coverage request as the queue; do not require each missing character or weapon to masquerade as a simulator defect.
2. Order work by reusable prerequisites, then complete vertical slices. A vertical slice includes static data, behavior, relevant constellation/refinement effects, focused tests, and one runnable party or fixture when practical.
3. Make one verified implementation commit per content unit. Group only tiny inseparable data variants; never mix unrelated mechanics merely to increase throughput.
4. Capture source URLs, game version, assumptions, commands, and observed results in the untracked session record immediately. Reconcile them into tracked docs once per four completed units or 60 minutes, and at wind-down.
5. Run focused compile/regression checks for every unit. Run expensive full-catalog, repeated deterministic baseline, report, or RL-facing checks once per batch unless the unit changes their contract or directly affects an audited party.
6. Keep `README.md` as a supported-content and usage summary. Keep detailed per-unit evidence in compact `BACKLOG.md`/`TASKS.md` batch tables rather than prose sections repeated for every item.
7. A unit is complete only when code/config load together, normal and abnormal boundaries pass, generated artifacts are not staged, and the commit can be reverted independently.
