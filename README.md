# Genshin Impact DPS Calculator & Battle Simulator

A highly detailed, time-driven combat simulator and DPS calculator for Genshin Impact, written in Java 11+.

This project goes beyond simple formula calculations by emulating skill animations (cast times), elemental auras, Internal Cooldowns (ICD), complex reaction mechanics, team-wide buffs, and time-based combat events. It also includes a multi-dimensional artifact optimization pipeline and a Java/Python Reinforcement Learning (RL) stack for learning combat rotations.

## Live Demo

**[View Simulation Report](https://kyobankatu.github.io/GenshinImpactDPSCalculator/simulation_report.html)**

**[View Javadoc](https://kyobankatu.github.io/GenshinImpactDPSCalculator/index.html)**

The report shows a full combat simulation for a custom 4-character team, including:
- Damage timeline with per-hit breakdowns and reaction labels
- Damage contribution pie chart per character
- Optimized artifact substat distributions
- Active buff tracking per character

## Core Features

- **Time-Driven Engine**: Simulates combat frame-by-frame, properly handling action durations, buff lifecycles, and periodic damage ticks (DoTs).
- **Accurate Damage Mechanics**: Implements the official game damage formulas including base multipliers, additive flat damage, defense & resistance shredding, and elemental gauges for Amplifying, Additive, and Transformative reactions.
- **Artifact Optimization Pipeline**: Contains a two-phase optimizer (Energy Recharge calibration followed by DPS substat hill-climbing) to automatically find the optimal artifact stat distribution based on KQM standards.
- **Custom Mechanics**: Includes an extensible framework for custom, non-canonical characters with completely original buffs and synergy mechanics.
- **Interactive HTML Reports**: Automatically generates visual timeline records, pie charts for damage contribution, and character stat snapshots via Chart.js.
- **Hybrid RL Stack**: Experimental Java rollout service plus Python recurrent PPO learner for optimizing combat rotations without per-step Python/Java overhead.
- **Registry-Driven RL Parties**: RL training, evaluation, capability profiling, and benchmarking all share one Java-side party registry, so adding a new RL party no longer requires editing multiple launch paths.

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
We have configured a dynamic Gradle rule that allows you to run any simulation class located in the `sample` package directly by its name.

```bash
# Run standard Raiden team simulation (sample.RaidenParty)
./gradlew RaidenParty

# Run custom Flins party simulation (sample.FlinsParty)
./gradlew FlinsParty

# Run alternate Flins party simulation used by RL (sample.FlinsParty2)
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
- RL-available parties are selected by name through the Java party registry.

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

1. **Party & Characters**: Defined under `src/java/model/`. Base stats, weapons, and artifact sets are assembled into a `CombatSimulator`.
2. **OptimizerPipeline**: Runs before the final scripted simulation and computes ER requirements plus substat allocations.
3. **CombatSimulator**: The core time-driven engine. Tracks time, event ordering, ICD counters, auras, swaps, buffs, and periodic effects.
4. **Visualization**: `VisualLogger` and `HtmlReportGenerator` turn one simulation into an inspectable HTML report.
5. **Java RL Layer**: `mechanics.rl` provides action masking, observation encoding, reward logic, party registry, vectorized rollout, and the local rollout service.
6. **Python RL Layer**: `src/python/rl/` provides recurrent PPO training, checkpoint loading, deterministic/stochastic evaluation, rollout benchmarking, and W&B metric logging.
