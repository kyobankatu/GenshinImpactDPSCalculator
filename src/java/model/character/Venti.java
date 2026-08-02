package model.character;

import java.util.EnumMap;
import java.util.Map;
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
import simulation.event.SimpleTimerEvent;
import simulation.event.TimerEvent;

/**
 * Venti's old-base-kit offensive mechanics for one stationary target.
 *
 * <p>
 * This slice models the six-step bow string, a fully charged Anemo aimed shot,
 * press Skill, twenty snapshotted Burst ticks, one prioritized elemental
 * absorption with fifteen independent ticks, Stormeye Energy, and the
 * representable C2-C6 effects. Suction, airborne state, hold Skill and its
 * upcurrent, C1 arrow geometry, defeat Energy, weak points, hitlag, and Hexerei
 * mechanics are intentionally outside the fixed single-target policy.
 */
public class Venti extends Character
        implements SimulatorInitializedCharacterEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double SKILL_COOLDOWN = 6.0;
    private static final double BURST_COOLDOWN = 15.0;
    private static final double BURST_TICK_INTERVAL = 24.0 * FRAME;
    private static final double PROJECTILE_TRAVEL = 10.0 * FRAME;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int BURST_TICK_COUNT = 20;
    private static final int ABSORBED_TICK_COUNT = 15;
    private static final double RESISTANCE_DURATION = 10.0;
    private static final Element[] ABSORPTION_PRIORITY = {
            Element.PYRO,
            Element.HYDRO,
            Element.ELECTRO,
            Element.CRYO
    };

    private final EnumMap<Element, Double> c6ExpirationByElement =
            new EnumMap<>(Element.class);
    private int normalAttackStep;
    private long burstGeneration;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private Element absorbedElement;
    private CombatSimulator initializedSimulator;

    /** Constructs the repository-default C6 Venti. */
    public Venti(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /**
     * Constructs Venti at an explicit constellation.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param constellation constellation in the inclusive range 0-6
     */
    public Venti(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Venti with injectable talent data and constellation state.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Venti(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Venti constellation must be between 0 and 6");
        }
        this.name = "Venti";
        this.characterId = CharacterId.VENTI;
        this.element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10531.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 263.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 669.0));
        baseStats.add(StatType.ENERGY_RECHARGE,
                getTalentValue("Ascension ER", 0.32));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds mutable listener and timer state to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Venti cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;
        sim.addParticleListener((element, count, time) -> {
            if (constellation >= 4
                    && count > 0.0
                    && sim.getActiveCharacter() == Venti.this) {
                replaceC4Buff(time);
            }
        });
    }

    /** Returns the 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Venti has no unconditional offensive stat passive in this slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // All modeled passives are event- or target-dependent.
    }

    /** Returns the element captured by the active Burst generation, if any. */
    public Element getAbsorbedElement() {
        return absorbedElement;
    }

    /** Returns whether the current Stormeye field remains active. */
    public boolean isBurstActive(double currentTime) {
        return currentTime < burstExpirationTime;
    }

    /** Dispatches Venti's supported typed offensive actions. */
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
                pressSkill(sim);
                break;
            case BURST:
                windGrandOde(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Venti: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        double[] multipliers = {
                0.3745, 0.8153, 0.9622, 0.4787, 0.9306, 1.3035
        };
        int[] hitCounts = { 2, 1, 1, 2, 1, 1 };
        int[][] releaseFrames = {
                { 17, 27 }, { 19 }, { 28 }, { 15, 28 }, { 17 }, { 49 }
        };
        double[] durations = {
                30.0 * FRAME,
                38.0 * FRAME,
                33.0 * FRAME,
                31.0 * FRAME,
                22.0 * FRAME,
                98.0 * FRAME
        };
        int step = normalAttackStep;
        String key = "N" + (step + 1);
        double multiplier = getTalentValue(key, multipliers[step]);
        for (int hit = 0; hit < hitCounts[step]; hit++) {
            int hitIndex = hit;
            AttackAction normal = new AttackAction(
                    "Divine Marksmanship " + key + " Hit " + (hitIndex + 1),
                    multiplier,
                    Element.PHYSICAL,
                    StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    0.0,
                    ActionType.NORMAL);
            normal.setICD(ICDType.Standard, ICDTag.NormalAttack, 0.0);
            schedule(
                    sim,
                    castTime + releaseFrames[step][hitIndex] * FRAME
                            + PROJECTILE_TRAVEL,
                    activeSim -> activeSim.performActionWithoutTimeAdvance(
                            characterId, normal));
        }
        normalAttackStep = (normalAttackStep + 1) % multipliers.length;
        sim.advanceTime(durations[step]);
    }

    private void fullyChargedAimedShot(CombatSimulator sim) {
        AttackAction charged = new AttackAction(
                "Divine Marksmanship Fully Charged Aimed Shot",
                getTalentValue("Fully Charged Aimed Shot", 2.1080),
                Element.ANEMO,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                94.0 * FRAME,
                ActionType.CHARGE);
        charged.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performAction(characterId, charged);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction plunge = new AttackAction(
                "Divine Marksmanship High Plunge",
                getTalentValue("Plunge High", 2.6076),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                1.0,
                ActionType.PLUNGE);
        plunge.setICD(ICDType.Standard, ICDTag.PlungeAttack, 0.0);
        sim.performAction(characterId, plunge);
    }

    private void pressSkill(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        schedule(sim, castTime + 21.0 * FRAME, activeSim ->
                markSkillUsed(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this)));
        schedule(sim, castTime + 51.0 * FRAME, this::resolvePressSkillHit);
        sim.advanceTime(98.0 * FRAME);
    }

    private void resolvePressSkillHit(CombatSimulator sim) {
        double defaultMultiplier = constellation >= 5 ? 5.52 : 4.692;
        String key = constellation >= 5 ? "Skill Press C5" : "Skill Press";
        AttackAction skill = new AttackAction(
                "Skyward Sonnet (Press)",
                getTalentValue(key, defaultMultiplier),
                Element.ANEMO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        skill.setICD(ICDType.None, ICDTag.ElementalSkill, 2.0);
        sim.performActionWithoutTimeAdvance(characterId, skill);
        if (sim.getEnemy() != null) {
            if (constellation >= 2) {
                applyC2ResistanceShred(sim, sim.getCurrentTime());
            }
            schedule(
                    sim,
                    sim.getCurrentTime() + PARTICLE_TRAVEL,
                    activeSim -> activeSim.getEnergyDistributor()
                            .distributeParticles(
                                    Element.ANEMO,
                                    3.0,
                                    ParticleType.PARTICLE));
        }
    }

    private void windGrandOde(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        long generation = ++burstGeneration;
        absorbedElement = null;
        burstExpirationTime = castTime + 574.0 * FRAME;

        schedule(sim, castTime + 81.0 * FRAME, activeSim -> {
            if (generation == burstGeneration) {
                markBurstUsed(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this));
            }
        });
        schedule(sim, castTime + 104.0 * FRAME, activeSim -> {
            if (generation == burstGeneration) {
                captureSnapshot(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this));
            }
        });
        scheduleBurstTicks(sim, castTime, generation);
        schedule(sim, burstExpirationTime, activeSim -> {
            if (generation == burstGeneration) {
                applyStormeyeEnergy(activeSim);
            }
        });
        sim.advanceTime(95.0 * FRAME);
    }

    private void scheduleBurstTicks(
            CombatSimulator sim,
            double castTime,
            long generation) {
        sim.registerEvent(new TimerEvent() {
            private int resolvedTicks;
            private boolean finished;

            @Override
            public void tick(CombatSimulator activeSim) {
                if (generation != burstGeneration
                        || activeSim != initializedSimulator) {
                    finished = true;
                    return;
                }

                Element captured = null;
                if (resolvedTicks == 3 && absorbedElement == null) {
                    captured = findAbsorbableAura(activeSim);
                }
                resolveBurstAnemoTick(activeSim, resolvedTicks);
                resolvedTicks++;

                if (captured != null) {
                    activateAbsorption(activeSim, generation, captured);
                } else if (resolvedTicks == 4 && absorbedElement == null) {
                    scheduleLateAbsorptionChecks(activeSim, generation);
                }
                if (resolvedTicks == BURST_TICK_COUNT) {
                    finished = true;
                }
            }

            @Override
            public boolean isFinished(double currentTime) {
                return finished;
            }

            @Override
            public double getNextTickTime() {
                return castTime
                        + (106.0 + 24.0 * resolvedTicks) * FRAME;
            }
        });
    }

    private void resolveBurstAnemoTick(
            CombatSimulator sim,
            int tickIndex) {
        double defaultMultiplier = constellation >= 3 ? 0.7520 : 0.6392;
        String key = constellation >= 3 ? "Burst DoT C3" : "Burst DoT";
        AttackAction tick = new AttackAction(
                "Wind's Grand Ode",
                getTalentValue(key, defaultMultiplier),
                Element.ANEMO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                true,
                ActionType.BURST);
        // Venti's dedicated 1-second group follows a fixed 1/0/0 sequence.
        // Encode the application sequence locally because shared Special ICD
        // support is intentionally outside this content-only phase.
        tick.setICD(
                ICDType.None,
                ICDTag.ElementalBurst,
                tickIndex % 3 == 0 ? 1.0 : 0.0);
        sim.performActionWithoutTimeAdvance(characterId, tick);
        if (constellation >= 6 && sim.getEnemy() != null) {
            refreshC6ResistanceShred(sim, Element.ANEMO);
        }
    }

    private void scheduleLateAbsorptionChecks(
            CombatSimulator sim,
            long generation) {
        double firstCheckTime = sim.getCurrentTime() + 18.0 * FRAME;
        sim.registerEvent(new TimerEvent() {
            private int resolvedChecks;
            private boolean finished;

            @Override
            public void tick(CombatSimulator activeSim) {
                if (generation != burstGeneration
                        || activeSim != initializedSimulator
                        || absorbedElement != null) {
                    finished = true;
                    return;
                }
                Element captured = findAbsorbableAura(activeSim);
                resolvedChecks++;
                if (captured != null) {
                    activateAbsorption(activeSim, generation, captured);
                    finished = true;
                } else if (resolvedChecks == 20) {
                    finished = true;
                }
            }

            @Override
            public boolean isFinished(double currentTime) {
                return finished;
            }

            @Override
            public double getNextTickTime() {
                return firstCheckTime + 18.0 * FRAME * resolvedChecks;
            }
        });
    }

    private Element findAbsorbableAura(CombatSimulator sim) {
        if (sim.getEnemy() == null) {
            return null;
        }
        for (Element candidate : ABSORPTION_PRIORITY) {
            if (sim.getEnemy().getAuraUnits(
                    candidate, sim.getCurrentTime()) > 0.0) {
                return candidate;
            }
        }
        return null;
    }

    private void activateAbsorption(
            CombatSimulator sim,
            long generation,
            Element element) {
        if (absorbedElement != null || generation != burstGeneration) {
            return;
        }
        absorbedElement = element;
        scheduleAbsorbedTicks(sim, generation, element);
    }

    private void scheduleAbsorbedTicks(
            CombatSimulator sim,
            long generation,
            Element element) {
        double firstTickTime = sim.getCurrentTime();
        sim.registerEvent(new TimerEvent() {
            private int resolvedTicks;
            private boolean finished;

            @Override
            public void tick(CombatSimulator activeSim) {
                if (generation != burstGeneration
                        || activeSim != initializedSimulator) {
                    finished = true;
                    return;
                }
                double defaultMultiplier = constellation >= 3
                        ? 0.3760 : 0.3196;
                String key = constellation >= 3
                        ? "Burst Absorbed C3" : "Burst Absorbed";
                AttackAction tick = new AttackAction(
                        "Wind's Grand Ode (Absorbed " + element.name() + ")",
                        getTalentValue(key, defaultMultiplier),
                        element,
                        StatType.BASE_ATK,
                        StatType.BURST_DMG_BONUS,
                        0.0,
                        true,
                        ActionType.BURST);
                // Absorbed damage has its own Venti 1/0/0 application sequence.
                tick.setICD(
                        ICDType.None,
                        ICDTag.None,
                        resolvedTicks % 3 == 0 ? 1.0 : 0.0);
                activeSim.performActionWithoutTimeAdvance(characterId, tick);
                if (constellation >= 6 && activeSim.getEnemy() != null) {
                    refreshC6ResistanceShred(activeSim, element);
                }
                resolvedTicks++;
                if (resolvedTicks == ABSORBED_TICK_COUNT) {
                    finished = true;
                }
            }

            @Override
            public boolean isFinished(double currentTime) {
                return finished;
            }

            @Override
            public double getNextTickTime() {
                return firstTickTime + BURST_TICK_INTERVAL * resolvedTicks;
            }
        });
    }

    private void applyStormeyeEnergy(CombatSimulator sim) {
        receiveFlatEnergy(getTalentValue("A4 Energy", 15.0));
        if (absorbedElement == null) {
            return;
        }
        for (Character member : sim.getPartyMembers()) {
            if (member != this && member.getElement() == absorbedElement) {
                member.receiveFlatEnergy(getTalentValue("A4 Energy", 15.0));
            }
        }
    }

    private void applyC2ResistanceShred(
            CombatSimulator sim,
            double currentTime) {
        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Venti C2 Breeze of Reminiscence",
                BuffId.VENTI_C2_RES_SHRED,
                RESISTANCE_DURATION,
                currentTime,
                stats -> {
                    stats.add(StatType.ANEMO_RES_SHRED, 0.12);
                    stats.add(StatType.PHYS_RES_SHRED, 0.12);
                }).sourcedBy(characterId));
    }

    private void replaceC4Buff(double currentTime) {
        getActiveBuffs().removeIf(buff -> buff instanceof VentiC4Buff);
        addBuff(new VentiC4Buff(currentTime));
    }

    private void refreshC6ResistanceShred(
            CombatSimulator sim,
            Element element) {
        double currentTime = sim.getCurrentTime();
        c6ExpirationByElement.entrySet().removeIf(
                entry -> entry.getValue() <= currentTime);
        c6ExpirationByElement.put(
                element, currentTime + RESISTANCE_DURATION);
        sim.applyTeamBuffNoStack(new VentiC6ResistanceBuff(
                currentTime,
                c6ExpirationByElement).sourcedBy(characterId));
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

    /** Owner-only C4 refresh marker and Anemo DMG Bonus. */
    private static final class VentiC4Buff extends Buff {
        private VentiC4Buff(double currentTime) {
            super("Venti C4 Hurricane of Freedom", 10.0, currentTime);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            stats.add(StatType.ANEMO_DMG_BONUS, 0.25);
        }
    }

    /** One typed C6 debuff carrying independently refreshed element windows. */
    private static final class VentiC6ResistanceBuff extends Buff {
        private final EnumMap<Element, Double> expirationByElement;

        private VentiC6ResistanceBuff(
                double currentTime,
                Map<Element, Double> expirationByElement) {
            super(
                    "Venti C6 Storm of Defiance",
                    BuffId.VENTI_C6_RES_SHRED,
                    maxExpiration(expirationByElement) - currentTime,
                    currentTime);
            this.expirationByElement = new EnumMap<>(expirationByElement);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            for (Map.Entry<Element, Double> entry
                    : expirationByElement.entrySet()) {
                if (currentTime < entry.getValue()) {
                    addResistanceShred(stats, entry.getKey(), 0.20);
                }
            }
        }

        private static double maxExpiration(
                Map<Element, Double> expirationByElement) {
            double maximum = Double.NEGATIVE_INFINITY;
            for (double expiration : expirationByElement.values()) {
                maximum = Math.max(maximum, expiration);
            }
            return maximum;
        }

        private static void addResistanceShred(
                StatsContainer stats,
                Element element,
                double amount) {
            switch (element) {
                case PYRO:
                    stats.add(StatType.PYRO_RES_SHRED, amount);
                    break;
                case HYDRO:
                    stats.add(StatType.HYDRO_RES_SHRED, amount);
                    break;
                case ELECTRO:
                    stats.add(StatType.ELECTRO_RES_SHRED, amount);
                    break;
                case CRYO:
                    stats.add(StatType.CRYO_RES_SHRED, amount);
                    break;
                case ANEMO:
                    stats.add(StatType.ANEMO_RES_SHRED, amount);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported Venti C6 element: " + element);
            }
        }
    }
}
