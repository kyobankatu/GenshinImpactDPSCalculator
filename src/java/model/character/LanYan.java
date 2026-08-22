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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Lan Yan's stationary single-target Feathermoon Ring kit through C6.
 *
 * <p>Multipliers, hitmarks, cooldowns, Energy timing, particles, A1/A4, and
 * constellation gates follow pinned gcsim {@code ef41805d}. The initial Skill
 * returns at its 33-frame Normal/Skill cancel so a second Press Skill or Normal
 * input can explicitly launch the Rings during the half-open 66-frame
 * Feathermoon window. A1 reads only the fixed target's typed Pyro, Hydro,
 * Electro, or Cryo Aura at the seven-frame detection hit; absent or unsupported
 * Aura fails closed to Anemo.</p>
 *
 * <p>Shield absorption and durability, player HP, movement and leap behavior,
 * target geometry, multi-target Ring retargeting, stamina, low plunge,
 * and Burst grouping are outside this bounded slice. High plunge is represented
 * as one stationary fixed-target impact. Unsupported grouping has no synthetic
 * damage or crowd-control effect.</p>
 */
public final class LanYan extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HITMARKS = {
        { 11 }, { 17, 37 }, { 15, 21 }, { 40 }
    };
    private static final int[] NORMAL_DURATIONS = { 30, 46, 53, 63 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2-1", "N2-2" },
        { "N3-1", "N3-2" }, { "N4" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.70448 }, { 0.347004, 0.424116 },
        { 0.45764, 0.45764 }, { 1.09752 }
    };
    private static final int[] CHARGED_HITMARKS = { 42, 49, 56 };
    private static final int[] RING_HITMARKS = { 38, 62, 85 };
    private static final int[] C1_RING_HITMARKS = { 38, 64, 90 };
    private static final int[] BURST_HITMARKS = { 30, 46, 51 };
    private static final Element[] ABSORPTION_PRIORITY = {
        Element.PYRO,
        Element.HYDRO,
        Element.ELECTRO,
        Element.CRYO
    };
    /**
     * Per-hit metadata from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_HITLAG_SHORT =
            new HitlagProfile(0.03, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_DEFENSE_ONLY =
            new HitlagProfile(0.0, 0.0, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N3_FIRST =
            new HitlagProfile(0.03, 0.01, false, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N4 =
            new HitlagProfile(0.06, 0.01, true, false, false);
    private static final HitlagProfile RING_HITLAG =
            new HitlagProfile(0.0, 0.01, true, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long ringGeneration;
    private long burstGeneration;
    private double feathermoonExpirationTime = Double.NEGATIVE_INFINITY;
    private double c4ExpirationTime = Double.NEGATIVE_INFINITY;
    private Element absorbedElement = Element.ANEMO;
    private boolean particleGenerated;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Lan Yan. */
    public LanYan(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Lan Yan at an explicit constellation. */
    public LanYan(
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
     * Constructs Lan Yan with explicit talent data and constellation.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData source for level-90 stats and talent values
     * @param constellation constellation level in the inclusive range 0-6
     */
    public LanYan(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Lan Yan constellation must be between 0 and 6");
        }
        name = "Lan Yan";
        characterId = CharacterId.LAN_YAN;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9244.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 251.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 580.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 16.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
        setSkillMaxCharges(constellation >= 6 ? 2 : 1);
    }

    /** Binds delayed Lan Yan state to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Lan Yan simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Lan Yan cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Lan Yan must belong to the target simulator party");
        }
        initializedSimulator = simulator;
    }

    /** Captures Lan Yan's combo, windows, and all surviving delayed work. */
    @Override
    public State captureCharacterState() {
        return new LanYanState(
                this,
                normalAttackStep,
                skillGeneration,
                ringGeneration,
                burstGeneration,
                feathermoonExpirationTime,
                c4ExpirationTime,
                absorbedElement,
                particleGenerated,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Lan Yan instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof LanYanState
                && ((LanYanState) state).owner == this;
    }

    /** Restores Lan Yan state and schedules each pending event exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Lan Yan state");
        }
        initializeForSimulator(simulator);
        LanYanState restored = (LanYanState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        ringGeneration = restored.ringGeneration;
        burstGeneration = restored.burstGeneration;
        feathermoonExpirationTime = restored.feathermoonExpirationTime;
        c4ExpirationTime = restored.c4ExpirationTime;
        absorbedElement = restored.absorbedElement;
        particleGenerated = restored.particleGenerated;
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

    /** Returns Lan Yan's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Lan Yan's represented passives are resolved at action boundaries. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A1, A4, and C4 depend on detected Aura or action-owned snapshots.
    }

    /** Resets the four-step Normal string without removing a Ring window. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /**
     * Lets a Press Skill launch Rings while the Feathermoon window is active.
     *
     * @param currentTime current simulator time in seconds
     * @return zero during the follow-up window, otherwise the core cooldown
     */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (isFeathermoonWindowActive(currentTime)) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Returns whether the half-open Ring follow-up window is active. */
    public boolean isFeathermoonWindowActive(double currentTime) {
        return currentTime < feathermoonExpirationTime - EPSILON;
    }

    /** Returns the fixed-target element captured by A1 detection. */
    public Element getAbsorbedElement() {
        return absorbedElement;
    }

    /** Returns the exact Feathermoon follow-up expiration timestamp. */
    public double getFeathermoonExpirationTime() {
        return feathermoonExpirationTime;
    }

    /** Returns the exact C4 party-EM expiration timestamp. */
    public double getC4ExpirationTime() {
        return c4ExpirationTime;
    }

    /** Dispatches Lan Yan's bounded fixed-target action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Lan Yan action is required");
        }
        initializeForSimulator(simulator);
        boolean ringFollowUp = isFeathermoonWindowActive(
                simulator.getCurrentTime())
                && (request.getKey() == CharacterActionKey.NORMAL
                        || request.getKey() == CharacterActionKey.SKILL);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        if (ringFollowUp) {
            launchFeathermoonRings(simulator);
            return;
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
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Lan Yan supports Press Skill only");
                }
                swallowWispPinionDance(simulator);
                break;
            case BURST:
                lustrousMoonrise(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Lan Yan: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        StatsContainer snapshot = captureLiveStats(castTime);
        for (int hit = 0; hit < NORMAL_HITMARKS[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HITMARKS[step][hit] * FRAME,
                    HitKind.NORMAL,
                    step,
                    hit,
                    false,
                    Element.ANEMO,
                    snapshot));
        }
        normalAttackStep = (normalAttackStep + 1) % 4;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        for (int index = 0; index < CHARGED_HITMARKS.length; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + CHARGED_HITMARKS[index] * FRAME,
                    HitKind.CHARGED,
                    index,
                    0,
                    false,
                    Element.ANEMO,
                    snapshot));
        }
        normalAttackStep = 0;
        simulator.advanceTime(70.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 46.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                0,
                false,
                Element.ANEMO,
                captureLiveStats(castTime)));
        normalAttackStep = 0;
        simulator.advanceTime(67.0 * FRAME);
    }

    private void swallowWispPinionDance(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        absorbedElement = Element.ANEMO;
        feathermoonExpirationTime = Double.NEGATIVE_INFINITY;
        particleGenerated = false;
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.SKILL_DETECT,
                generation));
        simulator.advanceTime(33.0 * FRAME);
    }

    private void detectSkillTarget(
            CombatSimulator simulator,
            long generation) {
        if (generation != skillGeneration || simulator.getEnemy() == null) {
            return;
        }
        absorbedElement = findAbsorbableAura(simulator);
        feathermoonExpirationTime = simulator.getCurrentTime()
                + getTalentValue("Feathermoon Ring Window", 1.1);
    }

    private Element findAbsorbableAura(CombatSimulator simulator) {
        for (Element candidate : ABSORPTION_PRIORITY) {
            if (simulator.getEnemy().getAuraUnits(
                    candidate, simulator.getCurrentTime()) > 0.0) {
                return candidate;
            }
        }
        return Element.ANEMO;
    }

    private void launchFeathermoonRings(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        feathermoonExpirationTime = Double.NEGATIVE_INFINITY;
        ringGeneration++;
        StatsContainer snapshot = captureLiveStats(castTime);
        queueRingVolley(
                simulator,
                castTime,
                RING_HITMARKS,
                false,
                snapshot);
        if (constellation >= 1 && absorbedElement != Element.ANEMO) {
            queueRingVolley(
                    simulator,
                    castTime,
                    C1_RING_HITMARKS,
                    true,
                    snapshot);
        }
        simulator.advanceTime(41.0 * FRAME);
    }

    private void queueRingVolley(
            CombatSimulator simulator,
            double castTime,
            int[] hitmarks,
            boolean c1Volley,
            StatsContainer snapshot) {
        for (int index = 0; index < hitmarks.length; index++) {
            double hitTime = castTime + hitmarks[index] * FRAME;
            queueHit(simulator, new PendingHit(
                    hitTime,
                    HitKind.RING,
                    index,
                    0,
                    c1Volley,
                    Element.ANEMO,
                    snapshot));
            if (absorbedElement != Element.ANEMO) {
                queueHit(simulator, new PendingHit(
                        hitTime,
                        HitKind.CONVERTED_RING,
                        index,
                        0,
                        c1Volley,
                        absorbedElement,
                        snapshot));
            }
        }
    }

    private void lustrousMoonrise(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        StatsContainer snapshot = captureLiveStats(castTime);
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation));
        for (int index = 0; index < BURST_HITMARKS.length; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + BURST_HITMARKS[index] * FRAME,
                    HitKind.BURST,
                    index,
                    0,
                    false,
                    Element.ANEMO,
                    snapshot));
        }
        if (constellation >= 4) {
            applyC4(simulator, castTime);
        }
        simulator.advanceTime(75.0 * FRAME);
    }

    private void applyC4(CombatSimulator simulator, double castTime) {
        double duration = getTalentValue("C4 Duration", 12.0);
        double amount = getTalentValue(
                "C4 Party Elemental Mastery", 60.0);
        c4ExpirationTime = castTime + duration;
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Lan Yan Dance on the Moon",
                BuffId.LAN_YAN_C4_PARTY_ELEMENTAL_MASTERY,
                duration,
                castTime,
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, amount))
                .sourcedBy(characterId));
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                resolveCharged(simulator, hit);
                break;
            case HIGH_PLUNGE:
                resolveHighPlunge(simulator, hit);
                break;
            case RING:
                resolveRing(simulator, hit, false);
                break;
            case CONVERTED_RING:
                resolveRing(simulator, hit, true);
                break;
            case BURST:
                resolveBurst(simulator, hit);
                break;
            default:
                throw new IllegalStateException("Unknown Lan Yan hit kind");
        }
    }

    private void resolveNormal(
            CombatSimulator simulator,
            PendingHit hit) {
        String key = NORMAL_KEYS[hit.index][hit.subIndex];
        AttackAction action = attack(
                "Black Pheasant Strides on Water " + key,
                getTalentValue(
                        key,
                        NORMAL_MULTIPLIERS[hit.index][hit.subIndex]),
                Element.ANEMO,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                hit.snapshot);
        HitlagProfile hitlagProfile = normalHitlag(hit.index, hit.subIndex);
        if (hitlagProfile != null) {
            action.setHitlagProfile(hitlagProfile);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveCharged(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Black Pheasant Strides on Water Charged Attack",
                getTalentValue("Charged Attack", 0.64328),
                Element.ANEMO,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.ChargedAttack,
                hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveHighPlunge(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Black Pheasant Strides on Water High Plunge",
                getTalentValue("High Plunge", 2.607632),
                Element.ANEMO,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.None,
                hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveRing(
            CombatSimulator simulator,
            PendingHit hit,
            boolean converted) {
        double multiplier = getTalentValue(
                constellation >= 3
                        ? "Feathermoon Ring C3"
                        : "Feathermoon Ring",
                constellation >= 3 ? 1.92512 : 1.636352);
        if (converted) {
            multiplier *= getTalentValue(
                    "A1 Converted Ring Ratio", 0.5);
        }
        String volley = hit.c1Volley ? " (C1)" : "";
        String conversion = converted
                ? " (" + hit.element.name() + ")" : "";
        StatsContainer actionSnapshot = hit.snapshot.merge(null);
        actionSnapshot.add(
                StatType.FLAT_DMG_BONUS,
                hit.snapshot.get(StatType.ELEMENTAL_MASTERY)
                        * getTalentValue("A4 Ring EM Flat Ratio", 3.09));
        AttackAction action = attack(
                "Feathermoon Ring" + volley + conversion,
                multiplier,
                hit.element,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                converted
                        ? ICDTag.LanYan_FeathermoonRingMix
                        : ICDTag.LanYan_FeathermoonRing,
                actionSnapshot);
        action.setHitlagProfile(RING_HITLAG);
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if (!particleGenerated && simulator.getEnemy() != null) {
            particleGenerated = true;
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime()
                            + getTalentValue("Particle Delay", 100.0 * FRAME),
                    CommandKind.PARTICLE,
                    0L));
        }
    }

    private void resolveBurst(
            CombatSimulator simulator,
            PendingHit hit) {
        StatsContainer actionSnapshot = hit.snapshot.merge(null);
        actionSnapshot.add(
                StatType.FLAT_DMG_BONUS,
                hit.snapshot.get(StatType.ELEMENTAL_MASTERY)
                        * getTalentValue("A4 Burst EM Flat Ratio", 7.74));
        AttackAction action = attack(
                "Lustrous Moonrise",
                getTalentValue(
                        constellation >= 5
                                ? "Lustrous Moonrise C5"
                                : "Lustrous Moonrise",
                        constellation >= 5 ? 4.82128 : 4.098088),
                Element.ANEMO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                actionSnapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
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
                case SKILL_COOLDOWN:
                    if (command.generation == skillGeneration) {
                        markSkillUsed(
                                activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case SKILL_DETECT:
                    detectSkillTarget(
                            activeSimulator, command.generation);
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.ANEMO,
                            getTalentValue("Particle Count", 3.0),
                            ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Lan Yan command kind");
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

    private static AttackAction attack(
            String displayName,
            double multiplier,
            Element element,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            StatsContainer snapshot) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, 1.0);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setStatSnapshot(snapshot);
        return action;
    }

    private static HitlagProfile normalHitlag(int step, int hit) {
        if (step == 0) {
            return NORMAL_HITLAG_SHORT;
        }
        if (step == 1) {
            return hit == 0
                    ? NORMAL_HITLAG_SHORT
                    : NORMAL_HITLAG_DEFENSE_ONLY;
        }
        if (step == 2) {
            return hit == 0 ? NORMAL_HITLAG_N3_FIRST : null;
        }
        return NORMAL_HITLAG_N4;
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
        HIGH_PLUNGE,
        RING,
        CONVERTED_RING,
        BURST
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        SKILL_DETECT,
        BURST_ENERGY,
        PARTICLE
    }

    /** Immutable delayed Lan Yan hit with an owner-captured stat snapshot. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final boolean c1Volley;
        private final Element element;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                boolean c1Volley,
                Element element,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.c1Volley = c1Volley;
            this.element = element;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    subIndex,
                    c1Volley,
                    element,
                    snapshot);
        }
    }

    /** Immutable delayed Lan Yan state transition. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, generation);
        }
    }

    /** Immutable Lan Yan-owned simulator snapshot payload. */
    private static final class LanYanState implements State {
        private final LanYan owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long ringGeneration;
        private final long burstGeneration;
        private final double feathermoonExpirationTime;
        private final double c4ExpirationTime;
        private final Element absorbedElement;
        private final boolean particleGenerated;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private LanYanState(
                LanYan owner,
                int normalAttackStep,
                long skillGeneration,
                long ringGeneration,
                long burstGeneration,
                double feathermoonExpirationTime,
                double c4ExpirationTime,
                Element absorbedElement,
                boolean particleGenerated,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.ringGeneration = ringGeneration;
            this.burstGeneration = burstGeneration;
            this.feathermoonExpirationTime = feathermoonExpirationTime;
            this.c4ExpirationTime = c4ExpirationTime;
            this.absorbedElement = absorbedElement;
            this.particleGenerated = particleGenerated;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
