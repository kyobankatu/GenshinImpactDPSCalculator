package model.character;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

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
 * Mona's classic single-target offensive kit through C5.
 *
 * <p>This vertical slice follows pinned gcsim {@code ef41805d} timing and
 * KQM TCL {@code 80ba6241} data. It models catalyst attacks, Press Skill,
 * Bubble/Omen, A4, C2, C3, C4, and C5. Bubble is target-bound through the
 * simulator's stationary single-enemy abstraction, so the triggering direct
 * hit receives Omen before the post-damage listener pops it.</p>
 *
 * <p>A1, alternate sprint, Hold Skill selection, C1, C6, geometry, Freeze
 * extension, indirect reaction-only triggers, Hexerei, and RL integration are
 * intentionally excluded.</p>
 */
public class Mona extends Character implements
        CharacterTeamBuffProvider,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EVENT_EPSILON = 1e-9;
    private static final double SKILL_COOLDOWN = 12.0;
    private static final double BURST_COOLDOWN = 15.0;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double BUBBLE_DURATION = 8.0;
    private static final double OMEN_DURATION = 5.0;
    private static final double C2_COOLDOWN = 5.0;
    private static final double EXPECTED_SKILL_PARTICLES = 10.0 / 3.0;
    private static final int[] NORMAL_HITMARK_FRAMES = { 11, 14, 25, 27 };
    private static final int[] NORMAL_DURATION_FRAMES = { 18, 23, 39, 67 };
    private static final double[] NORMAL_MULTIPLIERS = {
            0.6392, 0.6120, 0.7616, 0.95472
    };
    private static final int[] SKILL_EVENT_FRAMES = {
            86, 145, 204, 263, 329
    };

    private final DoubleSupplier c2ProcDraw;
    private final Buff omenTeamBuff;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private PendingSkill pendingSkill;
    private List<Double> pendingParticleTimes = new ArrayList<>();
    private long burstGeneration;
    private PendingBurstApplication pendingBurstApplication;
    private boolean bubbleActive;
    private double bubbleExpirationTime = Double.NEGATIVE_INFINITY;
    private double omenExpirationTime = Double.NEGATIVE_INFINITY;
    private PendingBubbleExplosion pendingBubbleExplosion;
    private double nextC2TriggerTime = Double.NEGATIVE_INFINITY;
    private PendingC2Impact pendingC2Impact;
    private boolean resolvingBubbleExplosion;

    /** Constructs the repository-default C6 Mona. */
    public Mona(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Mona at an explicit constellation. */
    public Mona(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Mona with deterministic C2 probability injection.
     *
     * @param weapon equipped catalyst
     * @param artifacts equipped artifact set
     * @param constellation constellation in the inclusive range 0-6
     * @param c2ProcDraw draw source returning values in {@code [0, 1]}
     */
    public Mona(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation,
            DoubleSupplier c2ProcDraw) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, c2ProcDraw);
    }

    /**
     * Constructs Mona with injectable talent and probability sources.
     *
     * @param weapon equipped catalyst
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     * @param c2ProcDraw draw source returning values in {@code [0, 1]}
     */
    public Mona(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier c2ProcDraw) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Mona constellation must be between 0 and 6");
        }
        if (c2ProcDraw == null) {
            throw new IllegalArgumentException("Mona C2 draw is required");
        }
        this.name = "Mona";
        this.characterId = CharacterId.MONA;
        this.element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.c2ProcDraw = c2ProcDraw;

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10409.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 287.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 653.0));
        baseStats.add(StatType.ENERGY_RECHARGE,
                getTalentValue("Ascension Energy Recharge", 0.32));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);

        omenTeamBuff = new Buff("Mona Omen") {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                if (!isOmenAmplified(currentTime)) {
                    return;
                }
                stats.add(StatType.DMG_BONUS_ALL, omenDamageBonus());
                if (Mona.this.constellation >= 4) {
                    stats.add(StatType.CRIT_RATE, 0.15);
                }
            }
        }.sourcedBy(characterId);
    }

    /** Binds Mona's listeners and mutable event state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Mona simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Mona cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) -> {
            handleBubbleTrigger(actor, action, damage, time, simulator);
            handleC2Trigger(actor, action, damage, time, simulator);
        });
    }

    /** Captures all Mona-owned progression needed to reconstruct future hits. */
    @Override
    public State captureCharacterState() {
        return new MonaState(
                normalAttackStep,
                skillGeneration,
                pendingSkill,
                pendingParticleTimes,
                burstGeneration,
                pendingBurstApplication,
                bubbleActive,
                bubbleExpirationTime,
                omenExpirationTime,
                pendingBubbleExplosion,
                nextC2TriggerTime,
                pendingC2Impact);
    }

    /** Reports whether the payload was produced by Mona. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof MonaState;
    }

    /**
     * Restores Mona-owned state and reconstructs each next pending event once.
     *
     * @param state immutable Mona payload
     * @param simulator restored simulator receiving future events
     */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Mona character state");
        }
        initializeForSimulator(simulator);
        MonaState restored = (MonaState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        pendingSkill = restored.pendingSkill == null
                ? null : restored.pendingSkill.copy();
        pendingParticleTimes = new ArrayList<>(restored.pendingParticleTimes);
        burstGeneration = restored.burstGeneration;
        pendingBurstApplication = restored.pendingBurstApplication == null
                ? null : restored.pendingBurstApplication.copy();
        bubbleActive = restored.bubbleActive;
        bubbleExpirationTime = restored.bubbleExpirationTime;
        omenExpirationTime = restored.omenExpirationTime;
        pendingBubbleExplosion = restored.pendingBubbleExplosion == null
                ? null : restored.pendingBubbleExplosion.copy();
        nextC2TriggerTime = restored.nextC2TriggerTime;
        pendingC2Impact = restored.pendingC2Impact == null
                ? null : restored.pendingC2Impact.copy();

        double currentTime = simulator.getCurrentTime();
        normalizePendingState(currentTime);
        if (pendingSkill != null) {
            scheduleNextSkillEvent(simulator, pendingSkill);
        }
        for (double particleTime : pendingParticleTimes) {
            scheduleParticle(simulator, particleTime);
        }
        if (pendingBurstApplication != null) {
            scheduleBurstApplication(simulator, pendingBurstApplication);
        }
        if (bubbleActive) {
            scheduleForcedBubblePop(
                    simulator, burstGeneration, bubbleExpirationTime);
        }
        if (pendingBubbleExplosion != null) {
            scheduleBubbleExplosion(simulator, pendingBubbleExplosion);
        }
        if (pendingC2Impact != null) {
            scheduleC2Impact(simulator, pendingC2Impact);
        }
    }

    /** Returns Mona's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Dynamically converts 20% of effective Energy Recharge into Hydro DMG. */
    @Override
    public void applyPassive(StatsContainer stats) {
        stats.add(
                StatType.HYDRO_DMG_BONUS,
                stats.getTotalEnergyRecharge()
                        * getTalentValue("A4 ER Conversion", 0.20));
    }

    /** Exposes Bubble/Omen and C4 as one target-bound team projection. */
    @Override
    public List<Buff> getTeamBuffs() {
        return Collections.singletonList(omenTeamBuff);
    }

    /** Returns whether the modeled target currently carries Bubble. */
    public boolean isBubbleActive(double currentTime) {
        return bubbleActive
                && currentTime + EVENT_EPSILON < bubbleExpirationTime;
    }

    /** Returns whether Bubble or Omen amplifies hits at this time. */
    public boolean isOmenAmplified(double currentTime) {
        return isBubbleActive(currentTime)
                || currentTime + EVENT_EPSILON < omenExpirationTime;
    }

    /** Returns the current Omen expiration timestamp. */
    public double getOmenExpirationTime() {
        return omenExpirationTime;
    }

    /** Dispatches Mona's supported typed offensive actions. */
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
            case CHARGE:
                chargedAttack(simulator);
                break;
            case PLUNGE:
                highPlunge(simulator);
                break;
            case SKILL:
                mirrorReflectionOfDoom(simulator);
                break;
            case BURST:
                stellarisPhantasm(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Mona: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        AttackAction normal = attack(
                "Ripple of Fate N" + (step + 1),
                getTalentValue("N" + (step + 1),
                        NORMAL_MULTIPLIERS[step]),
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0);
        schedule(simulator,
                castTime + NORMAL_HITMARK_FRAMES[step] * FRAME,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, normal));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        simulator.advanceTime(NORMAL_DURATION_FRAMES[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        AttackAction charged = attack(
                "Ripple of Fate Charged Attack",
                getTalentValue("Charged Attack", 2.54524),
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.None,
                ICDTag.ChargedAttack,
                1.0);
        schedule(simulator, castTime + 66.0 * FRAME,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, charged));
        simulator.advanceTime(113.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        AttackAction plunge = attack(
                "Ripple of Fate High Plunge",
                getTalentValue("High Plunge", 2.6076),
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                1.0);
        // No pinned Mona plunge frame exists; use the 75-frame catalyst policy.
        plunge.setAnimationDuration(75.0 * FRAME);
        simulator.performAction(characterId, plunge);
    }

    private void mirrorReflectionOfDoom(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        StatsContainer snapshot = captureActionSnapshot(simulator, castTime);
        pendingSkill = new PendingSkill(
                generation, castTime, snapshot, 0, constellation >= 5);
        schedule(simulator, castTime + 24.0 * FRAME, activeSim -> {
            if (generation == skillGeneration) {
                markSkillUsed(activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this));
            }
        });
        scheduleNextSkillEvent(simulator, pendingSkill);
        simulator.advanceTime(50.0 * FRAME);
    }

    private void scheduleNextSkillEvent(
            CombatSimulator simulator,
            PendingSkill skill) {
        int eventIndex = skill.nextEventIndex;
        if (eventIndex >= SKILL_EVENT_FRAMES.length) {
            return;
        }
        double eventTime = skill.castTime
                + SKILL_EVENT_FRAMES[eventIndex] * FRAME;
        schedule(simulator, eventTime, activeSim -> {
            if (pendingSkill != skill
                    || skill.generation != skillGeneration) {
                return;
            }
            boolean explosion = eventIndex == SKILL_EVENT_FRAMES.length - 1;
            AttackAction action = attack(
                    explosion
                            ? "Mirror Reflection of Doom Explosion"
                            : "Mirror Reflection of Doom Tick "
                                    + (eventIndex + 1),
                    skillMultiplier(explosion, skill.c5),
                    StatType.SKILL_DMG_BONUS,
                    ActionType.SKILL,
                    explosion ? ICDType.None : ICDType.Standard,
                    explosion ? ICDTag.None : ICDTag.ElementalSkill,
                    1.0);
            action.setStatSnapshot(skill.snapshot);
            activeSim.performActionWithoutTimeAdvance(characterId, action);
            if (explosion) {
                pendingSkill = null;
                queueParticle(activeSim,
                        activeSim.getCurrentTime() + PARTICLE_TRAVEL);
            } else {
                pendingSkill = skill.withNextEvent(eventIndex + 1);
                scheduleNextSkillEvent(activeSim, pendingSkill);
            }
        });
    }

    private double skillMultiplier(boolean explosion, boolean c5) {
        if (explosion) {
            return getTalentValue(
                    c5 ? "Skill Explosion C5" : "Skill Explosion",
                    c5 ? 2.656 : 2.2576);
        }
        return getTalentValue(
                c5 ? "Skill DoT C5" : "Skill DoT",
                c5 ? 0.640 : 0.544);
    }

    private void stellarisPhantasm(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstUsed(castTime, simulator.getApplicableBuffs(this));
        long generation = ++burstGeneration;
        bubbleActive = false;
        bubbleExpirationTime = Double.NEGATIVE_INFINITY;
        omenExpirationTime = Double.NEGATIVE_INFINITY;
        pendingBubbleExplosion = null;
        pendingBurstApplication = new PendingBurstApplication(
                generation, castTime + 107.0 * FRAME);
        scheduleBurstApplication(simulator, pendingBurstApplication);
        simulator.advanceTime(127.0 * FRAME);
    }

    private void scheduleBurstApplication(
            CombatSimulator simulator,
            PendingBurstApplication application) {
        schedule(simulator, application.time, activeSim -> {
            if (pendingBurstApplication != application
                    || application.generation != burstGeneration) {
                return;
            }
            pendingBurstApplication = null;
            bubbleActive = true;
            bubbleExpirationTime = activeSim.getCurrentTime()
                    + BUBBLE_DURATION;
            AttackAction initial = attack(
                    "Stellaris Phantasm Illusory Bubble",
                    0.0,
                    StatType.BURST_DMG_BONUS,
                    ActionType.BURST,
                    ICDType.Standard,
                    ICDTag.ElementalBurst,
                    1.0);
            initial.setHitEffectTrigger(false);
            activeSim.performActionWithoutTimeAdvance(characterId, initial);
            scheduleForcedBubblePop(activeSim,
                    application.generation, bubbleExpirationTime);
        });
    }

    private void scheduleForcedBubblePop(
            CombatSimulator simulator,
            long generation,
            double popTime) {
        schedule(simulator, popTime, activeSim -> {
            if (generation == burstGeneration && bubbleActive) {
                popBubble(activeSim, activeSim.getCurrentTime());
            }
        });
    }

    private void handleBubbleTrigger(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator simulator) {
        if (resolvingBubbleExplosion
                || !isBubbleActive(time)
                || actor == null
                || simulator.getCharacter(actor.getCharacterId()) != actor
                || action == null
                || !isDirectTrigger(action)
                || !action.isHitEffectTrigger()
                || damage <= 0.0) {
            return;
        }
        popBubble(simulator, time);
    }

    private boolean isDirectTrigger(AttackAction action) {
        switch (action.getActionType()) {
            case NORMAL:
            case CHARGE:
            case PLUNGE:
            case SKILL:
            case BURST:
                return true;
            default:
                return false;
        }
    }

    private void popBubble(CombatSimulator simulator, double time) {
        if (!bubbleActive) {
            return;
        }
        bubbleActive = false;
        bubbleExpirationTime = Double.NEGATIVE_INFINITY;
        omenExpirationTime = time + OMEN_DURATION;
        pendingBubbleExplosion = new PendingBubbleExplosion(
                burstGeneration, time + FRAME, constellation >= 3);
        scheduleBubbleExplosion(simulator, pendingBubbleExplosion);
    }

    private void scheduleBubbleExplosion(
            CombatSimulator simulator,
            PendingBubbleExplosion explosion) {
        schedule(simulator, explosion.time, activeSim -> {
            if (pendingBubbleExplosion != explosion
                    || explosion.generation != burstGeneration) {
                return;
            }
            pendingBubbleExplosion = null;
            AttackAction action = attack(
                    "Stellaris Phantasm Illusory Bubble Explosion",
                    getTalentValue(
                            explosion.c3
                                    ? "Bubble Explosion C3"
                                    : "Bubble Explosion",
                            explosion.c3 ? 8.848 : 7.5208),
                    StatType.BURST_DMG_BONUS,
                    ActionType.BURST,
                    ICDType.Standard,
                    ICDTag.ElementalBurst,
                    2.0);
            resolvingBubbleExplosion = true;
            try {
                activeSim.performActionWithoutTimeAdvance(
                        characterId, action);
            } finally {
                resolvingBubbleExplosion = false;
            }
        });
    }

    private void handleC2Trigger(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator simulator) {
        if (constellation < 2
                || actor != this
                || simulator.getCharacter(characterId) != this
                || action == null
                || action.getActionType() != ActionType.NORMAL
                || !action.isHitEffectTrigger()
                || damage <= 0.0
                || time + EVENT_EPSILON < nextC2TriggerTime) {
            return;
        }
        double draw = c2ProcDraw.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw > 1.0) {
            throw new IllegalStateException(
                    "Mona C2 draw must be finite and within [0, 1]");
        }
        if (draw >= getTalentValue("C2 Proc Chance", 0.20)) {
            return;
        }
        nextC2TriggerTime = time
                + getTalentValue("C2 Cooldown", C2_COOLDOWN);
        pendingC2Impact = new PendingC2Impact(time + 53.0 * FRAME);
        scheduleC2Impact(simulator, pendingC2Impact);
    }

    private void scheduleC2Impact(
            CombatSimulator simulator,
            PendingC2Impact impact) {
        schedule(simulator, impact.time, activeSim -> {
            if (pendingC2Impact != impact) {
                return;
            }
            pendingC2Impact = null;
            AttackAction charged = attack(
                    "Come 'n' Get Me, Hag! Charged Attack",
                    getTalentValue("Charged Attack", 2.54524),
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    ActionType.CHARGE,
                    ICDType.None,
                    ICDTag.ChargedAttack,
                    1.0);
            activeSim.performActionWithoutTimeAdvance(characterId, charged);
        });
    }

    private void queueParticle(CombatSimulator simulator, double time) {
        pendingParticleTimes.add(time);
        scheduleParticle(simulator, time);
    }

    private void scheduleParticle(CombatSimulator simulator, double time) {
        schedule(simulator, time, activeSim -> {
            if (!pendingParticleTimes.remove(time)) {
                return;
            }
            activeSim.getEnergyDistributor().distributeParticles(
                    Element.HYDRO,
                    EXPECTED_SKILL_PARTICLES,
                    ParticleType.PARTICLE);
        });
    }

    private void normalizePendingState(double currentTime) {
        if (pendingSkill != null) {
            int next = pendingSkill.nextEventIndex;
            while (next < SKILL_EVENT_FRAMES.length
                    && pendingSkill.castTime
                            + SKILL_EVENT_FRAMES[next] * FRAME
                            < currentTime - EVENT_EPSILON) {
                next++;
            }
            pendingSkill = next >= SKILL_EVENT_FRAMES.length
                    ? null : pendingSkill.withNextEvent(next);
        }
        pendingParticleTimes.removeIf(time ->
                time < currentTime - EVENT_EPSILON);
        if (pendingBurstApplication != null
                && pendingBurstApplication.time
                        < currentTime - EVENT_EPSILON) {
            pendingBurstApplication = null;
        }
        if (bubbleActive
                && bubbleExpirationTime < currentTime - EVENT_EPSILON) {
            bubbleActive = false;
        }
        if (pendingBubbleExplosion != null
                && pendingBubbleExplosion.time
                        < currentTime - EVENT_EPSILON) {
            pendingBubbleExplosion = null;
        }
        if (pendingC2Impact != null
                && pendingC2Impact.time < currentTime - EVENT_EPSILON) {
            pendingC2Impact = null;
        }
    }

    private double omenDamageBonus() {
        return getTalentValue(
                constellation >= 3
                        ? "Omen DMG Bonus C3"
                        : "Omen DMG Bonus",
                constellation >= 3 ? 0.60 : 0.58);
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
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                Element.HYDRO,
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

    /** Immutable snapshot of Mona-owned combat progression. */
    private static final class MonaState implements State {
        private final int normalAttackStep;
        private final long skillGeneration;
        private final PendingSkill pendingSkill;
        private final List<Double> pendingParticleTimes;
        private final long burstGeneration;
        private final PendingBurstApplication pendingBurstApplication;
        private final boolean bubbleActive;
        private final double bubbleExpirationTime;
        private final double omenExpirationTime;
        private final PendingBubbleExplosion pendingBubbleExplosion;
        private final double nextC2TriggerTime;
        private final PendingC2Impact pendingC2Impact;

        private MonaState(
                int normalAttackStep,
                long skillGeneration,
                PendingSkill pendingSkill,
                List<Double> pendingParticleTimes,
                long burstGeneration,
                PendingBurstApplication pendingBurstApplication,
                boolean bubbleActive,
                double bubbleExpirationTime,
                double omenExpirationTime,
                PendingBubbleExplosion pendingBubbleExplosion,
                double nextC2TriggerTime,
                PendingC2Impact pendingC2Impact) {
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.pendingSkill = pendingSkill == null
                    ? null : pendingSkill.copy();
            this.pendingParticleTimes = new ArrayList<>(pendingParticleTimes);
            this.burstGeneration = burstGeneration;
            this.pendingBurstApplication = pendingBurstApplication == null
                    ? null : pendingBurstApplication.copy();
            this.bubbleActive = bubbleActive;
            this.bubbleExpirationTime = bubbleExpirationTime;
            this.omenExpirationTime = omenExpirationTime;
            this.pendingBubbleExplosion = pendingBubbleExplosion == null
                    ? null : pendingBubbleExplosion.copy();
            this.nextC2TriggerTime = nextC2TriggerTime;
            this.pendingC2Impact = pendingC2Impact == null
                    ? null : pendingC2Impact.copy();
        }
    }

    /** Immutable Press Skill cast and next-event progress. */
    private static final class PendingSkill {
        private final long generation;
        private final double castTime;
        private final StatsContainer snapshot;
        private final int nextEventIndex;
        private final boolean c5;

        private PendingSkill(
                long generation,
                double castTime,
                StatsContainer snapshot,
                int nextEventIndex,
                boolean c5) {
            this.generation = generation;
            this.castTime = castTime;
            this.snapshot = snapshot.merge(null);
            this.nextEventIndex = nextEventIndex;
            this.c5 = c5;
        }

        private PendingSkill withNextEvent(int nextIndex) {
            return new PendingSkill(
                    generation, castTime, snapshot, nextIndex, c5);
        }

        private PendingSkill copy() {
            return withNextEvent(nextEventIndex);
        }
    }

    /** Immutable pending Bubble application. */
    private static final class PendingBurstApplication {
        private final long generation;
        private final double time;

        private PendingBurstApplication(long generation, double time) {
            this.generation = generation;
            this.time = time;
        }

        private PendingBurstApplication copy() {
            return new PendingBurstApplication(generation, time);
        }
    }

    /** Immutable pending Bubble explosion. */
    private static final class PendingBubbleExplosion {
        private final long generation;
        private final double time;
        private final boolean c3;

        private PendingBubbleExplosion(
                long generation,
                double time,
                boolean c3) {
            this.generation = generation;
            this.time = time;
            this.c3 = c3;
        }

        private PendingBubbleExplosion copy() {
            return new PendingBubbleExplosion(generation, time, c3);
        }
    }

    /** Immutable pending C2 follow-up impact. */
    private static final class PendingC2Impact {
        private final double time;

        private PendingC2Impact(double time) {
            this.time = time;
        }

        private PendingC2Impact copy() {
            return new PendingC2Impact(time);
        }
    }
}
