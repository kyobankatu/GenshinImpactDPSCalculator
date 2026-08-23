package mechanics.rotation;

/** Immutable state returned by reset, step, and restore operations. */
public final class RotationStep {
    public final double[] observation;
    public final double[] privilegedObservation;
    public final double[] legalActionMask;
    public final double reward;
    public final boolean done;
    public final boolean validAction;
    public final double damageDelta;
    public final double totalDamage;
    public final double timeDelta;
    public final int actionId;
    public final int stepCount;
    public final int partyId;
    public final long stateHash;
    public final RotationObjective.Score objective;

    /** Creates a defensive, persistent copy of one environment state. */
    public RotationStep(
            double[] observation,
            double[] privilegedObservation,
            double[] legalActionMask,
            double reward,
            boolean done,
            boolean validAction,
            double damageDelta,
            double totalDamage,
            double timeDelta,
            int actionId,
            int stepCount,
            int partyId,
            long stateHash,
            RotationObjective.Score objective) {
        if (observation == null || privilegedObservation == null || legalActionMask == null) {
            throw new IllegalArgumentException("state arrays must not be null");
        }
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        this.observation = observation.clone();
        this.privilegedObservation = privilegedObservation.clone();
        this.legalActionMask = legalActionMask.clone();
        this.reward = reward;
        this.done = done;
        this.validAction = validAction;
        this.damageDelta = damageDelta;
        this.totalDamage = totalDamage;
        this.timeDelta = timeDelta;
        this.actionId = actionId;
        this.stepCount = stepCount;
        this.partyId = partyId;
        this.stateHash = stateHash;
        this.objective = objective;
    }

    /** Returns a defensive copy suitable for snapshot storage and callers. */
    public RotationStep copy() {
        return new RotationStep(
                observation,
                privilegedObservation,
                legalActionMask,
                reward,
                done,
                validAction,
                damageDelta,
                totalDamage,
                timeDelta,
                actionId,
                stepCount,
                partyId,
                stateHash,
                objective);
    }
}
