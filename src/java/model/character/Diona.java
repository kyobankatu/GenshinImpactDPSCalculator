package model.character;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.CharacterTeamBuffProvider;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.SnapshotAwareCharacterEffect;
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
import simulation.event.SimpleTimerEvent;

/**
 * Diona's offensive kit for stationary single-target combat.
 *
 * <p>The typed Skill request represents Hold Icy Paws: five independently
 * resolved paws and their deterministic expected particle total. Signature Mix
 * includes its initial hit, six snapshotted field ticks, C1 Energy, C4 aimed
 * shot timing, the always-full-HP branch of C6, and snapshot reconstruction
 * for the Normal chain and future Signature Mix effects.</p>
 *
 * <p>Tap Skill selection, shields, healing, stamina/movement, enemy ATK
 * reduction and projectile geometry are outside this slice.</p>
 */
public final class Diona extends Character
        implements CharacterTeamBuffProvider,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double SKILL_COOLDOWN = 15.0;
    private static final double BURST_COOLDOWN = 20.0;
    private static final double BURST_FIELD_DURATION = 12.5;
    private static final int BURST_START_FRAME = 58;
    private static final int BURST_TICK_INTERVAL_FRAMES = 120;
    private static final int BURST_TICK_COUNT = 6;
    private static final int PROJECTILE_TRAVEL_FRAMES = 10;

    private final Buff c6TeamBuff;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private PendingSignatureMix pendingSignatureMix;
    private PendingChargedImpact pendingChargedImpact;
    private List<Double> pendingParticleTimes = new ArrayList<>();

    /** Constructs repository-default C6 Diona. */
    public Diona(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Diona at an explicit constellation. */
    public Diona(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Diona with injectable talent data and constellation state. */
    public Diona(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Diona constellation must be between 0 and 6");
        }
        name = "Diona";
        characterId = CharacterId.DIONA;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9570.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 212.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 601.0));
        baseStats.add(StatType.CRYO_DMG_BONUS,
                getTalentValue("Ascension Cryo DMG", 0.24));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
        c6TeamBuff = new Buff("Diona C6 Cat's Tail Closing Time") {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                if (Diona.this.constellation >= 6
                        && isBurstFieldActive(currentTime)) {
                    stats.add(StatType.ELEMENTAL_MASTERY, 200.0);
                }
            }
        }.sourcedBy(characterId);
    }

    /** Binds Diona's mutable field state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (sim == null) {
            throw new IllegalArgumentException("Diona simulator is required");
        }
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Diona cannot be reused across CombatSimulator instances");
        }
        initializedSimulator = sim;
    }

    /** Captures Diona's Normal chain and reconstructible Burst progress. */
    @Override
    public State captureCharacterState() {
        return new DionaState(
                normalAttackStep,
                pendingSignatureMix,
                pendingChargedImpact,
                pendingParticleTimes);
    }

    /**
     * Restores Diona's branch-local state and recreates only future Burst work.
     *
     * <p>The simulator restores active buffs before this method, so the
     * {@link DionaBurstFieldMarker} remains authoritative for field activity.
     * Tick and refund deadlines at or before the restored clock are considered
     * consumed and are not scheduled again.</p>
     *
     * @param state immutable state captured by this Diona instance
     * @param simulator restored simulator receiving future Burst events
     */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!(state instanceof DionaState)) {
            throw new IllegalArgumentException(
                    "Unexpected Diona character state");
        }
        DionaState restored = (DionaState) state;
        normalAttackStep = restored.normalAttackStep;
        pendingSignatureMix = restored.pendingSignatureMix == null
                ? null
                : restored.pendingSignatureMix.copy();
        pendingChargedImpact = restored.pendingChargedImpact == null
                ? null
                : restored.pendingChargedImpact.copy();
        pendingParticleTimes = new ArrayList<>(
                restored.pendingParticleTimes);
        double currentTime = simulator.getCurrentTime();
        normalizeSignatureMixProgress(simulator.getCurrentTime());
        if (pendingSignatureMix != null) {
            scheduleSignatureMixFutureEffects(
                    simulator,
                    pendingSignatureMix);
        }
        if (pendingChargedImpact != null) {
            if (pendingChargedImpact.time <= currentTime) {
                pendingChargedImpact = null;
            } else {
                scheduleChargedImpact(simulator, pendingChargedImpact);
            }
        }
        pendingParticleTimes.removeIf(time -> time <= currentTime);
        for (Double time : new ArrayList<>(pendingParticleTimes)) {
            scheduleParticle(simulator, time);
        }
    }

    /** Returns Diona's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Diona has no unconditional offensive self passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // All represented bonuses are action- or field-specific.
    }

    /** Returns whether Signature Mix's field is active. */
    public boolean isBurstFieldActive(double currentTime) {
        for (Buff buff : getActiveBuffs()) {
            if (buff instanceof DionaBurstFieldMarker
                    && currentTime >= buff.getStartTime()
                    && currentTime < buff.getExpirationTime()) {
                return true;
            }
        }
        return false;
    }

    /** Returns C6's dynamic team EM provider at C6. */
    @Override
    public List<Buff> getTeamBuffs() {
        if (constellation < 6) {
            return Collections.emptyList();
        }
        return Collections.singletonList(c6TeamBuff);
    }

    /** Dispatches Diona's supported typed offensive actions. */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        initializeForSimulator(sim);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(sim);
                break;
            case CHARGE:
                fullyChargedAimedShot(sim);
                break;
            case PLUNGE:
                highPlunge(sim);
                break;
            case SKILL:
                holdIcyPaws(sim);
                break;
            case BURST:
                signatureMix(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Diona: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double[] multipliers = { 0.6636, 0.6162, 0.8374, 0.7900, 0.9875 };
        int[] hitmarkFrames = { 19, 11, 21, 10, 38 };
        int[] durationFrames = { 30, 21, 44, 21, 73 };
        double castTime = sim.getCurrentTime();
        int step = normalAttackStep;
        AttackAction normal = attack(
                "Katzlein Style N" + (step + 1),
                getTalentValue("N" + (step + 1), multipliers[step]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.None,
                0.0,
                0.0);
        double snapshotTime = castTime + hitmarkFrames[step] * FRAME;
        schedule(sim, snapshotTime, activeSim -> {
            normal.setStatSnapshot(captureActionSnapshot(
                    activeSim,
                    activeSim.getCurrentTime()));
            schedule(
                    activeSim,
                    activeSim.getCurrentTime()
                            + PROJECTILE_TRAVEL_FRAMES * FRAME,
                    impactSim -> impactSim.performActionWithoutTimeAdvance(
                            characterId, normal));
        });
        normalAttackStep = (normalAttackStep + 1) % multipliers.length;
        sim.advanceTime(durationFrames[step] * FRAME);
    }

    private void fullyChargedAimedShot(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        boolean c4Field = constellation >= 4
                && isBurstFieldActive(castTime);
        int durationFrames = c4Field ? 58 : 94;
        int hitmarkFrames = c4Field ? 50 : 86;
        schedule(sim, castTime + hitmarkFrames * FRAME, activeSim -> {
            pendingChargedImpact = new PendingChargedImpact(
                    activeSim.getCurrentTime()
                            + PROJECTILE_TRAVEL_FRAMES * FRAME,
                    getTalentValue("Fully Charged Aimed Shot", 2.1080),
                    captureActionSnapshot(
                            activeSim,
                            activeSim.getCurrentTime()));
            scheduleChargedImpact(activeSim, pendingChargedImpact);
        });
        sim.advanceTime(durationFrames * FRAME);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction plunge = attack(
                "Katzlein Style High Plunge",
                getTalentValue("Plunge High", 2.6086),
                Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.Standard,
                ICDTag.None,
                0.0,
                1.0);
        sim.performAction(characterId, plunge);
    }

    private void holdIcyPaws(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        StatsContainer skillSnapshot = captureActionSnapshot(sim, castTime);
        schedule(sim, castTime + 29.0 * FRAME, activeSim ->
                markSkillUsed(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this)));
        boolean c5 = constellation >= 5;
        for (int paw = 0; paw < 5; paw++) {
            AttackAction action = attack(
                    "Icy Paw " + (paw + 1),
                    getTalentValue(
                            c5 ? "Icy Paw C5" : "Icy Paw",
                            c5 ? 0.8384 : 0.71264),
                    Element.CRYO,
                    StatType.SKILL_DMG_BONUS,
                    ActionType.SKILL,
                    ICDType.Standard,
                    ICDTag.ElementalSkill,
                    1.0,
                    0.0);
            action.setStatSnapshot(skillSnapshot);
            if (constellation >= 2) {
                action.addBonusStat(StatType.SKILL_DMG_BONUS, 0.15);
            }
            int pawIndex = paw;
            double impactTime = castTime + (34.0 + pawIndex) * FRAME;
            schedule(sim, impactTime, activeSim -> {
                activeSim.performActionWithoutTimeAdvance(characterId, action);
                if (pawIndex == 0) {
                    queueParticle(
                            activeSim,
                            activeSim.getCurrentTime() + PARTICLE_TRAVEL);
                }
            });
        }
        sim.advanceTime(49.0 * FRAME);
    }

    private void signatureMix(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        captureSnapshot(castTime, sim.getApplicableBuffs(this));
        StatsContainer burstSnapshot = getSnapshot().merge(null);
        schedule(sim, castTime + 41.0 * FRAME, activeSim ->
                markBurstCooldownUsed(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this)));
        schedule(sim, castTime + 43.0 * FRAME, activeSim ->
                spendBurstEnergy(activeSim.getCurrentTime()));
        double burstFieldStart = castTime + BURST_START_FRAME * FRAME;
        double burstFieldEnd = burstFieldStart + BURST_FIELD_DURATION;
        getActiveBuffs().removeIf(
                buff -> buff instanceof DionaBurstFieldMarker);
        addBuff(new DionaBurstFieldMarker(
                burstFieldStart,
                BURST_FIELD_DURATION));

        boolean c3 = constellation >= 3;
        AttackAction initial = attack(
                "Signature Mix Initial",
                getTalentValue(
                        c3 ? "Signature Mix Initial C3"
                                : "Signature Mix Initial",
                        c3 ? 1.6000 : 1.3600),
                Element.CRYO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0,
                0.0);
        initial.setStatSnapshot(burstSnapshot);
        schedule(sim, burstFieldStart,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, initial));
        pendingSignatureMix = new PendingSignatureMix(
                burstSnapshot,
                c3,
                castTime,
                burstFieldStart,
                burstFieldEnd,
                0,
                constellation >= 1);
        scheduleSignatureMixFutureEffects(sim, pendingSignatureMix);
        sim.advanceTime(64.0 * FRAME);
    }

    private void scheduleSignatureMixFutureEffects(
            CombatSimulator sim,
            PendingSignatureMix signatureMix) {
        double currentTime = sim.getCurrentTime();
        for (int tick = signatureMix.completedTicks + 1;
                tick <= BURST_TICK_COUNT;
                tick++) {
            double tickTime = signatureMix.getTickTime(tick);
            if (tickTime > currentTime) {
                scheduleSignatureMixTick(sim, signatureMix, tick, tickTime);
            }
        }
        if (signatureMix.refundPending
                && signatureMix.fieldEndTime > currentTime) {
            schedule(sim, signatureMix.fieldEndTime, activeSim -> {
                receiveFlatEnergy(15.0);
                completeSignatureMixRefund(signatureMix);
            });
        }
    }

    private void scheduleSignatureMixTick(
            CombatSimulator sim,
            PendingSignatureMix signatureMix,
            int tick,
            double tickTime) {
        AttackAction dot = attack(
                "Signature Mix Tick " + tick,
                getTalentValue(
                        signatureMix.c3
                                ? "Signature Mix Tick C3"
                                : "Signature Mix Tick",
                        signatureMix.c3 ? 1.0528 : 0.89488),
                Element.CRYO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0,
                0.0);
        dot.setStatSnapshot(signatureMix.castSnapshot);
        schedule(sim, tickTime, activeSim -> {
            activeSim.performActionWithoutTimeAdvance(characterId, dot);
            completeSignatureMixTick(signatureMix, tick);
        });
    }

    private void completeSignatureMixTick(
            PendingSignatureMix signatureMix,
            int tick) {
        if (!isCurrentSignatureMix(signatureMix)) {
            return;
        }
        pendingSignatureMix = pendingSignatureMix.withProgress(
                Math.max(pendingSignatureMix.completedTicks, tick),
                pendingSignatureMix.refundPending);
        clearCompletedSignatureMix();
    }

    private void completeSignatureMixRefund(
            PendingSignatureMix signatureMix) {
        if (!isCurrentSignatureMix(signatureMix)) {
            return;
        }
        pendingSignatureMix = pendingSignatureMix.withProgress(
                pendingSignatureMix.completedTicks,
                false);
        clearCompletedSignatureMix();
    }

    private void normalizeSignatureMixProgress(double currentTime) {
        if (pendingSignatureMix == null) {
            return;
        }
        int completedTicks = pendingSignatureMix.completedTicks;
        for (int tick = completedTicks + 1;
                tick <= BURST_TICK_COUNT;
                tick++) {
            if (pendingSignatureMix.getTickTime(tick) <= currentTime) {
                completedTicks = tick;
            }
        }
        boolean refundPending = pendingSignatureMix.refundPending
                && pendingSignatureMix.fieldEndTime > currentTime;
        pendingSignatureMix = pendingSignatureMix.withProgress(
                completedTicks,
                refundPending);
        clearCompletedSignatureMix();
    }

    private boolean isCurrentSignatureMix(
            PendingSignatureMix signatureMix) {
        return pendingSignatureMix != null
                && Double.compare(
                        pendingSignatureMix.castTime,
                        signatureMix.castTime) == 0;
    }

    private void clearCompletedSignatureMix() {
        if (pendingSignatureMix != null
                && pendingSignatureMix.completedTicks >= BURST_TICK_COUNT
                && !pendingSignatureMix.refundPending) {
            pendingSignatureMix = null;
        }
    }

    private StatsContainer captureActionSnapshot(
            CombatSimulator sim,
            double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : sim.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private void scheduleChargedImpact(
            CombatSimulator sim,
            PendingChargedImpact payload) {
        schedule(sim, payload.time, activeSim -> {
            if (pendingChargedImpact != payload) {
                return;
            }
            pendingChargedImpact = null;
            AttackAction charged = attack(
                    "Katzlein Style Fully Charged Aimed Shot",
                    payload.multiplier,
                    Element.CRYO,
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    ActionType.CHARGE,
                    ICDType.Standard,
                    ICDTag.ChargedAttack,
                    1.0,
                    0.0);
            charged.setStatSnapshot(payload.snapshot);
            activeSim.performActionWithoutTimeAdvance(
                    characterId, charged);
        });
    }

    private void queueParticle(CombatSimulator sim, double time) {
        pendingParticleTimes.add(time);
        scheduleParticle(sim, time);
    }

    private void scheduleParticle(CombatSimulator sim, double time) {
        schedule(sim, time, activeSim -> {
            if (!pendingParticleTimes.remove(time)) {
                return;
            }
            activeSim.getEnergyDistributor().distributeParticles(
                    Element.CRYO,
                    getTalentValue("Hold Skill Particles", 4.0),
                    ParticleType.PARTICLE);
        });
    }

    private static AttackAction attack(
            String name,
            double multiplier,
            Element element,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            double duration) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                duration,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        return action;
    }

    private static void schedule(
            CombatSimulator sim,
            double time,
            Consumer<CombatSimulator> effect) {
        sim.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                effect.accept(activeSim);
            }
        });
    }

    /** Immutable snapshot payload for Diona-owned mutable state. */
    private static final class DionaState implements State {
        private final int normalAttackStep;
        private final PendingSignatureMix pendingSignatureMix;
        private final PendingChargedImpact pendingChargedImpact;
        private final List<Double> pendingParticleTimes;

        private DionaState(
                int normalAttackStep,
                PendingSignatureMix pendingSignatureMix,
                PendingChargedImpact pendingChargedImpact,
                List<Double> pendingParticleTimes) {
            this.normalAttackStep = normalAttackStep;
            this.pendingSignatureMix = pendingSignatureMix == null
                    ? null
                    : pendingSignatureMix.copy();
            this.pendingChargedImpact = pendingChargedImpact == null
                    ? null
                    : pendingChargedImpact.copy();
            this.pendingParticleTimes = new ArrayList<>(
                    pendingParticleTimes);
        }
    }

    /** Immutable payload for an aimed shot released before its impact. */
    private static final class PendingChargedImpact {
        private final double time;
        private final double multiplier;
        private final StatsContainer snapshot;

        private PendingChargedImpact(
                double time,
                double multiplier,
                StatsContainer snapshot) {
            this.time = time;
            this.multiplier = multiplier;
            this.snapshot = snapshot.merge(null);
        }

        private PendingChargedImpact copy() {
            return new PendingChargedImpact(time, multiplier, snapshot);
        }
    }

    /** Immutable Signature Mix cast data and future-event progress. */
    private static final class PendingSignatureMix {
        private final StatsContainer castSnapshot;
        private final boolean c3;
        private final double castTime;
        private final double fieldStartTime;
        private final double fieldEndTime;
        private final int completedTicks;
        private final boolean refundPending;

        private PendingSignatureMix(
                StatsContainer castSnapshot,
                boolean c3,
                double castTime,
                double fieldStartTime,
                double fieldEndTime,
                int completedTicks,
                boolean refundPending) {
            this.castSnapshot = castSnapshot.merge(null);
            this.c3 = c3;
            this.castTime = castTime;
            this.fieldStartTime = fieldStartTime;
            this.fieldEndTime = fieldEndTime;
            this.completedTicks = completedTicks;
            this.refundPending = refundPending;
        }

        private double getTickTime(int tick) {
            return fieldStartTime
                    + tick * BURST_TICK_INTERVAL_FRAMES * FRAME;
        }

        private PendingSignatureMix withProgress(
                int newCompletedTicks,
                boolean newRefundPending) {
            return new PendingSignatureMix(
                    castSnapshot,
                    c3,
                    castTime,
                    fieldStartTime,
                    fieldEndTime,
                    newCompletedTicks,
                    newRefundPending);
        }

        private PendingSignatureMix copy() {
            return withProgress(completedTicks, refundPending);
        }
    }

    /** Snapshot-restorable marker for Signature Mix's field window. */
    private static final class DionaBurstFieldMarker extends Buff {
        private DionaBurstFieldMarker(
                double startTime,
                double duration) {
            super("Diona Signature Mix Field Marker", duration, startTime);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            // Team effects are projected by CharacterTeamBuffProvider.
        }
    }
}
