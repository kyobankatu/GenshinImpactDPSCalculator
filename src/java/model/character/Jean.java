package model.character;

import java.util.ArrayList;
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
 * Jean's offensive mechanics for one stationary target.
 *
 * <p>
 * This slice models the five-step Normal string, Charged and high Plunging
 * Attacks, tap Gale Blade, and Dandelion Breeze's initial and two guaranteed
 * border hits. Gale Blade owns a cast-time snapshot, as does every Burst hit.
 * Skill particles use the deterministic expectation of the sourced
 * two-or-three-particle distribution.
 *
 * <p>
 * A4, C2, C3, C4, and C5 are represented. Healing, C1's held pull, fall
 * damage, enemy displacement, movement speed, stamina, self-Swirl geometry,
 * and C6 incoming-damage reduction are intentionally excluded.
 */
public class Jean extends Character implements
        SimulatorInitializedCharacterEffect,
        CharacterTeamBuffProvider {
    private static final double FRAME = 1.0 / 60.0;
    private static final double SKILL_COOLDOWN = 6.0;
    private static final double BURST_COOLDOWN = 20.0;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double C2_DURATION = 15.0;
    private static final double C2_ATTACK_SPEED = 0.15;
    private static final double C4_ANEMO_RES_SHRED = 0.40;
    private static final int BURST_FIELD_START_FRAME = 40;
    private static final int BURST_INITIAL_HIT_FRAME = 55;
    private static final int BURST_FIELD_END_FRAME = 640;
    private static final int C4_EFFECT_END_FRAME = 712;

    private static final double[] NORMAL_MULTIPLIERS = {
            0.88796, 0.83740, 1.10758, 1.21028, 1.45518
    };
    private static final int[] NORMAL_HIT_FRAMES = {
            13, 6, 17, 37, 25
    };
    private static final int[] NORMAL_ACTION_FRAMES = {
            22, 14, 28, 44, 68
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;

    /** Constructs the repository-default C6 Jean. */
    public Jean(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /**
     * Constructs Jean at an explicit constellation.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param constellation constellation in the inclusive range 0-6
     */
    public Jean(
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
     * Constructs Jean with injectable talent data and constellation state.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Jean(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Jean constellation must be between 0 and 6");
        }
        this.name = "Jean";
        this.characterId = CharacterId.JEAN;
        this.element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 14695.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 239.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 769.0));
        baseStats.add(StatType.HEALING_BONUS,
                getTalentValue("Ascension Healing Bonus", 0.2215));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds Jean's particle listener and mutable markers to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Jean cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;
        if (constellation >= 2) {
            sim.addParticleListener((element, count, time) -> {
                if (count > 0.0 && sim.getActiveCharacter() == Jean.this) {
                    replaceC2Marker(time);
                }
            });
        }
    }

    /** Returns Jean's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Jean has no unconditional offensive passive in this slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Represented passives require Burst use or a particle pickup.
    }

    /**
     * Exposes C2 and C4 through snapshot-restorable character-owned markers.
     *
     * @return current team buffs; expired entries are harmless half-open buffs
     */
    @Override
    public List<Buff> getTeamBuffs() {
        List<Buff> buffs = new ArrayList<>();
        for (Buff marker : getActiveBuffs()) {
            if (marker instanceof JeanC2MarkerBuff) {
                buffs.add(new JeanC2AttackSpeedBuff(marker));
            } else if (marker instanceof JeanC4FieldMarkerBuff) {
                buffs.add(new JeanC4ResistanceBuff(marker));
            }
        }
        return buffs;
    }

    /** Dispatches Jean's supported typed offensive actions. */
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
                galeBlade(sim);
                break;
            case BURST:
                dandelionBreeze(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Jean: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        int step = normalAttackStep;
        double speedScale = 1.0 + attackSpeed(sim, castTime, true);
        AttackAction normal = attack(
                "Favonius Bladework N" + (step + 1),
                getTalentValue(
                        "N" + (step + 1),
                        NORMAL_MULTIPLIERS[step]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0);
        schedule(
                sim,
                castTime + NORMAL_HIT_FRAMES[step] * FRAME / speedScale,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, normal));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        sim.advanceTime(
                NORMAL_ACTION_FRAMES[step] * FRAME / speedScale);
    }

    private void chargedAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        double speedScale = 1.0 + attackSpeed(sim, castTime, false);
        AttackAction charged = attack(
                "Favonius Bladework Charged Attack",
                getTalentValue("Charged Attack", 2.97672),
                Element.PHYSICAL,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0);
        schedule(
                sim,
                castTime + 36.0 * FRAME / speedScale,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, charged));
        sim.advanceTime(57.0 * FRAME / speedScale);
    }

    private void highPlunge(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        AttackAction plunge = attack(
                "Favonius Bladework High Plunge",
                getTalentValue("High Plunge", 2.933586),
                Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                0.0);
        plunge.setShatterTrigger(true);
        schedule(
                sim,
                castTime + 43.0 * FRAME,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, plunge));
        sim.advanceTime(80.0 * FRAME);
    }

    private void galeBlade(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        StatsContainer skillSnapshot = captureActionSnapshot(sim, castTime);
        schedule(sim, castTime + 19.0 * FRAME, activeSim ->
                markSkillUsed(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this)));
        schedule(
                sim,
                castTime + 21.0 * FRAME,
                activeSim -> resolveGaleBlade(activeSim, skillSnapshot));
        sim.advanceTime(46.0 * FRAME);
    }

    private void resolveGaleBlade(
            CombatSimulator sim,
            StatsContainer skillSnapshot) {
        if (sim.getEnemy() == null) {
            return;
        }
        boolean c5 = constellation >= 5;
        AttackAction skill = attack(
                "Gale Blade",
                getTalentValue(
                        c5 ? "Gale Blade C5" : "Gale Blade",
                        c5 ? 5.8400 : 4.9640),
                Element.ANEMO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                2.0);
        skill.setStatSnapshot(skillSnapshot);
        sim.performActionWithoutTimeAdvance(characterId, skill);
        schedule(
                sim,
                sim.getCurrentTime() + PARTICLE_TRAVEL,
                activeSim -> activeSim.getEnergyDistributor()
                        .distributeParticles(
                                Element.ANEMO,
                                getTalentValue(
                                        "Skill Particles",
                                        2.67),
                                ParticleType.PARTICLE));
    }

    private void dandelionBreeze(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        StatsContainer burstSnapshot = captureActionSnapshot(sim, castTime);
        boolean c3 = constellation >= 3;

        schedule(sim, castTime + 38.0 * FRAME, activeSim ->
                markBurstCooldownUsed(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this)));
        schedule(sim, castTime + 41.0 * FRAME, activeSim ->
                spendBurstEnergy(activeSim.getCurrentTime()));
        if (constellation >= 4) {
            schedule(sim, castTime + 39.0 * FRAME, activeSim ->
                    replaceC4Marker(
                            activeSim.getCurrentTime(),
                            castTime + C4_EFFECT_END_FRAME * FRAME));
        }
        schedule(sim, castTime + 41.0 * FRAME, activeSim ->
                receiveFlatEnergy(getTalentValue("A4 Energy", 16.0)));
        schedule(sim, castTime + BURST_FIELD_START_FRAME * FRAME,
                activeSim -> resolveBurstHit(
                        activeSim,
                        "Dandelion Breeze Field Entry",
                        getTalentValue(
                                c3 ? "Field Border C3" : "Field Border",
                                c3 ? 1.5680 : 1.3328),
                        burstSnapshot));
        schedule(sim, castTime + BURST_INITIAL_HIT_FRAME * FRAME,
                activeSim -> resolveBurstHit(
                        activeSim,
                        "Dandelion Breeze Initial",
                        getTalentValue(
                                c3 ? "Dandelion Breeze C3"
                                        : "Dandelion Breeze",
                                c3 ? 8.4960 : 7.2216),
                        burstSnapshot));
        schedule(sim, castTime + BURST_FIELD_END_FRAME * FRAME,
                activeSim -> resolveBurstHit(
                        activeSim,
                        "Dandelion Breeze Field Exit",
                        getTalentValue(
                                c3 ? "Field Border C3" : "Field Border",
                                c3 ? 1.5680 : 1.3328),
                        burstSnapshot));
        sim.advanceTime(90.0 * FRAME);
    }

    private void resolveBurstHit(
            CombatSimulator sim,
            String name,
            double multiplier,
            StatsContainer snapshot) {
        AttackAction burst = attack(
                name,
                multiplier,
                Element.ANEMO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                2.0);
        burst.setStatSnapshot(snapshot);
        sim.performActionWithoutTimeAdvance(characterId, burst);
    }

    private StatsContainer captureActionSnapshot(
            CombatSimulator sim,
            double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : sim.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private double attackSpeed(
            CombatSimulator sim,
            double currentTime,
            boolean normalAttack) {
        StatsContainer stats = captureActionSnapshot(sim, currentTime);
        double speed = stats.get(StatType.ATK_SPD);
        if (normalAttack) {
            speed += stats.get(StatType.NORMAL_ATTACK_SPD);
        }
        return Math.min(0.60, Math.max(0.0, speed));
    }

    private void replaceC2Marker(double currentTime) {
        getActiveBuffs().removeIf(
                buff -> buff instanceof JeanC2MarkerBuff);
        addBuff(new JeanC2MarkerBuff(currentTime));
    }

    private void replaceC4Marker(
            double currentTime,
            double expirationTime) {
        getActiveBuffs().removeIf(
                buff -> buff instanceof JeanC4FieldMarkerBuff);
        addBuff(new JeanC4FieldMarkerBuff(
                currentTime,
                expirationTime));
    }

    private AttackAction attack(
            String name,
            double multiplier,
            Element attackElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                attackElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
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

    /** Snapshot-restorable marker for Jean's C2 team buff. */
    private static final class JeanC2MarkerBuff extends Buff {
        private JeanC2MarkerBuff(double currentTime) {
            super("Jean C2 People's Aegis Marker", C2_DURATION, currentTime);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            // Team effect is exposed only through CharacterTeamBuffProvider.
        }
    }

    /** Team ATK SPD projection of a snapshot-restorable C2 marker. */
    private static final class JeanC2AttackSpeedBuff extends Buff {
        private JeanC2AttackSpeedBuff(Buff marker) {
            super(
                    "Jean C2 People's Aegis",
                    marker.getExpirationTime() - marker.getStartTime(),
                    marker.getStartTime());
            restoreTimes(
                    marker.getStartTime(),
                    marker.getExpirationTime());
            sourcedBy(CharacterId.JEAN);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            stats.add(StatType.ATK_SPD, C2_ATTACK_SPEED);
        }
    }

    /**
     * Snapshot-restorable marker for Jean's stationary C4 field window.
     *
     * <p>The final frame-640 refresh lasts 1.2 seconds, so the half-open
     * stationary-target effect window ends at frame 712.
     */
    private static final class JeanC4FieldMarkerBuff extends Buff {
        private JeanC4FieldMarkerBuff(
                double currentTime,
                double expirationTime) {
            super(
                    "Jean C4 Lands of Dandelion Marker",
                    expirationTime - currentTime,
                    currentTime);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            // Team resistance effect is exposed by the provider projection.
        }
    }

    /** Team-visible Anemo RES shred projected from Jean's C4 field marker. */
    private static final class JeanC4ResistanceBuff extends Buff {
        private JeanC4ResistanceBuff(Buff marker) {
            super(
                    "Jean C4 Lands of Dandelion",
                    marker.getExpirationTime() - marker.getStartTime(),
                    marker.getStartTime());
            restoreTimes(
                    marker.getStartTime(),
                    marker.getExpirationTime());
            sourcedBy(CharacterId.JEAN);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            stats.add(StatType.ANEMO_RES_SHRED, C4_ANEMO_RES_SHRED);
        }
    }
}
