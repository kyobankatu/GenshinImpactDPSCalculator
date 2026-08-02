package model.artifact;

import java.util.Objects;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.DamageTriggeredArtifactEffect;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * A Day Carved From Rising Winds with its owner hit-triggered ATK window.
 *
 * <p>The fixed two-piece bonus grants 18% ATK. After an eligible Normal,
 * Charged, Skill, or Burst hit resolves, the bound owner gains 25% ATK over
 * the half-open interval {@code [hitTime, hitTime + 6)}. The hit that opens
 * the window is therefore not enhanced. Zero-damage hits remain hits, and the
 * owner may trigger the effect while off-field.</p>
 *
 * <p>The Witch's Homework upgrade is intentionally inactive because the
 * simulator has no external character-progression state from which to derive
 * its additional CRIT Rate.</p>
 */
public class ADayCarvedFromRisingWinds extends ArtifactSet
        implements SimulatorInitializedArtifactEffect,
        DamageTriggeredArtifactEffect {
    private static final double WINDOW_DURATION = 6.0;
    private static final double WINDOW_ATK_BONUS = 0.25;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs A Day Carved From Rising Winds with fresh fixed stats. */
    public ADayCarvedFromRisingWinds() {
        this(new StatsContainer());
    }

    /**
     * Constructs the set while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public ADayCarvedFromRisingWinds(StatsContainer stats) {
        super("A Day Carved From Rising Winds",
                Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
    }

    /**
     * Binds this stateful set to exactly one owner and simulator.
     *
     * <p>Repeating the identical binding is idempotent. Reusing one artifact
     * instance for another owner or simulator is rejected.</p>
     *
     * @param equippedOwner character carrying this set
     * @param sim simulator containing the owner
     * @param startsActive whether the owner starts active
     */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim,
            boolean startsActive) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Artifact owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "A Day Carved From Rising Winds is already bound");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Applies or refreshes the six-second ATK window after an eligible hit.
     *
     * <p>Unbound, mismatched, null, and unsupported callbacks are inert. The
     * damage value is not gated because a shielded or immune target can still
     * register a zero-damage hit.</p>
     *
     * @param sim simulator dispatching the resolved damage
     * @param action action that produced the hit
     * @param damage final post-mitigation damage, which may be zero
     * @param callbackOwner artifact wearer supplied by the dispatcher
     */
    @Override
    public void onDamage(
            CombatSimulator sim,
            AttackAction action,
            double damage,
            Character callbackOwner) {
        if (!matchesBinding(callbackOwner, sim)
                || action == null
                || !isEligibleHit(action)) {
            return;
        }

        owner.removeBuff(BuffId.A_DAY_CARVED_FROM_RISING_WINDS_4PC);
        owner.addBuff(new SimpleBuff(
                "A Day Carved From Rising Winds: Blessing of Pastoral Winds",
                BuffId.A_DAY_CARVED_FROM_RISING_WINDS_4PC,
                WINDOW_DURATION,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.ATK_PERCENT, WINDOW_ATK_BONUS))
                .sourcedBy(owner.getCharacterId()));
    }

    /** Returns whether a callback belongs to the initialized binding. */
    private boolean matchesBinding(
            Character callbackOwner,
            CombatSimulator callbackSimulator) {
        return owner != null
                && simulator != null
                && owner == callbackOwner
                && simulator == callbackSimulator;
    }

    /** Returns whether one resolved hit has an eligible attack category. */
    private boolean isEligibleHit(AttackAction action) {
        ActionType actionType = action.getActionType();
        return actionType == ActionType.NORMAL
                || actionType == ActionType.CHARGE
                || actionType == ActionType.SKILL
                || actionType == ActionType.BURST
                || action.isCountsAsSkillDmg()
                || action.isCountsAsBurstDmg();
    }
}
