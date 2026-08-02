package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Prototype Starglitter polearm with shared-duration Skill-use damage stacks.
 */
public class PrototypeStarglitter extends Weapon implements ActionTriggeredWeaponEffect {
    private static final int MAX_STACKS = 2;
    private static final double DURATION = 12.0;

    private final int refinement;
    private final double damageBonusPerStack;
    private int stackCount;
    private double expiration = Double.NEGATIVE_INFINITY;

    /** Constructs Prototype Starglitter at refinement rank five. */
    public PrototypeStarglitter() {
        this(5);
    }

    /**
     * Constructs Prototype Starglitter at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public PrototypeStarglitter(int refinement) {
        super("Prototype Starglitter", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.damageBonusPerStack = 0.06 + 0.02 * refinement;
        this.weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.459);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /** Gains and refreshes a stack whenever the owner uses an Elemental Skill. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (request.getKey() != CharacterActionKey.SKILL) {
            return;
        }
        double currentTime = sim.getCurrentTime();
        if (currentTime >= expiration) {
            stackCount = 0;
        }
        stackCount = Math.min(MAX_STACKS, stackCount + 1);
        expiration = currentTime + DURATION;
    }

    /** Applies the active Normal and Charged Attack damage stacks. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < expiration) {
            double bonus = damageBonusPerStack * stackCount;
            stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, bonus);
            stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, bonus);
        }
    }
}
