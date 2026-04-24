package model.character;

import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import model.entity.BurstStateProvider;
import model.entity.Character;
import model.entity.SwitchAwareCharacter;
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

public class RaidenShogun extends Character implements BurstStateProvider, SwitchAwareCharacter {

    private int normalAttackStep = 0;
    private double resolveStacks = 0;
    private double activeResolveBonus = 0; // Stacks captured at Burst cast
    private boolean listenerRegistered = false;

    public RaidenShogun(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    public RaidenShogun(Weapon weapon, ArtifactSet artifacts, TalentDataSource talentData) {
        super(talentData); // Init base stats
        this.name = "Raiden Shogun";
        this.characterId = CharacterId.RAIDEN_SHOGUN;
        baseStats.set(StatType.BASE_ATK, 337);
        baseStats.add(StatType.ENERGY_RECHARGE, 0.32); // Ascension Lv90
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.element = Element.ELECTRO;
        this.constellation = (int) getTalentValue("Constellation", 6.0);
        setSkillCD(10.0);
        setBurstCD(18.0);
    }

    @Override
    public double getEnergyCost() {
        return 90;
    }

    @Override
    public boolean isBurstActive(double currentTime) {
        return musouActive;
    }

    @Override
    public void onSwitchOut(CombatSimulator sim) {
        if (musouActive) {
            System.out.println("   [Raiden] Swapped out! Musou Shinsetsu ends early.");
            musouActive = false;
            // Note: The TimerEvent will still fire but musouActive is false, so it's fine.
            // Ideally we should cancel the timer, but setting boolean is sufficient.
        }
    }

    @Override
    public void applyPassive(StatsContainer stats) {
        double er = stats.get(StatType.ENERGY_RECHARGE);
        if (er > 1.0) {
            double excess = er - 1.0;
            stats.add(StatType.ELECTRO_DMG_BONUS, excess * 0.4);
        }
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
                normalAttackStep = 0; // Reset combo
                sim.advanceTime(0.4); // Dash time
                break;
            case PLUNGE:
                plunge(sim);
                break;
            default:
                throw new IllegalArgumentException("Unsupported action for Raiden Shogun: " + request.getKey());
        }
    }

    private double nextPassiveResolveTime = -10.0; // Ready immediately

    private void registerResolveListener(CombatSimulator sim) {
        if (!listenerRegistered) {
            // Burst Listener (Chakra Desiderata)
            sim.addListener((actor, action, time) -> {
                if (action.getActionType() == ActionType.BURST
                        && actor.getCharacterId() != this.characterId) {
                    double cost = actor.getEnergyCost();
                    double gain = cost * 0.2;
                    resolveStacks += gain;
                    if (resolveStacks > 60)
                        resolveStacks = 60;
                    System.out.println(String.format("   [Raiden] Gained Resolve: %.1f from %s (Total: %.1f)", gain,
                            actor.getName(), resolveStacks));
                }
            });

            // Passive: Wishes Unnumbered (Particles -> Resolve)
            sim.addParticleListener((ele, count, time) -> {
                if (time >= nextPassiveResolveTime) {
                    resolveStacks += 2.0;
                    if (resolveStacks > 60)
                        resolveStacks = 60;
                    nextPassiveResolveTime = time + 3.0; // 3s CD
                    System.out.println(String.format(
                            "   [Raiden] Passive (Wishes Unnumbered): +2.0 Resolve (Total: %.1f)", resolveStacks));
                }
            });

            listenerRegistered = true;
            System.out.println("   [Raiden] Resolve Listeners Registered.");
        }
    }

    private boolean musouActive = false;
    private double musouEnergyCount = 0;
    private double nextEnergyRestoreTime = 0;

    private void skill(CombatSimulator sim) {
        registerResolveListener(sim);
        double mv = getTalentValue("Raiden E Cast", 2.11);

        AttackAction e = new AttackAction("Raiden E Cast", mv, Element.ELECTRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.5, false, ActionType.SKILL); // Dynamic
        e.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0);
        sim.performAction(this.name, e);

        // Team Buff Logic (Eye of Stormy Judgment)
        for (Character c : sim.getPartyMembers()) {
            double cost = c.getEnergyCost();
            double burstBonus = cost * 0.003;
            c.addBuff(new mechanics.buff.SimpleBuff("Eye of Stormy Judgment", BuffId.RAIDEN_EYE_OF_STORMY_JUDGMENT,
                    25.0, sim.getCurrentTime(), s -> {
                s.add(StatType.BURST_DMG_BONUS, burstBonus);
            }));
        }

