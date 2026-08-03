package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.SwitchAwareCharacter;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Beidou's offensive kit for stationary single-target combat.
 *
 * <p>This bounded slice models Oceanborne N1-N5, zero-counter press
 * Tidecaller, Stormbreaker's initial hit, and one Lightning Discharge per
 * eligible trigger. Discharges retain Beidou's Burst-cast stats and use a
 * one-second trigger cooldown measured from the triggering direct hit.</p>
 *
 * <p>Hold and counter levels, defensive effects, A1/A4, C1/C4, Charged and
 * Plunging Attacks, multi-target C2 bounces, geometry, and hitlag are excluded.
 * C6 assumes the single stationary target remains within five metres.</p>
 */
public final class Beidou extends Character
        implements SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EVENT_EPSILON = 1e-9;
    private static final double SKILL_COOLDOWN = 7.5;
    private static final double BURST_COOLDOWN = 20.0;
    private static final double BURST_DURATION = 15.0;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double DISCHARGE_COOLDOWN = 1.0;
    private static final int SKILL_COOLDOWN_START_FRAME = 4;
    private static final int SKILL_HITMARK_FRAME = 23;
    private static final int SKILL_DURATION_FRAMES = 45;
    private static final int BURST_ENERGY_FRAME = 6;
    private static final int BURST_HITMARK_FRAME = 28;
    private static final int BURST_DURATION_FRAMES = 58;
    private static final int C6_START_FRAME = 30;
    private static final int C6_END_FRAME = 990;
    private static final double[] NORMAL_MULTIPLIERS = {
            1.30666, 1.30192, 1.62266, 1.58948, 2.06032
    };
    private static final int[] NORMAL_HITMARK_FRAMES = {
            // Pinned gcsim uses 45f for N3; pinned KQM lists 46f.
            23, 22, 45, 25, 43
    };
    private static final int[] NORMAL_DURATION_FRAMES = {
            31, 36, 54, 36, 96
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double stormbreakerExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextDischargeAllowedTime = Double.NEGATIVE_INFINITY;
    private StatsContainer stormbreakerSnapshot;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Beidou. */
    public Beidou(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Beidou at an explicit constellation. */
    public Beidou(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Beidou with injectable talent data.
     *
     * @param weapon equipped claymore
     * @param artifacts equipped artifact set
     * @param talentData static character-data source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Beidou(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Beidou constellation must be between 0 and 6");
        }
        name = "Beidou";
        characterId = CharacterId.BEIDOU;
        element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(
                StatType.BASE_HP,
                getTalentValue("Base HP", 13050.0));
        baseStats.set(
                StatType.BASE_ATK,
                getTalentValue("Base ATK", 225.0));
        baseStats.set(
                StatType.BASE_DEF,
                getTalentValue("Base DEF", 648.0));
        baseStats.add(
                StatType.ELECTRO_DMG_BONUS,
                getTalentValue("Ascension Electro DMG Bonus", 0.24));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds Stormbreaker's resolved-damage listener to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Beidou simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Beidou cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                handleStormbreakerTrigger(
                        simulator, actor, action, damage, time));
    }

    /** Returns Beidou's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Beidou has no unconditional offensive stat passive in this slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Represented passives are action- or target-state-specific.
    }

    /** Returns whether Stormbreaker's discharge state is active. */
    public boolean isStormbreakerActive(double currentTime) {
        return stormbreakerSnapshot != null
                && currentTime + EVENT_EPSILON
                        < stormbreakerExpirationTime;
    }

    /** Returns the earliest timestamp accepted by the discharge trigger gate. */
    public double getNextDischargeAllowedTime() {
        return nextDischargeAllowedTime;
    }

    /** Captures Beidou-owned progression and reconstructable future events. */
    @Override
    public State captureCharacterState() {
        return new BeidouState(
                normalAttackStep,
                stormbreakerExpirationTime,
                nextDischargeAllowedTime,
                stormbreakerSnapshot,
                pendingEvents);
    }

    /** Reports whether a snapshot payload belongs to Beidou. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof BeidouState;
    }

    /** Restores owner state and re-registers unconsumed future events. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Beidou character state");
        }
        initializeForSimulator(simulator);
        BeidouState restored = (BeidouState) state;
        normalAttackStep = restored.normalAttackStep;
        stormbreakerExpirationTime =
                restored.stormbreakerExpirationTime;
        nextDischargeAllowedTime = restored.nextDischargeAllowedTime;
        stormbreakerSnapshot = restored.stormbreakerSnapshot == null
                ? null
                : restored.stormbreakerSnapshot.merge(null);
        pendingEvents = copyEvents(restored.pendingEvents);
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EVENT_EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            schedulePendingEvent(simulator, event);
        }
    }

    /** Resets Oceanborne progression when Beidou leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Dispatches Beidou's supported typed actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        initializeForSimulator(simulator);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case SKILL:
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Beidou Hold Skill is outside this slice");
                }
                tidecaller(simulator);
                break;
            case BURST:
                stormbreaker(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Beidou: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        AttackAction normal = attack(
                "Oceanborne N" + (step + 1),
                getTalentValue(
                        "N" + (step + 1),
                        NORMAL_MULTIPLIERS[step]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0);
        schedule(
                simulator,
                castTime + NORMAL_HITMARK_FRAMES[step] * FRAME,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, normal));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        simulator.advanceTime(NORMAL_DURATION_FRAMES[step] * FRAME);
    }

    private void tidecaller(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueEvent(simulator, new PendingEvent(
                PendingEventKind.SKILL_COOLDOWN,
                castTime + SKILL_COOLDOWN_START_FRAME * FRAME,
                null,
                0.0));
        queueEvent(simulator, new PendingEvent(
                PendingEventKind.SKILL_HIT,
                castTime + SKILL_HITMARK_FRAME * FRAME,
                null,
                0.0));
        simulator.advanceTime(SKILL_DURATION_FRAMES * FRAME);
    }

    private void stormbreaker(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(
                castTime,
                simulator.getApplicableBuffs(this));
        stormbreakerSnapshot = captureActionSnapshot(
                simulator, castTime);
        stormbreakerExpirationTime = castTime + BURST_DURATION;
        nextDischargeAllowedTime = Double.NEGATIVE_INFINITY;
        queueEvent(simulator, new PendingEvent(
                PendingEventKind.BURST_ENERGY,
                castTime + BURST_ENERGY_FRAME * FRAME,
                null,
                0.0));
        queueEvent(simulator, new PendingEvent(
                PendingEventKind.BURST_INITIAL,
                castTime + BURST_HITMARK_FRAME * FRAME,
                null,
                0.0));
        if (constellation >= 6) {
            queueEvent(simulator, new PendingEvent(
                    PendingEventKind.C6_START,
                    castTime + C6_START_FRAME * FRAME,
                    null,
                    castTime + C6_END_FRAME * FRAME));
        }
        simulator.advanceTime(BURST_DURATION_FRAMES * FRAME);
    }

    private void handleStormbreakerTrigger(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (simulator.getActiveCharacter() != actor
                || !isStormbreakerActive(time)
                || action == null
                || !action.isHitEffectTrigger()
                || (action.getActionType() != ActionType.NORMAL
                        && action.getActionType() != ActionType.CHARGE
                        && action.getActionType() != ActionType.EXTRA)
                || time + EVENT_EPSILON
                        < nextDischargeAllowedTime) {
            return;
        }
        nextDischargeAllowedTime = time + DISCHARGE_COOLDOWN;
        queueEvent(simulator, new PendingEvent(
                PendingEventKind.DISCHARGE,
                time + FRAME,
                stormbreakerSnapshot,
                0.0));
    }

    private void queueEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        pendingEvents.add(event);
        schedulePendingEvent(simulator, event);
    }

    private void schedulePendingEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        schedule(simulator, event.time, activeSim -> {
            if (!pendingEvents.remove(event)) {
                return;
            }
            resolvePendingEvent(activeSim, event);
        });
    }

    private void resolvePendingEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.kind) {
            case SKILL_COOLDOWN:
                markSkillUsed(
                        simulator.getCurrentTime(),
                        simulator.getApplicableBuffs(this));
                break;
            case SKILL_HIT:
                resolveTidecaller(simulator);
                break;
            case PARTICLE:
                simulator.getEnergyDistributor().distributeParticles(
                        Element.ELECTRO,
                        getTalentValue("Skill Particles", 2.0),
                        ParticleType.PARTICLE);
                break;
            case BURST_ENERGY:
                spendBurstEnergy(simulator.getCurrentTime());
                break;
            case BURST_INITIAL:
                resolveStormbreakerInitial(simulator);
                break;
            case C6_START:
                applyC6ResistanceShred(simulator, event.endTime);
                break;
            case DISCHARGE:
                resolveDischarge(simulator, event.snapshot);
                break;
            default:
                throw new IllegalStateException(
                        "Unhandled Beidou event " + event.kind);
        }
    }

    private void resolveTidecaller(CombatSimulator simulator) {
        AttackAction skill = attack(
                "Tidecaller Press",
                getTalentValue(
                        constellation >= 3
                                ? "Tidecaller C3"
                                : "Tidecaller",
                        constellation >= 3 ? 2.432 : 2.0672),
                Element.ELECTRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                2.0);
        simulator.performActionWithoutTimeAdvance(characterId, skill);
        if (simulator.getEnemy() != null) {
            queueEvent(simulator, new PendingEvent(
                    PendingEventKind.PARTICLE,
                    simulator.getCurrentTime() + PARTICLE_TRAVEL,
                    null,
                    0.0));
        }
    }

    private void resolveStormbreakerInitial(CombatSimulator simulator) {
        AttackAction initial = attack(
                "Stormbreaker Initial",
                getTalentValue(
                        constellation >= 5
                                ? "Stormbreaker Initial C5"
                                : "Stormbreaker Initial",
                        constellation >= 5 ? 2.432 : 2.0672),
                Element.ELECTRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                4.0);
        simulator.performActionWithoutTimeAdvance(characterId, initial);
    }

    private void resolveDischarge(
            CombatSimulator simulator,
            StatsContainer snapshot) {
        AttackAction discharge = attack(
                "Stormbreaker Lightning Discharge",
                getTalentValue(
                        constellation >= 5
                                ? "Lightning Discharge C5"
                                : "Lightning Discharge",
                        constellation >= 5 ? 1.92 : 1.632),
                Element.ELECTRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0);
        discharge.setStatSnapshot(snapshot);
        simulator.performActionWithoutTimeAdvance(
                characterId, discharge);
    }

    private void applyC6ResistanceShred(
            CombatSimulator simulator,
            double endTime) {
        double currentTime = simulator.getCurrentTime();
        simulator.getTeamBuffList().removeIf(buff ->
                buff.getId() == BuffId.BEIDOU_C6_ELECTRO_RES_SHRED
                        && buff.getSourceCharacterId()
                                == CharacterId.BEIDOU);
        SimpleBuff shred = new SimpleBuff(
                "Beidou Bane of Evil",
                BuffId.BEIDOU_C6_ELECTRO_RES_SHRED,
                Math.max(0.0, endTime - currentTime),
                currentTime,
                stats -> stats.add(
                        StatType.ELECTRO_RES_SHRED, 0.15));
        shred.sourcedBy(characterId);
        simulator.applyTeamBuff(shred);
    }

    private StatsContainer captureActionSnapshot(
            CombatSimulator simulator,
            double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static AttackAction attack(
            String name,
            double multiplier,
            Element element,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        return action;
    }

    private static void schedule(
            CombatSimulator simulator,
            double time,
            Consumer<CombatSimulator> effect) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                effect.accept(activeSim);
            }
        });
    }

    private static List<PendingEvent> copyEvents(
            List<PendingEvent> events) {
        List<PendingEvent> copy = new ArrayList<>();
        for (PendingEvent event : events) {
            copy.add(event.copy());
        }
        return copy;
    }

    /** Kinds of future Beidou-owned work reconstructed after rollback. */
    private enum PendingEventKind {
        SKILL_COOLDOWN,
        SKILL_HIT,
        PARTICLE,
        BURST_ENERGY,
        BURST_INITIAL,
        C6_START,
        DISCHARGE
    }

    /** Immutable future event with an optional cast-stat payload. */
    private static final class PendingEvent {
        private final PendingEventKind kind;
        private final double time;
        private final StatsContainer snapshot;
        private final double endTime;

        private PendingEvent(
                PendingEventKind kind,
                double time,
                StatsContainer snapshot,
                double endTime) {
            this.kind = kind;
            this.time = time;
            this.snapshot = snapshot == null
                    ? null
                    : snapshot.merge(null);
            this.endTime = endTime;
        }

        private PendingEvent copy() {
            return new PendingEvent(kind, time, snapshot, endTime);
        }
    }

    /** Immutable snapshot of Beidou-owned combat state. */
    private static final class BeidouState implements State {
        private final int normalAttackStep;
        private final double stormbreakerExpirationTime;
        private final double nextDischargeAllowedTime;
        private final StatsContainer stormbreakerSnapshot;
        private final List<PendingEvent> pendingEvents;

        private BeidouState(
                int normalAttackStep,
                double stormbreakerExpirationTime,
                double nextDischargeAllowedTime,
                StatsContainer stormbreakerSnapshot,
                List<PendingEvent> pendingEvents) {
            this.normalAttackStep = normalAttackStep;
            this.stormbreakerExpirationTime =
                    stormbreakerExpirationTime;
            this.nextDischargeAllowedTime = nextDischargeAllowedTime;
            this.stormbreakerSnapshot = stormbreakerSnapshot == null
                    ? null
                    : stormbreakerSnapshot.merge(null);
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}
