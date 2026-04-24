# AGENTS.md

## Scope
- This file applies to `src/java/mechanics/energy/`.

## Directory role
- This package handles particle and flat energy distribution, plus particle-size definitions.

## Java files in this directory
- `EnergyManager.java`: distributes particles and flat energy to party members and schedules standard enemy particle drops for benchmark rotations.
- `ParticleType.java`: enumerates particle and orb base values for same-element and off-element energy gain.

## Coupling and dependencies
- `EnergyManager` depends on `simulation.CombatSimulator`, `model.entity.Character`, `model.type.Element`, `model.type.StatType`, and `simulation.event.TimerEvent`.
- `model.character` classes call `EnergyManager` directly when skills, bursts, and summons generate particles.
- `simulation.CombatSimulator` exposes particle listeners that `EnergyManager` notifies after distribution.
- `mechanics.analysis.EnergyAnalyzer` relies on energy-tracking side effects caused by this package.

## Agent guidance
- When changing particle flow, audit both gameplay behavior and optimizer behavior because ER targets will shift.
- Distinguish carefully between particle energy, which is ER-scaled and range-penalized, and flat energy, which bypasses both.
- If you add a new energy source, decide whether it should notify particle listeners or remain a flat-energy effect.
