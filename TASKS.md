# Python Learner Migration Tasks

## Goal

Move the RL training stack to a hybrid architecture:

- Java owns the combat simulator and environment execution
- Python owns the `Recurrent PPO` learner, experiment control, and GPU training

The target is higher policy quality and faster iteration by using the stronger Python RL ecosystem without falling back to slow per-step socket communication.

## Strategic Decision

We will not continue investing in a Java-native learner as the main training path.

The long-term architecture is:

- `CombatSimulator` remains in Java
- rollout generation remains close to `CombatSimulator`
- `Recurrent PPO` training moves to Python
- communication must happen at rollout or batch granularity, not one step at a time

## Why This Direction

This project needs:

- long-horizon credit assignment
- recurrent policies
- GPU training
- fast algorithm iteration
- robust logging, checkpointing, and experiment management

Python is significantly better than Java for:

- RL library availability
- recurrent PPO implementations
- distributed rollout/training tooling
- hyperparameter tuning
- experiment analysis

Java is significantly better than Python for:

- reusing the existing battle simulator directly
- avoiding a costly rewrite of combat mechanics
- keeping the authoritative environment close to existing simulation code

So the right split is not "all Java" or "all Python".
It is:

- Java environment
- Python learner

## Non-Negotiable Requirements

- No return to the old per-step Python-Java socket protocol
- No JSON per step in the hot path
- No blocking request/response loop for every action
- No teacher forcing
- No scripted next-action hints in observations
- No Q-table learner
- No Q-table persistence

## Target Architecture

### Java Side

- owns `CombatSimulator`
- owns environment reset and step logic
- owns observation encoding and reward computation
- owns action masking
- owns rollout collection close to the simulator
- exposes a transport layer that can send and receive rollout-sized or batch-sized data efficiently

### Python Side

- owns `Recurrent PPO`
- owns neural network policy/value model
- owns optimization loop
- owns checkpointing
- owns evaluation orchestration
- owns experiment tracking and tuning

### Data Exchange Boundary

The boundary between Java and Python must exchange one of these units:

- full rollout segments
- fixed-length trajectory batches
- batched policy inference requests for many environment slots at once

The boundary must not exchange:

- one action request per environment step
- one observation packet per environment step

## Preferred Communication Model

The default design should be:

- Java runs many environment slots in parallel
- Python owns the learner process
- Java sends batched observations for many env slots at once
- Python returns batched action logits, values, and recurrent states
- Java continues stepping locally until the next inference boundary
- Java returns rollout segments back to Python for PPO updates

This keeps the simulator authoritative while avoiding the old worst-case communication pattern.

## Transport Priorities

The communication layer should be chosen in this priority order:

1. shared-memory or zero-copy friendly IPC if feasible
2. binary framed local IPC
3. local TCP only if batching is large enough and overhead is proven acceptable

Avoid:

- text protocols
- JSON in the hot path
- ad hoc string parsing for observations or actions

## Rebuild Scope

### Phase 1: Remove Java-Native Learner As The Main Path

- Stop treating the current Java-native learner as the target architecture.
- Deprecate or remove components that only exist to train PPO fully inside Java.
- Keep only the parts that remain valid in the hybrid design:
  - environment logic
  - action space
  - observation encoding
  - reward function
  - simulator factory
  - evaluation report generation

### Phase 2: Stabilize The Java Environment Contract

- Define a clean Java-side RL environment contract:
  - `reset`
  - `step`
  - `done`
  - `observation`
  - `action_mask`
  - `reward`
  - `episode_summary`
- Freeze the discrete action space:
  - normal attack
  - skill
  - burst
  - swap to each party member
- Freeze the observation schema and version it explicitly.
- Freeze action-mask semantics and version them explicitly.

### Phase 3: Redesign The Hot Path Around Batches

- Replace any residual step-by-step learner coupling with batch-oriented interfaces.
- Add Java-side components such as:
  - `InferenceRequestBatch`
  - `InferenceResponseBatch`
  - `RolloutSegment`
  - `RolloutBatch`
  - `EpisodeSummary`
- Batch dimensions must support:
  - many environments per request
  - recurrent hidden states per environment slot
  - action masks per environment slot

### Phase 4: Build Parallel Java Rollout Collection

- Keep rollout execution on the Java side.
- Add or harden:
  - `RolloutWorker`
  - `VectorizedEnvironment`
  - `RolloutManager`
