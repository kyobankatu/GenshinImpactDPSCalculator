package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedArtifactEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Wanderer's Troupe artifact set with a weapon-type-gated Charged Attack bonus.
 *
 * <p>The fixed two-piece bonus grants 80 Elemental Mastery. Once initialized,
 * the four-piece bonus grants 35% Charged Attack DMG Bonus only while the owner
 * has a Bow or Catalyst equipped.</p>
 */
public class WanderersTroupe extends ArtifactSet
        implements SimulatorInitializedArtifactEffect {
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Wanderer's Troupe with a fresh fixed-stat container. */
    public WanderersTroupe() {
        this(new StatsContainer());
    }

    /**
     * Constructs Wanderer's Troupe while preserving supplied main/sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public WanderersTroupe(StatsContainer stats) {
        super("Wanderer's Troupe", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ELEMENTAL_MASTERY, 80.0);
    }

    /**
     * Binds the set to one owner and simulator for weapon-type evaluation.
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
                        "Wanderer's Troupe is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Applies the four-piece Charged Attack bonus for Bows and Catalysts.
     *
     * @param totalStats aggregated stats container to mutate in place
     */
    @Override
    public void applyPassive(StatsContainer totalStats) {
        if (owner == null || simulator == null) {
            return;
        }
        Weapon weapon = owner.getWeapon();
        if (weapon == null) {
            return;
        }
        WeaponType weaponType = weapon.getWeaponType();
        if (weaponType == WeaponType.BOW || weaponType == WeaponType.CATALYST) {
            totalStats.add(StatType.CHARGED_ATTACK_DMG_BONUS, 0.35);
        }
    }
}
