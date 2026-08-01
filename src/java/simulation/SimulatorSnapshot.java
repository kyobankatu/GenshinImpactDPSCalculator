package simulation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mechanics.buff.Buff;
import model.entity.Enemy;
import model.type.CharacterId;
import model.type.Element;
import simulation.runtime.ReactionState;

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
        public final double skillCooldownEndTime;
        public final double burstCooldownEndTime;
        public final double activeChargeCooldownDuration;
        public final List<Double> chargeRestoreTimes;
        /** Active buff references with their timing captured as [startTime, expirationTime]. */
        public final List<Buff> activeBuffRefs;
        public final List<double[]> activeBuffTimes;

        public CharacterSnapshot(
                double currentEnergy,
                double lastSkillTime,
                double lastBurstTime,
                double skillCooldownEndTime,
                double burstCooldownEndTime,
                double activeChargeCooldownDuration,
                List<Double> chargeRestoreTimes,
                List<Buff> activeBuffRefs,
                List<double[]> activeBuffTimes) {
            this.currentEnergy = currentEnergy;
            this.lastSkillTime = lastSkillTime;
            this.lastBurstTime = lastBurstTime;
            this.skillCooldownEndTime = skillCooldownEndTime;
            this.burstCooldownEndTime = burstCooldownEndTime;
            this.activeChargeCooldownDuration = activeChargeCooldownDuration;
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
    public final ReactionState.StandardElectroChargedState
            standardElectroChargedState;
    public final double standardElectroChargedDamageCooldownEndTime;
    public final double standardElectroChargedLastDamageTime;
    public final double thundercloudEndTime;
    public final boolean burningTimerRunning;
    public final double burningEndTime;
    public final ReactionState.BurningState burningState;
    public final int nextBurningGeneration;
    public final double quickenEndTime;
    public final ReactionState.QuickenState quickenState;
    public final double overloadTargetDamageCooldownEndTime;
    public final Map<CharacterId, Double> overloadOwnerDamageCooldownEndTimes;
    public final double superconductTargetDamageCooldownEndTime;
    public final Map<CharacterId, ReactionState.FixedDamageSequenceState>
            superconductOwnerDamageSequenceStates;
    public final double shatterTargetDamageCooldownEndTime;
    public final Map<CharacterId, ReactionState.FixedDamageSequenceState>
            shatterOwnerDamageSequenceStates;
    public final double standardCrystallizeCooldownEndTime;
    public final Map<Element, Double> swirlTargetDamageCooldownEndTimes;
    public final Map<Element, Map<CharacterId, ReactionState.FixedDamageSequenceState>>
            swirlOwnerDamageSequenceStates;
    public final int moondriftCount;
    public final int lunarCrystallizeTriggerCount;
    public final int verdantDewCount;
    public final int moonridgeDewCount;
    public final List<ReactionState.DendroCoreState> dendroCores;
    public final List<Double> recentDendroCoreDamageTimes;
    public final int nextDendroCoreId;
    /** Complete accelerating Frozen Aura payload. */
    public final Enemy.FreezeAuraState enemyFreezeAura;
    /**
     * Full enemy aura state keyed by element, each value holding
     * {@code {units, applicationTime, duration}} so continuous natural decay is
     * preserved across snapshot restore.
     */
    public final Map<Element, double[]> enemyAura;

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
     * @param standardElectroChargedState latest standard EC tick payload
     * @param standardElectroChargedDamageCooldownEndTime standard EC target cooldown end
     * @param standardElectroChargedLastDamageTime last successful standard EC damage time
     * @param thundercloudEndTime thundercloud expiry time
     * @param burningTimerRunning Burning timer flag
     * @param burningEndTime compatibility Burning expiry time
     * @param burningState typed Burning fuel and damage payload
     * @param nextBurningGeneration next Burning event generation
     * @param quickenEndTime Quicken expiry time
     * @param quickenState typed consumable Quicken Aura payload
     * @param overloadTargetDamageCooldownEndTime target-wide Overload damage cooldown end
     * @param overloadOwnerDamageCooldownEndTimes owner-specific Overload damage cooldown ends
     * @param superconductTargetDamageCooldownEndTime target-wide Superconduct damage cooldown end
     * @param superconductOwnerDamageSequenceStates owner-specific Superconduct sequence states
     * @param shatterTargetDamageCooldownEndTime target-wide Shatter damage cooldown end
     * @param shatterOwnerDamageSequenceStates owner-specific Shatter sequence states
     * @param standardCrystallizeCooldownEndTime standard Crystallize cooldown end
     * @param swirlTargetDamageCooldownEndTimes per-element Swirl target cooldown ends
     * @param swirlOwnerDamageSequenceStates per-element and owner Swirl sequence states
     * @param moondriftCount active Moondrift count
     * @param lunarCrystallizeTriggerCount Lunar-Crystallize trigger count
     * @param verdantDewCount Verdant Dew count
     * @param moonridgeDewCount Moonridge Dew count
     * @param dendroCores active Dendro Core payloads
     * @param recentDendroCoreDamageTimes active target damage-cap timestamps
     * @param nextDendroCoreId next Dendro Core identifier
     * @param enemyFreezeAura complete Frozen Aura gauge and decay payload
     * @param enemyAura          enemy aura state map ({units, applicationTime, duration} per element)
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
            ReactionState.StandardElectroChargedState standardElectroChargedState,
            double standardElectroChargedDamageCooldownEndTime,
            double standardElectroChargedLastDamageTime,
            double thundercloudEndTime,
            boolean burningTimerRunning,
            double burningEndTime,
            ReactionState.BurningState burningState,
            int nextBurningGeneration,
            double quickenEndTime,
            ReactionState.QuickenState quickenState,
            double overloadTargetDamageCooldownEndTime,
            Map<CharacterId, Double> overloadOwnerDamageCooldownEndTimes,
            double superconductTargetDamageCooldownEndTime,
            Map<CharacterId, ReactionState.FixedDamageSequenceState>
                    superconductOwnerDamageSequenceStates,
            double shatterTargetDamageCooldownEndTime,
            Map<CharacterId, ReactionState.FixedDamageSequenceState>
                    shatterOwnerDamageSequenceStates,
            double standardCrystallizeCooldownEndTime,
            Map<Element, Double> swirlTargetDamageCooldownEndTimes,
            Map<Element, Map<CharacterId, ReactionState.FixedDamageSequenceState>>
                    swirlOwnerDamageSequenceStates,
            int moondriftCount,
            int lunarCrystallizeTriggerCount,
            int verdantDewCount,
            int moonridgeDewCount,
            List<ReactionState.DendroCoreState> dendroCores,
            List<Double> recentDendroCoreDamageTimes,
            int nextDendroCoreId,
            Enemy.FreezeAuraState enemyFreezeAura,
            Map<Element, double[]> enemyAura,
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
        this.standardElectroChargedState = standardElectroChargedState;
        this.standardElectroChargedDamageCooldownEndTime =
                standardElectroChargedDamageCooldownEndTime;
        this.standardElectroChargedLastDamageTime =
                standardElectroChargedLastDamageTime;
        this.thundercloudEndTime = thundercloudEndTime;
        this.burningTimerRunning = burningTimerRunning;
        this.burningEndTime = burningEndTime;
        this.burningState = burningState;
        this.nextBurningGeneration = nextBurningGeneration;
        this.quickenEndTime = quickenEndTime;
        this.quickenState = quickenState;
        this.overloadTargetDamageCooldownEndTime =
                overloadTargetDamageCooldownEndTime;
        this.overloadOwnerDamageCooldownEndTimes =
                new EnumMap<>(CharacterId.class);
        this.overloadOwnerDamageCooldownEndTimes.putAll(
                overloadOwnerDamageCooldownEndTimes);
        this.superconductTargetDamageCooldownEndTime =
                superconductTargetDamageCooldownEndTime;
        this.superconductOwnerDamageSequenceStates =
                new EnumMap<>(CharacterId.class);
        this.superconductOwnerDamageSequenceStates.putAll(
                superconductOwnerDamageSequenceStates);
        this.shatterTargetDamageCooldownEndTime =
                shatterTargetDamageCooldownEndTime;
        this.shatterOwnerDamageSequenceStates =
                new EnumMap<>(CharacterId.class);
        this.shatterOwnerDamageSequenceStates.putAll(
                shatterOwnerDamageSequenceStates);
        this.standardCrystallizeCooldownEndTime =
                standardCrystallizeCooldownEndTime;
        this.swirlTargetDamageCooldownEndTimes =
                new EnumMap<>(Element.class);
        this.swirlTargetDamageCooldownEndTimes.putAll(
                swirlTargetDamageCooldownEndTimes);
        this.swirlOwnerDamageSequenceStates = new EnumMap<>(Element.class);
        for (Map.Entry<Element, Map<CharacterId,
                ReactionState.FixedDamageSequenceState>> entry
                : swirlOwnerDamageSequenceStates.entrySet()) {
            this.swirlOwnerDamageSequenceStates.put(
                    entry.getKey(), new EnumMap<>(entry.getValue()));
        }
        this.moondriftCount = moondriftCount;
        this.lunarCrystallizeTriggerCount = lunarCrystallizeTriggerCount;
        this.verdantDewCount = verdantDewCount;
        this.moonridgeDewCount = moonridgeDewCount;
        this.dendroCores = new ArrayList<>(dendroCores);
        this.recentDendroCoreDamageTimes =
                new ArrayList<>(recentDendroCoreDamageTimes);
        this.nextDendroCoreId = nextDendroCoreId;
        this.enemyFreezeAura = enemyFreezeAura;
        this.enemyAura = new HashMap<>(enemyAura);
        this.characters = characters;
        this.teamBuffRefs = new ArrayList<>(teamBuffRefs);
        this.teamBuffTimes = new ArrayList<>(teamBuffTimes);
        this.fieldBuffRefs = new ArrayList<>(fieldBuffRefs);
        this.fieldBuffTimes = new ArrayList<>(fieldBuffTimes);
    }
}
