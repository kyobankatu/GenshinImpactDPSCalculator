package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.ReactionAwareCharacter;
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
 * Navia's fixed-target Crystal Shrapnel offensive slice through C6.
 *
 * <p>Blunt Refusal, Press Ceremonial Crystalshot, Crystal Shrapnel,
 * Surging Blade, Rosula Dorata Salute, A1/A4, and the supportable C1-C6
 * branches follow pinned gcsim {@code ef41805d}. The fixed target is assumed
 * to receive every emitted Shardshot pellet, preserving the source-defined
 * hit-count multiplier without inventing projectile geometry.</p>
 *
 * <p>Standard Crystallize grants a stack only through the explicit shard
 * pickup hook because the runtime has no typed pickup event. Hold aiming,
 * shard suction and pickup geometry, shields, player HP and healing,
 * movement, multi-target behavior, stamina, and unsupported enemy
 * state branches are excluded rather than approximated.</p>
 */
public final class Navia extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        ReactionAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_DURATIONS = { 28, 42, 48, 93 };
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 23 }, { 22 }, { 31, 39, 48 }, { 41 }
    };
    private static final double[][] NORMAL_T9 = {
        { 1.718139 },
        { 1.589306 },
        { 0.640927, 0.640927, 0.640927 },
        { 2.451417 }
    };
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[][] NORMAL_HITLAG = {
        { new HitlagProfile(0.06, 0.01, true, false, false) },
        { new HitlagProfile(0.06, 0.01, true, false, false) },
        {
            new HitlagProfile(0.01, 0.01, false, true, false),
            new HitlagProfile(0.01, 0.01, false, true, false),
            new HitlagProfile(0.01, 0.01, false, true, false)
        },
        { new HitlagProfile(0.06, 0.01, true, false, false) }
    };
    private static final double[] SHARDSHOT_HIT_FACTORS = {
        1.2000000029802322,
        1.4000000059604645,
        1.6660000085830688,
        2.0
    };

    private final DoubleSupplier particleRandom;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int crystalShrapnel;
    private long burstGeneration;
    private double a1ExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextBurstShrapnelAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextSurgingBladeAllowedTime = Double.NEGATIVE_INFINITY;
    private Buff c4ResistanceBuff;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Navia. */
    public Navia(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Navia at an explicit constellation. */
    public Navia(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Navia with injectable talent data and particle randomness.
     *
     * @param weapon equipped claymore, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param particleRandom particle draw source in {@code [0, 1)}
     */
    public Navia(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleRandom) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Navia constellation must be between 0 and 6");
        }
        if (particleRandom == null) {
            throw new IllegalArgumentException(
                    "Navia particle random source is required");
        }
        name = "Navia";
        characterId = CharacterId.NAVIA;
        element = Element.GEO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleRandom = particleRandom;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12650.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 352.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 793.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 9.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
        setSkillMaxCharges(2);
    }

    /** Binds reaction observation and delayed work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Navia simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Navia must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Navia cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
    }

    /** Captures Shrapnel, timing gates, buffs, combo, and future work. */
    @Override
    public State captureCharacterState() {
        return new NaviaState(
                this,
                normalAttackStep,
                crystalShrapnel,
                burstGeneration,
                a1ExpirationTime,
                nextBurstShrapnelAllowedTime,
                nextSurgingBladeAllowedTime,
                c4ResistanceBuff,
                pendingEvents);
    }

    /** Accepts state captured from this exact Navia instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof NaviaState
                && ((NaviaState) state).owner == this;
    }

    /** Restores Navia state and schedules every surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Navia state");
        }
        initializeForSimulator(simulator);
        NaviaState restored = (NaviaState) state;
        normalAttackStep = restored.normalAttackStep;
        crystalShrapnel = restored.crystalShrapnel;
        burstGeneration = restored.burstGeneration;
        a1ExpirationTime = restored.a1ExpirationTime;
        nextBurstShrapnelAllowedTime =
                restored.nextBurstShrapnelAllowedTime;
        nextSurgingBladeAllowedTime =
                restored.nextSurgingBladeAllowedTime;
        c4ResistanceBuff = restored.c4ResistanceBuff;
        pendingEvents = copyEvents(restored.pendingEvents);
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            scheduleEvent(simulator, event);
        }
    }

    /** Returns Navia's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Applies A4 from up to two Pyro, Electro, Cryo, or Hydro allies. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (initializedSimulator == null) {
            return;
        }
        int eligible = 0;
        for (Character member : initializedSimulator.getPartyMembers()) {
            Element memberElement = member.getElement();
            if (memberElement == Element.PYRO
                    || memberElement == Element.ELECTRO
                    || memberElement == Element.CRYO
                    || memberElement == Element.HYDRO) {
                eligible++;
            }
        }
        int cap = (int) getTalentValue(
                "A4 Eligible Member Cap", 2.0);
        stats.add(
                StatType.ATK_PERCENT,
                Math.min(cap, eligible) * getTalentValue(
                        "A4 ATK Per Eligible Member", 0.20));
    }

    /** Resets only Navia's on-field Normal sequence. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the current Crystal Shrapnel count. */
    public int getCrystalShrapnelCount() {
        return crystalShrapnel;
    }

    /** Returns whether A1's Geo infusion is active at a half-open boundary. */
    public boolean isA1InfusionActive(double currentTime) {
        return currentTime + EPSILON < a1ExpirationTime;
    }

    /** Returns the number of unresolved Navia-owned delayed events. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /**
     * Records an externally confirmed standard Crystallize shard pickup.
     *
     * <p>The caller must have resolved pickup validity. A plain Crystallize
     * reaction does not call this method because it only creates a shard.</p>
     *
     * @param simulator bound simulator at pickup time
     * @return {@code true} when one stack was added
     */
    public boolean notifyCrystallizeShardObtained(
            CombatSimulator simulator) {
        initializeForSimulator(simulator);
        return gainCrystalShrapnel();
    }

    /** Gains a stack directly from a supported Lunar-Crystallize trigger. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || result == null
                || result.getKind()
                        != ReactionResult.Kind.LUNAR_CRYSTALLIZE
                || source == null
                || !simulator.getPartyMembers().contains(source)) {
            return;
        }
        gainCrystalShrapnel();
    }

    /** Dispatches Navia's represented typed actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Navia action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case PLUNGE:
                highPlunge(simulator);
                break;
            case SKILL:
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Navia Hold Skill is outside this slice");
                }
                ceremonialCrystalshot(simulator);
                break;
            case BURST:
                sunlitSkySalute(simulator);
                break;
            case CHARGE:
                throw new IllegalArgumentException(
                        "Navia Charged Attack is absent from the pinned source slice");
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Navia: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        int step = normalAttackStep;
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        boolean infused = isA1InfusionActive(castTime);
        double a1Bonus = infused
                ? getTalentValue("A1 Basic DMG Bonus", 0.40) : 0.0;
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    EventKind.NORMAL_HIT,
                    step,
                    hit,
                    0L,
                    snapshot,
                    a1Bonus,
                    infused));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean infused = isA1InfusionActive(castTime);
        queueEvent(simulator, new PendingEvent(
                castTime + 40.0 * FRAME,
                EventKind.PLUNGE_HIT,
                0,
                0,
                0L,
                captureLiveStats(castTime),
                infused
                        ? getTalentValue("A1 Basic DMG Bonus", 0.40)
                        : 0.0,
                infused));
        simulator.advanceTime(83.0 * FRAME);
    }

    private void ceremonialCrystalshot(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        double particleCount = simulator.getEnemy() == null
                ? 0.0 : particleCount();
        queueEvent(simulator, new PendingEvent(
                castTime + 11.0 * FRAME,
                EventKind.SKILL_FIRE,
                0,
                0,
                0L,
                null,
                particleCount,
                false));
        int duration = crystalShrapnel >= 3 ? 41 : 40;
        simulator.advanceTime(duration * FRAME);
    }

    private void fireCrystalshot(
            CombatSimulator simulator,
            PendingEvent event) {
        double firingTime = event.time;
        int shrapnelAtFire = crystalShrapnel;
        StatsContainer snapshot = captureLiveStats(firingTime);
        markSkillUsed(
                firingTime, simulator.getApplicableBuffs(this));
        a1ExpirationTime = firingTime
                + getTalentValue("A1 Duration", 4.0);
        if (constellation >= 1) {
            int c1Stacks = Math.min(
                    (int) getTalentValue("C1 Stack Cap", 3.0),
                    shrapnelAtFire);
            receiveFlatEnergy(c1Stacks
                    * getTalentValue("C1 Energy Per Stack", 3.0));
            reduceBurstCooldown(firingTime, c1Stacks);
        }
        queueEvent(simulator, new PendingEvent(
                firingTime + 9.0 * FRAME,
                EventKind.SKILL_HIT,
                shrapnelAtFire,
                0,
                0L,
                snapshot,
                event.value,
                false));
        double alignedStart = firingTime + 28.0 * FRAME;
        if (alignedStart + EPSILON >= nextSurgingBladeAllowedTime) {
            nextSurgingBladeAllowedTime = alignedStart
                    + getTalentValue("Surging Blade Cooldown", 7.0);
            queueEvent(simulator, new PendingEvent(
                    firingTime + 64.0 * FRAME,
                    EventKind.SURGING_BLADE,
                    shrapnelAtFire,
                    0,
                    0L,
                    snapshot,
                    0.0,
                    false));
        }
        crystalShrapnel = constellation >= 6
                ? Math.max(0, shrapnelAtFire
                        - (int) getTalentValue(
                                "C6 Retained Stack Threshold", 3.0))
                : 0;
    }

    private void sunlitSkySalute(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueEvent(simulator, new PendingEvent(
                castTime + 12.0 * FRAME,
                EventKind.BURST_ENERGY,
                0,
                0,
                generation,
                null,
                0.0,
                false));
        queueEvent(simulator, new PendingEvent(
                castTime + 104.0 * FRAME,
                EventKind.BURST_INITIAL,
                0,
                0,
                generation,
                captureLiveStats(castTime),
                0.0,
                false));
        int tick = 0;
        for (int offset = 0; offset <= 720;) {
            queueEvent(simulator, new PendingEvent(
                    castTime + (154.0 + offset + 9.0) * FRAME,
                    EventKind.BURST_SUPPORT,
                    tick,
                    0,
                    generation,
                    null,
                    0.0,
                    false));
            tick++;
            offset += tick % 3 == 2 ? 48 : 42;
        }
        simulator.advanceTime(127.0 * FRAME);
    }

    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.kind) {
            case NORMAL_HIT:
                resolveNormalHit(simulator, event);
                break;
            case PLUNGE_HIT:
                performHit(
                        simulator,
                        event,
                        "Blunt Refusal High Plunge",
                        getTalentValue("High Plunge", 3.422517),
                        event.infused ? Element.GEO : Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        event.infused ? 1.0 : 0.0,
                        event.value,
                        0.0,
                        0.0);
                break;
            case SKILL_FIRE:
                fireCrystalshot(simulator, event);
                break;
            case SKILL_HIT:
                resolveSkillHit(simulator, event);
                break;
            case SURGING_BLADE:
                resolveSurgingBlade(simulator, event);
                break;
            case C2_SUPPORT:
                resolveBurstDamage(simulator, event, true);
                break;
            case BURST_ENERGY:
                if (event.generation == burstGeneration) {
                    spendBurstEnergy(event.time);
                }
                break;
            case BURST_INITIAL:
                if (event.generation == burstGeneration) {
                    resolveBurstDamage(simulator, event, false);
                }
                break;
            case BURST_SUPPORT:
                if (event.generation == burstGeneration) {
                    resolveBurstDamage(simulator, event, true);
                }
                break;
            case PARTICLE:
                simulator.getEnergyDistributor().distributeParticles(
                        Element.GEO, event.value, ParticleType.PARTICLE);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Navia event " + event.kind);
        }
    }

    private void resolveNormalHit(
            CombatSimulator simulator,
            PendingEvent event) {
        String suffix = NORMAL_T9[event.index].length > 1
                ? " Hit " + (event.subIndex + 1) : "";
        performHit(
                simulator,
                event,
                "Blunt Refusal N" + (event.index + 1) + suffix,
                getTalentValue(
                        "N" + (event.index + 1)
                                + (NORMAL_T9[event.index].length > 1
                                        ? "-" + (event.subIndex + 1) : ""),
                        NORMAL_T9[event.index][event.subIndex]),
                event.infused ? Element.GEO : Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                event.infused ? 1.0 : 0.0,
                event.value,
                0.0,
                0.0);
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            PendingEvent event) {
        int countedStacks = Math.min(3, Math.max(0, event.index));
        double base = getTalentValue(
                constellation >= 3
                        ? "Rosula Shardshot Base C3"
                        : "Rosula Shardshot Base",
                constellation >= 3 ? 7.896000 : 6.711600);
        performHit(
                simulator,
                event,
                "Ceremonial Crystalshot: Rosula Shardshot",
                base * SHARDSHOT_HIT_FACTORS[countedStacks],
                Element.GEO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0,
                excessDamageBonus(event.index),
                c2CritRate(event.index),
                c6CritDamage(event.index));
        if (event.value > 0.0) {
            queueEvent(simulator, new PendingEvent(
                    event.time
                            + getTalentValue(
                                    "Particle Travel Frames", 100.0)
                                    * FRAME,
                    EventKind.PARTICLE,
                    0,
                    0,
                    0L,
                    null,
                    event.value,
                    false));
        }
        if (constellation >= 2 && simulator.getEnemy() != null) {
            queueEvent(simulator, new PendingEvent(
                    event.time
                            + getTalentValue(
                                    "C2 Support Fire Delay Frames", 30.0)
                                    * FRAME,
                    EventKind.C2_SUPPORT,
                    0,
                    0,
                    0L,
                    captureLiveStats(event.time),
                    0.0,
                    false));
        }
    }

    private void resolveSurgingBlade(
            CombatSimulator simulator,
            PendingEvent event) {
        performHit(
                simulator,
                event,
                "Ceremonial Crystalshot: Surging Blade",
                getTalentValue(
                        constellation >= 3
                                ? "Surging Blade C3" : "Surging Blade",
                        constellation >= 3 ? 0.720000 : 0.612000),
                Element.GEO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                0.0,
                excessDamageBonus(event.index),
                c2CritRate(event.index),
                c6CritDamage(event.index));
    }

    private void resolveBurstDamage(
            CombatSimulator simulator,
            PendingEvent event,
            boolean support) {
        StatsContainer snapshot = event.snapshot == null
                ? captureLiveStats(event.time) : event.snapshot;
        PendingEvent resolved = event.withSnapshot(snapshot);
        performHit(
                simulator,
                resolved,
                support
                        ? (event.kind == EventKind.C2_SUPPORT
                                ? "Cannon Fire Support (C2)"
                                : "Cannon Fire Support")
                        : "As the Sunlit Sky's Singing Salute",
                getTalentValue(
                        support
                                ? (constellation >= 5
                                        ? "Cannon Fire Support C5"
                                        : "Cannon Fire Support")
                                : (constellation >= 5
                                        ? "Sunlit Sky Initial C5"
                                        : "Sunlit Sky Initial"),
                        support
                                ? (constellation >= 5
                                        ? 0.863000 : 0.733550)
                                : (constellation >= 5
                                        ? 1.504000 : 1.278400)),
                Element.GEO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                support ? ICDType.Standard : ICDType.None,
                support ? ICDTag.ElementalBurst : ICDTag.None,
                support ? 1.0 : 2.0,
                0.0,
                0.0,
                0.0);
        if (simulator.getEnemy() != null) {
            gainBurstShrapnel(event.time);
            applyC4(simulator, event.time);
        }
    }

    private void performHit(
            CombatSimulator simulator,
            PendingEvent event,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            double damageBonus,
            double critRate,
            double critDamage) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        if (event.kind == EventKind.NORMAL_HIT) {
            action.setHitlagProfile(
                    NORMAL_HITLAG[event.index][event.subIndex]);
        }
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setShatterTrigger(true);
        if (damageBonus != 0.0) {
            action.addBonusStat(StatType.DMG_BONUS_ALL, damageBonus);
        }
        if (critRate != 0.0) {
            action.addBonusStat(StatType.CRIT_RATE, critRate);
        }
        if (critDamage != 0.0) {
            action.addBonusStat(StatType.CRIT_DMG, critDamage);
        }
        if (event.snapshot != null) {
            action.setStatSnapshot(event.snapshot);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void gainBurstShrapnel(double time) {
        if (time + EPSILON < nextBurstShrapnelAllowedTime) {
            return;
        }
        nextBurstShrapnelAllowedTime = time
                + getTalentValue("Burst Shrapnel Cooldown", 2.4);
        gainCrystalShrapnel();
    }

    private boolean gainCrystalShrapnel() {
        int cap = (int) getTalentValue("Crystal Shrapnel Cap", 6.0);
        if (crystalShrapnel >= cap) {
            return false;
        }
        crystalShrapnel++;
        return true;
    }

    private void applyC4(
            CombatSimulator simulator,
            double currentTime) {
        if (constellation < 4) {
            return;
        }
        if (c4ResistanceBuff != null) {
            simulator.getTeamBuffList().remove(c4ResistanceBuff);
        }
        c4ResistanceBuff = new SimpleBuff(
                "Navia The Oathsworn Never Capitulate",
                getTalentValue("C4 Duration", 8.0),
                currentTime,
                stats -> stats.add(
                        StatType.GEO_RES_SHRED,
                        getTalentValue("C4 Geo RES Shred", 0.20)))
                .sourcedBy(characterId);
        simulator.applyTeamBuff(c4ResistanceBuff);
    }

    private double excessDamageBonus(int stacks) {
        return Math.max(0, stacks - 3)
                * getTalentValue("Skill Excess DMG Bonus", 0.15);
    }

    private double c2CritRate(int stacks) {
        if (constellation < 2) {
            return 0.0;
        }
        return Math.min(
                (int) getTalentValue("C2 Stack Cap", 3.0),
                Math.max(0, stacks))
                * getTalentValue("C2 CRIT Rate Per Stack", 0.12);
    }

    private double c6CritDamage(int stacks) {
        if (constellation < 6) {
            return 0.0;
        }
        return Math.max(0, stacks - 3)
                * getTalentValue("C6 CRIT DMG Per Excess Stack", 0.45);
    }

    private double particleCount() {
        double draw = particleRandom.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Navia particle random draw must be in [0, 1)");
        }
        return draw < getTalentValue("Particle Chance Four", 0.50)
                ? 4.0 : 3.0;
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff
                    : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        return stats;
    }

    private void queueEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        pendingEvents.add(event);
        scheduleEvent(simulator, event);
    }

    private void scheduleEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        schedule(simulator, event.time, activeSimulator -> {
            if (!pendingEvents.remove(event)) {
                return;
            }
            resolveEvent(activeSimulator, event);
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

    private static List<PendingEvent> copyEvents(
            List<PendingEvent> source) {
        List<PendingEvent> copy = new ArrayList<>();
        for (PendingEvent event : source) {
            copy.add(event.copy());
        }
        return copy;
    }

    private enum EventKind {
        NORMAL_HIT,
        PLUNGE_HIT,
        SKILL_FIRE,
        SKILL_HIT,
        SURGING_BLADE,
        C2_SUPPORT,
        BURST_ENERGY,
        BURST_INITIAL,
        BURST_SUPPORT,
        PARTICLE
    }

    /** Immutable reconstructable Navia-owned event. */
    private static final class PendingEvent {
        private final double time;
        private final EventKind kind;
        private final int index;
        private final int subIndex;
        private final long generation;
        private final StatsContainer snapshot;
        private final double value;
        private final boolean infused;

        private PendingEvent(
                double time,
                EventKind kind,
                int index,
                int subIndex,
                long generation,
                StatsContainer snapshot,
                double value,
                boolean infused) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.value = value;
            this.infused = infused;
        }

        private PendingEvent withSnapshot(StatsContainer requestedSnapshot) {
            return new PendingEvent(
                    time,
                    kind,
                    index,
                    subIndex,
                    generation,
                    requestedSnapshot,
                    value,
                    infused);
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time,
                    kind,
                    index,
                    subIndex,
                    generation,
                    snapshot,
                    value,
                    infused);
        }
    }

    /** Immutable snapshot of all mutable Navia-owned runtime state. */
    private static final class NaviaState implements State {
        private final Navia owner;
        private final int normalAttackStep;
        private final int crystalShrapnel;
        private final long burstGeneration;
        private final double a1ExpirationTime;
        private final double nextBurstShrapnelAllowedTime;
        private final double nextSurgingBladeAllowedTime;
        private final Buff c4ResistanceBuff;
        private final List<PendingEvent> pendingEvents;

        private NaviaState(
                Navia owner,
                int normalAttackStep,
                int crystalShrapnel,
                long burstGeneration,
                double a1ExpirationTime,
                double nextBurstShrapnelAllowedTime,
                double nextSurgingBladeAllowedTime,
                Buff c4ResistanceBuff,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.crystalShrapnel = crystalShrapnel;
            this.burstGeneration = burstGeneration;
            this.a1ExpirationTime = a1ExpirationTime;
            this.nextBurstShrapnelAllowedTime =
                    nextBurstShrapnelAllowedTime;
            this.nextSurgingBladeAllowedTime =
                    nextSurgingBladeAllowedTime;
            this.c4ResistanceBuff = c4ResistanceBuff;
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}
