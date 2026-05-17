package mechanics.rl;

import mechanics.rl.ObservationEncoder.CapabilityProfileStore;
import model.type.CharacterId;
import simulation.CombatSimulator;

/**
 * Builds party-level role priors from static capability profiles and compares them
 * against realized role usage from one episode rollout.
 */
public class RoleAlignmentCalculator {
    private static final String DEFAULT_PROFILES_PATH = "config/capability_profiles/profiles.json";

    private final CapabilityProfileStore profileStore;

    /** Constructs a calculator backed by the default JSON profile store. */
    public RoleAlignmentCalculator() {
        this(new CapabilityProfileStore(DEFAULT_PROFILES_PATH));
    }

    /**
     * Constructs a calculator backed by the given profile store.
     *
     * @param profileStore capability profile store (null falls back to an empty store)
     */
    public RoleAlignmentCalculator(CapabilityProfileStore profileStore) {
        this.profileStore = profileStore != null ? profileStore : CapabilityProfileStore.empty();
    }

    /**
     * Builds the expected (prior) role share vector for the given party.
     *
     * @param partyOrder ordered party slots
     * @return flattened expected role vector of length {@code EpisodeRoleSummary.VECTOR_SIZE}
     */
    public double[] buildExpectedRoleVector(CharacterId[] partyOrder) {
        double[] onFieldBase = new double[ObservationEncoder.NUM_CHARS];
        double[] damageBase = new double[ObservationEncoder.NUM_CHARS];
        double[] offFieldBase = new double[ObservationEncoder.NUM_CHARS];
        double[] entryBase = new double[ObservationEncoder.NUM_CHARS];
        double[] stayBase = new double[ObservationEncoder.NUM_CHARS];

        for (int slot = 0; slot < partyOrder.length; slot++) {
            double[] profile = profileStore.getProfile(partyOrder[slot]);
            onFieldBase[slot] = positive(
                    0.45 * profile[CapabilityProfile.ON_FIELD_DPS_SCORE]
                            + 0.20 * profile[CapabilityProfile.BURST_WINDOW_SCORE]
                            + 0.20 * profile[CapabilityProfile.SELF_ENHANCEMENT_SCORE]
                            + 0.15 * profile[CapabilityProfile.SUSTAIN_VALUE_6_ACTIONS]);
            damageBase[slot] = positive(
                    0.50 * profile[CapabilityProfile.ON_FIELD_DPS_SCORE]
                            + 0.25 * profile[CapabilityProfile.OFF_FIELD_DPS_RATIO]
                            + 0.15 * profile[CapabilityProfile.BURST_WINDOW_SCORE]
                            + 0.10 * profile[CapabilityProfile.TEAM_BUFF_SCORE]);
            offFieldBase[slot] = positive(
                    0.60 * profile[CapabilityProfile.OFF_FIELD_DPS_RATIO]
                            + 0.25 * profile[CapabilityProfile.TEAM_BUFF_SCORE]
                            + 0.15 * profile[CapabilityProfile.ENERGY_GENERATION_SCORE]);
            entryBase[slot] = positive(
                    0.45 * profile[CapabilityProfile.ENTRY_VALUE_SCORE]
                            + 0.20 * profile[CapabilityProfile.SUSTAIN_VALUE_3_ACTIONS]
                            + 0.15 * profile[CapabilityProfile.ENERGY_GENERATION_SCORE]
                            + 0.20 * (1.0 - profile[CapabilityProfile.EXIT_COST_SCORE]));
            stayBase[slot] = positive(
                    0.45 * profile[CapabilityProfile.ON_FIELD_DPS_SCORE]
                            + 0.25 * profile[CapabilityProfile.SELF_ENHANCEMENT_SCORE]
                            + 0.20 * profile[CapabilityProfile.SUSTAIN_VALUE_6_ACTIONS]
                            + 0.10 * profile[CapabilityProfile.BURST_WINDOW_SCORE]);
        }

        normalizeInPlace(onFieldBase);
        normalizeInPlace(damageBase);
        normalizeInPlace(offFieldBase);
        normalizeInPlace(entryBase);
        normalizeInPlace(stayBase);

        double[] expected = new double[EpisodeRoleSummary.VECTOR_SIZE];
        for (int slot = 0; slot < ObservationEncoder.NUM_CHARS; slot++) {
            int base = slot * EpisodeRoleSummary.FEATURES_PER_SLOT;
            expected[base + EpisodeRoleSummary.ON_FIELD_SHARE] = onFieldBase[slot];
            expected[base + EpisodeRoleSummary.DAMAGE_SHARE] = damageBase[slot];
            expected[base + EpisodeRoleSummary.OFF_FIELD_DAMAGE_SHARE] = offFieldBase[slot];
            expected[base + EpisodeRoleSummary.ENTRY_SHARE] = entryBase[slot];
            expected[base + EpisodeRoleSummary.STAY_SHARE] = stayBase[slot];
        }
        return expected;
    }

