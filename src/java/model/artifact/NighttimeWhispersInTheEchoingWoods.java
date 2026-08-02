package model.artifact;

import java.util.Objects;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.ActionTriggeredArtifactEffect;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Nighttime Whispers in the Echoing Woods with its Skill-use Geo window.
 *
 * <p>The fixed two-piece bonus grants 18% ATK. An accepted owner Skill use
 * grants 20% Geo DMG Bonus over the half-open interval
 * {@code [castTime, castTime + 10)}.</p>
 *
 * <p>The Crystallize-shield and nearby-Moondrift 150% enhancement, including
 * its one-second grace period, is intentionally inactive because the simulator
 * does not model player shield ownership or player-to-Moondrift proximity.</p>
 */
public class NighttimeWhispersInTheEchoingWoods extends ArtifactSet
        implements SimulatorInitializedArtifactEffect,
        ActionTriggeredArtifactEffect {
    private static final double WINDOW_DURATION = 10.0;
    private static final double GEO_DAMAGE_BONUS = 0.20;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Nighttime Whispers with fresh fixed stats. */
    public NighttimeWhispersInTheEchoingWoods() {
        this(new StatsContainer());
    }

    /**
     * Constructs the set while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public NighttimeWhispersInTheEchoingWoods(StatsContainer stats) {
        super("Nighttime Whispers in the Echoing Woods",
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
                        "Nighttime Whispers in the Echoing Woods is already bound");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Applies or refreshes the ten-second Geo window on accepted owner Skill use.
     *
     * <p>The action dispatcher invokes this only after cooldown and other
     * action gates pass. Unbound, mismatched, null, and non-Skill callbacks are
     * inert.</p>
     *
     * @param user character whose action passed simulator gates
     * @param request accepted typed action request
     * @param sim simulator dispatching the action
     */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (owner == null
                || simulator == null
                || user != owner
                || sim != simulator
                || request == null
                || request.getKey() != CharacterActionKey.SKILL) {
            return;
        }

        owner.removeBuff(
                BuffId.NIGHTTIME_WHISPERS_IN_THE_ECHOING_WOODS_4PC);
        owner.addBuff(new SimpleBuff(
                "Nighttime Whispers in the Echoing Woods: Four-Piece Bonus",
                BuffId.NIGHTTIME_WHISPERS_IN_THE_ECHOING_WOODS_4PC,
                WINDOW_DURATION,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.GEO_DMG_BONUS, GEO_DAMAGE_BONUS))
                .sourcedBy(owner.getCharacterId()));
    }
}
