package model.weapon;

import mechanics.buff.BuffId;
import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.ElementalReactionTriggeredWeaponEffect;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/** Freedom-Sworn sword with off-field reaction sigils and Song of Resistance. */
public class FreedomSworn extends MillennialMovementWeapon
        implements ElementalReactionTriggeredWeaponEffect {
    private final double unconditionalDamageBonus;
    private final double actionDamageBonus;

    /** Constructs Freedom-Sworn at refinement rank five. */
    public FreedomSworn() {
        this(5);
    }

    /**
     * Constructs Freedom-Sworn at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FreedomSworn(int refinement) {
        super(
                "Freedom-Sworn",
                WeaponType.SWORD,
                608.0,
                StatType.ELEMENTAL_MASTERY,
                198.0,
                refinement,
                2,
                0.5,
                0.15 + 0.05 * refinement,
                "Millennial Movement: Song of Resistance",
                BuffId.FREEDOM_SWORN_SONG_OF_RESISTANCE);
        this.unconditionalDamageBonus = 0.075 + 0.025 * refinement;
        this.actionDamageBonus = 0.12 + 0.04 * refinement;
    }

    /** Registers only for actual elemental reaction notifications. */
    @Override
    protected void onInitialized(CombatSimulator sim) {
        sim.addElementalReactionTriggeredWeaponEffect(this);
    }

    /** Applies Revolutionary Chorale's unconditional all-DMG bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.DMG_BONUS_ALL, unconditionalDamageBonus);
    }

    /** Gains one Sigil of Rebellion from an attributed non-NONE reaction. */
    @Override
    public void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (result == null || result.getKind() == ReactionResult.Kind.NONE) {
            return;
        }
        tryGainSigil(source, sim, time);
    }

    /** Applies Song of Resistance's three typed action damage bonuses. */
    @Override
    protected void applyUniqueMovementStats(StatsContainer stats) {
        stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, actionDamageBonus);
        stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, actionDamageBonus);
        stats.add(StatType.PLUNGING_ATTACK_DMG_BONUS, actionDamageBonus);
    }
}
