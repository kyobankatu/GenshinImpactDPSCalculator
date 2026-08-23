package mechanics.rl;

import model.entity.ActionTriggeredArtifactEffect;
import model.entity.ActionTriggeredWeaponEffect;
import model.entity.ArtifactSet;
import model.entity.ArtifactTeamBuffProvider;
import model.entity.BurstTriggeredArtifactEffect;
import model.entity.Character;
import model.entity.DamageTriggeredArtifactEffect;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.ElementalReactionTriggeredWeaponEffect;
import model.entity.MovementAwareWeaponEffect;
import model.entity.ReactionAwareArtifact;
import model.entity.SimulatorInitializedArtifactEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.SwitchAwareArtifact;
import model.entity.SwitchAwareWeaponEffect;
import model.entity.TargetDependentArtifactEffect;
import model.entity.TargetDependentWeaponEffect;
import model.entity.Weapon;
import model.entity.WeaponTeamBuffProvider;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Encodes stable typed character and equipment features without display names. */
public final class LoadoutFeatureEncoder {
    /** Revision of the ordered feature block below. */
    public static final int SCHEMA_REVISION = 1;
    /** Number of loadout features appended to each character block. */
    public static final int SIZE = 41;

    /**
     * Fills one loadout block and returns the first index after it.
     *
     * @param character character whose fixed loadout is encoded
     * @param target destination observation/state array
     * @param offset first destination index
     * @return {@code offset + SIZE}
     */
    public int fill(Character character, double[] target, int offset) {
        if (character == null) {
            throw new IllegalArgumentException("Loadout character must not be null");
        }
        if (target == null || offset < 0 || target.length - offset < SIZE) {
            throw new IllegalArgumentException("Loadout feature destination is too small");
        }
        if (character.getConstellation() < 0 || character.getConstellation() > 6) {
            throw new IllegalStateException(
                    "Invalid constellation for " + character.getCharacterId());
        }
        if (character.getElement() == null) {
            throw new IllegalStateException(
                    "Missing typed element for " + character.getCharacterId());
        }

        int index = offset;
        target[index++] = bounded(character.getConstellation(), 6.0);
        target[index++] = bounded(character.getEnergyCost(), 100.0);
        target[index++] = bounded(character.getSkillCD(), 30.0);
        target[index++] = bounded(character.getBurstCD(), 30.0);

        Weapon weapon = character.getWeapon();
        if (weapon != null && (weapon.getRefinement() < 1 || weapon.getRefinement() > 5)) {
            throw new IllegalStateException(
                    "Invalid weapon refinement for " + character.getCharacterId());
        }
        target[index++] = weapon != null ? bounded(weapon.getRefinement(), 5.0) : 0.0;
        WeaponType weaponType = weapon != null ? weapon.getWeaponType() : null;
        for (WeaponType candidate : WeaponType.values()) {
            target[index++] = weaponType == candidate ? 1.0 : 0.0;
        }
        if (weapon != null && weaponType == null) {
            throw new IllegalStateException(
                    "Missing typed weapon category for " + character.getCharacterId());
        }

        StatsContainer weaponStats = weapon != null ? weapon.getStats() : new StatsContainer();
        index = fillCoreStats(weaponStats, target, index, false);
        target[index++] = isEventDriven(weapon) ? 1.0 : 0.0;
        target[index++] = weapon instanceof WeaponTeamBuffProvider ? 1.0 : 0.0;

        ArtifactSet[] artifacts = character.getArtifacts();
        int artifactCount = 0;
        boolean artifactEventDriven = false;
        boolean artifactTeamBuff = false;
        StatsContainer artifactStats = new StatsContainer();
        if (artifacts != null) {
            for (ArtifactSet artifact : artifacts) {
                if (artifact == null) {
                    continue;
                }
                artifactCount++;
                artifactStats = artifactStats.merge(artifact.getStats());
                artifactEventDriven |= isEventDriven(artifact);
                artifactTeamBuff |= artifact instanceof ArtifactTeamBuffProvider;
            }
        }
        target[index++] = bounded(artifactCount, 2.0);
        target[index++] = artifactEventDriven ? 1.0 : 0.0;
        target[index++] = artifactTeamBuff ? 1.0 : 0.0;
        index = fillArtifactStats(artifactStats, character, target, index);

        StatsContainer fixedStats = character.getBaseStats().merge(weaponStats).merge(artifactStats);
        target[index++] = bounded(fixedStats.getTotalHp(), 100000.0);
        target[index++] = bounded(fixedStats.getTotalAtk(), 10000.0);
        target[index++] = bounded(fixedStats.getTotalDef(), 10000.0);
        target[index++] = bounded(fixedStats.get(StatType.CRIT_RATE), 2.0);
        target[index++] = bounded(fixedStats.get(StatType.CRIT_DMG), 5.0);
        target[index++] = bounded(fixedStats.get(StatType.ELEMENTAL_MASTERY), 2000.0);
        target[index++] = bounded(fixedStats.getTotalEnergyRecharge(), 5.0);
        target[index++] = bounded(fixedStats.get(character.getElement().getBonusStatType()), 2.0);

        if (index != offset + SIZE) {
            throw new IllegalStateException("Loadout feature layout size mismatch");
        }
        validateFinite(target, offset, character);
        return index;
    }

