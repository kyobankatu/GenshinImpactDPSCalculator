---
name: validate-genshin-mechanics
description: Research, audit, implement, and regression-test Genshin combat mechanics including damage formulas, reactions, elemental gauges, ICD, buffs, energy, character hooks, weapons, artifacts, and Lunar behavior. Use when game accuracy, source provenance, assumptions, tolerances, or mechanic regressions matter.
---

# Validate Genshin mechanics

1. Define the exact mechanic claim, game version, source standard, simulator scope, and acceptance tolerance before changing code.
2. Read root and closest package `AGENTS.md` files, `README.md` accuracy notes, the affected implementation, and the relevant section of [mechanics-checklist.md](references/mechanics-checklist.md).
3. Record whether each rule is sourced, experimentally inferred, intentionally simplified, custom/non-canonical, or unresolved. Prefer primary game data and reproducible tests; use community references only with explicit provenance.
4. Freeze `ReactionRegressionTest` and the smallest affected party baseline. Separate known random output from deterministic expectations.
5. Implement at the narrowest mechanic or model owner. Keep presentation labels out of runtime control flow.
6. Add regression coverage for trigger, no-trigger, boundary timing/cap, state consumption, attribution, and snapshot restoration as applicable.
7. Run `ReactionRegressionTest`, the affected party, and `build`. Inspect optimizer, RL observation/reward, and report effects when the mechanic reaches them.
8. Report evidence, simplifications, uncertainty, numerical tolerance, and any behaviorally incompatible checkpoint or baseline change.

Never claim exact game fidelity from compilation or one sample total alone.
