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

/** Song of Broken Pines claymore with hit sigils and Banner-Hymn. */
public class SongOfBrokenPines extends MillennialMovementWeapon
        implements DamageTriggeredWeaponEffect {
    private final double unconditionalAttackBonus;
    private final double normalAttackSpeedBonus;

    /** Constructs Song of Broken Pines at refinement rank five. */
    public SongOfBrokenPines() {
        this(5);
    }

    /**
     * Constructs Song of Broken Pines at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SongOfBrokenPines(int refinement) {
        super(
                "Song of Broken Pines",
                WeaponType.CLAYMORE,
                741.0,
                StatType.PHYSICAL_DMG_BONUS,
                0.207,
                refinement,
                4,
                0.3,
                0.15 + 0.05 * refinement,
                "Millennial Movement: Banner-Hymn",
                BuffId.SONG_OF_BROKEN_PINES_BANNER_HYMN);
        this.unconditionalAttackBonus = 0.12 + 0.04 * refinement;
        this.normalAttackSpeedBonus = 0.09 + 0.03 * refinement;
    }

    /** Applies Rebel's Banner-Hymn's unconditional ATK bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ATK_PERCENT, unconditionalAttackBonus);
    }

    /** Gains a Sigil of Whispers after a positive Normal or Charged hit. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        ActionType actionType = action.getActionType();
        if (action.getDamagePercent() <= 0.0
                || (actionType != ActionType.NORMAL
                        && actionType != ActionType.CHARGE)) {
            return;
        }
        tryGainSigil(user, sim, currentTime);
    }

    /** Applies Banner-Hymn's Normal-only attack speed component. */
    @Override
    protected void applyUniqueMovementStats(StatsContainer stats) {
        stats.add(StatType.NORMAL_ATTACK_SPD, normalAttackSpeedBonus);
    }
}
