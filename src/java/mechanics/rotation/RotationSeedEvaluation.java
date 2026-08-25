package mechanics.rotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import mechanics.optimization.PartyBuildResolver;
import mechanics.optimization.TotalOptimizationResult;
import mechanics.rl.EpisodeConfig;
import model.entity.Character;
import model.type.CharacterId;
import simulation.CombatSimulator;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/** Replays reviewed human seeds across uninterrupted rotation cycles. */
public final class RotationSeedEvaluation {
    private static final int BUILD_CALIBRATION_CYCLES = 3;
    private static final double TIME_TOLERANCE = 1.0e-6;
    private static final double BOUNDARY_TOLERANCE = 0.100001;
    private static final double ER_TOLERANCE = 0.01;

    private RotationSeedEvaluation() {
    }

    /** Calibrates and replays one reviewed seed using its exact action sequence. */
    public static Result evaluate(SourcedRotationSeed seed, int cycleCount, long scenarioSeed) {
        validateSeed(seed, cycleCount);
        PartyDefinition definition = PartyCatalog.require(seed.getPartyName());
        requireMatchingLoadout(seed, definition);
        TotalOptimizationResult build = resolveBuild(seed, definition);
        return evaluate(seed, definition, build, cycleCount, scenarioSeed);
    }

    /** Returns the exact frozen build calibrated for one reviewed source seed. */
    public static TotalOptimizationResult resolveBuild(SourcedRotationSeed seed) {
        if (seed == null) {
            throw new IllegalArgumentException("Rotation seed must not be null");
        }
        seed.validate();
        if (!seed.isUsable()) {
            throw failure(seed, "rejected seed cannot resolve a build");
        }
        PartyDefinition definition = PartyCatalog.require(seed.getPartyName());
        requireMatchingLoadout(seed, definition);
        return resolveBuild(seed, definition);
    }

    /** Replays one seed against a supplied frozen build for tests and audits. */
    public static Result evaluate(
            SourcedRotationSeed seed,
            PartyDefinition definition,
            TotalOptimizationResult build,
            int cycleCount,
            long scenarioSeed) {
        validateSeed(seed, cycleCount);
        if (definition == null || build == null) {
            throw failure(seed, "party definition and optimized build are required");
        }
        if (!seed.getPartyName().equals(definition.name())) {
            throw failure(seed, "party name does not match the supplied definition");
        }
        requireMatchingLoadout(seed, definition);
        validateErTargets(seed, definition, build);

        RotationScenario scenario = RotationScenario.forPartyBuild(
                definition,
                build,
                new EpisodeConfig(),
                definition.rotationCycleSeconds(),
                cycleCount,
                scenarioSeed,
                RotationObjective.cyclicDamage());
        List<CycleResult> cycles = new ArrayList<>();
        try (BattleRotationEnvironment environment = new BattleRotationEnvironment(scenario)) {
            RotationStep step = environment.reset();
            environment.getSimulator().getEnergyDistributor().scheduleKQMSEnemyParticles(
                    definition.rotationCycleSeconds());
            List<Integer> pendingTrace = new ArrayList<>();
            step = executeActions(
                    environment,
                    step,
                    seed,
                    seed.getOpenerActions(),
                    "opener",
                    pendingTrace,
                    build.erTargets);
            double previousDamage = 0.0;
            List<int[]> cycleActions = seed.getCycleActions();
            for (int cycleIndex = 0; cycleIndex < cycleCount; cycleIndex++) {
                int[] actions = cycleActions.get(cycleIndex % cycleActions.size());
                step = executeActions(
                        environment,
                        step,
                        seed,
                        actions,
                        "cycle " + (cycleIndex + 1),
                        pendingTrace,
                        build.erTargets);
                double boundary = definition.rotationCycleSeconds() * (cycleIndex + 1);
                step = advanceToBoundary(
                        environment,
                        step,
                        seed,
                        boundary,
                        cycleIndex + 1,
                        pendingTrace);
                double cycleDamage = step.objective.totalDamage - previousDamage;
                previousDamage = step.objective.totalDamage;
                cycles.add(new CycleResult(
                        cycleIndex + 1,
                        cycleDamage,
                        cycleDamage / definition.rotationCycleSeconds(),
                        step.objective.elapsedSeconds,
                        endingEnergy(environment.getSimulator()),
                        pendingTrace,
                        step.stateHash,
                        step.objective.cyclicEnergyFeasible));
                pendingTrace = new ArrayList<>();
            }
            if (!step.done || Math.abs(step.objective.elapsedSeconds - scenario.getHorizonSeconds())
                    > BOUNDARY_TOLERANCE) {
                throw failure(seed, "replay ended with a partial final cycle");
            }
            int period = seed.getCycleActions().size();
            boolean cyclicEnergyFeasible = isSteadyEnergy(cycles, period);
            if (!cyclicEnergyFeasible) {
                CycleResult current = cycles.get(cycles.size() - 1);
                CycleResult previous = cycles.get(cycles.size() - 1 - period);
                throw failure(seed, "ending Energy decays across equivalent cycle phases: previous="
                        + previous.endingEnergy + ", current=" + current.endingEnergy);
            }
            double steadyDamage = 0.0;
            for (int index = 1; index < cycles.size(); index++) {
                steadyDamage += cycles.get(index).damage;
            }
            double steadyDps = steadyDamage
                    / (definition.rotationCycleSeconds() * (cycles.size() - 1));
            return new Result(
                    seed.getSeedId(),
                    scenario.getFingerprint(),
                    build.getBuildFingerprint(),
                    cycles,
                    cycles.get(0).damage,
                    steadyDps,
                    cyclicEnergyFeasible,
                    step.objective);
        }
    }

