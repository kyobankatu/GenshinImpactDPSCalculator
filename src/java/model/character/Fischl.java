package model.character;

import java.util.EnumSet;
import java.util.Set;

import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.FormStateProvider;
import model.entity.SimulatorInitializedCharacterEffect;
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
 * Fischl offensive implementation for stationary single-target combat.
 *
 * <p>
 * Bolts of Downfall uses talent-9 values. Nightrider snapshots its initial
 * summon, 59-frame Oz attacks, expected particle output, ten-second base
 * lifetime, and typed Skill recasts that refresh the snapshot and attack timer
 * without extending the lifetime. Midnight Phantasmagoria deals one contact
 * hit and redeploys Oz after its atomic full form ends. A4 and the
 * representable offensive portions of C1-C6 are included.
 *
 * <p>
 * The typed request exposes only a fully charged aimed shot for Charge and has
 * no separate Skill press/hold/recast parameter, so a Skill request while Oz
 * is active deterministically selects the sourced recast. Weak-point A1,
 * geometry and multi-enemy targeting, C4 healing, Witch/Hexerei effects,
 * hitlag-based modifier extension, and pending timer reconstruction in
 * simulator snapshots are intentionally excluded. Oz particle RNG is
 * represented by its sourced 0.67 expected particles per periodic attack.
 */
