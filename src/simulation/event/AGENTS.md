# AGENTS.md

## Scope
- This file applies to `src/simulation/event/`.

## Directory role
- This package defines the timer-event contract and the stock recurring event implementations used by the simulator queue.

## Java files in this directory
- `TimerEvent.java`: core event-queue interface for any time-driven simulator event.
- `SimpleTimerEvent.java`: convenience recurring event base with fixed interval and explicit early-finish support.
- `PeriodicDamageEvent.java`: recurring event that repeatedly executes an `AttackAction`, optionally with an extra callback.

## Coupling and dependencies
- `simulation.CombatSimulator` schedules and executes these events.
- Character, weapon, and optimization code create these events for summons, periodic attacks, delayed refunds, and recurring state changes.
- `PeriodicDamageEvent` depends on `simulation.action.AttackAction` and uses simulator no-time-advance execution.

## Agent guidance
- If you change event semantics, audit all summoning characters and timed weapon passives.
- Be careful with event completion conditions. Off-by-one timing changes can materially alter DPS and energy results.
- Keep timer events generic. Content-specific rules belong in the caller-provided callback or character class.