    private static RotationStep executeActions(
            BattleRotationEnvironment environment,
            RotationStep step,
            SourcedRotationSeed seed,
            int[] actions,
            String section,
            List<Integer> trace,
            Map<CharacterId, Double> requiredEr) {
        for (int index = 0; index < actions.length; index++) {
            int actionId = actions[index];
            if (step.done) {
                throw failure(seed, section + " exceeds the declared horizon");
            }
            if (actionId < 0 || actionId >= step.legalActionMask.length
                    || step.legalActionMask[actionId] < 0.5) {
                throw failure(seed, "unavailable action " + actionId + " in " + section
                        + " at index " + index
                        + actionState(environment, actionId, requiredEr));
            }
            step = environment.step(actionId);
            if (!step.validAction) {
                throw failure(seed, "action " + actionId + " was rejected in " + section);
            }
            trace.add(actionId);
        }
        return step;
    }

    private static String actionState(
            BattleRotationEnvironment environment,
            int actionId,
            Map<CharacterId, Double> requiredEr) {
        CombatSimulator simulator = environment.getSimulator();
        Character active = simulator.getActiveCharacter();
        if (active == null) {
            return " (time=" + simulator.getCurrentTime() + ", active=none)";
        }
        PolicyAction action = actionId >= 0 && actionId < PolicyAction.SIZE
                ? PolicyAction.fromId(actionId)
                : null;
        StringBuilder state = new StringBuilder(" (time=")
                .append(simulator.getCurrentTime())
                .append(", active=").append(active.getCharacterId());
        if (action != null && action.getActionRequest() != null) {
            switch (action.getActionRequest().getKey()) {
                case BURST:
                    state.append(", energy=").append(active.getCurrentEnergy())
                            .append('/').append(active.getEnergyCost())
                            .append(", er=")
                            .append(active.getEffectiveStats(simulator.getCurrentTime())
                                    .getTotalEnergyRecharge())
                            .append(", requiredEr=")
                            .append(requiredEr.get(active.getCharacterId()))
                            .append(", burstCd=")
                            .append(active.getBurstCDRemaining(simulator.getCurrentTime()));
                    break;
                case SKILL:
                    state.append(", skillCd=")
                            .append(active.getSkillCDRemaining(simulator.getCurrentTime()));
                    break;
                default:
                    break;
            }
        }
        return state.append(')').toString();
    }

