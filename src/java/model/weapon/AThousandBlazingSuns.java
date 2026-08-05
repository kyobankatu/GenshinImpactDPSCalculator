package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * A Thousand Blazing Suns' non-Nightsoul Scorching Brilliance contract.
 *
 * <p>Active-owner Skill/Burst use grants the R1-R5 ATK and CRIT DMG window.
 * Elemental Normal/Charged hits can extend it three times. Values and gates
 * follow pinned gcsim {@code ef41805d}. Nightsoul amplification and off-field
 * duration suspension are intentionally outside this implementation.</p>
 */
public final class AThousandBlazingSuns extends Weapon implements
        ActionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double WINDOW_DURATION = 6.0;
    private static final double ACTIVATION_COOLDOWN = 10.0;
    private static final double EXTENSION_DURATION = 2.0;
    private static final double EXTENSION_COOLDOWN = 1.0;
    private static final int MAX_EXTENSIONS = 3;

    private final int refinement;
    private final double attackBonus;
    private final double critDamageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double activeFrom = Double.POSITIVE_INFINITY;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextActivationAt = Double.NEGATIVE_INFINITY;
    private double nextExtensionAt = Double.NEGATIVE_INFINITY;
    private int extensionCount;

    /** Constructs the weapon at refinement rank five. */
    public AThousandBlazingSuns() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public AThousandBlazingSuns(int refinement) {
        super("A Thousand Blazing Suns", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "A Thousand Blazing Suns refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        attackBonus = 0.21 + 0.07 * refinement;
        critDamageBonus = 0.15 + 0.05 * refinement;
        weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 741.0);
        getStats().set(StatType.CRIT_RATE, 0.110);
    }

    /** Returns refinement rank in the inclusive range 1-5. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the Scorching Brilliance ATK bonus. */
    public double getAttackBonus() {
        return attackBonus;
    }

    /** Returns the Scorching Brilliance CRIT DMG bonus. */
    public double getCritDamageBonus() {
        return critDamageBonus;
    }

    /** Returns accepted extensions in the current activation. */
    public int getExtensionCount() {
        return extensionCount;
    }

    /** Returns whether the window is active at the supplied timestamp. */
    public boolean isWindowActive(double currentTime) {
        return currentTime >= activeFrom && currentTime < activeUntil;
    }

    /** Returns the current half-open expiration timestamp. */
    public double getActiveUntil() {
        return activeUntil;
    }

    /** Applies the live owner-only weapon stat window. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (!isWindowActive(currentTime)) {
            return;
        }
        stats.add(StatType.ATK_PERCENT, attackBonus);
        stats.add(StatType.CRIT_DMG, critDamageBonus);
    }

    /** Binds one equipped owner and direct-damage listener. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "A Thousand Blazing Suns owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "A Thousand Blazing Suns is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have A Thousand Blazing Suns equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addDamageListener((actor, action, damage, time) ->
                onResolvedDamage(actor, action, time));
    }

    /** Opens a new window on accepted active-owner Skill or Burst use. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (!isBoundActiveOwner(user, activeSimulator)
                || request == null
                || (request.getKey() != CharacterActionKey.SKILL
                        && request.getKey() != CharacterActionKey.BURST)) {
            return;
        }
        double currentTime = activeSimulator.getCurrentTime();
        if (currentTime < nextActivationAt) {
            return;
        }
        activeFrom = currentTime;
        activeUntil = currentTime + WINDOW_DURATION;
        nextActivationAt = currentTime + ACTIVATION_COOLDOWN;
        extensionCount = 0;
    }

    /** Captures all window, cooldown, and extension boundaries. */
    @Override
    public State captureWeaponState() {
        return new BlazingSunsState(
                this,
                activeFrom,
                activeUntil,
                nextActivationAt,
                nextExtensionAt,
                extensionCount);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof BlazingSunsState)) {
            throw new IllegalArgumentException(
                    "A Thousand Blazing Suns state type is invalid");
        }
        BlazingSunsState restored = (BlazingSunsState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "A Thousand Blazing Suns state belongs to another instance");
        }
        activeFrom = restored.activeFrom;
        activeUntil = restored.activeUntil;
        nextActivationAt = restored.nextActivationAt;
        nextExtensionAt = restored.nextExtensionAt;
        extensionCount = restored.extensionCount;
    }

    private void onResolvedDamage(
            Character actor,
            AttackAction action,
            double currentTime) {
        if (!isBoundActiveOwner(actor, simulator)
                || !isWindowActive(currentTime)
                || extensionCount >= MAX_EXTENSIONS
                || currentTime < nextExtensionAt
                || action == null
                || (action.getActionType() != ActionType.NORMAL
                        && action.getActionType() != ActionType.CHARGE)
                || action.getElement() == Element.PHYSICAL) {
            return;
        }
        extensionCount++;
        nextExtensionAt = currentTime + EXTENSION_COOLDOWN;
        activeUntil += EXTENSION_DURATION;
    }

    private boolean isBoundActiveOwner(
            Character actor,
            CombatSimulator activeSimulator) {
        return simulator != null
                && activeSimulator == simulator
                && actor == owner
                && owner.getWeapon() == this
                && simulator.getActiveCharacter() == owner;
    }

    private static final class BlazingSunsState implements State {
        private final AThousandBlazingSuns source;
        private final double activeFrom;
        private final double activeUntil;
        private final double nextActivationAt;
        private final double nextExtensionAt;
        private final int extensionCount;

        private BlazingSunsState(
                AThousandBlazingSuns source,
                double activeFrom,
                double activeUntil,
                double nextActivationAt,
                double nextExtensionAt,
                int extensionCount) {
            this.source = source;
            this.activeFrom = activeFrom;
            this.activeUntil = activeUntil;
            this.nextActivationAt = nextActivationAt;
            this.nextExtensionAt = nextExtensionAt;
            this.extensionCount = extensionCount;
        }
    }
}
