package model.character;

import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.BurstTriggeredArtifactEffect;
import model.entity.Character;
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
import simulation.action.CharacterActionRequest;
import simulation.event.SimpleTimerEvent;

/**
 * Amber character implementation for stationary single-target combat.
 *
 * <p>
 * Baron Bunny snapshots one explosion eight seconds after cast. Fiery Rain
 * models a target at the center of its area, receiving all 18 waves over two
 * seconds. C1, C3, C4, C5, and C6 are represented; weak-point A4, C2 manual
 * detonation, summon durability/taunt, random outer placement, and movement
 * speed remain outside the simulator's current state model.
 */
public class Amber extends Character {
    private static final double BARON_BUNNY_DURATION = 8.0;
    private static final double FIERY_RAIN_DURATION = 2.0;
    private static final int FIERY_RAIN_WAVES = 18;

    private int normalAttackStep;

    /**
     * Constructs Amber with the shared talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     */
    public Amber(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    /**
     * Constructs Amber with an explicit talent source for deterministic tests.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     */
    public Amber(Weapon weapon, ArtifactSet artifacts, TalentDataSource talentData) {
        super(talentData);
        this.name = "Amber";
        this.characterId = CharacterId.AMBER;
        this.element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = (int) getTalentValue("Constellation", 6.0);

        baseStats.set(StatType.BASE_HP, getTalentValue("Base HP", 9461.0));
        baseStats.set(StatType.BASE_ATK, getTalentValue("Base ATK", 223.0));
        baseStats.set(StatType.BASE_DEF, getTalentValue("Base DEF", 601.0));
        baseStats.add(StatType.ATK_PERCENT, getTalentValue("Ascension ATK", 0.24));
        setSkillCD(constellation >= 4 ? 12.0 : 15.0);
        setSkillMaxCharges(constellation >= 4 ? 2 : 1);
        setBurstCD(12.0);
    }

    /**
     * Returns Amber's 40-Energy Burst cost.
     *
     * @return current Burst cost
     */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 40.0);
    }

    /**
     * Amber has no unconditional self passive in the modeled slice.
     *
     * @param stats assembled stats container
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A1 is Burst-specific and is attached to each Fiery Rain wave.
    }

    /**
     * Dispatches Amber's typed player actions.
     *
     * @param request requested action
     * @param sim active combat simulator
     */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(sim);
                break;
            case CHARGE:
                fullyChargedAimedShot(sim);
                break;
            case SKILL:
                markSkillUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                baronBunny(sim);
                break;
            case BURST:
                markBurstUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                fieryRain(sim);
                break;
            case DASH:
                normalAttackStep = 0;
                sim.advanceTime(20.0 / 60.0);
                break;
            case PLUNGE:
                plunge(sim);
                break;
            default:
                throw new IllegalArgumentException("Unsupported action for Amber: " + request.getKey());
        }
    }

    private void baronBunny(CombatSimulator sim) {
        captureSnapshot(sim.getCurrentTime(), sim.getApplicableBuffs(this));
        double defaultMultiplier = constellation >= 5 ? 2.4640 : 2.0944;
        AttackAction explosion = new AttackAction(
                "Baron Bunny Explosion",
                getTalentValue("Baron Bunny Explosion", defaultMultiplier),
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                true,
                ActionType.SKILL);
        explosion.setICD(ICDType.None, ICDTag.ElementalSkill, 2.0);

        sim.registerEvent(new SimpleTimerEvent(
                sim.getCurrentTime() + BARON_BUNNY_DURATION, BARON_BUNNY_DURATION) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                activeSim.performActionWithoutTimeAdvance(Amber.this.characterId, explosion);
                activeSim.getEnergyDistributor().distributeParticles(
                        Element.PYRO, 4.0, ParticleType.PARTICLE);
                finish();
            }
        });
        sim.advanceTime(32.0 / 60.0);
    }

    private void fieryRain(CombatSimulator sim) {
        captureSnapshot(sim.getCurrentTime(), sim.getApplicableBuffs(this));
        for (ArtifactSet artifact : artifacts) {
            if (artifact instanceof BurstTriggeredArtifactEffect) {
                ((BurstTriggeredArtifactEffect) artifact).onBurst(sim);
            }
        }
        if (constellation >= 6) {
            sim.applyTeamBuff(new SimpleBuff(
                    "Amber C6 Wildfire", 10.0, sim.getCurrentTime(),
                    stats -> stats.add(StatType.ATK_PERCENT, 0.15))
                    .sourcedBy(characterId));
        }

        double defaultMultiplier = constellation >= 3 ? 0.5617 : 0.4774;
        AttackAction wave = new AttackAction(
                "Fiery Rain Wave",
                getTalentValue("Fiery Rain Wave", defaultMultiplier),
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                true,
                ActionType.BURST);
        wave.setICD(ICDType.Standard, ICDTag.ElementalBurst, 1.0);
        wave.addBonusStat(StatType.BURST_CRIT_RATE, 0.10);

        double interval = FIERY_RAIN_DURATION / FIERY_RAIN_WAVES;
        sim.registerEvent(new SimpleTimerEvent(sim.getCurrentTime() + interval, interval) {
            private int remainingWaves = FIERY_RAIN_WAVES;

            @Override
            public void onTick(CombatSimulator activeSim) {
                activeSim.performActionWithoutTimeAdvance(Amber.this.characterId, wave);
                remainingWaves--;
                if (remainingWaves == 0) {
                    finish();
                }
            }
        });
        sim.advanceTime(111.0 / 60.0);
    }

    private void normalAttack(CombatSimulator sim) {
        String key = "N" + (normalAttackStep + 1);
        double[] durations = { 26.0 / 60.0, 22.0 / 60.0, 37.0 / 60.0,
                34.0 / 60.0, 60.0 / 60.0 };
        double[] defaults = { 0.6636, 0.6636, 0.8532, 0.8688, 1.0903 };
        AttackAction normal = new AttackAction(
                "Amber " + key,
                getTalentValue(key, defaults[normalAttackStep]),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                durations[normalAttackStep],
                ActionType.NORMAL);
        normal.setICD(ICDType.Standard, ICDTag.NormalAttack, 0.0);
        sim.performAction(characterId, normal);

        normalAttackStep++;
        if (normalAttackStep >= defaults.length) {
            normalAttackStep = 0;
        }
    }

    private void fullyChargedAimedShot(CombatSimulator sim) {
        double multiplier = getTalentValue("Fully Charged Aimed Shot", 2.1055);
        AttackAction primary = new AttackAction(
                "Amber Charged Aimed Shot",
                multiplier,
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                constellation >= 1 ? 0.0 : 96.0 / 60.0,
                ActionType.CHARGE);
        primary.setICD(ICDType.Standard, ICDTag.ChargedAttack, 2.0);
        sim.performAction(characterId, primary);

        if (constellation >= 1) {
            AttackAction second = new AttackAction(
                    "Amber Charged C1 Arrow",
                    multiplier * 0.20,
                    Element.PYRO,
                    StatType.BASE_ATK,
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    96.0 / 60.0,
                    ActionType.CHARGE);
            second.setICD(ICDType.Standard, ICDTag.ChargedAttack, 2.0);
            sim.performAction(characterId, second);
        }
        normalAttackStep = 0;
    }

    private void plunge(CombatSimulator sim) {
        AttackAction plunge = new AttackAction(
                "Amber High Plunge",
                getTalentValue("Plunge High", 2.6086),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                1.0,
                ActionType.PLUNGE);
        plunge.setICD(ICDType.Standard, ICDTag.None, 0.0);
        sim.performAction(characterId, plunge);
        normalAttackStep = 0;
    }
}
