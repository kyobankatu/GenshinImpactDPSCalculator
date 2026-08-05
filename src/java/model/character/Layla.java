package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

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
 * Layla's fixed-target offensive Night Star slice through C6.
 *
 * <p>Sword of the Radiant Path, Nights of Formal Focus's initial hit and
 * twelve-second offensive state, Night Star generation, four-projectile
 * Shooting Star volleys, particles, Dream of the Star-Stream Shaker, A4,
 * C2-C3, and C5-C6 follow pinned gcsim {@code ef41805d}. All Layla-owned
 * delayed work is reconstructable after simulator rollback.</p>
 *
 * <p>Curtain of Slumber absorption and strength, A1, C1, C4's one-hit team
 * flat-damage state, geometry, target selection, stamina, and hitlag are
 * excluded rather than approximated. The Curtain timestamp below represents
 * only the offensive Night Star lifecycle, not a defensive shield.</p>
 */
public final class Layla extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 13, 12, 31 };
    private static final int[] NORMAL_DURATIONS = { 33, 50, 74 };
    private static final double[] NORMAL_T9 = {
        0.940969, 0.890741, 1.340677
    };
    private static final int[] SHOOTING_STAR_TRAVEL_FRAMES = {
        35, 33, 30, 28
    };
    private static final int BURST_SLUG_COUNT = 8;

    private final DoubleSupplier particleRandom;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int nightStars;
    private boolean shootingStars;
    private long curtainGeneration;
    private long cadenceGeneration;
    private long volleyGeneration;
    private double curtainExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextSkillStarAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextBurstStarAllowedTime = Double.NEGATIVE_INFINITY;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Layla. */
    public Layla(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Layla at an explicit constellation. */
    public Layla(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Layla with injectable data and particle randomness.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param particleRandom source of particle draws in {@code [0, 1)}
     */
    public Layla(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleRandom) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Layla constellation must be between 0 and 6");
        }
        if (particleRandom == null) {
            throw new IllegalArgumentException(
                    "Layla particle random source is required");
        }
        name = "Layla";
        characterId = CharacterId.LAYLA;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleRandom = particleRandom;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11092.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 217.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 655.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 12.0));
        setBurstCD(getTalentValue("Burst Cooldown", 12.0));
    }

    /** Binds action observation and future events to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Layla simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Layla must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Layla cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addActionRequestListener((actor, request, time) ->
                observeSkillRequest(simulator, request, time));
    }

    /** Captures combo, Curtain, star gates, and reconstructable future work. */
    @Override
    public State captureCharacterState() {
        return new LaylaState(
                this,
                normalAttackStep,
                nightStars,
                shootingStars,
                curtainGeneration,
                cadenceGeneration,
                volleyGeneration,
                curtainExpirationTime,
                nextSkillStarAllowedTime,
                nextBurstStarAllowedTime,
                pendingEvents);
    }

    /** Accepts state captured from this exact Layla instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof LaylaState
                && ((LaylaState) state).owner == this;
    }

    /** Restores Layla-owned state and registers each future event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Layla state");
        }
        initializeForSimulator(simulator);
        LaylaState restored = (LaylaState) state;
        normalAttackStep = restored.normalAttackStep;
        nightStars = restored.nightStars;
        shootingStars = restored.shootingStars;
        curtainGeneration = restored.curtainGeneration;
        cadenceGeneration = restored.cadenceGeneration;
        volleyGeneration = restored.volleyGeneration;
        curtainExpirationTime = restored.curtainExpirationTime;
        nextSkillStarAllowedTime = restored.nextSkillStarAllowedTime;
        nextBurstStarAllowedTime = restored.nextBurstStarAllowedTime;
        pendingEvents = copyEvents(restored.pendingEvents);
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            scheduleEvent(simulator, event);
        }
    }

    /** Returns Layla's 40-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 40.0);
    }

    /** Layla has no unconditional offensive stat passive in this slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Only Nights of Formal Focus's Press form is represented. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS;
    }

    /** Resets only the on-field Normal string. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether the offensive Curtain lifecycle is active. */
    public boolean isCurtainActive(double currentTime) {
        return curtainGeneration > 0
                && currentTime + EPSILON < curtainExpirationTime;
    }

    /** Returns the current offensive Curtain expiration timestamp. */
    public double getCurtainExpirationTime() {
        return curtainExpirationTime;
    }

    /** Returns stored Night Stars, clearing expired non-volley state logically. */
    public int getNightStarCount(double currentTime) {
        return isCurtainActive(currentTime) || shootingStars
                ? nightStars : 0;
    }

    /** Returns whether a four-projectile Shooting Star volley is firing. */
    public boolean isShootingStars() {
        return shootingStars;
    }

    /** Returns the count of unresolved Layla-owned events. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /** Dispatches Layla's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Layla action is required");
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
            case PLUNGE:
                highPlunge(simulator);
                break;
            case SKILL:
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Layla Hold Skill is outside this slice");
                }
                nightsOfFormalFocus(simulator);
                break;
            case BURST:
                dreamOfTheStarStreamShaker(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Layla: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        int step = normalAttackStep;
        queueEvent(simulator, new PendingEvent(
                simulator.getCurrentTime()
                        + NORMAL_HIT_FRAMES[step] * FRAME,
                EventKind.NORMAL_HIT,
                step,
                0L,
                0.0));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueEvent(simulator, new PendingEvent(
                castTime + 16.0 * FRAME,
                EventKind.CHARGED_HIT,
                0,
                0L,
                0.0));
        queueEvent(simulator, new PendingEvent(
                castTime + 27.0 * FRAME,
                EventKind.CHARGED_HIT,
                1,
                0L,
                0.0));
        simulator.advanceTime(49.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        queueEvent(simulator, new PendingEvent(
                simulator.getCurrentTime() + 45.0 * FRAME,
                EventKind.HIGH_PLUNGE,
                0,
                0L,
                0.0));
        simulator.advanceTime(76.0 * FRAME);
    }

    private void nightsOfFormalFocus(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueEvent(simulator, new PendingEvent(
                castTime + 19.0 * FRAME,
                EventKind.SKILL_ACTIVATE,
                0,
                0L,
                0.0));
        queueEvent(simulator, new PendingEvent(
                castTime + 19.0 * FRAME,
                EventKind.SKILL_COOLDOWN,
                0,
                0L,
                0.0));
        queueEvent(simulator, new PendingEvent(
                castTime + 32.0 * FRAME,
                EventKind.SKILL_INITIAL,
                0,
                0L,
                0.0));
        simulator.advanceTime(43.0 * FRAME);
    }

    private void dreamOfTheStarStreamShaker(
            CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueEvent(simulator, new PendingEvent(
                castTime + 6.0 * FRAME,
                EventKind.BURST_ENERGY,
                0,
                0L,
                0.0));
        for (int slug = 0; slug < BURST_SLUG_COUNT; slug++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + (114.0 + slug * 90.0) * FRAME,
                    EventKind.BURST_HIT,
                    slug,
                    0L,
                    0.0));
        }
        simulator.advanceTime(79.0 * FRAME);
    }

    private void observeSkillRequest(
            CombatSimulator simulator,
            CharacterActionRequest request,
            double time) {
        if (simulator != initializedSimulator
                || request == null
                || request.getKey() != CharacterActionKey.SKILL
                || !isCurtainActive(time)) {
            return;
        }
        addNightStars(simulator, 2, StarSource.SKILL, time);
    }

    private void activateCurtain(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        boolean replacing = isCurtainActive(currentTime);
        long generation = ++curtainGeneration;
        curtainExpirationTime = currentTime
                + getTalentValue("Curtain Duration", 12.0);
        if (!replacing && !shootingStars) {
            nightStars = 0;
        }
        queueEvent(simulator, new PendingEvent(
                curtainExpirationTime,
                EventKind.CURTAIN_EXPIRE,
                0,
                generation,
                0.0));
        restartCadence(simulator, currentTime);
    }

    private void restartCadence(
            CombatSimulator simulator,
            double currentTime) {
        long generation = ++cadenceGeneration;
        queueEvent(simulator, new PendingEvent(
                currentTime + nightStarInterval(),
                EventKind.STAR_TICK,
                0,
                generation,
                0.0));
    }

    private void cadenceTick(
            CombatSimulator simulator,
            PendingEvent event) {
        double currentTime = simulator.getCurrentTime();
        if (event.generation != cadenceGeneration
                || !isCurtainActive(currentTime)) {
            return;
        }
        addNightStars(simulator, 1, StarSource.CADENCE, currentTime);
        if (event.generation == cadenceGeneration
                && isCurtainActive(currentTime)) {
            queueEvent(simulator, new PendingEvent(
                    currentTime + nightStarInterval(),
                    EventKind.STAR_TICK,
                    0,
                    event.generation,
                    0.0));
        }
    }

    private void addNightStars(
            CombatSimulator simulator,
            int count,
            StarSource source,
            double currentTime) {
        if (!isCurtainActive(currentTime) || shootingStars) {
            return;
        }
        if (source == StarSource.SKILL) {
            if (currentTime + EPSILON < nextSkillStarAllowedTime) {
                return;
            }
            nextSkillStarAllowedTime = currentTime + getTalentValue(
                    "Skill Night Star ICD", 0.3);
        } else if (source == StarSource.BURST) {
            if (currentTime + EPSILON < nextBurstStarAllowedTime) {
                return;
            }
            nextBurstStarAllowedTime = currentTime + getTalentValue(
                    "Burst Night Star ICD", 0.5);
        }
        nightStars = Math.min(4, nightStars + count);
        if (nightStars == 4) {
            startShootingStars(simulator, currentTime);
        }
    }

    private void startShootingStars(
            CombatSimulator simulator,
            double currentTime) {
        shootingStars = true;
        cadenceGeneration++;
        long generation = ++volleyGeneration;
        queueEvent(simulator, new PendingEvent(
                currentTime + 0.1,
                EventKind.STAR_ACQUIRE_TARGET,
                0,
                generation,
                0.0));
    }

    private void acquireShootingStarTarget(
            CombatSimulator simulator,
            PendingEvent event) {
        if (event.generation != volleyGeneration || !shootingStars) {
            return;
        }
        queueEvent(simulator, new PendingEvent(
                simulator.getCurrentTime() + 0.5,
                EventKind.STAR_FIRE,
                0,
                event.generation,
                0.0));
    }

    private void fireShootingStar(
            CombatSimulator simulator,
            PendingEvent event) {
        if (event.generation != volleyGeneration
                || !shootingStars
                || nightStars <= 0) {
            return;
        }
        int projectile = 4 - nightStars;
        StatsContainer stats = captureLiveStats(simulator.getCurrentTime());
        double a4Damage = stats.getTotalHp()
                * getTalentValue("A4 Max HP Ratio", 0.015);
        queueEvent(simulator, new PendingEvent(
                simulator.getCurrentTime()
                        + SHOOTING_STAR_TRAVEL_FRAMES[projectile] * FRAME,
                EventKind.SHOOTING_STAR_HIT,
                projectile,
                event.generation,
                a4Damage));
        nightStars--;
        if (nightStars > 0) {
            queueEvent(simulator, new PendingEvent(
                    simulator.getCurrentTime() + 0.45,
                    EventKind.STAR_FIRE,
                    projectile + 1,
                    event.generation,
                    0.0));
            return;
        }
        shootingStars = false;
        if (isCurtainActive(simulator.getCurrentTime())) {
            restartCadence(simulator, simulator.getCurrentTime());
        }
    }

    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.kind) {
            case NORMAL_HIT:
                performHit(
                        simulator,
                        "Sword of the Radiant Path N" + (event.index + 1),
                        getTalentValue(
                                "N" + (event.index + 1),
                                NORMAL_T9[event.index]),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0,
                        0.0,
                        false);
                break;
            case CHARGED_HIT:
                performHit(
                        simulator,
                        "Sword of the Radiant Path Charged Hit "
                                + (event.index + 1),
                        getTalentValue(
                                "Charged Hit " + (event.index + 1),
                                event.index == 0 ? 0.876900 : 0.965380),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        0.0,
                        0.0,
                        false);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        "Sword of the Radiant Path High Plunge",
                        getTalentValue("High Plunge", 2.933586),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        0.0,
                        0.0,
                        false);
                break;
            case SKILL_ACTIVATE:
                activateCurtain(simulator);
                break;
            case SKILL_COOLDOWN:
                markSkillUsed(
                        simulator.getCurrentTime(),
                        simulator.getApplicableBuffs(this));
                break;
            case SKILL_INITIAL:
                performHit(
                        simulator,
                        "Nights of Formal Focus",
                        skillValue("Nights of Formal Focus",
                                0.217600, 0.256000),
                        Element.CRYO,
                        StatType.BASE_ATK,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        1.0,
                        0.0,
                        false);
                break;
            case CURTAIN_EXPIRE:
                if (event.generation == curtainGeneration) {
                    cadenceGeneration++;
                    if (!shootingStars) {
                        nightStars = 0;
                    }
                }
                break;
            case STAR_TICK:
                cadenceTick(simulator, event);
                break;
            case STAR_ACQUIRE_TARGET:
                acquireShootingStarTarget(simulator, event);
                break;
            case STAR_FIRE:
                fireShootingStar(simulator, event);
                break;
            case SHOOTING_STAR_HIT:
                resolveShootingStarHit(simulator, event);
                break;
            case PARTICLE:
                simulator.getEnergyDistributor().distributeParticles(
                        Element.CRYO,
                        event.value,
                        ParticleType.PARTICLE);
                break;
            case BURST_ENERGY:
                spendBurstEnergy(simulator.getCurrentTime());
                break;
            case BURST_HIT:
                resolveBurstHit(simulator);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Layla event " + event.kind);
        }
    }

    private void resolveShootingStarHit(
            CombatSimulator simulator,
            PendingEvent event) {
        performHit(
                simulator,
                "Shooting Star " + (event.index + 1),
                skillValue("Shooting Star", 0.250240, 0.294400),
                Element.CRYO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                event.index == 0 ? 1.0 : 0.0,
                event.value,
                constellation >= 6);
        if (constellation >= 2) {
            receiveFlatEnergy(getTalentValue(
                    "C2 Energy Per Star", 1.0));
        }
        if (event.index == 0) {
            double draw = particleRandom.getAsDouble();
            if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
                throw new IllegalStateException(
                        "Layla particle random draw must be in [0, 1)");
            }
            double count = draw < getTalentValue(
                    "Particle Chance Two", 0.33) ? 2.0 : 1.0;
            queueEvent(simulator, new PendingEvent(
                    simulator.getCurrentTime()
                            + getTalentValue(
                                    "Particle Travel Frames", 100.0)
                                    * FRAME,
                    EventKind.PARTICLE,
                    0,
                    0L,
                    count));
        }
    }

    private void resolveBurstHit(CombatSimulator simulator) {
        performHit(
                simulator,
                "Starlight Slug",
                burstValue(),
                Element.CRYO,
                StatType.BASE_HP,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0,
                0.0,
                constellation >= 6);
        addNightStars(
                simulator,
                1,
                StarSource.BURST,
                simulator.getCurrentTime());
    }

    private void performHit(
            CombatSimulator simulator,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            double fixedBaseDamage,
            boolean c6Bonus) {
        AttackAction action = fixedBaseDamage == 0.0
                ? new AttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        scalingStat,
                        bonusStat,
                        0.0,
                        actionType)
                : new LaylaAttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        scalingStat,
                        bonusStat,
                        actionType,
                        fixedBaseDamage);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (c6Bonus) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    getTalentValue(
                            actionType == ActionType.SKILL
                                    ? "C6 Shooting Star DMG Bonus"
                                    : "C6 Starlight Slug DMG Bonus",
                            0.40));
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private double skillValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue(
                key + suffix,
                constellation >= 3 ? talentTwelve : talentNine);
    }

    private double burstValue() {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(
                "Starlight Slug" + suffix,
                constellation >= 5 ? 0.092976 : 0.079030);
    }

    private double nightStarInterval() {
        return getTalentValue(
                constellation >= 6
                        ? "C6 Night Star Interval"
                        : "Night Star Interval",
                constellation >= 6 ? 1.2 : 1.5);
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
        CHARGED_HIT,
        HIGH_PLUNGE,
        SKILL_ACTIVATE,
        SKILL_COOLDOWN,
        SKILL_INITIAL,
        CURTAIN_EXPIRE,
        STAR_TICK,
        STAR_ACQUIRE_TARGET,
        STAR_FIRE,
        SHOOTING_STAR_HIT,
        PARTICLE,
        BURST_ENERGY,
        BURST_HIT
    }

    private enum StarSource {
        CADENCE,
        SKILL,
        BURST
    }

    /** Preserves A4's fire-time Max-HP addition through resolution. */
    private static final class LaylaAttackAction extends AttackAction {
        private final double fixedBaseDamage;

        private LaylaAttackAction(
                String displayName,
                double multiplier,
                Element element,
                StatType scalingStat,
                StatType bonusStat,
                ActionType actionType,
                double fixedBaseDamage) {
            super(
                    displayName,
                    multiplier,
                    element,
                    scalingStat,
                    bonusStat,
                    0.0,
                    actionType);
            this.fixedBaseDamage = fixedBaseDamage;
        }

        @Override
        public void setAdditiveBaseDmgBonus(double value) {
        }

        @Override
        public double getAdditiveBaseDmgBonus() {
            return fixedBaseDamage;
        }
    }

    /** Immutable reconstructable Layla-owned event. */
    private static final class PendingEvent {
        private final double time;
        private final EventKind kind;
        private final int index;
        private final long generation;
        private final double value;

        private PendingEvent(
                double time,
                EventKind kind,
                int index,
                long generation,
                double value) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.value = value;
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time, kind, index, generation, value);
        }
    }

    /** Immutable snapshot of all Layla-owned mutable runtime state. */
    private static final class LaylaState implements State {
        private final Layla owner;
        private final int normalAttackStep;
        private final int nightStars;
        private final boolean shootingStars;
        private final long curtainGeneration;
        private final long cadenceGeneration;
        private final long volleyGeneration;
        private final double curtainExpirationTime;
        private final double nextSkillStarAllowedTime;
        private final double nextBurstStarAllowedTime;
        private final List<PendingEvent> pendingEvents;

        private LaylaState(
                Layla owner,
                int normalAttackStep,
                int nightStars,
                boolean shootingStars,
                long curtainGeneration,
                long cadenceGeneration,
                long volleyGeneration,
                double curtainExpirationTime,
                double nextSkillStarAllowedTime,
                double nextBurstStarAllowedTime,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.nightStars = nightStars;
            this.shootingStars = shootingStars;
            this.curtainGeneration = curtainGeneration;
            this.cadenceGeneration = cadenceGeneration;
            this.volleyGeneration = volleyGeneration;
            this.curtainExpirationTime = curtainExpirationTime;
            this.nextSkillStarAllowedTime = nextSkillStarAllowedTime;
            this.nextBurstStarAllowedTime = nextBurstStarAllowedTime;
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}
