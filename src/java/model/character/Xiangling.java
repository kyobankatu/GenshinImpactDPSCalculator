package model.character;

import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
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
import simulation.event.PeriodicDamageEvent;

public class Xiangling extends Character implements BurstStateProvider {

    private int normalAttackStep = 0;

    public Xiangling(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    public Xiangling(Weapon weapon, ArtifactSet artifacts, TalentDataSource talentData) {
        super(talentData);
        this.name = "Xiangling";
        this.characterId = CharacterId.XIANGLING;

        double baseAtk = getTalentValue("Base ATK", 225);
        double ascEm = getTalentValue("Ascension EM", 96.0);

        baseStats.set(StatType.BASE_ATK, baseAtk);
        baseStats.add(StatType.ELEMENTAL_MASTERY, ascEm);
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.element = Element.PYRO;
        this.constellation = (int) getTalentValue("Constellation", 6.0);
        setSkillCD(12.0);
        setBurstCD(20.0);
    }

    @Override
    public boolean isBurstActive(double currentTime) {
        double duration = (this.constellation >= 4) ? 14.0 : 10.0;
        return (currentTime - getLastBurstTime()) < duration;
    }

    @Override
    public void applyPassive(StatsContainer stats) {
        // "Crossfire" (Range +20%) -> Not stats
        // "Beware, It's Super Hot!" (Chili ATK +10%) -> Handled in logic or assumed?
        // User asked for logic updates. But Chili pickup is conditional.
        // We can create a persistent buff for Chili if we want, or simple static if
        // uptime is high.
        // The instruction says "Picking up chili... increases ATK by 10% for 10s".
        // In this simulation, let's register the effect in `skill` after Guoba
        // finishes?
        // Actually, Guoba leaves chili when he disappears.
        // For simplicity in rotation, we can assume pickup.
    }

    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80);
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
            case PLUNGE:
                plunge(sim);
                break;
            case DASH:
                normalAttackStep = 0;
                sim.advanceTime(0.4);
                break;
            default:
                throw new IllegalArgumentException("Unsupported action for Xiangling: " + request.getKey());
        }
    }

    private void skill(CombatSimulator sim) {
        double mv = getTalentValue("Guoba", 2.23);

        // Guoba Setup
        AttackAction guobaHit = new AttackAction("Guoba Attack", mv, Element.PYRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.0, true, ActionType.SKILL);
        guobaHit.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0);

        // Guoba duration 7s approx, 4 hits.
        // Drops chili at end (approx t+7s).
        double duration = 6.0; // 4 hits over 6s duration

        sim.registerEvent(new PeriodicDamageEvent(
                this.name, guobaHit, sim.getCurrentTime() + 2.0, 1.5, duration,
                s -> {
                    // C1 Shred
                    if (this.constellation >= 1) {
                        s.applyFieldBuff(
                                new mechanics.buff.SimpleBuff("Guoba C1 Shred", BuffId.XIANGLING_GUOBA_C1_SHRED, 6.0, s.getCurrentTime(), st -> {
                                    st.add(StatType.PYRO_RES_SHRED, 0.15);
                                }));
                    }

                    // Particles
                    s.getEnergyDistributor().distributeParticles(Element.PYRO, 1.0,
                            mechanics.energy.ParticleType.PARTICLE);
                }));

        // Chili Pickup at end of duration (assuming auto-pickup for sim efficiency)
        // "Beware, It's Super Hot!"
        double chiliAtk = getTalentValue("Attack Bonus", 0.10);
        sim.applyTeamBuff(new mechanics.buff.SimpleBuff("Xiangling Chili", BuffId.XIANGLING_CHILI, 10.0,
                sim.getCurrentTime() + 7.0, s -> {
            s.add(StatType.ATK_PERCENT, chiliAtk);
        }));
    }

    private void burst(CombatSimulator sim) {
        // Snapshot
        sim.getPartyMembers().stream().filter(c -> c == this).findFirst()
                .ifPresent(c -> c.captureSnapshot(sim.getCurrentTime(), sim.getTeamBuffs()));

        // Lv12 Values
        double castMv1 = getTalentValue("Pyronado Cast 1", 1.44);
        AttackAction cast1 = new AttackAction("Pyronado Cast 1", castMv1, Element.PYRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.5, true, ActionType.BURST);
        cast1.setICD(ICDType.Standard, ICDTag.ElementalBurst, 1.0);
        sim.performAction(this.name, cast1);

        double castMv2 = getTalentValue("Pyronado Cast 2", 1.76);
        AttackAction cast2 = new AttackAction("Pyronado Cast 2", castMv2, Element.PYRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.5, true, ActionType.BURST);
        cast2.setICD(ICDType.Standard, ICDTag.ElementalBurst, 1.0);
        sim.performAction(this.name, cast2);

        double castMv3 = getTalentValue("Pyronado Cast 3", 2.19);
        AttackAction cast3 = new AttackAction("Pyronado Cast 3", castMv3, Element.PYRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.6, true, ActionType.BURST);
        cast3.setICD(ICDType.Standard, ICDTag.ElementalBurst, 1.0);
        sim.performAction(this.name, cast3);

        double hitMv = getTalentValue("Pyronado Hit", 2.24);
        AttackAction hit = new AttackAction("Pyronado Hit", hitMv, Element.PYRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.0, true, ActionType.BURST);
        hit.setICD(ICDType.None, ICDTag.Xiangling_Pyronado, 1.0);

        // C4 Duration (10s -> 14s)
        double pyronadoDuration = (this.constellation >= 4) ? 14.0 : 10.0;

        PeriodicDamageEvent pde = new PeriodicDamageEvent("Xiangling", hit, sim.getCurrentTime(), 1.2,
                pyronadoDuration);
        sim.registerEvent(pde);

        // C6: Team Pyro DMG +15%
        if (this.constellation >= 6) {
            sim.applyTeamBuff(
                    new mechanics.buff.SimpleBuff("Xiangling C6", BuffId.XIANGLING_C6, pyronadoDuration,
                            sim.getCurrentTime(), s -> {
                        s.add(StatType.PYRO_DMG_BONUS, 0.15);
                    }));
        }
    }

    private void normalAttack(CombatSimulator sim) {
        String baseKey = "N" + (normalAttackStep + 1);
        String name = "Xiangling " + baseKey;
        double dur = 0.3;

        switch (normalAttackStep) {
            case 0:
                dur = 0.25;
                break;
            case 1:
                dur = 0.25;
                break;
            case 2:
                dur = 0.3;
                break;
            case 3:
                dur = 0.4;
                break;
            case 4:
                dur = 0.5;
                break;
        }

        if (normalAttackStep == 2) { // N3 (2 Hits)
            double mv1 = getTalentValue("N3_1", 0.479);
            double mv2 = getTalentValue("N3_2", 0.479);

            AttackAction hit1 = new AttackAction(name + "_1", mv1, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, 0.1, ActionType.NORMAL);
            hit1.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performActionWithoutTimeAdvance(this.name, hit1);

            AttackAction hit2 = new AttackAction(name + "_2", mv2, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, 0.2, ActionType.NORMAL);
            hit2.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performAction(this.name, hit2);

        } else if (normalAttackStep == 3) { // N4 (4 Hits)
            double mv = getTalentValue("N4", 0.259);
            for (int i = 1; i <= 4; i++) {
                AttackAction hit = new AttackAction(name + "_" + i, mv, Element.PHYSICAL, StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS, (i == 4) ? dur : 0.1, ActionType.NORMAL);
                hit.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
                if (i < 4)
                    sim.performActionWithoutTimeAdvance(this.name, hit);
                else
                    sim.performAction(this.name, hit);
            }

        } else {
            // N1, N2, N5
            double mv = getTalentValue(baseKey, 0.5);
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
        double mv = getTalentValue("CA", 2.24);
        AttackAction hit = new AttackAction("Xiangling CA", mv, Element.PHYSICAL, StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS, 0.8, ActionType.CHARGE);
        hit.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performAction(this.name, hit);

        normalAttackStep = 0;
    }

    private void plunge(CombatSimulator sim) {
        double mv = getTalentValue("Plunge High", 2.93);
        AttackAction p = new AttackAction("Xiangling Plunge", mv, Element.PHYSICAL, StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS, 1.0, ActionType.PLUNGE);
        p.setICD(ICDType.Standard, ICDTag.None, 1.0);
        sim.performAction(this.name, p);
    }
}
