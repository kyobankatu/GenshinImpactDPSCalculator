package model.character;

import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.FormStateProvider;
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
import simulation.action.HitlagProfile;
import simulation.event.SimpleTimerEvent;

/**
 * Razor offensive implementation for stationary single-target combat.
 *
 * <p>
 * Steel Fang uses talent-9 values. Claw and Thunder Press models its hitmark,
 * cooldown start, particle count, and Electro Sigils.
 * Lightning Fang consumes Sigils, starts at its sourced hitmark, grants attack
 * speed, and emits one Burst-classified Electro echo with every Normal Attack.
 * Awakening, Hunger, C1, C3, C5, and the original C6 are represented.
 *
 * <p>
 * Claw and Thunder Hold is retained as sourced static data but is not exposed:
 * the shared typed action request has no Press/Hold discriminator. Charged-
 * attack stamina, player resistance and self-aura effects, C2 target-HP checks,
 * C4 enemy DEF reduction, Witch's Homework and Hexerei effects, area geometry,
 * hitlag extension, and switch-out Energy return are intentionally excluded.
 * Pending cast events and character-local listener state are not reconstructed
 * by global simulator snapshots.
 */
public class Razor extends Character implements
        FormStateProvider,
        SimulatorInitializedCharacterEffect,
        SwitchAwareCharacter {
    private static final double PRESS_SKILL_COOLDOWN = 6.0;
    private static final double AWAKENING_COOLDOWN_MULTIPLIER = 0.82;
    private static final double BURST_COOLDOWN = 20.0;
    private static final double BURST_DURATION = 15.0;
    private static final double SIGIL_DURATION = 18.0;
    private static final double SIGIL_ENERGY_RECHARGE = 0.20;
    private static final double SIGIL_ENERGY = 5.0;
    private static final int MAX_SIGILS = 3;
    private static final double HUNGER_ENERGY_RECHARGE = 0.30;
    private static final double C1_DAMAGE_BONUS = 0.10;
    private static final double C1_DURATION = 8.0;
    private static final double C6_COOLDOWN = 10.0;
    private static final double C6_DAMAGE_MULTIPLIER = 1.0;

    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[] NORMAL_HITLAG = {
        new HitlagProfile(0.10, 0.01, true, false, false),
        new HitlagProfile(0.10, 0.01, true, false, false),
        new HitlagProfile(0.10, 0.05, true, false, false),
        new HitlagProfile(0.15, 0.01, true, false, false)
    };
    private static final HitlagProfile PRESS_SKILL_HITLAG =
            new HitlagProfile(0.10, 0.03, true, false, false);

    private int normalAttackStep;
    private CombatSimulator initializedSimulator;

    /**
     * Constructs Razor with the shared talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     */
    public Razor(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    /**
     * Constructs Razor with an explicit talent source for deterministic tests.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     */
    public Razor(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData) {
        super(talentData);
        this.name = "Razor";
        this.characterId = CharacterId.RAZOR;
        this.element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = (int) getTalentValue("Constellation", 6.0);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Razor constellation must be between 0 and 6");
        }

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11962.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 234.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 751.0));
        baseStats.add(StatType.PHYSICAL_DMG_BONUS,
                getTalentValue("Ascension Physical DMG", 0.30));

        setSkillCD(effectiveSkillCooldown(PRESS_SKILL_COOLDOWN));
        setBurstCD(BURST_COOLDOWN);
    }

    /**
     * Registers Razor's active-field particle listener for C1.
     *
     * @param sim simulator receiving Razor
     * @throws IllegalStateException if this instance is reused across simulators
     */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Razor cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;
        sim.addParticleListener((element, count, time) -> {
            if (constellation >= 1
                    && count > 0.0
                    && sim.getActiveCharacter() == Razor.this) {
                replaceC1Buff(time);
            }
        });
    }

    /**
     * Returns Razor's 80-Energy Burst cost.
     *
     * @return current Burst cost
     */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /**
     * Reports whether the Wolf Within remains active.
     *
     * @param currentTime current simulation time in seconds
     * @return {@code true} while Lightning Fang's timed buff is active
     */
    @Override
    public boolean isFormActive(double currentTime) {
        return findActiveBuff(LightningFangBuff.class, currentTime) != null;
    }

    /**
     * Ends Lightning Fang immediately when Razor leaves the battlefield.
     *
     * @param sim active simulator whose party still has Razor active
     */
    @Override
    public void onSwitchOut(CombatSimulator sim) {
        removeBuffType(LightningFangBuff.class);
        normalAttackStep = 0;
    }

    /**
     * Applies Hunger's dynamic low-Energy bonus.
     *
     * @param stats assembled stats container
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (getCurrentEnergy() < getEnergyCost() * 0.50) {
            stats.add(
                    StatType.ENERGY_RECHARGE,
                    getTalentValue(
                            "Hunger Energy Recharge",
                            HUNGER_ENERGY_RECHARGE));
        }
    }

    /**
     * Returns the number of unexpired Electro Sigils.
     *
     * @param currentTime current simulation time in seconds
     * @return stack count in the inclusive range 0-3
     */
    public int getElectroSigilCount(double currentTime) {
        ElectroSigilBuff sigils = findActiveBuff(
                ElectroSigilBuff.class, currentTime);
        return sigils == null ? 0 : sigils.getStacks();
    }

    /**
     * Dispatches Razor's typed offensive actions. The canonical Skill request
     * selects Press; Hold remains inaccessible until the shared request type has
     * a typed Skill variant.
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
                clawAndThunderPress(sim);
                break;
            case BURST:
                lightningFang(sim);
                break;
            case PLUNGE:
                highPlunge(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Razor: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        String key = "N" + (normalAttackStep + 1);
        double[] defaults = { 1.6132, 1.38972, 1.73752, 2.28808 };
        double[] durations = {
                54.0 / 60.0,
                43.0 / 60.0,
                57.0 / 60.0,
                129.0 / 60.0
        };
        double multiplier = getTalentValue(
                key, defaults[normalAttackStep]);
        double duration = durations[normalAttackStep];
        double resolvedDuration = resolveNormalDuration(sim, duration);

        AttackAction normal = new AttackAction(
                "Steel Fang " + key,
                multiplier,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        normal.setHitlagProfile(NORMAL_HITLAG[normalAttackStep]);
        normal.setICD(ICDType.Standard, ICDTag.NormalAttack, 0.0);
        normal.setShatterTrigger(true);
        normal.setAnimationDuration(duration);
        // Resolve the physical hit and its same-hit follow-ups before advancing.
        sim.performActionWithoutTimeAdvance(characterId, normal);

        triggerC6(sim);
        if (isFormActive(sim.getCurrentTime())) {
            triggerWolfEcho(sim, multiplier);
        }
        sim.advanceTime(resolvedDuration);

        normalAttackStep++;
        if (normalAttackStep >= defaults.length) {
            normalAttackStep = 0;
        }
    }

    private void chargedSpin(CombatSimulator sim) {
        if (isFormActive(sim.getCurrentTime())) {
            throw new IllegalStateException(
                    "Lightning Fang disables Razor's Charged Attack");
        }
        AttackAction charged = new AttackAction(
                "Steel Fang Charged Spin",
                getTalentValue("Charged Spin", 1.1490),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                23.0 / 60.0,
                ActionType.CHARGE);
        charged.setICD(ICDType.Standard, ICDTag.ChargedAttack, 0.0);
        charged.setShatterTrigger(true);
        sim.performAction(characterId, charged);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction plunge = new AttackAction(
                "Steel Fang High Plunge",
                getTalentValue("Plunge High", 3.764769),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                58.0 / 60.0,
                ActionType.PLUNGE);
        plunge.setICD(ICDType.None, ICDTag.None, 0.0);
        plunge.setShatterTrigger(true);
        sim.performAction(characterId, plunge);
    }

    private void clawAndThunderPress(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        boolean burstActive = isFormActive(castTime);
        double cooldownStart = (burstActive ? 31.0 : 30.0) / 60.0;
        double hitmark = (burstActive ? 33.0 : 32.0) / 60.0;
        double duration = (burstActive ? 85.0 : 80.0) / 60.0;

        schedule(sim, castTime + cooldownStart, activeSim ->
                markSkillWithBaseCooldown(
                        activeSim,
                        PRESS_SKILL_COOLDOWN));
        schedule(sim, castTime + hitmark, activeSim -> {
            AttackAction press = new AttackAction(
                    "Claw and Thunder Press",
                    getTalentValue(
                            "Claw and Thunder Press",
                            constellation >= 5 ? 3.9840 : 3.3864),
                    Element.ELECTRO,
                    StatType.BASE_ATK,
                    StatType.SKILL_DMG_BONUS,
                    0.0,
                    ActionType.SKILL);
            press.setHitlagProfile(PRESS_SKILL_HITLAG);
            press.setICD(ICDType.None, ICDTag.ElementalSkill, 2.0);
            activeSim.performActionWithoutTimeAdvance(characterId, press);
            addElectroSigil(activeSim.getCurrentTime(), 1);
            if (!isFormActive(activeSim.getCurrentTime())) {
                activeSim.getEnergyDistributor().distributeParticles(
                        Element.ELECTRO, 3.0, ParticleType.PARTICLE);
            }
        });
        sim.advanceTime(duration);
    }

    private void lightningFang(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();

        schedule(sim, castTime + 6.0 / 60.0, activeSim -> {
            markBurstUsed(
                    activeSim.getCurrentTime(),
                    activeSim.getApplicableBuffs(this));
            reduceBurstCooldown(activeSim.getCurrentTime(), 4.0 / 60.0);
        });
        schedule(sim, castTime + 7.0 / 60.0, activeSim ->
                consumeElectroSigils(activeSim.getCurrentTime()));
        schedule(sim, castTime + 32.0 / 60.0, activeSim -> {
            resetSkillCooldown(activeSim.getCurrentTime());
            removeBuffType(LightningFangBuff.class);
            addBuff(new LightningFangBuff(
                    getTalentValue(
                            "Lightning Fang Duration",
                            BURST_DURATION),
                    activeSim.getCurrentTime(),
                    getTalentValue(
                            "Lightning Fang Attack Speed",
                            constellation >= 3 ? 0.40 : 0.39)));

            AttackAction burst = new AttackAction(
                    "Lightning Fang",
                    getTalentValue(
                            "Lightning Fang",
                            constellation >= 3 ? 3.20 : 2.72),
                    Element.ELECTRO,
                    StatType.BASE_ATK,
                    StatType.BURST_DMG_BONUS,
                    0.0,
                    ActionType.BURST);
            burst.setICD(ICDType.None, ICDTag.ElementalBurst, 2.0);
            burst.setShatterTrigger(true);
            activeSim.performActionWithoutTimeAdvance(characterId, burst);
        });
        sim.advanceTime(73.0 / 60.0);
    }

    private void triggerWolfEcho(
            CombatSimulator sim,
            double normalMultiplier) {
        double ratio = getTalentValue(
                "Lightning Fang Echo",
                constellation >= 3 ? 0.48 : 0.408);
        AttackAction echo = new AttackAction(
                "The Wolf Within Echo",
                normalMultiplier * ratio,
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                ActionType.BURST);
        echo.setICD(
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0);
        sim.performActionWithoutTimeAdvance(characterId, echo);
    }

    private void triggerC6(CombatSimulator sim) {
        double currentTime = sim.getCurrentTime();
        if (constellation < 6
                || findActiveBuff(C6CooldownBuff.class, currentTime) != null) {
            return;
        }

        addBuff(new C6CooldownBuff(
                getTalentValue("C6 Cooldown", C6_COOLDOWN),
                currentTime));
        AttackAction lightning = new AttackAction(
                "Lupus Fulguris",
                getTalentValue(
                        "C6 Lightning",
                        C6_DAMAGE_MULTIPLIER),
                Element.ELECTRO,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.OTHER);
        lightning.setICD(ICDType.None, ICDTag.None, 1.0);
        sim.performActionWithoutTimeAdvance(characterId, lightning);

        if (!isFormActive(currentTime)) {
            addElectroSigil(currentTime, 1);
        }
    }

    private void addElectroSigil(double currentTime, int amount) {
        int stacks = Math.min(
                MAX_SIGILS,
                getElectroSigilCount(currentTime) + amount);
        removeBuffType(ElectroSigilBuff.class);
        addBuff(new ElectroSigilBuff(
                getTalentValue("Electro Sigil Duration", SIGIL_DURATION),
                currentTime,
                stacks,
                getTalentValue(
                        "Electro Sigil Energy Recharge",
                        SIGIL_ENERGY_RECHARGE)));
    }

    private int consumeElectroSigils(double currentTime) {
        int stacks = getElectroSigilCount(currentTime);
        removeBuffType(ElectroSigilBuff.class);
        if (stacks > 0) {
            receiveFlatEnergy(stacks * getTalentValue(
                    "Electro Sigil Energy", SIGIL_ENERGY));
        }
        return stacks;
    }

    private void replaceC1Buff(double currentTime) {
        removeBuffType(C1ParticleBuff.class);
        addBuff(new C1ParticleBuff(
                getTalentValue("C1 Duration", C1_DURATION),
                currentTime,
                getTalentValue("C1 Damage Bonus", C1_DAMAGE_BONUS)));
    }

    private void markSkillWithBaseCooldown(
            CombatSimulator sim,
            double baseCooldown) {
        double pressCooldown = effectiveSkillCooldown(
                PRESS_SKILL_COOLDOWN);
        setSkillCD(effectiveSkillCooldown(baseCooldown));
        markSkillUsed(
                sim.getCurrentTime(),
                sim.getApplicableBuffs(this));
        setSkillCD(pressCooldown);
    }

    private double effectiveSkillCooldown(double baseCooldown) {
        return baseCooldown * AWAKENING_COOLDOWN_MULTIPLIER;
    }

    private double resolveNormalDuration(
            CombatSimulator sim,
            double duration) {
        double currentTime = sim.getCurrentTime();
        StatsContainer stats = getEffectiveStats(currentTime);
        List<Buff> buffs = sim.getApplicableBuffs(this);
        for (Buff buff : buffs) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        double speed = Math.min(
                0.60,
                stats.get(StatType.ATK_SPD)
                        + stats.get(StatType.NORMAL_ATTACK_SPD));
        if (speed <= 0.0) {
            return duration;
        }
        return duration / (1.0 + speed);
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

    /** Razor's refreshable 18-second Electro Sigil state. */
    private static final class ElectroSigilBuff extends SimpleBuff {
        private final int stacks;

        private ElectroSigilBuff(
                double duration,
                double currentTime,
                int stacks,
                double rechargePerStack) {
            super(
                    "Razor Electro Sigils",
                    duration,
                    currentTime,
                    stats -> stats.add(
                            StatType.ENERGY_RECHARGE,
                            stacks * rechargePerStack));
            this.stacks = stacks;
        }

        private int getStacks() {
            return stacks;
        }
    }

    /** Lightning Fang's timed form and Normal Attack speed bonus. */
    private static final class LightningFangBuff extends SimpleBuff {
        private LightningFangBuff(
                double duration,
                double currentTime,
                double attackSpeed) {
            super(
                    "Razor Lightning Fang",
                    duration,
                    currentTime,
                    stats -> stats.add(StatType.ATK_SPD, attackSpeed));
        }
    }

    /** C1's refreshable active-field particle pickup damage window. */
    private static final class C1ParticleBuff extends SimpleBuff {
        private C1ParticleBuff(
                double duration,
                double currentTime,
                double damageBonus) {
            super(
                    "Razor Wolf's Instinct",
                    duration,
                    currentTime,
                    stats -> stats.add(
                            StatType.DMG_BONUS_ALL,
                            damageBonus));
        }
    }

    /** C6's owner-local ten-second readiness marker. */
    private static final class C6CooldownBuff extends SimpleBuff {
        private C6CooldownBuff(double duration, double currentTime) {
            super(
                    "Razor Lupus Fulguris Cooldown",
                    duration,
                    currentTime,
                    stats -> {
                    });
        }
    }
}
