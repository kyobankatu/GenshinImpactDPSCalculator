# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Java 11+ Genshin Impact battle simulation and DPS calculator with artifact substat optimization. The simulation emulates skill animations (cast time), elemental aura/ICD, reactions, buffs, and a multi-dimensional artifact optimization pipeline. It also includes a Python RL training module communicating with the Java sim via TCP.

## Mandatory Rules for Claude Code

- Do NOT modify any files unless explicitly instructed.
- Do NOT refactor existing code unless clearly requested.
- Prefer minimal, localized changes over large improvements.
- Stability and existing behavior are more important than code cleanliness.

## Change Proposal Requirement

Before making any code changes:
- Explain what will be changed
- Explain why it is necessary
- Describe potential risks or side effects

Wait for explicit approval before proceeding.

## Security Rules

- Never request or output secrets, API keys, or credentials.
- Do not log or print personal data.
- Assume production-like constraints even in development.

## Cost Awareness

- Keep responses concise.
- Avoid repeating large code blocks unless necessary.
- Prefer explanation over full implementation when possible.

## Build & Run

The project has been migrated to use Gradle, but you can also compile manually if needed:

### Gradle (Recommended)
```bash
# Clean and build the project (compiles all sources)
./gradlew build

# Generate Javadoc (outputs to build/docs/javadoc/)
./gradlew javadoc
```

### Manual Compilation
No build tool needed (no Maven/Gradle required to just run). Compile manually with `javac`, output to `bin/`:

```bash
# Compile all sources (run from project root)
javac -cp src -d bin $(find src -name "*.java")

# Run Raiden National team simulation
java -cp bin sample.RaidenNational

# Run custom Lunar party simulation
java -cp bin sample.FlinsPartySimulation

# Start RL server (Java side), then run Python training in a separate terminal
java -cp bin RunRL
cd rl_optimization && python train.py
```

## Notation

- Indent must be 4
- Avoid using goto
- Write specification for all Classes and major methods for javadoc

## Architecture

### Core Data Flow

1. **Entry points** (`src/sample/`) create `Party` + characters → pass to `OptimizerPipeline`
2. `OptimizerPipeline` → `IterativeSimulator` → `ArtifactOptimizer` (hill-climbing over substat distributions)
3. Each simulation call → `CombatSimulator.performAction()` + `advanceTime()` drives the time-based engine
4. Damage events → `DamageCalculator` → logged by `VisualLogger` → `HtmlReportGenerator`

### Key Classes

| Class | Role |
|---|---|
| `CombatSimulator` | God class: time progression, action execution, buff lifecycle, reaction hooks |
| `DamageCalculator` | All damage formula logic (base × MV × buffs × crit × resist × reactions) |
| `Party` | Active character management and switching |
| `StatType` (enum) | Single source of truth for all stat keys (57 types) — add new stats here first |
| `StatsContainer` | Holds base/percent/flat bonus for each `StatType` |
| `ICDManager` | Tracks element application cooldowns per skill/ICD group |
| `EnergyManager` | Particle generation and ER-based energy fill |
| `ArtifactOptimizer` | Hill-climbing over {CR, CD, ATK%, HP%, EM} roll distributions |
| `HtmlReportGenerator` | Outputs interactive HTML with pie charts and timelines |

### Custom "Lunar" Mechanics (Non-Canonical)

Characters `Ineffa`, `Flins`, `Columbina` implement original mechanics not in the official game:

- **`isLunar` flag**: Lunar characters receive synergy buffs from non-Lunar teammates
- **`LUNAR_BASE_BONUS`** (Ineffa): Additive bonus applied before damage multipliers
- **`LUNAR_MULTIPLIER`** (Columbina): Independent final multiplier; scales as `(EM / 2000) * 1.5`; shown as `ColMult` in formula debug logs
- **Moonsign / Ascendant Blessing**: Non-Lunar characters (e.g. Sucrose) buff all Lunar party members on skill/burst use

These are implemented in `DamageCalculator.java` as special-case branches.

## Genshin Mechanics & Damage Formula

### 1. Standard Damage Formula
The simulation follows the official Genshin Impact damage formula:
`Outgoing Damage = (Base Damage + Additive Bonus) × (1 + DMG Bonus%) × Crit Multiplier`
`Actual Damage = Outgoing Damage × Enemy DEF Multiplier × Enemy RES Multiplier × Amplifying Reaction Multiplier`

- **Base Damage**: `Character Stat (ATK/HP/DEF) × Talent Multiplier`
- **Additive Bonus**: Flat damage additions like Shenhe's Icy Quill, Yun Jin's Cliffbreaker's Banner, or Aggravate/Spread base damage increases.
- **DMG Bonus%**: Sum of Elemental/Physical DMG%, specific attack type DMG% (e.g., Normal Attack DMG%), and All/Generic DMG%.
- **Crit Multiplier**: `1 + Crit DMG%` (Applied only if the attack crits).