        // Particle Generation & Coordinated Attack
        // MV: 75.6% (Lv10)
        double coordMv = getTalentValue("Raiden E Coordinated", 0.756);
        AttackAction coordAttack = new AttackAction("Eye of Stormy Judgment", coordMv, Element.ELECTRO,
                StatType.BASE_ATK, StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL); // Dynamic
        coordAttack.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0); // 1U

        sim.registerEvent(new simulation.event.PeriodicDamageEvent(
                this.name, coordAttack, sim.getCurrentTime() + 0.9, 0.9, 25.0,
                s -> {
                    // 50% chance to generate 1 particle -> Average 0.5
                    s.getEnergyDistributor().distributeParticles(Element.ELECTRO, 0.5,
                            mechanics.energy.ParticleType.PARTICLE);
                }));
    }

    private void burst(CombatSimulator sim) {
        registerResolveListener(sim);

        // Capture Resolve
        activeResolveBonus = resolveStacks;
        System.out.println(String.format("   [Raiden] Burst Cast! Consuming Resolve: %.1f", activeResolveBonus));
        resolveStacks = 0; // Consumed

        // Reset Energy Restoration State
        musouActive = true;
        musouEnergyCount = 0;
        nextEnergyRestoreTime = sim.getCurrentTime(); // Ready immediately

        // Burst End Timer (7s)
        sim.registerEvent(new simulation.event.TimerEvent() {
            private double endTime = sim.getCurrentTime() + 7.0;
            private boolean done = false;

            @Override
            public void tick(CombatSimulator s) {
                musouActive = false;
                done = true;
            }

            @Override
            public boolean isFinished(double t) {
                return done;
            }

            @Override
            public double getNextTickTime() {
                return done ? -1 : endTime;
            }
        });

        double baseMv = getTalentValue("Musou Shinsetsu", 6.81);
        double stackScale = getTalentValue("Musou Shinsetsu.2", 0.0661);

        double mv = baseMv + (activeResolveBonus * stackScale);

        AttackAction q = new AttackAction("Musou Shinsetsu", mv, Element.ELECTRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 1.5, false, ActionType.BURST); // Raiden dynamic stats (No Snapshot)
        q.setICD(ICDType.Standard, ICDTag.ElementalBurst, 2.0); // 2U Application
        if (this.constellation >= 2) {
            q.setDefenseIgnore(0.60);
        }
        sim.performAction(this.name, q);
    }

    // Helper to trigger Energy Restoration on hits
    private void checkMusouEnergy(CombatSimulator sim) {
        if (musouActive && musouEnergyCount < 5 && sim.getCurrentTime() >= nextEnergyRestoreTime) {
            // Restore Energy
            // Base: 2.5 (Lv10/Lv9 matches 2.5 usually, Lv1 is 1.6)
            double base = getTalentValue("Musou Energy Base", 2.5);

            // Passive: Each 1% above 100% ER -> +0.6% Restoration
            double threshold = getTalentValue("Enlightened One Threshold", 1.0);
            double energyConv = getTalentValue("Enlightened One Energy Conv", 0.6);

            double er = getEffectiveStats(sim.getCurrentTime()).get(StatType.ENERGY_RECHARGE);
            double multiplier = 1.0;
            if (er > threshold) {
                multiplier += (er - threshold) * energyConv;
            }
            double amount = base * multiplier;

            sim.getEnergyDistributor().distributeFlatEnergy(amount);

            System.out.println(String.format("   [Raiden] Musou Energy: +%.1f (ER %.0f%%)", amount, er * 100));

            musouEnergyCount++;
            nextEnergyRestoreTime = sim.getCurrentTime() + 1.0;
        }
    }

    private void normalAttack(CombatSimulator sim) {
        checkMusouEnergy(sim); // Trigger Energy Check

        // N1-N5 Chain
        String stepName = "N" + (normalAttackStep + 1);
        double dur = 0.3;
        // Simple duration mapping
        switch (normalAttackStep) {
            case 0:
                dur = 0.25;
                break;
            case 1:
                dur = 0.35;
                break;
            case 2:
                dur = 0.35;
                break;
            case 3:
                dur = 0.45;
                break;
            case 4:
                dur = 0.65;
                break;
        }

        String key;
        double mv;
        ActionType type;
        boolean countsAsBurst;
        Element dmgElement;

        if (musouActive) {
            // Burst Mode (Electro)
            key = "Burst " + stepName;
            double baseMv = getTalentValue(key, 0.5);
            double scaling = getTalentValue("Resolve Normal Scaling", 0.0123);
            mv = baseMv + (activeResolveBonus * scaling);

            type = ActionType.NORMAL; // Still normal attack type for triggers
            countsAsBurst = true; // But benefits from Burst Dmg Bonus
            dmgElement = Element.ELECTRO;
            stepName = "Raiden Burst N" + (normalAttackStep + 1);
        } else {
            // Physical Mode
            key = stepName;
            mv = getTalentValue(key, 0.5);

            type = ActionType.NORMAL;
            countsAsBurst = false;
            dmgElement = Element.PHYSICAL; // Should update Character to support Physical defaults? Or just pass
                                           // ELEMENT.
            // Wait, Element.PHYSICAL exists?
            try {
                dmgElement = Element.valueOf("PHYSICAL");
            } catch (IllegalArgumentException e) {
                dmgElement = Element.PHYSICAL; // Fallback if enum exists, otherwise assume logic checks
            }
            stepName = "Raiden N" + (normalAttackStep + 1);
        }

        AttackAction a = new AttackAction(stepName, mv, dmgElement, StatType.BASE_ATK,
                countsAsBurst ? StatType.BURST_DMG_BONUS : StatType.PHYSICAL_DMG_BONUS,
                dur, false, type);

        if (countsAsBurst) {
            a.setCountsAsBurstDmg(true);
            if (this.constellation >= 2) {
                a.setDefenseIgnore(0.60);
            }
        }
        a.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
        sim.performAction(this.name, a);

        normalAttackStep++;
        if (normalAttackStep >= 5)
            normalAttackStep = 0;
    }

    private void chargeAttack(CombatSimulator sim) {
        checkMusouEnergy(sim);

        String key;
        double mv;
        boolean countsAsBurst;
        Element dmgElement;
        String actionName;

        if (musouActive) {
            // Burst CA (2 Hits)
            double base1 = getTalentValue("Burst CA_1", 1.036);
            double base2 = getTalentValue("Burst CA_2", 1.251);
            double scaling = getTalentValue("Resolve CA Scaling", 0.0123);

            double bonus = activeResolveBonus * scaling;
            mv = (base1 + bonus) + (base2 + bonus);

            countsAsBurst = true;
            dmgElement = Element.ELECTRO;
            actionName = "Raiden Burst CA";
        } else {
            // Physical CA (1 Hit)
            mv = getTalentValue("CA", 1.83);

            countsAsBurst = false;
            dmgElement = Element.PHYSICAL;
            actionName = "Raiden CA";
        }

        AttackAction ca = new AttackAction(actionName, mv, dmgElement, StatType.BASE_ATK,
                countsAsBurst ? StatType.BURST_DMG_BONUS : StatType.PHYSICAL_DMG_BONUS,
                0.8, false, ActionType.CHARGE);

        if (countsAsBurst) {
            ca.setCountsAsBurstDmg(true);
            if (this.constellation >= 2) {
                ca.setDefenseIgnore(0.60);
            }
        }
        ca.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performAction(this.name, ca);
        normalAttackStep = 0;
    }

    private void plunge(CombatSimulator sim) {
        // Default to High Plunge for sim simplicity unless specified
        String keySuffix = "Plunge High";
        String actionName = "Raiden Plunge";

        double mv;
        boolean countsAsBurst;
        Element dmgElement;

        if (musouActive) {
            mv = getTalentValue("Burst " + keySuffix, 2.93);
            double scaling = getTalentValue("Resolve Normal Scaling", 0.0123); // Use normal scaling?
            // User didn't specify plunge scaling, assuming same as Normal/CA for Musou
            // Isshin
            mv += (activeResolveBonus * scaling);

            countsAsBurst = true;
            dmgElement = Element.ELECTRO;
            actionName = "Raiden Burst Plunge";
        } else {
            mv = getTalentValue(keySuffix, 2.93);
            countsAsBurst = false;
            dmgElement = Element.PHYSICAL;
        }

        AttackAction p = new AttackAction(actionName, mv, dmgElement, StatType.BASE_ATK,
                countsAsBurst ? StatType.BURST_DMG_BONUS : StatType.PHYSICAL_DMG_BONUS,
                1.0, false, ActionType.PLUNGE); // Duration arbitrary

        if (countsAsBurst) {
            p.setCountsAsBurstDmg(true);
            if (this.constellation >= 2) {
                p.setDefenseIgnore(0.60);
            }
        }
        p.setICD(ICDType.Standard, ICDTag.None, 1.0); // Plunge usually no ICD or special?
        sim.performAction(this.name, p);
    }
}
