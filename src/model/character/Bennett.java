package model.character;

import model.entity.BurstStateProvider;
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

public class Bennett extends Character implements BurstStateProvider {

    private int normalAttackStep = 0;

    public Bennett(Weapon weapon, ArtifactSet artifacts) {
        super();
        this.name = "Bennett";
        this.characterId = CharacterId.BENNETT;

        double baseAtk = mechanics.data.TalentDataManager.getInstance().get(this.name, "Base ATK", 191);
        double ascEr = mechanics.data.TalentDataManager.getInstance().get(this.name, "Ascension ER", 0.267);

        baseStats.set(StatType.BASE_ATK, baseAtk);
        baseStats.add(StatType.ENERGY_RECHARGE, ascEr);
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.element = Element.PYRO;
        this.constellation = (int) mechanics.data.TalentDataManager.getInstance().get(this.name, "Constellation", 6.0);
        setSkillCD(5.0); // Tap E Short CD
        setBurstCD(15.0);
    }

    @Override
    public double getEnergyCost() {
        return mechanics.data.TalentDataManager.getInstance().get(this.name, "Energy Cost", 60);
    }

    @Override
    public boolean isBurstActive(double currentTime) {
        return (currentTime - getLastBurstTime()) < 12.0;
    }

    @Override
    public void applyPassive(StatsContainer stats) {
        // No static passives affecting stats
    }

    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        switch (request.getKey()) {
            case SKILL:
                markSkillUsed(sim.getCurrentTime());
                skill(sim); // Default to Tap
                break;
            case BURST:
                markBurstUsed(sim.getCurrentTime());
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
        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Passion Overload Tap", 2.34);
        AttackAction hit = new AttackAction("Passion Overload (Tap)", mv, Element.PYRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL);
        hit.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0);
        sim.performAction(this.name, hit);

        // Generate 2 Pyro Particles (Tap)
        mechanics.energy.EnergyManager.distributeParticles(Element.PYRO, 2.0, mechanics.energy.ParticleType.PARTICLE,
                sim);
    }

    private void burst(CombatSimulator sim) {
        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Fantastic Voyage Hit", 3.96);
        AttackAction q = new AttackAction("Fantastic Voyage Hit", mv, Element.PYRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.8, ActionType.BURST);
        q.setICD(ICDType.Standard, ICDTag.ElementalBurst, 2.0); // 2U application
        sim.performAction(this.name, q);

        // Trigger Artifact Buffs
        for (ArtifactSet a : artifacts) {
            a.onBurst(sim);
        }

        // Apply Field Buff
        // Apply Field Buff
        double baseRatio = mechanics.data.TalentDataManager.getInstance().get(this.name, "Buff Ratio", 0.95);
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
        double hpRatio = mechanics.data.TalentDataManager.getInstance().get(this.name, "Heal HP Ratio", 0.102);
        double flatHeal = mechanics.data.TalentDataManager.getInstance().get(this.name, "Heal Flat", 1174);
        System.out.println(
                String.format("   [Bennett] Fantastic Voyage Field Active. ATK Buff: +%.0f, Heal/sec: %.2f%% HP + %.0f",
                        atkBonus, hpRatio * 100, flatHeal));
    }

    private void normalAttack(CombatSimulator sim) {
        // C6 Infusion Logic
        boolean hasInfusion = sim.getApplicableBuffs(this).stream()
                .anyMatch(b -> b.getId() == BuffId.FANTASTIC_VOYAGE);
        Element dmgElement = hasInfusion ? Element.PYRO : Element.PHYSICAL;

        String key = "N" + (normalAttackStep + 1);
        String name = "Bennett " + key;

        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, key, 0.5);
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

        AttackAction hit = new AttackAction(name, mv, dmgElement, StatType.BASE_ATK,
                hasInfusion ? StatType.NORMAL_ATTACK_DMG_BONUS : StatType.PHYSICAL_DMG_BONUS, // Using Normal Bonus for
                                                                                              // Pyro too?
                                                                                              // Actually Pyro Infusion
                                                                                              // benefits from Pyro DMG
                                                                                              // Bonus.
                                                                                              // StatsContainer usually
                                                                                              // adds specific element
                                                                                              // bonus to general DMG?
                                                                                              // Wait,
                                                                                              // `StatType.NORMAL_ATTACK_DMG_BONUS`
                                                                                              // is generic.
                                                                                              // The Element arg
                                                                                              // determines if Pyro/Phys
                                                                                              // Bonus applies in
                                                                                              // `DamageCalculator`.
                                                                                              // So passing
                                                                                              // `NORMAL_ATTACK_DMG_BONUS`
                                                                                              // is correct for the
                                                                                              // additive bonus.
                dur, ActionType.NORMAL);

        // Wait, Constructor of AttackAction takes `bonusType` (StatType).
        // If I pass NORMAL_ATTACK_DMG_BONUS, it adds that.
        // The DamageCalculator also looks up `stats.get(DMG_BONUS_ELEMENT)` e.g.
        // PYRO_DMG_BONUS automatically based on element.
        // So this is correct.

        hit.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
        sim.performAction(this.name, hit);

        normalAttackStep++;
        if (normalAttackStep >= 5)
            normalAttackStep = 0;
    }

    private void chargeAttack(CombatSimulator sim) {
        boolean hasInfusion = sim.getApplicableBuffs(this).stream()
                .anyMatch(b -> b.getId() == BuffId.FANTASTIC_VOYAGE);
        Element dmgElement = hasInfusion ? Element.PYRO : Element.PHYSICAL;

        double mv1 = mechanics.data.TalentDataManager.getInstance().get(this.name, "CA_1", 1.03);
        double mv2 = mechanics.data.TalentDataManager.getInstance().get(this.name, "CA_2", 1.12);

        AttackAction hit1 = new AttackAction("Bennett CA_1", mv1, dmgElement, StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS, 0.2, ActionType.CHARGE);
        hit1.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performActionWithoutTimeAdvance(this.name, hit1);

        AttackAction hit2 = new AttackAction("Bennett CA_2", mv2, dmgElement, StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS, 0.6, ActionType.CHARGE);
        hit2.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performAction(this.name, hit2);

        normalAttackStep = 0;
    }

    private void plunge(CombatSimulator sim) {
        boolean hasInfusion = sim.getApplicableBuffs(this).stream()
                .anyMatch(b -> b.getId() == BuffId.FANTASTIC_VOYAGE);
        Element dmgElement = hasInfusion ? Element.PYRO : Element.PHYSICAL;

        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Plunge High", 2.93);
        AttackAction p = new AttackAction("Bennett Plunge", mv, dmgElement, StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS, 1.0, ActionType.PLUNGE);
        p.setICD(ICDType.Standard, ICDTag.None, 1.0);
        sim.performAction(this.name, p);
    }
}
