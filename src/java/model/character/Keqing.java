package model.character;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
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
 * Keqing's legacy offensive kit for stationary single-target combat.
 *
 * <p>
 * Yunlai Swordsmanship, Stellar Restoration, and Starward Sword use current
 * gcsim release frames and talent-9 values. A second Skill request while the
 * Lightning Stiletto is live performs the recast; a Charged Attack instead
 * consumes it for two Thunderclap Slashes. The first cast's original 7.5-second
 * cooldown deadline is preserved across either resolution path.
 *
 * <p>
 * A1, A4, C1, C3, C4, C5, and C6 are represented. Charged Attack stamina,
 * hitlag, C2's random particle, geometry, and pending-event snapshot restore
 * are intentionally excluded. Character-local timers and listeners bind to one
 * simulator instance.
 */
public class Keqing extends Character implements
        SimulatorInitializedCharacterEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double SKILL_COOLDOWN = 7.5;
    private static final double BURST_COOLDOWN = 12.0;
    private static final double STILETTO_DURATION = 5.0 + 20.0 * FRAME;
    private static final double INFUSION_DURATION = 5.0 + 20.0 * FRAME;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double A4_DURATION = 8.0;
    private static final double C4_DURATION = 10.0;
    private static final double C6_DURATION = 8.0;

    private static final double[][] NORMAL_MULTIPLIERS = {
            { 0.75366 },
            { 0.75366 },
            { 1.00014 },
            { 0.57828, 0.63200 },
            { 1.23082 }
    };
    private static final int[][] NORMAL_HIT_FRAMES = {
            { 10 }, { 10 }, { 14 }, { 11, 21 }, { 22 }
    };
    private static final int[] NORMAL_ACTION_FRAMES = {
            19, 24, 36, 58, 66
    };
    private static final double[] CHARGED_MULTIPLIERS = {
            1.41094, 1.58000
    };
    private static final int[] CHARGED_HIT_FRAMES = { 21, 24 };

    private final Map<String, Double> c6Expirations = new HashMap<>();
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private boolean stilettoActive;
    private double stilettoExpiresAt = Double.NEGATIVE_INFINITY;
    private double firstSkillCastTime = Double.NEGATIVE_INFINITY;
    private long stilettoGeneration;
    private double infusionExpiresAt = Double.NEGATIVE_INFINITY;

    /** Constructs the repository-default C6 Keqing. */
    public Keqing(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Keqing at an explicit constellation. */
    public Keqing(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Keqing with injectable talent data and constellation state.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Keqing(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Keqing constellation must be between 0 and 6");
        }
        this.name = "Keqing";
        this.characterId = CharacterId.KEQING;
        this.element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13103.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 323.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 799.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds Keqing's reaction listener and timer state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Keqing cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;
        if (constellation >= 4) {
            sim.addReactionListener((result, source, time, activeSim) -> {
                if (source == Keqing.this
                        && result.getKind() != ReactionResult.Kind.NONE) {
                    replaceC4Buff(time);
                }
            });
        }
    }

    /** Returns Keqing's 40-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 40.0);
    }

    /** Keqing has no unconditional offensive passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Ascension passives are tied to Skill and Burst state.
    }

    /** Returns whether A1's Electro infusion is live. */
    public boolean isElectroInfusionActive(double currentTime) {
        return currentTime < infusionExpiresAt;
    }

    /** Returns whether a recastable Lightning Stiletto is live. */
    public boolean isStilettoActive(double currentTime) {
        expireStilettoIfNeeded(currentTime);
        return stilettoActive;
    }

    /** Dispatches Keqing's supported typed offensive actions. */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        initializeForSimulator(sim);
        expireStilettoIfNeeded(sim.getCurrentTime());
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
                if (stilettoActive) {
                    recastSkill(sim);
                } else {
                    castStiletto(sim);
                }
                break;
            case BURST:
                starwardSword(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Keqing: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        int step = normalAttackStep;
        Element attackElement = isElectroInfusionActive(castTime)
                ? Element.ELECTRO : Element.PHYSICAL;
        for (int hit = 0; hit < NORMAL_MULTIPLIERS[step].length; hit++) {
            int hitIndex = hit;
            AttackAction normal = attack(
                    "Yunlai Swordsmanship N" + (step + 1)
                            + " Hit " + (hit + 1),
                    getTalentValue(
                            "N" + (step + 1) + "_" + (hit + 1),
                            NORMAL_MULTIPLIERS[step][hit]),
                    attackElement,
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    ActionType.NORMAL,
                    ICDType.Standard,
                    ICDTag.NormalAttack,
                    attackElement == Element.ELECTRO ? 1.0 : 0.0);
            schedule(sim, castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    activeSim -> activeSim.performActionWithoutTimeAdvance(
                            characterId, normal));
        }
        activateC6Source("normal", castTime);
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        sim.advanceTime(NORMAL_ACTION_FRAMES[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        Element attackElement = isElectroInfusionActive(castTime)
                ? Element.ELECTRO : Element.PHYSICAL;
        for (int hit = 0; hit < CHARGED_MULTIPLIERS.length; hit++) {
            AttackAction charged = attack(
                    "Yunlai Swordsmanship Charged Hit " + (hit + 1),
                    getTalentValue(
                            "CA_" + (hit + 1),
                            CHARGED_MULTIPLIERS[hit]),
                    attackElement,
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    ActionType.CHARGE,
                    ICDType.Standard,
                    ICDTag.ChargedAttack,
                    attackElement == Element.ELECTRO ? 1.0 : 0.0);
            schedule(sim, castTime + CHARGED_HIT_FRAMES[hit] * FRAME,
                    activeSim -> activeSim.performActionWithoutTimeAdvance(
                            characterId, charged));
        }
        if (stilettoActive) {
            consumeStiletto(sim);
            for (int hit = 0; hit < 2; hit++) {
                boolean generatesParticles = hit == 0;
                AttackAction thunderclap = skillAttack(
                        "Stellar Restoration Thunderclap Slash " + (hit + 1),
                        getTalentValue(
                                constellation >= 5
                                        ? "Skill Thunderclap C5"
                                        : "Skill Thunderclap",
                                constellation >= 5 ? 1.680 : 1.428),
                        2.0);
                thunderclap.setICD(
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        2.0);
                schedule(sim,
                        castTime + CHARGED_HIT_FRAMES[hit] * FRAME,
                        activeSim -> {
                            activeSim.performActionWithoutTimeAdvance(
                                    characterId, thunderclap);
                            if (generatesParticles) {
                                scheduleParticles(activeSim);
                            }
                        });
            }
        }
        activateC6Source("charged", castTime);
        sim.advanceTime(36.0 * FRAME);
    }

    private void highPlunge(CombatSimulator sim) {
        Element attackElement = isElectroInfusionActive(sim.getCurrentTime())
                ? Element.ELECTRO : Element.PHYSICAL;
        AttackAction plunge = attack(
                "Yunlai Swordsmanship High Plunge",
                getTalentValue("Plunge High", 2.933586),
                attackElement,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                attackElement == Element.ELECTRO ? 1.0 : 0.0);
        plunge.setAnimationDuration(75.0 * FRAME);
        sim.performAction(characterId, plunge);
    }

    private void castStiletto(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        firstSkillCastTime = castTime;
        stilettoActive = true;
        stilettoExpiresAt = castTime + STILETTO_DURATION;
        long generation = ++stilettoGeneration;

        markSkillUsed(castTime, sim.getApplicableBuffs(this));
        resetSkillCooldown(castTime);
        AttackAction stiletto = skillAttack(
                "Stellar Restoration Lightning Stiletto",
                getTalentValue(
                        constellation >= 5 ? "Skill Stiletto C5" : "Skill Stiletto",
                        constellation >= 5 ? 1.008 : 0.8568),
                1.0);
        schedule(sim, castTime + 25.0 * FRAME,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, stiletto));
        schedule(sim, stilettoExpiresAt, activeSim -> {
            if (generation == stilettoGeneration && stilettoActive) {
                consumeStiletto(activeSim);
            }
        });
        activateC6Source("skill", castTime);
        sim.advanceTime(37.0 * FRAME);
    }

    private void recastSkill(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        consumeStiletto(sim);
        infusionExpiresAt = castTime + INFUSION_DURATION;

        if (constellation >= 1) {
            AttackAction c1 = skillAttack(
                    "Stellar Restoration C1 Terminus",
                    getTalentValue("C1 Multiplier", 0.50),
                    1.0);
            c1.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0);
            schedule(sim, castTime + 16.0 * FRAME,
                    activeSim -> activeSim.performActionWithoutTimeAdvance(
                            characterId, c1));
        }

        AttackAction slash = skillAttack(
                "Stellar Restoration Slashing",
                getTalentValue(
                        constellation >= 5 ? "Skill Slashing C5" : "Skill Slashing",
                constellation >= 5 ? 3.360 : 2.856),
                2.0);
        slash.setICD(ICDType.Standard, ICDTag.ElementalSkill, 2.0);
        schedule(sim, castTime + 16.0 * FRAME, activeSim -> {
            activeSim.performActionWithoutTimeAdvance(characterId, slash);
            scheduleParticles(activeSim);
        });
        activateC6Source("skill", castTime);
        sim.advanceTime(43.0 * FRAME);
    }

    private void starwardSword(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        markBurstUsed(castTime, sim.getApplicableBuffs(this));
        replaceA4Buff(castTime);
        activateC6Source("burst", castTime);

        boolean c3 = constellation >= 3;
        scheduleBurstHit(sim, castTime + 56.0 * FRAME,
                "Starward Sword Initial",
                getTalentValue(c3 ? "Burst Initial C3" : "Burst Initial",
                        c3 ? 1.760 : 1.496));
        for (int hit = 0; hit < 8; hit++) {
            scheduleBurstHit(sim, castTime + (82.0 + hit * 11.0) * FRAME,
                    "Starward Sword Consecutive Slash " + (hit + 1),
                    getTalentValue(c3 ? "Burst Slash C3" : "Burst Slash",
                            c3 ? 0.480 : 0.408));
        }
        scheduleBurstHit(sim, castTime + 197.0 * FRAME,
                "Starward Sword Last Attack",
                getTalentValue(c3 ? "Burst Final C3" : "Burst Final",
                        c3 ? 3.776 : 3.2096));
        sim.advanceTime(124.0 * FRAME);
    }

    private void scheduleBurstHit(
            CombatSimulator sim,
            double hitTime,
            String name,
            double multiplier) {
        AttackAction hit = attack(
                name,
                multiplier,
                Element.ELECTRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0);
        schedule(sim, hitTime,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, hit));
    }

    private AttackAction skillAttack(
            String actionName,
            double multiplier,
            double gaugeUnits) {
        return attack(
                actionName,
                multiplier,
                Element.ELECTRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                gaugeUnits);
    }

    private static AttackAction attack(
            String actionName,
            double multiplier,
            Element attackElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits) {
        AttackAction action = new AttackAction(
                actionName,
                multiplier,
                attackElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        return action;
    }

    private void consumeStiletto(CombatSimulator sim) {
        if (!stilettoActive) {
            return;
        }
        stilettoActive = false;
        stilettoExpiresAt = Double.NEGATIVE_INFINITY;
        stilettoGeneration++;
        double remaining = Math.max(
                0.0,
                firstSkillCastTime + SKILL_COOLDOWN - sim.getCurrentTime());
        setSkillCD(remaining);
        markSkillUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
        setSkillCD(SKILL_COOLDOWN);
    }

    private void expireStilettoIfNeeded(double currentTime) {
        if (stilettoActive && currentTime >= stilettoExpiresAt) {
            stilettoActive = false;
            stilettoExpiresAt = Double.NEGATIVE_INFINITY;
            stilettoGeneration++;
        }
    }

    private void scheduleParticles(CombatSimulator sim) {
        schedule(sim, sim.getCurrentTime() + PARTICLE_TRAVEL,
                activeSim -> activeSim.getEnergyDistributor()
                        .distributeParticles(
                                Element.ELECTRO,
                                2.5,
                                ParticleType.PARTICLE));
    }

    private void replaceA4Buff(double currentTime) {
        removeBuff(BuffId.KEQING_A4_CRIT_RATE_AND_ER);
        addBuff(new SimpleBuff(
                "Keqing A4 Aristocratic Dignity",
                BuffId.KEQING_A4_CRIT_RATE_AND_ER,
                A4_DURATION,
                currentTime,
                stats -> {
                    stats.add(StatType.CRIT_RATE, 0.15);
                    stats.add(StatType.ENERGY_RECHARGE, 0.15);
                }));
    }

    private void replaceC4Buff(double currentTime) {
        removeBuff(BuffId.KEQING_C4_ATK_BONUS);
        addBuff(new SimpleBuff(
                "Keqing C4 Attunement",
                BuffId.KEQING_C4_ATK_BONUS,
                C4_DURATION,
                currentTime,
                stats -> stats.add(StatType.ATK_PERCENT, 0.25)));
    }

    private void activateC6Source(String source, double currentTime) {
        if (constellation < 6) {
            return;
        }
        c6Expirations.entrySet().removeIf(
                entry -> entry.getValue() <= currentTime);
        c6Expirations.put(source, currentTime + C6_DURATION);
        getActiveBuffs().removeIf(buff -> buff instanceof C6ElectroBuff);
        addBuff(new C6ElectroBuff(currentTime, c6Expirations));
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

    /** C6 stack set with one independently refreshed window per action source. */
    private static final class C6ElectroBuff extends Buff {
        private final Map<String, Double> expirations;

        private C6ElectroBuff(
                double currentTime,
                Map<String, Double> expirations) {
            super(
                    "Keqing C6 Tenacious Star",
                    BuffId.KEQING_C6_ELECTRO_DMG_BONUS,
                    maxExpiration(expirations) - currentTime,
                    currentTime);
            this.expirations = new HashMap<>(expirations);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            long stacks = expirations.values().stream()
                    .filter(expiration -> currentTime < expiration)
                    .count();
            stats.add(StatType.ELECTRO_DMG_BONUS, 0.06 * stacks);
        }

        private static double maxExpiration(
                Map<String, Double> expirations) {
            return expirations.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElse(Double.NEGATIVE_INFINITY);
        }
    }
}
