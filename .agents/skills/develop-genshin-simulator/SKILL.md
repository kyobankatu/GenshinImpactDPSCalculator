---
name: develop-genshin-simulator
description: Route and implement changes to the Java Genshin combat simulator across runtime, actions, events, mechanics, models, parties, optimization, visualization, and regression entry points. Use for simulator bug fixes, refactors, new runtime state, timing changes, snapshot behavior, or cross-package combat changes.
---

# Develop the Genshin simulator

1. Read root `AGENTS.md`, then every nearest `AGENTS.md` governing the files in scope. Read `README.md` and the affected party definition or sample entry point when behavior changes.
2. Classify each target as source, configuration, generated documentation, or output. Do not edit generated reports or `docs/` artifacts unless requested.
3. Read [change-routing.md](references/change-routing.md) and select only the rows matching the change. Trace behavior from party/sample entry point through `CombatSimulator` into the narrowest runtime, mechanic, or model owner.
4. Freeze the smallest current baseline before editing. Distinguish deterministic expectations from known random behavior.
5. Make a local change at the owning boundary. Keep typed IDs and keys inside runtime code; translate display names only at data, sample, logging, or report boundaries.
6. Add or extend an executable regression for changed behavior. Cover a normal path, an invalid or no-trigger path, and timing/cap/cooldown boundaries when relevant.
7. Run the routed checks plus `./gradlew build`. Verify snapshot/restore, optimizer, report, and RL consumers whenever the changed state crosses those boundaries.
8. Report changed files, assumptions, commands run, observed results, skipped checks, and whether existing outputs or checkpoints are behaviorally incompatible.

Do not broaden a focused mechanic fix into an unrelated architecture rewrite. Do not introduce external network dependencies into the core simulation path.
