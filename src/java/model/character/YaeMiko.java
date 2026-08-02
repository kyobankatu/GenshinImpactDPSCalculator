package model.character;

import java.util.ArrayList;
import java.util.List;

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
import simulation.event.SimpleTimerEvent;

/**
 * Yae Miko's old-base-kit offensive mechanics for a single stationary target.
 *
 * <p>
 * The implementation models three independently timed Sesshou Sakura, dynamic
 * Skill damage, immediate Burst consumption, delayed Tenko Thunderbolts, and
 * the representable offensive portions of C1-C6. Sakura placement geometry,
 * range, random multi-target selection, hitlag, Witch's Revelation, and
 * Stellar-Conduct are intentionally outside this vertical slice.
 */
public class YaeMiko extends Character implements SimulatorInitializedCharacterEffect {
    private static final double SKILL_COOLDOWN = 4.0;
    private static final double BURST_COOLDOWN = 22.0;
    private static final double SAKURA_DURATION = 14.0;
    private static final double SAKURA_TICK_INTERVAL = 176.0 / 60.0;
    private static final double PARTICLE_COOLDOWN = 2.5;
    private static final double EPSILON = 1e-9;

    private final List<SesshouSakura> sakuras = new ArrayList<>();
    private int normalAttackStep;
    private long nextSakuraGeneration;
    private double nextParticleReadyTime = Double.NEGATIVE_INFINITY;
    private CombatSimulator initializedSimulator;

    /** Constructs the repository-default C6 Yae Miko. */
    public YaeMiko(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /**
     * Constructs Yae Miko at an explicit constellation.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param constellation constellation in the inclusive range 0-6
     */
    public YaeMiko(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Yae Miko with injectable talent data and constellation state.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     */
    public YaeMiko(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Yae Miko constellation must be between 0 and 6");
        }
        this.name = "Yae Miko";
        this.characterId = CharacterId.YAE_MIKO;
        this.element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(StatType.BASE_HP, getTalentValue("Base HP", 10372.0));
        baseStats.set(StatType.BASE_ATK, getTalentValue("Base ATK", 340.0));
        baseStats.set(StatType.BASE_DEF, getTalentValue("Base DEF", 569.0));
        baseStats.add(
                StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(SKILL_COOLDOWN);
        setSkillMaxCharges(3);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds this mutable summon owner to exactly one simulator instance. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Yae Miko cannot be reused across CombatSimulator instances");
        }
        initializedSimulator = sim;
    }

    /** Returns the 90-Energy old-base-kit Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 90.0);
    }

    /** Adds A4's live final-EM to Sesshou Sakura DMG Bonus conversion. */
    @Override
    public void applyPassive(StatsContainer stats) {
        stats.add(
                StatType.ELEMENTAL_MASTERY_TO_SKILL_DMG_BONUS_RATIO,
                getTalentValue("A4 EM Skill DMG Ratio", 0.0015));
    }

    /** Returns the number of Sakura active in the half-open lifetime window. */
    public int getSakuraCount(double currentTime) {
        pruneInactiveSakuras(currentTime);
        return sakuras.size();
    }

    /**
     * Returns the current single-target linked Sakura level.
     *
     * <p>C2 starts one level higher and raises the maximum from three to four.
     */
    public int getCurrentSakuraLevel(double currentTime) {
        int count = getSakuraCount(currentTime);
        if (count == 0) {
            return 0;
        }
        return constellation >= 2 ? Math.min(4, count + 1) : count;
    }

    /** Dispatches typed attacks, summon placement, and Burst consumption. */
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
            case SKILL:
                castSkill(sim);
                break;
            case BURST:
                castBurst(sim);
                break;
            case PLUNGE:
                highPlunge(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Yae Miko: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double[] multipliers = { 0.674193, 0.654826, 0.967110 };
        double[] durations = { 16.0 / 60.0, 36.0 / 60.0, 79.0 / 60.0 };
        String key = "N" + (normalAttackStep + 1);
        AttackAction normal = new AttackAction(
                "Spiritfox Sin-Eater " + key,
                getTalentValue(key, multipliers[normalAttackStep]),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                durations[normalAttackStep],
                ActionType.NORMAL);
        normal.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
        sim.performAction(characterId, normal);
        normalAttackStep = (normalAttackStep + 1) % multipliers.length;
    }

    private void chargedAttack(CombatSimulator sim) {
        AttackAction charged = new AttackAction(
                "Spiritfox Sin-Eater Charged Attack",
                getTalentValue("Charged Attack", 2.429212),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                96.0 / 60.0,
                ActionType.CHARGE);
        charged.setICD(ICDType.None, ICDTag.ChargedAttack, 1.0);
        sim.performAction(characterId, charged);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction plunge = new AttackAction(
                "Spiritfox Sin-Eater High Plunge",
                getTalentValue("High Plunge", 2.607632),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                68.0 / 60.0,
                ActionType.PLUNGE);
        plunge.setICD(ICDType.None, ICDTag.PlungeAttack, 1.0);
        sim.performAction(characterId, plunge);
    }

    private void castSkill(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        schedule(sim, castTime + 16.0 / 60.0, activeSim ->
                markSkillUsed(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this)));
        schedule(sim, castTime + 34.0 / 60.0, activeSim ->
                placeSakura(activeSim, activeSim.getCurrentTime()));
        sim.advanceTime(37.0 / 60.0);
    }