- Required properties:
  - multiple workers
  - multiple environments per worker
  - reusable simulator instances where practical
  - minimal allocation pressure in hot loops
  - no file output during training rollouts

### Phase 5: Add A Production-Quality Local IPC Layer

- Build a local-only binary IPC transport between Java and Python.
- The transport must support:
  - batched inference calls
  - rollout segment transfer
  - checkpoint/eval commands if needed
  - schema version checking
- Evaluate framing and serialization options such as:
  - FlatBuffers
  - Protocol Buffers
  - Arrow IPC
  - custom binary framing if measurably simpler and faster

### Phase 6: Create The Python Training Workspace

- Reintroduce a Python training workspace as a first-class part of the repo.
- The Python side should include:
  - environment connector client
  - recurrent PPO trainer
  - config management
  - checkpoint handling
  - evaluation scripts
  - throughput and training diagnostics
- Candidate frameworks:
  - `sb3-contrib` if the connector shape fits cleanly
  - `CleanRL` if we want maximum control with moderate implementation effort
  - `RLlib` if distributed training is worth the added complexity

The initial recommendation is:

- use `CleanRL` or a similarly explicit codebase first

because the Java boundary and batching constraints will likely require custom integration.

### Phase 7: Implement Python Recurrent PPO

- Python learner must support:
  - recurrent policy
  - recurrent value head
  - PPO clipping
  - entropy bonus
  - value loss
  - GAE
  - minibatch SGD
  - checkpoint save/load
  - deterministic evaluation mode
- Hidden-state handling must align exactly with Java environment slot boundaries.

### Phase 8: Optimize The Java-Python Boundary

- Measure:
  - inference round-trip time
  - rollout transfer time
  - effective environment steps per second
  - GPU utilization
  - CPU worker utilization
- Optimize until the bottleneck is no longer obviously IPC overhead.
- Specific goals:
  - inference is batched
  - rollout collection is parallel
  - learner is mostly GPU-bound or rollout-bound, not protocol-bound

### Phase 9: Add Evaluation And Report Flow

- Evaluation should run saved Python checkpoints against the Java simulator.
- Java remains responsible for combat report generation.
- Python should be able to request:
  - deterministic evaluation
  - stochastic evaluation
  - HTML report generation for selected episodes only

### Phase 10: Update Commands And Tooling

- Replace the current Java-only RL commands with hybrid commands.
- Example target command groups:
  - start Java rollout service
  - run Python training
  - run Python evaluation
  - run throughput benchmark
- Ensure local development and cluster execution both have documented paths.

### Phase 11: Throughput-Oriented Design Constraints

- Java side must support:
  - many environment slots in parallel
  - batched inference requests
  - minimal per-step allocation
  - muted logging during training
- Python side must support:
  - large learner minibatches
  - GPU-friendly tensor packing
  - recurrent sequence batching
  - efficient checkpointing

### Phase 12: Documentation Refresh

- Update:
  - root `AGENTS.md`
  - `README.md`
  - `src/java/mechanics/rl/AGENTS.md`
  - `src/java/sample/AGENTS.md`
- Add documentation for the new Python training workspace:
  - environment connector design
  - how to start Java-side services
  - how to run Python training
  - where checkpoints and logs are stored

## Deliverables

The migration is complete only when all of the following are true:

- Java is the authoritative combat environment
- Python is the authoritative learner
- the old per-step socket protocol is gone
- communication is rollout-sized or batch-sized
- recurrent PPO training runs end-to-end with the Java simulator
- saved Python checkpoints can be evaluated against Java combat episodes
- HTML combat report generation still works in evaluation mode
- documentation matches the hybrid architecture

## Verification

Minimum required checks before handoff:

- `./gradlew build`
- Java-side rollout service or batch environment starts successfully
- Python trainer can connect and complete at least one training iteration
- Python evaluation can load a checkpoint and run one episode
- throughput benchmark reports batch-oriented collection numbers
- HTML report generation works in evaluation mode

## Non-Goals

- preserving the old per-step Python-Java communication path
- keeping the Java-native learner as the primary training method
- preserving Q-table artifacts
- preserving teacher-guided state design

## Notes

- The key mistake to avoid is moving back to a step-by-step RPC design.
- If batching is done correctly, the hybrid design should be easier to improve than the all-Java learner.
- The first success criterion is architectural correctness and acceptable throughput.
- The next success criterion is policy quality.
