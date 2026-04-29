package mechanics.rl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import model.entity.Character;
import model.type.CharacterId;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Derives capability scores for each character in a party by running
 * controlled simulation experiments.
 *
 * <p>Seven experiment templates are run per character:
 * <ul>
 *   <li>A — full on-field presence: measures raw on-field DPS</li>
 *   <li>B — passive presence: measures off-field damage after skill+burst</li>
 *   <li>C — team with subject buffing: measures buff contribution</li>
 *   <li>D — team without subject buffing: baseline for buff measurement</li>
 *   <li>E — self with burst: measures self-enhanced DPS</li>
 *   <li>F — self without burst: baseline for self-enhancement measurement</li>
 *   <li>G — energy counting: measures ER-scaled particles per skill use</li>
 * </ul>
 *
 * <p>Results are written to a JSON file loadable by {@link ObservationEncoder}.
 */
public class CapabilityProfiler {
    private static final int N_RUNS = 50;
    private static final double EPISODE_DURATION = 20.0;
    private static final double PARTICLE_WAIT = 5.0;

    private final Supplier<CombatSimulator> simulatorSupplier;
    private final EpisodeConfig config;

    /** Holds per-character profiling results for later JSON output. */
    private final Map<CharacterId, double[]> results = new LinkedHashMap<>();

    public CapabilityProfiler(Supplier<CombatSimulator> simulatorSupplier, EpisodeConfig config) {
        this.simulatorSupplier = simulatorSupplier;
        this.config = config;
    }

    /**
     * Runs all templates for every character in {@link EpisodeConfig#partyOrder}.
     * Results are stored internally and can be written via {@link #writeJson}.
     */
    public void runAll() {
        System.out.println("[CapabilityProfiler] Starting profiling run (N=" + N_RUNS + " per template)");
        for (CharacterId subjectId : config.partyOrder) {
            System.out.println("[CapabilityProfiler] Profiling: " + subjectId.name());
            double[] scores = profileCharacter(subjectId);
            results.put(subjectId, scores);
        }
        System.out.println("[CapabilityProfiler] Done.");
    }

    /**
     * Writes the profiling results to a JSON file.
     *
     * @param path destination file path (parent directories must exist)
     * @throws IOException if the file cannot be written
     */
    public void writeJson(String path) throws IOException {
        writeJson(path, results);
    }

