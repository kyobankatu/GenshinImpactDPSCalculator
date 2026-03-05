# Genshin Rotation Optimization (RL)

This project uses Reinforcement Learning (RL) to optimize character rotation and skill usage in Genshin Impact.
It communicates with the Java-based simulation engine via a TCP Socket connection.

## Architecture

```mermaid
graph LR
    subgraph Python [Python RL Agent]
        A[RL Agent (Stable-Baselines3)] -->|Action ID| B[GenshinEnv (Gymnasium)]
        B -->|State Vector + Reward| A
    end

    subgraph Java [Java Simulation Engine]
        C[RLServer (Socket Listener)] <-->|TCP/IP| B
        C -->|Action| D[CombatSimulator]
        D -->|Game State| C
    end
```

## Setup

1.  **Java Side**:
    *   Implement `RLServer.java` entry point.
    *   Start the server (e.g., `java -cp bin mechanics.rl.RLServer`).
2.  **Python Side**:
    *   Install dependencies: `pip install -r requirements.txt`
    *   Run training: `python train.py`

## State & Action Space

*   **State**: [HP, Energy, Cooldowns, Buff Durations, Enemy Aura...] (Normalized Float Vector)
*   **Action**: Discrete ID (0: Attack, 1: Skill, 2: Burst, 3: Swap...)
*   **Reward**: Damage dealt in the last tick/action.
