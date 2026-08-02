package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.entity.Enemy;
import model.entity.TargetDependentArtifactEffect;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;

/** Shared live-Aura outgoing damage passive for artifact sets. */
abstract class TargetAuraDamageArtifactSet extends ArtifactSet
        implements TargetDependentArtifactEffect {
    private final Element eligibleAura;
    private final double damageBonus;

    /**
     * Constructs one immutable Aura-conditional artifact set.
     *
     * @param name artifact set display name
     * @param stats non-null artifact main and sub stats
     * @param eligibleAura target Aura that activates the passive
     * @param damageBonus additive all-DMG bonus while eligible
     */
    TargetAuraDamageArtifactSet(
            String name,
            StatsContainer stats,
            Element eligibleAura,
            double damageBonus) {
        super(name, Objects.requireNonNull(stats, "stats"));
        this.eligibleAura = Objects.requireNonNull(
                eligibleAura, "eligibleAura");
        this.damageBonus = damageBonus;
    }

    /** Applies the outgoing bonus from the target's current ordinary Aura. */
    @Override
    public final void applyTargetDependentStats(
            StatsContainer stats,
            Enemy target,
            double currentTime) {
        if (stats != null
                && target != null
                && target.getAuraUnits(eligibleAura, currentTime) > 0.0) {
            stats.add(StatType.DMG_BONUS_ALL, damageBonus);
        }
    }
}
