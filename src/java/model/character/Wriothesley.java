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
import simulation.action.HitlagProfile;
import simulation.event.SimpleTimerEvent;

/**
 * Wriothesley's fixed-target, fixed-full-HP offensive vertical slice.
 *
 * <p>Level-90 stats, talent multipliers, hitmarks, cooldowns, particles,
 * Icefang Rush, Darkgold Wolfbite, and the represented C1/C3/C5/C6 branches
 * follow the pinned gcsim {@code ef41805d} files under
 * {@code internal/characters/wriothesley/} and its generated character
 * catalog. C1 is restricted to the source-backed in-Skill N5 trigger; its
 * unavailable low-HP trigger never activates.</p>
 *
 * <p>The simulator has no player current-HP, HP-change, healing, stamina,
 * airborne, movement, or target-geometry state. Chilling Penalty therefore
 * operates only in the repository's fixed-full-HP boundary: its Normal
 * scaling and particles are represented, while its HP drain is not invented.
 * A typed Plunge request emits only the sourced high-Plunge damage packet and
 * does not create or claim airborne state.
 * A1, A4, C2, C4, Rebuke healing/stamina changes, low-HP alternatives,
 * collision Plunge, hitlag, and multi-target geometry fail closed. C6's
 * offensive Rebuke-only duplicate and CRIT modifiers remain representable.
 * No mechanic in this slice requires randomness.</p>
 */
