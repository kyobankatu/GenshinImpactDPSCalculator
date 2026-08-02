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
 * Gladiator's Finale artifact set with a weapon-type-gated Normal Attack bonus.
 *
 * <p>The fixed stats include the 2-piece 18% ATK bonus. Once initialized, the
 * 4-piece bonus grants 35% Normal Attack DMG Bonus only while the owner has a
 * Sword, Claymore, or Polearm equipped.</p>
 */
public class GladiatorsFinale extends ArtifactSet
        implements SimulatorInitializedArtifactEffect {
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Gladiator's Finale with a fresh fixed-stat container. */
    public GladiatorsFinale() {
        this(new StatsContainer());
    }

    /**
     * Constructs Gladiator's Finale while preserving supplied main/sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public GladiatorsFinale(StatsContainer stats) {
        super("Gladiator's Finale", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
    }

    /**
     * Binds the set to its owner and simulator for weapon-type evaluation.
     *
     * <p>Repeated initialization with the identical owner and simulator is a
     * no-op. An artifact instance cannot be reused by another owner or
     * simulator.</p>
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
                        "Gladiator's Finale is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Applies the 4-piece Normal Attack bonus for eligible melee weapon types.
     *
     * @param totalStats aggregated stats container to mutate in place
     */
    @Override
    public void applyPassive(StatsContainer totalStats) {
        if (owner == null) {
            return;
        }
        Weapon weapon = owner.getWeapon();
        if (weapon == null) {
            return;
        }
        WeaponType weaponType = weapon.getWeaponType();
        if (weaponType == WeaponType.SWORD
                || weaponType == WeaponType.CLAYMORE
                || weaponType == WeaponType.POLEARM) {
            totalStats.add(StatType.NORMAL_ATTACK_DMG_BONUS, 0.35);
        }
    }
}
