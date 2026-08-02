package model.weapon;

import java.util.EnumSet;
import java.util.function.DoubleSupplier;

import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/** Skyward Harp bow with CRIT DMG and an injected immediate Physical proc. */
public class SkywardHarp extends DirectDamageProcWeapon {
    private final double criticalDamageBonus;

    /** Constructs Skyward Harp at refinement rank five with stochastic draws. */
    public SkywardHarp() {
        this(5, Math::random);
    }

    /**
     * Constructs Skyward Harp at a selected refinement with stochastic draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SkywardHarp(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs R5 Skyward Harp with an explicit proc draw source.
     *
     * @param procDraw source of values in the usual {@code [0, 1)} range
     */
    public SkywardHarp(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Skyward Harp with selected refinement and proc draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source of values in the usual {@code [0, 1)} range
     */
    public SkywardHarp(int refinement, DoubleSupplier procDraw) {
        super(
                "Skyward Harp",
                WeaponType.BOW,
                674.0,
                StatType.CRIT_RATE,
                0.221,
                refinement,
                EnumSet.allOf(ActionType.class),
                0.50 + 0.10 * refinement,
                4.5 - 0.5 * refinement,
                1.25,
                procDraw);
        this.criticalDamageBonus = 0.15 + 0.05 * refinement;
    }

    /** Applies Skyward Harp's unconditional 20-40% CRIT DMG bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.CRIT_DMG, criticalDamageBonus);
    }
}
