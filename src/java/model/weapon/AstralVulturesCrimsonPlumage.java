package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Astral Vulture's Crimson Plumage bow with Swirl ATK and party-diversity tiers.
 */
public class AstralVulturesCrimsonPlumage extends Weapon
        implements SimulatorInitializedWeaponEffect, CombatSimulator.ReactionListener {
    private static final double SWIRL_DURATION = 12.0;

    private final int refinement;
    private final double swirlAttackBonus;
    private final double oneAllyChargedBonus;
    private final double twoAllyChargedBonus;
    private final double oneAllyBurstBonus;
    private final double twoAllyBurstBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double swirlActiveUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Astral Vulture's Crimson Plumage at refinement rank five. */
    public AstralVulturesCrimsonPlumage() {
        this(5);
    }

    /**
     * Constructs Astral Vulture's Crimson Plumage at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public AstralVulturesCrimsonPlumage(int refinement) {
        super("Astral Vulture's Crimson Plumage", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.swirlAttackBonus = 0.18 + 0.06 * refinement;
        this.oneAllyChargedBonus = 0.15 + 0.05 * refinement;
        this.twoAllyChargedBonus = 0.36 + 0.12 * refinement;
        this.oneAllyBurstBonus = 0.075 + 0.025 * refinement;
        this.twoAllyBurstBonus = 0.18 + 0.06 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_DMG, 0.662);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner and registers one attributed Swirl listener. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Astral Vulture's Crimson Plumage is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addReactionListener(this);
    }

    /** Refreshes the ATK window after an active-owner Swirl reaction. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (sim == simulator
                && source == owner
                && sim.getActiveCharacter() == owner
                && result.getKind() == ReactionResult.Kind.SWIRL) {
            swirlActiveUntil = time + SWIRL_DURATION;
        }
    }

    /** Applies the live Swirl window and one/two different-element ally tiers. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < swirlActiveUntil) {
            stats.add(StatType.ATK_PERCENT, swirlAttackBonus);
        }
        if (owner == null || simulator == null) {
            return;
        }
        long differentElementAllies = simulator.getPartyMembers().stream()
                .filter(member -> member != owner)
                .filter(member -> member.getElement() != owner.getElement())
                .count();
        if (differentElementAllies >= 2) {
            stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, twoAllyChargedBonus);
            stats.add(StatType.BURST_DMG_BONUS, twoAllyBurstBonus);
        } else if (differentElementAllies == 1) {
            stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, oneAllyChargedBonus);
            stats.add(StatType.BURST_DMG_BONUS, oneAllyBurstBonus);
        }
    }
}
