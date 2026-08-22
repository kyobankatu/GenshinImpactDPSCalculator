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
import model.entity.TargetDependentTeamEffect;
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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Faruzan's stationary single-target Anemo support slice through C6.
 *
 * <p>Stats, Talent 9/12 values, bow release/impact frames, field refreshes,
 * and support gates follow pinned gcsim {@code ef41805d} and KQM TCL
 * {@code 80ba6241}. Manifest Gale turns fully charged shots into shortened
 * Hurricane Arrows whose Collapse snapshots at arrow impact. Prayerful and
 * Perfidious Wind use fixed-target field refresh schedules.</p>
 *
 * <p>Physical aimed shots, Plunge timing, weak points, suction, Burst triangle
 * movement, geometry, multi-target C4 scaling, and co-op C6 gates are
 * intentionally excluded.</p>
 */
public final class Faruzan extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double SUPPORT_WINDOW = 240.0 * FRAME;
    private static final double MANIFEST_DURATION = 1080.0 * FRAME;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double PARTICLE_GATE = 330.0 * FRAME;
    private static final double A4_GATE = 48.0 * FRAME;
    private static final double C6_GATE = 180.0 * FRAME;
    private static final int[] NORMAL_RELEASE_FRAMES = { 14, 10, 24, 29 };
    private static final int[] NORMAL_DURATION_FRAMES = { 26, 21, 39, 86 };
    private static final String[] NORMAL_KEYS = { "N1", "N2", "N3", "N4" };
    private static final double[] NORMAL_MULTIPLIERS = {
        0.821774, 0.775053, 0.976724, 1.297449
    };
    private static final int[] PRAYER_FRAMES_C0 = { 43, 282, 521, 760, 788 };
    private static final int[] SHRED_FRAMES_C0 = {
        180, 300, 420, 540, 660, 780
    };
    private static final int[] PRAYER_FRAMES_C2 = {
        43, 282, 521, 760, 788, 999, 1148
    };
    private static final int[] SHRED_FRAMES_C2 = {
        180, 300, 420, 540, 660, 780, 900, 1020, 1140
    };

    /**
     * Aimed-shot hitlag from gcsim config YAML pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile AIMED_HEADSHOT_HITLAG =
            new HitlagProfile(0.12, 0.01, false, true, true);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int manifestCharges;
    private double manifestExpirationTime = Double.NEGATIVE_INFINITY;
    private double prayerExpirationTime = Double.NEGATIVE_INFINITY;
    private double perfidiousExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private double nextA4Time = Double.NEGATIVE_INFINITY;
    private double nextC6Time = Double.NEGATIVE_INFINITY;
    private long skillGeneration;
    private long burstGeneration;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Faruzan. */
    public Faruzan(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Faruzan at an explicit constellation. */
    public Faruzan(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Faruzan with injectable talent data and constellation. */
    public Faruzan(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Faruzan constellation must be between 0 and 6");
        }
        name = "Faruzan";
        characterId = CharacterId.FARUZAN;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9570.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 196.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 628.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK Percent", 0.24));
        setSkillCD(6.0);
        setBurstCD(20.0);
    }

    /** Binds shared A4 and C6 damage gates exactly once. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Faruzan simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Faruzan cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                onResolvedDamage(actor, action, damage, time));
        simulator.addIndirectDamageListener((owner, damage, time) ->
                triggerC6Collapse(owner, damage, time));
    }

    /** Captures charge counts, support gates, generations, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new FaruzanState(
                this,
                normalAttackStep,
                manifestCharges,
                manifestExpirationTime,
                prayerExpirationTime,
                perfidiousExpirationTime,
                nextParticleTime,
                nextA4Time,
                nextC6Time,
                skillGeneration,
                burstGeneration,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Faruzan instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof FaruzanState
                && ((FaruzanState) state).owner == this;
    }

    /** Restores surviving delayed work without duplicating old timer events. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Faruzan character state");
        }
        initializeForSimulator(simulator);
        FaruzanState restored = (FaruzanState) state;
        normalAttackStep = restored.normalAttackStep;
        manifestCharges = restored.manifestCharges;
        manifestExpirationTime = restored.manifestExpirationTime;
        prayerExpirationTime = restored.prayerExpirationTime;
        perfidiousExpirationTime = restored.perfidiousExpirationTime;
        nextParticleTime = restored.nextParticleTime;
        nextA4Time = restored.nextA4Time;
        nextC6Time = restored.nextC6Time;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        double currentTime = simulator.getCurrentTime();
        pendingHits.removeIf(hit -> hit.time < currentTime - EPSILON);
        pendingCommands.removeIf(command ->
                command.time < currentTime - EPSILON);
        for (PendingHit hit : new ArrayList<>(pendingHits)) {
            scheduleHit(simulator, hit);
        }
        for (PendingCommand command : new ArrayList<>(pendingCommands)) {
            scheduleCommand(simulator, command);
        }
    }

    /** Returns Faruzan's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Applies Faruzan's ATK ascension stat through loaded base data. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A4 is target- and gate-dependent and is resolved per hit.
    }

    /** Resets only the Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns remaining Manifest Gale charges at the supplied time. */
    public int getManifestCharges(double currentTime) {
        return currentTime < manifestExpirationTime ? manifestCharges : 0;
    }

    /** Returns the half-open Manifest Gale expiration timestamp. */
    public double getManifestExpirationTime() {
        return manifestExpirationTime;
    }

    /** Returns the current Prayerful Wind expiration timestamp. */
    public double getPrayerExpirationTime() {
        return prayerExpirationTime;
    }

    /** Returns the current Perfidious Wind expiration timestamp. */
    public double getPerfidiousExpirationTime() {
        return perfidiousExpirationTime;
    }

    /** Adds A4 Base-ATK damage to one eligible Anemo direct hit. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            model.entity.Enemy target,
            AttackAction action,
            double currentTime) {
        if (target == null
                || !qualifiesForA4(attacker, action, currentTime)) {
            return;
        }
        double baseAttack = getStructuralStats(currentTime)
                .get(StatType.BASE_ATK);
        stats.add(StatType.FLAT_DMG_BONUS,
                baseAttack * getTalentValue("A4 Base ATK Ratio", 0.32));
    }

    /** Dispatches Normal, fully charged/Hurricane, Press Skill, and Burst. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Faruzan action is required");
        }
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
            case SKILL:
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Unsupported Faruzan Skill mode: "
                                    + request.getSkillMode());
                }
                windRealmOfNasamjnin(simulator);
                break;
            case BURST:
                theWindsSecretWays(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Faruzan: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueCommand(simulator, new PendingCommand(
                castTime + NORMAL_RELEASE_FRAMES[step] * FRAME,
                CommandKind.NORMAL_RELEASE,
                step,
                0L,
                0.0));
        normalAttackStep = (normalAttackStep + 1) % 4;
        simulator.advanceTime(NORMAL_DURATION_FRAMES[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean hurricane = getManifestCharges(castTime) > 0;
        if (hurricane) {
            manifestCharges--;
        }
        queueCommand(simulator, new PendingCommand(
                castTime + (hurricane ? 49.0 : 86.0) * FRAME,
                hurricane
                        ? CommandKind.HURRICANE_RELEASE
                        : CommandKind.CHARGED_RELEASE,
                0,
                0L,
                0.0));
        simulator.advanceTime((hurricane ? 60.0 : 96.0) * FRAME);
    }

    private void windRealmOfNasamjnin(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        StatsContainer snapshot = captureActionStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 12.0 * FRAME,
                CommandKind.MANIFEST,
                0,
                generation,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 12.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0,
                generation,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 14.0 * FRAME,
                HitKind.SKILL,
                0,
                generation,
                snapshot));
        simulator.advanceTime(35.0 * FRAME);
    }

    private void theWindsSecretWays(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 3.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0,
                generation,
                0.0));
        int[] prayerFrames = constellation >= 2
                ? PRAYER_FRAMES_C2 : PRAYER_FRAMES_C0;
        for (int frame : prayerFrames) {
            queueCommand(simulator, new PendingCommand(
                    castTime + frame * FRAME,
                    CommandKind.PRAYER,
                    0,
                    generation,
                    0.0));
        }
        queueHit(simulator, new PendingHit(
                castTime + 54.0 * FRAME,
                HitKind.BURST,
                0,
                generation,
                captureActionStats(castTime)));
        int[] shredFrames = constellation >= 2
                ? SHRED_FRAMES_C2 : SHRED_FRAMES_C0;
        for (int frame : shredFrames) {
            queueCommand(simulator, new PendingCommand(
                    castTime + frame * FRAME,
                    CommandKind.SHRED,
                    0,
                    generation,
                    0.0));
        }
        simulator.advanceTime(60.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        if ((hit.kind == HitKind.SKILL
                && hit.generation != skillGeneration)
                || (hit.kind == HitKind.BURST
                        && hit.generation != burstGeneration)) {
            return;
        }
        AttackAction action;
        switch (hit.kind) {
            case NORMAL:
                action = attack(
                        "Parthian Shot " + NORMAL_KEYS[hit.index],
                        getTalentValue(
                                NORMAL_KEYS[hit.index],
                                NORMAL_MULTIPLIERS[hit.index]),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0);
                break;
            case CHARGED:
                action = attack(
                        "Parthian Shot Fully Charged",
                        getTalentValue("Fully Charged", 2.108),
                        Element.ANEMO,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case HURRICANE:
                action = attack(
                        "Hurricane Arrow",
                        getTalentValue("Fully Charged", 2.108),
                        Element.ANEMO,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case SKILL:
                action = attack(
                        "Wind Realm of Nasamjnin",
                        getTalentValue(
                                constellation >= 3 ? "Skill C3" : "Skill",
                                constellation >= 3 ? 2.976 : 2.5296),
                        Element.ANEMO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            case COLLAPSE:
                action = attack(
                        "Pressurized Collapse",
                        getTalentValue(
                                constellation >= 3
                                        ? "Collapse C3" : "Collapse",
                                constellation >= 3 ? 2.16 : 1.836),
                        Element.ANEMO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            case BURST:
                action = attack(
                        "The Wind's Secret Ways",
                        getTalentValue(
                                constellation >= 5 ? "Burst C5" : "Burst",
                                constellation >= 5 ? 7.552 : 6.4192),
                        Element.ANEMO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            default:
                throw new IllegalStateException("Unknown Faruzan hit kind");
        }
        if (hit.kind == HitKind.CHARGED
                || hit.kind == HitKind.HURRICANE) {
            action.setHitlagProfile(AIMED_HEADSHOT_HITLAG);
        }
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if (hit.kind == HitKind.BURST) {
            applyPerfidiousWind(
                    simulator, simulator.getCurrentTime());
        } else if (hit.kind == HitKind.HURRICANE
                && simulator.getEnemy() != null) {
            queueCollapse(simulator, captureActionStats(
                    simulator.getCurrentTime()));
        } else if (hit.kind == HitKind.COLLAPSE) {
            resolveCollapseAftermath(simulator);
        }
    }

    private void queueCollapse(
            CombatSimulator simulator,
            StatsContainer snapshot) {
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + 33.0 * FRAME,
                HitKind.COLLAPSE,
                0,
                0L,
                snapshot));
    }

    private void resolveCollapseAftermath(CombatSimulator simulator) {
        if (simulator.getEnemy() == null) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        applyPerfidiousWind(simulator, currentTime);
        if (currentTime >= nextParticleTime) {
            nextParticleTime = currentTime + PARTICLE_GATE;
            queueCommand(simulator, new PendingCommand(
                    currentTime + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    0,
                    0L,
                    2.0));
        }
        if (constellation >= 4) {
            receiveFlatEnergy(getTalentValue("C4 Energy", 2.0));
        }
    }

    private void onResolvedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (damage <= 0.0 || action == null || actor == null) {
            return;
        }
        if (qualifiesForA4(actor, action, time)) {
            nextA4Time = time + A4_GATE;
        }
        triggerC6Collapse(actor, damage, time);
    }

    private void triggerC6Collapse(
            Character actor,
            double damage,
            double time) {
        if (damage <= 0.0
                || constellation < 6
                || initializedSimulator == null
                || actor != initializedSimulator.getActiveCharacter()
                || time >= prayerExpirationTime
                || time < nextC6Time) {
            return;
        }
        nextC6Time = time + C6_GATE;
        queueCollapse(initializedSimulator, captureActionStats(time));
    }

    private boolean qualifiesForA4(
            Character attacker,
            AttackAction action,
            double currentTime) {
        if (initializedSimulator == null
                || attacker == null
                || !initializedSimulator.getPartyMembers().contains(attacker)
                || action == null
                || action.getElement() != Element.ANEMO
                || currentTime >= prayerExpirationTime
                || currentTime < nextA4Time) {
            return false;
        }
        ActionType type = action.getActionType();
        return type == ActionType.NORMAL
                || type == ActionType.CHARGE
                || type == ActionType.PLUNGE
                || type == ActionType.SKILL
                || type == ActionType.BURST;
    }

    private void applyPrayerfulWind(
            CombatSimulator simulator,
            double currentTime) {
        prayerExpirationTime = currentTime + SUPPORT_WINDOW;
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Faruzan Prayerful Wind's Gift",
                BuffId.FARUZAN_PRAYERFUL_WIND,
                SUPPORT_WINDOW,
                currentTime,
                stats -> {
                    stats.add(
                            StatType.ANEMO_DMG_BONUS,
                            getTalentValue(
                                    constellation >= 5
                                            ? "Anemo DMG Bonus C5"
                                            : "Anemo DMG Bonus",
                                    constellation >= 5 ? 0.36 : 0.306));
                    if (constellation >= 6) {
                        stats.add(
                                StatType.ANEMO_CRIT_DMG,
                                getTalentValue(
                                        "C6 Anemo CRIT DMG", 0.40));
                    }
                }).sourcedBy(characterId));
    }

    private void applyPerfidiousWind(
            CombatSimulator simulator,
            double currentTime) {
        perfidiousExpirationTime = currentTime + SUPPORT_WINDOW;
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Faruzan Perfidious Wind's Ruin",
                BuffId.FARUZAN_PERFIDIOUS_WIND,
                SUPPORT_WINDOW,
                currentTime,
                stats -> stats.add(StatType.ANEMO_RES_SHRED, 0.30))
                .sourcedBy(characterId));
    }

    private StatsContainer captureActionStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static AttackAction attack(
            String displayName,
            double multiplier,
            Element element,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        return action;
    }

    private void queueHit(CombatSimulator simulator, PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(CombatSimulator simulator, PendingHit hit) {
        schedule(simulator, hit.time, activeSimulator -> {
            if (!pendingHits.remove(hit)) {
                return;
            }
            resolveHit(activeSimulator, hit);
        });
    }

    private void queueCommand(
            CombatSimulator simulator,
            PendingCommand command) {
        pendingCommands.add(command);
        scheduleCommand(simulator, command);
    }

    private void scheduleCommand(
            CombatSimulator simulator,
            PendingCommand command) {
        schedule(simulator, command.time, activeSimulator -> {
            if (!pendingCommands.remove(command)) {
                return;
            }
            resolveCommand(activeSimulator, command);
        });
    }

    private void resolveCommand(
            CombatSimulator simulator,
            PendingCommand command) {
        switch (command.kind) {
            case NORMAL_RELEASE:
                queueProjectileHit(
                        simulator, HitKind.NORMAL, command.index);
                break;
            case CHARGED_RELEASE:
                queueProjectileHit(simulator, HitKind.CHARGED, 0);
                break;
            case HURRICANE_RELEASE:
                queueProjectileHit(simulator, HitKind.HURRICANE, 0);
                break;
            case MANIFEST:
                if (command.generation == skillGeneration) {
                    manifestCharges = constellation >= 1 ? 2 : 1;
                    manifestExpirationTime = simulator.getCurrentTime()
                            + MANIFEST_DURATION;
                }
                break;
            case SKILL_COOLDOWN:
                if (command.generation == skillGeneration) {
                    markSkillUsed(
                            simulator.getCurrentTime(),
                            simulator.getApplicableBuffs(this));
                }
                break;
            case PARTICLE:
                simulator.getEnergyDistributor().distributeParticles(
                        Element.ANEMO,
                        command.value,
                        ParticleType.PARTICLE);
                break;
            case BURST_ENERGY:
                if (command.generation == burstGeneration) {
                    spendBurstEnergy(simulator.getCurrentTime());
                }
                break;
            case PRAYER:
                if (command.generation == burstGeneration) {
                    applyPrayerfulWind(
                            simulator, simulator.getCurrentTime());
                }
                break;
            case SHRED:
                if (command.generation == burstGeneration) {
                    applyPerfidiousWind(
                            simulator, simulator.getCurrentTime());
                }
                break;
            default:
                throw new IllegalStateException("Unknown Faruzan command kind");
        }
    }

    private void queueProjectileHit(
            CombatSimulator simulator,
            HitKind kind,
            int index) {
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + 10.0 * FRAME,
                kind,
                index,
                0L,
                captureActionStats(simulator.getCurrentTime())));
    }

    private static void schedule(
            CombatSimulator simulator,
            double time,
            Consumer<CombatSimulator> effect) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                effect.accept(activeSimulator);
            }
        });
    }

    private static StatsContainer copyStats(StatsContainer stats) {
        return stats == null ? null : stats.merge(null);
    }

    private static List<PendingHit> copyHits(List<PendingHit> source) {
        List<PendingHit> copy = new ArrayList<>();
        for (PendingHit hit : source) {
            copy.add(hit.copy());
        }
        return copy;
    }

    private static List<PendingCommand> copyCommands(
            List<PendingCommand> source) {
        List<PendingCommand> copy = new ArrayList<>();
        for (PendingCommand command : source) {
            copy.add(command.copy());
        }
        return copy;
    }

    private enum HitKind {
        NORMAL,
        CHARGED,
        HURRICANE,
        SKILL,
        COLLAPSE,
        BURST
    }

    private enum CommandKind {
        NORMAL_RELEASE,
        CHARGED_RELEASE,
        HURRICANE_RELEASE,
        MANIFEST,
        SKILL_COOLDOWN,
        PARTICLE,
        BURST_ENERGY,
        PRAYER,
        SHRED
    }

    /** Immutable delayed Faruzan hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.snapshot = copyStats(snapshot);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, generation, snapshot);
        }
    }

    /** Immutable delayed Faruzan command description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final int index;
        private final long generation;
        private final double value;

        private PendingCommand(
                double time,
                CommandKind kind,
                int index,
                long generation,
                double value) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time, kind, index, generation, value);
        }
    }

    /** Immutable Faruzan-owned simulator snapshot payload. */
    private static final class FaruzanState implements State {
        private final Faruzan owner;
        private final int normalAttackStep;
        private final int manifestCharges;
        private final double manifestExpirationTime;
        private final double prayerExpirationTime;
        private final double perfidiousExpirationTime;
        private final double nextParticleTime;
        private final double nextA4Time;
        private final double nextC6Time;
        private final long skillGeneration;
        private final long burstGeneration;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private FaruzanState(
                Faruzan owner,
                int normalAttackStep,
                int manifestCharges,
                double manifestExpirationTime,
                double prayerExpirationTime,
                double perfidiousExpirationTime,
                double nextParticleTime,
                double nextA4Time,
                double nextC6Time,
                long skillGeneration,
                long burstGeneration,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.manifestCharges = manifestCharges;
            this.manifestExpirationTime = manifestExpirationTime;
            this.prayerExpirationTime = prayerExpirationTime;
            this.perfidiousExpirationTime = perfidiousExpirationTime;
            this.nextParticleTime = nextParticleTime;
            this.nextA4Time = nextA4Time;
            this.nextC6Time = nextC6Time;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
