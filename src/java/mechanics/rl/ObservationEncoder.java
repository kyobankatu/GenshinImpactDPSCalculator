package mechanics.rl;

import model.entity.BurstStateProvider;
import model.entity.Character;
import model.entity.Enemy;
import model.type.CharacterId;
import model.type.Element;
import simulation.CombatSimulator;

/**
 * Encodes simulator state into a fixed-size observation vector.
 */
public class ObservationEncoder {
    public static final int FEATURES_PER_CHARACTER = 6;
    public static final int GLOBAL_FEATURES = 7;
    public static final int OBSERVATION_SIZE = FEATURES_PER_CHARACTER * EpisodeConfig.DEFAULT_PARTY.length + GLOBAL_FEATURES;

    public double[] encode(CombatSimulator sim, EpisodeConfig config, double lastSwapTime) {
        double[] observation = new double[OBSERVATION_SIZE];
        fillObservation(sim, config, lastSwapTime, observation);
        return observation;
    }

    public void fillObservation(CombatSimulator sim, EpisodeConfig config, double lastSwapTime, double[] observation) {
        double now = sim.getCurrentTime();
        int index = 0;

        for (CharacterId id : config.partyOrder) {
            Character character = sim.getCharacter(id);
            if (character == null) {
                index += FEATURES_PER_CHARACTER;
                continue;
            }
            double energyCost = Math.max(1.0, character.getEnergyCost());
            observation[index++] = clamp01(character.getCurrentEnergy() / energyCost);
            observation[index++] = sim.getActiveCharacter() != null
                    && sim.getActiveCharacter().getCharacterId() == id ? 1.0 : 0.0;
            observation[index++] = character.canSkill(now) ? 1.0 : 0.0;
            observation[index++] = character.canBurst(now) ? 1.0 : 0.0;
            observation[index++] = isBurstActive(character, now) ? 1.0 : 0.0;
            observation[index++] = clamp01(countActiveBuffs(character, now) / 6.0);
        }

        Enemy enemy = sim.getEnemy();
        observation[index++] = (now - lastSwapTime) >= config.swapCooldown ? 1.0 : 0.0;
        observation[index++] = clamp01((config.maxEpisodeTime - now) / config.maxEpisodeTime);
        observation[index++] = normalizedAura(enemy, Element.PYRO);
        observation[index++] = normalizedAura(enemy, Element.HYDRO);
        observation[index++] = normalizedAura(enemy, Element.ELECTRO);
        observation[index++] = normalizedAura(enemy, Element.ANEMO);
        observation[index] = sim.getThundercloudEndTime() > now ? 1.0 : 0.0;
    }

    private boolean isBurstActive(Character character, double currentTime) {
        return character instanceof BurstStateProvider
                && ((BurstStateProvider) character).isBurstActive(currentTime);
    }

    private double countActiveBuffs(Character character, double currentTime) {
        int count = 0;
        for (mechanics.buff.Buff buff : character.getActiveBuffs()) {
            if (!buff.isExpired(currentTime)) {
                count++;
            }
        }
        return count;
    }

    private double normalizedAura(Enemy enemy, Element element) {
        if (enemy == null) {
            return 0.0;
        }
        return clamp01(enemy.getAuraUnits(element) / 2.0);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
