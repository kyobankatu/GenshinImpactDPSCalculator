package model.character;

import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.BurstTriggeredArtifactEffect;
import model.entity.Character;
import model.entity.FormStateProvider;
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
 * Kaeya character implementation for stationary single-target combat.
 *
 * <p>
 * Frostgnaw applies 2U Cryo without ICD and uses 2.67 expected particles. When
 * the one modeled enemy is Frozen after the hit, Glacial Heart adds one more
 * particle. Glacial Waltz snapshots 13 stationary hits over eight seconds, or
 * 17 at C6, where it also refunds 15 flat Energy to Kaeya.
 *
 * <p>
 * C1, C3, C5, and C6 are represented. Defeat-driven C2, low-HP/incoming-damage
 * C4, healing, stamina, multi-target A4, and movement-dependent Burst hit counts
 * remain outside the simulator's current state model.
 */
public class Kaeya extends Character implements FormStateProvider {
    private static final double BURST_DURATION = 8.0;
    private static final int BASE_BURST_HITS = 13;
    private static final int C6_BURST_HITS = 17;

    private int normalAttackStep;

    /**
     * Constructs Kaeya with the shared talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     */
    public Kaeya(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    /**
     * Constructs Kaeya with an explicit talent source for deterministic tests.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     */
    public Kaeya(Weapon weapon, ArtifactSet artifacts, TalentDataSource talentData) {
        super(talentData);
        this.name = "Kaeya";
        this.characterId = CharacterId.KAEYA;
        this.element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = (int) getTalentValue("Constellation", 6.0);

        baseStats.set(StatType.BASE_HP, getTalentValue("Base HP", 11636.0));
        baseStats.set(StatType.BASE_ATK, getTalentValue("Base ATK", 223.0));
        baseStats.set(StatType.BASE_DEF, getTalentValue("Base DEF", 792.0));
        baseStats.add(StatType.ENERGY_RECHARGE, getTalentValue("Ascension ER", 0.267));
        setSkillCD(6.0);
        setBurstCD(15.0);
    }

    /**
     * Returns Kaeya's 60-Energy Burst cost.
     *
     * @return current Burst cost
     */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /**
     * Returns whether Glacial Waltz remains in its fixed eight-second window.
     *
     * @param currentTime current simulation time in seconds
     * @return {@code true} before the eight-second expiry
     */
    @Override
    public boolean isFormActive(double currentTime) {
        return currentTime - getLastBurstTime() < BURST_DURATION;
    }

    /**
     * Kaeya has no unconditional static stat passive in the modeled slice.
     *
     * @param stats assembled stats container
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Conditional C1 is attached to eligible actions at impact time.
    }

    /**
     * Dispatches Kaeya's typed player actions.
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
                chargedAttack(sim);
                break;
            case SKILL:
                markSkillUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                frostgnaw(sim);
                break;
            case BURST:
                markBurstUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                glacialWaltz(sim);
                break;
            case DASH:
                normalAttackStep = 0;
                sim.advanceTime(19.0 / 60.0);
                break;
            case PLUNGE:
                plunge(sim);
                break;
            default:
                throw new IllegalArgumentException("Unsupported action for Kaeya: " + request.getKey());
        }
    }

    private void frostgnaw(CombatSimulator sim) {
        double defaultMultiplier = constellation >= 3 ? 3.82 : 3.2504;
        AttackAction skill = new AttackAction(
                "Frostgnaw",
                getTalentValue("Frostgnaw", defaultMultiplier),
                Element.CRYO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                53.0 / 60.0,
                ActionType.SKILL);
        skill.setICD(ICDType.None, ICDTag.ElementalSkill, 2.0);
        sim.performAction(characterId, skill);

        double particles = 2.67;
        if (sim.getEnemy() != null && sim.getEnemy().isFrozen(sim.getCurrentTime())) {
            particles += 1.0;
        }
        sim.getEnergyDistributor().distributeParticles(
                Element.CRYO, particles, ParticleType.PARTICLE);
    }

    private void glacialWaltz(CombatSimulator sim) {
        captureSnapshot(sim.getCurrentTime(), sim.getApplicableBuffs(this));
        for (ArtifactSet artifact : artifacts) {
            if (artifact instanceof BurstTriggeredArtifactEffect) {
                ((BurstTriggeredArtifactEffect) artifact).onBurst(sim);
            }
        }

        if (constellation >= 6) {
            receiveFlatEnergy(15.0);
        }

        double defaultMultiplier = constellation >= 5 ? 1.55 : 1.3192;
        AttackAction icicle = new AttackAction(
                "Glacial Waltz Icicle",
                getTalentValue("Glacial Waltz", defaultMultiplier),
                Element.CRYO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                true,
                ActionType.BURST);
        icicle.setICD(ICDType.Standard, ICDTag.ElementalBurst, 1.0);

        int hitCount = constellation >= 6 ? C6_BURST_HITS : BASE_BURST_HITS;
        double interval = BURST_DURATION / hitCount;
        sim.registerEvent(new SimpleTimerEvent(sim.getCurrentTime() + interval, interval) {
            private int remainingHits = hitCount;

            @Override
            public void onTick(CombatSimulator activeSim) {
                activeSim.performActionWithoutTimeAdvance(Kaeya.this.characterId, icicle);
                remainingHits--;
                if (remainingHits == 0) {
                    finish();
                }
            }
        });
        sim.advanceTime(51.0 / 60.0);
    }

    private void normalAttack(CombatSimulator sim) {
        String key = "N" + (normalAttackStep + 1);
        double[] durations = { 27.0 / 60.0, 27.0 / 60.0, 47.0 / 60.0,
                46.0 / 60.0, 74.0 / 60.0 };
        double[] defaults = { 0.9875, 0.9496, 1.1992, 1.3019, 1.6211 };
        AttackAction normal = new AttackAction(
                "Kaeya " + key,
                getTalentValue(key, defaults[normalAttackStep]),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                durations[normalAttackStep],
                ActionType.NORMAL);
        normal.setICD(ICDType.Standard, ICDTag.NormalAttack, 0.0);
        addC1CritBonus(normal, sim);
        sim.performAction(characterId, normal);

        normalAttackStep++;
        if (normalAttackStep >= defaults.length) {
            normalAttackStep = 0;
        }
    }

    private void chargedAttack(CombatSimulator sim) {
        AttackAction first = new AttackAction(
                "Kaeya CA_1",
                getTalentValue("CA_1", 1.0112),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                0.0,
                ActionType.CHARGE);
        first.setICD(ICDType.Standard, ICDTag.ChargedAttack, 0.0);
        addC1CritBonus(first, sim);
        sim.performActionWithoutTimeAdvance(characterId, first);

        AttackAction second = new AttackAction(
                "Kaeya CA_2",
                getTalentValue("CA_2", 1.3430),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                55.0 / 60.0,
                ActionType.CHARGE);
        second.setICD(ICDType.Standard, ICDTag.ChargedAttack, 0.0);
        addC1CritBonus(second, sim);
        sim.performAction(characterId, second);
        normalAttackStep = 0;
    }

    private void plunge(CombatSimulator sim) {
        AttackAction plunge = new AttackAction(
                "Kaeya High Plunge",
                getTalentValue("Plunge High", 2.9336),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                1.0,
                ActionType.PLUNGE);
        plunge.setICD(ICDType.Standard, ICDTag.None, 0.0);
        sim.performAction(characterId, plunge);
        normalAttackStep = 0;
    }

    private void addC1CritBonus(AttackAction action, CombatSimulator sim) {
        if (constellation < 1 || sim.getEnemy() == null) {
            return;
        }
        boolean cryoAffected = sim.getEnemy().getAuraUnits(Element.CRYO, sim.getCurrentTime()) > 0.0;
        if (cryoAffected || sim.getEnemy().isFrozen(sim.getCurrentTime())) {
            action.addBonusStat(StatType.CRIT_RATE, 0.15);
        }
    }
}
