# Genshin Impact DPS Calculator & Battle Simulator

A highly detailed, time-driven combat simulator and DPS calculator for Genshin Impact, written in Java 11+.

This project goes beyond simple formula calculations by emulating skill animations (cast times), elemental auras, Internal Cooldowns (ICD), complex reaction mechanics, team-wide buffs, and time-based combat events. It also includes a multi-dimensional artifact optimization pipeline and a Java/Python Reinforcement Learning (RL) stack for learning combat rotations.

## Live Demo

**[View Simulation Report](https://kyobankatu.github.io/GenshinImpactDPSCalculator/simulation_report.html)**

**[View Javadoc](https://kyobankatu.github.io/GenshinImpactDPSCalculator/index.html)**

The report shows a full combat simulation for a custom 4-character team, including:
- Filterable damage timeline with per-hit breakdowns, reaction labels, aura
  state, and expandable formula details
- Damage contribution and cumulative damage charts per character
- Reaction damage, reaction-labeled direct damage, action damage, rolling DPS,
  aura timeline, energy timeline, and buff uptime charts
- Optimized artifact substat distributions
- Active buff tracking per character

## Core Features

- **Time-Driven Engine**: Simulates combat frame-by-frame, properly handling action durations, buff lifecycles, and periodic damage ticks (DoTs).
- **Accurate Damage Mechanics**: Implements the official game damage formulas including base multipliers, additive flat damage, defense & resistance shredding, and elemental gauges for Amplifying, Additive, and Transformative reactions.
- **Artifact Optimization Pipeline**: Contains a two-phase optimizer (Energy Recharge calibration followed by DPS substat hill-climbing) to automatically find the optimal artifact stat distribution based on KQM standards.
- **Custom Mechanics**: Includes an extensible framework for custom, non-canonical characters with completely original buffs and synergy mechanics.
- **Interactive HTML Reports**: Automatically generates dashboard-style reports with damage contribution, cumulative damage, reaction/action damage, rolling DPS, aura, energy, buff uptime, stat snapshots, and filterable event timelines via Chart.js.
- **Hybrid RL Stack**: Experimental Java rollout service plus Python recurrent PPO learner for optimizing combat rotations without per-step Python/Java overhead.
- **Catalog-Driven Parties**: Sample simulations and RL use the same Java-side `PartyDefinition` catalog, so adding a party does not require separate simulation and RL entry points.

## Requirements

- **Java 11 or higher**
- **Python 3** for the RL learner scripts

## Build and Run

This project uses the Gradle Build Tool. A Gradle wrapper (`gradlew`) is included so you don't need to install Gradle globally.

### 1. Build the Project
Compile the source code:
```bash
./gradlew build
```

### 2. Run a Simulation
We have configured a dynamic Gradle rule that resolves party names through the shared party catalog.

```bash
# Run standard Raiden team simulation
./gradlew RaidenParty

# Run custom Flins party simulation
./gradlew FlinsParty

# Run alternate Flins party simulation used by RL
./gradlew FlinsParty2
```

### 3. Generate Javadoc
Generate the technical documentation for the core classes:
```bash
./gradlew javadoc
```
The documentation will be generated in the `build/docs/javadoc/` folder. Open `index.html` in your browser.

### 4. Run The Hybrid RL Stack

The RL system keeps rollout execution in Java and PPO training/evaluation in Python.

- Java owns simulation, action masking, reward calculation, observation encoding, and report generation.
- Python owns recurrent PPO, checkpointing, evaluation control, and optional W&B logging.
- RL-available parties are selected by name through the same Java party catalog used by sample simulations.

#### Install Python dependencies

```bash
python3 -m pip install -r requirements.txt
```

#### Local Java rollout service

The default `ServeRLJava` task runs the default single-party RL setup. To select specific parties, call the class directly:

```bash
./gradlew ServeRLJava

# Explicit single-party selection
java -cp build/classes/java/main sample.ServeRLJava 5005 127.0.0.1 4 FlinsParty2

# Multi-party selection by registry names
java -cp build/classes/java/main sample.ServeRLJava 5005 127.0.0.1 4 FlinsParty2,RaidenParty
```

Available selection styles:

- `FlinsParty2`: one registered RL party
- `RaidenParty`: one registered RL party
- `FlinsParty2,RaidenParty`: comma-separated custom catalog
- `default`: the default multi-party training catalog from `RLPartyRegistry`
- `all`: all registered RL parties

#### Capability profiles

Observation encoding includes static per-character capability features. Regenerate them when adding or materially changing RL parties:

```bash
./gradlew ProfileCapabilities

# Restrict profiling to a subset of registered RL parties
./gradlew ProfileCapabilities --args="config/capability_profiles/profiles.json FlinsParty2,RaidenParty"
```

#### Local Python training, evaluation, and benchmarking

For local debugging, start a local Java rollout service and then run Python training, evaluation, and optional benchmarking:

```bash
./gradlew ServeRLJava
./gradlew BenchmarkRLJava
python3 src/python/rl/train_recurrent_ppo.py --preset debug --updates 20 --envs 4 --rollout-length 32
python3 src/python/rl/train_recurrent_ppo.py --preset debug --wandb --wandb-project genshin-recurrent-ppo --wandb-run-name local-debug
python3 src/python/rl/evaluate_policy.py --mode both
python3 src/python/rl/benchmark_rollout.py --envs 4 --steps 128
```

Training writes `output/recurrent_ppo_py/latest-model.pt` and `output/recurrent_ppo_py/training_log.csv`.
If `.venv` includes `wandb`, training can also stream metrics to Weights & Biases with `--wandb`.
Evaluation supports `--mode deterministic|stochastic|both`. Deterministic evaluation generates `output/rl_report.html` plus party-specific files such as `output/rl_report_flinsparty2.html` when party names are available.

#### Manual multi-process or remote rollout setups

Shell scripts and sweep definitions are intentionally not documented here because
this repository ignores `*.sh` and `sweeps/`, so batch-job wrappers are not part
of the tracked project surface.

If you want to run the learner against one or more already-running rollout
services, use the Python entry points directly:

```bash
python3 src/python/rl/train_recurrent_ppo.py --preset debug --endpoints cpu-node-a:5005,cpu-node-b:5005
python3 src/python/rl/evaluate_policy.py --mode both --endpoints cpu-node-a:5005
python3 src/python/rl/benchmark_rollout.py --envs 8 --steps 128 --endpoints cpu-node-a:5005,cpu-node-b:5005
```

For single-node evaluation without any wrapper scripts, start
`sample.ServeRLJava` separately and then run:

```bash
python3 src/python/rl/evaluate_policy.py --mode both --checkpoint output/recurrent_ppo_py/latest-model.pt
```

## Architecture

1. **Party & Characters**: Character/item models live under `src/java/model/`; runnable party setups and rotations live under `src/java/simulation/party/` as cataloged `PartyDefinition`s.
2. **OptimizerPipeline**: Runs before the final scripted simulation and computes ER requirements plus substat allocations.
3. **CombatSimulator**: The core time-driven engine. Tracks time, event ordering, ICD counters, auras, swaps, buffs, and periodic effects.
4. **Visualization**: `VisualLogger` and `HtmlReportGenerator` turn one simulation into an inspectable HTML report.
5. **Java RL Layer**: `mechanics.rl` provides action masking, observation encoding, reward logic, generic party-backed RL factories, vectorized rollout, and the local rollout service.
6. **Python RL Layer**: `src/python/rl/` provides recurrent PPO training, checkpoint loading, deterministic/stochastic evaluation, rollout benchmarking, and W&B metric logging.

## Agent Workflows

The repository includes project-specific workflows for both Codex and Claude.
Canonical skills live under `.agents/skills/`; `.claude/skills/` exposes the
same catalog to Claude while routing detailed instructions to the canonical
copy. The catalog covers simulator changes, mechanic accuracy, hybrid RL,
optimizer benchmarking, HTML reports, native HPC rollout operation, new
content, sourced game research, durable experiments, safe artifact cleanup,
result presentation, and explicitly requested agent coordination.

It also covers the day-to-day development loop: `plan-genshin-implementation`
for `TASKS.md` phase plans, `apply-genshin-code-style` for the mandatory
notation and Javadoc rules, `verify-genshin-changes` for the pre-commit check
set, `manage-genshin-git` for branch and commit hygiene,
`submit-genshin-gpu-job` for the concrete `ybatch` submission path on this
machine, `diagnose-genshin-training` for misbehaving PPO runs,
`run-genshin-autonomous-session` for long unattended sessions, and
`discover-genshin-work` to replenish the queue once the active plan is finished.

`TASKS.md` holds the work that is being done. `BACKLOG.md` is the durable ledger
of discovered candidates and settled decisions, so a long session does not
rediscover or re-litigate the same item after a restart.

Validate skill discovery and references with:

```bash
python scripts/validate_agent_assets.py
```

Plan the smallest checks for changed paths without executing them:

```bash
python scripts/agent_validate.py --path src/java/mechanics/reaction/ReactionSystem.java
python scripts/agent_validate.py --base origin/master
```

Pass `--run` only when the printed Gradle/Python checks are intended. This tool
does not submit scheduler jobs or start persistent rollout services.

Gate a change set before committing. This adds a leak check that rejects staged
paths the repository keeps untracked on purpose:

```bash
python scripts/preflight.py
python scripts/preflight.py --run
```

## Accuracy Notes

`TASKS.md` tracks the current implementation plan and latest audit notes. The
current audited benchmark parties are `RaidenParty` and `FlinsParty2`.

Known simplifications:

- `RaidenParty`: Xingqiu's contact-based orbital Rain Swords are modeled as
  zero-damage 1U Hydro pulses every 2.25 seconds, while his separate Raincutter
  sword waves deal Burst damage. Continuous enemy contact is assumed. The party
  definition explicitly opts into Xiangling Chili pickup and uses a fixed
  per-simulator Skyward Spine proc seed so optimizer candidates and sample runs
  are reproducible. Generic Skyward Spine construction remains stochastic, and
  generic Xiangling simulations do not pick up the chili unless their party
  definition enables that assumption. Artifact allocation includes Emblem's
  static 20% ER; the accepted set-aware result is 1,317,080 damage / 62,718 DPS
  over 21.0 seconds.
- `FlinsParty2`: defensive shield HP is logged but not consumed by enemy attacks,
  Columbina treats every Lunar reaction during Gravity Ripple as nearby because
  field position is not simulated, and her Thundercloud extra strikes use 33%
  expected damage. Her Moondrift extra attacks remain random. Flins's Thunderous
  Symphony correctly uses its active 30-energy cost while retaining an 80-energy
  maximum. Viridescent Venerer applies one independently refreshed 40% shred per
  Swirled element only when its on-field owner triggers the reaction. The
  accepted result is 13,633,123 damage / 197,296 DPS over 69.1 seconds.
- `FlinsParty`: generic Favonius Codex and Columbina construction remains
  stochastic, while this optimizer-driven sample injects independent fixed
  Windfall and Moondrift streams so every candidate and final run uses the same
  random scenario. Its legal cadence requests one Sucrose Burst in each of
  three outer loops; artifact allocation rejects unmet ER targets instead of
  silently returning an underfilled build. Wandering Evenstar snapshots the
  owner's effective EM after 64 frames and every 10 seconds for its linked
  owner/team ATK buffs. Viridescent Venerer uses the same non-stacking,
  owner-triggered shred contract. The accepted result is 18,343,092 damage / 184,353 DPS
  over 99.5 seconds with three successful Sucrose Bursts.

### Continuous Aura Decay Model

Enemy elemental auras decay continuously over time rather than persisting at full
strength until a fixed expiry. The model is intentionally simplified:

- **Duration formula**: a runtime-applied aura lasts `6 + units * 5` seconds
  (1U = 11 s, 2U = 16 s, 4U = 26 s).
- **Linear decay**: units fall linearly from the applied value to zero across that
  duration at a fixed per-aura rate (`units / duration` units per second).
- **Discrete consumption**: reactions (Vaporize, Swirl, Electro-Charged ticks,
  Burning maintenance, Quicken, etc.) consume the decayed *current* value at the
  reaction time, then natural decay resumes from the remaining units.
- **Single source of truth**: reaction eligibility/consumption, combat logs, the
  HTML Aura Timeline, RL observations, and snapshot save/restore all read the same
  current-time-aware aura value. Snapshots preserve application time and duration
  so decay resumes correctly after rollback.
- **Aura Timeline**: rendered as continuous (non-stepped) lines that slope down to
  zero at expiry, matching the per-event aura bars in the Timeline view.

Known differences from exact game internals: the real game uses non-linear gauge
decay and hidden reaction "tax" rules; this simulator uses the linear duration
model above and does not model multi-target or per-enemy aura gauges.

Latest validation baseline from the accuracy pass:

- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`: 1,317,080 total damage / 62,718 DPS
- `./gradlew FlinsParty2`: 13,633,123 total damage / 197,296 DPS
- `./gradlew BenchmarkRLJava`
- `./gradlew ProfileCapabilities`
