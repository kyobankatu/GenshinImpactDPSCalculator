---
name: apply-genshin-code-style
description: Apply this repository's mandatory Java and Python notation rules when writing or editing source, covering four-space indentation, required braces, right-facing comparison operators, forbidden constructs, typed identifiers over display strings, debug log prefixes, naming conventions, and Javadoc coverage for classes and major methods. Use whenever authoring or modifying code here.
---

# Apply Genshin code style

1. Read [notation-rules.md](references/notation-rules.md), then read enough of the target file to match its local conventions. This codebase mixes terse implementations with heavy Javadoc; follow the file you are in.
2. Indent with four spaces. Never insert extra alignment spaces, and never reformat lines you did not otherwise change.
3. Always write braces, including for single-statement `if`, `else`, `for`, and `while` bodies.
4. Write comparisons with the right-facing operators `<` and `<=` in new and edited expressions. Do not rewrite existing `>` or `>=` occurrences elsewhere in the file; that is an unrequested refactor.
5. Use typed identifiers inside runtime code: `CharacterId`, `CharacterActionKey`, `BuffId`, `StatType`, `ReactionResult.Kind`, `ReactionResult.LunarType`. Translate to display names only at data loading, sample, logging, and report boundaries.
6. Follow the naming rules: `PascalCase` types, `camelCase` members, `SCREAMING_SNAKE_CASE` constants and enum entries, and bracketed module tags for debug logs such as `[DC_DEBUG]` or `[FormulaDebug]`.
7. Write Javadoc for every new class and every major method: purpose, the mechanic or assumption it encodes, and non-obvious parameter or return semantics. Add a short comment whenever a rule is game-specific or intentionally simplified.
8. Add a new `StatType` entry before referencing a new stat anywhere else. Keep new classes in the closest existing package instead of creating a parallel abstraction.
9. Keep Python in `src/python/rl/` consistent with its neighbors: `from __future__ import annotations`, type hints, four-space indent, module docstrings, and no new external dependencies.

Do not introduce `goto`-style control flow, do not reformat unrelated files, and do not let presentation labels become control-flow keys in simulator internals.
