package mechanics.rl;

import mechanics.rl.ObservationEncoder.CapabilityProfileStore;
import model.entity.BurstStateProvider;
import model.entity.Character;
import model.entity.Enemy;
import model.type.CharacterId;
import model.type.Element;
import simulation.CombatSimulator;

/**
 * Encodes training-time privileged combat state for the critic and auxiliary
 * prediction targets.
 */
public class PrivilegedStateEncoder {
    public static final int FEATURES_PER_CHARACTER = 5;
    public static final int GLOBAL_FEATURES = 3;
    public static final int NUM_CHARS = 4;
    public static final int STATE_SIZE = FEATURES_PER_CHARACTER * NUM_CHARS + GLOBAL_FEATURES;

    private static final String DEFAULT_PROFILES_PATH = "config/capability_profiles/profiles.json";

    private final CapabilityProfileStore profileStore;

    public PrivilegedStateEncoder() {
        this(new CapabilityProfileStore(DEFAULT_PROFILES_PATH));
    }

    public PrivilegedStateEncoder(CapabilityProfileStore profileStore) {
        this.profileStore = profileStore != null ? profileStore : CapabilityProfileStore.empty();
    }

    public double[] encode(CombatSimulator sim, EpisodeConfig config) {
        double[] state = new double[STATE_SIZE];
        fillState(sim, config, state);
        return state;
    }

    public void fillState(CombatSimulator sim, EpisodeConfig config, double[] state) {
        double now = sim.getCurrentTime();
        Character active = sim.getActiveCharacter();
        int index = 0;

        double[] commitments = new double[config.partyOrder.length];
        double[] payloads = new double[config.partyOrder.length];
        double[] swapInValues = new double[config.partyOrder.length];

        for (int slot = 0; slot < config.partyOrder.length; slot++) {
            CharacterId id = config.partyOrder[slot];
            Character character = sim.getCharacter(id);
            if (character == null) {
                index += FEATURES_PER_CHARACTER;
                continue;
            }

            double[] profile = profileStore.getProfile(id);
            double energyRatio = clamp01(character.getCurrentEnergy() / Math.max(1.0, character.getEnergyCost()));
            double skillReady = character.canSkill(now) ? 1.0 : 0.0;
            double burstReady = character.canBurst(now) ? 1.0 : 0.0;
            double burstActive = isBurstActive(character, now) ? 1.0 : 0.0;
            double buffActivity = clamp01(countActiveBuffs(character, now) / 6.0);
            double recentActionMomentum = Math.max(
                    decay(now - character.getLastSkillTime(), 8.0),
                    decay(now - character.getLastBurstTime(), 10.0));
            double skillCooldownFraction = cooldownFraction(character.getSkillCDRemaining(now), character.getSkillCD());
            double burstCooldownFraction = cooldownFraction(character.getBurstCDRemaining(now), character.getBurstCD());

            double commitment = clamp01(
                    0.25 * profile[CapabilityProfile.SELF_ENHANCEMENT_SCORE]
                            + 0.25 * profile[CapabilityProfile.SUSTAIN_VALUE_6_ACTIONS]
                            + 0.20 * profile[CapabilityProfile.REENTRY_COST_SCORE]
                            + 0.15 * recentActionMomentum
                            + 0.15 * Math.max(buffActivity, burstActive));
            if (active != null && active.getCharacterId() != id) {
                commitment *= 0.75;
            }

            double offFieldPayload = clamp01(
                    profile[CapabilityProfile.OFF_FIELD_DPS_RATIO]
                            * Math.max(burstActive, Math.max(skillCooldownFraction, burstCooldownFraction)));

            double followUpOpportunity = clamp01(
                    0.40 * burstReady
                            + 0.30 * skillReady
                            + 0.15 * profile[CapabilityProfile.ENTRY_VALUE_SCORE]
                            + 0.15 * recentActionMomentum);

            double swapInValue = clamp01(
                    profile[CapabilityProfile.ENTRY_VALUE_SCORE]
                            * (0.45 * skillReady + 0.45 * burstReady + 0.10 * energyRatio)
                            + 0.15 * profile[CapabilityProfile.SUSTAIN_VALUE_3_ACTIONS]);

            double setupAmortization = clamp01(
                    0.35 * recentActionMomentum
                            + 0.25 * profile[CapabilityProfile.EXIT_COST_SCORE]
                            + 0.20 * profile[CapabilityProfile.REENTRY_COST_SCORE]
                            + 0.20 * offFieldPayload);

            state[index++] = commitment;
            state[index++] = offFieldPayload;
            state[index++] = followUpOpportunity;
            state[index++] = swapInValue;
            state[index++] = setupAmortization;

            commitments[slot] = commitment;
            payloads[slot] = offFieldPayload;
            swapInValues[slot] = swapInValue;
        }

        double teamSetupValue = 0.0;
        double maxSwapInValue = 0.0;
        for (int slot = 0; slot < config.partyOrder.length; slot++) {
            teamSetupValue += payloads[slot] + 0.25 * commitments[slot];
            maxSwapInValue = Math.max(maxSwapInValue, swapInValues[slot]);
        }
        teamSetupValue = clamp01(teamSetupValue / Math.max(1.0, config.partyOrder.length));

        double reactionPotential = reactionPotential(sim.getEnemy());
        double activeSwapOpportunity = 0.0;
        if (active != null) {
            int activeSlot = findSlot(config.partyOrder, active.getCharacterId());
            double activeCommitment = activeSlot >= 0 ? commitments[activeSlot] : 0.0;
            activeSwapOpportunity = clamp01(maxSwapInValue - 0.5 * activeCommitment + 0.3 * reactionPotential);
        }

        state[index++] = teamSetupValue;
        state[index++] = reactionPotential;
        state[index] = activeSwapOpportunity;
    }

    private int findSlot(CharacterId[] partyOrder, CharacterId target) {
        for (int index = 0; index < partyOrder.length; index++) {
            if (partyOrder[index] == target) {
                return index;
            }
        }
        return -1;
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

    private double cooldownFraction(double remaining, double baseCooldown) {
        if (baseCooldown <= 1e-6) {
            return 0.0;
        }
        return clamp01(remaining / baseCooldown);
    }

    private double reactionPotential(Enemy enemy) {
        if (enemy == null) {
            return 0.0;
        }
        double auraSum = 0.0;
        auraSum += enemy.getAuraUnits(Element.PYRO);
        auraSum += enemy.getAuraUnits(Element.HYDRO);
        auraSum += enemy.getAuraUnits(Element.ELECTRO);
        auraSum += enemy.getAuraUnits(Element.ANEMO);
        return clamp01(auraSum / 4.0);
    }

    private double decay(double elapsed, double horizon) {
        if (elapsed < 0.0) {
            return 0.0;
        }
        return clamp01(1.0 - (elapsed / Math.max(1.0, horizon)));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