    private static RotationStep advanceToBoundary(
            BattleRotationEnvironment environment,
            RotationStep step,
            SourcedRotationSeed seed,
            double boundary,
            int cycleIndex,
            List<Integer> trace) {
        if (step.objective.elapsedSeconds > boundary + BOUNDARY_TOLERANCE) {
            throw failure(seed, "cycle " + cycleIndex + " exceeds its declared horizon");
        }
        while (!step.done && step.objective.elapsedSeconds < boundary - TIME_TOLERANCE) {
            step = environment.step(PolicyAction.WAIT_SHORT.getId());
            if (!step.validAction || step.objective.elapsedSeconds > boundary + BOUNDARY_TOLERANCE) {
                throw failure(seed, "cycle " + cycleIndex + " cannot reach its exact boundary");
            }
            trace.add(PolicyAction.WAIT_SHORT.getId());
        }
        return step;
    }

    private static void executeDirect(
            CombatSimulator simulator,
            SourcedRotationSeed seed,
            PartyDefinition definition,
            int cycleCount) {
        simulator.getEnergyDistributor().scheduleKQMSEnemyParticles(
                definition.rotationCycleSeconds());
        executeDirectActions(simulator, definition.partyOrder(), seed.getOpenerActions());
        List<int[]> cycles = seed.getCycleActions();
        for (int cycleIndex = 0; cycleIndex < cycleCount; cycleIndex++) {
            executeDirectActions(
                    simulator,
                    definition.partyOrder(),
                    cycles.get(cycleIndex % cycles.size()));
            double boundary = definition.rotationCycleSeconds() * (cycleIndex + 1);
            if (simulator.getCurrentTime() > boundary + TIME_TOLERANCE) {
                throw failure(seed, "optimizer replay exceeds cycle " + (cycleIndex + 1));
            }
            if (simulator.getCurrentTime() < boundary) {
                simulator.advanceTime(boundary - simulator.getCurrentTime());
            }
        }
    }

    private static void executeDirectActions(
            CombatSimulator simulator,
            CharacterId[] partyOrder,
            int[] actions) {
        for (int actionId : actions) {
            PolicyAction action = PolicyAction.fromId(actionId);
            if (action.isWait()) {
                simulator.advanceTime(0.1);
            } else if (action.isSwap()) {
                simulator.switchCharacter(partyOrder[action.getTargetSlot()]);
            } else {
                simulator.performAction(
                        simulator.getActiveCharacter().getCharacterId(),
                        action.getActionRequest());
            }
        }
    }

    private static TotalOptimizationResult resolveBuild(
            SourcedRotationSeed seed,
            PartyDefinition definition) {
        return PartyBuildResolver.require(
                definition,
                "source:" + seed.getContentHash(),
                simulator -> executeDirect(
                        simulator,
                        seed,
                        definition,
                        BUILD_CALIBRATION_CYCLES));
    }

    private static void validateSeed(SourcedRotationSeed seed, int cycleCount) {
        if (seed == null) {
            throw new IllegalArgumentException("Rotation seed must not be null");
        }
        seed.validate();
        if (!seed.isUsable()) {
            throw failure(seed, "rejected seed cannot be evaluated");
        }
        if (cycleCount < 2) {
            throw failure(seed, "at least two cycles are required for steady-state evaluation");
        }
    }

    private static void requireMatchingLoadout(
            SourcedRotationSeed seed,
            PartyDefinition definition) {
        if (!definition.loadoutFingerprint().equals(seed.getScenarioFingerprint())) {
            throw failure(seed, "loadout fingerprint is stale");
        }
    }

