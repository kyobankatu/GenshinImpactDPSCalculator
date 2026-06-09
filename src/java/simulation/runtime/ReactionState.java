package simulation.runtime;

import java.util.ArrayList;
import java.util.List;

import model.type.CharacterId;

/**
 * Holds transient simulation state for Electro-Charged and Thundercloud handling.
 */
public class ReactionState {
    public static final class DendroCoreState {
        public final int id;
        public final CharacterId ownerId;
        public final double creationTime;
        public final double expiryTime;
        public final double damage;

        public DendroCoreState(int id, CharacterId ownerId, double creationTime, double expiryTime, double damage) {
            this.id = id;
            this.ownerId = ownerId;
            this.creationTime = creationTime;
            this.expiryTime = expiryTime;
            this.damage = damage;
        }
    }

    private boolean ecTimerRunning = false;
    private double thundercloudEndTime = -1.0;
    private boolean burningTimerRunning = false;
    private double burningEndTime = -1.0;
    private double quickenEndTime = -1.0;
    private int moondriftCount = 0;
    private int lunarCrystallizeTriggerCount = 0;
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

    public DendroCoreState addDendroCore(CharacterId ownerId, double currentTime, double damage) {
        DendroCoreState core = new DendroCoreState(nextDendroCoreId++, ownerId, currentTime, currentTime + 6.0, damage);
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
