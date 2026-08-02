package model.character;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

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
 * Yoimiya's old-base-kit offensive mechanics for one stationary target.
 *
 * <p>
 * Firework Flare-Up uses talent-9 values and current gcsim release frames with
 * its default ten-frame projectile travel. Niwabi Fire-Dance converts Normal
 * arrows to Pyro and snapshots its multiplicative talent modifier when each
 * arrow is released. Ryuukin Saxifrage applies one owner-local Aurous Blaze
 * mark whose positive true-hit triggers are restricted to other party members.
 *
 * <p>
 * A1, A4, C1, C3, C4, and C5 are represented. Fully charged aimed geometry,
 * projectile misses, hitlag, actual-CRIT C2, random C6 arrows, enemy-defeat
 * mark transfer and C1 ATK, player defeat, and multi-target behavior are
 * intentionally excluded. Pending character timer events are not reconstructed
 * by global simulator snapshots.
 */
public class Yoimiya extends Character implements
        SimulatorInitializedCharacterEffect,
        SwitchAwareCharacter {
    private static final double SKILL_COOLDOWN = 18.0;
    private static final double BURST_COOLDOWN = 15.0;
    private static final double NIWABI_DURATION = 10.0;
    private static final double A1_DURATION = 3.0;
    private static final double A4_DURATION = 15.0;
    private static final double BASE_MARK_DURATION = 10.0;
    private static final double C1_MARK_DURATION = 14.0;
    private static final double MARK_TRIGGER_COOLDOWN = 2.0;
    private static final double PARTICLE_COOLDOWN = 2.0;
    private static final double PROJECTILE_TRAVEL = 10.0 / 60.0;
    private static final double EPSILON = 1e-9;

    private static final double[][] NORMAL_MULTIPLIERS = {
            { 0.5994, 0.5994 },
            { 1.14996 },
            { 1.494948 },
            { 0.7807, 0.7807 },
            { 1.78044 }
    };
    private static final int[][] NORMAL_RELEASE_FRAMES = {
            { 15, 24 },
            { 17 },
            { 25 },
            { 11, 26 },
            { 17 }
    };
    private static final int[] NORMAL_ACTION_FRAMES = {
            35, 26, 39, 44, 52
    };

    private final Set<AttackAction> niwabiArrows =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private boolean niwabiActive;
    private double niwabiExpiresAt = Double.NEGATIVE_INFINITY;
    private long niwabiGeneration;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private int a1Stacks;
    private double a1ExpiresAt = Double.NEGATIVE_INFINITY;
    private boolean aurousBlazeActive;
    private double aurousBlazeExpiresAt = Double.NEGATIVE_INFINITY;
    private double nextAurousBlazeTriggerTime = Double.NEGATIVE_INFINITY;
    private long aurousBlazeGeneration;
    private boolean resolvingAurousBlaze;

    /** Constructs the repository-default C6 Yoimiya. */
    public Yoimiya(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /**
     * Constructs Yoimiya at an explicit constellation.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param constellation constellation in the inclusive range 0-6
     */
    public Yoimiya(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Yoimiya with injectable talent data and constellation state.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Yoimiya(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Yoimiya constellation must be between 0 and 6");
        }
        this.name = "Yoimiya";
        this.characterId = CharacterId.YOIMIYA;
        this.element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(
                StatType.BASE_HP,
                getTalentValue("Base HP", 10164.0));
        baseStats.set(
                StatType.BASE_ATK,
                getTalentValue("Base ATK", 323.0));
        baseStats.set(
                StatType.BASE_DEF,
                getTalentValue("Base DEF", 615.0));
        baseStats.add(
                StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds owner-local listeners to exactly one simulator instance. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Yoimiya cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;
        sim.addDamageListener((actor, action, damage, time) -> {
            handleNiwabiHit(actor, action, damage, time, sim);
            handleAurousBlazeTrigger(actor, action, damage, time, sim);
        });
    }

    /** Ends Niwabi immediately and resets the Normal chain on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator sim) {
        niwabiActive = false;
        niwabiExpiresAt = Double.NEGATIVE_INFINITY;
        niwabiGeneration++;
        normalAttackStep = 0;
    }

    /** Returns Yoimiya's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Yoimiya has no unconditional static passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /**
     * Reports whether Niwabi remains active in its half-open window.
     *
     * @param currentTime current simulation time in seconds
     * @return whether Normal arrows released now receive Niwabi
     */
    public boolean isNiwabiActive(double currentTime) {
        return niwabiActive
                && currentTime + EPSILON < niwabiExpiresAt;
    }

    /** Returns the current Niwabi expiry timestamp. */
    public double getNiwabiExpiresAt() {
        return niwabiExpiresAt;
    }

    /**
     * Returns unexpired A1 stacks.
     *
     * @param currentTime current simulation time in seconds
     * @return stack count in the inclusive range 0-10
     */
    public int getA1StackCount(double currentTime) {
        if (currentTime + EPSILON >= a1ExpiresAt) {
            return 0;
        }
        return a1Stacks;
    }

    /**
     * Reports whether Aurous Blaze remains active.
     *
     * @param currentTime current simulation time in seconds
     * @return whether the mark accepts party hits
     */
    public boolean isAurousBlazeActive(double currentTime) {
        return aurousBlazeActive
                && currentTime + EPSILON < aurousBlazeExpiresAt;
    }

    /** Returns the current Aurous Blaze expiry timestamp. */
    public double getAurousBlazeExpiresAt() {
        return aurousBlazeExpiresAt;
    }

    /** Dispatches Yoimiya's supported typed offensive actions. */
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
            case PLUNGE:
                highPlunge(sim);
                break;
            case SKILL:
                niwabiFireDance(sim);
                break;
            case BURST:
                ryuukinSaxifrage(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Yoimiya: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        int step = normalAttackStep;
        boolean infused = isNiwabiActive(castTime);
        double niwabiMultiplier = infused
                ? getTalentValue(
                        constellation >= 3
                                ? "Blazing Arrow Multiplier C3"
                                : "Blazing Arrow Multiplier",
                        constellation >= 3 ? 1.67646 : 1.58793)
                : 1.0;

        for (int hit = 0; hit < NORMAL_MULTIPLIERS[step].length; hit++) {
            int hitIndex = hit;
            double impactTime = castTime
                    + NORMAL_RELEASE_FRAMES[step][hit] / 60.0
                    + PROJECTILE_TRAVEL;
            schedule(sim, impactTime, activeSim -> {
                AttackAction action = new AttackAction(
                        "Firework Flare-Up N" + (step + 1)
                                + (NORMAL_MULTIPLIERS[step].length > 1
                                        ? " Hit " + (hitIndex + 1) : ""),
                        normalMultiplier(step, hitIndex)
                                * niwabiMultiplier,
                        infused ? Element.PYRO : Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        0.0,
                        ActionType.NORMAL);
                action.setICD(
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        infused ? 1.0 : 0.0);
                if (infused) {
                    niwabiArrows.add(action);
                }
                activeSim.performActionWithoutTimeAdvance(
                        characterId, action);
            });
        }

        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        sim.advanceTime(NORMAL_ACTION_FRAMES[step] / 60.0);
    }

    private double normalMultiplier(int step, int hit) {
        String key = "N" + (step + 1);
        if (NORMAL_MULTIPLIERS[step].length > 1) {
            key += " Hit " + (hit + 1);
        }
        return getTalentValue(key, NORMAL_MULTIPLIERS[step][hit]);
    }

    private void fullyChargedAimedShot(CombatSimulator sim) {
        AttackAction action = new AttackAction(
                "Firework Flare-Up Fully Charged Aimed Shot",
                getTalentValue("Fully Charged Aimed Shot", 2.108),
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                97.0 / 60.0,
                ActionType.CHARGE);
        action.setICD(ICDType.None, ICDTag.ChargedAttack, 1.0);
        sim.performAction(characterId, action);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction action = new AttackAction(
                "Firework Flare-Up High Plunge",
                getTalentValue("Plunge High", 2.6076),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                58.0 / 60.0,
                ActionType.PLUNGE);
        action.setICD(ICDType.None, ICDTag.PlungeAttack, 0.0);
        sim.performAction(characterId, action);
    }

    private void niwabiFireDance(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        long generation = ++niwabiGeneration;
        schedule(sim, castTime + 11.0 / 60.0, activeSim -> {
            if (generation != niwabiGeneration) {
                return;
            }
            markSkillUsed(
                    activeSim.getCurrentTime(),
                    activeSim.getApplicableBuffs(this));
            if (getA1StackCount(activeSim.getCurrentTime()) == 0) {
                a1Stacks = 0;
            }
            niwabiActive = true;
            niwabiExpiresAt = activeSim.getCurrentTime()
                    + NIWABI_DURATION;
            schedule(activeSim, niwabiExpiresAt, expirySim -> {
                if (generation == niwabiGeneration) {
                    niwabiActive = false;
                }
            });
        });
        sim.advanceTime(34.0 / 60.0);
    }

    private void handleNiwabiHit(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator sim) {
        boolean niwabiArrow = action != null
                && niwabiArrows.remove(action);
        if (!niwabiArrow
                || actor != this
                || sim.getCharacter(characterId) != this
                || action.getActionType() != ActionType.NORMAL
                || action.getElement() != Element.PYRO
                || !action.isHitEffectTrigger()
                || damage <= 0.0) {
            return;
        }

        if (time + EPSILON >= nextParticleTime) {
            nextParticleTime = time + PARTICLE_COOLDOWN;
            sim.getEnergyDistributor().distributeParticles(
                    Element.PYRO, 1.0, ParticleType.PARTICLE);
        }

        if (sim.getActiveCharacter() == this && isNiwabiActive(time)) {
            if (getA1StackCount(time) == 0) {
                a1Stacks = 0;
            }
            a1Stacks = Math.min(10, a1Stacks + 1);
            a1ExpiresAt = time + A1_DURATION;
            removeBuff(BuffId.YOIMIYA_A1_PYRO_DMG_BONUS);
            addBuff(new SimpleBuff(
                    "Yoimiya Tricks of the Trouble-Maker",
                    BuffId.YOIMIYA_A1_PYRO_DMG_BONUS,
                    A1_DURATION,
                    time,
                    stats -> stats.add(
                            StatType.PYRO_DMG_BONUS,
                            a1Stacks * 0.02)));
        }
    }

    private void ryuukinSaxifrage(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        markBurstUsed(castTime, sim.getApplicableBuffs(this));
        long generation = ++aurousBlazeGeneration;
        aurousBlazeActive = false;
        aurousBlazeExpiresAt = Double.NEGATIVE_INFINITY;
        nextAurousBlazeTriggerTime = Double.NEGATIVE_INFINITY;

        schedule(sim, castTime + 75.0 / 60.0, activeSim -> {
            if (generation != aurousBlazeGeneration) {
                return;
            }
            AttackAction initial = new AttackAction(
                    "Ryuukin Saxifrage",
                    getTalentValue(
                            constellation >= 5
                                    ? "Ryuukin Saxifrage C5"
                                    : "Ryuukin Saxifrage",
                            constellation >= 5 ? 2.544 : 2.1624),
                    Element.PYRO,
                    StatType.BASE_ATK,
                    StatType.BURST_DMG_BONUS,
                    0.0,
                    ActionType.BURST);
            initial.setICD(
                    ICDType.Standard, ICDTag.ElementalBurst, 2.0);
            performAurousBlazeSafe(activeSim, initial);
            applyAurousBlaze(activeSim, generation);
            applyA4(activeSim);
        });
        sim.advanceTime(113.0 / 60.0);
    }

    private void applyAurousBlaze(
            CombatSimulator sim,
            long generation) {
        aurousBlazeActive = true;
        aurousBlazeExpiresAt = sim.getCurrentTime()
                + (constellation >= 1
                        ? C1_MARK_DURATION : BASE_MARK_DURATION);
        nextAurousBlazeTriggerTime = Double.NEGATIVE_INFINITY;
        schedule(sim, aurousBlazeExpiresAt, activeSim -> {
            if (generation == aurousBlazeGeneration) {
                aurousBlazeActive = false;
            }
        });
    }

    private void applyA4(CombatSimulator sim) {
        int stacks = getA1StackCount(sim.getCurrentTime());
        double atkBonus = 0.10 + stacks * 0.01;
        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Yoimiya Summer Night's Dawn",
                BuffId.YOIMIYA_A4_TEAM_ATK,
                A4_DURATION,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.ATK_PERCENT, atkBonus))
                .exclude(characterId)
                .sourcedBy(characterId));
    }

    private void handleAurousBlazeTrigger(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator sim) {
        if (resolvingAurousBlaze
                || !isAurousBlazeActive(time)
                || actor == null
                || actor == this
                || sim.getCharacter(actor.getCharacterId()) != actor
                || action == null
                || !isAurousBlazeEligible(action)
                || !action.isHitEffectTrigger()
                || damage <= 0.0
                || time + EPSILON < nextAurousBlazeTriggerTime) {
            return;
        }
        nextAurousBlazeTriggerTime = time + MARK_TRIGGER_COOLDOWN;
        if (constellation >= 4) {
            reduceSkillCooldown(
                    time,
                    getTalentValue("C4 Skill Cooldown Reduction", 1.2));
        }
        long generation = aurousBlazeGeneration;
        schedule(sim, time + 1.0 / 60.0, activeSim -> {
            if (generation != aurousBlazeGeneration) {
                return;
            }
            AttackAction explosion = new AttackAction(
                    "Aurous Blaze Explosion",
                    getTalentValue(
                            constellation >= 5
                                    ? "Aurous Blaze Explosion C5"
                                    : "Aurous Blaze Explosion",
                            constellation >= 5 ? 2.44 : 2.074),
                    Element.PYRO,
                    StatType.BASE_ATK,
                    StatType.BURST_DMG_BONUS,
                    0.0,
                    ActionType.BURST);
            explosion.setICD(
                    ICDType.Standard,
                    ICDTag.ElementalBurst,
                    1.0);
            performAurousBlazeSafe(activeSim, explosion);
        });
    }

    private boolean isAurousBlazeEligible(AttackAction action) {
        switch (action.getActionType()) {
            case NORMAL:
            case CHARGE:
            case PLUNGE:
            case SKILL:
            case BURST:
                return true;
            default:
                return false;
        }
    }

    private void performAurousBlazeSafe(
            CombatSimulator sim,
            AttackAction action) {
        resolvingAurousBlaze = true;
        try {
            sim.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAurousBlaze = false;
        }
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
