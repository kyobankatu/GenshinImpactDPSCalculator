package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/** The First Great Magic bow with live same-element party ATK tiers. */
public class TheFirstGreatMagic extends Weapon
        implements SimulatorInitializedWeaponEffect {
    private static final int MAX_GIMMICK_STACKS = 3;

    private final int refinement;
    private final double chargedDamageBonus;
    private final double attackBonusPerStack;
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs The First Great Magic at refinement rank five. */
    public TheFirstGreatMagic() {
        this(5);
    }

    /**
     * Constructs The First Great Magic at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TheFirstGreatMagic(int refinement) {
        super("The First Great Magic", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.chargedDamageBonus = 0.12 + 0.04 * refinement;
        this.attackBonusPerStack = 0.12 + 0.04 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_DMG, 0.662);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner and party whose elemental composition is read live. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "The First Great Magic is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Applies Charged DMG and one-to-three same-element Gimmick stacks. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, chargedDamageBonus);
        if (owner == null || simulator == null) {
            return;
        }
        long sameElementMembers = simulator.getPartyMembers().stream()
                .filter(member -> member.getElement() == owner.getElement())
                .count();
        int stackCount = (int) Math.min(
                sameElementMembers, MAX_GIMMICK_STACKS);
        stats.add(StatType.ATK_PERCENT, attackBonusPerStack * stackCount);
    }
}
