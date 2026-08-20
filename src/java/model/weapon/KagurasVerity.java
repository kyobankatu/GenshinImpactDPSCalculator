package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Kagura's Verity catalyst with shared-duration Skill and Stellar-Conduct stacks.
 *
 * <p>R1-R5 values and the 24-second shared duration follow Genshin Optimizer
 * commit {@code d791814a}. Each Skill use adds one Kagura Dance stack to both
 * typed damage channels; three stacks additionally grant All Elemental DMG.</p>
 */
public class KagurasVerity extends Weapon
        implements ActionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int MAX_STACKS = 3;
    private static final double STACK_DURATION = 24.0;

    private final int refinement;
    private final double bonusPerStack;
    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Kagura's Verity at refinement rank five. */
    public KagurasVerity() {
        this(5);
    }

    /**
     * Constructs Kagura's Verity at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public KagurasVerity(int refinement) {
        super("Kagura's Verity", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.bonusPerStack = 0.09 + 0.03 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_DMG, 0.662);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the currently active Kagura Dance stack count. */
    public int getStackCount() {
        if (simulator != null && simulator.getCurrentTime() >= activeUntil) {
            return 0;
        }
        return stackCount;
    }

    /** Binds the owner whose typed Skill input drives the passive. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Kagura's Verity owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Kagura's Verity is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this
                || !sim.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Kagura's Verity owner must equip this weapon in the simulator party");
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Adds or refreshes Kagura Dance before active-owner Skill resolution. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (sim != simulator
                || user != owner
                || request == null
                || sim.getActiveCharacter() != owner
                || request.getKey() != CharacterActionKey.SKILL) {
            return;
        }
        double currentTime = sim.getCurrentTime();
        if (currentTime >= activeUntil) {
            stackCount = 0;
        }
        stackCount = Math.min(MAX_STACKS, stackCount + 1);
        activeUntil = currentTime + STACK_DURATION;
    }

    /** Applies Skill stacks and the three-stack owner-element bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime >= activeUntil || stackCount == 0) {
            return;
        }
        stats.add(StatType.SKILL_DMG_BONUS, bonusPerStack * stackCount);
        stats.add(StatType.STELLAR_CONDUCT_DMG_BONUS,
                bonusPerStack * stackCount);
        if (stackCount == MAX_STACKS) {
            for (Element element : Element.values()) {
                if (element != Element.PHYSICAL) {
                    stats.add(element.getBonusStatType(), bonusPerStack);
                }
            }
        }
    }

    /** Captures the stack count and shared expiry for simulator branching. */
    @Override
    public State captureWeaponState() {
        return new KaguraState(this, stackCount, activeUntil);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof KaguraState)) {
            throw new IllegalArgumentException("Kagura's Verity state type is invalid");
        }
        KaguraState restored = (KaguraState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Kagura's Verity state belongs to another instance");
        }
        stackCount = restored.stackCount;
        activeUntil = restored.activeUntil;
    }

    /** Immutable Kagura Dance state tied to one weapon instance. */
    private static final class KaguraState implements State {
        private final KagurasVerity source;
        private final int stackCount;
        private final double activeUntil;

        private KaguraState(
                KagurasVerity source,
                int stackCount,
                double activeUntil) {
            this.source = source;
            this.stackCount = stackCount;
            this.activeUntil = activeUntil;
        }
    }
}