public final class Wriothesley extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[] NORMAL_HITLAG = {
        new HitlagProfile(0.0, 0.0, true, false, false),
        new HitlagProfile(0.0, 0.0, true, false, false),
        new HitlagProfile(0.03, 0.01, true, false, false),
        new HitlagProfile(0.0, 0.0, true, false, false),
        new HitlagProfile(0.06, 0.01, true, false, false)
    };
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.09, 0.01, false, false, false);
    private static final HitlagProfile REBUKE_HITLAG =
            new HitlagProfile(0.12, 0.03, false, false, false);
    private static final HitlagProfile BURST_HITLAG =
            new HitlagProfile(0.03, 0.01, false, false, false);
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 12 }, { 10 }, { 18 }, { 25, 35 }, { 39 }
    };
    private static final int[] NORMAL_DURATIONS = { 27, 25, 41, 56, 59 };
    private static final double[][] NORMAL_T9 = {
        { 0.980327 },
        { 0.951650 },
        { 1.235023 },
        { 0.696377, 0.696377 },
        { 1.667121 }
    };
    private static final double[][] NORMAL_C3 = {
        { 1.203692 },
        { 1.168481 },
        { 1.516420 },
        { 0.855045, 0.855045 },
        { 2.046972 }
    };
    private static final int[] BURST_HIT_FRAMES = { 99, 104, 109, 114, 119 };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double skillExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean skillExtensionUsed;
    private boolean graciousRebukeReady;
    private double nextRebukeAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextOusiaAllowedTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Wriothesley. */
    public Wriothesley(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /**
     * Constructs Wriothesley at an explicit constellation.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param constellation constellation in the inclusive range {@code [0, 6]}
     */
    public Wriothesley(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Wriothesley with injectable static talent data.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation in the inclusive range {@code [0, 6]}
     */
    public Wriothesley(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Wriothesley constellation must be between 0 and 6");
        }
        name = "Wriothesley";
        characterId = CharacterId.WRIOTHESLEY;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13593.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 311.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 763.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 16.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Wriothesley's delayed work to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Wriothesley simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Wriothesley must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Wriothesley cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures owner gates, windows, and reconstructable delayed work. */
    @Override
    public State captureCharacterState() {
        return new WriothesleyState(
                this,
                normalAttackStep,
                skillExpirationTime,
                skillExtensionUsed,
                graciousRebukeReady,
                nextRebukeAllowedTime,
                nextParticleAllowedTime,
                nextOusiaAllowedTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Wriothesley instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof WriothesleyState
                && ((WriothesleyState) state).owner == this;
    }

    /** Restores owner state and registers every surviving delayed event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Wriothesley state");
        }
        initializeForSimulator(simulator);
        WriothesleyState restored = (WriothesleyState) state;
        normalAttackStep = restored.normalAttackStep;
        skillExpirationTime = restored.skillExpirationTime;
        skillExtensionUsed = restored.skillExtensionUsed;
        graciousRebukeReady = restored.graciousRebukeReady;
        nextRebukeAllowedTime = restored.nextRebukeAllowedTime;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        nextOusiaAllowedTime = restored.nextOusiaAllowedTime;
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

    /** Returns Wriothesley's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** No unconditional passive stat exists in the represented boundary. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Ends Chilling Penalty and resets the Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        skillExpirationTime = simulator.getCurrentTime();
        skillExtensionUsed = false;
    }

    /** Returns whether fixed-full-HP Chilling Penalty is active. */
    public boolean isChillingPenaltyActive(double currentTime) {
        return currentTime + EPSILON < skillExpirationTime;
    }

    /** Returns Chilling Penalty's half-open expiration timestamp. */
    public double getChillingPenaltyExpirationTime() {
        return skillExpirationTime;
    }

    /** Returns whether the represented C1 N5 branch prepared a Rebuke. */
    public boolean hasGraciousRebuke() {
        return graciousRebukeReady;
    }

    /** Returns the next timestamp at which C1 may prepare a Rebuke. */
    public double getNextRebukeAllowedTime() {
        return nextRebukeAllowedTime;
    }

    /** Returns the next timestamp at which a represented hit may make particles. */
    public double getNextParticleAllowedTime() {
        return nextParticleAllowedTime;
    }

    /** Returns the number of unresolved Wriothesley-owned damage hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Wriothesley's represented typed offensive actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Wriothesley action is required");
        }
        initializeForSimulator(simulator);
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case CHARGE:
                chargedAttack(simulator);
                normalAttackStep = 0;
                break;
            case PLUNGE:
                highPlunge(simulator);
                normalAttackStep = 0;
                break;
            case SKILL:
                icefangRush(simulator);
                break;
            case BURST:
                darkgoldWolfbite(simulator);
                normalAttackStep = 0;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Wriothesley: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int variant = 0;
                variant < NORMAL_HIT_FRAMES[step].length;
                variant++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HIT_FRAMES[step][variant] * FRAME,
                    HitKind.NORMAL,
                    step,
                    variant,
                    false,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean rebuke = constellation >= 1 && graciousRebukeReady;
        StatsContainer snapshot = captureLiveStats(castTime);
        if (rebuke) {
            snapshot.add(StatType.DMG_BONUS_ALL,
                    getTalentValue("C1 Rebuke DMG Bonus", 2.0));
            if (constellation >= 6) {
                snapshot.add(StatType.CRIT_RATE,
                        getTalentValue("C6 Rebuke CRIT Rate", 0.10));
                snapshot.add(StatType.CRIT_DMG,
                        getTalentValue("C6 Rebuke CRIT DMG", 0.80));
            }
        }
        queueHit(simulator, new PendingHit(
                castTime + 19.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                rebuke,
                snapshot));
        if (rebuke && constellation >= 6) {
            queueHit(simulator, new PendingHit(
                    castTime + 19.0 * FRAME,
                    HitKind.CHARGED,
                    0,
                    1,
                    true,
                    snapshot));
        }
        simulator.advanceTime(52.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 47.0 * FRAME,
                HitKind.PLUNGE,
                0,
                0,
                false,
                captureLiveStats(castTime)));
        simulator.advanceTime(76.0 * FRAME);
    }

    private void icefangRush(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        skillExtensionUsed = false;
        skillExpirationTime = castTime
                + getTalentValue("Skill Duration", 10.0) + FRAME;
        queueCommand(simulator, new PendingCommand(
                castTime + FRAME,
                CommandKind.SKILL_COOLDOWN,
                0.0));
        simulator.advanceTime(29.0 * FRAME);
    }

    private void darkgoldWolfbite(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        markBurstCooldownUsed(castTime,
                simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 5.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0));
        for (int index = 0; index < BURST_HIT_FRAMES.length; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + BURST_HIT_FRAMES[index] * FRAME,
                    HitKind.BURST,
                    index,
                    0,
                    false,
                    snapshot));
        }
        queueHit(simulator, new PendingHit(
                castTime + 160.0 * FRAME,
                HitKind.OUSIA,
                0,
                0,
                false,
                snapshot));
        simulator.advanceTime(133.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                resolveCharged(simulator, hit);
                break;
            case PLUNGE:
                performHit(simulator, hit,
                        "Forceful Fists of Frost High Plunge",
                        normalTalentValue(
                                "High Plunge", 2.607632, 3.201776),
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            case BURST:
                performHit(simulator, hit,
                        "Darkgold Wolfbite " + (hit.index + 1),
                        burstTalentValue(
                                "Darkgold Wolfbite", 2.162400, 2.544000),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            case OUSIA:
                resolveOusia(simulator, hit);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Wriothesley hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        boolean enhanced = isChillingPenaltyActive(hit.time);
        String key = normalKey(hit.index, hit.variant);
        double multiplier = normalTalentValue(
                key,
                NORMAL_T9[hit.index][hit.variant],
                NORMAL_C3[hit.index][hit.variant]);
        if (enhanced) {
            multiplier *= getTalentValue(
                    "Enhanced Repelling Fist Scaling", 1.669515);
        }
        performHit(simulator, hit,
                "Repelling Fists N" + (hit.index + 1)
                        + (enhanced ? " (Enhanced)" : ""),
                multiplier,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0);
        if (simulator.getEnemy() == null) {
            return;
        }
        if (enhanced) {
            tryGenerateParticle(simulator, hit.time);
        }
        if (constellation >= 1
                && hit.index == 4
                && enhanced
                && hit.time + EPSILON >= nextRebukeAllowedTime) {
            graciousRebukeReady = true;
        }
    }

    private void resolveCharged(
            CombatSimulator simulator,
            PendingHit hit) {
        String name = hit.rebuke
                ? "Rebuke: Vaulting Fist" : "Forceful Fists Charged Attack";
        if (hit.variant == 1) {
            name += " (C6 Icicle)";
        }
        performHit(simulator, hit, name,
                normalTalentValue(
                        "Charged Attack", 2.600320, 3.059200),
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.None,
                ICDTag.ChargedAttack,
                1.0);
        if (!hit.rebuke || simulator.getEnemy() == null) {
            return;
        }
        tryGenerateParticle(simulator, hit.time);
        if (hit.variant != 0) {
            return;
        }
        graciousRebukeReady = false;
        nextRebukeAllowedTime = hit.time
                + getTalentValue("C1 Rebuke Cooldown", 2.5);
        if (isChillingPenaltyActive(hit.time) && !skillExtensionUsed) {
            skillExpirationTime += getTalentValue(
                    "C1 Skill Extension", 4.0);
            skillExtensionUsed = true;
        }
    }

    private void resolveOusia(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.time + EPSILON < nextOusiaAllowedTime) {
            return;
        }
        nextOusiaAllowedTime = hit.time
                + getTalentValue("Ousia Cooldown", 10.0);
        performHit(simulator, hit,
                "Darkgold Wolfbite: Surging Blade",
                burstTalentValue(
                        "Surging Blade", 0.720800, 0.848000),
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                0.0);
    }

    private void tryGenerateParticle(
            CombatSimulator simulator,
            double hitTime) {
        if (hitTime + EPSILON < nextParticleAllowedTime) {
            return;
        }
        nextParticleAllowedTime = hitTime
                + getTalentValue("Particle Cooldown", 2.0);
        double travel = getTalentValue(
                "Particle Travel Frames", 100.0) * FRAME;
        queueCommand(simulator, new PendingCommand(
                hitTime + travel,
                CommandKind.PARTICLE,
                1.0));
    }

    private double normalTalentValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue(key + suffix,
                constellation >= 3 ? talentTwelve : talentNine);
    }

    private double burstTalentValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(key + suffix,
                constellation >= 5 ? talentTwelve : talentNine);
    }

    private static String normalKey(int step, int variant) {
        if (step == 3) {
            return "N4-" + (variant + 1);
        }
        return "N" + (step + 1);
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                Element.CRYO,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setHitlagProfile(hitlagProfile(hit));
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private HitlagProfile hitlagProfile(PendingHit hit) {
        if (hit.kind == HitKind.CHARGED) {
            return hit.rebuke ? REBUKE_HITLAG : CHARGED_HITLAG;
        }
        if (hit.kind == HitKind.OUSIA) {
            return BURST_HITLAG;
        }
        if (hit.kind == HitKind.NORMAL) {
            return NORMAL_HITLAG[hit.index];
        }
        return HitlagProfile.none();
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
                    markSkillUsed(activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.CRYO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Wriothesley command kind");
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
        CHARGED,
        PLUNGE,
        BURST,
        OUSIA
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final boolean rebuke;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                boolean rebuke,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.rebuke = rebuke;
            this.snapshot = snapshot == null ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, variant, rebuke, snapshot);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final double value;

        private PendingCommand(
                double time,
                CommandKind kind,
                double value) {
            this.time = time;
            this.kind = kind;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value);
        }
    }

    private static final class WriothesleyState implements State {
        private final Wriothesley owner;
        private final int normalAttackStep;
        private final double skillExpirationTime;
        private final boolean skillExtensionUsed;
        private final boolean graciousRebukeReady;
        private final double nextRebukeAllowedTime;
        private final double nextParticleAllowedTime;
        private final double nextOusiaAllowedTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private WriothesleyState(
                Wriothesley owner,
                int normalAttackStep,
                double skillExpirationTime,
                boolean skillExtensionUsed,
                boolean graciousRebukeReady,
                double nextRebukeAllowedTime,
                double nextParticleAllowedTime,
                double nextOusiaAllowedTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillExpirationTime = skillExpirationTime;
            this.skillExtensionUsed = skillExtensionUsed;
            this.graciousRebukeReady = graciousRebukeReady;
            this.nextRebukeAllowedTime = nextRebukeAllowedTime;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.nextOusiaAllowedTime = nextOusiaAllowedTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
