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
 * Furina's deterministic fixed-target Ousia Salon Solitaire slice.
 *
 * <p>Level-90 sword basics, high Plunge, the Ousia Skill bubble, all three
 * Salon Member cadences, particles, Burst damage, A4, and the fixed-HP
 * offensive portions of C1-C6 follow pinned gcsim {@code ef41805d}. Salon
 * timing uses the source's rounded mean attack frames with no random offset.
 * The simulator has no player-HP model, so every represented party member is
 * held at full HP and contributes the source's 10% Salon damage increment;
 * no HP is actually drained.</p>
 *
 * <p>Current HP and HP changes, healing, Fanfare gained from HP changes, A1,
 * C2's dynamic Fanfare branch, Pneuma and Arkhe switching, multi-target and
 * geometry, random targeting, hitlag, stamina, low Plunge, underwater, and
 * exploration behavior fail closed. C1 therefore supplies only its initial
 * 150 Fanfare, while C6 supplies its Hydro conversion and Max-HP damage but
 * never its Ousia healing.</p>
 */
public final class Furina extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 15, 12, 21, 27 };
    private static final int[] NORMAL_DURATIONS = { 34, 28, 48, 58 };
    private static final double[] NORMAL_T9 = {
        0.888955, 0.803398, 1.012669, 1.346634
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long salonGeneration;
    private double salonActiveUntil = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC4AllowedTime = Double.NEGATIVE_INFINITY;
    private double c6ActiveUntil = Double.NEGATIVE_INFINITY;
    private int c6HitCount;
    private AttackAction resolvingAction;
    private boolean resolvingSalonHit;
    private boolean resolvingC6Hit;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Furina. */
    public Furina(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Furina at an explicit constellation. */
    public Furina(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Furina with injectable static talent data.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Furina(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Furina constellation must be between 0 and 6");
        }
        name = "Furina";
        characterId = CharacterId.FURINA;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 15307.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 244.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 696.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(getTalentValue("Skill Cooldown", 20.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds accepted-hit callbacks to exactly one owning simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Furina simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Furina must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Furina cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                handleAcceptedDamage(
                        simulator, actor, action, damage, time));
    }

    /** Captures combo, Salon, gate, C6, and future-event state. */
    @Override
    public State captureCharacterState() {
        return new FurinaState(
                this,
                normalAttackStep,
                salonGeneration,
                salonActiveUntil,
                nextParticleAllowedTime,
                nextC4AllowedTime,
                c6ActiveUntil,
                c6HitCount,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Furina instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof FurinaState
                && ((FurinaState) state).owner == this;
    }

    /** Restores owner state and reconstructs each unresolved event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Furina state");
        }
        initializeForSimulator(simulator);
        FurinaState restored = (FurinaState) state;
        normalAttackStep = restored.normalAttackStep;
        salonGeneration = restored.salonGeneration;
        salonActiveUntil = restored.salonActiveUntil;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        nextC4AllowedTime = restored.nextC4AllowedTime;
        c6ActiveUntil = restored.c6ActiveUntil;
        c6HitCount = restored.c6HitCount;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        resolvingAction = null;
        resolvingSalonHit = false;
        resolvingC6Hit = false;
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

    /** Returns Furina's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** A4 is applied only to represented Salon Member attacks. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets Furina's four-stage Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets Furina's four-stage Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether the deterministic Ousia Salon is active. */
    public boolean isSalonActive(double currentTime) {
        return currentTime + EPSILON < salonActiveUntil;
    }

    /** Returns the exact represented Salon expiration timestamp. */
    public double getSalonActiveUntil() {
        return salonActiveUntil;
    }

    /** Returns the next accepted Salon hit eligible to create a particle. */
    public double getNextParticleAllowedTime() {
        return nextParticleAllowedTime;
    }

    /** Returns the number of accepted attacks consumed by the C6 window. */
    public int getC6HitCount() {
        return c6HitCount;
    }

    /** Reports that mutable player HP and HP-change Fanfare are excluded. */
    public boolean isPlayerHpRepresented() {
        return false;
    }

    /** Reports that random target and cadence offsets are excluded. */
    public boolean isRandomTargetingRepresented() {
        return false;
    }

    /** Routes Furina's fixed-target actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        validateActionRequest(request);
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Furina supports Ousia Press Skill only");
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
            case PLUNGE:
                highPlunge(simulator);
                break;
            case SKILL:
                salonSolitaire(simulator);
                break;
            case BURST:
                letThePeopleRejoice(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Furina: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                0L,
                null));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 33.0 * FRAME,
                HitKind.CHARGED,
                0,
                0L,
                null));
        simulator.advanceTime(33.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 47.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                0L,
                null));
        simulator.advanceTime(76.0 * FRAME);
    }

    private void salonSolitaire(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        long generation = ++salonGeneration;
        salonActiveUntil = castTime
                + getTalentValue("Salon Duration Frames", 1754.0) * FRAME;
        if (constellation >= 6) {
            c6HitCount = 0;
            c6ActiveUntil = castTime
                    + getTalentValue("C6 Duration", 10.0);
        }
        queueHit(simulator, new PendingHit(
                castTime + 18.0 * FRAME,
                HitKind.OUSIA_BUBBLE,
                0,
                generation,
                captureLiveStats(castTime)));
        queueFirstSalonCommand(
                simulator, castTime, generation, Member.CHEVALMARIN);
        queueFirstSalonCommand(
                simulator, castTime, generation, Member.USHER);
        queueFirstSalonCommand(
                simulator, castTime, generation, Member.CRABALETTA);
        simulator.advanceTime(54.0 * FRAME);
    }

    private void letThePeopleRejoice(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.BURST_ENERGY,
                null,
                0,
                0L));
        if (constellation >= 1) {
            queueCommand(simulator, new PendingCommand(
                    castTime + 95.0 * FRAME,
                    CommandKind.C1_FANFARE,
                    null,
                    0,
                    0L));
        }
        queueHit(simulator, new PendingHit(
                castTime + 98.0 * FRAME,
                HitKind.BURST,
                0,
                0L,
                null));
        simulator.advanceTime(121.0 * FRAME);
    }

    private void queueFirstSalonCommand(
            CombatSimulator simulator,
            double castTime,
            long generation,
            Member member) {
        double summonTime = castTime + 18.0 * FRAME;
        double commandTime = summonTime
                + roundedSalonFrame(member, 0) * FRAME;
        queueCommand(simulator, new PendingCommand(
                commandTime,
                CommandKind.SALON_ATTACK,
                member,
                0,
                generation));
    }

    private void emitSalonAttack(
            CombatSimulator simulator,
            PendingCommand command) {
        double currentTime = simulator.getCurrentTime();
        if (command.generation != salonGeneration
                || !isSalonActive(currentTime)) {
            return;
        }
        queueHit(simulator, new PendingHit(
                currentTime + command.member.travelFrames * FRAME,
                command.member.hitKind,
                0,
                command.generation,
                captureLiveStats(currentTime)));
        int nextTick = command.tick + 1;
        double summonTime = currentTime
                - roundedSalonFrame(
                        command.member, command.tick) * FRAME;
        double nextTime = summonTime
                + roundedSalonFrame(
                        command.member, nextTick) * FRAME;
        if (nextTime < salonActiveUntil - EPSILON) {
            queueCommand(simulator, new PendingCommand(
                    nextTime,
                    CommandKind.SALON_ATTACK,
                    command.member,
                    nextTick,
                    command.generation));
        }
    }

    private static long roundedSalonFrame(Member member, int tick) {
        return Math.round(
                member.initialFrames + tick * member.intervalFrames);
    }

    private void handleAcceptedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (actor != this
                || action != resolvingAction
                || damage <= 0.0) {
            return;
        }
        if (resolvingSalonHit) {
            if (time + EPSILON >= nextParticleAllowedTime) {
                nextParticleAllowedTime = time
                        + getTalentValue("Particle Cooldown", 2.5);
                queueCommand(simulator, new PendingCommand(
                        time + getTalentValue(
                                "Particle Travel Frames", 100.0) * FRAME,
                        CommandKind.PARTICLE,
                        null,
                        0,
                        0L));
            }
            if (constellation >= 4
                    && time + EPSILON >= nextC4AllowedTime) {
                nextC4AllowedTime = time
                        + getTalentValue("C4 Energy Cooldown", 5.0);
                receiveFlatEnergy(getTalentValue("C4 Energy", 4.0));
            }
        }
        if (resolvingC6Hit) {
            c6HitCount++;
            if (c6HitCount >= (int) getTalentValue("C6 Hit Cap", 6.0)) {
                c6ActiveUntil = Double.NEGATIVE_INFINITY;
            }
        }
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.kind.isSalon
                && (hit.generation != salonGeneration
                        || !isSalonActive(hit.time))) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator, hit,
                        "Soloist's Solicitation N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        false);
                break;
            case CHARGED:
                performHit(
                        simulator, hit,
                        "Soloist's Solicitation Charged",
                        getTalentValue(
                                "Soloist's Solicitation Charged", 1.36354),
                        StatType.BASE_ATK,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        false);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator, hit,
                        "Soloist's Solicitation High Plunge",
                        getTalentValue("High Plunge", 2.933586),
                        StatType.BASE_ATK,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        false);
                break;
            case OUSIA_BUBBLE:
                performHit(
                        simulator, hit,
                        "Salon Solitaire: Ousia Bubble",
                        skillValue("Ousia Bubble", 0.133688, 0.15728),
                        StatType.BASE_HP,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        false);
                break;
            case CHEVALMARIN:
                performSalonHit(
                        simulator, hit,
                        "Salon Member: Surintendante Chevalmarin",
                        skillValue(
                                "Surintendante Chevalmarin",
                                0.054944,
                                0.06464),
                        ICDType.FurinaSalonSolitaire,
                        ICDTag.Furina_Chevalmarin);
                break;
            case USHER:
                performSalonHit(
                        simulator, hit,
                        "Salon Member: Gentilhomme Usher",
                        skillValue(
                                "Gentilhomme Usher", 0.10132, 0.1192),
                        ICDType.FurinaSalonSolitaire,
                        ICDTag.Furina_Usher);
                break;
            case CRABALETTA:
                performSalonHit(
                        simulator, hit,
                        "Salon Member: Mademoiselle Crabaletta",
                        skillValue(
                                "Mademoiselle Crabaletta",
                                0.140896,
                                0.16576),
                        ICDType.None,
                        ICDTag.None);
                break;
            case BURST:
                performHit(
                        simulator, hit,
                        "Let the People Rejoice",
                        burstValue(),
                        StatType.BASE_HP,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        false);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Furina hit kind " + hit.kind);
        }
    }

    private void performSalonHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            ICDType icdType,
            ICDTag icdTag) {
        int representedAllies = simulator.getPartyMembers().size();
        double fixedHpMultiplier = 1.0
                + representedAllies * getTalentValue(
                        "Fixed Full HP Ally Bonus", 0.1);
        performHit(
                simulator,
                hit,
                displayName,
                multiplier * fixedHpMultiplier,
                StatType.BASE_HP,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                icdType,
                icdTag,
                true);
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            boolean salonHit) {
        boolean c6Enhanced = !salonHit
                && (actionType == ActionType.NORMAL
                        || actionType == ActionType.CHARGE
                        || actionType == ActionType.PLUNGE)
                && constellation >= 6
                && hit.time + EPSILON < c6ActiveUntil
                && c6HitCount < (int) getTalentValue("C6 Hit Cap", 6.0);
        Element hitElement = c6Enhanced ? Element.HYDRO
                : actionType == ActionType.NORMAL
                        || actionType == ActionType.CHARGE
                        || actionType == ActionType.PLUNGE
                                ? Element.PHYSICAL : Element.HYDRO;
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                scalingStat,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, hitElement == Element.HYDRO ? 1.0 : 0.0);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(hit.time) : hit.snapshot.merge(null);
        if (salonHit) {
            double a4Bonus = Math.min(
                    getTalentValue("A4 DMG Cap", 0.28),
                    snapshot.getTotalHp() / 1000.0
                            * getTalentValue(
                                    "A4 DMG Per 1000 HP", 0.007));
            action.addBonusStat(StatType.DMG_BONUS_ALL, a4Bonus);
        }
        if (c6Enhanced) {
            snapshot.add(
                    StatType.FLAT_DMG_BONUS,
                    snapshot.getTotalHp()
                            * getTalentValue("C6 Max HP Ratio", 0.18));
        }
        action.setStatSnapshot(snapshot);
        resolvingAction = action;
        resolvingSalonHit = salonHit;
        resolvingC6Hit = c6Enhanced;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingSalonHit = false;
            resolvingC6Hit = false;
        }
    }

    private double skillValue(String key, double t9, double c5) {
        return getTalentValue(
                constellation >= 5 ? key + " C5" : key,
                constellation >= 5 ? c5 : t9);
    }

    private double burstValue() {
        return getTalentValue(
                constellation >= 3
                        ? "Let the People Rejoice C3"
                        : "Let the People Rejoice",
                constellation >= 3 ? 0.228128 : 0.193909);
    }

    private void applyC1Fanfare(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        double ratio = getTalentValue(
                constellation >= 3
                        ? "Fanfare DMG Ratio C3"
                        : "Fanfare DMG Ratio",
                constellation >= 3 ? 0.0029 : 0.0023);
        double bonus = getTalentValue("C1 Initial Fanfare", 150.0) * ratio;
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Furina C1 Initial Fanfare",
                BuffId.FURINA_C1_FANFARE_DMG,
                getTalentValue("Burst Duration", 18.2),
                currentTime,
                stats -> stats.add(StatType.DMG_BONUS_ALL, bonus))
                .sourcedBy(characterId));
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
                case SALON_ATTACK:
                    emitSalonAttack(activeSimulator, command);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.HYDRO,
                                    getTalentValue("Particle Count", 1.0),
                                    ParticleType.PARTICLE);
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case C1_FANFARE:
                    applyC1Fanfare(activeSimulator);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Furina command " + command.kind);
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
        NORMAL(false),
        CHARGED(false),
        HIGH_PLUNGE(false),
        OUSIA_BUBBLE(false),
        CHEVALMARIN(true),
        USHER(true),
        CRABALETTA(true),
        BURST(false);

        private final boolean isSalon;

        HitKind(boolean isSalon) {
            this.isSalon = isSalon;
        }
    }

    private enum CommandKind {
        SALON_ATTACK,
        PARTICLE,
        BURST_ENERGY,
        C1_FANFARE
    }

    private enum Member {
        CHEVALMARIN(72.3333, 97.5858, 20.0, HitKind.CHEVALMARIN),
        USHER(74.5, 202.138, 40.0, HitKind.USHER),
        CRABALETTA(71.5926, 313.859, 41.0, HitKind.CRABALETTA);

        private final double initialFrames;
        private final double intervalFrames;
        private final double travelFrames;
        private final HitKind hitKind;

        Member(
                double initialFrames,
                double intervalFrames,
                double travelFrames,
                HitKind hitKind) {
            this.initialFrames = initialFrames;
            this.intervalFrames = intervalFrames;
            this.travelFrames = travelFrames;
            this.hitKind = hitKind;
        }
    }

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
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, generation, snapshot);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final Member member;
        private final int tick;
        private final long generation;

        private PendingCommand(
                double time,
                CommandKind kind,
                Member member,
                int tick,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.member = member;
            this.tick = tick;
            this.generation = generation;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time, kind, member, tick, generation);
        }
    }

    private static final class FurinaState implements State {
        private final Furina owner;
        private final int normalAttackStep;
        private final long salonGeneration;
        private final double salonActiveUntil;
        private final double nextParticleAllowedTime;
        private final double nextC4AllowedTime;
        private final double c6ActiveUntil;
        private final int c6HitCount;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private FurinaState(
                Furina owner,
                int normalAttackStep,
                long salonGeneration,
                double salonActiveUntil,
                double nextParticleAllowedTime,
                double nextC4AllowedTime,
                double c6ActiveUntil,
                int c6HitCount,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.salonGeneration = salonGeneration;
            this.salonActiveUntil = salonActiveUntil;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.nextC4AllowedTime = nextC4AllowedTime;
            this.c6ActiveUntil = c6ActiveUntil;
            this.c6HitCount = c6HitCount;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
