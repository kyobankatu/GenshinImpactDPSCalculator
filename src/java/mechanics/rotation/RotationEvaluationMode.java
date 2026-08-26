package mechanics.rotation;

/** Controls whether a proposed rotation may repair unavailable actions. */
public enum RotationEvaluationMode {
    /** Executes every proposed action exactly and rejects the first unavailable action. */
    STRICT,
    /** Replaces unavailable proposal actions with legal policy-guided samples. */
    REPAIR
}
