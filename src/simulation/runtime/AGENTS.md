# AGENTS.md

## Scope
- This file applies to `src/simulation/runtime/` unless a deeper `AGENTS.md` overrides it.

## Directory role
- `runtime` holds helper collaborators extracted from `simulation.CombatSimulator`.
- Classes here should own narrow runtime policies such as event dispatch, buff aggregation, swap handling, action gating, timeline progression, and transient combat state.
- This package exists to reduce orchestration pressure on `CombatSimulator` without changing the public simulator API more than necessary.

## Java files in this directory
- `ActionGateway.java`: typed action cooldown and energy gating before dispatch to characters and weapons, with boundary labels used only for compatibility and logging.
- `ActionTimelineExecutor.java`: post-resolution action sequencing, action listener timing, animation duration scaling, Moonsign follow-up timing, and timeline advancement.
- `BuffManager.java`: simulator-owned team and field buff storage plus applicable-buff assembly.
- `CombatActionResolver.java`: resolves attack actions into damage, aura changes, reactions, and follow-up effects without advancing time.
- `DamageReport.java`: cumulative damage accounting and DPS summary formatting.
- `MoonsignManager.java`: party-wide Moonsign derivation and Ascendant Blessing handling.
- `ReactionState.java`: transient Electro-Charged and Thundercloud state owned by the simulator.
- `ReactionStateController.java`: focused controller exposing reaction-state convenience operations without leaking direct `ReactionState` access back into `CombatSimulator`.
- `SimulationClock.java`: current time, rotation time, timer queue, and due-event execution.
- `SimulationEventDispatcher.java`: listener registration and event fan-out for actions, particles, and reactions.
- `SwitchManager.java`: swap cooldown, switch callbacks, and swap timeline logging.
- `VisualLoggerSink.java`: adapter from simulator logging calls to `visualization.VisualLogger`.

## Coupling and dependencies
- Most classes in this package depend on `simulation.CombatSimulator` as the shared runtime façade.
- Runtime helpers also interact heavily with `model.entity.*`, `mechanics.*`, `simulation.action.*`, and `simulation.event.*`.
- Keep dependencies directional: prefer `CombatSimulator -> runtime helper -> mechanics/model` rather than helpers calling each other indirectly through unrelated packages.
- Runtime identity should stay typed (`CharacterId`, `CharacterActionKey`, `BuffId`, reaction metadata). String labels are adapter, log, or report data.

## Agent guidance
- Keep each runtime class focused on one simulator concern. If a helper starts owning multiple independent policies, split it again instead of re-creating a god class under a new name.
- Preserve `CombatSimulator` as the main public entry point for callers in `sample`, `mechanics`, and `model`.
- Prefer extracting policy objects and state holders here rather than adding more flags and branches back into `CombatSimulator`.
- When editing sequencing logic, validate the smallest affected flow first: swaps, event timing, reactions, buffs, and logging can regress together.
- Avoid moving game-specific formulas into this package unless the class is explicitly about runtime orchestration rather than reusable mechanics math.
- Keep no-stack buff replacement typed; do not restore display-name fallback removal in `BuffManager`.
