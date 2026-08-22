package model.character;

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
 * Ganyu's legacy offensive kit for stationary single-target combat.
 *
 * <p>
 * This slice models the six-step bow string, Frostflake Arrow and Bloom,
 * both Trail of the Qilin hits, and the ten deterministic Celestial Shower
 * shards guaranteed by the fixed target-selection cadence. Charged, Skill,
 * and Burst damage snapshot at release or cast. A1, A4, and the representable
 * C1-C6 effects are included.
 *
 * <p>
 * The other forty randomly targeted Burst shards, weak points, range falloff,
 * enemy size, taunt health, field geometry, and pending-event snapshot restore
 * are intentionally outside this single-target policy.
 */
public class Ganyu extends Character
        implements SimulatorInitializedCharacterEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double PROJECTILE_TRAVEL = 10.0 * FRAME;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double SKILL_COOLDOWN = 10.0;
    private static final double BURST_COOLDOWN = 15.0;
    private static final double BURST_DURATION = 15.0;
    private static final double C4_LINGER_DURATION = 3.0;
    private static final double A1_DURATION = 5.0;
    private static final double C1_DURATION = 6.0;
    private static final double C6_WINDOW = 30.0;
    private static final int FROSTFLAKE_RELEASE_FRAMES = 103;
    private static final int C6_FROSTFLAKE_RELEASE_FRAMES = 20;
    private static final int BLOOM_DELAY_FRAMES = 18;
    private static final int BURST_SHARD_COUNT = 10;
    private static final int BURST_FIELD_START_FRAME = 122;
    private static final int FIRST_BURST_SHARD_FRAME = 148;
    private static final int BURST_SHARD_INTERVAL_FRAMES = 90;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile FROSTFLAKE_HEADSHOT_HITLAG =
            new HitlagProfile(0.12, 0.01, false, true, true);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long frostflakeGeneration;
    private double burstStartTime = Double.POSITIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean c6InstantFrostflakeAvailable;
    private double c6InstantFrostflakeExpiration = Double.NEGATIVE_INFINITY;

    /** Constructs the repository-default C6 Ganyu. */
    public Ganyu(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Ganyu at an explicit constellation. */
    public Ganyu(
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
     * Constructs Ganyu with injectable talent data and constellation state.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Ganyu(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Ganyu constellation must be between 0 and 6");
        }
        this.name = "Ganyu";
        this.characterId = CharacterId.GANYU;
        this.element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9797.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 335.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 630.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(SKILL_COOLDOWN);
        setSkillMaxCharges(constellation >= 2 ? 2 : 1);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds mutable timer state to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Ganyu cannot be reused across CombatSimulator instances");
        }
        initializedSimulator = sim;
    }

    /** Returns Ganyu's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Ganyu has no unconditional static offensive passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Modeled passives require Frostflake or Burst field state.
    }

    /** Returns whether the current Celestial Shower field remains active. */
    public boolean isBurstActive(double currentTime) {
        return currentTime >= burstStartTime
                && currentTime < burstExpirationTime;
    }

    /** Dispatches Ganyu's supported typed offensive actions. */
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
                frostflakeArrow(sim);
                break;
            case PLUNGE:
                highPlunge(sim);
                break;
            case SKILL:
                trailOfTheQilin(sim);
                break;
            case BURST:
                celestialShower(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Ganyu: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        double[] multipliers = {
                0.58302, 0.65412, 0.83582, 0.83582, 0.88638, 1.05860
        };
        int[] releaseFrames = { 13, 14, 20, 26, 21, 22 };
        int[] durations = { 19, 27, 38, 37, 28, 59 };
        int step = normalAttackStep;
        AttackAction normal = attack(
                "Liutian Archery N" + (step + 1),
                getTalentValue("N" + (step + 1), multipliers[step]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0,
                false);
        double impactTime = castTime
                + releaseFrames[step] * FRAME + PROJECTILE_TRAVEL;
        schedule(sim, impactTime,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, normal));
        normalAttackStep = (normalAttackStep + 1) % multipliers.length;
        sim.advanceTime(durations[step] * FRAME);
    }

    private void frostflakeArrow(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        boolean instant = constellation >= 6
                && c6InstantFrostflakeAvailable
                && castTime < c6InstantFrostflakeExpiration;
        if (instant) {
            c6InstantFrostflakeAvailable = false;
        }
        int releaseFrames = instant
                ? C6_FROSTFLAKE_RELEASE_FRAMES
                : FROSTFLAKE_RELEASE_FRAMES;
        double releaseTime = castTime + releaseFrames * FRAME;
        long shotGeneration = ++frostflakeGeneration;
        schedule(sim, releaseTime,
                activeSim -> resolveFrostflakeRelease(
                        activeSim, releaseTime, shotGeneration));
        sim.advanceTime((releaseFrames + 10.0
                + BLOOM_DELAY_FRAMES + 1.0) * FRAME);
    }

    private void resolveFrostflakeRelease(
            CombatSimulator sim,
            double releaseTime,
            long shotGeneration) {
        captureSnapshot(releaseTime, sim.getApplicableBuffs(this));
        AttackAction arrow = attack(
                "Liutian Archery Frostflake Arrow",
                getTalentValue("Frostflake Arrow", 2.1760),
                Element.CRYO,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.None,
                ICDTag.ChargedAttack,
                1.0,
                true);
        AttackAction bloom = attack(
                "Liutian Archery Frostflake Bloom",
                getTalentValue("Frostflake Bloom", 3.6992),
                Element.CRYO,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.None,
                ICDTag.ChargedAttack,
                1.0,
                true);
        arrow.setHitlagProfile(FROSTFLAKE_HEADSHOT_HITLAG);
        replaceA1Buff(releaseTime);
        double arrowImpactTime = releaseTime + PROJECTILE_TRAVEL;
        schedule(sim, arrowImpactTime, activeSim -> {
            if (shotGeneration != frostflakeGeneration) {
                return;
            }
            activeSim.performActionWithoutTimeAdvance(characterId, arrow);
            if (constellation >= 1 && activeSim.getEnemy() != null) {
                refreshC1(activeSim, activeSim.getCurrentTime());
                receiveFlatEnergy(
                        getTalentValue("C1 Frostflake Energy", 2.0));
            }
        });
        schedule(sim, arrowImpactTime + BLOOM_DELAY_FRAMES * FRAME,
                activeSim -> {
                    if (shotGeneration == frostflakeGeneration) {
                        activeSim.performActionWithoutTimeAdvance(
                                characterId, bloom);
                    }
                });
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction plunge = attack(
                "Liutian Archery High Plunge",
                getTalentValue("Plunge High", 2.6076),
                Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                0.0,
                false);
        plunge.setAnimationDuration(58.0 * FRAME);
        sim.performAction(characterId, plunge);
    }

    private void trailOfTheQilin(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        markSkillUsed(castTime, sim.getApplicableBuffs(this));
        captureSnapshot(castTime, sim.getApplicableBuffs(this));
        if (constellation >= 6) {
            c6InstantFrostflakeAvailable = true;
            c6InstantFrostflakeExpiration = castTime + C6_WINDOW;
        }

        double multiplier = getTalentValue(
                constellation >= 5 ? "Trail of the Qilin C5"
                        : "Trail of the Qilin",
                constellation >= 5 ? 2.6400 : 2.2440);
        AttackAction initial = skillAttack(
                "Trail of the Qilin Initial", multiplier);
        AttackAction explosion = skillAttack(
                "Trail of the Qilin Explosion", multiplier);
        schedule(sim, castTime + 13.0 * FRAME,
                activeSim -> resolveSkillHit(activeSim, initial));
        schedule(sim, castTime + 373.0 * FRAME,
                activeSim -> resolveSkillHit(activeSim, explosion));
        sim.advanceTime(28.0 * FRAME);
    }

    private AttackAction skillAttack(String name, double multiplier) {
        return attack(
                name,
                multiplier,
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0,
                true);
    }

    private void resolveSkillHit(
            CombatSimulator sim,
            AttackAction action) {
        sim.performActionWithoutTimeAdvance(characterId, action);
        if (sim.getEnemy() != null) {
            schedule(sim, sim.getCurrentTime() + PARTICLE_TRAVEL,
                    activeSim -> activeSim.getEnergyDistributor()
                            .distributeParticles(
                                    Element.CRYO,
                                    getTalentValue("Skill Particles", 2.0),
                                    ParticleType.PARTICLE));
        }
    }

    private void celestialShower(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        markBurstUsed(castTime, sim.getApplicableBuffs(this));
        captureSnapshot(castTime, sim.getApplicableBuffs(this));
        burstStartTime = castTime + BURST_FIELD_START_FRAME * FRAME;
        burstExpirationTime = burstStartTime + BURST_DURATION;
        schedule(sim, burstStartTime, activeSim -> {
            replaceA4Field(activeSim, activeSim.getCurrentTime());
            if (constellation >= 4) {
                replaceC4Debuff(activeSim, activeSim.getCurrentTime());
            }
        });

        double multiplier = getTalentValue(
                constellation >= 3 ? "Celestial Shower C3"
                        : "Celestial Shower",
                constellation >= 3 ? 1.40544 : 1.194624);
        for (int shard = 0; shard < BURST_SHARD_COUNT; shard++) {
            int shardIndex = shard;
            int impactFrame = FIRST_BURST_SHARD_FRAME
                    + shardIndex * BURST_SHARD_INTERVAL_FRAMES;
            AttackAction icicle = attack(
                    "Celestial Shower Shard " + (shardIndex + 1),
                    multiplier,
                    Element.CRYO,
                    StatType.BURST_DMG_BONUS,
                    ActionType.BURST,
                    ICDType.Standard,
                    ICDTag.ElementalBurst,
                    1.0,
                    true);
            schedule(sim, castTime + impactFrame * FRAME,
                    activeSim -> activeSim.performActionWithoutTimeAdvance(
                            characterId, icicle));
        }
        sim.advanceTime(115.0 * FRAME);
    }

    private void replaceA1Buff(double currentTime) {
        removeBuff(BuffId.GANYU_A1_FROSTFLAKE_CRIT_RATE);
        addBuff(new SimpleBuff(
                "Ganyu Undivided Heart",
                BuffId.GANYU_A1_FROSTFLAKE_CRIT_RATE,
                A1_DURATION,
                currentTime,
                stats -> stats.add(
                        StatType.CHARGED_ATTACK_CRIT_RATE, 0.20)));
    }

    private void replaceA4Field(CombatSimulator sim, double currentTime) {
        sim.getFieldBuffList().removeIf(
                buff -> buff.getId() == BuffId.GANYU_A4_CRYO_DMG_BONUS);
        sim.applyFieldBuff(new SimpleBuff(
                "Ganyu Harmony Between Heaven and Earth",
                BuffId.GANYU_A4_CRYO_DMG_BONUS,
                BURST_DURATION,
                currentTime,
                stats -> stats.add(StatType.CRYO_DMG_BONUS, 0.20))
                .sourcedBy(characterId));
    }

    private void refreshC1(CombatSimulator sim, double currentTime) {
        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Ganyu Dew-Drinker",
                BuffId.GANYU_C1_CRYO_RES_SHRED,
                C1_DURATION,
                currentTime,
                stats -> stats.add(StatType.CRYO_RES_SHRED, 0.15))
                .sourcedBy(characterId));
    }

    private void replaceC4Debuff(CombatSimulator sim, double currentTime) {
        sim.applyTeamBuffNoStack(new CelestialShowerDebuff(currentTime)
                .sourcedBy(characterId));
    }

    private static double c4BonusAtElapsed(double elapsed) {
        int stacks = Math.min(5, 1 + (int) Math.floor(elapsed / 3.0));
        return stacks * 0.05;
    }

    private static AttackAction attack(
            String name,
            double multiplier,
            Element element,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            boolean snapshot) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                snapshot,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        return action;
    }

    private static void schedule(
            CombatSimulator sim,
            double time,
            Consumer<CombatSimulator> effect) {
        sim.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                effect.accept(activeSim);
            }
        });
    }

    /** Team-wide stationary-target C4 damage-taken ramp. */
    private static final class CelestialShowerDebuff extends Buff {
        private CelestialShowerDebuff(double currentTime) {
            super(
                    "Ganyu Westward Sojourn",
                    BuffId.GANYU_C4_CELESTIAL_SHOWER_DMG_BONUS,
                    BURST_DURATION + C4_LINGER_DURATION,
                    currentTime);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            stats.add(
                    StatType.DMG_BONUS_ALL,
                    c4BonusAtElapsed(currentTime - startTime));
        }
    }
}
