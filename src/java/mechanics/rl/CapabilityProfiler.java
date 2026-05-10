package mechanics.rl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import mechanics.buff.Buff;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Derives capability scores for each character in a party by running
 * controlled simulation experiments.
 *
 * <p>Seven core experiment templates are run per character:
 * <ul>
 *   <li>A — full on-field presence: measures raw on-field DPS</li>
 *   <li>B — passive presence: measures off-field damage after skill+burst</li>
 *   <li>C/D — team support under a simple normal-attack carry script</li>
 *   <li>E — self with burst: measures self-enhanced DPS</li>
 *   <li>F — self without burst: baseline for self-enhancement measurement</li>
 *   <li>G — energy counting: measures ER-scaled particles per skill use</li>
 * </ul>
 *
 * <p>The exported {@code team_buff_score} is intentionally kept as a single
 * support-ness scalar for RL consumption, but it is now derived from
 * permutation-wide support profiling instead of a single fixed-carry script.
 *
 * <p>Results are written to a JSON file loadable by {@link ObservationEncoder}.
 */
public class CapabilityProfiler {
    private static final int N_RUNS = 1;
    private static final double EPISODE_DURATION = 20.0;
    private static final double PARTICLE_WAIT = 5.0;
    private static final double MIN_DAMAGE_FOR_RATIO = 1.0;
    private static final List<CharacterActionKey> DAMAGE_PROBE_ACTIONS = Arrays.asList(
            CharacterActionKey.BURST,
            CharacterActionKey.SKILL,
            CharacterActionKey.NORMAL);

    private final Supplier<CombatSimulator> simulatorSupplier;
    private final EpisodeConfig config;
    private final List<CharacterId[]> partyPermutations;
    private final List<List<CharacterActionKey>> actionSequenceLibrary;

    /** Holds per-character profiling results for later JSON output. */
    private final Map<CharacterId, double[]> results = new LinkedHashMap<>();

