package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterRegion;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/** Shared party-composition passive for the Lithic weapon family. */
abstract class LithicWeapon extends Weapon
        implements SimulatorInitializedWeaponEffect {
    private static final int MAX_STACKS = 4;

    private final int refinement;
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs one refinement-aware Lithic family member. */
    LithicWeapon(
            String name,
            WeaponType type,
            double baseAtk,
            double atkPercent,
            int refinement) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    name + " refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        weaponType = type;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(StatType.ATK_PERCENT, atkPercent);
    }

    /** Returns the selected refinement rank. */
    public final int getRefinement() {
        return refinement;
    }

    /** Returns the capped number of Liyue party members. */
    public final int getLiyueMemberCount() {
        if (simulator == null) {
            return 0;
        }
        int count = 0;
        for (Character member : simulator.getPartyMembers()) {
            if (member.getCharacterId().getRegion() == CharacterRegion.LIYUE) {
                count++;
            }
        }
        return Math.min(count, MAX_STACKS);
    }

    /** Binds the composition lookup to one equipped owner and simulator. */
    @Override
    public final void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    getName() + " owner and simulator are required");
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    getName() + " owner must have this weapon equipped");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        getName() + " is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Applies one ATK and CRIT Rate stack per Liyue party member. */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        int stacks = getLiyueMemberCount();
        stats.add(StatType.ATK_PERCENT,
                stacks * (0.06 + 0.01 * refinement));
        stats.add(StatType.CRIT_RATE,
                stacks * (0.02 + 0.01 * refinement));
    }
}
