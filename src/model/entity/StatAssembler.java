package model.entity;

import mechanics.buff.Buff;
import model.stats.StatsContainer;

/**
 * Assembles character stat views from base stats, equipment, passives, and buffs.
 */
final class StatAssembler {
    public StatsContainer assembleEffectiveStats(Character character, double currentTime) {
        StatsContainer total = new StatsContainer();
        total = total.merge(character.baseStats);

        if (character.weapon != null) {
            total = total.merge(character.weapon.getStats());
            character.weapon.applyPassive(total, currentTime);
        }

        if (character.artifacts != null) {
            for (ArtifactSet artifact : character.artifacts) {
                if (artifact != null) {
                    total = total.merge(artifact.getStats());
                }
            }
        }

        for (Buff buff : character.activeBuffs) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(total, currentTime);
            }
        }

        if (character.artifacts != null) {
            for (ArtifactSet artifact : character.artifacts) {
                if (artifact != null) {
                    artifact.applyPassive(total);
                }
            }
        }

        character.applyPassive(total);
        return total;
    }

    public StatsContainer assembleStructuralStats(Character character, double currentTime) {
        StatsContainer total = new StatsContainer();
        total = total.merge(character.baseStats);

        if (character.weapon != null) {
            total = total.merge(character.weapon.getStats());
            character.weapon.applyPassive(total, currentTime);
        }

        if (character.artifacts != null) {
            for (ArtifactSet artifact : character.artifacts) {
                if (artifact != null) {
                    total = total.merge(artifact.getStats());
                    artifact.applyPassive(total);
                }
            }
        }

        character.applyPassive(total);
        return total;
    }
}
