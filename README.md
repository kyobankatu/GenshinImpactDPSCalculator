# Genshin Impact DPS Calculator & Battle Simulator

A highly detailed, time-driven combat simulator and DPS calculator for Genshin Impact, written in Java 11+.

This project goes beyond simple formula calculations by emulating skill animations (cast times), elemental auras, Internal Cooldowns (ICD), complex reaction mechanics, team-wide buffs, and time-based combat events. It uniquely features a multi-dimensional artifact optimization pipeline and an experimental Reinforcement Learning (RL) training module.

## Live Demo

**[View Simulation Report](https://kyobankatu.github.io/GenshinImpactDPSCalculator/simulation_report.html)**

**[View Javadoc](https://kyobankatu.github.io/GenshinImpactDPSCalculator/index.html)**

The report shows a full combat simulation for a custom 4-character team (FlinsParty2), including:
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

## Requirements

- **Java 11 or higher**

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
```

*(Note: If you encounter classpath issues, you can also compile manually using `javac -cp src -d bin $(find src -name "*.java")` and run from the `bin` directory)*

### 3. Generate Javadoc
Generate the technical documentation for the core classes:
```bash
./gradlew javadoc
```
The documentation will be generated in the `build/docs/javadoc/` folder. Open `index.html` in your browser.

### 4. Run The Hybrid RL Stack
For local debugging, start a local Java rollout service and then run Python training, evaluation, and optional benchmarking:

```bash
python3 -m pip install -r requirements.txt
./gradlew ServeRLJava
./gradlew BenchmarkRLJava
python3 src/python/rl/train_recurrent_ppo.py --preset debug --updates 20 --envs 4 --rollout-length 32
python3 src/python/rl/train_recurrent_ppo.py --preset debug --wandb --wandb-project genshin-recurrent-ppo --wandb-run-name local-debug
python3 src/python/rl/evaluate_policy.py --mode both
python3 src/python/rl/benchmark_rollout.py --envs 4 --steps 128
```

Training writes `output/recurrent_ppo_py/latest-model.pt` and `output/recurrent_ppo_py/training_log.csv`.
If `.venv` includes `wandb`, training can also stream metrics to Weights & Biases with `--wandb`.
Evaluation supports `--mode deterministic|stochastic|both` and deterministic evaluation generates `output/rl_report.html`.

For cluster training, the default recommendation is the simpler single-node setup:

```bash
ybatch execute.sh
```

- `execute.sh` starts a local Java rollout service and the Python learner in the same batch job
- this is currently the preferred production path because split-node rollout did not improve throughput enough to justify the added orchestration cost

For short rollout-parallelism diagnosis runs, `execute.sh` also accepts environment overrides:

```bash
TRAIN_PROFILE=diagnosis TRAIN_ENVS=12 JAVA_ROLLOUT_WORKERS=4 WANDB_GROUP=rollout-grid ybatch execute.sh
TRAIN_PROFILE=diagnosis TRAIN_ENVS=16 JAVA_ROLLOUT_WORKERS=8 WANDB_GROUP=rollout-grid ybatch execute.sh
```

- `TRAIN_PROFILE=diagnosis` shortens the run for throughput comparison
- `TRAIN_ENVS` controls the vectorized environment count
- `JAVA_ROLLOUT_WORKERS` overrides the Java-side worker pool size
- `WANDB_GROUP` helps compare the resulting runs together in Weights & Biases

If you want to use native W&B sweep configuration instead of manual batch overrides:

```bash
wandb sweep sweeps/rollout_parallelism.yaml
SWEEP_ID=katumon/genshin-recurrent-ppo/<sweep-id> ybatch execute_sweep_agent.sh
```

- `sweeps/rollout_parallelism.yaml` defines a grid over `envs` and `rollout_workers`
- `execute_sweep_agent.sh` runs `wandb agent` on a compute node and each trial launches a local Java rollout service plus the Python learner
- each trial writes its rollout log under `logs/sweep_rollout_<wandb-run-id>_5005.log`

The split-node scripts are still available for later experiments:

```bash
ybatch execute_rollout.sh
ybatch execute_learner.sh
```

You can also target remote rollout services manually:

```bash
python3 src/python/rl/train_recurrent_ppo.py --preset debug --endpoints cpu-node-a:5005,cpu-node-b:5005
python3 src/python/rl/evaluate_policy.py --mode both --endpoints cpu-node-a:5005
python3 src/python/rl/benchmark_rollout.py --envs 8 --steps 128 --endpoints cpu-node-a:5005,cpu-node-b:5005
```

## Architecture

1. **Party & Characters**: Defined in `/model/entity/` and `/model/character/`. Base stats, weapons, and artifact sets are assembled into a `Party`.
2. **OptimizerPipeline**: Initiates optimization before the actual combat simulation starts. Computes ER requirements and allocates substat rolls.
3. **CombatSimulator**: The core driver. Tracks time, manages the event queue (attacks, swaps, periodic ticks), ICD counters, and active buffs.
4. **DamageCalculator**: Pure mathematical functions utilizing `StatsContainer` snapshots to resolve exactly how much damage a hit deals, including special non-canonical branches.
5. **VisualLogger / HtmlReportGenerator**: Records all events for debugging and spits out a graphical HTML report upon simulation completion.
6. **Hybrid RL Stack**: Keeps environment stepping in Java near `CombatSimulator` while a Python recurrent PPO learner communicates through a binary batch protocol that supports both local and split-node rollout endpoints.
