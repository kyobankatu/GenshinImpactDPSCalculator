package model.character;

import java.util.List;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import model.entity.FormStateProvider;
import model.entity.Character;
import model.entity.Weapon;
import model.entity.ArtifactSet;
import mechanics.buff.BuffId;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.ICDType;
import model.type.ICDTag;
import model.type.ActionType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionRequest;
import simulation.action.HitlagProfile;

/**
 * Bennett character implementation with Fantastic Voyage field buff handling.
 */
public class Bennett extends Character implements FormStateProvider {
    private static final double FANTASTIC_VOYAGE_SKILL_COOLDOWN_REDUCTION = 0.5;

    /**
     * Hitlag from gcsim config YAML pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_HITLAG_SHORT =
            new HitlagProfile(0.03, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_MEDIUM =
            new HitlagProfile(0.06, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_LONG =
            new HitlagProfile(0.09, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_LONGEST =
            new HitlagProfile(0.12, 0.01, true, false, false);

    private int normalAttackStep = 0;

    /**
     * Constructs Bennett with the shared talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     */
    public Bennett(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    /**
     * Constructs Bennett with an explicit talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent values backing this character
     */
    public Bennett(Weapon weapon, ArtifactSet artifacts, TalentDataSource talentData) {
        super(talentData);
        this.name = "Bennett";
        this.characterId = CharacterId.BENNETT;

        double baseAtk = getTalentValue("Base ATK", 191);
        double ascEr = getTalentValue("Ascension ER", 0.267);

        baseStats.set(StatType.BASE_ATK, baseAtk);
        baseStats.add(StatType.ENERGY_RECHARGE, ascEr);
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.element = Element.PYRO;
        this.constellation = (int) getTalentValue("Constellation", 6.0);
        setSkillCD(4.0); // Rekindle reduces the Tap Skill cooldown by 20%.
        setBurstCD(15.0);
    }

    /**
     * Returns Bennett's burst energy cost.
     *
     * @return burst cost in energy
     */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60);
    }

    /**
     * Returns whether Fantastic Voyage is currently active.
     *
     * @param currentTime current simulation time in seconds
     * @return {@code true} while Bennett's burst field persists
     */
    @Override
    public boolean isFormActive(double currentTime) {
        return (currentTime - getLastBurstTime()) < 12.0;
    }

    /**
     * Applies Bennett's passive stat effects.
     *
     * @param stats stats container to modify
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        // No static passives affecting stats
    }

    /**
     * Executes the requested combat action for Bennett.
     *
     * @param request requested player action
     * @param sim active combat simulator
     */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        switch (request.getKey()) {
            case SKILL:
                double skillTime = sim.getCurrentTime();
                List<Buff> applicableBuffs = sim.getApplicableBuffs(this);
                markSkillUsed(skillTime, applicableBuffs);
                if (hasFantasticVoyage(applicableBuffs)) {
                    reduceSkillCooldown(
                            skillTime,
                            getSkillCD() * FANTASTIC_VOYAGE_SKILL_COOLDOWN_REDUCTION);
                }
                skill(sim); // Default to Tap
                break;
            case BURST:
                markBurstUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                burst(sim);
                break;
            case NORMAL:
                normalAttack(sim);
                break;
            case CHARGE:
                chargeAttack(sim);
                break;
            case DASH:
                normalAttackStep = 0;
                sim.advanceTime(0.4);
                break;
            case PLUNGE:
                plunge(sim);
                break;
            default:
                throw new IllegalArgumentException("Unsupported action for Bennett: " + request.getKey());
        }
    }

    private void skill(CombatSimulator sim) {
        double mv = getTalentValue("Passion Overload Tap", 2.34);
        AttackAction hit = new AttackAction("Passion Overload (Tap)", mv, Element.PYRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL);
        hit.setICD(ICDType.None, ICDTag.ElementalSkill, 2.0);
        hit.setHitlagProfile(NORMAL_HITLAG_LONG);
        sim.performAction(this.characterId, hit);

        // Generate 2 Pyro Particles (Tap)
        sim.getEnergyDistributor().distributeParticles(Element.PYRO, 2.0, mechanics.energy.ParticleType.PARTICLE);
    }

    private void burst(CombatSimulator sim) {
        double mv = getTalentValue("Fantastic Voyage Hit", 3.96);
        AttackAction q = new AttackAction("Fantastic Voyage Hit", mv, Element.PYRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.8, ActionType.BURST);
        q.setICD(ICDType.None, ICDTag.ElementalBurst, 2.0);
        q.setHitlagProfile(HitlagProfile.none());
        sim.performAction(this.characterId, q);

        // Apply Field Buff
        // Apply Field Buff
        double baseRatio = getTalentValue("Buff Ratio", 0.95);
        double totalRatio = baseRatio;

        // C1: Base ATK +20%
        if (this.constellation >= 1) {
            totalRatio += 0.20;
        }

        double atkBonus = (baseStats.get(StatType.BASE_ATK) + weapon.getBaseAtk()) * totalRatio;

        sim.applyFieldBuff(new mechanics.buff.SimpleBuff("Fantastic Voyage", BuffId.FANTASTIC_VOYAGE, 12.0,
                sim.getCurrentTime(), s -> {
            s.add(StatType.ATK_FLAT, atkBonus);

            // C6: Pyro Bonus
            if (this.constellation >= 6) {
                s.add(StatType.PYRO_DMG_BONUS, 0.15);
            }
        }));

        // Healing (Not fully implemented in sim, but we can log it)
        double hpRatio = getTalentValue("Heal HP Ratio", 0.102);
        double flatHeal = getTalentValue("Heal Flat", 1174);
        System.out.println(
                String.format("   [Bennett] Fantastic Voyage Field Active. ATK Buff: +%.0f, Heal/sec: %.2f%% HP + %.0f",
                        atkBonus, hpRatio * 100, flatHeal));
    }

    private void normalAttack(CombatSimulator sim) {
        boolean hasInfusion = hasC6Infusion(sim);
        Element dmgElement = hasInfusion ? Element.PYRO : Element.PHYSICAL;

        String key = "N" + (normalAttackStep + 1);
        String name = "Bennett " + key;

        double mv = getTalentValue(key, 0.5);
        double dur = 0.3; // Approx

        switch (normalAttackStep) {
            case 0:
                dur = 0.25;
                break;
            case 1:
                dur = 0.3;
                break;
            case 2:
                dur = 0.4;
                break;
            case 3:
                dur = 0.5;
                break;
            case 4:
                dur = 0.7;
                break;
        }

        AttackAction hit = new AttackAction(
                name,
                mv,
                dmgElement,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                dur,
                ActionType.NORMAL);

        hit.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
        hit.setHitlagProfile(getNormalHitlagProfile(normalAttackStep));
        sim.performAction(this.characterId, hit);

        normalAttackStep++;
        if (normalAttackStep >= 5) {
            normalAttackStep = 0;
        }
    }

    private HitlagProfile getNormalHitlagProfile(int attackStep) {
        switch (attackStep) {
            case 0:
            case 1:
                return NORMAL_HITLAG_SHORT;
            case 2:
                return NORMAL_HITLAG_MEDIUM;
            case 3:
                return NORMAL_HITLAG_LONG;
            case 4:
                return NORMAL_HITLAG_LONGEST;
            default:
                return HitlagProfile.none();
        }
    }

    private void chargeAttack(CombatSimulator sim) {
        boolean hasInfusion = hasC6Infusion(sim);
        Element dmgElement = hasInfusion ? Element.PYRO : Element.PHYSICAL;

        double mv1 = getTalentValue("CA_1", 1.03);
        double mv2 = getTalentValue("CA_2", 1.12);

        AttackAction hit1 = new AttackAction("Bennett CA_1", mv1, dmgElement, StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS, 0.2, ActionType.CHARGE);
        hit1.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performActionWithoutTimeAdvance(characterId, hit1);

        AttackAction hit2 = new AttackAction("Bennett CA_2", mv2, dmgElement, StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS, 0.6, ActionType.CHARGE);
        hit2.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performAction(this.characterId, hit2);

        normalAttackStep = 0;
    }

    private void plunge(CombatSimulator sim) {
        boolean hasInfusion = hasC6Infusion(sim);
        Element dmgElement = hasInfusion ? Element.PYRO : Element.PHYSICAL;

        double mv = getTalentValue("Plunge High", 2.93);
        AttackAction p = new AttackAction("Bennett Plunge", mv, dmgElement, StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS, 1.0, ActionType.PLUNGE);
        p.setICD(ICDType.Standard, ICDTag.None, 1.0);
        sim.performAction(this.characterId, p);
    }

    private boolean hasC6Infusion(CombatSimulator sim) {
        return constellation >= 6
                && hasFantasticVoyage(sim.getApplicableBuffs(this));
    }

    private boolean hasFantasticVoyage(List<Buff> applicableBuffs) {
        return applicableBuffs.stream()
                .anyMatch(buff -> buff.getId() == BuffId.FANTASTIC_VOYAGE);
    }
}