    public Map<CharacterId, double[]> getResults() {
        Map<CharacterId, double[]> copy = new HashMap<>();
        for (Map.Entry<CharacterId, double[]> entry : results.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return copy;
    }

    public static void writeJson(String path, Map<CharacterId, double[]> sourceResults) throws IOException {
        Files.createDirectories(Paths.get(path).getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int outerCount = 0;
        for (Map.Entry<CharacterId, double[]> entry : sourceResults.entrySet()) {
            double[] s = entry.getValue();
            sb.append("  \"").append(entry.getKey().name()).append("\": {\n");
            sb.append("    \"off_field_dps_ratio\": ").append(format(s[0])).append(",\n");
            sb.append("    \"team_buff_score\": ").append(format(s[1])).append(",\n");
            sb.append("    \"self_enhancement_score\": ").append(format(s[2])).append(",\n");
            sb.append("    \"energy_generation_score\": ").append(format(s[3])).append("\n");
            sb.append("  }");
            outerCount++;
            if (outerCount < sourceResults.size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("}\n");
        Files.writeString(Paths.get(path), sb.toString());
        System.out.println("[CapabilityProfiler] Wrote profiles to: " + path);
    }

    // -------------------------------------------------------------------------
    // Per-character profiling
    // -------------------------------------------------------------------------

    private double[] profileCharacter(CharacterId subjectId) {
        CharacterId primaryId = getFixedPrimaryAttacker();
        CharacterId dummyId = getDummyAttacker(subjectId);

        double[] aVals = new double[N_RUNS];
        double[] bVals = new double[N_RUNS];
        double[] cVals = new double[N_RUNS];
        double[] dVals = new double[N_RUNS];
        double[] eVals = new double[N_RUNS];
        double[] fVals = new double[N_RUNS];
        double[] gVals = new double[N_RUNS];

        for (int i = 0; i < N_RUNS; i++) {
            aVals[i] = runQuietly(() -> runTemplateA(subjectId));
            bVals[i] = runQuietly(() -> runTemplateB(subjectId, dummyId));
            cVals[i] = runQuietly(() -> runTemplateC(subjectId, primaryId));
            dVals[i] = runQuietly(() -> runTemplateD(primaryId));
            eVals[i] = runQuietly(() -> runTemplateE(subjectId));
            fVals[i] = runQuietly(() -> runTemplateF(subjectId));
            gVals[i] = runQuietly(() -> runTemplateG(subjectId));
        }

        double A = mean(aVals);
        double B = mean(bVals);
        double C = mean(cVals);
        double D = mean(dVals);
        double E = mean(eVals);
        double F = mean(fVals);
        double G = mean(gVals);
        double maxEnergyCost = getMaxEnergyCost();

        double offField = clamp01(B / Math.max(A, 1.0));
        double teamBuff = clamp01((C - D) / Math.max(D, 1.0));
        double selfEnhance = clamp01((E - F) / Math.max(F, 1.0));
        double energyGen = clamp01(G / Math.max(maxEnergyCost, 1.0));

        System.out.printf(
                "  A=%,.0f(±%.0f) B=%,.0f(±%.0f) C=%,.0f(±%.0f) D=%,.0f(±%.0f) "
                + "E=%,.0f(±%.0f) F=%,.0f(±%.0f) G=%.2f(±%.3f)%n",
                A, stddev(aVals), B, stddev(bVals), C, stddev(cVals),
                D, stddev(dVals), E, stddev(eVals), F, stddev(fVals),
                G, stddev(gVals));
        System.out.printf(
                "  offField=%.4f  teamBuff=%.4f  selfEnhance=%.4f  energyGen=%.4f%n",
                offField, teamBuff, selfEnhance, energyGen);

        return new double[]{offField, teamBuff, selfEnhance, energyGen};
    }

    // -------------------------------------------------------------------------
    // Templates
    // -------------------------------------------------------------------------

    /** Template A: subject on field for full episode, uses skill/burst whenever available. */
    private double runTemplateA(CharacterId subjectId) {
        CombatSimulator sim = createFilledSim();
        sim.setActiveCharacter(subjectId);
        while (sim.getCurrentTime() < EPISODE_DURATION) {
            sim.performAction(subjectId, chooseBestAction(sim, subjectId, true, true));
        }
        return sim.getDamageByCharacter(subjectId);
    }

    /**
     * Template B: subject uses skill+burst at t=0, swaps off.
     * Dummy attacker fills the next 20 seconds.
     * Records only the subject's off-field damage after the swap.
     */
    private double runTemplateB(CharacterId subjectId, CharacterId dummyId) {
        CombatSimulator sim = createFilledSim();
        sim.setActiveCharacter(subjectId);
        useSkillIfReady(sim, subjectId);
        useBurstIfReady(sim, subjectId);
        if (!subjectId.equals(dummyId)) {
            sim.switchCharacter(dummyId);
            double damageBefore = sim.getDamageByCharacter(subjectId);
            double measureUntil = sim.getCurrentTime() + EPISODE_DURATION;
            fillWithNormals(sim, dummyId, measureUntil);
            return sim.getDamageByCharacter(subjectId) - damageBefore;
        }
        return 0.0;
    }

    /**
     * Template C: subject uses skill+burst at t=0, then primary attacks for a full 20-second window.
     * Records only the primary's damage during that post-setup window.
     */
    private double runTemplateC(CharacterId subjectId, CharacterId primaryId) {
        CombatSimulator sim = createFilledSim();
        if (subjectId.equals(primaryId)) {
            sim.setActiveCharacter(primaryId);
            double damageBefore = sim.getDamageByCharacter(primaryId);
            fillWithNormals(sim, primaryId, EPISODE_DURATION);
            return sim.getDamageByCharacter(primaryId) - damageBefore;
        }
        sim.setActiveCharacter(subjectId);
        useSkillIfReady(sim, subjectId);
        useBurstIfReady(sim, subjectId);
        sim.switchCharacter(primaryId);
        double damageBefore = sim.getDamageByCharacter(primaryId);
        double measureUntil = sim.getCurrentTime() + EPISODE_DURATION;
        fillWithNormals(sim, primaryId, measureUntil);
        return sim.getDamageByCharacter(primaryId) - damageBefore;
    }

    /**
     * Template D: same as C but subject does nothing.
     * Records only the primary's damage during its 20-second attack window.
     */
    private double runTemplateD(CharacterId primaryId) {
        CombatSimulator sim = createFilledSim();
        sim.setActiveCharacter(primaryId);
        double damageBefore = sim.getDamageByCharacter(primaryId);
        fillWithNormals(sim, primaryId, EPISODE_DURATION);
        return sim.getDamageByCharacter(primaryId) - damageBefore;
    }

    /** Template E: subject on field for full episode, uses burst at t=0, then ATTACKs only. */
    private double runTemplateE(CharacterId subjectId) {
        CombatSimulator sim = createFilledSim();
        sim.setActiveCharacter(subjectId);
        useBurstIfReady(sim, subjectId);
        while (sim.getCurrentTime() < EPISODE_DURATION) {
            sim.performAction(subjectId, CharacterActionRequest.of(CharacterActionKey.NORMAL));
        }
        return sim.getDamageByCharacter(subjectId);
    }

    /** Template F: subject on field for full episode, never uses burst, only skill+attack. */
    private double runTemplateF(CharacterId subjectId) {
        CombatSimulator sim = createFilledSim();
        sim.setActiveCharacter(subjectId);
        while (sim.getCurrentTime() < EPISODE_DURATION) {
            sim.performAction(subjectId, chooseBestAction(sim, subjectId, false, true));
        }
        return sim.getDamageByCharacter(subjectId);
    }

    /**
     * Template G: subject uses skill once (from fresh sim with 0 energy), then idles 5s.
     * Records ER-scaled particle energy received.
     */
    private double runTemplateG(CharacterId subjectId) {
        CombatSimulator sim = simulatorSupplier.get();
        sim.setLoggingEnabled(false);
        sim.setActiveCharacter(subjectId);
        Character subject = sim.getCharacter(subjectId);
        if (subject == null) {
            return 0.0;
        }
        double energyBefore = subject.getTotalScaledParticleEnergy();
        if (subject.canSkill(sim.getCurrentTime())) {
            sim.performAction(subjectId, CharacterActionRequest.of(CharacterActionKey.SKILL));
        }
        sim.advanceTime(PARTICLE_WAIT);
        return subject.getTotalScaledParticleEnergy() - energyBefore;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Creates a fresh simulator with all characters' energy filled to burst cost. */
    private CombatSimulator createFilledSim() {
        CombatSimulator sim = simulatorSupplier.get();
        sim.setLoggingEnabled(false);
        for (Character character : sim.getPartyMembers()) {
            character.receiveFlatEnergy(character.getEnergyCost());
        }
        return sim;
    }

    /** Returns the fixed primary attacker for templates C/D: partyOrder[0]. */
    private CharacterId getFixedPrimaryAttacker() {
        return config.partyOrder[0];
    }

    /** Returns the fixed dummy attacker for Template B, preferring partyOrder[0] when possible. */
    private CharacterId getDummyAttacker(CharacterId subjectId) {
        if (!subjectId.equals(config.partyOrder[0])) {
            return config.partyOrder[0];
        }
        for (CharacterId id : config.partyOrder) {
            if (!id.equals(subjectId)) {
                return id;
            }
        }
        return subjectId;
    }

    private double runQuietly(TemplateRun run) {
        return QuietExecution.call(run::execute);
    }

    /**
     * Chooses the best action for the subject: burst (if allowed and ready),
     * then skill (if allowed and ready), then normal attack.
     */
    private CharacterActionRequest chooseBestAction(
            CombatSimulator sim, CharacterId subjectId, boolean allowBurst, boolean allowSkill) {
        double now = sim.getCurrentTime();
        Character c = sim.getCharacter(subjectId);
        if (c == null) {
            return CharacterActionRequest.of(CharacterActionKey.NORMAL);
        }
        if (allowBurst && c.canBurst(now)) {
            return CharacterActionRequest.of(CharacterActionKey.BURST);
        }
        if (allowSkill && c.canSkill(now)) {
            return CharacterActionRequest.of(CharacterActionKey.SKILL);
        }
        return CharacterActionRequest.of(CharacterActionKey.NORMAL);
    }

    private void useSkillIfReady(CombatSimulator sim, CharacterId id) {
        Character c = sim.getCharacter(id);
        if (c != null && c.canSkill(sim.getCurrentTime())) {
            sim.performAction(id, CharacterActionRequest.of(CharacterActionKey.SKILL));
        }
    }

    private void useBurstIfReady(CombatSimulator sim, CharacterId id) {
        Character c = sim.getCharacter(id);
        if (c != null && c.canBurst(sim.getCurrentTime())) {
            sim.performAction(id, CharacterActionRequest.of(CharacterActionKey.BURST));
        }
    }

    /** Has the given character perform normal attacks until the episode limit is reached. */
    private void fillWithNormals(CombatSimulator sim, CharacterId id, double limit) {
        while (sim.getCurrentTime() < limit) {
            sim.performAction(id, CharacterActionRequest.of(CharacterActionKey.NORMAL));
        }
    }

    private double getMaxEnergyCost() {
        CombatSimulator sim = simulatorSupplier.get();
        double max = 1.0;
        for (CharacterId id : config.partyOrder) {
            Character c = sim.getCharacter(id);
            if (c != null) {
                max = Math.max(max, c.getEnergyCost());
            }
        }
        return max;
    }

    @FunctionalInterface
    private interface TemplateRun {
        double execute();
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private static double stddev(double[] values) {
        double m = mean(values);
        double sumSq = 0.0;
        for (double v : values) {
            sumSq += (v - m) * (v - m);
        }
        return Math.sqrt(sumSq / values.length);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static String format(double v) {
        return String.format("%.6f", v);
    }
}
