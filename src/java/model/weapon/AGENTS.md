# AGENTS.md

## Scope
- This file applies to `src/java/model/weapon/`.

## Directory role
- This package contains concrete weapon implementations and their passive logic.
- Most classes are lightweight stat carriers, but several implement stateful passives tied to actions, damage events, reactions, or switch timing.

## Java files in this directory
- `AlleyFlash.java`: sword with fixed base stats and unconditional all-damage bonus.
- `AmenomaKageuchi.java`: sword with Succession Seed tracking and delayed burst energy refund.
- `CalamityQueller.java`: polearm with persistent elemental damage bonus and stateful stacking ATK passive driven by skill use and timed stack gain.
- `Deathmatch.java`: polearm whose passive depends on single-target versus multi-target mode.
- `DragonsBane.java`: polearm that evaluates its Hydro/Pyro target condition for every direct hit.
- `FavoniusCodex.java`: catalyst with injectable crit-based particle generation draws and an internal cooldown.
- `NocturnesCurtainCall.java`: catalyst for Lunar teams with HP bonus, Lunar-triggered energy recovery, and temporary Lunar crit-damage buff.
- `PrimordialJadeWingedSpear.java`: polearm with on-hit stack tracking, timed expiration, and max-stack damage bonus.
- `ProspectorShovel.java`: custom polearm with Electro-Charged bonus and Moonsign-gated Lunar-Charged bonus.
- `SacrificialSword.java`: R5 sword with an injectable Skill-damage reset draw
  and a sixteen-second Composed cooldown.
- `SkywardBlade.java`: sword with burst-triggered buff window and extra physical proc on normal or charged actions.
- `SkywardSpine.java`: polearm with crit-rate and attack-speed stats plus random vacuum-blade proc logic.
- `SunnyMorningSleepIn.java`: catalyst with reaction-listener registration and separate EM buffs for swirl, skill hits, and burst hits.
- `TheCatch.java`: polearm with burst damage and burst crit passive.
- `WanderingEvenstar.java`: catalyst that snapshots the wielder's effective EM on its timed trigger and derives source-attributed self and team flat-ATK buffs.
- `WolfFang.java`: sword with skill and burst damage bonus plus on-field crit-rate stack tracking for skill and burst hits.

## Coupling and dependencies
- All classes extend `model.entity.Weapon`.
- Stateful passives depend on `simulation.CombatSimulator`, `simulation.action.AttackAction`, `mechanics.buff.Buff`, `mechanics.energy.EnergyManager`, or focused capability interfaces such as action-triggered, damage-triggered, switch-aware, team-buff, or reaction-listener behavior.
- Weapon passives are applied during `Character` stat assembly and triggered via typed action dispatch or `DamageCalculator` target/damage hooks only when the weapon implements the relevant capability.
- Several weapons depend on custom Lunar state or on names and timing established elsewhere in the simulator.

## Agent guidance
- Before editing a weapon, confirm whether its passive is stat-only, action-triggered, damage-triggered, reaction-triggered, or switch-stateful.
- Be cautious with randomness. Random procs can destabilize optimizer or RL behavior if introduced into heavily reused paths.
- Keep stochastic default constructors for general simulations, but inject the
  draw source when a benchmark or optimizer requires common reproducible random
  numbers. Reject null sources at construction.
- If a weapon grants team buffs, implement `WeaponTeamBuffProvider` and audit `Character.getEffectiveStats` behavior to avoid recursive stat loops.
- Use `SimulatorInitializedWeaponEffect` when a weapon must own a periodic trigger; derive every linked buff from one captured stat view rather than separate assembly stages.
- Prefer implementing the narrowest capability interface instead of adding optional methods to `Weapon`.
