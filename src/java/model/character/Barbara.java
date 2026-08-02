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
import simulation.event.SimpleTimerEvent;

/**
 * Barbara offensive character implementation for stationary single-target combat.
 *
 * <p>
 * Whisper of Water uses talent-9 multipliers and KQM action intervals. Let the
 * Show Begin schedules its two talent-12 droplet hits at 0.5 s and 1.0 s,
 * starts the 15 s Melody Loop, and generates no particles. Shining Miracle is
 * represented as a zero-damage 80-Energy Burst cast. C1, C2, C4, C5, and the
 * offensively relevant C2 extension through Encore are represented.
 *
 * <p>
 * Healing, stamina consumption, player/self Wet, incoming damage, C6 revival,
 * and proximity-dependent Melody Loop contact applications are outside the
 * current simulator state and targeting hooks.
 */
public class Barbara extends Character implements
        FormStateProvider,
        SimulatorInitializedCharacterEffect {
    private static final double BASE_SKILL_COOLDOWN = 32.0;
    private static final double BASE_MELODY_LOOP_DURATION = 15.0;
    private static final double MAX_MELODY_LOOP_EXTENSION = 5.0;
    private static final double C2_SKILL_COOLDOWN_REDUCTION = 0.15;
    private static final double C2_HYDRO_DMG_BONUS = 0.15;
    private static final double C1_ENERGY_INTERVAL = 10.0;
    private static final double C1_ENERGY_AMOUNT = 1.0;
    private static final double C4_CHARGED_ENERGY = 1.0;

    private int normalAttackStep;
    private CombatSimulator initializedSimulator;

    /**
     * Constructs Barbara with the shared talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     */
    public Barbara(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    /**
     * Constructs Barbara with an explicit talent source for deterministic tests.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     */
    public Barbara(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData) {
        super(talentData);
        this.name = "Barbara";
        this.characterId = CharacterId.BARBARA;
        this.element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = (int) getTalentValue("Constellation", 6.0);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Barbara constellation must be between 0 and 6");
        }

        baseStats.set(StatType.BASE_HP, getTalentValue("Base HP", 9787.0));
        baseStats.set(StatType.BASE_ATK, getTalentValue("Base ATK", 159.0));
        baseStats.set(StatType.BASE_DEF, getTalentValue("Base DEF", 669.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP", 0.24));

        double cooldownReduction = constellation >= 2
                ? getTalentValue(
                        "C2 Skill CD Reduction",
                        C2_SKILL_COOLDOWN_REDUCTION)
                : 0.0;
        setSkillCD(BASE_SKILL_COOLDOWN * (1.0 - cooldownReduction));
        setBurstCD(20.0);
    }

    /**
     * Registers Barbara's particle, charged-hit, and periodic C1 listeners.
     *
     * @param sim simulator receiving Barbara
     * @throws IllegalStateException if this instance is reused across simulators
     */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Barbara cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;

        sim.addParticleListener((element, count, time) ->
                extendMelodyLoop(sim, count, time));
        if (constellation >= 4) {
            sim.addDamageListener((actor, action, damage, time) -> {
                if (actor == this
                        && action.getActionType() == ActionType.CHARGE) {
                    receiveFlatEnergy(getTalentValue(
                            "C4 Charged Energy",
                            C4_CHARGED_ENERGY));
                }
            });
        }
        if (constellation >= 1) {
            double interval = getTalentValue(
                    "C1 Energy Interval",
                    C1_ENERGY_INTERVAL);
            sim.registerEvent(new SimpleTimerEvent(
                    sim.getCurrentTime() + interval,
                    interval) {
                @Override
                public void onTick(CombatSimulator activeSim) {
                    receiveFlatEnergy(getTalentValue(
                            "C1 Energy",
                            C1_ENERGY_AMOUNT));
                }
            });
        }
    }

    /**
     * Returns Barbara's 80-Energy Burst cost.
     *
     * @return current Burst cost
     */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /**
     * Reports whether the Melody Loop field state remains active.
     *
     * @param currentTime current simulation time in seconds
     * @return {@code true} before the current Melody Loop expires
     */
    @Override
    public boolean isFormActive(double currentTime) {
        return findMelodyLoop(initializedSimulator, currentTime) != null;
    }

    /**
     * Barbara has no unconditional static offensive passive in this slice.
     *
     * @param stats assembled stats container
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Conditional offensive effects are attached to Melody Loop and hits.
    }

    /**
     * Dispatches Barbara's typed player actions.
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
                chargedAttack(sim);
                break;
            case SKILL:
                markSkillUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                letTheShowBegin(sim);
                break;
            case BURST:
                markBurstUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                shiningMiracle(sim);
                break;
            case DASH:
                sim.advanceTime(23.0 / 60.0);
                break;
            case PLUNGE:
                highPlunge(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Barbara: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        String key = "N" + (normalAttackStep + 1);
        double[] defaults = { 0.64328, 0.60384, 0.69768, 0.93840 };
        double[] durations = {
                15.0 / 60.0,
                21.0 / 60.0,
                22.0 / 60.0,
                60.0 / 60.0
        };
        AttackAction normal = new AttackAction(
                "Whisper of Water " + key,
                getTalentValue(key, defaults[normalAttackStep]),
                Element.HYDRO,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                durations[normalAttackStep],
                ActionType.NORMAL);
        normal.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
        sim.performAction(characterId, normal);

        normalAttackStep++;
        if (normalAttackStep >= defaults.length) {
            normalAttackStep = 0;
        }
    }

    private void chargedAttack(CombatSimulator sim) {
        AttackAction charged = new AttackAction(
                "Whisper of Water Charged Attack",
                getTalentValue("Charged Attack", 2.82608),
                Element.HYDRO,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                89.0 / 60.0,
                ActionType.CHARGE);
        // The 89-frame action interval always exceeds Barbara's sourced 0.5 s ICD.
        charged.setICD(ICDType.None, ICDTag.ChargedAttack, 1.0);
        sim.performAction(characterId, charged);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction plunge = new AttackAction(
                "Whisper of Water High Plunge",
                getTalentValue("Plunge High", 2.607632),
                Element.HYDRO,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                1.0,
                ActionType.PLUNGE);
        plunge.setICD(ICDType.Standard, ICDTag.PlungeAttack, 1.0);
        sim.performAction(characterId, plunge);
    }

    /**
     * Starts Melody Loop and schedules the two sourced droplet hits.
     *
     * <p>
     * The two hits share standard Skill ICD, so the second hit at 1.0 s deals
     * damage but cannot reapply Hydro after the first hit at 0.5 s.
     *
     * @param sim active simulator
     */
    private void letTheShowBegin(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        startMelodyLoop(sim, castTime);

        double defaultMultiplier = constellation >= 5 ? 1.1680 : 0.9928;
        AttackAction droplet = new AttackAction(
                "Let the Show Begin Droplet",
                getTalentValue("Skill Droplet", defaultMultiplier),
                Element.HYDRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        droplet.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0);

        sim.registerEvent(new SimpleTimerEvent(castTime + 0.5, 0.5) {
            private int remainingHits = 2;

            @Override
            public void onTick(CombatSimulator activeSim) {
                activeSim.performActionWithoutTimeAdvance(
                        Barbara.this.characterId,
                        droplet);
                remainingHits--;
                if (remainingHits == 0) {
                    finish();
                }
            }
        });

        AttackAction cast = new AttackAction(
                "Let the Show Begin Cast",
                0.0,
                Element.HYDRO,
                StatType.BASE_ATK,
                null,
                54.0 / 60.0,
                ActionType.SKILL);
        cast.setICD(ICDType.None, ICDTag.ElementalSkill, 0.0);
        sim.performAction(characterId, cast);
    }

    private void shiningMiracle(CombatSimulator sim) {
        AttackAction cast = new AttackAction(
                "Shining Miracle Cast",
                getTalentValue("Shining Miracle", 0.0),
                Element.HYDRO,
                StatType.BASE_ATK,
                null,
                141.0 / 60.0,
                ActionType.BURST);
        cast.setICD(ICDType.None, ICDTag.ElementalBurst, 0.0);
        sim.performAction(characterId, cast);
    }

    private void startMelodyLoop(CombatSimulator sim, double currentTime) {
        sim.getFieldBuffList().removeIf(buff ->
                buff instanceof MelodyLoopBuff
                        && buff.getSourceCharacterId() == characterId);
        double duration = getTalentValue(
                "Skill Duration",
                BASE_MELODY_LOOP_DURATION);
        double maximumExtension = getTalentValue(
                "Encore Max Extension",
                MAX_MELODY_LOOP_EXTENSION);
        double hydroBonus = constellation >= 2
                ? getTalentValue(
                        "C2 Hydro DMG Bonus",
                        C2_HYDRO_DMG_BONUS)
                : 0.0;
        sim.applyFieldBuff(new MelodyLoopBuff(
                duration,
                maximumExtension,
                hydroBonus,
                currentTime).sourcedBy(characterId));
    }

    private void extendMelodyLoop(
            CombatSimulator sim,
            double particleCount,
            double currentTime) {
        if (particleCount <= 0.0) {
            return;
        }
        MelodyLoopBuff melodyLoop = findMelodyLoop(sim, currentTime);
        if (melodyLoop != null) {
            melodyLoop.extend(particleCount);
        }
    }

    private MelodyLoopBuff findMelodyLoop(
            CombatSimulator sim,
            double currentTime) {
        if (sim == null) {
            return null;
        }
        for (Buff buff : sim.getFieldBuffList()) {
            if (buff instanceof MelodyLoopBuff
                    && buff.getSourceCharacterId() == characterId
                    && !buff.isExpired(currentTime)) {
                return (MelodyLoopBuff) buff;
            }
        }
        return null;
    }

    /**
     * Field-only Melody Loop state carrying Barbara's C2 Hydro bonus.
     */
    private static final class MelodyLoopBuff extends SimpleBuff {
        private final double maximumExpirationTime;

        private MelodyLoopBuff(
                double duration,
                double maximumExtension,
                double hydroBonus,
                double currentTime) {
            super(
                    "Barbara Melody Loop",
                    duration,
                    currentTime,
                    stats -> stats.add(StatType.HYDRO_DMG_BONUS, hydroBonus));
            this.maximumExpirationTime = currentTime + duration + maximumExtension;
        }

        private void extend(double duration) {
            if (Double.isFinite(duration) && duration > 0.0) {
                expirationTime = Math.min(
                        maximumExpirationTime,
                        expirationTime + duration);
            }
        }
    }
}
