package model.character;

import java.util.EnumMap;
import java.util.Map;

import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import model.entity.FormStateProvider;
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
import simulation.event.SimpleTimerEvent;

/**
 * Raiden Shogun character implementation with Resolve stacking and Musou Isshin form logic.
 */
public class RaidenShogun extends Character implements FormStateProvider, SwitchAwareCharacter {

    private int normalAttackStep = 0;
    private double resolveStacks = 0;
    private double activeResolveBonus = 0; // Stacks captured at Burst cast
    private boolean listenerRegistered = false;
    private final Map<CharacterId, Double> creditedBurstCastTimes = new EnumMap<>(CharacterId.class);
    private int musouGeneration = 0;

    /**
     * Constructs Raiden Shogun with the shared talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     */
    public RaidenShogun(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    /**
     * Constructs Raiden Shogun with an explicit talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent values backing this character
     */
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

    /**
     * Returns Raiden Shogun's burst energy cost.
     *
     * @return burst cost in energy
     */
    @Override
    public double getEnergyCost() {
        return 90;
    }

    /**
     * Returns whether Musou Isshin is currently active.
     *
     * @param currentTime current simulation time in seconds
     * @return {@code true} while the burst form is active
     */
    @Override
    public boolean isFormActive(double currentTime) {
        return musouActive;
    }

    /**
     * Returns current Chakra Desiderata Resolve stacks.
     *
     * @return current Resolve stack count
     */
    public double getResolveStacks() {
        return resolveStacks;
    }

    /**
     * Ends Musou Isshin early when Raiden leaves the field.
     *
     * @param sim active combat simulator
     */
    @Override
    public void onSwitchOut(CombatSimulator sim) {
        if (musouActive) {
            System.out.println("   [Raiden] Swapped out! Musou Shinsetsu ends early.");
            endMusouIsshin(sim);
        }
    }

    /**
     * Applies Raiden Shogun's Electro damage bonus from excess Energy Recharge.
     *
     * @param stats stats container to modify
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        double er = stats.get(StatType.ENERGY_RECHARGE);
        if (er > 1.0) {
            double excess = er - 1.0;
            stats.add(StatType.ELECTRO_DMG_BONUS, excess * 0.4);
        }
    }

    /**
     * Executes the requested combat action for Raiden Shogun.
     *
     * @param request requested player action
     * @param sim active combat simulator
     */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        switch (request.getKey()) {
            case SKILL:
                markSkillUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                skill(sim);
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
            // Burst Listener (Chakra Desiderata). Action listeners see every
            // Burst-damage hit, so credit each character once per actual burst cast.
            sim.addListener((actor, action, time) -> {
                if (action.getActionType() == ActionType.BURST
                        && actor.getCharacterId() != this.characterId) {
                    double burstCastTime = actor.getLastBurstTime();
                    Double creditedTime = creditedBurstCastTimes.get(actor.getCharacterId());
                    if (creditedTime != null && Math.abs(creditedTime - burstCastTime) < 1e-9) {
                        return;
                    }
                    creditedBurstCastTimes.put(actor.getCharacterId(), burstCastTime);

                    double cost = actor.getEnergyCost();
                    double gain = cost * 0.2;
                    if (constellation >= 1) {
                        gain *= actor.getElement() == Element.ELECTRO ? 1.8 : 1.2;
                    }
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
    private boolean eyeDamageListenerRegistered = false;
    private AttackAction eyeCoordinatedAttack;
    private double eyeExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextEyeTriggerTime = Double.NEGATIVE_INFINITY;

    /**
     * Registers the one resolved-damage listener that drives Raiden's Eye.
     *
     * <p>The 0.9-second cooldown begins at triggering damage. Coordinated damage
     * is deferred to a same-timestamp event so the source action finishes before
     * the Eye resolves; exact in-game frame delay is intentionally not modeled.
     *
     * @param sim active combat simulator
     */
    private void registerEyeDamageListener(CombatSimulator sim) {
        if (eyeDamageListenerRegistered) {
            return;
        }
        sim.addDamageListener((actor, action, damage, time) -> {
            AttackAction activeEyeAttack = eyeCoordinatedAttack;
            if (activeEyeAttack == null || action == activeEyeAttack || damage <= 0.0) {
                return;
            }
            if (time >= eyeExpirationTime || time + 1e-9 < nextEyeTriggerTime) {
                return;
            }

            nextEyeTriggerTime = time + 0.9;
            sim.registerEvent(new SimpleTimerEvent(time, 1.0) {
                @Override
                public void onTick(CombatSimulator activeSim) {
                    activeSim.performActionWithoutTimeAdvance(characterId, activeEyeAttack);
                    activeSim.getEnergyDistributor().distributeParticles(
                            Element.ELECTRO, 0.5, mechanics.energy.ParticleType.PARTICLE);
                    finish();
                }
            });
        });
        eyeDamageListenerRegistered = true;
    }

    private void skill(CombatSimulator sim) {
        registerResolveListener(sim);
        double mv = getTalentValue("Raiden E Cast", 2.11);

        AttackAction e = new AttackAction("Raiden E Cast", mv, Element.ELECTRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.5, false, ActionType.SKILL); // Dynamic
        e.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);
        sim.performAction(this.characterId, e);

        // Team Buff Logic (Eye of Stormy Judgment)
        for (Character c : sim.getPartyMembers()) {
            double cost = c.getEnergyCost();
            double burstBonus = cost * 0.003;
            c.removeBuff(BuffId.RAIDEN_EYE_OF_STORMY_JUDGMENT);
            c.addBuff(new mechanics.buff.SimpleBuff("Eye of Stormy Judgment", BuffId.RAIDEN_EYE_OF_STORMY_JUDGMENT,
                    25.0, sim.getCurrentTime(), s -> {
                s.add(StatType.BURST_DMG_BONUS, burstBonus);
            }).sourcedBy(this.getCharacterId()));
        }

        // Coordinated Attack: 75.6% MV at Lv10.
        double coordMv = getTalentValue("Raiden E Coordinated", 0.756);
        AttackAction coordAttack = new AttackAction("Eye of Stormy Judgment", coordMv, Element.ELECTRO,
                StatType.BASE_ATK, StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL); // Dynamic
        coordAttack.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0); // 1U

        registerEyeDamageListener(sim);
        eyeCoordinatedAttack = coordAttack;
        eyeExpirationTime = sim.getCurrentTime() + 25.0;
        nextEyeTriggerTime = sim.getCurrentTime();
    }

    private void burst(CombatSimulator sim) {
        registerResolveListener(sim);

        // Capture Resolve
        activeResolveBonus = resolveStacks;
        System.out.println(String.format("   [Raiden] Burst Cast! Consuming Resolve: %.1f", activeResolveBonus));
        resolveStacks = 0; // Consumed

        // Reset Energy Restoration State
        musouActive = true;
        int activeGeneration = ++musouGeneration;
        musouEnergyCount = 0;
        nextEnergyRestoreTime = sim.getCurrentTime(); // Ready immediately

        double baseMv = getTalentValue("Musou Shinsetsu", 6.81);
        double stackScale = getTalentValue("Musou Shinsetsu.2", 0.0661);

        double mv = baseMv + (activeResolveBonus * stackScale);

        AttackAction q = new AttackAction("Musou Shinsetsu", mv, Element.ELECTRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 1.5, false, ActionType.BURST); // Raiden dynamic stats (No Snapshot)
        q.setICD(ICDType.None, ICDTag.ElementalBurst, 2.0);
        if (this.constellation >= 2) {
            q.setDefenseIgnore(0.60);
        }
        sim.performAction(this.characterId, q);

        // KQM records the seven-second Musou Isshin timer from cast-animation end.
        sim.registerEvent(new simulation.event.TimerEvent() {
            private final double endTime = sim.getCurrentTime() + 7.0;
            private boolean done = false;

            @Override
            public void tick(CombatSimulator activeSim) {
                if (activeGeneration == musouGeneration) {
                    endMusouIsshin(activeSim);
                }
                done = true;
            }

            @Override
            public boolean isFinished(double currentTime) {
                return done;
            }

            @Override
            public double getNextTickTime() {
                return done ? -1.0 : endTime;
            }
        });
    }

    private void endMusouIsshin(CombatSimulator sim) {
        if (!musouActive) {
            return;
        }
        musouActive = false;
        if (constellation < 4) {
            return;
        }
        sim.applyTeamBuffNoStack(new mechanics.buff.SimpleBuff(
                "Pledge of Propriety",
                BuffId.RAIDEN_C4_PLEDGE_OF_PROPRIETY,
                10.0,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.ATK_PERCENT, 0.30))
                .exclude(characterId)
                .sourcedBy(characterId));
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

        String actionName;
        double resolveBonus = 0.0;
        boolean countsAsBurst;
        Element dmgElement;

        if (musouActive) {
            double scaling = getTalentValue("Resolve Normal Scaling", 0.0123);
            resolveBonus = activeResolveBonus * scaling;
            countsAsBurst = true;
            dmgElement = Element.ELECTRO;
            actionName = "Raiden Burst N" + (normalAttackStep + 1);
        } else {
            countsAsBurst = false;
            dmgElement = Element.PHYSICAL;
            actionName = "Raiden N" + (normalAttackStep + 1);
        }

        if (normalAttackStep == 3) {
            String keyPrefix = countsAsBurst ? "Burst N4 Hit " : "N4 Hit ";
            double firstDefault = countsAsBurst ? 0.5195 : 0.5325;
            double secondDefault = countsAsBurst ? 0.5210 : 0.5325;
            AttackAction firstHit = createRaidenAttack(
                    actionName + " Hit 1",
                    getTalentValue(keyPrefix + "1", firstDefault) + resolveBonus,
                    dmgElement,
                    countsAsBurst,
                    ActionType.NORMAL,
                    0.0);
            AttackAction secondHit = createRaidenAttack(
                    actionName + " Hit 2",
                    getTalentValue(keyPrefix + "2", secondDefault) + resolveBonus,
                    dmgElement,
                    countsAsBurst,
                    ActionType.NORMAL,
                    dur);
            sim.performActionWithoutTimeAdvance(characterId, firstHit);
            sim.performAction(characterId, secondHit);
        } else {
            String key = countsAsBurst ? "Burst " + stepName : stepName;
            AttackAction hit = createRaidenAttack(
                    actionName,
                    getTalentValue(key, 0.5) + resolveBonus,
                    dmgElement,
                    countsAsBurst,
                    ActionType.NORMAL,
                    dur);
            sim.performAction(characterId, hit);
        }

        normalAttackStep++;
        if (normalAttackStep >= 5) {
            normalAttackStep = 0;
        }
    }

    private void chargeAttack(CombatSimulator sim) {
        checkMusouEnergy(sim);

        if (musouActive) {
            double base1 = getTalentValue("Burst CA_1", 1.036);
            double base2 = getTalentValue("Burst CA_2", 1.2506);
            double scaling = getTalentValue("Resolve CA Scaling", 0.0123);
            double bonus = activeResolveBonus * scaling;
            AttackAction firstHit = createRaidenAttack(
                    "Raiden Burst CA Hit 1",
                    base1 + bonus,
                    Element.ELECTRO,
                    true,
                    ActionType.CHARGE,
                    0.0);
            AttackAction secondHit = createRaidenAttack(
                    "Raiden Burst CA Hit 2",
                    base2 + bonus,
                    Element.ELECTRO,
                    true,
                    ActionType.CHARGE,
                    0.8);
            sim.performActionWithoutTimeAdvance(characterId, firstHit);
            sim.performAction(characterId, secondHit);
        } else {
            AttackAction physicalHit = createRaidenAttack(
                    "Raiden CA",
                    getTalentValue("CA", 1.83),
                    Element.PHYSICAL,
                    false,
                    ActionType.CHARGE,
                    0.8);
            sim.performAction(characterId, physicalHit);
        }
        normalAttackStep = 0;
    }

    /**
     * Creates one physical or Musou attack hit with its shared classification.
     *
     * <p>Musou multi-hit actions call this once per sourced multiplier so each
     * hit receives Burst classification, C2 DEF ignore, and the shared ICD
     * group independently while the caller assigns timeline advancement only
     * to the final hit.
     *
     * @param actionName display label for this hit
     * @param multiplier talent and Resolve multiplier for this hit
     * @param damageElement Physical or Electro damage element
     * @param countsAsBurst whether Musou Burst rules apply
     * @param actionType input action category retained for trigger behavior
     * @param animationDuration timeline duration in seconds for this hit
     * @return configured attack hit
     */
    private AttackAction createRaidenAttack(
            String actionName,
            double multiplier,
            Element damageElement,
            boolean countsAsBurst,
            ActionType actionType,
            double animationDuration) {
        AttackAction action = new AttackAction(
                actionName,
                multiplier,
                damageElement,
                StatType.BASE_ATK,
                countsAsBurst
                        ? StatType.BURST_DMG_BONUS
                        : StatType.PHYSICAL_DMG_BONUS,
                animationDuration,
                false,
                actionType);
        if (countsAsBurst) {
            action.setCountsAsBurstDmg(true);
            if (constellation >= 2) {
                action.setDefenseIgnore(0.60);
            }
        }
        ICDTag icdTag;
        if (countsAsBurst) {
            icdTag = ICDTag.Raiden_MusouIsshin;
        } else if (actionType == ActionType.CHARGE) {
            icdTag = ICDTag.ChargedAttack;
        } else {
            icdTag = ICDTag.NormalAttack;
        }
        action.setICD(ICDType.Standard, icdTag, 1.0);
        return action;
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
        sim.performAction(this.characterId, p);
    }
}
