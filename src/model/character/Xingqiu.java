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

public class Xingqiu extends Character implements BurstStateProvider {

    private int normalAttackStep = 0;
    private java.util.Map<String, Double> triggerCooldowns = new java.util.HashMap<>();
    private int raincutterWaveCount = 0;

    public Xingqiu(Weapon weapon, ArtifactSet artifacts) {
        super();
        this.name = "Xingqiu";
        this.characterId = CharacterId.XINGQIU;

        double baseAtk = mechanics.data.TalentDataManager.getInstance().get(this.name, "Base ATK", 202);
        double ascAtk = mechanics.data.TalentDataManager.getInstance().get(this.name, "Ascension ATK%", 0.24);

        baseStats.set(StatType.BASE_ATK, baseAtk);
        baseStats.add(StatType.ATK_PERCENT, ascAtk);
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.element = Element.HYDRO;
        this.constellation = (int) mechanics.data.TalentDataManager.getInstance().get(this.name, "Constellation", 6.0);
        setSkillCD(21.0);
        setBurstCD(20.0);
    }

    @Override
    public double getEnergyCost() {
        return mechanics.data.TalentDataManager.getInstance().get(this.name, "Energy Cost", 80);
    }

    @Override
    public boolean isBurstActive(double currentTime) {
        return (currentTime - getLastBurstTime()) < 18.0;
    }

