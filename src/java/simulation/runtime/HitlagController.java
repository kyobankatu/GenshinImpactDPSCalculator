package simulation.runtime;

import java.util.EnumMap;
import java.util.Map;

import model.type.CharacterId;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.HitlagProfile;

/**
 * Applies the simulator's pinned-gcsim hitlag timing policy.
 *
 * <p>The stationary target is assumed to have unbroken defense, matching
 * gcsim's current lack of enemy poise simulation. Defense-halt-capable hits
 * therefore add 3.6 frames before both frame-ceiling operations.
 */
public final class HitlagController {
    private static final double FRAMES_PER_SECOND = 60.0;
    private static final double DEFENSE_HALT_FRAMES = 3.6;

    private final CombatSimulator sim;
    private final Map<CharacterId, Double> idleOwnerFreezeEndTimes =
            new EnumMap<>(CharacterId.class);
    private CharacterId ownerActionId;
    private int pendingOwnerFreezeFrames;

    /** Creates a controller for one simulator instance. */
    public HitlagController(CombatSimulator sim) {
        this.sim = sim;
    }

    /**
     * Applies target-local hitlag after a landed action resolves.
     *
     * @param actorId character responsible for the landed hit
     * @param action resolved action
     */
    public void applyHitlag(CharacterId actorId, AttackAction action) {
        int freezeFrames = resolveTargetFreezeFrames(action);
        if (freezeFrames > 0 && sim.getEnemy() != null) {
            double duration = freezeFrames / FRAMES_PER_SECOND;
            sim.getEnemy().applyHitlag(sim.getCurrentTime(), duration);
            sim.applyTargetHitlagToRuntime(duration);
        }
        int ownerFreezeFrames = resolveOwnerFreezeFrames(actorId, action);
        if (ownerActionId == actorId) {
            pendingOwnerFreezeFrames += ownerFreezeFrames;
        } else if (ownerFreezeFrames > 0) {
            double currentTime = sim.getCurrentTime();
            double currentEnd = idleOwnerFreezeEndTimes.getOrDefault(
                    actorId, currentTime);
            idleOwnerFreezeEndTimes.put(
                    actorId,
                    Math.max(currentTime, currentEnd)
                            + ownerFreezeFrames / FRAMES_PER_SECOND);
        }
    }

    /**
     * Opens an owner-action scope if one is not already active.
     *
     * @param actorId top-level acting character
     * @return whether this call owns the newly opened scope
     */
    public boolean beginOwnerAction(CharacterId actorId) {
        if (ownerActionId != null) {
            return false;
        }
        drainIdleOwnerHitlag(actorId);
        ownerActionId = actorId;
        pendingOwnerFreezeFrames = 0;
        return true;
    }

    /**
     * Drains owner hitlag accumulated by every landed hit in the current scope.
     *
     * @param actorId top-level acting character
     * @return accumulated owner lock in seconds
     */
    public double drainOwnerHitlagDuration(CharacterId actorId) {
        if (ownerActionId != actorId || pendingOwnerFreezeFrames <= 0) {
            return 0.0;
        }
        int frames = pendingOwnerFreezeFrames;
        pendingOwnerFreezeFrames = 0;
        return frames / FRAMES_PER_SECOND;
    }

    /** Closes the current owner-action scope. */
    public void endOwnerAction(CharacterId actorId) {
        if (ownerActionId != actorId) {
            return;
        }
        ownerActionId = null;
        pendingOwnerFreezeFrames = 0;
    }

    /** Returns a snapshot copy of out-of-action owner freeze boundaries. */
    public Map<CharacterId, Double> copyIdleOwnerFreezeEndTimes() {
        return new EnumMap<>(idleOwnerFreezeEndTimes);
    }

    /** Restores out-of-action owner freeze boundaries after rollback. */
    public void restoreIdleOwnerFreezeEndTimes(
            Map<CharacterId, Double> freezeEndTimes) {
        ownerActionId = null;
        pendingOwnerFreezeFrames = 0;
        idleOwnerFreezeEndTimes.clear();
        if (freezeEndTimes == null) {
            return;
        }
        for (Map.Entry<CharacterId, Double> entry
                : freezeEndTimes.entrySet()) {
            CharacterId actorId = entry.getKey();
            Double endTime = entry.getValue();
            if (actorId != null && endTime != null
                    && Double.isFinite(endTime)) {
                idleOwnerFreezeEndTimes.put(actorId, endTime);
            }
        }
    }

    private void drainIdleOwnerHitlag(CharacterId actorId) {
        if (sim.getActiveCharacter() == null
                || sim.getActiveCharacter().getCharacterId() != actorId) {
            return;
        }
        double endTime = idleOwnerFreezeEndTimes.getOrDefault(
                actorId, sim.getCurrentTime());
        while (endTime > sim.getCurrentTime()) {
            sim.advanceTime(endTime - sim.getCurrentTime());
            endTime = idleOwnerFreezeEndTimes.getOrDefault(
                    actorId, sim.getCurrentTime());
        }
        idleOwnerFreezeEndTimes.remove(actorId);
    }

    /**
     * Returns owner freeze frames for a landed non-deployable hit.
     */
    private int resolveOwnerFreezeFrames(
            CharacterId actorId, AttackAction action) {
        HitlagProfile profile = action.getHitlagProfile();
        if (profile.isDeployable()
                || sim.getEnemy() == null
                || sim.getActiveCharacter() == null
                || sim.getActiveCharacter().getCharacterId() != actorId) {
            return 0;
        }
        return resolveOwnerFreezeFrames(profile);
    }

    /**
     * Converts source seconds to owner freeze frames without pre-rounding halt.
     */
    private int resolveOwnerFreezeFrames(HitlagProfile profile) {
        double haltFrames = resolveHaltFrames(profile);
        return (int) Math.ceil(haltFrames * (1.0 - profile.getFactor()));
    }

    /**
     * Converts source seconds to target freeze frames after pre-rounding halt.
     *
     * <p>The current target mode has no weak point, so headshot-only target
     * hitlag fails closed. gcsim still applies owner hitlag for the same attack.
     */
    private int resolveTargetFreezeFrames(AttackAction action) {
        HitlagProfile profile = action.getHitlagProfile();
        if (profile.isHeadshotOnly()) {
            return 0;
        }
        int roundedHaltFrames = (int) Math.ceil(resolveHaltFrames(profile));
        return (int) Math.ceil(
                roundedHaltFrames * (1.0 - profile.getFactor()));
    }

    private double resolveHaltFrames(HitlagProfile profile) {
        double haltFrames = profile.getHaltTimeSeconds() * FRAMES_PER_SECOND;
        return profile.canDefenseHalt()
                ? haltFrames + DEFENSE_HALT_FRAMES
                : haltFrames;
    }
}