    /**
     * Produces an episode role summary comparing realized usage against the prior vector.
     *
     * @param partyOrder ordered party slots
     * @param simulator simulator at episode end (used for damage totals)
     * @param slotOnFieldTime cumulative on-field time per slot
     * @param offFieldDamageBySlot off-field damage attributed to each slot
     * @param swapInCounts number of swap-in events per slot
     * @param stintTotalSecondsBySlot total stint seconds per slot
     * @param stintCountsBySlot number of stints per slot
     * @param episodeDurationSeconds total episode duration
     * @return populated EpisodeRoleSummary
     */
    public EpisodeRoleSummary summarize(
            CharacterId[] partyOrder,
            CombatSimulator simulator,
            double[] slotOnFieldTime,
            double[] offFieldDamageBySlot,
            int[] swapInCounts,
            double[] stintTotalSecondsBySlot,
            int[] stintCountsBySlot,
            double episodeDurationSeconds) {
        double[] expected = buildExpectedRoleVector(partyOrder);
        double[] realized = buildRealizedRoleVector(
                partyOrder,
                simulator,
                slotOnFieldTime,
                offFieldDamageBySlot,
                swapInCounts,
                stintTotalSecondsBySlot,
                stintCountsBySlot,
                episodeDurationSeconds);

        double onFieldAlignment = featureAlignment(expected, realized, EpisodeRoleSummary.ON_FIELD_SHARE);
        double damageAlignment = featureAlignment(expected, realized, EpisodeRoleSummary.DAMAGE_SHARE);
        double offFieldAlignment = featureAlignment(expected, realized, EpisodeRoleSummary.OFF_FIELD_DAMAGE_SHARE);
        double entryAlignment = featureAlignment(expected, realized, EpisodeRoleSummary.ENTRY_SHARE);
        double stayAlignment = featureAlignment(expected, realized, EpisodeRoleSummary.STAY_SHARE);

        double carryAlignment = 0.5 * onFieldAlignment + 0.5 * damageAlignment;
        double roleAlignment = 0.30 * onFieldAlignment
                + 0.25 * damageAlignment
                + 0.20 * offFieldAlignment
                + 0.15 * entryAlignment
                + 0.10 * stayAlignment;

        return new EpisodeRoleSummary(
                clamp01(roleAlignment),
                clamp01(carryAlignment),
                clamp01(offFieldAlignment),
                clamp01(entryAlignment),
                clamp01(stayAlignment),
                expected,
                realized);
    }

    private double[] buildRealizedRoleVector(
            CharacterId[] partyOrder,
            CombatSimulator simulator,
            double[] slotOnFieldTime,
            double[] offFieldDamageBySlot,
            int[] swapInCounts,
            double[] stintTotalSecondsBySlot,
            int[] stintCountsBySlot,
            double episodeDurationSeconds) {
        double[] onFieldShares = slotOnFieldTime.clone();
        normalizeInPlace(onFieldShares);

        double totalDamage = Math.max(1.0, simulator.getTotalDamage());
        double[] damageShares = new double[ObservationEncoder.NUM_CHARS];
        double[] offFieldShares = new double[ObservationEncoder.NUM_CHARS];
        double[] entryShares = new double[ObservationEncoder.NUM_CHARS];
        double[] stayShares = new double[ObservationEncoder.NUM_CHARS];

        for (int slot = 0; slot < partyOrder.length; slot++) {
            damageShares[slot] = clamp01(simulator.getDamageByCharacter(partyOrder[slot]) / totalDamage);
            offFieldShares[slot] = clamp01(offFieldDamageBySlot[slot] / totalDamage);
            entryShares[slot] = Math.max(0.0, swapInCounts[slot]);
            double meanStay = stintCountsBySlot[slot] > 0
                    ? stintTotalSecondsBySlot[slot] / stintCountsBySlot[slot]
                    : 0.0;
            stayShares[slot] = clamp01(meanStay / Math.max(1.0, episodeDurationSeconds));
        }

        normalizeInPlace(entryShares);
        normalizeInPlace(stayShares);

        double[] realized = new double[EpisodeRoleSummary.VECTOR_SIZE];
        for (int slot = 0; slot < ObservationEncoder.NUM_CHARS; slot++) {
            int base = slot * EpisodeRoleSummary.FEATURES_PER_SLOT;
            realized[base + EpisodeRoleSummary.ON_FIELD_SHARE] = onFieldShares[slot];
            realized[base + EpisodeRoleSummary.DAMAGE_SHARE] = damageShares[slot];
            realized[base + EpisodeRoleSummary.OFF_FIELD_DAMAGE_SHARE] = offFieldShares[slot];
            realized[base + EpisodeRoleSummary.ENTRY_SHARE] = entryShares[slot];
            realized[base + EpisodeRoleSummary.STAY_SHARE] = stayShares[slot];
        }
        return realized;
    }

    private double featureAlignment(double[] expected, double[] realized, int featureIndex) {
        double l1 = 0.0;
        for (int slot = 0; slot < ObservationEncoder.NUM_CHARS; slot++) {
            int offset = slot * EpisodeRoleSummary.FEATURES_PER_SLOT + featureIndex;
            l1 += Math.abs(expected[offset] - realized[offset]);
        }
        return clamp01(1.0 - 0.5 * l1);
    }

    private static double positive(double value) {
        return Math.max(0.0, value);
    }

    private static void normalizeInPlace(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += Math.max(0.0, value);
        }
        if (sum <= 1e-8) {
            double uniform = 1.0 / values.length;
            java.util.Arrays.fill(values, uniform);
            return;
        }
        for (int index = 0; index < values.length; index++) {
            values[index] = Math.max(0.0, values[index]) / sum;
        }
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
