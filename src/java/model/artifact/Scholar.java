package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedArtifactEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.ParticleListener;

/**
 * Scholar artifact set with a particle-triggered party Energy effect.
 *
 * <p>The fixed two-piece bonus grants 20% Energy Recharge. When any positive
 * particle or orb notification occurs, the four-piece effect grants three flat
 * Energy to every current Bow or Catalyst wielder in the party. The effect has
 * a half-open three-second cooldown and uses the simulator's shared particle
 * notification for both modeled particles and orbs.</p>
 */
public class Scholar extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, ParticleListener {
    private static final double EFFECT_COOLDOWN = 3.0;
    private static final double PARTY_FLAT_ENERGY = 3.0;

    private Character owner;
    private CombatSimulator simulator;
    private double nextEligibleTime = Double.NEGATIVE_INFINITY;

    /** Constructs Scholar with a fresh fixed-stat container. */
    public Scholar() {
        this(new StatsContainer());
    }

    /**
     * Constructs Scholar while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public Scholar(StatsContainer stats) {
        super("Scholar", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ENERGY_RECHARGE, 0.20);
    }

    /**
     * Binds the set to one owner and registers exactly one particle listener.
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
                        "Scholar is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addParticleListener(this);
    }

    /**
     * Grants flat Energy to each current Bow or Catalyst party member.
     *
     * <p>Non-positive notifications do not consume the cooldown. An eligible
     * notification at exactly the previous trigger time plus three seconds is
     * accepted.</p>
     *
     * @param element generated particle element; all elements are eligible
     * @param count generated particle or orb count, which must be positive
     * @param time particle notification time in simulation seconds
     */
    @Override
    public void onParticle(Element element, double count, double time) {
        if (simulator == null || !(count > 0.0) || time < nextEligibleTime) {
            return;
        }
        nextEligibleTime = time + EFFECT_COOLDOWN;

        for (Character partyMember : simulator.getPartyMembers()) {
            Weapon weapon = partyMember.getWeapon();
            if (weapon == null) {
                continue;
            }
            WeaponType weaponType = weapon.getWeaponType();
            if (weaponType == WeaponType.BOW || weaponType == WeaponType.CATALYST) {
                partyMember.receiveFlatEnergy(PARTY_FLAT_ENERGY);
            }
        }
    }
}
