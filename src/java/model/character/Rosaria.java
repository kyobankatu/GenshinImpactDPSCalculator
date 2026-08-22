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
 * Rosaria's offensive mechanics for stationary single-target combat.
 *
 * <p>
 * This slice uses talent-9 multipliers and current gcsim hitmarks for Rosaria's
 * five-step Normal string, Charged and high Plunging Attacks, two-hit Skill,
 * and snapshotted Burst field. Ravaging Confession assumes that its teleport
 * successfully reaches the target's back, so A1 starts before its first hit.
 * A4 shares 15% of Rosaria's displayed CRIT Rate, capped at 15%, with every
 * other party member. C2, C3, C5, and C6 are represented.
 *
 * <p>
 * Enemy facing and large-target geometry, movement, multi-target
 * selection, and actual CRIT outcomes are outside the current simulator. C1
 * and C4 therefore remain intentionally inactive instead of deriving procs
 * from expected CRIT damage.
 */
public class Rosaria extends Character
        implements SimulatorInitializedCharacterEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double SKILL_COOLDOWN = 6.0;
    private static final double BURST_COOLDOWN = 15.0;
    private static final double A1_DURATION = 5.0;
    private static final double A4_DURATION = 10.0;
    private static final double C6_DURATION = 10.0;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double BURST_TICK_INTERVAL = 120.0 * FRAME;
    private static final int BASE_BURST_TICKS = 4;
    private static final int C2_BURST_TICKS = 6;

    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[][] NORMAL_HITLAG = {
        { new HitlagProfile(0.06, 0.01, true, false, false) },
        { new HitlagProfile(0.06, 0.01, true, false, false) },
        { HitlagProfile.none(), new HitlagProfile(0.03, 0.01, true, false, false) },
        { new HitlagProfile(0.09, 0.01, true, false, false) },
        {
            new HitlagProfile(0.06, 0.01, false, false, false),
            new HitlagProfile(0.06, 0.01, true, false, false)
        }
    };
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.0, 0.01, true, true, false);
    private static final HitlagProfile SKILL_FIRST_HITLAG =
            new HitlagProfile(0.06, 0.01, false, false, false);
    private static final HitlagProfile SKILL_SECOND_HITLAG =
            new HitlagProfile(0.09, 0.01, true, false, false);
    private static final HitlagProfile BURST_FIRST_HITLAG =
            new HitlagProfile(0.06, 0.01, false, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long burstGeneration;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;

    /** Constructs the repository-default C6 Rosaria. */
    public Rosaria(Weapon weapon, ArtifactSet artifacts) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                6);
    }

    /**
     * Constructs Rosaria at an explicit constellation.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param constellation constellation in the inclusive range 0-6
     */
    public Rosaria(
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
     * Constructs Rosaria with injectable talent data and constellation state.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Rosaria(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Rosaria constellation must be between 0 and 6");
        }
        this.name = "Rosaria";
        this.characterId = CharacterId.ROSARIA;
        this.element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12289.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 240.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 710.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK", 0.24));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds mutable timer state to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Rosaria cannot be reused across CombatSimulator instances");
        }
        initializedSimulator = sim;
    }

    /** Returns Rosaria's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Rosaria has no unconditional static offensive passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Modeled passives require an action, target hit, or Burst cast.
    }

    /**
     * Returns whether the current Ice Lance field remains active.
     *
     * @param currentTime current simulation time in seconds
     * @return {@code true} before the active generation's expiry
     */
    public boolean isBurstActive(double currentTime) {
        return currentTime < burstExpirationTime;
    }

    /** Dispatches Rosaria's supported typed offensive actions. */
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
                ravagingConfession(sim);
                break;
            case BURST:
                ritesOfTermination(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Rosaria: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        double[][] multipliers = {
                { 0.9638 },
                { 0.9480 },
                { 0.5846, 0.5846 },
                { 1.2798 },
                { 0.76472, 0.7900 }
        };
        int[][] hitmarks = {
                { 9 }, { 13 }, { 19, 28 }, { 32 }, { 26, 40 }
        };
        int[] durations = { 24, 27, 34, 52, 66 };
        int step = normalAttackStep;
        for (int hit = 0; hit < multipliers[step].length; hit++) {
            int hitIndex = hit;
            String key = normalTalentKey(step, hitIndex);
            AttackAction normal = new AttackAction(
                    "Spear of the Church N" + (step + 1)
                            + " Hit " + (hitIndex + 1),
                    getTalentValue(key, multipliers[step][hitIndex]),
                    Element.PHYSICAL,
                    StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    0.0,
                    ActionType.NORMAL);
            normal.setICD(
                    ICDType.Standard, ICDTag.NormalAttack, 0.0);
            normal.setHitlagProfile(NORMAL_HITLAG[step][hitIndex]);
            schedule(
                    sim,
                    castTime + hitmarks[step][hitIndex] * FRAME,
                    activeSim -> activeSim.performActionWithoutTimeAdvance(
                            characterId, normal));
        }
        normalAttackStep = (normalAttackStep + 1) % multipliers.length;
        sim.advanceTime(durations[step] * FRAME);
    }

    private static String normalTalentKey(int step, int hit) {
        if (step == 4) {
            return "N5 Hit " + (hit + 1);
        }
        return "N" + (step + 1);
    }

    private void chargedAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        AttackAction charged = new AttackAction(
                "Spear of the Church Charged Attack",
                getTalentValue("Charged Attack", 2.5122),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                0.0,
                ActionType.CHARGE);
        charged.setICD(
                ICDType.Standard, ICDTag.ChargedAttack, 0.0);
        charged.setHitlagProfile(CHARGED_HITLAG);
        schedule(
                sim,
                castTime + 22.0 * FRAME,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, charged));
        sim.advanceTime(69.0 * FRAME);
    }

    private void highPlunge(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        AttackAction plunge = new AttackAction(
                "Spear of the Church High Plunge",
                getTalentValue("High Plunge", 2.933586),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                0.0,
                ActionType.PLUNGE);
        plunge.setICD(ICDType.None, ICDTag.PlungeAttack, 0.0);
        plunge.setShatterTrigger(true);
        schedule(
                sim,
                castTime + 43.0 * FRAME,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, plunge));
        sim.advanceTime(80.0 * FRAME);
    }

    private void ravagingConfession(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        schedule(sim, castTime + 23.0 * FRAME, activeSim ->
                markSkillUsed(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this)));
        schedule(sim, castTime + 24.0 * FRAME, this::resolveSkillFirstHit);
        schedule(sim, castTime + 38.0 * FRAME, this::resolveSkillSecondHit);
        sim.advanceTime(51.0 * FRAME);
    }

    private void resolveSkillFirstHit(CombatSimulator sim) {
        if (sim.getEnemy() != null) {
            replaceA1Buff(sim.getCurrentTime());
        }
        AttackAction first = new AttackAction(
                "Ravaging Confession Hit 1",
                getSkillMultiplier(0),
                Element.CRYO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        first.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);
        first.setHitlagProfile(SKILL_FIRST_HITLAG);
        sim.performActionWithoutTimeAdvance(characterId, first);
    }

    private void resolveSkillSecondHit(CombatSimulator sim) {
        AttackAction second = new AttackAction(
                "Ravaging Confession Hit 2",
                getSkillMultiplier(1),
                Element.CRYO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        second.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);
        second.setHitlagProfile(SKILL_SECOND_HITLAG);
        second.setShatterTrigger(true);
        sim.performActionWithoutTimeAdvance(characterId, second);
        if (sim.getEnemy() != null) {
            schedule(
                    sim,
                    sim.getCurrentTime() + PARTICLE_TRAVEL,
                    activeSim -> activeSim.getEnergyDistributor()
                            .distributeParticles(
                                    Element.CRYO,
                                    getTalentValue("Skill Particles", 3.0),
                                    ParticleType.PARTICLE));
        }
    }

    private double getSkillMultiplier(int hit) {
        if (constellation >= 3) {
            return getTalentValue(
                    "Ravaging Confession Hit " + (hit + 1) + " C3",
                    hit == 0 ? 1.1680 : 2.7200);
        }
        return getTalentValue(
                "Ravaging Confession Hit " + (hit + 1),
                hit == 0 ? 0.9928 : 2.3120);
    }

    private void ritesOfTermination(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        long generation = ++burstGeneration;
        StatsContainer[] initialSnapshot = new StatsContainer[1];
        StatsContainer[] lanceSnapshot = new StatsContainer[1];
        int tickCount = constellation >= 2
                ? C2_BURST_TICKS : BASE_BURST_TICKS;
        burstExpirationTime = castTime
                + (56.0 + 120.0 * tickCount + 30.0) * FRAME;

        markBurstUsed(castTime, sim.getApplicableBuffs(this));
        applyA4Share(sim, castTime);

        schedule(sim, castTime + 15.0 * FRAME, activeSim -> {
            if (generation == burstGeneration) {
                captureSnapshot(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this));
                initialSnapshot[0] = getSnapshot().merge(null);
                resolveBurstHit(
                        activeSim,
                        "Rites of Termination Initial 1",
                        getBurstInitialMultiplier(0),
                        initialSnapshot[0],
                        BURST_FIRST_HITLAG);
            }
        });
        schedule(sim, castTime + 56.0 * FRAME, activeSim -> {
            if (generation != burstGeneration) {
                return;
            }
            captureSnapshot(
                    activeSim.getCurrentTime(),
                    activeSim.getApplicableBuffs(this));
            lanceSnapshot[0] = getSnapshot().merge(null);
            resolveBurstHit(
                    activeSim,
                    "Rites of Termination Initial 2",
                    getBurstInitialMultiplier(1),
                    lanceSnapshot[0],
                    HitlagProfile.none());
        });
        scheduleBurstTicks(
                sim, castTime, generation, tickCount, lanceSnapshot);
        sim.advanceTime(70.0 * FRAME);
    }

    private void scheduleBurstTicks(
            CombatSimulator sim,
            double castTime,
            long generation,
            int tickCount,
            StatsContainer[] lanceSnapshot) {
        double firstTickTime = castTime + 176.0 * FRAME;
        sim.registerEvent(new SimpleTimerEvent(
                firstTickTime, BURST_TICK_INTERVAL) {
            private int resolvedTicks;

            @Override
            public void onTick(CombatSimulator activeSim) {
                if (activeSim != initializedSimulator
                        || generation != burstGeneration) {
                    finish();
                    return;
                }
                resolveBurstHit(
                        activeSim,
                        "Rites of Termination Ice Lance DoT",
                        getBurstDotMultiplier(),
                        lanceSnapshot[0],
                        HitlagProfile.none());
                resolvedTicks++;
                if (resolvedTicks == tickCount) {
                    finish();
                }
            }
        });
    }

    private void resolveBurstHit(
            CombatSimulator sim,
            String actionName,
            double multiplier,
            StatsContainer snapshot,
            HitlagProfile hitlagProfile) {
        AttackAction burst = new AttackAction(
                actionName,
                multiplier,
                Element.CRYO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                true,
                ActionType.BURST);
        burst.setStatSnapshot(snapshot);
        burst.setICD(ICDType.None, ICDTag.ElementalBurst, 1.0);
        burst.setHitlagProfile(hitlagProfile);
        sim.performActionWithoutTimeAdvance(characterId, burst);
        if (constellation >= 6 && sim.getEnemy() != null) {
            refreshC6PhysicalShred(sim, sim.getCurrentTime());
        }
    }

    private double getBurstInitialMultiplier(int hit) {
        if (constellation >= 5) {
            return getTalentValue(
                    "Rites of Termination Initial " + (hit + 1) + " C5",
                    hit == 0 ? 2.0800 : 3.0400);
        }
        return getTalentValue(
                "Rites of Termination Initial " + (hit + 1),
                hit == 0 ? 1.7680 : 2.5840);
    }

    private double getBurstDotMultiplier() {
        if (constellation >= 5) {
            return getTalentValue(
                    "Rites of Termination DoT C5", 2.6400);
        }
        return getTalentValue("Rites of Termination DoT", 2.2440);
    }

    private void replaceA1Buff(double currentTime) {
        getActiveBuffs().removeIf(
                buff -> buff instanceof ReginaProbationumBuff);
        addBuff(new ReginaProbationumBuff(currentTime));
    }

    private void applyA4Share(CombatSimulator sim, double currentTime) {
        StatsContainer sourceStats = getEffectiveStats(currentTime);
        for (Buff buff : sim.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(sourceStats, currentTime);
            }
        }
        double share = Math.min(
                0.15,
                Math.max(0.0,
                        sourceStats.get(StatType.CRIT_RATE) * 0.15));
        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Rosaria Shadow Samaritan",
                BuffId.ROSARIA_A4_TEAM_CRIT_RATE,
                A4_DURATION,
                currentTime,
                stats -> stats.add(StatType.CRIT_RATE, share))
                .exclude(characterId)
                .sourcedBy(characterId));
    }

    private void refreshC6PhysicalShred(
            CombatSimulator sim,
            double currentTime) {
        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Rosaria C6 Divine Retribution",
                BuffId.ROSARIA_C6_PHYSICAL_RES_SHRED,
                C6_DURATION,
                currentTime,
                stats -> stats.add(StatType.PHYS_RES_SHRED, 0.20))
                .sourcedBy(characterId));
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

    /** Owner-only A1 CRIT Rate window under the fixed behind-target policy. */
    private static final class ReginaProbationumBuff extends Buff {
        private ReginaProbationumBuff(double currentTime) {
            super("Rosaria Regina Probationum", A1_DURATION, currentTime);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            stats.add(StatType.CRIT_RATE, 0.12);
        }
    }
}
