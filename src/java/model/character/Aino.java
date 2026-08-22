package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.ActiveCharacterBuff;
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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Aino's deterministic fixed-target Musecatcher and Ducky slice.
 *
 * <p>Three Physical Normal stages, two Skill impacts, particles, the Ducky
 * cadence, A1/A4, and representable C1-C6 branches follow pinned gcsim
 * {@code ef41805d}. Aino contributes typed Lunar membership, and A1/C6 use the
 * simulator's existing Moonsign and reaction-bonus stats.</p>
 *
 * <p>Random and multi-target placement, geometry, movement, hitlag extension, stamina,
 * Charged and Plunging attacks absent from the pinned implementation, Hold
 * Skill, unsupported defensive state, and untyped Lunar target behavior fail
 * closed. Every Ducky ball deterministically addresses the simulator's single
 * enemy; emitted damage captures live stats before its sourced 10-frame
 * travel.</p>
 */
public final class Aino extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 23 }, { 20 }, { 35, 43 }
    };
    private static final int[] NORMAL_DURATIONS = { 48, 75, 93 };
    private static final double[][] NORMAL_T9 = {
        { 1.221719 }, { 1.216079 }, { 0.904210, 0.904210 }
    };
    private static final int[] SKILL_HIT_FRAMES = { 15, 33 };
    private static final double[] SKILL_T9 = { 1.115200, 3.209600 };
    private static final double[] SKILL_C5 = { 1.312000, 3.776000 };

    /**
     * Per-hit hitlag from gcsim config YAML pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_HEAVY_HITLAG =
            new HitlagProfile(0.10, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_N3_SECOND_HITLAG =
            new HitlagProfile(0.08, 0.01, true, false, false);
    private static final HitlagProfile SKILL_SECOND_HITLAG =
            new HitlagProfile(0.03, 0.01, false, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long eventGeneration;
    private double duckyActiveUntil = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC2AllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC4AllowedTime = Double.NEGATIVE_INFINITY;
    private AttackAction resolvingAction;
    private boolean resolvingSkillImpact;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Aino. */
    public Aino(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Aino at an explicit constellation. */
    public Aino(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Aino with injectable static talent data.
     *
     * @param weapon equipped claymore, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Aino(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Aino constellation must be between 0 and 6");
        }
        name = "Aino";
        characterId = CharacterId.AINO;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11201.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 242.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 607.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Ascension Elemental Mastery", 96.0));
        setSkillCD(getTalentValue("Skill Cooldown", 10.0));
        setBurstCD(getTalentValue("Burst Cooldown", 13.5));
    }

    /** Binds Aino's damage listener and future work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Aino simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Aino must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Aino cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                handleAcceptedDamage(
                        simulator, actor, action, damage, time));
    }

    /** Captures gates, Ducky state, event generation, and reconstructable work. */
    @Override
    public State captureCharacterState() {
        return new AinoState(
                this,
                normalAttackStep,
                eventGeneration,
                duckyActiveUntil,
                nextParticleAllowedTime,
                nextC2AllowedTime,
                nextC4AllowedTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Aino instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof AinoState
                && ((AinoState) state).owner == this;
    }

    /** Restores surviving Aino work while invalidating every older queue. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Aino state");
        }
        initializeForSimulator(simulator);
        AinoState restored = (AinoState) state;
        normalAttackStep = restored.normalAttackStep;
        eventGeneration = Math.max(
                eventGeneration, restored.eventGeneration) + 1L;
        duckyActiveUntil = restored.duckyActiveUntil;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        nextC2AllowedTime = restored.nextC2AllowedTime;
        nextC4AllowedTime = restored.nextC4AllowedTime;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        resolvingAction = null;
        resolvingSkillImpact = false;
        double currentTime = simulator.getCurrentTime();
        pendingHits.removeIf(hit -> hit.time < currentTime - EPSILON);
        pendingCommands.removeIf(command ->
                command.time < currentTime - EPSILON);
        for (PendingHit hit : new ArrayList<>(pendingHits)) {
            scheduleHit(simulator, hit);
        }
        for (PendingCommand command
                : new ArrayList<>(pendingCommands)) {
            scheduleCommand(simulator, command);
        }
    }

    /** Returns Aino's 50-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 50.0);
    }

    /** Aino has no unconditional represented passive stat beyond ascension EM. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Reports Aino's typed Moonsign contribution. */
    @Override
    public boolean isLunarCharacter() {
        return true;
    }

    /** Resets the three-stage Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets the three-stage Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether the C2 trigger window is active at the supplied time. */
    public boolean isDuckyActive(double currentTime) {
        return currentTime + EPSILON < duckyActiveUntil;
    }

    /** Returns the exact current Ducky-window expiration timestamp. */
    public double getDuckyActiveUntil() {
        return duckyActiveUntil;
    }

    /** Returns the number of unresolved Aino-owned impacts. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Returns the number of unresolved Aino-owned state commands. */
    public int getPendingCommandCount() {
        return pendingCommands.size();
    }

    /** Reports that random placement and multi-target geometry are excluded. */
    public boolean isRandomPlacementRepresented() {
        return false;
    }

    /** Reports that unsupported defensive player state is excluded. */
    public boolean isDefensiveStateRepresented() {
        return false;
    }

    /** Dispatches Aino's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Aino action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Aino supports Press Skill only");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case SKILL:
                musecatcher(simulator);
                break;
            case BURST:
                coolYourJetsDucky(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Aino: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    HitKind.NORMAL,
                    step,
                    hit,
                    false,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void musecatcher(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        applyC1(simulator, castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Skill Cooldown Start Frame", 13.0) * FRAME,
                CommandKind.SKILL_COOLDOWN,
                false));
        for (int hit = 0; hit < SKILL_HIT_FRAMES.length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + SKILL_HIT_FRAMES[hit] * FRAME,
                    HitKind.SKILL,
                    hit,
                    0,
                    false,
                    null));
        }
        simulator.advanceTime(52.0 * FRAME);
    }

    private void coolYourJetsDucky(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean enhanced = simulator.getMoonsign()
                == CombatSimulator.Moonsign.ASCENDANT_GLEAM;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        applyC1(simulator, castTime);
        applyC6(simulator, castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Burst Energy Spend Frame", 5.0) * FRAME,
                CommandKind.BURST_ENERGY,
                false));
        double startTime = castTime + getTalentValue(
                "Ducky Start Frame", 123.0) * FRAME;
        queueCommand(simulator, new PendingCommand(
                startTime, CommandKind.DUCKY_START, false));
        double interval = getTalentValue(
                enhanced ? "A1 Ducky Interval" : "Ducky Interval",
                enhanced ? 0.7 : 1.5);
        double duration = getTalentValue("Ducky Duration", 14.2);
        for (int index = 0; index * interval < duration - EPSILON;
                index++) {
            queueCommand(simulator, new PendingCommand(
                    startTime + index * interval,
                    CommandKind.DUCKY_EMIT,
                    enhanced));
        }
        simulator.advanceTime(60.0 * FRAME);
    }

    private void applyC1(
            CombatSimulator simulator,
            double currentTime) {
        if (constellation < 1) {
            return;
        }
        double amount = getTalentValue(
                "C1 Elemental Mastery", 80.0);
        double duration = getTalentValue("C1 Duration", 15.0);
        for (Character member : simulator.getPartyMembers()) {
            member.removeBuff(BuffId.AINO_C1_ELEMENTAL_MASTERY);
            if (member == this) {
                member.addBuff(new SimpleBuff(
                        "Aino Theory of Ash-Field Equilibrium",
                        BuffId.AINO_C1_ELEMENTAL_MASTERY,
                        duration,
                        currentTime,
                        stats -> stats.add(
                                StatType.ELEMENTAL_MASTERY, amount))
                        .sourcedBy(characterId));
            } else {
                member.addBuff(new ActiveCharacterBuff(
                        "Aino Theory of Ash-Field Equilibrium",
                        BuffId.AINO_C1_ELEMENTAL_MASTERY,
                        duration,
                        currentTime,
                        simulator,
                        member,
                        stats -> stats.add(
                                StatType.ELEMENTAL_MASTERY, amount))
                        .sourcedBy(characterId));
            }
        }
    }

    private void applyC6(
            CombatSimulator simulator,
            double currentTime) {
        if (constellation < 6) {
            return;
        }
        double duration = getTalentValue("C6 Duration", 15.0);
        for (Character member : simulator.getPartyMembers()) {
            member.removeBuff(BuffId.AINO_C6_REACTION_DMG_BONUS);
            member.addBuff(new ActiveCharacterBuff(
                    "Aino Burden of Creative Genius",
                    BuffId.AINO_C6_REACTION_DMG_BONUS,
                    duration,
                    currentTime,
                    simulator,
                    member,
                    stats -> applyC6ReactionBonus(stats, simulator))
                    .sourcedBy(characterId));
        }
    }

    private void applyC6ReactionBonus(
            StatsContainer stats,
            CombatSimulator simulator) {
        double bonus = getTalentValue(
                "C6 Reaction DMG Bonus", 0.15);
        if (simulator.getMoonsign()
                == CombatSimulator.Moonsign.ASCENDANT_GLEAM) {
            bonus += getTalentValue(
                    "C6 Ascendant Bonus", 0.20);
        }
        stats.add(StatType.ELECTRO_CHARGED_DMG_BONUS, bonus);
        stats.add(StatType.BLOOM_DMG_BONUS, bonus);
        stats.add(StatType.LUNAR_REACTION_DMG_BONUS_ALL, bonus);
    }

    private void startDucky(double currentTime) {
        duckyActiveUntil = Math.max(
                duckyActiveUntil,
                currentTime + getTalentValue(
                        "Ducky Active Duration", 14.0));
    }

    private void emitDucky(
            CombatSimulator simulator,
            boolean enhanced) {
        StatsContainer snapshot = captureLiveStats(
                simulator.getCurrentTime());
        snapshot.add(
                StatType.FLAT_DMG_BONUS,
                snapshot.get(StatType.ELEMENTAL_MASTERY)
                        * getTalentValue(
                                "A4 EM Flat Damage Ratio", 0.5));
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime()
                        + getTalentValue(
                                "Ducky Attack Delay Frames", 10.0)
                                * FRAME,
                HitKind.DUCKY,
                0,
                0,
                enhanced,
                snapshot));
    }

    private void handleAcceptedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (simulator != initializedSimulator || damage <= 0.0) {
            return;
        }
        if (actor == this && action == resolvingAction
                && resolvingSkillImpact) {
            triggerSkillHitEffects(simulator, time);
        }
        if (constellation < 2
                || !isDuckyActive(time)
                || simulator.getActiveCharacter() == this
                || actor != simulator.getActiveCharacter()
                || time + EPSILON < nextC2AllowedTime) {
            return;
        }
        nextC2AllowedTime = time
                + getTalentValue("C2 Cooldown", 5.0);
        StatsContainer snapshot = captureLiveStats(time);
        double elementalMastery = snapshot.get(
                StatType.ELEMENTAL_MASTERY);
        snapshot.add(
                StatType.FLAT_DMG_BONUS,
                elementalMastery * (getTalentValue(
                        "C2 EM Flat Damage Ratio", 1.0)
                        + getTalentValue(
                                "A4 EM Flat Damage Ratio", 0.5)));
        queueHit(simulator, new PendingHit(
                time + getTalentValue(
                        "C2 Attack Delay Frames", 10.0) * FRAME,
                HitKind.C2,
                0,
                0,
                false,
                snapshot));
    }

    private void triggerSkillHitEffects(
            CombatSimulator simulator,
            double hitTime) {
        if (hitTime + EPSILON >= nextParticleAllowedTime) {
            nextParticleAllowedTime = hitTime
                    + getTalentValue("Particle Cooldown", 0.6);
            queueCommand(simulator, new PendingCommand(
                    hitTime + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE,
                    false));
        }
        if (constellation >= 4
                && hitTime + EPSILON >= nextC4AllowedTime) {
            nextC4AllowedTime = hitTime
                    + getTalentValue("C4 Cooldown", 10.0);
            receiveFlatEnergy(getTalentValue("C4 Flat Energy", 10.0));
        }
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if (simulator.getEnemy() == null) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Bish-Bash-Bosh Repair N" + (hit.index + 1)
                                + (hit.variant > 0
                                        ? "-" + (hit.variant + 1) : ""),
                        getTalentValue(
                                hit.index == 2
                                        ? "N3 Hit " + (hit.variant + 1)
                                        : "N" + (hit.index + 1),
                                NORMAL_T9[hit.index][hit.variant]),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0,
                        true,
                        false);
                break;
            case SKILL:
                performHit(
                        simulator,
                        hit,
                        "Musecatcher Stage " + (hit.index + 1),
                        skillValue(hit.index),
                        Element.HYDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        1.0,
                        hit.index == 1,
                        true);
                break;
            case DUCKY:
                performHit(
                        simulator,
                        hit,
                        "Cool Your Jets Ducky Water Ball",
                        burstValue(),
                        Element.HYDRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        hit.enhanced
                                ? ICDType.AinoDucky : ICDType.Standard,
                        hit.enhanced
                                ? ICDTag.Aino_Ducky : ICDTag.ElementalBurst,
                        1.0,
                        false,
                        false);
                break;
            case C2:
                performHit(
                        simulator,
                        hit,
                        "Cool Your Jets Ducky C2",
                        getTalentValue("C2 ATK Ratio", 0.25),
                        Element.HYDRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        false,
                        false);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Aino hit kind " + hit.kind);
        }
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            boolean shatter,
            boolean skillImpact) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setShatterTrigger(shatter);
        action.setHitlagProfile(hitlagProfile(hit));
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot.merge(null);
        action.setStatSnapshot(snapshot);
        resolvingAction = action;
        resolvingSkillImpact = skillImpact;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingSkillImpact = false;
        }
    }

    private static HitlagProfile hitlagProfile(PendingHit hit) {
        if (hit.kind == HitKind.NORMAL) {
            if (hit.index < 2) {
                return NORMAL_HEAVY_HITLAG;
            }
            if (hit.index == 2 && hit.variant == 1) {
                return NORMAL_N3_SECOND_HITLAG;
            }
        }
        if (hit.kind == HitKind.SKILL && hit.index == 1) {
            return SKILL_SECOND_HITLAG;
        }
        return HitlagProfile.none();
    }

    private double skillValue(int index) {
        boolean c5 = constellation >= 5;
        return getTalentValue(
                "Musecatcher Stage " + (index + 1)
                        + (c5 ? " C5" : ""),
                c5 ? SKILL_C5[index] : SKILL_T9[index]);
    }

    private double burstValue() {
        boolean c3 = constellation >= 3;
        return getTalentValue(
                "Ducky Water Ball" + (c3 ? " C3" : ""),
                c3 ? 0.402240 : 0.341904);
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator == null) {
            return stats;
        }
        for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private void queueHit(
            CombatSimulator simulator,
            PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(
            CombatSimulator simulator,
            PendingHit hit) {
        long scheduledGeneration = eventGeneration;
        schedule(simulator, hit.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingHits.remove(hit)) {
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
        long scheduledGeneration = eventGeneration;
        schedule(simulator, command.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingCommands.remove(command)) {
                return;
            }
            switch (command.kind) {
                case SKILL_COOLDOWN:
                    markSkillUsed(
                            command.time,
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(command.time);
                    break;
                case DUCKY_START:
                    startDucky(command.time);
                    break;
                case DUCKY_EMIT:
                    emitDucky(activeSimulator, command.enhanced);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.HYDRO,
                                    getTalentValue("Particle Count", 3.0),
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Aino command " + command.kind);
            }
        });
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
        SKILL,
        DUCKY,
        C2
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        DUCKY_START,
        DUCKY_EMIT,
        PARTICLE
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final boolean enhanced;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                boolean enhanced,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.enhanced = enhanced;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, variant, enhanced, snapshot);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final boolean enhanced;

        private PendingCommand(
                double time,
                CommandKind kind,
                boolean enhanced) {
            this.time = time;
            this.kind = kind;
            this.enhanced = enhanced;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, enhanced);
        }
    }

    private static final class AinoState implements State {
        private final Aino owner;
        private final int normalAttackStep;
        private final long eventGeneration;
        private final double duckyActiveUntil;
        private final double nextParticleAllowedTime;
        private final double nextC2AllowedTime;
        private final double nextC4AllowedTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private AinoState(
                Aino owner,
                int normalAttackStep,
                long eventGeneration,
                double duckyActiveUntil,
                double nextParticleAllowedTime,
                double nextC2AllowedTime,
                double nextC4AllowedTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.eventGeneration = eventGeneration;
            this.duckyActiveUntil = duckyActiveUntil;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.nextC2AllowedTime = nextC2AllowedTime;
            this.nextC4AllowedTime = nextC4AllowedTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
