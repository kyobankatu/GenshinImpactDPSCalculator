package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.event.SimpleTimerEvent;

/** Fruit of Fulfillment catalyst with off-field reaction stacks and decay. */
public class FruitOfFulfillment extends Weapon
        implements SimulatorInitializedWeaponEffect, CombatSimulator.ReactionListener {
    private static final int MAX_STACKS = 5;
    private static final double STACK_COOLDOWN = 0.3;
    private static final double DECAY_INTERVAL = 6.0;
    private static final double ATTACK_PENALTY_PER_STACK = 0.05;

    private final int refinement;
    private final double elementalMasteryPerStack;
    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private long decayGeneration;
    private double nextStackTime = Double.NEGATIVE_INFINITY;

    /** Constructs Fruit of Fulfillment at refinement rank five. */
    public FruitOfFulfillment() {
        this(5);
    }

    /**
     * Constructs Fruit of Fulfillment at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FruitOfFulfillment(int refinement) {
        super("Fruit of Fulfillment", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.elementalMasteryPerStack = 21.0 + 3.0 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.459);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the current Wax and Wane stack count. */
    public int getStackCount() {
        return stackCount;
    }

    /** Binds the owner and registers one attributed reaction listener. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Fruit of Fulfillment is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addReactionListener(this);
    }

    /** Gains a stack at 0.3-second CT and resets inactivity decay on every reaction. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (sim != simulator
                || source != owner
                || result.getKind() == ReactionResult.Kind.NONE) {
            return;
        }

        long scheduledGeneration = ++decayGeneration;
        sim.registerEvent(new SimpleTimerEvent(
                time + DECAY_INTERVAL, DECAY_INTERVAL) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                if (decayGeneration != scheduledGeneration || stackCount == 0) {
                    finish();
                    return;
                }
                stackCount--;
                if (stackCount == 0) {
                    finish();
                }
            }
        });

        if (time < nextStackTime) {
            return;
        }
        stackCount = Math.min(MAX_STACKS, stackCount + 1);
        nextStackTime = time + STACK_COOLDOWN;
    }

    /** Applies EM gain and the fixed five-percent ATK penalty per stack. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ELEMENTAL_MASTERY,
                elementalMasteryPerStack * stackCount);
        stats.add(StatType.ATK_PERCENT,
                -ATTACK_PENALTY_PER_STACK * stackCount);
    }
}
