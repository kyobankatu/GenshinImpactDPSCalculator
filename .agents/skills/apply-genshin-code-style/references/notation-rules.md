# Notation rules

These are project-level requirements, not preferences. They apply to code you write or edit.

## Java notation

| Rule | Detail |
|---|---|
| Indentation | exactly four spaces per level; no tabs |
| Extra spaces | never pad for visual alignment |
| Braces | required for every block, including one-statement `if` / `else` / `for` / `while` |
| Control flow | no `goto`-style jumps or label-driven escapes |
| Javadoc | required on all classes and major methods |

Comparison operator direction is not constrained. Write whichever of `<`, `<=`, `>`, `>=` reads most
naturally, and match the surrounding code.

## Naming

| Kind | Convention | Example |
|---|---|---|
| Class, interface, enum type | `PascalCase` | `DamageCalculator`, `ICDManager` |
| Method, variable, field | `camelCase` | `calculateDamage`, `animationDuration` |
| Constant, enum entry | `SCREAMING_SNAKE_CASE` | `LUNAR_BASE_BONUS`, `STANDARD_STRATEGY` |
| Debug log prefix | bracketed module tag | `[DC_DEBUG]`, `[FormulaDebug]`, `[FormulaValues]` |

## Typed identifiers

Runtime control flow uses typed keys, never display strings:

`CharacterId`, `CharacterActionKey`, `BuffId`, `StatType`, `ActionType`, `ReactionResult.Kind`,
`ReactionResult.LunarType`.

Display-name translation is allowed only at data loading (`config/characters/`), sample and party setup,
logging, and report generation. A presentation label must never become a control-flow key inside
`simulation/`, `mechanics/`, or `model/`.

## Javadoc shape

Match the existing house style: a one-line summary, then a `<p>` or `<ol>` block explaining the mechanic,
with formulas in `{@code ...}` and cross-references via `{@link ...}`.

```java
/**
 * Computes outgoing damage for a single attack action.
 *
 * <p>
 * Two independent code paths are implemented inside {@link #calculateDamage}:
 * <ol>
 * <li><b>Lunar path</b> – custom formula applied after all standard bonuses.</li>
 * <li><b>Standard path</b> – the official Genshin Impact formula.</li>
 * </ol>
 */
```

For a major method, document what the method decides, the mechanic or assumption it encodes, units and
ranges for numeric parameters, and any intentional simplification. Add an inline comment wherever a value is
game-specific, non-canonical, or a deterministic stand-in for random behavior.

Verify documentation changes with `./gradlew javadoc`, which must stay UTF-8 clean.

## Structural rules

- Add a new stat to `StatType` before referencing it from `DamageCalculator`, `CombatSimulator`, or config.
- Place a new class in the closest matching existing package; do not create a parallel abstraction layer.
- Add a party as one `PartyDefinition` plus catalog registration, never as a new sample wrapper or a
  party-specific RL simulator factory.
- Keep new mechanics local to the owning character, weapon, artifact, buff, reaction, or runtime class.
- Do not introduce external network dependencies into the core simulation path or the RL rollout path.

## CSV data format

Header must be exactly `Character,AbilityType,Key,Level,Value1,Value2`.

- `AbilityType` is uppercase and matches `ActionType`: `NORMAL`, `CHARGED`, `PLUNGE`, `SKILL`, `BURST`,
  `PASSIVE`.
- `Value1` / `Value2` are plain decimals: `0.640`, never `64%` or an expression.
- No spaces around commas; keep a trailing newline at end of file.

## Python notation

Files under `src/python/rl/` and `scripts/`:

- `from __future__ import annotations` at the top;
- type hints on public functions and dataclass fields;
- four-space indentation, module-level docstring;
- standard library plus the pinned dependencies in `requirements.txt` only; do not add new dependencies;
- keep the rollout hot path allocation-light.
