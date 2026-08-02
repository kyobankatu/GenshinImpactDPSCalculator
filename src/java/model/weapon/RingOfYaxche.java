package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Ring of Yaxche catalyst with a Max-HP-snapshotted Normal Attack window.
 *
 * <p>Using an Elemental Skill snapshots the owner's effective Max HP and
 * grants Normal Attack DMG Bonus for 10 seconds. The bonus uses complete
 * 1,000-HP increments and is limited by the weapon's refinement-specific
 * cap.</p>
 */
public class RingOfYaxche extends Weapon
        implements ActionTriggeredWeaponEffect, SimulatorInitializedWeaponEffect {
    private static final double DURATION = 10.0;

    private final int refinement;
    private final double bonusPerThousandHp;
    private final double maximumDamageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double normalAttackDamageBonus;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Ring of Yaxche at refinement rank five. */
    public RingOfYaxche() {
        this(5);
    }

    /**
     * Constructs Ring of Yaxche at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public RingOfYaxche(int refinement) {
        super("Ring of Yaxche", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.bonusPerThousandHp = 0.005 + 0.001 * refinement;
        this.maximumDamageBonus = 0.12 + 0.04 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.HP_PERCENT, 0.413);
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
     * Binds the owner and simulator used to validate Skill notifications.
     *
     * @param equippedOwner character carrying this weapon instance
     * @param sim simulator containing the equipped owner
     */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Ring of Yaxche is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Snapshots effective Max HP when the bound owner uses an Elemental Skill.
     *
     * <p>The snapshot uses only complete 1,000-HP increments. A later Skill
     * replaces both the stored bonus and the active window.</p>
     *
     * @param user character performing the action
     * @param request typed action request
     * @param sim active combat simulator
     */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (simulator == null
                || sim != simulator
                || user != owner
                || request == null
                || request.getKey() != CharacterActionKey.SKILL) {
            return;
        }
        double currentTime = sim.getCurrentTime();
        double maximumHp = owner.getEffectiveStats(currentTime).getTotalHp();
        normalAttackDamageBonus = Math.min(
                maximumDamageBonus,
                Math.floor(maximumHp / 1000.0) * bonusPerThousandHp);
        activeUntil = currentTime + DURATION;
    }

    /**
     * Applies the snapshotted Normal Attack DMG Bonus before exact expiry.
     *
     * @param stats stats container receiving the passive
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < activeUntil) {
            stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, normalAttackDamageBonus);
        }
    }
}
