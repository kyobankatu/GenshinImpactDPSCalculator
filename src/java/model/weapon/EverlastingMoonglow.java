package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Everlasting Moonglow with Max-HP Normal scaling and post-Burst Energy. */
public final class EverlastingMoonglow extends Weapon implements
        ActionTriggeredWeaponEffect,
        DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double ENERGY_WINDOW_DURATION = 12.0;
    private static final double ENERGY_COOLDOWN = 0.1;
    private static final double ENERGY_RECOVERY = 0.6;

    private final int refinement;
    private final double healingBonus;
    private final double maxHpNormalRatio;
    private Character owner;
    private CombatSimulator simulator;
    private double energyWindowUntil = Double.NEGATIVE_INFINITY;
    private double nextEnergyTime = Double.NEGATIVE_INFINITY;

    /** Constructs Everlasting Moonglow at refinement rank five. */
    public EverlastingMoonglow() {
        this(5);
    }

    /** Constructs Everlasting Moonglow at the selected refinement rank. */
    public EverlastingMoonglow(int refinement) {
        super("Everlasting Moonglow", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        healingBonus = 0.075 + 0.025 * refinement;
        maxHpNormalRatio = 0.005 + 0.005 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.HP_PERCENT, 0.496);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional Healing Bonus. */
    public double getHealingBonus() {
        return healingBonus;
    }

    /** Returns the final-Max-HP ratio added to Normal damage. */
    public double getMaxHpNormalRatio() {
        return maxHpNormalRatio;
    }

    /** Binds mutable Energy state to one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Everlasting Moonglow owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Everlasting Moonglow is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have this Everlasting Moonglow equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    /** Applies permanent Healing Bonus and Max-HP Normal conversion. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.HEALING_BONUS, healingBonus);
        stats.add(StatType.MAX_HP_TO_NORMAL_FLAT_DMG_RATIO,
                maxHpNormalRatio);
    }

    /** Opens the twelve-second Energy window on active-owner Burst use. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (!isBoundOwner(user, activeSimulator)
                || request == null
                || request.getKey() != CharacterActionKey.BURST
                || activeSimulator.getActiveCharacter() != owner) {
            return;
        }
        energyWindowUntil = activeSimulator.getCurrentTime()
                + ENERGY_WINDOW_DURATION;
        nextEnergyTime = Double.NEGATIVE_INFINITY;
    }

    /** Restores 0.6 Energy after accepted owner Normal hits on a 0.1s gate. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator activeSimulator) {
        if (!isBoundOwner(user, activeSimulator)
                || action == null
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0
                || action.getActionType() != ActionType.NORMAL
                || currentTime >= energyWindowUntil
                || currentTime < nextEnergyTime) {
            return;
        }
        owner.receiveFlatEnergy(ENERGY_RECOVERY);
        nextEnergyTime = currentTime + ENERGY_COOLDOWN;
    }

    /** Captures both Energy-window timestamps. */
    @Override
    public State captureWeaponState() {
        return new MoonglowState(this, energyWindowUntil, nextEnergyTime);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof MoonglowState)) {
            throw new IllegalArgumentException(
                    "Everlasting Moonglow state type is invalid");
        }
        MoonglowState restored = (MoonglowState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Everlasting Moonglow state belongs to another instance");
        }
        energyWindowUntil = restored.energyWindowUntil;
        nextEnergyTime = restored.nextEnergyTime;
    }

    private boolean isBoundOwner(
            Character user,
            CombatSimulator activeSimulator) {
        return simulator != null
                && user == owner
                && activeSimulator == simulator
                && owner.getWeapon() == this;
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Everlasting Moonglow refinement must be between 1 and 5");
        }
    }

    /** Immutable mutable-state payload tied to one Moonglow instance. */
    private static final class MoonglowState implements State {
        private final EverlastingMoonglow source;
        private final double energyWindowUntil;
        private final double nextEnergyTime;

        private MoonglowState(
                EverlastingMoonglow source,
                double energyWindowUntil,
                double nextEnergyTime) {
            this.source = source;
            this.energyWindowUntil = energyWindowUntil;
            this.nextEnergyTime = nextEnergyTime;
        }
    }
}
