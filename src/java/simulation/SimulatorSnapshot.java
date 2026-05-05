package simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mechanics.buff.Buff;
import model.type.CharacterId;
import model.type.Element;

/**
 * Immutable capture of all mutable {@link CombatSimulator} state at a point in time.
 *
 * <p>Produced by {@link CombatSimulator#saveSnapshot()} and consumed by
 * {@link CombatSimulator#restoreSnapshot(SimulatorSnapshot)}.  The snapshot stores
 * references to existing {@link Buff} objects (not copies); restoration resets their
 * timing fields in-place and rebuilds the buff lists to exactly the saved membership.
 *
 * <p>The event queue ({@link simulation.runtime.SimulationClock} timer events) is
 * intentionally excluded.  Pending events (e.g. Flins burst delayed hits) are
 * anonymous inner classes that capture local variables and cannot be serialised.
 * The resulting estimation bias approximately cancels in the VinePPO
 * {@code Q_MC - V_MC} advantage difference.
 */
public class SimulatorSnapshot {

    /** Holds per-character mutable state. */
    public static class CharacterSnapshot {
        public final double currentEnergy;
        public final double lastSkillTime;
        public final double lastBurstTime;
        public final List<Double> chargeRestoreTimes;
        /** Active buff references with their timing captured as [startTime, expirationTime]. */
        public final List<Buff> activeBuffRefs;
        public final List<double[]> activeBuffTimes;

        public CharacterSnapshot(
                double currentEnergy,
                double lastSkillTime,
                double lastBurstTime,
                List<Double> chargeRestoreTimes,
                List<Buff> activeBuffRefs,
                List<double[]> activeBuffTimes) {
            this.currentEnergy = currentEnergy;
            this.lastSkillTime = lastSkillTime;
            this.lastBurstTime = lastBurstTime;
            this.chargeRestoreTimes = new ArrayList<>(chargeRestoreTimes);
            this.activeBuffRefs = new ArrayList<>(activeBuffRefs);
            this.activeBuffTimes = new ArrayList<>(activeBuffTimes);
        }
    }

    public final double currentTime;
    public final double rotationTime;
    public final double totalDamage;
    public final Map<String, Double> damageBySource;
    public final double lastSwapTime;
    public final CharacterId activeCharacterId;
    public final CombatSimulator.Moonsign moonsign;
    public final Map<String, double[]> icdStates;
    public final boolean ecTimerRunning;
    public final double thundercloudEndTime;
    public final Map<Element, Double> enemyAura;

    /** Per-character snapshots keyed by CharacterId. */
    public final Map<CharacterId, CharacterSnapshot> characters;

    /** Simulator-managed team buff references and their timing at save time. */
    public final List<Buff> teamBuffRefs;
    public final List<double[]> teamBuffTimes;

    /** Simulator-managed field buff references and their timing at save time. */
    public final List<Buff> fieldBuffRefs;
    public final List<double[]> fieldBuffTimes;

    /**
     * Constructs a simulator snapshot.
     *
     * @param currentTime        simulation clock time
     * @param rotationTime       rotation time
     * @param totalDamage        accumulated total damage
     * @param damageBySource     per-source damage map
     * @param lastSwapTime       last swap time
     * @param activeCharacterId  currently active character id
     * @param moonsign           current moonsign state
     * @param icdStates          ICD group states
     * @param ecTimerRunning     EC timer flag
     * @param thundercloudEndTime thundercloud expiry time
     * @param enemyAura          enemy aura gauge map
     * @param characters         per-character snapshots
     * @param teamBuffRefs       team buff object references
     * @param teamBuffTimes      team buff timing pairs [startTime, expirationTime]
     * @param fieldBuffRefs      field buff object references
     * @param fieldBuffTimes     field buff timing pairs [startTime, expirationTime]
     */
    public SimulatorSnapshot(
            double currentTime,
            double rotationTime,
            double totalDamage,
            Map<String, Double> damageBySource,
            double lastSwapTime,
            CharacterId activeCharacterId,
            CombatSimulator.Moonsign moonsign,
            Map<String, double[]> icdStates,
            boolean ecTimerRunning,
            double thundercloudEndTime,
            Map<Element, Double> enemyAura,
            Map<CharacterId, CharacterSnapshot> characters,
            List<Buff> teamBuffRefs,
            List<double[]> teamBuffTimes,
            List<Buff> fieldBuffRefs,
            List<double[]> fieldBuffTimes) {
        this.currentTime = currentTime;
        this.rotationTime = rotationTime;
        this.totalDamage = totalDamage;
        this.damageBySource = new HashMap<>(damageBySource);
        this.lastSwapTime = lastSwapTime;
        this.activeCharacterId = activeCharacterId;
        this.moonsign = moonsign;
        this.icdStates = icdStates;
        this.ecTimerRunning = ecTimerRunning;
        this.thundercloudEndTime = thundercloudEndTime;
        this.enemyAura = new HashMap<>(enemyAura);
        this.characters = characters;
        this.teamBuffRefs = new ArrayList<>(teamBuffRefs);
        this.teamBuffTimes = new ArrayList<>(teamBuffTimes);
        this.fieldBuffRefs = new ArrayList<>(fieldBuffRefs);
        this.fieldBuffTimes = new ArrayList<>(fieldBuffTimes);
    }
}
