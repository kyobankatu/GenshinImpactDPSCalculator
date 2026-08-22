package model.entity;

/** Capability for character-owned states explicitly extended by owner hitlag. */
public interface OwnerHitlagAwareCharacter {
    /**
     * Extends source-backed character-local state after a landed owner hit.
     *
     * @param currentTime hit time in global simulator seconds
     * @param duration effective owner freeze duration in seconds
     */
    void onOwnerHitlag(double currentTime, double duration);
}
