package model.character;

import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.CharacterTeamBuffProvider;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.StellarReactionProvider;
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

/**
 * Odette's source-backed Version 7.0 fixed-target combat slice.
 *
 * <p>Level 90 stats, talent level 9 and constellation-adjusted level 12
 * multipliers, windows,
 * passive values, and reliable constellation values follow Genshin Optimizer
 * revision {@code d791814a} from Version 7.0 content commit
 * {@code cf769c73}. Stellar-Conduct takes priority when both Radiance states
 * are active.</p>
 *
 * <p>Coda, Plume, and Wing are public manual commands because their authoritative
 * cadence is unavailable. Animation frames, gauge, ICD, particles, and geometry
 * deliberately fail closed. Every represented hit resolves immediately with no
 * elemental application, and C4 follows accepted direct Stellar damage.</p>
 */
public final class Odette extends Character implements
        CharacterTeamBuffProvider,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        StellarReactionProvider,
        SwitchAwareCharacter {
    private static final int NORMAL_ATTACK_COUNT = 5;
    private static final double EPSILON = 1e-9;

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double danceDoubleUntil = Double.NEGATIVE_INFINITY;
    private double codaAvailableUntil = Double.NEGATIVE_INFINITY;
    private double nextCodaAt = Double.NEGATIVE_INFINITY;
    private boolean codaDanceEmpowered;
    private double burstBonusUntil = Double.NEGATIVE_INFINITY;
    private int selfSplendorStacks;
    private int teamSplendorStacks;
    private boolean offField;
    private double lastSplendorTransferTime;
    private double nextC4CoordinatedAt = Double.NEGATIVE_INFINITY;
    private boolean resolvingC4Coordinated;

    /** Constructs repository-default C0 Odette. */
    public Odette(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 0);
    }

    /**
     * Constructs Odette at an explicit constellation.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param constellation constellation in the inclusive range 0-6
     */
    public Odette(
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
     * Constructs Odette with injectable sourced talent data.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData character-data source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Odette(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Odette constellation must be between 0 and 6");
        }
        name = "Odette";
        characterId = CharacterId.ODETTE;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12980.6656));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 334.8497));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 786.9997));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Odette to exactly one simulator and records initial field state. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Odette simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Odette must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Odette cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        offField = simulator.getActiveCharacter() != this;
        lastSplendorTransferTime = simulator.getCurrentTime();
        if (constellation >= 4) {
            simulator.addDamageListener((actor, action, damage, time) ->
                    handleAcceptedStellarDamage(
                            simulator, actor, action, damage, time));
        }
    }

    /** Captures every mutable Odette-owned fixed-target state value. */
    @Override
    public State captureCharacterState() {
        return new OdetteState(
                this,
                normalAttackStep,
                danceDoubleUntil,
                codaAvailableUntil,
                nextCodaAt,
                codaDanceEmpowered,
                burstBonusUntil,
                selfSplendorStacks,
                teamSplendorStacks,
                offField,
                lastSplendorTransferTime,
                nextC4CoordinatedAt);
    }

    /** Accepts only state captured from this exact Odette instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof OdetteState
                && ((OdetteState) state).owner == this;
    }

    /** Restores all Odette-owned state without creating inferred events. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Odette state");
        }
        initializeForSimulator(simulator);
        OdetteState restored = (OdetteState) state;
        normalAttackStep = restored.normalAttackStep;
        danceDoubleUntil = restored.danceDoubleUntil;
        codaAvailableUntil = restored.codaAvailableUntil;
        nextCodaAt = restored.nextCodaAt;
        codaDanceEmpowered = restored.codaDanceEmpowered;
        burstBonusUntil = restored.burstBonusUntil;
        selfSplendorStacks = restored.selfSplendorStacks;
        teamSplendorStacks = restored.teamSplendorStacks;
        offField = restored.offField;
        lastSplendorTransferTime = restored.lastSplendorTransferTime;
        nextC4CoordinatedAt = restored.nextC4CoordinatedAt;
        resolvingC4Coordinated = false;
    }

    /** Returns Odette's sourced 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Enables conversion into Stellar-Conduct while Odette is in the party. */
    @Override
    public boolean enablesStellarConduct() {
        return true;
    }

    /** Enables conversion into Stellar-Swirl while Odette is in the party. */
    @Override
    public boolean enablesStellarSwirl() {
        return true;
    }

    /** Applies live owner Splendor, Burst, and C6 Stellar bonuses. */
    @Override
    public void applyPassive(StatsContainer stats) {
        double currentTime = initializedSimulator == null
                ? 0.0 : initializedSimulator.getCurrentTime();
        syncState(currentTime);
        double splendorBonus = selfSplendorStacks
                * getTalentValue("A1 Stellar DMG Bonus Per Stack", 0.15);
        addStellarDamageBonus(stats, splendorBonus);
        if (constellation >= 2) {
            stats.add(StatType.ATK_PERCENT,
                    selfSplendorStacks
                            * getTalentValue("C2 ATK Per Stack", 0.07));
        }
        if (currentTime + EPSILON < burstBonusUntil) {
            addStellarDamageBonus(stats, burstStellarBonus());
        }
        if (constellation >= 6) {
            addStellarSpecialBonus(stats,
                    getTalentValue("C6 Odette Stellar Special Bonus", 0.20));
        }
    }

    /** Resets the Normal string and starts off-field Splendor transfer. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        requireSimulator(simulator);
        syncState(simulator.getCurrentTime());
        normalAttackStep = 0;
        offField = true;
        lastSplendorTransferTime = simulator.getCurrentTime();
    }

    /** Resets the Normal string and stops off-field Splendor transfer. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        requireSimulator(simulator);
        syncState(simulator.getCurrentTime());
        normalAttackStep = 0;
        offField = false;
        lastSplendorTransferTime = simulator.getCurrentTime();
    }

    /** Dispatches Odette's sourced top-level actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Odette action is required");
        }
        requireSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Odette supports Press Skill only");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case CHARGE:
                performRegularHit(
                        simulator,
                        "Ode to the Snow Swan: Charged Attack",
                        getTalentValue("Charged Attack", 1.97342),
                        Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE);
                break;
            case SKILL:
                skill(simulator);
                break;
            case BURST:
                burst(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Odette: " + request.getKey());
        }
    }

    /**
     * Manually resolves one sourced Coda sequence.
     *
     * <p>The command is valid only while Odette is on field and the six-second
     * post-summon Coda window remains active. No-Radiance Coda defaults to
     * direct Stellar-Conduct; simultaneous Radiance also chooses Conduct.</p>
     *
     * @param simulator bound simulator
     */
    public void performCoda(CombatSimulator simulator) {
        requireSimulator(simulator);
        double currentTime = simulator.getCurrentTime();
        syncState(currentTime);
        if (offField || currentTime + EPSILON >= codaAvailableUntil
                || currentTime + EPSILON < nextCodaAt
                || !isDanceDoubleActive(currentTime)) {
            throw new IllegalStateException("Odette Coda is not available");
        }
        AttackAction.StellarReactionType type = codaRadianceType(simulator);
        performRegularHit(
                simulator,
                "The Swan's Dream: Coda",
                skillValue("Coda DoT", "C3 Coda DoT", 1.62928, 1.9168),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL);
        performDirectStellarHit(
                simulator,
                "The Swan's Dream: Coda " + type.name(),
                type == AttackAction.StellarReactionType.CONDUCT
                        ? skillValue(
                                "Coda Conduct",
                                "C3 Coda Conduct",
                                5.19792,
                                6.1152)
                        : skillValue(
                                "Coda Swirl",
                                "C3 Coda Swirl",
                                7.79688,
                                9.1728),
                type);
        if (constellation >= 1) {
            performDirectStellarHit(
                    simulator,
                    "The Swan's Dream: C1 Coda",
                    type == AttackAction.StellarReactionType.CONDUCT
                            ? getTalentValue("C1 Coda Conduct", 3.0)
                            : getTalentValue("C1 Coda Swirl", 4.5),
                    type);
        }
        codaAvailableUntil = Double.NEGATIVE_INFINITY;
        nextCodaAt = currentTime + getTalentValue("Coda Cooldown", 15.0);
        codaDanceEmpowered = true;
    }

    /**
     * Manually resolves one Plume hit and its eligible direct Stellar follow-up.
     *
     * @param simulator bound simulator
     */
    public void performPlume(CombatSimulator simulator) {
        performDanceHit(
                simulator,
                "Plume",
                "Plume Normal",
                "C3 Plume Normal",
                0.73168,
                0.8608,
                "Plume Conduct",
                "C3 Plume Conduct",
                0.459408,
                0.54048,
                "Plume Swirl",
                "C3 Plume Swirl",
                0.688976,
                0.81056);
    }

    /**
     * Manually resolves one Wing hit and its eligible direct Stellar follow-up.
     *
     * @param simulator bound simulator
     */
    public void performWing(CombatSimulator simulator) {
        performDanceHit(
                simulator,
                "Wing",
                "Wing Normal",
                "C3 Wing Normal",
                0.874888,
                1.02928,
                "Wing Conduct",
                "C3 Wing Conduct",
                0.549304,
                0.64624,
                "Wing Swirl",
                "C3 Wing Swirl",
                0.823888,
                0.96928);
    }

    /** Returns current owner Splendor after applying elapsed off-field ticks. */
    public int getSelfSplendorStacks(double currentTime) {
        syncState(currentTime);
        return selfSplendorStacks;
    }

    /** Returns current party Splendor after applying elapsed off-field ticks. */
    public int getTeamSplendorStacks(double currentTime) {
        syncState(currentTime);
        return teamSplendorStacks;
    }

    /** Returns whether the Dance Double exists in its sourced half-open window. */
    public boolean isDanceDoubleActive(double currentTime) {
        return currentTime + EPSILON < danceDoubleUntil;
    }

    /** Returns whether the six-second manual Coda window is active. */
    public boolean isCodaAvailable(double currentTime) {
        syncState(currentTime);
        return currentTime + EPSILON < codaAvailableUntil;
    }

    /** Returns whether Odette's sourced Burst Stellar bonus window is active. */
    public boolean isBurstBonusActive(double currentTime) {
        return currentTime + EPSILON < burstBonusUntil;
    }

    /** Returns permanent dynamic Stellar conversion and live team buffs. */
    @Override
    public List<Buff> getTeamBuffs() {
        List<Buff> buffs = new ArrayList<>();
        buffs.add(createBaseConversionBuff());
        buffs.add(createTeamSplendorBuff());
        buffs.add(createC2ResistanceBuff());
        buffs.add(createC4BurstBuff());
        return buffs;
    }

    private void normalAttack(CombatSimulator simulator) {
        int step = normalAttackStep;
        normalAttackStep = (normalAttackStep + 1) % NORMAL_ATTACK_COUNT;
        if (step == 2) {
            performRegularHit(
                    simulator,
                    "Ode to the Snow Swan: N3 Hit 1",
                    getTalentValue("N3 Hit 1", 0.599136),
                    Element.PHYSICAL,
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    ActionType.NORMAL);
            performRegularHit(
                    simulator,
                    "Ode to the Snow Swan: N3 Hit 2",
                    getTalentValue("N3 Hit 2", 0.703258),
                    Element.PHYSICAL,
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    ActionType.NORMAL);
            return;
        }
        String key;
        double fallback;
        switch (step) {
            case 0:
                key = "N1";
                fallback = 0.952724;
                break;
            case 1:
                key = "N2";
                fallback = 0.946357;
                break;
            case 3:
                key = "N4";
                fallback = 1.370065;
                break;
            case 4:
                key = "N5";
                fallback = 1.657388;
                break;
            default:
                throw new IllegalStateException("Unexpected Odette Normal step");
        }
        performRegularHit(
                simulator,
                "Ode to the Snow Swan: " + key,
                getTalentValue(key, fallback),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL);
    }

    private void skill(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        performRegularHit(
                simulator,
                "The Swan's Dream: Cast",
                skillValue("Skill Cast", "C3 Skill Cast", 1.83736, 2.1616),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL);
        markSkillUsed(currentTime, simulator.getApplicableBuffs(this));
        summonDanceDouble(currentTime);
    }

    private void burst(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        double slash = burstValue(
                "Burst Slash", "C5 Burst Slash", 1.872992, 2.20352);
        for (int hit = 1; hit <= 3; hit++) {
            performRegularHit(
                    simulator,
                    "Song of the Snow Swan: Slash " + hit,
                    slash,
                    Element.CRYO,
                    StatType.BURST_DMG_BONUS,
                    ActionType.BURST);
        }
        performRegularHit(
                simulator,
                "Song of the Snow Swan: Final",
                burstValue("Burst Final", "C5 Burst Final", 2.894624, 3.40544),
                Element.CRYO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST);
        markBurstUsed(currentTime, simulator.getApplicableBuffs(this));
        burstBonusUntil = currentTime
                + getTalentValue("Burst Stellar Bonus Duration", 20.0);
        summonDanceDouble(currentTime);
    }

    private void summonDanceDouble(double currentTime) {
        danceDoubleUntil = currentTime
                + getTalentValue("Dance Double Duration", 20.0);
        codaAvailableUntil = currentTime
                + getTalentValue("Coda Availability Duration", 6.0);
        codaDanceEmpowered = false;
        selfSplendorStacks = constellation >= 1
                ? (int) getTalentValue("C1 Initial Splendor Stacks", 6.0)
                : (int) getTalentValue("A1 Initial Splendor Stacks", 4.0);
        teamSplendorStacks = 0;
        lastSplendorTransferTime = currentTime;
    }

    private void performDanceHit(
            CombatSimulator simulator,
            String displayName,
            String normalKey,
            String c3NormalKey,
            double normalFallback,
            double c3NormalFallback,
            String conductKey,
            String c3ConductKey,
            double conductFallback,
            double c3ConductFallback,
            String swirlKey,
            String c3SwirlKey,
            double swirlFallback,
            double c3SwirlFallback) {
        requireSimulator(simulator);
        double currentTime = simulator.getCurrentTime();
        syncState(currentTime);
        if (!isDanceDoubleActive(currentTime)) {
            throw new IllegalStateException("Odette Dance Double is inactive");
        }
        performRegularHit(
                simulator,
                "The Swan's Dream: " + displayName,
                skillValue(normalKey, c3NormalKey, normalFallback, c3NormalFallback),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL);
        AttackAction.StellarReactionType type = activeRadianceType(
                simulator, currentTime);
        if (!codaDanceEmpowered || type == null) {
            return;
        }
        double multiplier = type == AttackAction.StellarReactionType.CONDUCT
                ? skillValue(
                        conductKey,
                        c3ConductKey,
                        conductFallback,
                        c3ConductFallback)
                : skillValue(
                        swirlKey,
                        c3SwirlKey,
                        swirlFallback,
                        c3SwirlFallback);
        performDirectStellarHit(
                simulator,
                "The Swan's Dream: " + displayName + " " + type.name(),
                multiplier,
                type);
    }

    private void performRegularHit(
            CombatSimulator simulator,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType) {
        AttackAction action = createAction(
                displayName,
                multiplier,
                hitElement,
                bonusStat,
                actionType);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void performDirectStellarHit(
            CombatSimulator simulator,
            String displayName,
            double multiplier,
            AttackAction.StellarReactionType type) {
        double currentTime = simulator.getCurrentTime();
        AttackAction action = createAction(
                displayName,
                multiplier,
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL);
        action.setStellarReactionType(type);
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        double a4Bonus = Math.min(
                getTalentValue("A4 Stellar Special Bonus Cap", 0.30),
                Math.max(
                        0.0,
                        stats.getTotalAtk()
                                - getTalentValue("A4 ATK Threshold", 1000.0))
                        * getTalentValue(
                                "A4 Stellar Special Bonus Per ATK", 0.00015));
        stats.add(
                specialStat(type),
                a4Bonus);
        action.setStatSnapshot(stats);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private AttackAction createAction(
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        return action;
    }

    private Buff createBaseConversionBuff() {
        return new Buff("Odette Stellar Base Conversion") {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                double bonus = Math.min(
                        getTalentValue("Stellar Base DMG Bonus Cap", 0.14),
                        Odette.this.getStructuralStats(currentTime).getTotalAtk()
                                * getTalentValue(
                                        "Stellar Base DMG Bonus Per ATK",
                                        0.00007));
                stats.add(StatType.STELLAR_CONDUCT_BASE_DMG_BONUS, bonus);
                stats.add(StatType.STELLAR_SWIRL_BASE_DMG_BONUS, bonus);
            }
        }.sourcedBy(characterId);
    }

    private Buff createTeamSplendorBuff() {
        return new Buff("Odette Shared Splendor") {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                syncState(currentTime);
                double splendor = teamSplendorStacks
                        * getTalentValue(
                                "A1 Stellar DMG Bonus Per Stack", 0.15);
                addStellarDamageBonus(stats, splendor);
                double special = 0.0;
                if (constellation >= 6 && teamSplendorStacks > 0) {
                    special += getTalentValue(
                            "C6 Team Stellar Special Bonus", 0.25);
                }
                addStellarSpecialBonus(stats, special);
                if (constellation >= 2) {
                    stats.add(StatType.ATK_PERCENT,
                            teamSplendorStacks
                                    * getTalentValue(
                                            "C2 ATK Per Stack", 0.07));
                }
            }
        }.exclude(characterId).sourcedBy(characterId);
    }

    private Buff createC2ResistanceBuff() {
        return new Buff("Odette C2 Dance Double RES Reduction") {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                if (constellation < 2
                        || initializedSimulator == null
                        || !isDanceDoubleActive(currentTime)) {
                    return;
                }
                AttackAction.StellarReactionType type = activeRadianceType(
                        initializedSimulator, currentTime);
                if (type == null) {
                    return;
                }
                double shred = getTalentValue("C2 RES Shred", 0.20);
                stats.add(StatType.CRYO_RES_SHRED, shred);
                stats.add(
                        type == AttackAction.StellarReactionType.CONDUCT
                                ? StatType.ELECTRO_RES_SHRED
                                : StatType.ANEMO_RES_SHRED,
                        shred);
            }
        }.sourcedBy(characterId);
    }

    private Buff createC4BurstBuff() {
        return new Buff("Odette C4 Snow Swan Echo") {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                if (constellation >= 4
                        && currentTime + EPSILON < burstBonusUntil) {
                    addStellarDamageBonus(
                            stats,
                            burstStellarBonus()
                                    * getTalentValue(
                                            "C4 Team Burst Bonus Ratio", 0.50));
                }
            }
        }.exclude(characterId).sourcedBy(characterId);
    }

    private void syncState(double currentTime) {
        if (currentTime + EPSILON >= danceDoubleUntil) {
            selfSplendorStacks = 0;
            teamSplendorStacks = 0;
            codaDanceEmpowered = false;
            codaAvailableUntil = Double.NEGATIVE_INFINITY;
            return;
        }
        if (!offField || selfSplendorStacks < 1) {
            return;
        }
        int elapsedTicks = (int) Math.floor(
                currentTime - lastSplendorTransferTime + EPSILON);
        if (elapsedTicks < 1) {
            return;
        }
        int perTick = constellation >= 1
                ? (int) getTalentValue("C1 Off-Field Transfer Per Second", 2.0)
                : (int) getTalentValue("A1 Off-Field Transfer Per Second", 1.0);
        int maximumStacks = constellation >= 1
                ? (int) getTalentValue("C1 Initial Splendor Stacks", 6.0)
                : (int) getTalentValue("A1 Initial Splendor Stacks", 4.0);
        int transferred = Math.min(
                selfSplendorStacks,
                elapsedTicks * perTick);
        teamSplendorStacks = Math.min(
                maximumStacks,
                teamSplendorStacks + transferred);
        if (constellation < 6) {
            selfSplendorStacks -= transferred;
        }
        lastSplendorTransferTime += elapsedTicks;
    }

    private AttackAction.StellarReactionType codaRadianceType(
            CombatSimulator simulator) {
        AttackAction.StellarReactionType active = activeRadianceType(
                simulator, simulator.getCurrentTime());
        return active == null
                ? AttackAction.StellarReactionType.CONDUCT
                : active;
    }

    private AttackAction.StellarReactionType activeRadianceType(
            CombatSimulator simulator,
            double currentTime) {
        if (simulator.getStellarReactionManager()
                .hasStellarConductRadiance(currentTime)) {
            return AttackAction.StellarReactionType.CONDUCT;
        }
        if (simulator.getStellarReactionManager()
                .hasStellarSwirlRadiance(currentTime)) {
            return AttackAction.StellarReactionType.SWIRL;
        }
        return null;
    }

    private double skillValue(
            String baseKey,
            String c3Key,
            double baseFallback,
            double c3Fallback) {
        return constellation >= 3
                ? getTalentValue(c3Key, c3Fallback)
                : getTalentValue(baseKey, baseFallback);
    }

    private double burstValue(
            String baseKey,
            String c5Key,
            double baseFallback,
            double c5Fallback) {
        return constellation >= 5
                ? getTalentValue(c5Key, c5Fallback)
                : getTalentValue(baseKey, baseFallback);
    }

    private double burstStellarBonus() {
        return constellation >= 5
                ? getTalentValue("C5 Burst Stellar DMG Bonus", 0.58)
                : getTalentValue("Burst Stellar DMG Bonus", 0.46);
    }

    private void handleAcceptedStellarDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        if (simulator != initializedSimulator
                || resolvingC4Coordinated
                || damage <= 0.0
                || actor == null
                || action == null
                || !action.isStellarConsidered()
                || !simulator.getPartyMembers().contains(actor)
                || currentTime + EPSILON < nextC4CoordinatedAt) {
            return;
        }
        AttackAction.StellarReactionType type = codaRadianceType(simulator);
        nextC4CoordinatedAt = currentTime
                + getTalentValue("C4 Coordinated Cooldown", 3.5);
        resolvingC4Coordinated = true;
        try {
            performDirectStellarHit(
                    simulator,
                    "Snow Swan's Dream: C4 Coordinated Attack",
                    type == AttackAction.StellarReactionType.CONDUCT
                            ? getTalentValue("C4 Coordinated Conduct", 0.66)
                            : getTalentValue("C4 Coordinated Swirl", 0.99),
                    type);
        } finally {
            resolvingC4Coordinated = false;
        }
    }

    private StatType specialStat(AttackAction.StellarReactionType type) {
        return type == AttackAction.StellarReactionType.CONDUCT
                ? StatType.STELLAR_CONDUCT_SPECIAL_DMG_BONUS
                : StatType.STELLAR_SWIRL_SPECIAL_DMG_BONUS;
    }

    private void addStellarSpecialBonus(
            StatsContainer stats,
            double bonus) {
        stats.add(StatType.STELLAR_CONDUCT_SPECIAL_DMG_BONUS, bonus);
        stats.add(StatType.STELLAR_SWIRL_SPECIAL_DMG_BONUS, bonus);
    }

    private void addStellarDamageBonus(
            StatsContainer stats,
            double bonus) {
        stats.add(StatType.STELLAR_CONDUCT_DMG_BONUS, bonus);
        stats.add(StatType.STELLAR_SWIRL_DMG_BONUS, bonus);
    }

    private void requireSimulator(CombatSimulator simulator) {
        initializeForSimulator(simulator);
    }

    private static final class OdetteState implements State {
        private final Odette owner;
        private final int normalAttackStep;
        private final double danceDoubleUntil;
        private final double codaAvailableUntil;
        private final double nextCodaAt;
        private final boolean codaDanceEmpowered;
        private final double burstBonusUntil;
        private final int selfSplendorStacks;
        private final int teamSplendorStacks;
        private final boolean offField;
        private final double lastSplendorTransferTime;
        private final double nextC4CoordinatedAt;

        private OdetteState(
                Odette owner,
                int normalAttackStep,
                double danceDoubleUntil,
                double codaAvailableUntil,
                double nextCodaAt,
                boolean codaDanceEmpowered,
                double burstBonusUntil,
                int selfSplendorStacks,
                int teamSplendorStacks,
                boolean offField,
                double lastSplendorTransferTime,
                double nextC4CoordinatedAt) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.danceDoubleUntil = danceDoubleUntil;
            this.codaAvailableUntil = codaAvailableUntil;
            this.nextCodaAt = nextCodaAt;
            this.codaDanceEmpowered = codaDanceEmpowered;
            this.burstBonusUntil = burstBonusUntil;
            this.selfSplendorStacks = selfSplendorStacks;
            this.teamSplendorStacks = teamSplendorStacks;
            this.offField = offField;
            this.lastSplendorTransferTime = lastSplendorTransferTime;
            this.nextC4CoordinatedAt = nextC4CoordinatedAt;
        }
    }
}
