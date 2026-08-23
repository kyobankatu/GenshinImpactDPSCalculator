package mechanics.rotation;

import model.entity.Character;
import simulation.CombatSimulator;

/**
 * Evaluates a complete or partial rotation without coupling search to reward
 * shaping used by the online RL environment.
 */
public final class RotationObjective {
    private final double requiredEndEnergyFraction;
    private final double energyDeficitPenalty;
    private final double invalidActionPenalty;
    private final double energyFeasibilityTolerance;

    /**
     * Creates an objective with explicit cyclic-energy and invalid-action costs.
     *
     * @param requiredEndEnergyFraction required fraction of each energy bar at the horizon
     * @param energyDeficitPenalty score penalty per missing energy point
     * @param invalidActionPenalty score penalty per invalid action
     * @param energyFeasibilityTolerance maximum total energy deficit considered feasible
     */
    public RotationObjective(
            double requiredEndEnergyFraction,
            double energyDeficitPenalty,
            double invalidActionPenalty,
            double energyFeasibilityTolerance) {
        requireFiniteRange(requiredEndEnergyFraction, 0.0, 1.0, "requiredEndEnergyFraction");
        requireFiniteNonNegative(energyDeficitPenalty, "energyDeficitPenalty");
        requireFiniteNonNegative(invalidActionPenalty, "invalidActionPenalty");
        requireFiniteNonNegative(energyFeasibilityTolerance, "energyFeasibilityTolerance");
        this.requiredEndEnergyFraction = requiredEndEnergyFraction;
        this.energyDeficitPenalty = energyDeficitPenalty;
        this.invalidActionPenalty = invalidActionPenalty;
        this.energyFeasibilityTolerance = energyFeasibilityTolerance;
    }

    /**
     * Returns the default objective used by expert search.
     *
     * @return objective requiring full cyclic energy with inspectable penalties
     */
    public static RotationObjective cyclicDamage() {
        return new RotationObjective(1.0, 100.0, 10000.0, 0.01);
    }

    /**
     * Evaluates the current simulator state.
     *
     * @param simulator active simulator
     * @param invalidActionCount invalid actions taken since reset
     * @return immutable score components
     */
    public Score evaluate(CombatSimulator simulator, int invalidActionCount) {
        if (simulator == null) {
            throw new IllegalArgumentException("simulator must not be null");
        }
        double energyDeficit = 0.0;
        for (Character character : simulator.getPartyMembers()) {
            double requiredEnergy = character.getMaxEnergy() * requiredEndEnergyFraction;
            energyDeficit += Math.max(0.0, requiredEnergy - character.getCurrentEnergy());
        }
        return evaluate(
                simulator.getTotalDamage(),
                simulator.getCurrentTime(),
                energyDeficit,
                invalidActionCount);
    }

    /**
     * Evaluates pre-aggregated objective components. This overload keeps tests
     * and offline dataset validation independent of simulator construction.
     *
     * @param totalDamage accumulated damage
     * @param elapsedSeconds elapsed simulator time
     * @param energyDeficit total missing cyclic energy
     * @param invalidActionCount invalid actions taken
     * @return immutable score components
     */
    public Score evaluate(
            double totalDamage,
            double elapsedSeconds,
            double energyDeficit,
            int invalidActionCount) {
        requireFiniteNonNegative(totalDamage, "totalDamage");
        requireFiniteNonNegative(elapsedSeconds, "elapsedSeconds");
        requireFiniteNonNegative(energyDeficit, "energyDeficit");
        if (invalidActionCount < 0) {
            throw new IllegalArgumentException("invalidActionCount must be non-negative");
        }
        double dps = elapsedSeconds > 0.0 ? totalDamage / elapsedSeconds : 0.0;
        double objectiveScore = totalDamage
                - energyDeficitPenalty * energyDeficit
                - invalidActionPenalty * invalidActionCount;
        return new Score(
                totalDamage,
                dps,
                elapsedSeconds,
                energyDeficit,
                invalidActionCount,
                energyDeficit <= energyFeasibilityTolerance,
                objectiveScore);
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requireFiniteRange(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be finite and between " + minimum + " and " + maximum);
        }
    }

    /** Immutable decomposition of one rotation objective value. */
    public static final class Score {
        public final double totalDamage;
        public final double dps;
        public final double elapsedSeconds;
        public final double energyDeficit;
        public final int invalidActionCount;
        public final boolean cyclicEnergyFeasible;
        public final double objectiveScore;

        private Score(
                double totalDamage,
                double dps,
                double elapsedSeconds,
                double energyDeficit,
                int invalidActionCount,
                boolean cyclicEnergyFeasible,
                double objectiveScore) {
            this.totalDamage = totalDamage;
            this.dps = dps;
            this.elapsedSeconds = elapsedSeconds;
            this.energyDeficit = energyDeficit;
            this.invalidActionCount = invalidActionCount;
            this.cyclicEnergyFeasible = cyclicEnergyFeasible;
            this.objectiveScore = objectiveScore;
        }
    }
}
