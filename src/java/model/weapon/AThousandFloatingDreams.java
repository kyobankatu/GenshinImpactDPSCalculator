package model.weapon;

import java.util.Collections;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.entity.WeaponTeamBuffProvider;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/** A Thousand Floating Dreams catalyst with live ally-element bonuses. */
public class AThousandFloatingDreams extends Weapon
        implements SimulatorInitializedWeaponEffect, WeaponTeamBuffProvider {
    private static final int MAX_COMPOSITION_STACKS = 3;

    private final int refinement;
    private final double sameElementMastery;
    private final double differentElementDamageBonus;
    private final double partyElementalMastery;
    private Character owner;
    private CombatSimulator simulator;
    private Buff teamBuff;

    /** Constructs A Thousand Floating Dreams at refinement rank five. */
    public AThousandFloatingDreams() {
        this(5);
    }

    /**
     * Constructs A Thousand Floating Dreams at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public AThousandFloatingDreams(int refinement) {
        super("A Thousand Floating Dreams", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.sameElementMastery = 24.0 + 8.0 * refinement;
        this.differentElementDamageBonus = 0.06 + 0.04 * refinement;
        this.partyElementalMastery = 38.0 + 2.0 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.ELEMENTAL_MASTERY, 265.0);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds live party composition and creates one reusable ally-only buff. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "A Thousand Floating Dreams is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        teamBuff = new SimpleBuff(
                "A Thousand Floating Dreams (Party)",
                BuffId.A_THOUSAND_FLOATING_DREAMS_TEAM_EM,
                Double.MAX_VALUE,
                0.0,
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, partyElementalMastery));
        teamBuff.exclude(owner.getCharacterId()).sourcedBy(owner.getCharacterId());
    }

    /** Applies same-element EM and different-element owner DMG stacks live. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (owner == null || simulator == null) {
            return;
        }
        int sameElementAllies = 0;
        int differentElementAllies = 0;
        for (Character member : simulator.getPartyMembers()) {
            if (member == owner) {
                continue;
            }
            if (member.getElement() == owner.getElement()) {
                sameElementAllies++;
            } else {
                differentElementAllies++;
            }
        }
        sameElementAllies = Math.min(sameElementAllies, MAX_COMPOSITION_STACKS);
        differentElementAllies = Math.min(differentElementAllies, MAX_COMPOSITION_STACKS);
        stats.add(StatType.ELEMENTAL_MASTERY, sameElementMastery * sameElementAllies);
        stats.add(owner.getElement().getBonusStatType(),
                differentElementDamageBonus * differentElementAllies);
    }

    /** Returns the permanent stackable EM share for party members other than the owner. */
    @Override
    public List<Buff> getTeamBuffs(Character equippedOwner) {
        if (equippedOwner != owner || teamBuff == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(teamBuff);
    }
}
