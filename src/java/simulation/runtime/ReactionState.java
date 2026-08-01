package simulation.runtime;

import java.util.ArrayList;
import java.util.List;

import model.type.CharacterId;

/**
 * Holds transient simulation state for Electro-Charged and Thundercloud handling.
 */
public class ReactionState {
    /** Immutable delayed Dendro Core payload stored across snapshot/restore. */
    public static final class DendroCoreState {
        /** Stable runtime core identifier. */
        public final int id;
        /** Character credited when this core deals damage. */
        public final CharacterId ownerId;
        /** Core creation time. */
        public final double creationTime;
        /** Scheduled natural explosion time. */
        public final double expiryTime;
        /** Reaction damage before resistance is evaluated at impact. */
        public final double preResistanceDamage;

        /**
         * Creates an immutable delayed Dendro Core payload.
         *
         * @param id stable runtime identifier
         * @param ownerId character credited for later damage
         * @param creationTime core creation time
         * @param expiryTime natural explosion time
         * @param preResistanceDamage damage before impact-time resistance
         */
        public DendroCoreState(
                int id,
                CharacterId ownerId,
                double creationTime,
                double expiryTime,
                double preResistanceDamage) {
            this.id = id;
            this.ownerId = ownerId;
            this.creationTime = creationTime;
            this.expiryTime = expiryTime;
            this.preResistanceDamage = preResistanceDamage;
        }
    }

    private boolean ecTimerRunning = false;
    private double thundercloudEndTime = -1.0;
    private boolean burningTimerRunning = false;
    private double burningEndTime = -1.0;
    private double quickenEndTime = -1.0;
    private int moondriftCount = 0;
    private int lunarCrystallizeTriggerCount = 0;
    private int verdantDewCount = 0;
    private int moonridgeDewCount = 0;
    private final List<DendroCoreState> dendroCores = new ArrayList<>();
    private int nextDendroCoreId = 1;

    /**
     * Returns whether an EC-related timer is active.
     *
     * @return {@code true} if active
     */
    public boolean isEcTimerRunning() {
        return ecTimerRunning;
    }

    /**
     * Sets whether an EC-related timer is active.
     *
     * @param running new active state
     */
    public void setEcTimerRunning(boolean running) {
        this.ecTimerRunning = running;
    }

    /**
     * Returns the Thundercloud expiry time.
     *
     * @return absolute simulation time in seconds
     */
    public double getThundercloudEndTime() {
        return thundercloudEndTime;
    }

    /**
     * Sets the Thundercloud expiry time.
     *
     * @param thundercloudEndTime absolute simulation time in seconds
     */
    public void setThundercloudEndTime(double thundercloudEndTime) {
        this.thundercloudEndTime = thundercloudEndTime;
    }

    public boolean isBurningTimerRunning() {
        return burningTimerRunning;
    }

    public void setBurningTimerRunning(boolean burningTimerRunning) {
        this.burningTimerRunning = burningTimerRunning;
    }

    public double getBurningEndTime() {
        return burningEndTime;
    }

    public void setBurningEndTime(double burningEndTime) {
        this.burningEndTime = burningEndTime;
    }

    public double getQuickenEndTime() {
        return quickenEndTime;
    }

    public void setQuickenEndTime(double quickenEndTime) {
        this.quickenEndTime = quickenEndTime;
    }

    public int getMoondriftCount() {
        return moondriftCount;
    }

    public void setMoondriftCount(int moondriftCount) {
        this.moondriftCount = moondriftCount;
    }

    public int getLunarCrystallizeTriggerCount() {
        return lunarCrystallizeTriggerCount;
    }

    public void setLunarCrystallizeTriggerCount(int lunarCrystallizeTriggerCount) {
        this.lunarCrystallizeTriggerCount = lunarCrystallizeTriggerCount;
    }

    public int incrementLunarCrystallizeTriggerCount() {
        lunarCrystallizeTriggerCount++;
        return lunarCrystallizeTriggerCount;
    }

    public int getVerdantDewCount() {
        return verdantDewCount;
    }

    public void setVerdantDewCount(int verdantDewCount) {
        this.verdantDewCount = verdantDewCount;
    }

    public int incrementVerdantDewCount() {
        verdantDewCount++;
        return verdantDewCount;
    }

    public int getMoonridgeDewCount() {
        return moonridgeDewCount;
    }

    public void setMoonridgeDewCount(int moonridgeDewCount) {
        this.moonridgeDewCount = moonridgeDewCount;
    }

    public int incrementMoonridgeDewCount() {
        moonridgeDewCount++;
        return moonridgeDewCount;
    }

    public DendroCoreState addDendroCore(
            CharacterId ownerId, double currentTime, double preResistanceDamage) {
        DendroCoreState core = new DendroCoreState(
                nextDendroCoreId++, ownerId, currentTime,
                currentTime + 6.0, preResistanceDamage);
        dendroCores.add(core);
        return core;
    }

    public List<DendroCoreState> getDendroCores() {
        return dendroCores;
    }

    public DendroCoreState removeOldestDendroCore() {
        if (dendroCores.isEmpty()) {
            return null;
        }
        return dendroCores.remove(0);
    }

    public boolean removeDendroCore(int coreId) {
        return dendroCores.removeIf(core -> core.id == coreId);
    }

    public void clearDendroCores() {
        dendroCores.clear();
    }

    public void restoreDendroCores(List<DendroCoreState> cores, int nextCoreId) {
        dendroCores.clear();
        dendroCores.addAll(cores);
        nextDendroCoreId = nextCoreId;
    }

    public int getNextDendroCoreId() {
        return nextDendroCoreId;
    }
}