    private void placeSakura(CombatSimulator sim, double placementTime) {
        pruneInactiveSakuras(placementTime);
        if (sakuras.size() == 3) {
            SesshouSakura oldest = sakuras.remove(0);
            oldest.active = false;
        }
        SesshouSakura sakura = new SesshouSakura(
                ++nextSakuraGeneration,
                placementTime + getTalentValue(
                        "Sakura Duration", SAKURA_DURATION));
        sakuras.add(sakura);
        scheduleSakuraTicks(
                sim,
                sakura,
                placementTime + getTalentValue(
                        "Sakura First Tick Delay", 86.0 / 60.0));
        schedule(sim, sakura.expirationTime, activeSim -> {
            if (sakura.active
                    && activeSim == initializedSimulator
                    && activeSim.getCurrentTime() + EPSILON
                            >= sakura.expirationTime) {
                sakura.active = false;
                pruneInactiveSakuras(activeSim.getCurrentTime());
            }
        });
    }

    private void scheduleSakuraTicks(
            CombatSimulator sim,
            SesshouSakura sakura,
            double firstTickTime) {
        sim.registerEvent(new SimpleTimerEvent(
                firstTickTime,
                getTalentValue(
                        "Sakura Tick Interval", SAKURA_TICK_INTERVAL)) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                double time = activeSim.getCurrentTime();
                if (activeSim != initializedSimulator
                        || !sakura.isActive(time)) {
                    finish();
                    return;
                }
                resolveSakuraStrike(activeSim, time);
            }
        });
    }

    private void resolveSakuraStrike(CombatSimulator sim, double time) {
        int level = getCurrentSakuraLevel(time);
        if (level == 0) {
            return;
        }
        AttackAction strike = new AttackAction(
                "Sesshou Sakura Level " + level,
                sakuraMultiplier(level),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                false,
                ActionType.SKILL);
        strike.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0);
        if (constellation >= 6) {
            strike.setDefenseIgnore(
                    getTalentValue("C6 Sakura DEF Ignore", 0.60));
        }
        sim.performActionWithoutTimeAdvance(characterId, strike);

        if (time + EPSILON >= nextParticleReadyTime) {
            nextParticleReadyTime = time + getTalentValue(
                    "Particle Cooldown", PARTICLE_COOLDOWN);
            sim.getEnergyDistributor().distributeParticles(
                    Element.ELECTRO,
                    getTalentValue("Sakura Particles", 1.0),
                    ParticleType.PARTICLE);
        }
        if (constellation >= 4) {
            applyC4Buff(sim, time);
        }
    }

    private double sakuraMultiplier(int level) {
        double[] talentNine = { 1.031424, 1.28928, 1.6116, 2.0145 };
        double[] talentTwelve = { 1.21344, 1.5168, 1.896, 2.37 };
        double defaultValue = constellation >= 3
                ? talentTwelve[level - 1]
                : talentNine[level - 1];
        return getTalentValue("Sakura Level " + level, defaultValue);
    }

    private void applyC4Buff(CombatSimulator sim, double time) {
        double bonus = getTalentValue("C4 Electro DMG Bonus", 0.20);
        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Sakura Channeling",
                BuffId.YAE_MIKO_C4_ELECTRO_DMG_BONUS,
                getTalentValue("C4 Duration", 5.0),
                time,
                stats -> stats.add(StatType.ELECTRO_DMG_BONUS, bonus))
                .forElement(Element.ELECTRO)
                .sourcedBy(characterId));
    }

    private void castBurst(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        pruneInactiveSakuras(castTime);
        int consumedSakuras = sakuras.size();
        markBurstUsed(castTime, sim.getApplicableBuffs(this));
        consumeAllSakuras();
        for (int i = 0; i < consumedSakuras; i++) {
            resetSkillCooldown(castTime);
        }

        schedule(sim, castTime + 100.0 / 60.0, activeSim ->
                resolveBurstHit(activeSim, false));
        for (int i = 0; i < consumedSakuras; i++) {
            double hitTime = castTime + (154.0 + (24.0 * i)) / 60.0;
            schedule(sim, hitTime, activeSim -> {
                resolveBurstHit(activeSim, true);
                if (constellation >= 1) {
                    receiveFlatEnergy(getTalentValue("C1 Flat Energy", 8.0));
                }
            });
        }
        sim.advanceTime(114.0 / 60.0);
    }

    private void resolveBurstHit(CombatSimulator sim, boolean tenko) {
        String key = tenko ? "Tenko Thunderbolt" : "Tenko Kenshin";
        double defaultValue;
        if (tenko) {
            defaultValue = constellation >= 5 ? 6.67632 : 5.674872;
        } else {
            defaultValue = constellation >= 5 ? 5.20 : 4.42;
        }
        AttackAction burst = new AttackAction(
                key,
                getTalentValue(key, defaultValue),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                false,
                ActionType.BURST);
        burst.setICD(ICDType.None, ICDTag.ElementalBurst, 1.0);
        sim.performActionWithoutTimeAdvance(characterId, burst);
    }

    private void consumeAllSakuras() {
        for (SesshouSakura sakura : sakuras) {
            sakura.active = false;
        }
        sakuras.clear();
    }

    private void pruneInactiveSakuras(double currentTime) {
        for (SesshouSakura sakura : sakuras) {
            if (currentTime + EPSILON >= sakura.expirationTime) {
                sakura.active = false;
            }
        }
        sakuras.removeIf(sakura -> !sakura.active);
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

    /** Owner-local Sakura generation and half-open lifetime. */
    private static final class SesshouSakura {
        private final long generation;
        private final double expirationTime;
        private boolean active = true;

        private SesshouSakura(long generation, double expirationTime) {
            this.generation = generation;
            this.expirationTime = expirationTime;
        }

        private boolean isActive(double currentTime) {
            return generation > 0
                    && active
                    && currentTime + EPSILON < expirationTime;
        }
    }
}