    public CapabilityProfiler(Supplier<CombatSimulator> simulatorSupplier, EpisodeConfig config) {
        this.simulatorSupplier = simulatorSupplier;
        this.config = config;
        this.partyPermutations = buildPermutations(config.partyOrder);
        this.actionSequenceLibrary = buildActionSequenceLibrary();
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
        normalizeTeamBuffScores();
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
            sb.append("    \"energy_generation_score\": ").append(format(s[3])).append(",\n");
            sb.append("    \"entry_value_score\": ").append(format(s[4])).append(",\n");
            sb.append("    \"sustain_value_3_actions\": ").append(format(s[5])).append(",\n");
            sb.append("    \"sustain_value_6_actions\": ").append(format(s[6])).append(",\n");
            sb.append("    \"exit_cost_score\": ").append(format(s[7])).append(",\n");
            sb.append("    \"reentry_cost_score\": ").append(format(s[8])).append(",\n");
            sb.append("    \"on_field_dps_score\": ").append(format(s[9])).append(",\n");
            sb.append("    \"burst_window_score\": ").append(format(s[10])).append("\n");
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
        CharacterId dummyId = getDummyAttacker(subjectId);

        double[] aVals = new double[N_RUNS];
        double[] bVals = new double[N_RUNS];
        double[] eVals = new double[N_RUNS];
        double[] fVals = new double[N_RUNS];
        double[] gVals = new double[N_RUNS];
        double[] hVals = new double[N_RUNS];
        double[] iVals = new double[N_RUNS];
        double[] jVals = new double[N_RUNS];
        double[] kVals = new double[N_RUNS];
        double[] lVals = new double[N_RUNS];
        double[] teamBuffVals = new double[N_RUNS];
        double[] teamBuffTeamVals = new double[N_RUNS];
        double[] teamBuffBeneficiaryVals = new double[N_RUNS];
        TeamBuffProfileResult bestTeamBuffResult = TeamBuffProfileResult.zero();

        for (int i = 0; i < N_RUNS; i++) {
            aVals[i] = runQuietly(() -> runTemplateA(subjectId));
            bVals[i] = runQuietly(() -> runTemplateB(subjectId, dummyId));
            eVals[i] = runQuietly(() -> runTemplateE(subjectId));
            fVals[i] = runQuietly(() -> runTemplateF(subjectId));
            gVals[i] = runQuietly(() -> runTemplateG(subjectId));
            hVals[i] = runQuietly(() -> runEntryValueTemplate(subjectId));
            iVals[i] = runQuietly(() -> runSustainValueTemplate(subjectId, 3));
            jVals[i] = runQuietly(() -> runSustainValueTemplate(subjectId, 6));
            kVals[i] = runQuietly(() -> runExitCostTemplate(subjectId));
            lVals[i] = runQuietly(() -> runReentryCostTemplate(subjectId));
            TeamBuffProfileResult teamBuffResult = QuietExecution.call(() -> runPermutationTeamBuffProfile(subjectId));
            teamBuffVals[i] = teamBuffResult.compositeUplift;
            teamBuffTeamVals[i] = teamBuffResult.teamUplift;
            teamBuffBeneficiaryVals[i] = teamBuffResult.beneficiaryUplift;
            if (teamBuffResult.compositeUplift > bestTeamBuffResult.compositeUplift) {
                bestTeamBuffResult = teamBuffResult;
            }
        }

        double A = mean(aVals);
        double B = mean(bVals);
        double E = mean(eVals);
        double F = mean(fVals);
        double G = mean(gVals);
        double H = mean(hVals);
        double I = mean(iVals);
        double J = mean(jVals);
        double K = mean(kVals);
        double L = mean(lVals);
        double permutationTeamBuff = mean(teamBuffVals);
        double permutationTeamBuffTeam = mean(teamBuffTeamVals);
        double permutationTeamBuffBeneficiary = mean(teamBuffBeneficiaryVals);
        double maxEnergyCost = getMaxEnergyCost();

        double offField = clamp01(B / Math.max(A, 1.0));
        double teamBuff = Math.max(0.0, permutationTeamBuff);
        double selfEnhance = clamp01((E - F) / Math.max(F, 1.0));
        double energyGen = clamp01(G / Math.max(maxEnergyCost, 1.0));
        double entryValue = clamp01(H / Math.max(A, 1.0));
        double sustain3 = clamp01(I / Math.max(A, 1.0));
        double sustain6 = clamp01(J / Math.max(A, 1.0));
        double exitCost = clamp01(K);
        double reentryCost = clamp01(L);
        double onFieldDpsScore = clamp01(A / 500000.0);
        double burstWindowScore = clamp01(E / 500000.0);

        System.out.printf(
                "  A=%,.0f(±%.0f) B=%,.0f(±%.0f) E=%,.0f(±%.0f) F=%,.0f(±%.0f) G=%.2f(±%.3f)%n",
                A, stddev(aVals), B, stddev(bVals), E, stddev(eVals), F, stddev(fVals),
                G, stddev(gVals));
        System.out.printf(
                "  offField=%.4f  teamBuffRaw=%.4f  teamBuffTeam=%.4f  teamBuffBeneficiary=%.4f  selfEnhance=%.4f  energyGen=%.4f  entry=%.4f  sustain3=%.4f  sustain6=%.4f  exit=%.4f  reentry=%.4f  onField=%.4f  burstWindow=%.4f%n",
                offField, teamBuff, permutationTeamBuffTeam, permutationTeamBuffBeneficiary, selfEnhance, energyGen,
                entryValue, sustain3, sustain6, exitCost, reentryCost,
                onFieldDpsScore, burstWindowScore);
        System.out.printf(
                "  teamBuffPermutationMean=%.4f  teamBuffTeamMean=%.4f  teamBuffBeneficiaryMean=%.4f (permutations=%d)%n",
                permutationTeamBuff,
                permutationTeamBuffTeam,
                permutationTeamBuffBeneficiary,
                partyPermutations.size());
        printBestTeamBuffTrace(bestTeamBuffResult);

        return new double[]{
                offField, teamBuff, selfEnhance, energyGen, entryValue, sustain3, sustain6, exitCost, reentryCost,
                onFieldDpsScore, burstWindowScore};
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

    private TeamBuffProfileResult runPermutationTeamBuffProfile(CharacterId subjectId) {
        TeamBuffProfileResult best = TeamBuffProfileResult.zero();
        for (CharacterId[] permutation : partyPermutations) {
            TeamBuffProfileResult candidate = searchPermutationMaxUplift(permutation, subjectId, new ArrayList<>(), 0);
            if (candidate.compositeUplift > best.compositeUplift) {
                best = candidate;
            }
        }
        return best;
    }

    private TeamBuffProfileResult searchPermutationMaxUplift(
            CharacterId[] permutation,
            CharacterId subjectId,
            List<List<CharacterActionKey>> assignedSequences,
            int index) {
        if (index >= permutation.length) {
            return runPermutationScenarioMaxUplift(permutation, subjectId, assignedSequences);
        }
        TeamBuffProfileResult best = TeamBuffProfileResult.zero();
        for (List<CharacterActionKey> sequence : actionSequenceLibrary) {
            assignedSequences.add(sequence);
            TeamBuffProfileResult candidate = searchPermutationMaxUplift(permutation, subjectId, assignedSequences, index + 1);
            if (candidate.compositeUplift > best.compositeUplift) {
                best = candidate;
            }
            assignedSequences.remove(assignedSequences.size() - 1);
        }
        return best;
    }

    private TeamBuffProfileResult runPermutationScenarioMaxUplift(
            CharacterId[] order,
            CharacterId subjectId,
            List<List<CharacterActionKey>> assignedSequences) {
        CombatSimulator activeSim = createFilledSim();
        CombatSimulator idleSim = createFilledSim();
        CharacterId first = order[0];
        activeSim.setActiveCharacter(first);
        idleSim.setActiveCharacter(first);
        TeamBuffProfileResult best = TeamBuffProfileResult.zero();
        List<String> traceLines = new ArrayList<>();
        int subjectIndex = -1;
        for (int index = 0; index < order.length; index++) {
            CharacterId actorId = order[index];
            if (index > 0) {
                activeSim.switchCharacter(actorId);
                idleSim.switchCharacter(actorId);
            }
            if (actorId.equals(subjectId)) {
                subjectIndex = index;
                activeSim.restoreSnapshot(activeSim.saveSnapshot());
                idleSim.restoreSnapshot(idleSim.saveSnapshot());
                double subjectSyncTime = executeSubjectSequenceAndSync(
                        activeSim,
                        idleSim,
                        actorId,
                        assignedSequences.get(index));
                traceLines.add(String.format("subject-sync %s sequence=%s t=%.2f",
                        actorId.name(),
                        formatSequence(assignedSequences.get(index)),
                        activeSim.getCurrentTime()));
            } else if (subjectIndex < 0) {
                replaySequencePair(
                        activeSim,
                        idleSim,
                        actorId,
                        assignedSequences.get(index),
                        false);
            }
        }
        if (subjectIndex >= 0) {
            double subjectSyncTime = activeSim.getCurrentTime();
            best = evaluatePostSubjectActionProbes(activeSim, idleSim, subjectId, order, subjectIndex, subjectSyncTime, traceLines);
        }
        if (best.compositeUplift > 0.0) {
            best = best.withScenario(order, assignedSequences, traceLines);
        }
        return best;
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

    private double runEntryValueTemplate(CharacterId subjectId) {
        CombatSimulator sim = createFilledSim();
        sim.setActiveCharacter(subjectId);
        CharacterActionRequest action = chooseBestAction(sim, subjectId, true, true);
        double before = sim.getDamageByCharacter(subjectId);
        sim.performAction(subjectId, action);
        return sim.getDamageByCharacter(subjectId) - before;
    }

    private double runSustainValueTemplate(CharacterId subjectId, int actionCount) {
        CombatSimulator sim = createFilledSim();
        sim.setActiveCharacter(subjectId);
        double before = sim.getDamageByCharacter(subjectId);
        performBestActions(sim, subjectId, actionCount, true, true);
        double totalDamage = sim.getDamageByCharacter(subjectId) - before;
        return totalDamage / Math.max(1, actionCount);
    }

    private double runExitCostTemplate(CharacterId subjectId) {
        CharacterId dummyId = getDummyAttacker(subjectId);
        CombatSimulator staySim = createFilledSim();
        staySim.setActiveCharacter(subjectId);
        double stayBefore = staySim.getDamageByCharacter(subjectId);
        performBestActions(staySim, subjectId, 6, true, true);
        double stayDamage = staySim.getDamageByCharacter(subjectId) - stayBefore;

        CombatSimulator swapSim = createFilledSim();
        swapSim.setActiveCharacter(subjectId);
        double swapBefore = swapSim.getDamageByCharacter(subjectId);
        performBestActions(swapSim, subjectId, 3, true, true);
        if (!subjectId.equals(dummyId)) {
            swapSim.switchCharacter(dummyId);
            performBestActions(swapSim, dummyId, 3, true, true);
        }
        double earlyDamage = swapSim.getDamageByCharacter(subjectId) - swapBefore;
        return clamp01((stayDamage - earlyDamage) / Math.max(1.0, stayDamage));
    }

    private double runReentryCostTemplate(CharacterId subjectId) {
        CharacterId dummyId = getDummyAttacker(subjectId);
        CombatSimulator staySim = createFilledSim();
        staySim.setActiveCharacter(subjectId);
        double stayBefore = staySim.getDamageByCharacter(subjectId);
        performBestActions(staySim, subjectId, 6, true, true);
        double stayDamage = staySim.getDamageByCharacter(subjectId) - stayBefore;

        CombatSimulator reentrySim = createFilledSim();
        reentrySim.setActiveCharacter(subjectId);
        double reentryBefore = reentrySim.getDamageByCharacter(subjectId);
        performBestActions(reentrySim, subjectId, 3, true, true);
        if (!subjectId.equals(dummyId)) {
            reentrySim.switchCharacter(dummyId);
            performBestActions(reentrySim, dummyId, 2, true, true);
            reentrySim.switchCharacter(subjectId);
        }
        performBestActions(reentrySim, subjectId, 3, true, true);
        double reentryDamage = reentrySim.getDamageByCharacter(subjectId) - reentryBefore;
        return clamp01((stayDamage - reentryDamage) / Math.max(1.0, stayDamage));
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

    private void performBestActions(CombatSimulator sim, CharacterId id, int actionCount,
            boolean allowBurst, boolean allowSkill) {
        for (int index = 0; index < actionCount; index++) {
            sim.performAction(id, chooseBestAction(sim, id, allowBurst, allowSkill));
        }
    }

    private void performNormals(CombatSimulator sim, CharacterId id, int actionCount) {
        for (int index = 0; index < actionCount; index++) {
            sim.performAction(id, CharacterActionRequest.of(CharacterActionKey.NORMAL));
        }
    }

    private void performBestUntil(CombatSimulator sim, CharacterId id, double limit,
            boolean allowBurst, boolean allowSkill) {
        while (sim.getCurrentTime() < limit) {
            sim.performAction(id, chooseBestAction(sim, id, allowBurst, allowSkill));
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

    private TeamBuffProfileResult applyActionSequencePair(
            CombatSimulator activeSim,
            CombatSimulator idleSim,
            CharacterId actorId,
            List<CharacterActionKey> sequence,
            boolean idleActor,
            CharacterId excludedId,
            List<String> traceLines) {
        TeamBuffProfileResult best = TeamBuffProfileResult.zero();
        for (CharacterActionKey key : sequence) {
            CharacterActionRequest request = CharacterActionRequest.of(key);
            TeamBuffProfileResult candidate = executeActionPair(activeSim, idleSim, actorId, request, idleActor, excludedId);
            if (candidate.traceSummary != null) {
                traceLines.add(candidate.traceSummary);
            }
            best = TeamBuffProfileResult.max(best, candidate);
        }
        return best;
    }

    private void replaySequencePair(
            CombatSimulator activeSim,
            CombatSimulator idleSim,
            CharacterId actorId,
            List<CharacterActionKey> sequence,
            boolean idleActor) {
        for (CharacterActionKey key : sequence) {
            executeActionPair(
                    activeSim,
                    idleSim,
                    actorId,
                    CharacterActionRequest.of(key),
                    idleActor,
                    actorId);
        }
    }

    private double executeSubjectSequenceAndSync(
            CombatSimulator activeSim,
            CombatSimulator idleSim,
            CharacterId actorId,
            List<CharacterActionKey> sequence) {
        SimulatorSnapshot idlePreSubject = idleSim.saveSnapshot();
        for (CharacterActionKey key : sequence) {
            executeActionPair(
                    activeSim,
                    idleSim,
                    actorId,
                    CharacterActionRequest.of(key),
                    true,
                    actorId);
        }
        SimulatorSnapshot activePostSubject = activeSim.saveSnapshot();
        idleSim.restoreSnapshot(buildPostSubjectAlignedSnapshot(activePostSubject, idlePreSubject));
        return activeSim.getCurrentTime();
    }

    private TeamBuffProfileResult evaluatePostSubjectActionProbes(
            CombatSimulator activeSim,
            CombatSimulator idleSim,
            CharacterId subjectId,
            CharacterId[] order,
            int subjectIndex,
            double subjectSyncTime,
            List<String> traceLines) {
        traceLines.add("pre-isolation active-buffs " + dumpAllBuffs(activeSim));
        traceLines.add("pre-isolation idle-buffs   " + dumpAllBuffs(idleSim));
        String activeIsolation = isolateSubjectBuffEffects(activeSim, subjectId, subjectSyncTime, "active");
        String idleIsolation = isolateSubjectBuffEffects(idleSim, subjectId, subjectSyncTime, "idle");
        traceLines.add(activeIsolation);
        traceLines.add(idleIsolation);
        traceLines.add("post-isolation active-buffs " + dumpAllBuffs(activeSim));
        traceLines.add("post-isolation idle-buffs   " + dumpAllBuffs(idleSim));
        activeSim.restoreSnapshot(activeSim.saveSnapshot());
        idleSim.restoreSnapshot(idleSim.saveSnapshot());
        traceLines.add("post-queue-clear active-buffs " + dumpAllBuffs(activeSim));
        traceLines.add("post-queue-clear idle-buffs   " + dumpAllBuffs(idleSim));
        clearNonBuffReactionState(activeSim);
        clearNonBuffReactionState(idleSim);
        traceLines.add("post-aura-clear active-aura " + formatEnemyAura(activeSim));
        traceLines.add("post-aura-clear idle-aura   " + formatEnemyAura(idleSim));
        SimulatorSnapshot activeBase = activeSim.saveSnapshot();
        SimulatorSnapshot idleBase = idleSim.saveSnapshot();
        TeamBuffProfileResult best = TeamBuffProfileResult.zero();
        for (int index = subjectIndex + 1; index < order.length; index++) {
            CharacterId actorId = order[index];
            for (CharacterActionKey actionKey : DAMAGE_PROBE_ACTIONS) {
                activeSim.restoreSnapshot(activeBase);
                idleSim.restoreSnapshot(idleBase);
                activeSim.switchCharacter(actorId);
                idleSim.switchCharacter(actorId);
                TeamBuffProfileResult candidate = probeDirectActionComparison(
                        activeSim,
                        idleSim,
                        actorId,
                        CharacterActionRequest.of(actionKey),
                        subjectId);
                if (candidate.traceSummary != null) {
                    traceLines.add(candidate.traceSummary);
                }
                best = TeamBuffProfileResult.max(best, candidate);
            }
        }
        return best;
    }

    private String isolateSubjectBuffEffects(CombatSimulator sim, CharacterId subjectId, double subjectSyncTime, String label) {
        List<String> dropped = new ArrayList<>();
        for (Character character : sim.getPartyMembers()) {
            character.getActiveBuffs().removeIf(buff -> {
                boolean drop = shouldDropForBuffOnlyProbe(buff, subjectId, subjectSyncTime);
                if (drop) {
                    dropped.add("self:" + character.getCharacterId().name() + ":" + describeBuff(buff));
                }
                return drop;
            });
        }
        sim.getTeamBuffList().removeIf(buff -> {
            boolean drop = shouldDropForBuffOnlyProbe(buff, subjectId, subjectSyncTime);
            if (drop) {
                dropped.add("team:" + describeBuff(buff));
            }
            return drop;
        });
        sim.getFieldBuffList().removeIf(buff -> {
            boolean drop = shouldDropForBuffOnlyProbe(buff, subjectId, subjectSyncTime);
            if (drop) {
                dropped.add("field:" + describeBuff(buff));
            }
            return drop;
        });
        return "subject-buff-filter[" + label + "] subject=" + subjectId.name() + " t=" + format(subjectSyncTime)
                + " dropped=" + (dropped.isEmpty() ? "-" : String.join(" | ", dropped));
    }

    private boolean shouldDropForBuffOnlyProbe(Buff buff, CharacterId subjectId, double subjectSyncTime) {
        if (buff.getSourceCharacterId() != subjectId) {
            return true;
        }
        return buff.getStartTime() > subjectSyncTime + 1e-9;
    }

    private String dumpAllBuffs(CombatSimulator sim) {
        List<String> entries = new ArrayList<>();
        for (Character character : sim.getPartyMembers()) {
            for (Buff buff : character.getActiveBuffs()) {
                if (!buff.isExpired(sim.getCurrentTime())) {
                    entries.add("self:" + character.getCharacterId().name() + ":" + describeBuff(buff));
                }
            }
        }
        for (Buff buff : sim.getTeamBuffList()) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                entries.add("team:" + describeBuff(buff));
            }
        }
        for (Buff buff : sim.getFieldBuffList()) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                entries.add("field:" + describeBuff(buff));
            }
        }
        return entries.isEmpty() ? "-" : String.join(" | ", entries);
    }

    private String describeBuff(Buff buff) {
        return buff.getSourceCharacterId().name() + ":" + buff.getId().name()
                + "@" + format(buff.getStartTime()) + "->" + format(buff.getExpirationTime());
    }

    private void clearNonBuffReactionState(CombatSimulator sim) {
        if (sim.getEnemy() != null) {
            for (model.type.Element element : model.type.Element.values()) {
                sim.getEnemy().setAura(element, 0.0);
            }
        }
        sim.setECTimerRunning(false);
        sim.setThundercloudEndTime(sim.getCurrentTime());
    }

    private TeamBuffProfileResult probeDirectActionComparison(
            CombatSimulator activeSim,
            CombatSimulator idleSim,
            CharacterId actorId,
            CharacterActionRequest request,
            CharacterId subjectId) {
        neutralizeProbeActorSelfBuffs(activeSim, actorId);
        neutralizeProbeActorSelfBuffs(idleSim, actorId);
        String activeState = formatProbeState(activeSim, actorId, request.getKey());
        String idleState = formatProbeState(idleSim, actorId, request.getKey());
        String activeBuffs = formatApplicableBuffs(activeSim, actorId);
        String idleBuffs = formatApplicableBuffs(idleSim, actorId);
        String activeAura = formatEnemyAura(activeSim);
        String idleAura = formatEnemyAura(idleSim);
        activeSim.performAction(actorId, request);
        idleSim.performAction(actorId, request);
        double activeDelta = Math.max(0.0, activeSim.getLastActionDirectDamageCapture());
        double idleDelta = Math.max(0.0, idleSim.getLastActionDirectDamageCapture());
        double uplift = ratioUplift(activeDelta, idleDelta);
        String traceSummary = String.format(
                "probe t=%.2f subject=%s actor=%s action=%s activeDamage=%.1f idleDamage=%.1f uplift=%.3f%n"
                        + "      active-state %s%n"
                        + "      idle-state   %s%n"
                        + "      active-aura  %s%n"
                        + "      idle-aura    %s%n"
                        + "      active-buffs %s%n"
                        + "      idle-buffs   %s",
                activeSim.getCurrentTime(),
                subjectId.name(),
                actorId.name(),
                request.getKey().name(),
                activeDelta,
                idleDelta,
                uplift,
                activeState,
                idleState,
                activeAura,
                idleAura,
                activeBuffs,
                idleBuffs);
        return new TeamBuffProfileResult(uplift, uplift, traceSummary);
    }

    private String formatProbeState(CombatSimulator sim, CharacterId actorId, CharacterActionKey actionKey) {
        Character actor = sim.getCharacter(actorId);
        if (actor == null) {
            return "missing-actor";
        }
        StatsContainer stats = actor.getEffectiveStats(sim.getCurrentTime());
        StatType elementBonusStat = actor.getElement().getBonusStatType();
        double typedBonus = 0.0;
        if (actionKey == CharacterActionKey.BURST) {
            typedBonus = stats.get(StatType.BURST_DMG_BONUS);
        } else if (actionKey == CharacterActionKey.SKILL) {
            typedBonus = stats.get(StatType.SKILL_DMG_BONUS);
        } else if (actionKey == CharacterActionKey.NORMAL) {
            typedBonus = stats.get(StatType.NORMAL_ATTACK_DMG_BONUS);
        }
        return String.format(
                "t=%.2f active=%s atk=%.1f hp=%.1f em=%.1f cr=%.3f cd=%.3f dmgAll=%.3f elemBonus=%.3f typedBonus=%.3f burstCrit=%.3f skillCrit=%.3f lunarBase=%.3f lunarMult=%.3f thundercloud=%s moonsign=%s",
                sim.getCurrentTime(),
                sim.getActiveCharacter() == null ? "-" : sim.getActiveCharacter().getCharacterId().name(),
                stats.getTotalAtk(),
                stats.getTotalHp(),
                stats.get(StatType.ELEMENTAL_MASTERY),
                stats.get(StatType.CRIT_RATE),
                stats.get(StatType.CRIT_DMG),
                stats.get(StatType.DMG_BONUS_ALL),
                stats.get(elementBonusStat),
                typedBonus,
                stats.get(StatType.BURST_CRIT_RATE),
                stats.get(StatType.SKILL_CRIT_RATE),
                stats.get(StatType.LUNAR_BASE_BONUS),
                stats.get(StatType.LUNAR_MULTIPLIER),
                sim.isThundercloudActive(),
                sim.getMoonsign().name());
    }

    private String formatApplicableBuffs(CombatSimulator sim, CharacterId actorId) {
        Character actor = sim.getCharacter(actorId);
        if (actor == null) {
            return "missing-actor";
        }
        List<String> names = new ArrayList<>();
        for (Buff buff : actor.getActiveBuffs()) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                names.add("self[" + buff.getSourceCharacterId().name() + "]:" + buff.getId().name() + ":" + buff.getName());
            }
        }
        for (Buff buff : sim.getApplicableBuffs(actor)) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                names.add("team[" + buff.getSourceCharacterId().name() + "]:" + buff.getId().name() + ":" + buff.getName());
            }
        }
        if (names.isEmpty()) {
            return "-";
        }
        return String.join(" | ", names);
    }

    private String formatEnemyAura(CombatSimulator sim) {
        if (sim.getEnemy() == null) {
            return "-";
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<model.type.Element, Double> entry : sim.getEnemy().getAuraMap().entrySet()) {
            if (entry.getValue() > 1e-9) {
                entries.add(entry.getKey().name() + "=" + format(entry.getValue()));
            }
        }
        return entries.isEmpty() ? "-" : String.join(" ", entries);
    }

    private void neutralizeProbeActorSelfBuffs(CombatSimulator sim, CharacterId actorId) {
        Character actor = sim.getCharacter(actorId);
        if (actor == null) {
            return;
        }
        actor.getActiveBuffs().removeIf(buff -> buff.getSourceCharacterId() == actorId);
    }

    private TeamBuffProfileResult executeActionPair(
            CombatSimulator activeSim,
            CombatSimulator idleSim,
            CharacterId actorId,
            CharacterActionRequest request,
            boolean idleActor,
            CharacterId excludedId) {
        double before = activeSim.getCurrentTime();
        double teamBeforeActive = getTeamDamageExcluding(activeSim, excludedId);
        double teamBeforeIdle = getTeamDamageExcluding(idleSim, excludedId);
        double[] charBeforeActive = getPerCharacterDamageExcluding(activeSim, excludedId);
        double[] charBeforeIdle = getPerCharacterDamageExcluding(idleSim, excludedId);
        activeSim.performAction(actorId, request);
        double delta = Math.max(0.0, activeSim.getCurrentTime() - before);
        if (delta > 0.0) {
            if (idleActor) {
                idleSim.advanceTime(delta);
            } else {
                double idleBefore = idleSim.getCurrentTime();
                idleSim.performAction(actorId, request);
                double idleDelta = Math.max(0.0, idleSim.getCurrentTime() - idleBefore);
                if (idleDelta + 1e-9 < delta) {
                    idleSim.advanceTime(delta - idleDelta);
                }
            }
        }
        if (idleActor) {
            return TeamBuffProfileResult.zero();
        }
        return actionStepUplift(
                activeSim,
                idleSim,
                actorId,
                request.getKey(),
                excludedId,
                teamBeforeActive,
                teamBeforeIdle,
                charBeforeActive,
                charBeforeIdle);
    }

    private SimulatorSnapshot buildPostSubjectAlignedSnapshot(
            SimulatorSnapshot activePostSubject,
            SimulatorSnapshot idlePreSubject) {
        Map<CharacterId, SimulatorSnapshot.CharacterSnapshot> mergedCharacters = new HashMap<>();
        for (Map.Entry<CharacterId, SimulatorSnapshot.CharacterSnapshot> entry : activePostSubject.characters.entrySet()) {
            CharacterId characterId = entry.getKey();
            SimulatorSnapshot.CharacterSnapshot activeCharacter = entry.getValue();
            SimulatorSnapshot.CharacterSnapshot idleCharacter = idlePreSubject.characters.get(characterId);
            if (idleCharacter == null) {
                mergedCharacters.put(characterId, activeCharacter);
                continue;
            }
            mergedCharacters.put(characterId, new SimulatorSnapshot.CharacterSnapshot(
                    activeCharacter.currentEnergy,
                    activeCharacter.lastSkillTime,
                    activeCharacter.lastBurstTime,
                    activeCharacter.chargeRestoreTimes,
                    idleCharacter.activeBuffRefs,
                    idleCharacter.activeBuffTimes));
        }
        return new SimulatorSnapshot(
                activePostSubject.currentTime,
                activePostSubject.rotationTime,
                idlePreSubject.totalDamage,
                idlePreSubject.damageBySource,
                activePostSubject.lastSwapTime,
                activePostSubject.activeCharacterId,
                activePostSubject.moonsign,
                activePostSubject.icdStates,
                activePostSubject.ecTimerRunning,
                activePostSubject.thundercloudEndTime,
                activePostSubject.enemyAura,
                mergedCharacters,
                idlePreSubject.teamBuffRefs,
                idlePreSubject.teamBuffTimes,
                idlePreSubject.fieldBuffRefs,
                idlePreSubject.fieldBuffTimes);
    }

    private double getTeamDamageExcluding(CombatSimulator sim, CharacterId excludedId) {
        double total = 0.0;
        for (CharacterId id : config.partyOrder) {
            if (id != excludedId) {
                total += sim.getDamageByCharacter(id);
            }
        }
        return total;
    }

    private double[] getPerCharacterDamageExcluding(CombatSimulator sim, CharacterId excludedId) {
        List<Double> values = new ArrayList<>();
        for (CharacterId id : config.partyOrder) {
            if (id != excludedId) {
                values.add(sim.getDamageByCharacter(id));
            }
        }
        double[] result = new double[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private List<CharacterId> getCharacterIdsExcluding(CharacterId excludedId) {
        List<CharacterId> ids = new ArrayList<>();
        for (CharacterId id : config.partyOrder) {
            if (id != excludedId) {
                ids.add(id);
            }
        }
        return ids;
    }

    private TeamBuffProfileResult actionStepUplift(
            CombatSimulator activeSim,
            CombatSimulator idleSim,
            CharacterId actorId,
            CharacterActionKey actionKey,
            CharacterId excludedId,
            double teamBeforeActive,
            double teamBeforeIdle,
            double[] charBeforeActive,
            double[] charBeforeIdle) {
        double teamDeltaActive = Math.max(0.0, getTeamDamageExcluding(activeSim, excludedId) - teamBeforeActive);
        double teamDeltaIdle = Math.max(0.0, getTeamDamageExcluding(idleSim, excludedId) - teamBeforeIdle);
        double teamUplift = ratioUplift(teamDeltaActive, teamDeltaIdle);

        double[] charAfterActive = getPerCharacterDamageExcluding(activeSim, excludedId);
        double[] charAfterIdle = getPerCharacterDamageExcluding(idleSim, excludedId);
        double beneficiaryUplift = 0.0;
        CharacterId beneficiaryId = null;
        double beneficiaryActiveDelta = 0.0;
        double beneficiaryIdleDelta = 0.0;
        List<CharacterId> beneficiaryIds = getCharacterIdsExcluding(excludedId);
        int count = Math.min(Math.min(charBeforeActive.length, charBeforeIdle.length), Math.min(charAfterActive.length, charAfterIdle.length));
        for (int index = 0; index < count; index++) {
            double activeDelta = Math.max(0.0, charAfterActive[index] - charBeforeActive[index]);
            double idleDelta = Math.max(0.0, charAfterIdle[index] - charBeforeIdle[index]);
            double uplift = ratioUplift(activeDelta, idleDelta);
            if (uplift > beneficiaryUplift) {
                beneficiaryUplift = uplift;
                beneficiaryId = beneficiaryIds.get(index);
                beneficiaryActiveDelta = activeDelta;
                beneficiaryIdleDelta = idleDelta;
            }
        }
        String traceSummary = String.format(
                "t=%.2f actor=%s action=%s team(active=%.1f idle=%.1f uplift=%.3f) beneficiary=%s(active=%.1f idle=%.1f uplift=%.3f)",
                activeSim.getCurrentTime(),
                actorId.name(),
                actionKey.name(),
                teamDeltaActive,
                teamDeltaIdle,
                teamUplift,
                beneficiaryId != null ? beneficiaryId.name() : "-",
                beneficiaryActiveDelta,
                beneficiaryIdleDelta,
                beneficiaryUplift);
        return new TeamBuffProfileResult(teamUplift, beneficiaryUplift, traceSummary);
    }

    private double ratioUplift(double activeDamageDelta, double idleDamageDelta) {
        if (idleDamageDelta < MIN_DAMAGE_FOR_RATIO) {
            return 0.0;
        }
        return Math.max(0.0, (activeDamageDelta / idleDamageDelta) - 1.0);
    }

    private static List<CharacterId[]> buildPermutations(CharacterId[] source) {
        List<CharacterId[]> permutations = new ArrayList<>();
        CharacterId[] working = source.clone();
        permute(working, 0, permutations);
        return permutations;
    }

    private static List<List<CharacterActionKey>> buildActionSequenceLibrary() {
        return Arrays.asList(
                Arrays.asList(CharacterActionKey.SKILL),
                Arrays.asList(CharacterActionKey.BURST),
                Arrays.asList(CharacterActionKey.SKILL, CharacterActionKey.BURST),
                Arrays.asList(CharacterActionKey.BURST, CharacterActionKey.SKILL),
                Arrays.asList(CharacterActionKey.SKILL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL),
                Arrays.asList(CharacterActionKey.BURST, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL),
                Arrays.asList(CharacterActionKey.SKILL, CharacterActionKey.BURST, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL),
                Arrays.asList(CharacterActionKey.BURST, CharacterActionKey.SKILL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL),
                Arrays.asList(CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL, CharacterActionKey.NORMAL));
    }

    private void printBestTeamBuffTrace(TeamBuffProfileResult result) {
        if (result == null || result.scenarioOrder == null || result.scenarioSequences == null) {
            return;
        }
        System.out.printf("  bestTeamBuffScenario composite=%.4f team=%.4f beneficiary=%.4f%n",
                result.compositeUplift, result.teamUplift, result.beneficiaryUplift);
        System.out.printf("  bestOrder=%s%n", formatOrder(result.scenarioOrder));
        for (int index = 0; index < result.scenarioOrder.length; index++) {
            System.out.printf("    %s -> %s%n",
                    result.scenarioOrder[index].name(),
                    formatSequence(result.scenarioSequences.get(index)));
        }
        for (String line : result.traceLines) {
            String[] parts = line.split("\\R");
            for (String part : parts) {
                System.out.printf("    %s%n", part);
            }
        }
    }

    private static String formatOrder(CharacterId[] order) {
        List<String> names = new ArrayList<>();
        for (CharacterId id : order) {
            names.add(id.name());
        }
        return String.join(" -> ", names);
    }

    private static String formatSequence(List<CharacterActionKey> sequence) {
        List<String> keys = new ArrayList<>();
        for (CharacterActionKey key : sequence) {
            keys.add(key.name());
        }
        return String.join(" ", keys);
    }

    private static void permute(CharacterId[] values, int index, List<CharacterId[]> out) {
        if (index >= values.length) {
            out.add(values.clone());
            return;
        }
        for (int cursor = index; cursor < values.length; cursor++) {
            swap(values, index, cursor);
            permute(values, index + 1, out);
            swap(values, index, cursor);
        }
    }

    private static void swap(CharacterId[] values, int left, int right) {
        if (left == right) {
            return;
        }
        CharacterId tmp = values[left];
        values[left] = values[right];
        values[right] = tmp;
    }

    @FunctionalInterface
    private interface TemplateRun {
        double execute();
    }

    private static final class TeamBuffProfileResult {
        private final double teamUplift;
        private final double beneficiaryUplift;
        private final double compositeUplift;
        private final String traceSummary;
        private final CharacterId[] scenarioOrder;
        private final List<List<CharacterActionKey>> scenarioSequences;
        private final List<String> traceLines;

        private TeamBuffProfileResult(double teamUplift, double beneficiaryUplift) {
            this(teamUplift, beneficiaryUplift, null, null, null, null);
        }

        private TeamBuffProfileResult(double teamUplift, double beneficiaryUplift, String traceSummary) {
            this(teamUplift, beneficiaryUplift, traceSummary, null, null, null);
        }

        private TeamBuffProfileResult(
                double teamUplift,
                double beneficiaryUplift,
                String traceSummary,
                CharacterId[] scenarioOrder,
                List<List<CharacterActionKey>> scenarioSequences,
                List<String> traceLines) {
            this.teamUplift = teamUplift;
            this.beneficiaryUplift = beneficiaryUplift;
            this.compositeUplift = 0.5 * teamUplift + 0.5 * beneficiaryUplift;
            this.traceSummary = traceSummary;
            this.scenarioOrder = scenarioOrder;
            this.scenarioSequences = scenarioSequences;
            this.traceLines = traceLines;
        }

        private static TeamBuffProfileResult zero() {
            return new TeamBuffProfileResult(0.0, 0.0);
        }

        private static TeamBuffProfileResult max(TeamBuffProfileResult left, TeamBuffProfileResult right) {
            return right.compositeUplift > left.compositeUplift ? right : left;
        }

        private TeamBuffProfileResult withScenario(
                CharacterId[] order,
                List<List<CharacterActionKey>> sequences,
                List<String> lines) {
            List<List<CharacterActionKey>> sequenceCopy = new ArrayList<>();
            for (List<CharacterActionKey> sequence : sequences) {
                sequenceCopy.add(new ArrayList<>(sequence));
            }
            return new TeamBuffProfileResult(
                    teamUplift,
                    beneficiaryUplift,
                    traceSummary,
                    order.clone(),
                    sequenceCopy,
                    new ArrayList<>(lines));
        }
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

    private static double meanList(List<Double> values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / Math.max(1, values.size());
    }

    private void normalizeTeamBuffScores() {
        List<Double> positives = new ArrayList<>();
        for (double[] scores : results.values()) {
            double raw = Math.max(0.0, scores[CapabilityProfile.TEAM_BUFF_SCORE]);
            if (raw > 1e-8) {
                positives.add(raw);
            }
        }
        if (positives.isEmpty()) {
            return;
        }
        double scale = percentile(positives, 0.75);
        if (scale <= 1e-8) {
            scale = meanList(positives);
        }
        if (scale <= 1e-8) {
            scale = 1.0;
        }
        for (Map.Entry<CharacterId, double[]> entry : results.entrySet()) {
            double[] scores = entry.getValue();
            double raw = Math.max(0.0, scores[CapabilityProfile.TEAM_BUFF_SCORE]);
            double normalized = raw / (raw + scale);
            scores[CapabilityProfile.TEAM_BUFF_SCORE] = clamp01(normalized);
            System.out.printf(
                    "  [CapabilityProfiler] teamBuff normalized: %s raw=%.4f scale=%.4f normalized=%.4f%n",
                    entry.getKey().name(),
                    raw,
                    scale,
                    scores[CapabilityProfile.TEAM_BUFF_SCORE]);
        }
    }

    private static double percentile(List<Double> values, double q) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        double clampedQ = Math.max(0.0, Math.min(1.0, q));
        int index = (int) Math.floor(clampedQ * (sorted.size() - 1));
        return sorted.get(index);
    }

    private static String format(double v) {
        return String.format("%.6f", v);
    }
}
