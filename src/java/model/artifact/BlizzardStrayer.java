package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Blizzard Strayer artifact set with target-state-dependent Critical Rate.
 *
 * <p>The fixed two-piece bonus grants 15% Cryo DMG Bonus. The four-piece
 * bonus reads the enemy's live state whenever stats are assembled: Frozen
 * grants 40% Critical Rate, while a positive ordinary Cryo Aura grants 20%.
 * Frozen takes precedence even when the reaction hides the ordinary Aura.</p>
 */
public class BlizzardStrayer extends ArtifactSet
        implements SimulatorInitializedArtifactEffect {
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Blizzard Strayer with a fresh fixed-stat container. */
    public BlizzardStrayer() {
        this(new StatsContainer());
    }

    /**
     * Constructs Blizzard Strayer while preserving supplied main/sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public BlizzardStrayer(StatsContainer stats) {
        super("Blizzard Strayer", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.CRYO_DMG_BONUS, 0.15);
    }

    /**
     * Binds the set to one owner and simulator for live enemy-state queries.
     *
     * <p>Repeating the identical binding is idempotent. Reusing one artifact
     * instance for another owner or simulator is rejected.</p>
     *
     * @param equippedOwner character carrying this artifact set
     * @param sim simulator containing the equipped owner
     * @param startsActive whether the owner starts as the active character
     */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim,
            boolean startsActive) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Artifact owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Blizzard Strayer is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Applies Critical Rate from the enemy's current Frozen or Cryo state.
     *
     * <p>No target state is snapshotted or scheduled. Because stat assembly
     * occurs before the hit changes Aura state, the activating hit does not
     * receive the bonus while a hit against existing Cryo or Frozen does.</p>
     *
     * @param totalStats aggregated stats container to mutate in place
     */
    @Override
    public void applyPassive(StatsContainer totalStats) {
        if (owner == null || simulator == null) {
            return;
        }
        Enemy enemy = simulator.getEnemy();
        if (enemy == null) {
            return;
        }

        double currentTime = simulator.getCurrentTime();
        if (enemy.isFrozen(currentTime)) {
            totalStats.add(StatType.CRIT_RATE, 0.40);
        } else if (enemy.getAuraUnits(Element.CRYO, currentTime) > 0.0) {
            totalStats.add(StatType.CRIT_RATE, 0.20);
        }
    }
}
