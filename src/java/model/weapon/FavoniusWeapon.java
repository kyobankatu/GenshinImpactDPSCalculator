package model.weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

import mechanics.energy.ParticleType;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Shared Windfall implementation for the Favonius weapon family.
 *
 * <p>
 * The simulator samples the joint probability of a CRIT hit and the weapon's
 * refinement proc chance with one injectable draw. A successful Windfall emits
 * three neutral particles, matching the existing six-base-energy approximation.
 */
public abstract class FavoniusWeapon extends Weapon implements
        DamageTriggeredWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double BASE_PROC_CHANCE = 0.50;
    private static final double PROC_CHANCE_PER_REFINEMENT = 0.10;
    private static final double BASE_COOLDOWN = 13.50;
    private static final double COOLDOWN_REDUCTION_PER_REFINEMENT = 1.50;

    private final int refinement;
    private final double procChance;
    private final double procCooldown;
    private final DoubleSupplier procDraw;
    private final List<Double> procDrawTape = new ArrayList<>();
    private int procDrawCursor;
    private double nextProcTime = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one Lv. 90 Favonius family member.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param energyRecharge Lv. 90 Energy Recharge as a decimal
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source of values in the usual {@code [0, 1)} range
     * @throws IllegalArgumentException when refinement is outside 1-5
     * @throws NullPointerException when {@code procDraw} is null
     */
    protected FavoniusWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            double energyRecharge,
            int refinement,
            DoubleSupplier procDraw) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Favonius refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procChance = BASE_PROC_CHANCE + PROC_CHANCE_PER_REFINEMENT * refinement;
        this.procCooldown = BASE_COOLDOWN - COOLDOWN_REDUCTION_PER_REFINEMENT * refinement;
        this.procDraw = Objects.requireNonNull(procDraw, "procDraw");
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(StatType.ENERGY_RECHARGE, energyRecharge);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /**
     * Samples Windfall after one resolved owner damage event.
     *
     * @param user character who dealt the damage
     * @param action resolved attack; Windfall accepts every damage action type
     * @param currentTime simulation time in seconds
     * @param sim active combat simulator
     */
    @Override
    public final void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (currentTime < nextProcTime) {
            return;
        }

        double critRate = user.getEffectiveStats(currentTime).get(StatType.CRIT_RATE);
        critRate = Math.max(0.0, Math.min(critRate, 1.0));
        if (nextProcDraw() >= critRate * procChance) {
            return;
        }

        sim.getEnergyDistributor().distributeParticles(
                Element.PHYSICAL, 3.0, ParticleType.PARTICLE);
        nextProcTime = currentTime + procCooldown;
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format(
                    "   [Favonius] Windfall Triggered by %s (CR: %.1f%%)",
                    user.getName(), critRate * 100.0));
        }
    }

    /** Captures cooldown and deterministic draw replay state. */
    @Override
    public final State captureWeaponState() {
        return new FavoniusState(
                this,
                nextProcTime,
                procDrawCursor);
    }

    /** Restores state captured from this exact Favonius instance. */
    @Override
    public final void restoreWeaponState(State state) {
        if (!(state instanceof FavoniusState)) {
            throw new IllegalArgumentException("Favonius weapon state type is invalid");
        }
        FavoniusState restored = (FavoniusState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Favonius weapon state belongs to another instance");
        }
        nextProcTime = restored.nextProcTime;
        procDrawCursor = restored.procDrawCursor;
    }

    private double nextProcDraw() {
        if (procDrawCursor < procDrawTape.size()) {
            return procDrawTape.get(procDrawCursor++);
        }
        double draw = procDraw.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException("Favonius proc draw must be in [0, 1)");
        }
        procDrawTape.add(draw);
        procDrawCursor++;
        return draw;
    }

    /** Immutable cooldown and random-tape snapshot. */
    private static final class FavoniusState implements State {
        private final FavoniusWeapon source;
        private final double nextProcTime;
        private final int procDrawCursor;

        private FavoniusState(
                FavoniusWeapon source,
                double nextProcTime,
                int procDrawCursor) {
            this.source = source;
            this.nextProcTime = nextProcTime;
            this.procDrawCursor = procDrawCursor;
        }
    }
}
