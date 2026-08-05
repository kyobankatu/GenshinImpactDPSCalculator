package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
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
 * Tartaglia's stationary fixed-target bow and Foul Legacy offensive slice.
 *
 * <p>Ranged basics, stance entry and exit, melee Normal and Charged Attacks,
 * duration-based Skill cooldowns, both Burst forms, Riptide Flash, Slash,
 * Blast, particles, A1, and representable C1/C3/C4/C5/C6 behavior follow the
 * pinned gcsim {@code ef41805d} implementation.</p>
 *
 * <p>Riptide is deliberately one fixed-target status. Weak points, enemy
 * defeat propagation and C2, movement, geometry, multi-target selection,
 * stamina, hitlag, player HP, the crit-gated A4 application, the party talent
 * level passive, and Riptide Flash's private aura ICD are excluded instead of
 * being approximated through shared runtime APIs.</p>
 */
public final class Tartaglia extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] RANGED_HIT_FRAMES = { 27, 18, 25, 29, 21, 24 };
    private static final int[] RANGED_DURATIONS = { 26, 27, 33, 32, 33, 66 };
    private static final int[][] MELEE_HIT_FRAMES = {
        { 8 }, { 6 }, { 16 }, { 7 }, { 7 }, { 4, 20 }
    };
    private static final int[] MELEE_DURATIONS = { 23, 23, 37, 37, 23, 65 };
    private static final double[] RANGED_T9 = {
        0.758400, 0.850040, 1.017520, 1.047540, 1.118640, 1.336680
    };
    private static final double[][] MELEE_T9 = {
        { 0.714160 }, { 0.764720 }, { 1.034900 }, { 1.101260 },
        { 1.015940 }, { 0.650960, 0.692040 }
    };
    private static final double[][] MELEE_C3 = {
        { 0.876880 }, { 0.938960 }, { 1.270700 }, { 1.352180 },
        { 1.247420 }, { 0.799280, 0.849720 }
    };
    private static final double[] MELEE_CHARGED_T9 = { 1.106000, 1.322460 };
    private static final double[] MELEE_CHARGED_C3 = { 1.358000, 1.623780 };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long stanceGeneration;
    private long riptideGeneration;
    private double stanceStartTime = Double.NEGATIVE_INFINITY;
    private double stanceExpirationTime = Double.NEGATIVE_INFINITY;
    private double riptideExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextRiptideFlashTime = Double.NEGATIVE_INFINITY;
    private double nextRiptideSlashTime = Double.NEGATIVE_INFINITY;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private boolean meleeBurstUsed;
    private AttackAction resolvingAction;
    private TriggerKind resolvingTrigger = TriggerKind.NONE;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Tartaglia. */
    public Tartaglia(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Tartaglia at an explicit constellation. */
    public Tartaglia(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Tartaglia with injectable static talent data.
     *
     * @param weapon equipped bow, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Tartaglia(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Tartaglia constellation must be between 0 and 6");
        }
        name = "Tartaglia";
        characterId = CharacterId.TARTAGLIA;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13103.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 301.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 815.0));
        baseStats.add(StatType.HYDRO_DMG_BONUS,
                getTalentValue("Ascension Hydro DMG", 0.288));
        setSkillCD(1.0);
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Tartaglia's accepted-hit callbacks to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Tartaglia simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Tartaglia must belong to the simulator party");
        }
        if (initializedSimulator != null && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Tartaglia cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor == this && action == resolvingAction && damage > 0.0) {
                handleAcceptedHit(simulator, resolvingTrigger, time);
            }
        });
    }

    /** Captures stance, Riptide, cooldown gates, and all delayed owner work. */
    @Override
    public State captureCharacterState() {
        return new TartagliaState(
                this,
                normalAttackStep,
                stanceGeneration,
                riptideGeneration,
                stanceStartTime,
                stanceExpirationTime,
                riptideExpirationTime,
                nextRiptideFlashTime,
                nextRiptideSlashTime,
                nextParticleTime,
                meleeBurstUsed,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Tartaglia instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof TartagliaState
                && ((TartagliaState) state).owner == this;
    }

    /** Restores Tartaglia-owned state and each surviving event exactly once. */
    @Override
    public void restoreCharacterState(State state, CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Tartaglia state");
        }
        initializeForSimulator(simulator);
        TartagliaState restored = (TartagliaState) state;
        normalAttackStep = restored.normalAttackStep;
        stanceGeneration = restored.stanceGeneration;
        riptideGeneration = restored.riptideGeneration;
        stanceStartTime = restored.stanceStartTime;
        stanceExpirationTime = restored.stanceExpirationTime;
        riptideExpirationTime = restored.riptideExpirationTime;
        nextRiptideFlashTime = restored.nextRiptideFlashTime;
        nextRiptideSlashTime = restored.nextRiptideSlashTime;
        nextParticleTime = restored.nextParticleTime;
        meleeBurstUsed = restored.meleeBurstUsed;
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

    /** Returns Tartaglia's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Tartaglia has no unconditional self-stat passive in this slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Ends Foul Legacy immediately when Tartaglia leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        if (isMeleeStanceActive(simulator.getCurrentTime())) {
            endMeleeStance(simulator, 0.0);
        }
    }

    /** Resets the represented attack string on field entry. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether the melee stance is active at a half-open boundary. */
    public boolean isMeleeStanceActive(double currentTime) {
        return currentTime + EPSILON < stanceExpirationTime;
    }

    /** Returns whether the fixed target currently carries Riptide. */
    public boolean isRiptideActive(double currentTime) {
        return currentTime + EPSILON < riptideExpirationTime;
    }

    /** Returns the current fixed-target Riptide expiration timestamp. */
    public double getRiptideExpirationTime() {
        return riptideExpirationTime;
    }

    /** Returns the current melee stance expiration timestamp. */
    public double getStanceExpirationTime() {
        return stanceExpirationTime;
    }

    /** Returns the number of unresolved Tartaglia-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Reports that crit-gated A4 application is intentionally unavailable. */
    public boolean isA4CritApplicationRepresented() {
        return false;
    }

    /** Reports that the party Normal-talent passive is not a runtime stat. */
    public boolean isPartyTalentPassiveRepresented() {
        return false;
    }

    /** Dispatches Tartaglia's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Tartaglia action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Tartaglia supports Press Skill only");
        }
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
                toggleMeleeStance(simulator);
                break;
            case BURST:
                havocObliteration(simulator);
                break;
            case PLUNGE:
                throw new IllegalArgumentException(
                        "Tartaglia Plunge lacks pinned character source data");
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Tartaglia: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        if (isMeleeStanceActive(simulator.getCurrentTime())) {
            meleeNormalAttack(simulator);
            return;
        }
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + RANGED_HIT_FRAMES[step] * FRAME,
                HitKind.RANGED_NORMAL,
                step,
                0,
                captureLiveStats(castTime)));
        normalAttackStep = (normalAttackStep + 1) % RANGED_T9.length;
        simulator.advanceTime(RANGED_DURATIONS[step] * FRAME);
    }

    private void meleeNormalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        StatsContainer snapshot = captureLiveStats(castTime);
        for (int hit = 0; hit < MELEE_HIT_FRAMES[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + MELEE_HIT_FRAMES[step][hit] * FRAME,
                    HitKind.MELEE_NORMAL,
                    step,
                    hit,
                    snapshot));
        }
        normalAttackStep = (normalAttackStep + 1) % MELEE_T9.length;
        simulator.advanceTime(MELEE_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        if (isMeleeStanceActive(simulator.getCurrentTime())) {
            meleeChargedAttack(simulator);
            return;
        }
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 96.0 * FRAME,
                HitKind.RANGED_CHARGED,
                0,
                0,
                captureLiveStats(castTime)));
        simulator.advanceTime(94.0 * FRAME);
    }

    private void meleeChargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        queueHit(simulator, new PendingHit(
                castTime + 14.0 * FRAME,
                HitKind.MELEE_CHARGED,
                0,
                0,
                snapshot));
        queueHit(simulator, new PendingHit(
                castTime + 27.0 * FRAME,
                HitKind.MELEE_CHARGED,
                0,
                1,
                snapshot));
        simulator.advanceTime(55.0 * FRAME);
    }

    private void toggleMeleeStance(CombatSimulator simulator) {
        if (isMeleeStanceActive(simulator.getCurrentTime())) {
            endMeleeStance(simulator, 11.0 * FRAME);
            simulator.advanceTime(18.0 * FRAME);
            return;
        }
        double castTime = simulator.getCurrentTime();
        long generation = ++stanceGeneration;
        stanceStartTime = castTime;
        stanceExpirationTime = castTime
                + getTalentValue("Melee Stance Maximum Duration", 30.0);
        meleeBurstUsed = false;
        normalAttackStep = 0;
        setSkillCD(1.0);
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        queueHit(simulator, new PendingHit(
                castTime + 16.0 * FRAME,
                HitKind.STANCE_CHANGE,
                0,
                0,
                captureLiveStats(castTime)));
        queueCommand(simulator, new PendingCommand(
                stanceExpirationTime,
                CommandKind.STANCE_EXPIRE,
                0.0,
                generation));
        simulator.advanceTime(39.0 * FRAME);
    }

    private void endMeleeStance(
            CombatSimulator simulator,
            double cooldownDelay) {
        double exitTime = simulator.getCurrentTime();
        double cooldown = stanceCooldown(exitTime - stanceStartTime);
        long generation = ++stanceGeneration;
        stanceExpirationTime = Double.NEGATIVE_INFINITY;
        normalAttackStep = 0;
        if (cooldownDelay <= EPSILON) {
            startStanceCooldown(simulator, cooldown);
            return;
        }
        queueCommand(simulator, new PendingCommand(
                exitTime + cooldownDelay,
                CommandKind.STANCE_COOLDOWN,
                cooldown,
                generation));
    }

    private double stanceCooldown(double duration) {
        double cooldown;
        if (duration < 2.0) {
            cooldown = 7.0;
        } else if (duration < 4.0) {
            cooldown = 8.0;
        } else if (duration < 5.0) {
            cooldown = 9.0;
        } else if (duration < 8.0) {
            cooldown = 5.0 + duration;
        } else if (duration < 30.0) {
            cooldown = 6.0 + duration;
        } else {
            cooldown = 45.0;
        }
        return constellation >= 1 ? cooldown * 0.8 : cooldown;
    }

    private void startStanceCooldown(
            CombatSimulator simulator,
            double cooldown) {
        setSkillCD(cooldown);
        markSkillUsed(simulator.getCurrentTime(),
                simulator.getApplicableBuffs(this));
        setSkillCD(1.0);
        if (constellation >= 6 && meleeBurstUsed) {
            resetSkillCooldown(simulator.getCurrentTime());
        }
        meleeBurstUsed = false;
    }

    private void havocObliteration(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean melee = isMeleeStanceActive(castTime);
        if (melee) {
            queueCommand(simulator, new PendingCommand(
                    castTime + 66.0 * FRAME,
                    CommandKind.BURST_COOLDOWN,
                    0.0,
                    0L));
            queueCommand(simulator, new PendingCommand(
                    castTime + 71.0 * FRAME,
                    CommandKind.BURST_ENERGY,
                    0.0,
                    0L));
            queueHit(simulator, new PendingHit(
                    castTime + 69.0 * FRAME,
                    HitKind.MELEE_BURST,
                    0,
                    0,
                    captureLiveStats(castTime)));
            meleeBurstUsed = constellation >= 6;
            simulator.advanceTime(103.0 * FRAME);
            return;
        }
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 3.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0,
                0L));
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.RANGED_BURST_REFUND,
                20.0,
                0L));
        queueHit(simulator, new PendingHit(
                castTime + 70.0 * FRAME,
                HitKind.RANGED_BURST,
                0,
                0,
                captureLiveStats(castTime)));
        simulator.advanceTime(55.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case RANGED_NORMAL:
                performHit(simulator, hit,
                        "Cutting Torrent Ranged N" + (hit.index + 1),
                        getTalentValue("Ranged N" + (hit.index + 1),
                                RANGED_T9[hit.index]),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0,
                        TriggerKind.NONE);
                break;
            case RANGED_CHARGED:
                performHit(simulator, hit,
                        "Cutting Torrent Fully-Charged Aimed Shot",
                        getTalentValue("Fully-Charged Aimed Shot", 2.108000),
                        Element.HYDRO,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        1.0,
                        TriggerKind.RANGED_CHARGED);
                break;
            case STANCE_CHANGE:
                performHit(simulator, hit,
                        "Foul Legacy: Raging Tide Stance Change",
                        stanceValue(),
                        Element.HYDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        2.0,
                        TriggerKind.NONE);
                break;
            case MELEE_NORMAL:
                performHit(simulator, hit,
                        "Foul Legacy Melee N" + (hit.index + 1)
                                + (MELEE_HIT_FRAMES[hit.index].length > 1
                                        ? "-" + (hit.variant + 1) : ""),
                        meleeNormalValue(hit.index, hit.variant),
                        Element.HYDRO,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0,
                        TriggerKind.MELEE_CONTACT);
                break;
            case MELEE_CHARGED:
                performHit(simulator, hit,
                        "Foul Legacy Melee Charged " + (hit.variant + 1),
                        meleeChargedValue(hit.variant),
                        Element.HYDRO,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        1.0,
                        TriggerKind.MELEE_CONTACT);
                break;
            case RANGED_BURST:
                performHit(simulator, hit,
                        "Havoc: Obliteration Ranged Stance",
                        burstValue(false),
                        Element.HYDRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        2.0,
                        TriggerKind.RANGED_BURST);
                break;
            case MELEE_BURST:
                performHit(simulator, hit,
                        "Havoc: Obliteration Melee Stance",
                        burstValue(true),
                        Element.HYDRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        2.0,
                        TriggerKind.MELEE_BURST);
                break;
            case RIPTIDE_FLASH:
                performHit(simulator, hit,
                        "Riptide Flash " + (hit.variant + 1),
                        getTalentValue("Riptide Flash", 0.210800),
                        Element.HYDRO,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.None,
                        ICDTag.None,
                        0.0,
                        TriggerKind.RIPTIDE_PARTICLE);
                break;
            case RIPTIDE_SLASH:
                performHit(simulator, hit,
                        "Riptide Slash",
                        slashValue(),
                        Element.HYDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        TriggerKind.RIPTIDE_PARTICLE);
                break;
            case RIPTIDE_BLAST:
                performHit(simulator, hit,
                        "Riptide Blast",
                        blastValue(),
                        Element.HYDRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        2.0,
                        TriggerKind.NONE);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Tartaglia hit kind " + hit.kind);
        }
    }

    private void handleAcceptedHit(
            CombatSimulator simulator,
            TriggerKind trigger,
            double hitTime) {
        switch (trigger) {
            case RANGED_CHARGED:
                if (isRiptideActive(hitTime)) {
                    triggerRiptideFlash(simulator, hitTime, true);
                }
                applyRiptide(simulator, hitTime);
                break;
            case MELEE_CONTACT:
                if (isRiptideActive(hitTime)) {
                    triggerRiptideSlash(simulator, hitTime, true);
                }
                break;
            case RANGED_BURST:
                applyRiptide(simulator, hitTime);
                break;
            case MELEE_BURST:
                triggerRiptideBlast(simulator, hitTime);
                break;
            case RIPTIDE_PARTICLE:
                triggerParticle(simulator, hitTime);
                break;
            case NONE:
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Tartaglia trigger " + trigger);
        }
    }

    private void applyRiptide(CombatSimulator simulator, double hitTime) {
        boolean newlyApplied = !isRiptideActive(hitTime);
        riptideExpirationTime = hitTime
                + getTalentValue("Riptide Duration", 10.0)
                + getTalentValue("A1 Riptide Extension", 8.0);
        if (!newlyApplied) {
            return;
        }
        long generation = ++riptideGeneration;
        if (constellation >= 4) {
            queueCommand(simulator, new PendingCommand(
                    hitTime + getTalentValue("C4 Interval", 3.9),
                    CommandKind.C4_TICK,
                    0.0,
                    generation));
        }
    }

    private void triggerRiptideFlash(
            CombatSimulator simulator,
            double hitTime,
            boolean enforceCooldown) {
        if (enforceCooldown && hitTime + EPSILON < nextRiptideFlashTime) {
            return;
        }
        if (enforceCooldown) {
            nextRiptideFlashTime = hitTime + 0.7;
        }
        StatsContainer snapshot = captureLiveStats(hitTime);
        for (int index = 0; index < 3; index++) {
            queueHit(simulator, new PendingHit(
                    hitTime + FRAME,
                    HitKind.RIPTIDE_FLASH,
                    0,
                    index,
                    snapshot));
        }
    }

    private void triggerRiptideSlash(
            CombatSimulator simulator,
            double hitTime,
            boolean enforceCooldown) {
        if (enforceCooldown && hitTime + EPSILON < nextRiptideSlashTime) {
            return;
        }
        if (enforceCooldown) {
            nextRiptideSlashTime = hitTime + 1.5;
        }
        queueHit(simulator, new PendingHit(
                hitTime + FRAME,
                HitKind.RIPTIDE_SLASH,
                0,
                0,
                captureLiveStats(hitTime)));
    }

    private void triggerRiptideBlast(
            CombatSimulator simulator,
            double hitTime) {
        if (!isRiptideActive(hitTime)
                || hitTime + EPSILON < nextRiptideSlashTime) {
            return;
        }
        riptideExpirationTime = Double.NEGATIVE_INFINITY;
        riptideGeneration++;
        queueHit(simulator, new PendingHit(
                hitTime + FRAME,
                HitKind.RIPTIDE_BLAST,
                0,
                0,
                captureLiveStats(hitTime)));
    }

    private void triggerParticle(
            CombatSimulator simulator,
            double hitTime) {
        if (hitTime + EPSILON < nextParticleTime) {
            return;
        }
        nextParticleTime = hitTime
                + getTalentValue("Particle Cooldown", 3.0);
        queueCommand(simulator, new PendingCommand(
                hitTime + getTalentValue("Particle Travel Frames", 80.0)
                        * FRAME,
                CommandKind.PARTICLE,
                1.0,
                0L));
    }

    private void resolveC4Tick(
            CombatSimulator simulator,
            long generation) {
        double currentTime = simulator.getCurrentTime();
        if (generation != riptideGeneration
                || !isRiptideActive(currentTime)) {
            return;
        }
        if (isMeleeStanceActive(currentTime)) {
            triggerRiptideSlash(simulator, currentTime, false);
        } else {
            triggerRiptideFlash(simulator, currentTime, false);
        }
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue("C4 Interval", 3.9),
                CommandKind.C4_TICK,
                0.0,
                generation));
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
            TriggerKind trigger) {
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
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        resolvingAction = action;
        resolvingTrigger = trigger;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingTrigger = TriggerKind.NONE;
        }
    }

    private double stanceValue() {
        return getTalentValue(
                constellation >= 3 ? "Stance Change C3" : "Stance Change",
                constellation >= 3 ? 1.440000 : 1.224000);
    }

    private double meleeNormalValue(int step, int hit) {
        String key = "Melee N" + (step + 1);
        if (MELEE_HIT_FRAMES[step].length > 1) {
            key += "-" + (hit + 1);
        }
        if (constellation >= 3) {
            key += " C3";
        }
        return getTalentValue(key,
                constellation >= 3 ? MELEE_C3[step][hit]
                        : MELEE_T9[step][hit]);
    }

    private double meleeChargedValue(int hit) {
        String key = "Melee Charged " + (hit + 1)
                + (constellation >= 3 ? " C3" : "");
        return getTalentValue(key,
                constellation >= 3 ? MELEE_CHARGED_C3[hit]
                        : MELEE_CHARGED_T9[hit]);
    }

    private double slashValue() {
        return getTalentValue(
                constellation >= 3
                        ? "Riptide Slash C3" : "Riptide Slash",
                constellation >= 3 ? 1.358000 : 1.106000);
    }

    private double burstValue(boolean melee) {
        String key = melee ? "Melee Burst" : "Ranged Burst";
        if (constellation >= 5) {
            key += " C5";
        }
        if (melee) {
            return getTalentValue(key,
                    constellation >= 5 ? 9.280000 : 7.888000);
        }
        return getTalentValue(key,
                constellation >= 5 ? 7.568000 : 6.432800);
    }

    private double blastValue() {
        return getTalentValue(
                constellation >= 5
                        ? "Riptide Blast C5" : "Riptide Blast",
                constellation >= 5 ? 2.400000 : 2.040000);
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
            switch (command.kind) {
                case STANCE_EXPIRE:
                    if (command.generation == stanceGeneration
                            && Math.abs(stanceExpirationTime - command.time)
                                    <= EPSILON) {
                        endMeleeStance(activeSimulator, 0.0);
                    }
                    break;
                case STANCE_COOLDOWN:
                    if (command.generation == stanceGeneration) {
                        startStanceCooldown(activeSimulator, command.value);
                    }
                    break;
                case BURST_COOLDOWN:
                    markBurstCooldownUsed(activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case RANGED_BURST_REFUND:
                    receiveEnergy(command.value);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.HYDRO,
                            (int) command.value,
                            ParticleType.PARTICLE);
                    break;
                case C4_TICK:
                    resolveC4Tick(activeSimulator, command.generation);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Tartaglia command " + command.kind);
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
        RANGED_NORMAL,
        RANGED_CHARGED,
        STANCE_CHANGE,
        MELEE_NORMAL,
        MELEE_CHARGED,
        RANGED_BURST,
        MELEE_BURST,
        RIPTIDE_FLASH,
        RIPTIDE_SLASH,
        RIPTIDE_BLAST
    }

    private enum TriggerKind {
        NONE,
        RANGED_CHARGED,
        MELEE_CONTACT,
        RANGED_BURST,
        MELEE_BURST,
        RIPTIDE_PARTICLE
    }

    private enum CommandKind {
        STANCE_EXPIRE,
        STANCE_COOLDOWN,
        BURST_COOLDOWN,
        BURST_ENERGY,
        RANGED_BURST_REFUND,
        PARTICLE,
        C4_TICK
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, variant, snapshot);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final double value;
        private final long generation;

        private PendingCommand(
                double time,
                CommandKind kind,
                double value,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.value = value;
            this.generation = generation;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value, generation);
        }
    }

    private static final class TartagliaState implements State {
        private final Tartaglia owner;
        private final int normalAttackStep;
        private final long stanceGeneration;
        private final long riptideGeneration;
        private final double stanceStartTime;
        private final double stanceExpirationTime;
        private final double riptideExpirationTime;
        private final double nextRiptideFlashTime;
        private final double nextRiptideSlashTime;
        private final double nextParticleTime;
        private final boolean meleeBurstUsed;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private TartagliaState(
                Tartaglia owner,
                int normalAttackStep,
                long stanceGeneration,
                long riptideGeneration,
                double stanceStartTime,
                double stanceExpirationTime,
                double riptideExpirationTime,
                double nextRiptideFlashTime,
                double nextRiptideSlashTime,
                double nextParticleTime,
                boolean meleeBurstUsed,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.stanceGeneration = stanceGeneration;
            this.riptideGeneration = riptideGeneration;
            this.stanceStartTime = stanceStartTime;
            this.stanceExpirationTime = stanceExpirationTime;
            this.riptideExpirationTime = riptideExpirationTime;
            this.nextRiptideFlashTime = nextRiptideFlashTime;
            this.nextRiptideSlashTime = nextRiptideSlashTime;
            this.nextParticleTime = nextParticleTime;
            this.meleeBurstUsed = meleeBurstUsed;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
