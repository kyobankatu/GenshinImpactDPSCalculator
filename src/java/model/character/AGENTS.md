# AGENTS.md

## Scope
- This file applies to `src/java/model/character/`.

## Directory role
- This package contains the concrete playable character implementations.
- Each class encodes action dispatch, cooldown behavior, base stats, skill or burst logic, particle generation, passives, and any simulator listeners specific to that character.

## Java files in this directory
- `Bennett.java`: Pyro sword support with burst field ATK buff, optional infusion logic, healing log output, and simple normal or charged attack handling.
- `Columbina.java`: custom Lunar Hydro character with Gravity Ripple, Lunar Domain, Gravity accumulation, Dew resources, interference triggers, and simulator reaction-listener behavior.
- `Flins.java`: custom Lunar Electro polearm DPS with form switching, alternate burst cost and state, Lunar base-bonus team support, and constellation-driven reaction-listener behavior.
- `Ineffa.java`: custom Lunar Electro support with shielded skill summon, burst-driven EM sharing, Birgitta periodic attacks, and permanent Lunar base-bonus team buff generation.
- `RaidenShogun.java`: Electro burst carry with resolve-stack listeners, Musou state energy restoration, coordinated Eye attacks, and burst-mode attack conversion.
- `Sucrose.java`: Anemo catalyst support with EM-sharing passives, burst absorption, swirl-adjacent party buffs, and multi-charge skill handling.
- `Xiangling.java`: Pyro polearm sub-DPS with Guoba periodic attacks, Pyronado snapshot behavior, chili ATK buff, and constellation effects.
- `Xingqiu.java`: Hydro sword sub-DPS with Raincutter trigger listener, orbital hits, burst-wave pattern logic, and skill or burst scaling interactions.

## Coupling and dependencies
- All classes extend `model.entity.Character`.
- Most characters depend on inherited `mechanics.data.TalentDataSource` access, `simulation.CombatSimulator`, `simulation.action.AttackAction`, `simulation.action.CharacterActionRequest`, `mechanics.energy.EnergyManager`, and `model.type` enums.
- Summon or periodic-damage characters depend on `simulation.event.PeriodicDamageEvent` or `SimpleTimerEvent`.
- Custom Lunar characters depend on `simulation.CombatSimulator` Lunar state, `mechanics.buff`, `mechanics.reaction`, and Lunar-specific stats in `model.type.StatType`.
- Several characters trigger artifact and weapon capability hooks implicitly by using simulator action flow rather than calling item logic themselves.
- Characters have typed `CharacterId` identity; `name` is still used as a display label and as a CSV lookup key.

## Agent guidance
- Character behavior often spans constructor data loading, `applyPassive`, `onAction`, cooldown helpers, periodic events, and listener registration. Audit the whole file before editing.
- Keep simulator-facing action keys and boundary labels stable unless you also update sample rotations, RL code, or profile files that call them.
- If you touch energy generation, burst activity windows, or snapshot behavior, re-check optimizer and report outputs.
- Use typed action requests and character IDs for new runtime logic; only adapt strings at sample/profile/report boundaries.