    private static void validateErTargets(
            SourcedRotationSeed seed,
            PartyDefinition definition,
            TotalOptimizationResult build) {
        for (CharacterId characterId : definition.partyOrder()) {
            Double declared = seed.getErTargets().get(characterId.name());
            Double required = build.erTargets.get(characterId);
            if (declared == null || required == null) {
                throw failure(seed, "ER targets do not cover " + characterId);
            }
            if (declared + ER_TOLERANCE < required) {
                throw failure(seed, "declared ER for " + characterId + " is " + declared
                        + " but the replay requires " + required);
            }
        }
    }

    private static Map<CharacterId, Double> endingEnergy(CombatSimulator simulator) {
        Map<CharacterId, Double> energy = new EnumMap<>(CharacterId.class);
        for (Character character : simulator.getPartyMembers()) {
            energy.put(character.getCharacterId(), character.getCurrentEnergy());
        }
        return Collections.unmodifiableMap(energy);
    }

    private static boolean isSteadyEnergy(List<CycleResult> cycles, int period) {
        if (cycles.size() <= period) {
            return false;
        }
        CycleResult current = cycles.get(cycles.size() - 1);
        CycleResult previous = cycles.get(cycles.size() - 1 - period);
        for (Map.Entry<CharacterId, Double> entry : current.endingEnergy.entrySet()) {
            double priorEnergy = previous.endingEnergy.getOrDefault(entry.getKey(), 0.0);
            if (entry.getValue() + ER_TOLERANCE < priorEnergy) {
                return false;
            }
        }
        return true;
    }

    private static IllegalArgumentException failure(SourcedRotationSeed seed, String message) {
        return new IllegalArgumentException("Rotation seed " + seed.getSeedId() + ": " + message);
    }

    /** Immutable metrics captured at one complete cycle boundary. */
    public static final class CycleResult {
        public final int cycleIndex;
        public final double damage;
        public final double dps;
        public final double elapsedSeconds;
        public final Map<CharacterId, Double> endingEnergy;
        public final List<Integer> executedActions;
        public final long stateHash;
        public final boolean cyclicEnergyFeasible;

        private CycleResult(
                int cycleIndex,
                double damage,
                double dps,
                double elapsedSeconds,
                Map<CharacterId, Double> endingEnergy,
                List<Integer> executedActions,
                long stateHash,
                boolean cyclicEnergyFeasible) {
            this.cycleIndex = cycleIndex;
            this.damage = damage;
            this.dps = dps;
            this.elapsedSeconds = elapsedSeconds;
            this.endingEnergy = endingEnergy;
            this.executedActions = List.copyOf(executedActions);
            this.stateHash = stateHash;
            this.cyclicEnergyFeasible = cyclicEnergyFeasible;
        }
    }

    /** Immutable complete replay result separating opener and steady cycles. */
    public static final class Result {
        public final String seedId;
        public final String scenarioFingerprint;
        public final String buildFingerprint;
        public final List<CycleResult> cycles;
        public final double firstCycleDamage;
        public final double steadyCycleDps;
        public final boolean cyclicEnergyFeasible;
        public final RotationObjective.Score finalScore;

        private Result(
                String seedId,
                String scenarioFingerprint,
                String buildFingerprint,
                List<CycleResult> cycles,
                double firstCycleDamage,
                double steadyCycleDps,
                boolean cyclicEnergyFeasible,
                RotationObjective.Score finalScore) {
            this.seedId = seedId;
            this.scenarioFingerprint = scenarioFingerprint;
            this.buildFingerprint = buildFingerprint;
            this.cycles = List.copyOf(cycles);
            this.firstCycleDamage = firstCycleDamage;
            this.steadyCycleDps = steadyCycleDps;
            this.cyclicEnergyFeasible = cyclicEnergyFeasible;
            this.finalScore = finalScore;
        }
    }
}
