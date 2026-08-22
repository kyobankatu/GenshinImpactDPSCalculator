package model.character;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
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
 * Noelle offensive implementation for stationary single-target combat.
 *
 * <p>
 * Favonius Bladework uses talent-9 values. Breastplate deals DEF-scaled
 * damage, creates an offensive-only 12 s state marker, generates no particles,
 * and supports the A4 cooldown reduction. Sweeping Time schedules its two cast
 * hits, applies an unoverrideable Geo infusion, and snapshots DEF into a flat
 * ATK conversion for 15 s. C2, C3, C4, C5, and C6 offensive branches are
 * represented where current hooks preserve their sourced behavior.
 *
 * <p>
 * Healing, shield absorption, incoming damage, stamina, enemy-defeat duration
 * extension, and area geometry are intentionally excluded. C4 is emitted only
 * at the scheduled natural-expiry boundary; shield destruction and replacement
 * cannot be represented without shield lifecycle hooks. Pending timer events
 * are not reconstructed by global simulator snapshots.
 */
public class Noelle extends Character implements
        FormStateProvider,
        SimulatorInitializedCharacterEffect {
    private static final double SKILL_COOLDOWN = 24.0;
    private static final double BURST_COOLDOWN = 15.0;
    private static final double BREASTPLATE_DURATION = 12.0;
    private static final double SWEEPING_TIME_DURATION = 15.0;
    private static final double A4_MINIMUM_HIT_INTERVAL = 0.1;
    private static final int A4_HITS_PER_REDUCTION = 4;
    private static final double A4_COOLDOWN_REDUCTION = 1.0;
    private static final double C2_CHARGED_DMG_BONUS = 0.15;
    private static final double C4_DAMAGE_MULTIPLIER = 4.0;
    private static final double C6_DEF_CONVERSION_BONUS = 0.50;

    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile PHYSICAL_N3_HITLAG =
            new HitlagProfile(0.09, 0.01, true, false, false);
    private static final HitlagProfile[] SWEEPING_NORMAL_HITLAG = {
        new HitlagProfile(0.10, 0.01, true, false, false),
        new HitlagProfile(0.10, 0.01, true, false, false),
        new HitlagProfile(0.10, 0.01, true, false, false),
        new HitlagProfile(0.15, 0.01, true, false, false)
    };
    private static final HitlagProfile PHYSICAL_CHARGED_HITLAG =
            new HitlagProfile(0.03, 0.01, true, false, false);
    private static final HitlagProfile BREASTPLATE_HITLAG =
            new HitlagProfile(0.0, 0.0, true, false, false);
    private static final HitlagProfile CAST_HITLAG =
            new HitlagProfile(0.15, 0.01, true, false, false);

    private int normalAttackStep;
    private int a4HitCount;
    private double lastA4HitTime = Double.NEGATIVE_INFINITY;
    private CombatSimulator initializedSimulator;

    /**
     * Constructs Noelle with the shared talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     */
    public Noelle(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    /**
     * Constructs Noelle with an explicit talent source for deterministic tests.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     */
    public Noelle(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData) {
        super(talentData);
        this.name = "Noelle";
        this.characterId = CharacterId.NOELLE;
        this.element = Element.GEO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = (int) getTalentValue("Constellation", 6.0);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Noelle constellation must be between 0 and 6");
        }

        baseStats.set(StatType.BASE_HP, getTalentValue("Base HP", 12071.0));
        baseStats.set(StatType.BASE_ATK, getTalentValue("Base ATK", 191.0));
        baseStats.set(StatType.BASE_DEF, getTalentValue("Base DEF", 799.0));
        baseStats.add(StatType.DEF_PERCENT,
                getTalentValue("Ascension DEF", 0.30));

        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /**
     * Registers Noelle's single-target A4 hit counter.
     *
     * <p>
     * The listener is simulator-owned, so a Noelle instance is deliberately
     * bound to the first simulator that receives it.
     *
     * @param sim simulator receiving Noelle
     * @throws IllegalStateException if this instance is reused across simulators
     */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Noelle cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;
        sim.addDamageListener((actor, action, damage, time) ->
                countA4Hit(actor, action, time));
    }

    /**
     * Returns Noelle's 60-Energy Burst cost.
     *
     * @return current Burst cost
     */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /**
     * Reports whether Sweeping Time's infusion and conversion remain active.
     *
     * @param currentTime current simulation time in seconds
     * @return {@code true} before the current Sweeping Time buff expires
     */
    @Override
    public boolean isFormActive(double currentTime) {
        return findActiveBuff(SweepingTimeBuff.class, currentTime) != null;
    }

    /**
     * Noelle has no unconditional static offensive passive in this slice.
     *
     * @param stats assembled stats container
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Conditional offensive effects are attached to actions and timed buffs.
    }

    /**
     * Dispatches Noelle's typed offensive actions.
     *
     * @param request requested action
     * @param sim active combat simulator
     */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(sim);
                break;
            case CHARGE:
                chargedSpin(sim);
                break;
            case SKILL:
                markSkillUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                breastplate(sim);
                break;
            case BURST:
                markBurstUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                sweepingTime(sim);
                break;
            case PLUNGE:
                highPlunge(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Noelle: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        int step = normalAttackStep;
        String key = "N" + (normalAttackStep + 1);
        double[] defaults = { 1.4536, 1.34774, 1.58474, 2.08402 };
        double[] durations = {
                48.0 / 60.0,
                56.0 / 60.0,
                41.0 / 60.0,
                120.0 / 60.0
        };
        AttackAction normal = createInfusedAttack(
                "Favonius Bladework - Maid " + key,
                getTalentValue(key, defaults[normalAttackStep]),
                StatType.NORMAL_ATTACK_DMG_BONUS,
                durations[normalAttackStep],
                ActionType.NORMAL,
                ICDTag.NormalAttack,
                sim.getCurrentTime());
        if (normal.getElement() == Element.GEO) {
            normal.setHitlagProfile(SWEEPING_NORMAL_HITLAG[step]);
        } else if (step == 2) {
            normal.setHitlagProfile(PHYSICAL_N3_HITLAG);
        }
        sim.performAction(characterId, normal);

        normalAttackStep++;
        if (normalAttackStep >= defaults.length) {
            normalAttackStep = 0;
        }
    }

    /**
     * Emits one steady-state cyclic Charged Attack hit.
     *
     * <p>
     * The sourced 23-frame spin interval is represented explicitly. Startup,
     * the final slash, maximum duration, and stamina consumption are excluded.
     *
     * @param sim active simulator
     */
    private void chargedSpin(CombatSimulator sim) {
        AttackAction charged = createInfusedAttack(
                "Favonius Bladework - Maid Charged Spin",
                getTalentValue("Charged Spin", 0.9322),
                StatType.CHARGED_ATTACK_DMG_BONUS,
                23.0 / 60.0,
                ActionType.CHARGE,
                ICDTag.ChargedAttack,
                sim.getCurrentTime());
        if (constellation >= 2) {
            charged.addBonusStat(
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    getTalentValue(
                            "C2 Charged DMG Bonus",
                            C2_CHARGED_DMG_BONUS));
        }
        if (charged.getElement() == Element.PHYSICAL) {
            charged.setHitlagProfile(PHYSICAL_CHARGED_HITLAG);
        }
        sim.performAction(characterId, charged);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction plunge = createInfusedAttack(
                "Favonius Bladework - Maid High Plunge",
                getTalentValue("Plunge High", 3.422517),
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                82.0 / 60.0,
                ActionType.PLUNGE,
                ICDTag.PlungeAttack,
                sim.getCurrentTime());
        plunge.setICD(
                ICDType.None,
                ICDTag.PlungeAttack,
                isFormActive(sim.getCurrentTime()) ? 1.0 : 0.0);
        sim.performAction(characterId, plunge);
    }

    private AttackAction createInfusedAttack(
            String actionName,
            double multiplier,
            StatType bonusStat,
            double duration,
            ActionType actionType,
            ICDTag icdTag,
            double currentTime) {
        boolean infused = isFormActive(currentTime);
        AttackAction action = new AttackAction(
                actionName,
                multiplier,
                infused ? Element.GEO : Element.PHYSICAL,
                StatType.BASE_ATK,
                bonusStat,
                duration,
                actionType);
        action.setICD(
                infused ? ICDType.None : ICDType.Standard,
                icdTag,
                infused ? 1.0 : 0.0);
        action.setShatterTrigger(true);
        return action;
    }

    /**
     * Starts Breastplate's offensive state and schedules its sourced cast hit.
     *
     * <p>
     * C4 is scheduled only for natural expiration of the same marker instance.
     * Replaced markers invalidate their old timer without producing damage.
     *
     * @param sim active simulator
     */
    private void breastplate(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        removeBuffType(BreastplateBuff.class);
        BreastplateBuff breastplate = new BreastplateBuff(
                getTalentValue("Breastplate Duration", BREASTPLATE_DURATION),
                castTime);
        addBuff(breastplate);

        double defaultMultiplier = constellation >= 3 ? 2.40 : 2.04;
        AttackAction hit = new AttackAction(
                "Breastplate",
                getTalentValue("Breastplate", defaultMultiplier),
                Element.GEO,
                StatType.BASE_DEF,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        hit.setICD(ICDType.Standard, ICDTag.ElementalSkill, 2.0);
        hit.setHitlagProfile(BREASTPLATE_HITLAG);
        hit.setShatterTrigger(true);
        scheduleSingleHit(sim, castTime + 15.0 / 60.0, hit);

        double expirationTime = breastplate.getExpirationTime();
        sim.registerEvent(new SimpleTimerEvent(
                expirationTime,
                BREASTPLATE_DURATION) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                if (!Noelle.this.getActiveBuffs().remove(breastplate)) {
                    return;
                }
                if (constellation >= 4) {
                    explodeBreastplate(activeSim);
                }
            }
        });

        AttackAction cast = new AttackAction(
                "Breastplate Cast",
                0.0,
                Element.GEO,
                StatType.BASE_DEF,
                null,
                43.0 / 60.0,
                ActionType.SKILL);
        cast.setICD(ICDType.None, ICDTag.ElementalSkill, 0.0);
        sim.performAction(characterId, cast);
    }

    private void explodeBreastplate(CombatSimulator sim) {
        AttackAction explosion = new AttackAction(
                "Breastplate Natural Expiry (C4)",
                getTalentValue("C4 Breastplate Explosion", C4_DAMAGE_MULTIPLIER),
                Element.GEO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        explosion.setICD(ICDType.Standard, ICDTag.ElementalSkill, 0.0);
        explosion.setHitlagProfile(CAST_HITLAG);
        explosion.setShatterTrigger(true);
        sim.performActionWithoutTimeAdvance(characterId, explosion);
    }

    /**
     * Applies Sweeping Time's snapshotted conversion and schedules both cast hits.
     *
     * @param sim active simulator
     */
    private void sweepingTime(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        StatsContainer castStats = getEffectiveStats(castTime);
        for (Buff buff : sim.getApplicableBuffs(this)) {
            if (!buff.isExpired(castTime)) {
                buff.apply(castStats, castTime);
            }
        }
        double defaultConversion = constellation >= 5 ? 0.80 : 0.68;
        double conversion = getTalentValue(
                "Burst DEF Conversion",
                defaultConversion);
        if (constellation >= 6) {
            conversion += getTalentValue(
                    "C6 DEF Conversion Bonus",
                    C6_DEF_CONVERSION_BONUS);
        }
        double capturedAtk = castStats.getTotalDef() * conversion;

        removeBuffType(SweepingTimeBuff.class);
        addBuff(new SweepingTimeBuff(
                getTalentValue(
                        "Sweeping Time Duration",
                        SWEEPING_TIME_DURATION),
                castTime,
                capturedAtk));

        double defaultBurstHit = constellation >= 5 ? 1.344 : 1.1424;
        AttackAction burstHit = new AttackAction(
                "Sweeping Time Burst Hit",
                getTalentValue("Sweeping Time Burst Hit", defaultBurstHit),
                Element.GEO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                ActionType.BURST);
        burstHit.setICD(ICDType.Standard, ICDTag.ElementalBurst, 1.0);
        burstHit.setHitlagProfile(CAST_HITLAG);
        burstHit.setShatterTrigger(true);

        double defaultSkillHit = constellation >= 5 ? 1.856 : 1.5776;
        AttackAction skillHit = new AttackAction(
                "Sweeping Time Skill Hit",
                getTalentValue("Sweeping Time Skill Hit", defaultSkillHit),
                Element.GEO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                ActionType.BURST);
        skillHit.setICD(ICDType.Standard, ICDTag.ElementalBurst, 1.0);
        skillHit.setHitlagProfile(CAST_HITLAG);
        skillHit.setShatterTrigger(true);

        scheduleSingleHit(sim, castTime + 24.0 / 60.0, burstHit);
        scheduleSingleHit(sim, castTime + 64.0 / 60.0, skillHit);

        AttackAction cast = new AttackAction(
                "Sweeping Time Cast",
                0.0,
                Element.GEO,
                StatType.BASE_ATK,
                null,
                89.0 / 60.0,
                ActionType.BURST);
        cast.setICD(ICDType.None, ICDTag.ElementalBurst, 0.0);
        sim.performAction(characterId, cast);
    }

    private void scheduleSingleHit(
            CombatSimulator sim,
            double hitTime,
            AttackAction action) {
        sim.registerEvent(new SimpleTimerEvent(hitTime, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                activeSim.performActionWithoutTimeAdvance(characterId, action);
            }
        });
    }

    private void countA4Hit(
            Character actor,
            AttackAction action,
            double currentTime) {
        if (actor != this) {
            return;
        }
        ActionType actionType = action.getActionType();
        if (actionType != ActionType.NORMAL && actionType != ActionType.CHARGE) {
            return;
        }
        if (currentTime - lastA4HitTime + 1e-9 < A4_MINIMUM_HIT_INTERVAL) {
            return;
        }
        lastA4HitTime = currentTime;
        a4HitCount++;
        if (a4HitCount >= A4_HITS_PER_REDUCTION) {
            a4HitCount = 0;
            reduceSkillCooldown(
                    currentTime,
                    getTalentValue(
                            "A4 Skill CD Reduction",
                            A4_COOLDOWN_REDUCTION));
        }
    }

    private <T extends Buff> T findActiveBuff(
            Class<T> buffType,
            double currentTime) {
        for (Buff buff : activeBuffs) {
            if (buffType.isInstance(buff) && !buff.isExpired(currentTime)) {
                return buffType.cast(buff);
            }
        }
        return null;
    }

    private void removeBuffType(Class<? extends Buff> buffType) {
        activeBuffs.removeIf(buffType::isInstance);
    }

    /** Offensive-only marker for Breastplate's sourced 12-second duration. */
    private static final class BreastplateBuff extends SimpleBuff {
        private BreastplateBuff(double duration, double currentTime) {
            super("Noelle Breastplate", duration, currentTime, stats -> {
                // Shield durability and defensive effects are outside this slice.
            });
        }
    }

    /** Timed Sweeping Time conversion captured from DEF at Burst activation. */
    private static final class SweepingTimeBuff extends SimpleBuff {
        private SweepingTimeBuff(
                double duration,
                double currentTime,
                double capturedAtk) {
            super(
                    "Noelle Sweeping Time",
                    duration,
                    currentTime,
                    stats -> stats.add(StatType.ATK_FLAT, capturedAtk));
        }
    }
}
