package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
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
 * Chevreuse's fixed-target offensive and Overload-support slice through C5.
 *
 * <p>Four physical Normals, Press and minimum-Hold Skill, Overcharged Ball,
 * particles, Ousia-aligned Surging Blade, Burst initial damage, A1/A4, and
 * representable C1-C5 behavior follow pinned gcsim {@code ef41805d}. Mutable
 * reaction gates, delayed hits, cooldown exceptions, and snapshots are bound
 * to one character and one simulator.</p>
 *
 * <p>Healing and current HP, C6 healing-derived bonuses, stamina, secondary
 * Burst shell geometry, C2 placement, hitlag extension, and non-minimum Hold
 * charge time are excluded rather than represented by invented state.</p>
 */
public final class Chevreuse extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 11 }, { 12 }, { 15, 25 }, { 33 }
    };
    private static final int[] NORMAL_DURATIONS = { 33, 33, 46, 67 };
    private static final double[][] NORMAL_T9 = {
        { 0.976108 }, { 0.905940 }, { 0.507895, 0.596225 },
        { 1.419456 }
    };

    private final DoubleSupplier c2Random;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private boolean overchargedBall;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private double nextArkheTime = Double.NEGATIVE_INFINITY;
    private double nextC1Time = Double.NEGATIVE_INFINITY;
    private double nextC2Time = Double.NEGATIVE_INFINITY;
    private double c4ExpirationTime = Double.NEGATIVE_INFINITY;
    private int c4ShotsRemaining;
    private long nextHitId;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Chevreuse. */
    public Chevreuse(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Chevreuse at an explicit constellation. */
    public Chevreuse(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Chevreuse with injectable talent data and C2 timing draws.
     *
     * @param weapon equipped polearm, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData character data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param c2Random source of draws in {@code [0, 1)} for C2 delays
     */
    public Chevreuse(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier c2Random) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Chevreuse constellation must be between 0 and 6");
        }
        if (c2Random == null) {
            throw new IllegalArgumentException(
                    "Chevreuse C2 random source is required");
        }
        name = "Chevreuse";
        characterId = CharacterId.CHEVREUSE;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.c2Random = c2Random;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11962.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 193.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 605.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds reaction observation and delayed work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Chevreuse simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Chevreuse must belong to the simulator party");
        }
        if (initializedSimulator != null) {
            if (initializedSimulator != simulator) {
                throw new IllegalStateException(
                        "Chevreuse cannot be reused across simulators");
            }
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this::handleReaction);
    }

    /** Captures combo, reaction gates, C4, and delayed-work state. */
    @Override
    public State captureCharacterState() {
        return new ChevreuseState(
                this,
                normalAttackStep,
                overchargedBall,
                nextParticleTime,
                nextArkheTime,
                nextC1Time,
                nextC2Time,
                c4ExpirationTime,
                c4ShotsRemaining,
                nextHitId,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Chevreuse instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof ChevreuseState
                && ((ChevreuseState) state).owner == this;
    }

    /** Restores surviving delayed work exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Chevreuse state");
        }
        initializeForSimulator(simulator);
        ChevreuseState restored = (ChevreuseState) state;
        normalAttackStep = restored.normalAttackStep;
        overchargedBall = restored.overchargedBall;
        nextParticleTime = restored.nextParticleTime;
        nextArkheTime = restored.nextArkheTime;
        nextC1Time = restored.nextC1Time;
        nextC2Time = restored.nextC2Time;
        c4ExpirationTime = restored.c4ExpirationTime;
        c4ShotsRemaining = restored.c4ShotsRemaining;
        nextHitId = restored.nextHitId;
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

    /** Returns Chevreuse's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Chevreuse has no unconditional represented owner stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets the Normal string when Chevreuse leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Accepts both Press and the represented minimum-Hold Skill. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS
                || mode == SkillActionMode.HOLD;
    }

    /** Returns whether an Overcharged Ball is currently held. */
    public boolean hasOverchargedBall() {
        return overchargedBall;
    }

    /** Returns current unspent C4 no-cooldown Hold shots. */
    public int getC4ShotsRemaining(double currentTime) {
        return isC4Active(currentTime) ? c4ShotsRemaining : 0;
    }

    /** Returns the number of unresolved Chevreuse-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Chevreuse's represented action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Chevreuse action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case SKILL:
                shortRangeRapidInterdictionFire(
                        simulator, request.getSkillMode());
                break;
            case BURST:
                ringOfBurstingGrenades(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Chevreuse: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    nextHitId++,
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    HitKind.NORMAL,
                    step,
                    hit,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void shortRangeRapidInterdictionFire(
            CombatSimulator simulator,
            SkillActionMode mode) {
        double castTime = simulator.getCurrentTime();
        boolean hold = mode == SkillActionMode.HOLD;
        boolean overcharged = hold && overchargedBall;
        if (overcharged) {
            overchargedBall = false;
        }

        boolean c4FreeShot = hold && isC4Active(castTime);
        if (c4FreeShot) {
            c4ShotsRemaining--;
        } else {
            int cooldownStartFrame = hold ? 13 : 18;
            queueCommand(simulator, new PendingCommand(
                    castTime + cooldownStartFrame * FRAME,
                    CommandKind.SKILL_COOLDOWN,
                    getTalentValue("Skill Cooldown", 15.0),
                    -1L));
        }
        if (overcharged) {
            queueCommand(simulator, new PendingCommand(
                    castTime + 13.0 * FRAME,
                    CommandKind.A4_BUFF,
                    0.0,
                    -1L));
        }

        HitKind kind = overcharged
                ? HitKind.SKILL_OVERCHARGED
                : hold ? HitKind.SKILL_HOLD : HitKind.SKILL_PRESS;
        int impactFrame = hold ? 19 : 25;
        queueHit(simulator, new PendingHit(
                nextHitId++,
                castTime + impactFrame * FRAME,
                kind,
                0,
                0,
                null));
        simulator.advanceTime((hold ? 26.0 : 31.0) * FRAME);
    }

    private void ringOfBurstingGrenades(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(castTime,
                simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0,
                -1L));
        if (constellation >= 4) {
            c4ExpirationTime = castTime + getTalentValue(
                    "C4 Duration", 6.0);
            c4ShotsRemaining = (int) getTalentValue(
                    "C4 Free Hold Shots", 2.0);
        }
        PendingHit burst = new PendingHit(
                nextHitId++,
                castTime + 59.0 * FRAME,
                HitKind.BURST,
                0,
                0,
                null);
        queueHit(simulator, burst);
        queueCommand(simulator, new PendingCommand(
                castTime + 43.0 * FRAME,
                CommandKind.CAPTURE_HIT,
                0.0,
                burst.id));
        simulator.advanceTime(61.0 * FRAME);
    }

    private void handleReaction(
            ReactionResult result,
            Character trigger,
            double time,
            CombatSimulator simulator) {
        if (!isOverload(result)) {
            return;
        }
        overchargedBall = true;
        if (!hasCoordinatedTacticsParty(simulator)) {
            return;
        }
        double shred = getTalentValue("A1 RES Shred", 0.40);
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Chevreuse Coordinated Tactics",
                BuffId.CHEVREUSE_A1_COORDINATED_TACTICS,
                getTalentValue("A1 Duration", 6.0),
                time,
                stats -> {
                    stats.add(StatType.PYRO_RES_SHRED, shred);
                    stats.add(StatType.ELECTRO_RES_SHRED, shred);
                }).sourcedBy(characterId));

        if (constellation >= 1
                && trigger != null
                && trigger != this
                && trigger == simulator.getActiveCharacter()
                && time + EPSILON >= nextC1Time) {
            trigger.receiveFlatEnergy(getTalentValue("C1 Energy", 6.0));
            nextC1Time = time + getTalentValue("C1 ICD", 10.0);
        }
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Line Bayonet Thrust EX N" + (hit.index + 1)
                                + (NORMAL_HIT_FRAMES[hit.index].length > 1
                                        ? " Hit " + (hit.variant + 1) : ""),
                        normalValue(hit.index, hit.variant),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0);
                break;
            case SKILL_PRESS:
            case SKILL_HOLD:
            case SKILL_OVERCHARGED:
                resolveSkillHit(simulator, hit);
                break;
            case ARKHE:
                performHit(
                        simulator,
                        hit,
                        "Surging Blade",
                        skillValue("Surging Blade DMG", 0.489600, 0.576000),
                        Element.PYRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        0.0);
                break;
            case C2:
                performHit(
                        simulator,
                        hit,
                        "Sniper Induced Explosion " + (hit.index + 1),
                        getTalentValue(
                                "Sniper Induced Explosion C2", 1.20),
                        Element.PYRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        1.0);
                break;
            case BURST:
                performHit(
                        simulator,
                        hit,
                        "Explosive Grenade",
                        burstValue(6.258720, 7.363200),
                        Element.PYRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        2.0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Chevreuse hit kind");
        }
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            PendingHit hit) {
        boolean hold = hit.kind != HitKind.SKILL_PRESS;
        String key;
        String displayName;
        double talentNine;
        double talentTwelve;
        if (hit.kind == HitKind.SKILL_OVERCHARGED) {
            key = "Overcharged Ball DMG";
            displayName = "Short-Range Rapid Interdiction Fire [Overcharged]";
            talentNine = 4.800800;
            talentTwelve = 5.648000;
        } else if (hold) {
            key = "Hold DMG";
            displayName = "Short-Range Rapid Interdiction Fire [Hold]";
            talentNine = 2.937600;
            talentTwelve = 3.456000;
        } else {
            key = "Press DMG";
            displayName = "Short-Range Rapid Interdiction Fire";
            talentNine = 1.958400;
            talentTwelve = 2.304000;
        }
        performHit(
                simulator,
                hit,
                displayName,
                skillValue(key, talentNine, talentTwelve),
                Element.PYRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0);

        if (hit.time + EPSILON >= nextParticleTime) {
            nextParticleTime = hit.time
                    + getTalentValue("Particle ICD", 10.0);
            queueCommand(simulator, new PendingCommand(
                    hit.time + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    getTalentValue("Particle Count", 4.0),
                    -1L));
        }
        if (hit.time + EPSILON >= nextArkheTime) {
            nextArkheTime = hit.time + getTalentValue("Arkhe ICD", 10.0);
            queueHit(simulator, new PendingHit(
                    nextHitId++,
                    hit.time + (hold ? 36.0 : 34.0) * FRAME,
                    HitKind.ARKHE,
                    0,
                    0,
                    null));
        }
        if (hold
                && constellation >= 2
                && hit.time + EPSILON >= nextC2Time) {
            nextC2Time = hit.time + getTalentValue("C2 ICD", 10.0);
            for (int explosion = 0; explosion < 2; explosion++) {
                double delay = 0.6 + 0.4 * nextC2RandomDraw();
                queueHit(simulator, new PendingHit(
                        nextHitId++,
                        hit.time + delay,
                        HitKind.C2,
                        explosion,
                        0,
                        null));
            }
        }
    }

    private void applyA4(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        double maxHp = captureLiveStats(currentTime).getTotalHp();
        double amount = Math.min(
                getTalentValue("A4 ATK Cap", 0.40),
                Math.max(0.0, maxHp) / 1000.0 * 0.01);
        simulator.applyTeamBuffNoStack(new PyroElectroAtkBuff(
                getTalentValue("A4 Duration", 30.0),
                currentTime,
                amount).sourcedByChevreuse(characterId));
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
            double gauge) {
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
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private double skillValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(key + suffix,
                constellation >= 5 ? talentTwelve : talentNine);
    }

    private double normalValue(int step, int variant) {
        String key = "N" + (step + 1);
        if (NORMAL_HIT_FRAMES[step].length > 1) {
            key += " Hit " + (variant + 1);
        }
        return getTalentValue(key, NORMAL_T9[step][variant]);
    }

    private double burstValue(
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue("Explosive Grenade" + suffix,
                constellation >= 3 ? talentTwelve : talentNine);
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

    private boolean hasCoordinatedTacticsParty(
            CombatSimulator simulator) {
        boolean pyro = false;
        boolean electro = false;
        for (Character member : simulator.getPartyMembers()) {
            if (member.getElement() == Element.PYRO) {
                pyro = true;
            } else if (member.getElement() == Element.ELECTRO) {
                electro = true;
            } else {
                return false;
            }
        }
        return pyro && electro;
    }

    private boolean isC4Active(double currentTime) {
        return constellation >= 4
                && c4ShotsRemaining > 0
                && currentTime < c4ExpirationTime;
    }

    private double nextC2RandomDraw() {
        double draw = c2Random.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Chevreuse C2 random draw must be in [0, 1)");
        }
        return draw;
    }

    private static boolean isOverload(ReactionResult result) {
        if (result == null) {
            return false;
        }
        return result.getKind() == ReactionResult.Kind.OVERLOAD
                || result.getKind() == ReactionResult.Kind.OVERLOADED;
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
                case SKILL_COOLDOWN:
                    setSkillCD(command.value);
                    markSkillUsed(activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.PYRO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                case A4_BUFF:
                    applyA4(activeSimulator);
                    break;
                case CAPTURE_HIT:
                    capturePendingHit(command.targetHitId,
                            activeSimulator.getCurrentTime());
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Chevreuse command kind");
            }
        });
    }

    private void capturePendingHit(long hitId, double currentTime) {
        for (PendingHit hit : pendingHits) {
            if (hit.id == hitId) {
                hit.snapshot = captureLiveStats(currentTime);
                return;
            }
        }
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
        SKILL_PRESS,
        SKILL_HOLD,
        SKILL_OVERCHARGED,
        ARKHE,
        C2,
        BURST
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE,
        A4_BUFF,
        CAPTURE_HIT
    }

    /** Immutable hit identity with an optional frame-43 Burst snapshot. */
    private static final class PendingHit {
        private final long id;
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private StatsContainer snapshot;

        private PendingHit(
                long id,
                double time,
                HitKind kind,
                int index,
                int variant,
                StatsContainer snapshot) {
            this.id = id;
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(id, time, kind, index, variant, snapshot);
        }
    }

    /** Reconstructable non-damage work scheduled on the simulator clock. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final double value;
        private final long targetHitId;

        private PendingCommand(
                double time,
                CommandKind kind,
                double value,
                long targetHitId) {
            this.time = time;
            this.kind = kind;
            this.value = value;
            this.targetHitId = targetHitId;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value, targetHitId);
        }
    }

    /** Immutable owner-bound snapshot payload for Chevreuse-specific state. */
    private static final class ChevreuseState implements State {
        private final Chevreuse owner;
        private final int normalAttackStep;
        private final boolean overchargedBall;
        private final double nextParticleTime;
        private final double nextArkheTime;
        private final double nextC1Time;
        private final double nextC2Time;
        private final double c4ExpirationTime;
        private final int c4ShotsRemaining;
        private final long nextHitId;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private ChevreuseState(
                Chevreuse owner,
                int normalAttackStep,
                boolean overchargedBall,
                double nextParticleTime,
                double nextArkheTime,
                double nextC1Time,
                double nextC2Time,
                double c4ExpirationTime,
                int c4ShotsRemaining,
                long nextHitId,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.overchargedBall = overchargedBall;
            this.nextParticleTime = nextParticleTime;
            this.nextArkheTime = nextArkheTime;
            this.nextC1Time = nextC1Time;
            this.nextC2Time = nextC2Time;
            this.c4ExpirationTime = c4ExpirationTime;
            this.c4ShotsRemaining = c4ShotsRemaining;
            this.nextHitId = nextHitId;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }

    /** A4 buff whose recipient filter accepts only Pyro and Electro members. */
    private static final class PyroElectroAtkBuff extends SimpleBuff {
        private PyroElectroAtkBuff(
                double duration,
                double currentTime,
                double amount) {
            super(
                    "Chevreuse Vertical Force Coordination",
                    BuffId.CHEVREUSE_A4_VERTICAL_FORCE_COORDINATION,
                    duration,
                    currentTime,
                    stats -> stats.add(StatType.ATK_PERCENT, amount));
        }

        private PyroElectroAtkBuff sourcedByChevreuse(
                CharacterId source) {
            sourcedBy(source);
            return this;
        }

        @Override
        public boolean appliesToCharacter(Character character) {
            return character != null
                    && (character.getElement() == Element.PYRO
                            || character.getElement() == Element.ELECTRO);
        }
    }
}