    @Override
    public void applyPassive(StatsContainer stats) {
        double hydroBonus = mechanics.data.TalentDataManager.getInstance().get(this.name, "Hydro Bonus", 0.20);
        stats.add(StatType.HYDRO_DMG_BONUS, hydroBonus);
    }

    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        switch (request.getKey()) {
            case SKILL:
                markSkillUsed(sim.getCurrentTime());
                skill(sim);
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
                throw new IllegalArgumentException("Unsupported action for Xingqiu: " + request.getKey());
        }
    }

    private void skill(CombatSimulator sim) {
        double mvMulti = 1.0;
        // Check for C4 (50% DMG multiplier if Burst active)
        boolean isBurstActive = sim.getApplicableBuffs(this).stream()
                .anyMatch(b -> b.getId() == BuffId.RAINCUTTER);
        if (isBurstActive) {
            mvMulti = 1.5; // Multiplicative increase
            System.out.println("   [Xingqiu] C4 Activation: Skill DMG x1.5");
        }

        double mv1 = mechanics.data.TalentDataManager.getInstance().get(this.name, "Rain Screen Hit 1", 2.86);
        AttackAction hit1 = new AttackAction("Fatal Rainscreen Hit 1", mv1 * mvMulti, Element.HYDRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.5, ActionType.SKILL);
        hit1.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0);
        sim.performAction(this.name, hit1);

        double mv2 = mechanics.data.TalentDataManager.getInstance().get(this.name, "Rain Screen Hit 2", 3.25);
        AttackAction hit2 = new AttackAction("Fatal Rainscreen Hit 2", mv2 * mvMulti, Element.HYDRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.5, ActionType.SKILL);
        hit2.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0);
        sim.performAction(this.name, hit2);

        // Generate 5 Hydro Particles
        mechanics.energy.EnergyManager.distributeParticles(Element.HYDRO, 5.0, mechanics.energy.ParticleType.PARTICLE,
                sim);
    }

    private void burst(CombatSimulator sim) {
        AttackAction cast = new AttackAction("Raincutter Cast", 0.0, Element.HYDRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 1.0, ActionType.BURST);
        sim.performAction(this.name, cast);

        // Buff Logic
        sim.applyTeamBuff(new mechanics.buff.SimpleBuff("Raincutter", BuffId.RAINCUTTER, 18.0,
                sim.getCurrentTime(), s -> {
            s.add(StatType.RES_SHRED, 0.15); // Hydro Shred (C2)
        }));

        AttackAction orbital = new AttackAction("Raincutter Orbital", 0.0, Element.HYDRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.0, false, ActionType.OTHER);
        orbital.setICD(ICDType.Standard, ICDTag.Xingqiu_Orbital, 1.0);

        sim.registerEvent(
                new simulation.event.PeriodicDamageEvent("Xingqiu", orbital, sim.getCurrentTime(), 2.2, 18.0));

        // Register Raincutter Trigger (Self-contained)
        double expiryTime = sim.getCurrentTime() + 18.0;
        final Xingqiu self = this;

        sim.addListener((actor, action, time) -> {
            if (time > expiryTime)
                return; // Expired

            // Trigger on Normal Attacks (Atomic)
            if (action.getActionType() == model.type.ActionType.NORMAL) {
                // Check internal CD (1.0s)
                Double lastTrigger = self.triggerCooldowns.get("Raincutter");
                if (lastTrigger == null || time - lastTrigger >= 1.0) {
                    // Fire Raincutter (Multiple Swords)
                    java.util.List<AttackAction> rainSwords = self.getRaincutterAttack(self.raincutterWaveCount++);

                    if (rainSwords.size() == 5) {
                        // C6 Energy Restore
                        mechanics.energy.EnergyManager.distributeFlatEnergy(3.0, sim);
                        System.out.println("   [Energy] Xingqiu C6 restored 3 Energy");
                    }

                    System.out.println(
                            "   [Trigger] Raincutter Wave (" + rainSwords.size() + " Swords) on " + action.getName());

                    // Fire swords
                    for (AttackAction sword : rainSwords) {
                        sim.performActionWithoutTimeAdvance(self.getName(), sword);
                    }
                    self.triggerCooldowns.put("Raincutter", time);
                }
            }
        });
    }

    public java.util.List<AttackAction> getRaincutterAttack(int waveCount) {
        // C6 pattern: 2 - 3 - 5 swords.
        // Non-C6: 2 - 3 ...
        int cycle = waveCount % 3;
        int swords;

        if (this.constellation >= 6) {
            swords = (cycle == 0) ? 2 : (cycle == 1 ? 3 : 5);
        } else {
            swords = (waveCount % 2 == 0) ? 2 : 3;
        }

        double mvPerSword = mechanics.data.TalentDataManager.getInstance().get(this.name, "Raincutter Sword", 0.923);

        java.util.List<AttackAction> actions = new java.util.ArrayList<>();
        for (int i = 0; i < swords; i++) {
            AttackAction sword = new AttackAction("Raincutter Sword", mvPerSword, Element.HYDRO,
                    StatType.BASE_ATK, StatType.BURST_DMG_BONUS, 0.0, false, ActionType.BURST);
            sword.setICD(ICDType.Standard, ICDTag.Xingqiu_Raincutter, 1.0);
            actions.add(sword);
        }
        return actions;
    }

    private void normalAttack(CombatSimulator sim) {
        String baseKey = "N" + (normalAttackStep + 1);
        String name = "Xingqiu " + baseKey;
        double dur = 0.3; // Approx

        switch (normalAttackStep) {
            case 0:
                dur = 0.2;
                break; // N1
            case 1:
                dur = 0.25;
                break; // N2
            case 2:
                dur = 0.35;
                break; // N3 (2 hits)
            case 3:
                dur = 0.3;
                break; // N4
            case 4:
                dur = 0.5;
                break; // N5 (2 hits)
        }

        // Handle multi-hits for N3 and N5
        if (normalAttackStep == 2) { // N3
            double mv1 = mechanics.data.TalentDataManager.getInstance().get(this.name, "N3_1", 0.525);
            double mv2 = mechanics.data.TalentDataManager.getInstance().get(this.name, "N3_2", 0.525);

            AttackAction hit1 = new AttackAction(name + "_1", mv1, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, 0.15, ActionType.NORMAL);
            hit1.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performActionWithoutTimeAdvance(this.name, hit1);

            AttackAction hit2 = new AttackAction(name + "_2", mv2, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, 0.2, ActionType.NORMAL);
            hit2.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performAction(this.name, hit2);

        } else if (normalAttackStep == 4) { // N5
            double mv1 = mechanics.data.TalentDataManager.getInstance().get(this.name, "N5_1", 0.659);
            double mv2 = mechanics.data.TalentDataManager.getInstance().get(this.name, "N5_2", 0.659);

            AttackAction hit1 = new AttackAction(name + "_1", mv1, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, 0.2, ActionType.NORMAL);
            hit1.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performActionWithoutTimeAdvance(this.name, hit1);

            AttackAction hit2 = new AttackAction(name + "_2", mv2, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, 0.3, ActionType.NORMAL);
            hit2.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performAction(this.name, hit2);

        } else {
            // Single Hit
            double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, baseKey, 0.5);
            AttackAction hit = new AttackAction(name, mv, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, dur, ActionType.NORMAL);
            hit.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performAction(this.name, hit);
        }

        normalAttackStep++;
        if (normalAttackStep >= 5)
            normalAttackStep = 0;
    }

    private void chargeAttack(CombatSimulator sim) {
        double mv1 = mechanics.data.TalentDataManager.getInstance().get(this.name, "CA_1", 0.869);
        double mv2 = mechanics.data.TalentDataManager.getInstance().get(this.name, "CA_2", 1.03);

        AttackAction hit1 = new AttackAction("Xingqiu CA_1", mv1, Element.PHYSICAL, StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS, 0.2, ActionType.CHARGE);
        hit1.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performActionWithoutTimeAdvance(this.name, hit1);

        AttackAction hit2 = new AttackAction("Xingqiu CA_2", mv2, Element.PHYSICAL, StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS, 0.6, ActionType.CHARGE);
        hit2.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performAction(this.name, hit2);

        normalAttackStep = 0;
    }

    private void plunge(CombatSimulator sim) {
        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Plunge High", 2.93);
        AttackAction p = new AttackAction("Xingqiu Plunge", mv, Element.PHYSICAL, StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS, 1.0, ActionType.PLUNGE);
        p.setICD(ICDType.Standard, ICDTag.None, 1.0);
        sim.performAction(this.name, p);
    }
}
