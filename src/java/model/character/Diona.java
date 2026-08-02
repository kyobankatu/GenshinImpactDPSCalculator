package model.character;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.CharacterTeamBuffProvider;
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
 * Diona's offensive kit for stationary single-target combat.
 *
 * <p>The typed Skill request represents Hold Icy Paws: five independently
 * resolved paws and their deterministic expected particle total. Signature Mix
 * includes its initial hit, six snapshotted field ticks, C1 Energy, C4 aimed
 * shot timing, and the always-full-HP branch of C6.</p>
 *
 * <p>Tap Skill selection, shields, healing, stamina/movement, enemy ATK
 * reduction, projectile geometry, and pending-event snapshot reconstruction
 * are outside this slice.</p>
 */
public final class Diona extends Character
        implements CharacterTeamBuffProvider,
        SimulatorInitializedCharacterEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double SKILL_COOLDOWN = 15.0;
    private static final double BURST_COOLDOWN = 20.0;
    private static final double BURST_FIELD_DURATION = 12.5;
    private static final int BURST_START_FRAME = 58;
    private static final int BURST_TICK_INTERVAL_FRAMES = 120;
    private static final int BURST_TICK_COUNT = 6;

    private final Buff c6TeamBuff;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;

    /** Constructs repository-default C6 Diona. */
    public Diona(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Diona at an explicit constellation. */
    public Diona(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Diona with injectable talent data and constellation state. */
    public Diona(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Diona constellation must be between 0 and 6");
        }
        name = "Diona";
        characterId = CharacterId.DIONA;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9570.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 212.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 601.0));
        baseStats.add(StatType.CRYO_DMG_BONUS,
                getTalentValue("Ascension Cryo DMG", 0.24));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
        c6TeamBuff = new Buff("Diona C6 Cat's Tail Closing Time") {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                if (Diona.this.constellation >= 6
                        && isBurstFieldActive(currentTime)) {
                    stats.add(StatType.ELEMENTAL_MASTERY, 200.0);
                }
            }
        }.sourcedBy(characterId);
    }

    /** Binds Diona's mutable field state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (sim == null) {
            throw new IllegalArgumentException("Diona simulator is required");
        }
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Diona cannot be reused across CombatSimulator instances");
        }
        initializedSimulator = sim;
    }

    /** Returns Diona's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Diona has no unconditional offensive self passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // All represented bonuses are action- or field-specific.
    }

    /** Returns whether Signature Mix's field is active. */
    public boolean isBurstFieldActive(double currentTime) {
        for (Buff buff : getActiveBuffs()) {
            if (buff instanceof DionaBurstFieldMarker
                    && currentTime >= buff.getStartTime()
                    && currentTime < buff.getExpirationTime()) {
                return true;
            }
        }
        return false;
    }

    /** Returns C6's dynamic team EM provider at C6. */
    @Override
    public List<Buff> getTeamBuffs() {
        if (constellation < 6) {
            return Collections.emptyList();
        }
        return Collections.singletonList(c6TeamBuff);
    }

    /** Dispatches Diona's supported typed offensive actions. */
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
                holdIcyPaws(sim);
                break;
            case BURST:
                signatureMix(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Diona: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double[] multipliers = { 0.6636, 0.6162, 0.8374, 0.7900, 0.9875 };
        int[] durationFrames = { 30, 21, 44, 21, 73 };
        int step = normalAttackStep;
        AttackAction normal = attack(
                "Katzlein Style N" + (step + 1),
                getTalentValue("N" + (step + 1), multipliers[step]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.None,
                0.0,
                durationFrames[step] * FRAME);
        sim.performAction(characterId, normal);
        normalAttackStep = (normalAttackStep + 1) % multipliers.length;
    }

    private void fullyChargedAimedShot(CombatSimulator sim) {
        int durationFrames = constellation >= 4
                && isBurstFieldActive(sim.getCurrentTime()) ? 58 : 94;
        AttackAction charged = attack(
                "Katzlein Style Fully Charged Aimed Shot",
                getTalentValue("Fully Charged Aimed Shot", 2.1080),
                Element.CRYO,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.ChargedAttack,
                1.0,
                durationFrames * FRAME);
        sim.performAction(characterId, charged);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction plunge = attack(
                "Katzlein Style High Plunge",
                getTalentValue("Plunge High", 2.6086),
                Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.Standard,
                ICDTag.None,
                0.0,
                1.0);
        sim.performAction(characterId, plunge);
    }

    private void holdIcyPaws(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        markSkillUsed(castTime, sim.getApplicableBuffs(this));
        for (int paw = 0; paw < 5; paw++) {
            AttackAction action = attack(
                    "Icy Paw " + (paw + 1),
                    getTalentValue("Icy Paw", 0.8384),
                    Element.CRYO,
                    StatType.SKILL_DMG_BONUS,
                    ActionType.SKILL,
                    ICDType.Standard,
                    ICDTag.ElementalSkill,
                    1.0,
                    0.0);
            if (constellation >= 2) {
                action.addBonusStat(StatType.SKILL_DMG_BONUS, 0.15);
            }
            int pawIndex = paw;
            double impactTime = castTime + (34.0 + pawIndex) * FRAME;
            schedule(sim, impactTime, activeSim -> {
                activeSim.performActionWithoutTimeAdvance(characterId, action);
                if (pawIndex == 0) {
                    schedule(activeSim,
                            activeSim.getCurrentTime() + PARTICLE_TRAVEL,
                            particleSim -> particleSim.getEnergyDistributor()
                                    .distributeParticles(
                                            Element.CRYO,
                                            getTalentValue(
                                                    "Hold Skill Particles", 4.0),
                                            ParticleType.PARTICLE));
                }
            });
        }
        sim.advanceTime(49.0 * FRAME);
    }

    private void signatureMix(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        markBurstUsed(castTime, sim.getApplicableBuffs(this));
        captureSnapshot(castTime, sim.getApplicableBuffs(this));
        StatsContainer burstSnapshot = getSnapshot().merge(null);
        double burstFieldStart = castTime + BURST_START_FRAME * FRAME;
        double burstFieldEnd = burstFieldStart + BURST_FIELD_DURATION;
        getActiveBuffs().removeIf(
                buff -> buff instanceof DionaBurstFieldMarker);
        addBuff(new DionaBurstFieldMarker(
                burstFieldStart,
                BURST_FIELD_DURATION));

        AttackAction initial = attack(
                "Signature Mix Initial",
                getTalentValue("Signature Mix Initial", 1.6000),
                Element.CRYO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0,
                0.0);
        schedule(sim, burstFieldStart,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, initial));

        for (int tick = 1; tick <= BURST_TICK_COUNT; tick++) {
            AttackAction dot = attack(
                    "Signature Mix Tick " + tick,
                    getTalentValue("Signature Mix Tick", 1.0528),
                    Element.CRYO,
                    StatType.BURST_DMG_BONUS,
                    ActionType.BURST,
                    ICDType.Standard,
                    ICDTag.ElementalBurst,
                    1.0,
                    0.0);
            dot.setStatSnapshot(burstSnapshot);
            double tickTime = burstFieldStart
                    + tick * BURST_TICK_INTERVAL_FRAMES * FRAME;
            schedule(sim, tickTime,
                    activeSim -> activeSim.performActionWithoutTimeAdvance(
                            characterId, dot));
        }
        if (constellation >= 1) {
            schedule(sim, burstFieldEnd,
                    activeSim -> receiveFlatEnergy(15.0));
        }
        sim.advanceTime(64.0 * FRAME);
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
            double duration) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                duration,
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

    /** Snapshot-restorable marker for Signature Mix's field window. */
    private static final class DionaBurstFieldMarker extends Buff {
        private DionaBurstFieldMarker(
                double startTime,
                double duration) {
            super("Diona Signature Mix Field Marker", duration, startTime);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            // Team effects are projected by CharacterTeamBuffProvider.
        }
    }
}