public class Fischl extends Character implements
        FormStateProvider,
        SimulatorInitializedCharacterEffect,
        CombatSimulator.ReactionListener {
    private static final double SKILL_COOLDOWN = 25.0;
    private static final double BURST_COOLDOWN = 15.0;
    private static final double BASE_OZ_DURATION = 10.0;
    private static final double C6_OZ_DURATION = 12.0;
    private static final double OZ_TICK_INTERVAL = 59.0 / 60.0;
    private static final double OZ_EXPECTED_PARTICLES = 0.67;
    private static final double RECAST_COOLDOWN = 92.0 / 60.0;
    private static final double SHARED_ICD_DURATION = 5.0;
    private static final int SHARED_ICD_HIT_COUNT = 4;
    private static final double EPSILON = 1e-9;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile AIMED_HEADSHOT_HITLAG =
            new HitlagProfile(0.12, 0.01, false, true, true);
    private static final Set<ReactionResult.Kind> ELECTRO_REACTIONS =
            EnumSet.of(
                    ReactionResult.Kind.OVERLOAD,
                    ReactionResult.Kind.OVERLOADED,
                    ReactionResult.Kind.ELECTRO_CHARGED,
                    ReactionResult.Kind.SUPERCONDUCT,
                    ReactionResult.Kind.QUICKEN,
                    ReactionResult.Kind.AGGRAVATE,
                    ReactionResult.Kind.HYPERBLOOM,
                    ReactionResult.Kind.LUNAR_CHARGED);

    private int normalAttackStep;
    private CombatSimulator initializedSimulator;
    private boolean ozActive;
    private double ozActiveUntil = Double.NEGATIVE_INFINITY;
    private double nextFullSkillReadyTime = Double.NEGATIVE_INFINITY;
    private double burstFormUntil = Double.NEGATIVE_INFINITY;
    private double nextA4Time = Double.NEGATIVE_INFINITY;
    private double lastCoordinatedNormalTime = Double.NEGATIVE_INFINITY;
    private double sharedIcdLastApplication = Double.NEGATIVE_INFINITY;
    private int sharedIcdHitCount;
    private long ozLifetimeGeneration;
    private long ozAttackGeneration;
    private long burstFormGeneration;
    private boolean resolvingCoordinatedAttack;

    /**
     * Constructs Fischl with the shared talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     */
    public Fischl(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    /**
     * Constructs Fischl with an explicit talent source for deterministic tests.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     */
    public Fischl(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData) {
        super(talentData);
        this.name = "Fischl";
        this.characterId = CharacterId.FISCHL;
        this.element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = (int) getTalentValue("Constellation", 6.0);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Fischl constellation must be between 0 and 6");
        }

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9189.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 244.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 594.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK", 0.24));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /**
     * Registers A4 and coordinated-attack listeners for this simulator.
     *
     * @param sim simulator receiving Fischl
     * @throws IllegalStateException if this instance is reused across simulators
     */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Fischl cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;
        sim.addReactionListener(this);
        sim.addDamageListener((actor, action, damage, time) ->
                handleNormalDamage(actor, action, time, sim));
    }

    /** Returns Fischl's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Fischl has no unconditional static passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /**
     * Reports whether Midnight Phantasmagoria's movement form remains active.
     *
     * @param currentTime current simulation time in seconds
     * @return whether the Burst form is active
     */
    @Override
    public boolean isFormActive(double currentTime) {
        return currentTime + EPSILON < burstFormUntil;
    }

    /**
     * Reports whether Oz is currently deployed.
     *
     * @param currentTime current simulation time in seconds
     * @return whether Oz remains within his half-open lifetime
     */
    public boolean isOzActive(double currentTime) {
        return ozActive && currentTime + EPSILON < ozActiveUntil;
    }

    /** Returns Oz's current expiry timestamp, or negative infinity if absent. */
    public double getOzActiveUntil() {
        return ozActiveUntil;
    }

    /**
     * Dispatches Fischl's typed player actions.
     *
     * <p>The runtime executes a typed Burst atomically, so gcsim's 24-frame
     * early-swap cancel cannot be requested while the action is in flight.
     */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        initializeForSimulator(sim);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(sim);
                break;
            case CHARGE:
                fullyChargedAimedShot(sim);
                break;
            case SKILL:
                if (isOzActive(sim.getCurrentTime())) {
                    recastOz(sim);
                } else {
                    summonOz(sim);
                }
                break;
            case BURST:
                midnightPhantasmagoria(sim);
                break;
            case PLUNGE:
                highPlunge(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Fischl: " + request.getKey());
        }
    }

    /** Handles A4 reaction triggers from the simulator's typed reaction bus. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (!isOzActive(time)
                || source == null
                || source != sim.getActiveCharacter()
                || time + EPSILON < nextA4Time
                || !isElectroRelatedReaction(result)) {
            return;
        }
        nextA4Time = time + getTalentValue("A4 Cooldown", 0.50);
        schedule(sim, time + 4.0 / 60.0, activeSim -> {
            AttackAction retaliation = new AttackAction(
                    "Thundering Retribution",
                    getTalentValue("A4 Thundering Retribution", 0.80),
                    Element.ELECTRO,
                    StatType.BASE_ATK,
                    StatType.SKILL_DMG_BONUS,
                    0.0,
                    true,
                    ActionType.SKILL);
            retaliation.setICD(
                    ICDType.None, ICDTag.ElementalSkill, 1.0);
            activeSim.performActionWithoutTimeAdvance(
                    characterId, retaliation);
        });
    }

    private void normalAttack(CombatSimulator sim) {
        String key = "N" + (normalAttackStep + 1);
        double[] defaults = {
                0.81054, 0.85952, 1.06808, 1.06018, 1.32404
        };
        double[] durations = {
                25.0 / 60.0,
                22.0 / 60.0,
                38.0 / 60.0,
                32.0 / 60.0,
                67.0 / 60.0
        };
        AttackAction normal = new AttackAction(
                "Bolts of Downfall " + key,
                getTalentValue(key, defaults[normalAttackStep]),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                durations[normalAttackStep],
                ActionType.NORMAL);
        normal.setICD(ICDType.Standard, ICDTag.NormalAttack, 0.0);
        sim.performAction(characterId, normal);
        normalAttackStep = (normalAttackStep + 1) % defaults.length;
    }

    private void fullyChargedAimedShot(CombatSimulator sim) {
        AttackAction charged = new AttackAction(
                "Fully Charged Aimed Shot",
                getTalentValue("Fully Charged Aimed Shot", 2.108),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                96.0 / 60.0,
                ActionType.CHARGE);
        charged.setICD(ICDType.None, ICDTag.ChargedAttack, 1.0);
        charged.setHitlagProfile(AIMED_HEADSHOT_HITLAG);
        sim.performAction(characterId, charged);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction plunge = new AttackAction(
                "Bolts of Downfall High Plunge",
                getTalentValue("Plunge High", 2.6086),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                58.0 / 60.0,
                ActionType.PLUNGE);
        plunge.setICD(ICDType.None, ICDTag.PlungeAttack, 0.0);
        sim.performAction(characterId, plunge);
    }

    private void summonOz(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        nextFullSkillReadyTime = castTime + 18.0 / 60.0 + SKILL_COOLDOWN;
        schedule(sim, castTime + 18.0 / 60.0, activeSim -> {
            deployOz(activeSim, activeSim.getCurrentTime(), 75.0 / 60.0);
            exposeOzRecast(
                    activeSim,
                    activeSim.getCurrentTime(),
                    RECAST_COOLDOWN);
        });
        schedule(sim, castTime + 38.0 / 60.0, activeSim -> {
            double baseMultiplier = constellation >= 3 ? 2.3088 : 1.96248;
            double c2Bonus = constellation >= 2
                    ? getTalentValue("C2 Summon Bonus", 2.0) : 0.0;
            AttackAction summon = new AttackAction(
                    "Oz Summon",
                    getTalentValue("Oz Summon", baseMultiplier) + c2Bonus,
                    Element.ELECTRO,
                    StatType.BASE_ATK,
                    StatType.SKILL_DMG_BONUS,
                    0.0,
                    true,
                    ActionType.SKILL);
            summon.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);
            activeSim.performActionWithoutTimeAdvance(characterId, summon);
        });
        sim.advanceTime(43.0 / 60.0);
    }

    private void recastOz(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        setSkillCD(RECAST_COOLDOWN);
        markSkillUsed(castTime, sim.getApplicableBuffs(this));
        setSkillCD(SKILL_COOLDOWN);
        schedule(sim, castTime + 2.0 / 60.0, activeSim -> {
            if (!isOzActive(activeSim.getCurrentTime())) {
                return;
            }
            captureSnapshot(
                    activeSim.getCurrentTime(),
                    activeSim.getApplicableBuffs(this));
            ozAttackGeneration++;
            scheduleOzTicks(
                    activeSim,
                    ozAttackGeneration,
                    activeSim.getCurrentTime() + 70.0 / 60.0);
        });
        sim.advanceTime(37.0 / 60.0);
    }

    private void midnightPhantasmagoria(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        markBurstUsed(castTime, sim.getApplicableBuffs(this));
        invalidateOz(castTime);
        long formGeneration = ++burstFormGeneration;
        burstFormUntil = castTime + 113.0 / 60.0;

        if (constellation >= 4) {
            schedule(sim, castTime + 8.0 / 60.0, activeSim -> {
                AttackAction c4 = new AttackAction(
                        "Her Pilgrimage of Bleak",
                        getTalentValue("C4 Burst Damage", 2.22),
                        Element.ELECTRO,
                        StatType.BASE_ATK,
                        StatType.BURST_DMG_BONUS,
                        0.0,
                        ActionType.BURST);
                c4.setICD(ICDType.Standard, ICDTag.ElementalBurst, 2.0);
                activeSim.performActionWithoutTimeAdvance(characterId, c4);
            });
        }
        schedule(sim, castTime + 18.0 / 60.0, activeSim -> {
            AttackAction burst = new AttackAction(
                    "Midnight Phantasmagoria",
                    getTalentValue(
                            "Midnight Phantasmagoria",
                            constellation >= 5 ? 4.16 : 3.536),
                    Element.ELECTRO,
                    StatType.BASE_ATK,
                    StatType.BURST_DMG_BONUS,
                    0.0,
                    ActionType.BURST);
            burst.setICD(ICDType.Standard, ICDTag.ElementalBurst, 1.0);
            activeSim.performActionWithoutTimeAdvance(characterId, burst);
        });
        schedule(sim, burstFormUntil, activeSim -> {
            if (formGeneration != burstFormGeneration) {
                return;
            }
            deployOz(activeSim, activeSim.getCurrentTime(), 79.0 / 60.0);
            exposeOzRecast(activeSim, activeSim.getCurrentTime(), 0.0);
            burstFormUntil = Double.NEGATIVE_INFINITY;
        });
        sim.advanceTime(148.0 / 60.0);
    }

    private void deployOz(
            CombatSimulator sim,
            double deploymentTime,
            double firstTickDelay) {
        captureSnapshot(deploymentTime, sim.getApplicableBuffs(this));
        ozActive = true;
        ozActiveUntil = deploymentTime + (constellation >= 6
                ? getTalentValue("C6 Oz Duration", C6_OZ_DURATION)
                : getTalentValue("Oz Duration", BASE_OZ_DURATION));
        long lifetimeGeneration = ++ozLifetimeGeneration;
        long attackGeneration = ++ozAttackGeneration;
        scheduleOzTicks(sim, attackGeneration, deploymentTime + firstTickDelay);
        schedule(sim, ozActiveUntil, activeSim -> {
            if (lifetimeGeneration != ozLifetimeGeneration) {
                return;
            }
            ozActive = false;
            restoreFullSkillCooldown(activeSim);
        });
    }

    private void scheduleOzTicks(
            CombatSimulator sim,
            long attackGeneration,
            double firstTickTime) {
        sim.registerEvent(new SimpleTimerEvent(
                firstTickTime,
                getTalentValue("Oz Tick Interval", OZ_TICK_INTERVAL)) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                if (attackGeneration != ozAttackGeneration
                        || !isOzActive(activeSim.getCurrentTime())) {
                    finish();
                    return;
                }
                AttackAction ozAttack = new AttackAction(
                        "Oz Attack",
                        getTalentValue(
                                "Oz Attack",
                                constellation >= 3 ? 1.776 : 1.5096),
                        Element.ELECTRO,
                        StatType.BASE_ATK,
                        StatType.SKILL_DMG_BONUS,
                        0.0,
                        true,
                        ActionType.SKILL);
                ozAttack.setICD(
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        sharedOzGauge(activeSim.getCurrentTime()));
                activeSim.performActionWithoutTimeAdvance(
                        characterId, ozAttack);
                activeSim.getEnergyDistributor().distributeParticles(
                        Element.ELECTRO,
                        getTalentValue(
                                "Oz Expected Particles",
                                OZ_EXPECTED_PARTICLES),
                        ParticleType.PARTICLE);
            }
        });
    }

    private void handleNormalDamage(
            Character actor,
            AttackAction action,
            double time,
            CombatSimulator sim) {
        if (resolvingCoordinatedAttack
                || action.getActionType() != ActionType.NORMAL
                || actor != sim.getActiveCharacter()
                || Math.abs(time - lastCoordinatedNormalTime) <= EPSILON) {
            return;
        }
        lastCoordinatedNormalTime = time;
        resolvingCoordinatedAttack = true;
        try {
            if (constellation >= 6 && isOzActive(time)) {
                schedule(sim, time + 10.0 / 60.0, activeSim -> {
                    AttackAction c6 = new AttackAction(
                            "Evernight Raven",
                            getTalentValue("C6 Coordinated Attack", 0.30),
                            Element.ELECTRO,
                            StatType.BASE_ATK,
                            StatType.SKILL_DMG_BONUS,
                            0.0,
                            true,
                            ActionType.SKILL);
                    c6.setICD(
                            ICDType.None,
                            ICDTag.ElementalSkill,
                            sharedOzGauge(activeSim.getCurrentTime()));
                    activeSim.performActionWithoutTimeAdvance(
                            characterId, c6);
                });
            } else if (constellation >= 1
                    && actor == this
                    && !isOzActive(time)) {
                AttackAction c1 = new AttackAction(
                        "Gaze of the Deep",
                        getTalentValue("C1 Coordinated Attack", 0.22),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        0.0,
                        ActionType.NORMAL);
                c1.setICD(ICDType.None, ICDTag.NormalAttack, 0.0);
                sim.performActionWithoutTimeAdvance(characterId, c1);
            }
        } finally {
            resolvingCoordinatedAttack = false;
        }
    }

    private double sharedOzGauge(double time) {
        boolean durationReady = time - sharedIcdLastApplication
                + EPSILON >= SHARED_ICD_DURATION;
        boolean hitReady = sharedIcdHitCount + 1 >= SHARED_ICD_HIT_COUNT;
        if (durationReady || hitReady) {
            sharedIcdLastApplication = time;
            sharedIcdHitCount = 1;
            return 1.0;
        }
        sharedIcdHitCount++;
        return 0.0;
    }

    private boolean isElectroRelatedReaction(ReactionResult result) {
        if (result == null || result.getKind() == ReactionResult.Kind.NONE) {
            return false;
        }
        if (ELECTRO_REACTIONS.contains(result.getKind())) {
            return true;
        }
        return (result.getKind() == ReactionResult.Kind.SWIRL
                || result.getKind() == ReactionResult.Kind.CRYSTALLIZE)
                && result.getRelatedElement() == Element.ELECTRO;
    }

    private void exposeOzRecast(
            CombatSimulator sim,
            double time,
            double recastCooldown) {
        if (recastCooldown <= EPSILON) {
            resetSkillCooldown(time);
        } else {
            setSkillCD(recastCooldown);
            markSkillUsed(time, sim.getApplicableBuffs(this));
        }
        setSkillCD(SKILL_COOLDOWN);
    }

    private void restoreFullSkillCooldown(CombatSimulator sim) {
        double remaining = Math.max(
                0.0,
                nextFullSkillReadyTime - sim.getCurrentTime());
        if (remaining <= EPSILON) {
            resetSkillCooldown(sim.getCurrentTime());
            return;
        }
        setSkillCD(remaining);
        markSkillUsed(
                sim.getCurrentTime(),
                sim.getApplicableBuffs(this));
        setSkillCD(SKILL_COOLDOWN);
    }

    private void invalidateOz(double time) {
        ozActive = false;
        ozActiveUntil = time;
        ozLifetimeGeneration++;
        ozAttackGeneration++;
    }

    private void schedule(
            CombatSimulator sim,
            double time,
            java.util.function.Consumer<CombatSimulator> effect) {
        sim.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                effect.accept(activeSim);
            }
        });
    }
}
