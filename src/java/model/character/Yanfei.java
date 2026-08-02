package model.character;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedCharacterEffect;
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
import simulation.event.SimpleTimerEvent;

/**
 * Yanfei offensive implementation for stationary single-target combat.
 *
 * <p>Seal of Approval uses talent-9 values and a fixed ten-frame projectile
 * travel assumption. Enemy-hit Normal Attacks grant one Scarlet Seal for ten
 * seconds. Charged Attacks consume every live Seal at cast time, select the
 * sourced seal-specific multiplier, and apply Proviso before their hit.</p>
 *
 * <p>Signed Edict and Done Deal resolve at their current gcsim hitmarks. Done
 * Deal opens a half-open 15-second Brilliance window, while an owner-local
 * generation rejects stale one-second Seal timers after recast or switch.
 * C3, C5, and C6 offensive branches are represented. Stamina and interruption
 * resistance from C1, enemy-HP-dependent C2, the C4 shield, actual-crit A4,
 * hitlag, geometry, and multi-target behavior are intentionally excluded.</p>
 */
public class Yanfei extends Character implements
        SimulatorInitializedCharacterEffect,
        SwitchAwareCharacter {
    private static final double SKILL_COOLDOWN = 9.0;
    private static final double BURST_COOLDOWN = 20.0;
    private static final double SCARLET_SEAL_DURATION = 10.0;
    private static final double BRILLIANCE_DURATION = 15.0;
    private static final double BRILLIANCE_INTERVAL = 1.0;
    private static final double A1_DURATION = 6.0;
    private static final double PARTICLE_TRAVEL = 100.0 / 60.0;
    private static final double EPSILON = 1e-9;

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int scarletSeals;
    private double scarletSealsExpireAt = Double.NEGATIVE_INFINITY;
    private boolean brillianceActive;
    private double brillianceExpiresAt = Double.NEGATIVE_INFINITY;
    private long brillianceGeneration;

    /** Constructs the repository-default C6 Yanfei. */
    public Yanfei(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /**
     * Constructs Yanfei at an explicit constellation.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param constellation constellation in the inclusive range 0-6
     */
    public Yanfei(
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
     * Constructs Yanfei with injectable talent data and constellation state.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Yanfei(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Yanfei constellation must be between 0 and 6");
        }
        this.name = "Yanfei";
        this.characterId = CharacterId.YANFEI;
        this.element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9352.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 240.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 587.0));
        baseStats.add(StatType.PYRO_DMG_BONUS,
                getTalentValue("Ascension Pyro DMG", 0.24));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds timer state to exactly one simulator instance. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Yanfei cannot be reused across CombatSimulator instances");
        }
        initializedSimulator = sim;
    }

    /** Returns Yanfei's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Yanfei has no unconditional static offensive passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /**
     * Returns the live Scarlet Seal count, pruning the exact expiry boundary.
     *
     * @param currentTime current simulation time in seconds
     * @return current Seal count in the range 0-3, or 0-4 at C6
     */
    public int getScarletSealCount(double currentTime) {
        pruneExpiredSeals(currentTime);
        return scarletSeals;
    }

    /** Returns the current constellation-dependent Scarlet Seal limit. */
    public int getScarletSealLimit() {
        return constellation >= 6 ? 4 : 3;
    }

    /** Returns the expiry timestamp shared by the current Scarlet Seals. */
    public double getScarletSealsExpireAt() {
        return scarletSealsExpireAt;
    }

    /**
     * Reports whether Brilliance remains active in its half-open window.
     *
     * @param currentTime current simulation time in seconds
     * @return whether Done Deal's Brilliance effect remains active
     */
    public boolean isBrillianceActive(double currentTime) {
        if (brillianceActive
                && currentTime + EPSILON >= brillianceExpiresAt) {
            endBrilliance();
        }
        return brillianceActive;
    }

    /** Returns the current Brilliance expiry timestamp. */
    public double getBrillianceExpiresAt() {
        return brillianceExpiresAt;
    }

    /** Clears Seals and Brilliance when Yanfei leaves the active field. */
    @Override
    public void onSwitchOut(CombatSimulator sim) {
        normalAttackStep = 0;
        clearScarletSeals();
        endBrilliance();
    }

    /** Dispatches Yanfei's supported typed offensive actions. */
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
                signedEdict(sim);
                break;
            case BURST:
                doneDeal(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Yanfei: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        double[] multipliers = { 0.991807, 0.886135, 1.292218 };
        double[] durations = {
                26.0 / 60.0, 28.0 / 60.0, 73.0 / 60.0
        };
        double[] hitmarks = {
                12.0 / 60.0, 16.0 / 60.0, 37.0 / 60.0
        };
        int step = normalAttackStep;
        String key = "N" + (step + 1);
        AttackAction normal = new AttackAction(
                "Seal of Approval " + key,
                getTalentValue(key, multipliers[step]),
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        normal.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);

        double travel = getTalentValue(
                "Normal Projectile Travel", 10.0 / 60.0);
        schedule(sim, castTime + hitmarks[step] + travel, activeSim -> {
            activeSim.performActionWithoutTimeAdvance(characterId, normal);
            if (activeSim.getEnemy() != null
                    && activeSim.getActiveCharacter() == this) {
                grantScarletSeals(1, activeSim.getCurrentTime());
            }
        });
        normalAttackStep = (normalAttackStep + 1) % multipliers.length;
        sim.advanceTime(durations[step]);
    }

    private void chargedAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        int consumedSeals = consumeScarletSeals(castTime);
        applyProviso(consumedSeals, castTime);

        double[] multipliers = {
                1.523438, 1.792280, 2.061122, 2.329964, 2.598806
        };
        String key = "Charged " + consumedSeals + " Seals";
        AttackAction charged = new AttackAction(
                "Seal of Approval Charged (" + consumedSeals + " Seals)",
                getTalentValue(key, multipliers[consumedSeals]),
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                0.0,
                ActionType.CHARGE);
        charged.setICD(ICDType.None, ICDTag.ChargedAttack, 1.0);
        charged.setShatterTrigger(true);
        schedule(sim, castTime + 63.0 / 60.0, activeSim ->
                activeSim.performActionWithoutTimeAdvance(
                        characterId, charged));
        sim.advanceTime(79.0 / 60.0);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction plunge = new AttackAction(
                "Seal of Approval High Plunge",
                getTalentValue("High Plunge", 2.607632),
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                68.0 / 60.0,
                ActionType.PLUNGE);
        plunge.setICD(ICDType.None, ICDTag.PlungeAttack, 1.0);
        plunge.setShatterTrigger(true);
        sim.performAction(characterId, plunge);
    }

    private void signedEdict(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        captureSnapshot(castTime, sim.getApplicableBuffs(this));
        schedule(sim, castTime + 28.0 / 60.0, activeSim ->
                markSkillUsed(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this)));
        schedule(sim, castTime + 32.0 / 60.0, activeSim -> {
            String key = constellation >= 3
                    ? "Signed Edict C3" : "Signed Edict";
            double defaultValue = constellation >= 3
                    ? 3.3920 : 2.8832;
            AttackAction skill = new AttackAction(
                    "Signed Edict",
                    getTalentValue(key, defaultValue),
                    Element.PYRO,
                    StatType.BASE_ATK,
                    StatType.SKILL_DMG_BONUS,
                    0.0,
                    true,
                    ActionType.SKILL);
            skill.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);
            skill.setShatterTrigger(true);
            activeSim.performActionWithoutTimeAdvance(characterId, skill);
            grantScarletSeals(
                    getScarletSealLimit(), activeSim.getCurrentTime());
            schedule(
                    activeSim,
                    activeSim.getCurrentTime() + PARTICLE_TRAVEL,
                    particleSim -> particleSim.getEnergyDistributor()
                            .distributeParticles(
                                    Element.PYRO,
                                    getTalentValue("Skill Particles", 3.0),
                                    ParticleType.PARTICLE));
        });
        sim.advanceTime(46.0 / 60.0);
    }

    private void doneDeal(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        captureSnapshot(castTime, sim.getApplicableBuffs(this));
        markBurstUsed(castTime, sim.getApplicableBuffs(this));
        startBrilliance(sim, castTime);

        schedule(sim, castTime + 24.0 / 60.0, activeSim -> {
            String key = constellation >= 5
                    ? "Done Deal C5" : "Done Deal";
            double defaultValue = constellation >= 5
                    ? 3.6480 : 3.1008;
            AttackAction burst = new AttackAction(
                    "Done Deal",
                    getTalentValue(key, defaultValue),
                    Element.PYRO,
                    StatType.BASE_ATK,
                    StatType.BURST_DMG_BONUS,
                    0.0,
                    true,
                    ActionType.BURST);
            burst.setICD(
                    ICDType.Standard, ICDTag.ElementalBurst, 2.0);
            burst.setShatterTrigger(true);
            activeSim.performActionWithoutTimeAdvance(characterId, burst);
            grantScarletSeals(
                    getScarletSealLimit(), activeSim.getCurrentTime());
        });
        sim.advanceTime(58.0 / 60.0);
    }

    private void applyProviso(int consumedSeals, double currentTime) {
        removeBuff(BuffId.YANFEI_A1_PYRO_DMG_BONUS);
        if (consumedSeals == 0) {
            return;
        }
        double bonus = consumedSeals * getTalentValue(
                "A1 Pyro DMG per Seal", 0.05);
        addBuff(new SimpleBuff(
                "Yanfei Proviso",
                BuffId.YANFEI_A1_PYRO_DMG_BONUS,
                A1_DURATION,
                currentTime,
                stats -> stats.add(StatType.PYRO_DMG_BONUS, bonus))
                .sourcedBy(characterId));
    }

    private void startBrilliance(CombatSimulator sim, double startTime) {
        endBrilliance();
        brillianceActive = true;
        brillianceExpiresAt = startTime + getTalentValue(
                "Brilliance Duration", BRILLIANCE_DURATION);
        long generation = ++brillianceGeneration;
        String key = constellation >= 5
                ? "Brilliance Charged DMG Bonus C5"
                : "Brilliance Charged DMG Bonus";
        double defaultBonus = constellation >= 5 ? 0.596 : 0.518;
        double bonus = getTalentValue(key, defaultBonus);
        addBuff(new SimpleBuff(
                "Yanfei Brilliance",
                BuffId.YANFEI_BRILLIANCE_CHARGED_DMG_BONUS,
                brillianceExpiresAt - startTime,
                startTime,
                stats -> stats.add(
                        StatType.CHARGED_ATTACK_DMG_BONUS, bonus))
                .sourcedBy(characterId));

        double interval = getTalentValue(
                "Brilliance Seal Interval", BRILLIANCE_INTERVAL);
        sim.registerEvent(new SimpleTimerEvent(
                startTime + interval, interval) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                double currentTime = activeSim.getCurrentTime();
                if (activeSim != initializedSimulator
                        || generation != brillianceGeneration
                        || !brillianceActive
                        || currentTime + EPSILON >= brillianceExpiresAt
                        || activeSim.getActiveCharacter() != Yanfei.this) {
                    finish();
                    return;
                }
                grantScarletSeals(1, currentTime);
            }
        });
        schedule(sim, brillianceExpiresAt, activeSim -> {
            if (activeSim == initializedSimulator
                    && generation == brillianceGeneration) {
                endBrilliance();
            }
        });
    }

    private void endBrilliance() {
        brillianceActive = false;
        brillianceExpiresAt = Double.NEGATIVE_INFINITY;
        brillianceGeneration++;
        removeBuff(BuffId.YANFEI_BRILLIANCE_CHARGED_DMG_BONUS);
    }

    private void grantScarletSeals(int count, double currentTime) {
        pruneExpiredSeals(currentTime);
        scarletSeals = Math.min(
                getScarletSealLimit(), scarletSeals + count);
        scarletSealsExpireAt = currentTime + getTalentValue(
                "Scarlet Seal Duration", SCARLET_SEAL_DURATION);
    }

    private int consumeScarletSeals(double currentTime) {
        int consumed = getScarletSealCount(currentTime);
        clearScarletSeals();
        return consumed;
    }

    private void pruneExpiredSeals(double currentTime) {
        if (scarletSeals > 0
                && currentTime + EPSILON >= scarletSealsExpireAt) {
            clearScarletSeals();
        }
    }

    private void clearScarletSeals() {
        scarletSeals = 0;
        scarletSealsExpireAt = Double.NEGATIVE_INFINITY;
    }

    private void schedule(
            CombatSimulator sim,
            double time,
            java.util.function.Consumer<CombatSimulator> effect) {
        sim.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                if (activeSim == initializedSimulator) {
                    effect.accept(activeSim);
                }
            }
        });
    }
}
