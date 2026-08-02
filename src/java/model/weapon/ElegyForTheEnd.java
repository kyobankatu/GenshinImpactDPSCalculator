package model.weapon;

import mechanics.buff.BuffId;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/** Elegy for the End bow with off-field sigils and a timed party song. */
public class ElegyForTheEnd extends MillennialMovementWeapon
        implements DamageTriggeredWeaponEffect {
    private final double passiveElementalMastery;
    private final double songElementalMastery;

    /** Constructs Elegy for the End at refinement rank five. */
    public ElegyForTheEnd() {
        this(5);
    }

    /**
     * Constructs Elegy for the End at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public ElegyForTheEnd(int refinement) {
        super(
                "Elegy for the End",
                WeaponType.BOW,
                608.0,
                StatType.ENERGY_RECHARGE,
                0.551,
                refinement,
                4,
                0.2,
                0.15 + 0.05 * refinement,
                "Millennial Movement: Farewell Song",
                BuffId.ELEGY_FAREWELL_SONG);
        this.passiveElementalMastery = 45.0 + 15.0 * refinement;
        this.songElementalMastery = 75.0 + 25.0 * refinement;
    }

    /** Applies the weapon's unconditional 60-120 Elemental Mastery. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ELEMENTAL_MASTERY, passiveElementalMastery);
    }

    /** Records eligible Skill/Burst hits after their damage has resolved. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (!isSkillOrBurstDamage(action)) {
            return;
        }
        tryGainSigil(user, sim, currentTime);
    }

    /** Applies Farewell Song's unique Elemental Mastery component. */
    @Override
    protected void applyUniqueMovementStats(StatsContainer stats) {
        stats.add(StatType.ELEMENTAL_MASTERY, songElementalMastery);
    }

    private static boolean isSkillOrBurstDamage(AttackAction action) {
        return action.getActionType() == ActionType.SKILL
                || action.getActionType() == ActionType.BURST
                || action.isCountsAsSkillDmg()
                || action.isCountsAsBurstDmg();
    }
}