### 2. Enemy Mitigation
- **Enemy DEF Multiplier**: `(Char Level + 100) / [ (Enemy Level + 100) × (1 - DEF Shred%) × (1 - DEF Ignore%) + (Char Level + 100) ]`
- **Enemy RES Multiplier**: 
  - If RES < 0: `1 - (RES / 2)`
  - If 0 ≤ RES < 0.75: `1 - RES`
  - If RES ≥ 0.75: `1 / (4 × RES + 1)`

### 3. Elemental Reactions
- **Amplifying Reactions (Melt / Vaporize)**: Multiplies the final hit damage.
  - `Reaction Multiplier × (1 + (2.78 × EM / (EM + 1400)) + Reaction Bonus%)`
  - Base Reaction Multiplier is 2.0 (Forward) or 1.5 (Reverse).
- **Additive Reactions (Spread / Aggravate)**: Adds flat damage to the **Additive Bonus** step before DMG% and Crit multipliers.
  - `Level Multiplier × Base Reaction Multiplier × (1 + (5 × EM / (EM + 1200)) + Reaction Bonus%)`
  - Base Multiplier is 1.15 for Aggravate and 1.25 for Spread.
- **Transformative Reactions (Swirl, Electro-Charged, Overload, Bloom, etc.)**: Independent damage instances. They ignore Enemy DEF and cannot Crit (unless specified, e.g., Nahida C2), but are affected by Enemy RES.
  - `Level Multiplier × Base Reaction Multiplier × (1 + (16 × EM / (EM + 2000)) + Reaction Bonus%) × Enemy RES Multiplier`

### 4. Elemental Gauge Theory & ICD (Internal Cooldown)
- **Aura and Trigger**: Elements apply "gauges" (e.g., 1U, 2U, 4U). The trigger element consumes the aura element's gauge based on reaction tax rules.
- **ICD Rules**: Application of elements is limited by the Internal Cooldown manager (`ICDManager`), classically adhering to the "every 2.5 seconds or every 3 hits" rule for specific attack groups.

These standard mechanics are computed within `DamageCalculator.java` prior to any custom Lunar additions.

## Adding New Content

### New Character
1. Create `src/model/character/<Name>.java` extending `Character`
2. Add CSV files: `config/characters/<Name>_Status.csv` and `<Name>_Multipliers.csv`
3. Register skills as `AttackAction` with `animationDuration` set (this auto-advances sim time)

### New Buff / Stat
Always add to `StatType.java` first, then reference from `DamageCalculator` and/or `CombatSimulator`.

### New Weapon / Artifact
Extend `Weapon.java` or `ArtifactSet.java`. Hook buff application into `CombatSimulator`'s reaction/action callbacks.

## CSV Format (config/characters/)

Header must be exactly: `Character,AbilityType,Key,Level,Value1,Value2`

- **AbilityType**: `NORMAL`, `CHARGED`, `PLUNGE`, `SKILL`, `BURST`, `PASSIVE` (uppercase, matches `ActionType` enum)
- **Value1/Value2**: Pure numbers only — damage ratios as decimals (e.g. `0.640` for 64.0%), no `%` or expressions
- No spaces around commas; ensure trailing newline at EOF

## Naming Conventions

- Classes/Interfaces: `PascalCase`
- Methods/variables: `camelCase`
- Enums/constants: `SCREAMING_SNAKE_CASE`
- Debug log prefixes: bracketed module tag, e.g. `[DC_DEBUG]`, `[FormulaDebug]`, `[FormulaValues]`

## Debug Outputs

- **Text log** (`sim_output*.txt`): Frame-by-frame timeline `[T=1.5] ...` — primary source for verifying correctness
- **Formula debug** in `DamageCalculator`: logs `MV`, `BaseI`, `React`, `Gear`, `Burst`, `Crit`, `Res`, `ColMult` per hit
- **HTML report**: Pie chart of damage share, cumulative damage timeline, artifact roll distribution

## Critical Constraints

When modifying the simulation, verify that changes are consistent with:
1. `CombatSimulator.advanceTime` / `performAction` time flow — `animationDuration` on `AttackAction` must correctly represent cast time
2. `DamageCalculator` formula order — Lunar multipliers apply after all other bonuses
3. MinER constraints in `ArtifactOptimizer` — ER rolls are allocated first before other substats


## Model Usage Policy

- Use the Default (recommended) model for all tasks.
- The Default model is currently Sonnet.
- Do NOT switch to Opus unless explicitly instructed by the user.
- Prefer lower-cost models unless higher capability is required.