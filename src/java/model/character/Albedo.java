package model.character;

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
 * Albedo offensive implementation for stationary single-target combat.
 *
 * <p>Favonius Bladework - Weiss uses talent-9 values. Abiogenesis: Solar
 * Isotoma snapshots at cast, deploys at its sourced hitmark, persists for 30
 * seconds, and lets positive true hits from any party member generate one
 * Transient Blossom every two seconds. The expected 0.67 Geo particles are
 * distributed on each Blossom. Rite of Progeniture uses one targeted Fatal
 * Blossom in the single-target abstraction; the other six geometry-dependent
 * Blossoms are treated as misses.</p>
 *
 * <p>The old base-kit A4 and representable C1-C5 effects are included. Enemy
 * HP-dependent A1, C6 shield checks, Hexerei and Silver Isotoma additions,
 * placement, construct durability, multi-target geometry, and hitlag extension are
 * intentionally excluded.</p>
 */
public class Albedo extends Character implements
        SimulatorInitializedCharacterEffect {
    private static final double SKILL_COOLDOWN = 4.0;
    private static final double BURST_COOLDOWN = 12.0;
    private static final double FIELD_DURATION = 30.0;
    private static final double BLOSSOM_COOLDOWN = 2.0;
    private static final double BLOSSOM_DELAY = 1.0 / 60.0;
    private static final double EXPECTED_PARTICLES = 0.67;
    private static final double FATAL_RECKONING_DURATION = 30.0;
    private static final int MAX_FATAL_RECKONING_STACKS = 4;
    private static final double EPSILON = 1e-9;

    /**
     * Normal-attack hitlag from gcsim config YAML pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile[] NORMAL_HITLAG = {
        new HitlagProfile(0.03, 0.01, true, false, false),
        new HitlagProfile(0.03, 0.01, true, false, false),
        new HitlagProfile(0.06, 0.01, true, false, false),
        new HitlagProfile(0.09, 0.01, true, false, false),
        new HitlagProfile(0.12, 0.01, true, false, false)
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private boolean solarIsotomaActive;
    private double solarIsotomaExpiresAt = Double.NEGATIVE_INFINITY;
    private double nextTransientBlossomTime = Double.NEGATIVE_INFINITY;
    private long solarIsotomaGeneration;
    private int fatalReckoningStacks;
    private double fatalReckoningExpiresAt = Double.NEGATIVE_INFINITY;
    private boolean suppressTransientBlossomTrigger;

    /**
     * Constructs Albedo with the shared talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     */
    public Albedo(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    /**
     * Constructs Albedo with an explicit talent source for deterministic tests.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     */
    public Albedo(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData) {
        super(talentData);
        this.name = "Albedo";
        this.characterId = CharacterId.ALBEDO;
        this.element = Element.GEO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = (int) getTalentValue("Constellation", 6.0);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Albedo constellation must be between 0 and 6");
        }

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13226.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 251.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 876.0));
        baseStats.add(StatType.GEO_DMG_BONUS,
                getTalentValue("Ascension Geo DMG", 0.288));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Registers the owner-local Transient Blossom damage listener. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Albedo cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;
        sim.addDamageListener((actor, action, damage, time) ->
                handlePartyDamage(actor, action, damage, time, sim));
        sim.addIndirectDamageListener((owner, damage, time) ->
                handlePartyDamage(owner, null, damage, time, sim));
    }

    /** Returns Albedo's 40-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 40.0);
    }

    /** Albedo has no unconditional static passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /**
     * Reports whether the current Solar Isotoma field is active.
     *
     * @param currentTime current simulation time in seconds
     * @return whether the half-open 30-second field window remains active
     */
    public boolean isSolarIsotomaActive(double currentTime) {
        return solarIsotomaActive
                && currentTime + EPSILON < solarIsotomaExpiresAt;
    }

    /** Returns the current Solar Isotoma expiry timestamp. */
    public double getSolarIsotomaExpiresAt() {
        return solarIsotomaExpiresAt;
    }

    /**
     * Returns unexpired old-base-kit C2 Fatal Reckoning stacks.
     *
     * @param currentTime current simulation time in seconds
     * @return stack count in the inclusive range 0-4
     */
    public int getFatalReckoningStacks(double currentTime) {
        if (currentTime + EPSILON >= fatalReckoningExpiresAt) {
            return 0;
        }
        return fatalReckoningStacks;
    }

    /** Dispatches Albedo's supported typed player actions. */
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
                chargedAttack(sim);
                break;
            case PLUNGE:
                highPlunge(sim);
                break;
            case SKILL:
                solarIsotoma(sim);
                break;
            case BURST:
                tectonicTide(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Albedo: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double[] multipliers = {
                0.674976, 0.674976, 0.871844, 0.914030, 1.140428
        };
        double[] durations = {
                14.0 / 60.0,
                22.0 / 60.0,
                32.0 / 60.0,
                34.0 / 60.0,
                62.0 / 60.0
        };
        String key = "N" + (normalAttackStep + 1);
        AttackAction action = new AttackAction(
                "Favonius Bladework - Weiss " + key,
                getTalentValue(key, multipliers[normalAttackStep]),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                durations[normalAttackStep],
                ActionType.NORMAL);
        action.setICD(ICDType.Standard, ICDTag.NormalAttack, 0.0);
        action.setHitlagProfile(NORMAL_HITLAG[normalAttackStep]);
        sim.performAction(characterId, action);
        normalAttackStep = (normalAttackStep + 1) % multipliers.length;
    }

    private void chargedAttack(CombatSimulator sim) {
        double[] multipliers = { 0.869, 1.106 };
        for (int i = 0; i < multipliers.length; i++) {
            AttackAction action = new AttackAction(
                    "Favonius Bladework - Weiss Charged " + (i + 1),
                    getTalentValue("Charged " + (i + 1), multipliers[i]),
                    Element.PHYSICAL,
                    StatType.BASE_ATK,
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    0.0,
                    ActionType.CHARGE);
            action.setICD(ICDType.Standard, ICDTag.NormalAttack, 0.0);
            sim.performActionWithoutTimeAdvance(characterId, action);
        }
        sim.advanceTime(56.0 / 60.0);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction action = new AttackAction(
                "Favonius Bladework - Weiss High Plunge",
                getTalentValue("Plunge High", 2.933586),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                66.0 / 60.0,
                ActionType.PLUNGE);
        action.setICD(ICDType.None, ICDTag.PlungeAttack, 0.0);
        action.setShatterTrigger(true);
        sim.performAction(characterId, action);
    }

    private void solarIsotoma(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        captureSnapshot(castTime, sim.getApplicableBuffs(this));
        markSkillUsed(castTime, sim.getApplicableBuffs(this));
        schedule(sim, castTime + 25.0 / 60.0, activeSim -> {
            AttackAction cast = new AttackAction(
                    "Abiogenesis: Solar Isotoma",
                    getTalentValue(
                            constellation >= 3
                                    ? "Solar Isotoma Cast C3"
                                    : "Solar Isotoma Cast",
                            constellation >= 3 ? 2.608 : 2.2168),
                    Element.GEO,
                    StatType.BASE_ATK,
                    StatType.SKILL_DMG_BONUS,
                    0.0,
                    true,
                    ActionType.SKILL);
            cast.setICD(ICDType.Standard, ICDTag.None, 1.0);
            activeSim.performActionWithoutTimeAdvance(characterId, cast);
            deploySolarIsotoma(activeSim, activeSim.getCurrentTime());
        });
        sim.advanceTime(33.0 / 60.0);
    }

    private void deploySolarIsotoma(
            CombatSimulator sim,
            double deploymentTime) {
        solarIsotomaActive = true;
        solarIsotomaExpiresAt = deploymentTime + FIELD_DURATION;
        nextTransientBlossomTime = Double.NEGATIVE_INFINITY;
        long generation = ++solarIsotomaGeneration;

        if (constellation >= 4) {
            sim.applyTeamBuffNoStack(new SimpleBuff(
                    "Albedo Descent of Divinity",
                    BuffId.ALBEDO_C4_PLUNGING_DMG_BONUS,
                    FIELD_DURATION,
                    deploymentTime,
                    stats -> stats.add(
                            StatType.PLUNGING_ATTACK_DMG_BONUS, 0.30))
                    .sourcedBy(characterId));
        }

        schedule(sim, solarIsotomaExpiresAt, activeSim -> {
            if (generation != solarIsotomaGeneration) {
                return;
            }
            solarIsotomaActive = false;
        });
    }

    private void handlePartyDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator sim) {
        if (suppressTransientBlossomTrigger
                || (action != null && !action.isHitEffectTrigger())
                || (actor == this
                        && action != null
                        && action.getActionType() == ActionType.BURST)
                || damage <= 0.0
                || !isSolarIsotomaActive(time)
                || time + EPSILON < nextTransientBlossomTime) {
            return;
        }
        nextTransientBlossomTime = time + BLOSSOM_COOLDOWN;
        if (constellation >= 1) {
            receiveFlatEnergy(getTalentValue("C1 Flat Energy", 1.2));
        }
        if (constellation >= 2) {
            acquireFatalReckoning(time);
        }
        long generation = solarIsotomaGeneration;
        schedule(sim, time + BLOSSOM_DELAY, activeSim ->
                resolveTransientBlossom(activeSim, generation));
    }

    private void resolveTransientBlossom(
            CombatSimulator sim,
            long generation) {
        if (generation != solarIsotomaGeneration) {
            return;
        }
        AttackAction blossom = new AttackAction(
                "Transient Blossom",
                getTalentValue(
                        constellation >= 3
                                ? "Transient Blossom C3"
                                : "Transient Blossom",
                        constellation >= 3 ? 2.672 : 2.2712),
                Element.GEO,
                StatType.BASE_DEF,
                StatType.SKILL_DMG_BONUS,
                0.0,
                true,
                ActionType.SKILL);
        blossom.setICD(
                ICDType.Standard, ICDTag.ElementalSkill, 1.0);
        performSuppressedDamage(sim, blossom);
        sim.getEnergyDistributor().distributeParticles(
                Element.GEO,
                getTalentValue("Expected Particles", EXPECTED_PARTICLES),
                ParticleType.PARTICLE);

    }

    private void acquireFatalReckoning(double currentTime) {
        if (currentTime + EPSILON >= fatalReckoningExpiresAt) {
            fatalReckoningStacks = 0;
        }
        fatalReckoningStacks = Math.min(
                MAX_FATAL_RECKONING_STACKS,
                fatalReckoningStacks + 1);
        fatalReckoningExpiresAt = currentTime
                + FATAL_RECKONING_DURATION;
    }

    private void tectonicTide(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        int consumedStacks = constellation >= 2
                ? getFatalReckoningStacks(castTime) : 0;
        fatalReckoningStacks = 0;
        fatalReckoningExpiresAt = Double.NEGATIVE_INFINITY;
        double initialC2Damage = consumedStacks * 0.30
                * getFinalDef(sim, castTime);
        markBurstUsed(castTime, sim.getApplicableBuffs(this));

        schedule(sim, castTime + 75.0 / 60.0, activeSim -> {
            AttackAction burst = new AttackAction(
                    "Rite of Progeniture: Tectonic Tide",
                    getTalentValue(
                            constellation >= 5
                                    ? "Tectonic Tide C5"
                                    : "Tectonic Tide",
                            constellation >= 5 ? 7.344 : 6.2424),
                    Element.GEO,
                    StatType.BASE_ATK,
                    StatType.BURST_DMG_BONUS,
                    0.0,
                    ActionType.BURST);
            burst.setICD(ICDType.None, ICDTag.ElementalBurst, 1.0);
            performLiveFlatDamage(activeSim, burst, initialC2Damage);
            applyHomuncularNature(activeSim);

            if (!isSolarIsotomaActive(activeSim.getCurrentTime())) {
                return;
            }
            long generation = solarIsotomaGeneration;
            double fatalC2Damage = consumedStacks * 0.30
                    * getFinalDef(activeSim, activeSim.getCurrentTime());
            schedule(activeSim, castTime + 145.0 / 60.0,
                    fatalSim -> resolveFatalBlossom(
                            fatalSim, generation, fatalC2Damage));
        });
        sim.advanceTime(96.0 / 60.0);
    }

    private void resolveFatalBlossom(
            CombatSimulator sim,
            long generation,
            double c2Damage) {
        if (generation != solarIsotomaGeneration) {
            return;
        }
        AttackAction blossom = new AttackAction(
                "Fatal Blossom",
                getTalentValue(
                        constellation >= 5
                                ? "Fatal Blossom C5"
                                : "Fatal Blossom",
                        constellation >= 5 ? 1.44 : 1.224),
                Element.GEO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                true,
                ActionType.BURST);
        blossom.setICD(
                ICDType.Standard, ICDTag.ElementalBurst, 1.0);
        performSnapshotFlatDamage(sim, blossom, c2Damage);
    }

    private void applyHomuncularNature(CombatSimulator sim) {
        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Albedo Homuncular Nature",
                BuffId.ALBEDO_A4_TEAM_EM,
                10.0,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, 125.0))
                .sourcedBy(characterId));
    }

    private double getFinalDef(CombatSimulator sim, double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        List<Buff> applicableBuffs = sim.getApplicableBuffs(this);
        for (Buff buff : applicableBuffs) {
            buff.apply(stats, currentTime);
        }
        return stats.getTotalDef();
    }

    private void performSuppressedDamage(
            CombatSimulator sim,
            AttackAction action) {
        suppressTransientBlossomTrigger = true;
        try {
            sim.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            suppressTransientBlossomTrigger = false;
        }
    }

    private void performLiveFlatDamage(
            CombatSimulator sim,
            AttackAction action,
            double flatDamage) {
        if (flatDamage == 0.0) {
            performSuppressedDamage(sim, action);
            return;
        }
        Buff c2Damage = new SimpleBuff(
                "Albedo Fatal Reckoning Burst Damage",
                1.0,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.FLAT_DMG_BONUS, flatDamage));
        getActiveBuffs().add(c2Damage);
        try {
            performSuppressedDamage(sim, action);
        } finally {
            getActiveBuffs().remove(c2Damage);
        }
    }

    private void performSnapshotFlatDamage(
            CombatSimulator sim,
            AttackAction action,
            double flatDamage) {
        if (flatDamage == 0.0) {
            performSuppressedDamage(sim, action);
            return;
        }
        StatsContainer snapshot = getSnapshot();
        snapshot.add(StatType.FLAT_DMG_BONUS, flatDamage);
        try {
            performSuppressedDamage(sim, action);
        } finally {
            snapshot.add(StatType.FLAT_DMG_BONUS, -flatDamage);
        }
    }

    private void schedule(
            CombatSimulator sim,
            double time,
            Consumer<CombatSimulator> action) {
        sim.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                action.accept(activeSim);
            }
        });
    }
}
