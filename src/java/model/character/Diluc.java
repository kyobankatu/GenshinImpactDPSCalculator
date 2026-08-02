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
 * Diluc offensive implementation for stationary single-target combat.
 *
 * <p>
 * Tempered Sword, the three-stage Searing Onslaught chain, Dawn, the Dawn
 * infusion, A4, C3-C6, deterministic average Skill particles, and sourced
 * hitmarks are represented. Dawn's unusual five-hit/five-second application
 * rule is encoded locally by granting 2U only to hit one and hit six.
 *
 * <p>
 * Charged Attack is intentionally unavailable because its complete release
 * and ending timing is not established for this campaign. A1 stamina, C1
 * enemy-HP checks, C2 incoming-hit stacks, moving-Burst geometry, hitlag, and
 * defensive behavior remain outside this stationary offensive slice.
 */
public class Diluc extends Character implements
        SimulatorInitializedCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double SKILL_COOLDOWN = 10.0;
    private static final double SKILL_CHAIN_WINDOW = 4.0;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double BURST_COOLDOWN = 12.0;
    private static final double BASE_INFUSION_DURATION = 8.0;
    private static final double A4_INFUSION_DURATION =
            BASE_INFUSION_DURATION + 4.0;
    private static final double C4_READY_DELAY = 2.0;
    private static final double C4_WINDOW = 2.0;
    private static final double C6_DURATION = 6.0;

    private static final double[] NORMAL_MULTIPLIERS = {
            1.64794, 1.61002, 1.81542, 2.46164
    };
    private static final int[] NORMAL_HIT_FRAMES = { 24, 39, 26, 49 };
    private static final int[] NORMAL_ACTION_FRAMES = { 42, 56, 44, 111 };
    private static final double[] SKILL_MULTIPLIERS = {
            1.6048, 1.6592, 2.1896
    };
    private static final double[] C3_SKILL_MULTIPLIERS = {
            1.888, 1.952, 2.576
    };
    private static final int[] SKILL_HIT_FRAMES = { 24, 28, 46 };
    private static final int[] SKILL_ACTION_FRAMES = { 43, 49, 71 };
    private static final int[] BURST_HIT_FRAMES = {
            100, 112, 124, 136, 148, 160, 172, 184, 196, 202
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int skillStage;
    private double skillChainDeadline = Double.NEGATIVE_INFINITY;
    private double infusionExpiresAt = Double.NEGATIVE_INFINITY;
    private long c4Generation;
    private int c6NormalUsesRemaining;
    private double c6ExpiresAt = Double.NEGATIVE_INFINITY;

    /** Constructs the repository-default C6 Diluc. */
    public Diluc(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /**
     * Constructs Diluc at an explicit constellation.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param constellation constellation in the inclusive range 0-6
     */
    public Diluc(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Diluc with injectable talent data and constellation state.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Diluc(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Diluc constellation must be between 0 and 6");
        }
        this.name = "Diluc";
        this.characterId = CharacterId.DILUC;
        this.element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(
                StatType.BASE_HP,
                getTalentValue("Base HP", 12981.0));
        baseStats.set(
                StatType.BASE_ATK,
                getTalentValue("Base ATK", 335.0));
        baseStats.set(
                StatType.BASE_DEF,
                getTalentValue("Base DEF", 784.0));
        baseStats.add(
                StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds character-local delayed state to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Diluc cannot be reused across CombatSimulator instances");
        }
        initializedSimulator = sim;
    }

    /** Resets only the Normal chain when Diluc leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator sim) {
        normalAttackStep = 0;
    }

    /** Returns Diluc's 40-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 40.0);
    }

    /** Diluc has no unconditional static combat passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /**
     * Lets the second and third Searing Onslaught stages pass the shared
     * gateway while preserving the ten-second cooldown started by stage one.
     */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (skillStage > 0
                && currentTime + EPSILON < skillChainDeadline) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Returns whether either a chain continuation or a fresh Skill is ready. */
    @Override
    public boolean canSkill(double currentTime) {
        return getSkillCDRemaining(currentTime) <= EPSILON;
    }

    /** Returns the next Skill stage, where zero denotes the first stage. */
    public int getSkillStage(double currentTime) {
        if (skillStage > 0
                && currentTime + EPSILON < skillChainDeadline) {
            return skillStage;
        }
        return 0;
    }

    /** Returns the current Searing Onslaught continuation deadline. */
    public double getSkillChainDeadline() {
        return skillChainDeadline;
    }

    /** Reports whether Dawn's weapon infusion remains active. */
    public boolean isPyroInfusionActive(double currentTime) {
        return currentTime + EPSILON < infusionExpiresAt;
    }

    /** Returns Dawn's current infusion expiry timestamp. */
    public double getInfusionExpiresAt() {
        return infusionExpiresAt;
    }

    /** Returns C6 Normal uses remaining in its half-open six-second window. */
    public int getC6NormalUsesRemaining(double currentTime) {
        if (currentTime + EPSILON >= c6ExpiresAt) {
            return 0;
        }
        return c6NormalUsesRemaining;
    }

    /** Dispatches Diluc's supported typed offensive actions. */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        initializeForSimulator(sim);
        boolean preservesNormalChain = constellation >= 6
                && request.getKey() == CharacterActionKey.SKILL;
        if (request.getKey() != CharacterActionKey.NORMAL
                && !preservesNormalChain) {
            normalAttackStep = 0;
        }

        switch (request.getKey()) {
            case NORMAL:
                normalAttack(sim);
                break;
            case PLUNGE:
                highPlunge(sim);
                break;
            case SKILL:
                searingOnslaught(sim);
                break;
            case BURST:
                dawn(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Diluc: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        int step = normalAttackStep;
        boolean infused = isPyroInfusionActive(castTime);
        boolean c6Empowered = constellation >= 6
                && getC6NormalUsesRemaining(castTime) > 0;
        double speed = normalAttackSpeed(sim, castTime, c6Empowered);
        double speedScale = 1.0 + speed;

        AttackAction normal = new AttackAction(
                "Tempered Sword N" + (step + 1),
                getTalentValue(
                        "N" + (step + 1),
                        NORMAL_MULTIPLIERS[step]),
                infused ? Element.PYRO : Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        normal.setICD(
                ICDType.Standard,
                ICDTag.NormalAttack,
                infused ? 1.0 : 0.0);
        normal.setShatterTrigger(true);
        if (c6Empowered) {
            normal.addBonusStat(StatType.NORMAL_ATTACK_DMG_BONUS, 0.30);
            c6NormalUsesRemaining--;
        }

        schedule(
                sim,
                castTime + NORMAL_HIT_FRAMES[step] * FRAME / speedScale,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, normal));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        sim.advanceTime(NORMAL_ACTION_FRAMES[step] * FRAME / speedScale);
    }

    private void highPlunge(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        boolean infused = isPyroInfusionActive(castTime);
        AttackAction plunge = new AttackAction(
                "Tempered Sword High Plunge",
                getTalentValue("High Plunge", 4.10702),
                infused ? Element.PYRO : Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                0.0,
                ActionType.PLUNGE);
        plunge.setICD(
                ICDType.None,
                ICDTag.PlungeAttack,
                infused ? 1.0 : 0.0);
        plunge.setShatterTrigger(true);
        schedule(
                sim,
                castTime + 58.0 * FRAME,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, plunge));
        sim.advanceTime(58.0 * FRAME);
    }

    private void searingOnslaught(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        if (skillStage > 0
                && castTime + EPSILON >= skillChainDeadline) {
            skillStage = 0;
        }
        int stage = skillStage;
        if (stage == 0) {
            markSkillUsed(castTime, sim.getApplicableBuffs(this));
        }

        double[] defaults = constellation >= 3
                ? C3_SKILL_MULTIPLIERS : SKILL_MULTIPLIERS;
        AttackAction skill = new AttackAction(
                "Searing Onslaught " + (stage + 1),
                getTalentValue(
                        "Searing Onslaught " + (stage + 1)
                                + (constellation >= 3 ? " C3" : ""),
                        defaults[stage]),
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        skill.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);
        skill.setShatterTrigger(true);
        double hitTime = castTime + SKILL_HIT_FRAMES[stage] * FRAME;
        schedule(sim, hitTime, activeSim -> {
            activeSim.performActionWithoutTimeAdvance(characterId, skill);
            schedule(
                    activeSim,
                    activeSim.getCurrentTime() + PARTICLE_TRAVEL,
                    particleSim -> particleSim.getEnergyDistributor()
                            .distributeParticles(
                                    Element.PYRO,
                                    1.25,
                                    ParticleType.PARTICLE));
        });

        if (constellation >= 4) {
            armC4Window(sim, castTime);
        }
        if (constellation >= 6) {
            c6NormalUsesRemaining = 2;
            c6ExpiresAt = castTime + C6_DURATION;
        }

        if (stage < 2) {
            skillStage = stage + 1;
            skillChainDeadline = castTime + SKILL_CHAIN_WINDOW;
        } else {
            skillStage = 0;
            skillChainDeadline = Double.NEGATIVE_INFINITY;
        }
        sim.advanceTime(SKILL_ACTION_FRAMES[stage] * FRAME);
    }

    private void dawn(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        markBurstUsed(castTime, sim.getApplicableBuffs(this));
        StatsContainer[] dawnSnapshot = new StatsContainer[1];

        infusionExpiresAt = castTime + A4_INFUSION_DURATION;
        removeBuff(BuffId.DILUC_A4_PYRO_DMG_BONUS);
        addBuff(new SimpleBuff(
                "Diluc Blessing of Phoenix",
                BuffId.DILUC_A4_PYRO_DMG_BONUS,
                A4_INFUSION_DURATION,
                castTime,
                stats -> stats.add(StatType.PYRO_DMG_BONUS, 0.20)));

        for (int hit = 0; hit < BURST_HIT_FRAMES.length; hit++) {
            int hitIndex = hit;
            schedule(
                    sim,
                    castTime + BURST_HIT_FRAMES[hit] * FRAME,
                    activeSim -> resolveDawnHit(
                            activeSim, hitIndex, dawnSnapshot));
        }
        sim.advanceTime(140.0 * FRAME);
    }

    private void resolveDawnHit(
            CombatSimulator sim,
            int hitIndex,
            StatsContainer[] dawnSnapshot) {
        boolean c5 = constellation >= 5;
        String name;
        String key;
        double multiplier;
        if (hitIndex == 0) {
            name = "Dawn Initial";
            key = c5 ? "Dawn Initial C5" : "Dawn Initial";
            multiplier = c5 ? 4.08 : 3.468;
        } else if (hitIndex == BURST_HIT_FRAMES.length - 1) {
            name = "Dawn Explosion";
            key = c5 ? "Dawn Explosion C5" : "Dawn Explosion";
            multiplier = c5 ? 4.08 : 3.468;
        } else {
            name = "Dawn DoT " + hitIndex;
            key = c5 ? "Dawn DoT C5" : "Dawn DoT";
            multiplier = c5 ? 1.20 : 1.02;
        }

        AttackAction burst = new AttackAction(
                name,
                getTalentValue(key, multiplier),
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                ActionType.BURST);
        if (hitIndex > 0 && dawnSnapshot[0] != null) {
            burst.setStatSnapshot(dawnSnapshot[0]);
        }
        double gauge = hitIndex == 0 || hitIndex == 5 ? 2.0 : 0.0;
        burst.setICD(ICDType.None, ICDTag.ElementalBurst, gauge);
        burst.setShatterTrigger(hitIndex == 0);
        sim.performActionWithoutTimeAdvance(characterId, burst);
        if (hitIndex == 0) {
            captureSnapshot(
                    sim.getCurrentTime(),
                    sim.getApplicableBuffs(this));
            dawnSnapshot[0] = getSnapshot().merge(null);
        }
    }

    private void armC4Window(CombatSimulator sim, double castTime) {
        long generation = ++c4Generation;
        double readyAt = castTime + C4_READY_DELAY;
        schedule(sim, readyAt, activeSim -> {
            if (generation != c4Generation) {
                return;
            }
            removeBuff(BuffId.DILUC_C4_SKILL_DMG_BONUS);
            addBuff(new SimpleBuff(
                    "Diluc Flowing Flame",
                    BuffId.DILUC_C4_SKILL_DMG_BONUS,
                    C4_WINDOW,
                    activeSim.getCurrentTime(),
                    stats -> stats.add(
                            StatType.SKILL_DMG_BONUS,
                            0.40)));
        });
    }

    private double normalAttackSpeed(
            CombatSimulator sim,
            double currentTime,
            boolean c6Empowered) {
        StatsContainer stats = getEffectiveStats(currentTime);
        List<Buff> buffs = sim.getApplicableBuffs(this);
        for (Buff buff : buffs) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        double speed = stats.get(StatType.ATK_SPD)
                + stats.get(StatType.NORMAL_ATTACK_SPD);
        if (c6Empowered) {
            speed += 0.30;
        }
        return Math.min(0.60, Math.max(0.0, speed));
    }

    private void schedule(
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
}