    private int fillCoreStats(StatsContainer stats, double[] target, int index, boolean artifact) {
        target[index++] = bounded(stats.get(artifact ? StatType.HP_FLAT : StatType.BASE_ATK),
                artifact ? 20000.0 : 1000.0);
        target[index++] = bounded(stats.get(StatType.HP_PERCENT), 2.0);
        target[index++] = bounded(stats.get(StatType.ATK_PERCENT), 2.0);
        target[index++] = bounded(stats.get(StatType.DEF_PERCENT), 2.0);
        target[index++] = bounded(stats.get(StatType.CRIT_RATE), 2.0);
        target[index++] = bounded(stats.get(StatType.CRIT_DMG), 5.0);
        target[index++] = bounded(stats.get(StatType.ELEMENTAL_MASTERY), 2000.0);
        target[index++] = bounded(stats.getTotalEnergyRecharge(), 5.0);
        return index;
    }

    private int fillArtifactStats(
            StatsContainer stats,
            Character character,
            double[] target,
            int index) {
        index = fillCoreStats(stats, target, index, true);
        target[index++] = bounded(stats.get(StatType.ATK_FLAT), 2000.0);
        target[index++] = bounded(stats.get(character.getElement().getBonusStatType()), 2.0);
        return index;
    }

    private boolean isEventDriven(Weapon weapon) {
        return weapon instanceof ActionTriggeredWeaponEffect
                || weapon instanceof DamageTriggeredWeaponEffect
                || weapon instanceof ElementalReactionTriggeredWeaponEffect
                || weapon instanceof MovementAwareWeaponEffect
                || weapon instanceof SimulatorInitializedWeaponEffect
                || weapon instanceof SnapshotAwareWeaponEffect
                || weapon instanceof SwitchAwareWeaponEffect
                || weapon instanceof TargetDependentWeaponEffect;
    }

    private boolean isEventDriven(ArtifactSet artifact) {
        return artifact instanceof ActionTriggeredArtifactEffect
                || artifact instanceof BurstTriggeredArtifactEffect
                || artifact instanceof DamageTriggeredArtifactEffect
                || artifact instanceof ReactionAwareArtifact
                || artifact instanceof SimulatorInitializedArtifactEffect
                || artifact instanceof SwitchAwareArtifact
                || artifact instanceof TargetDependentArtifactEffect;
    }

    private double bounded(double value, double scale) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Non-finite loadout source value");
        }
        return Math.max(-2.0, Math.min(2.0, value / scale));
    }

    private void validateFinite(double[] target, int offset, Character character) {
        for (int index = offset; index < offset + SIZE; index++) {
            if (!Double.isFinite(target[index])) {
                throw new IllegalStateException(
                        "Non-finite loadout feature for " + character.getCharacterId()
                                + " at index " + (index - offset));
            }
        }
    }
}
