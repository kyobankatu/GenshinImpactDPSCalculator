package model.character;

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
import simulation.event.PeriodicDamageEvent;

/**
 * Xingqiu character implementation with Raincutter sword-wave scheduling.
 */
public class Xingqiu extends Character implements FormStateProvider {
    private static final double ORBITAL_APPLICATION_INTERVAL = 2.25;
    private static final double RAINCUTTER_BASE_DURATION = 15.0;
    private static final double RAINCUTTER_C2_DURATION = 18.0;
    private static final double RAINCUTTER_TRIGGER_COOLDOWN = 1.0;
    private static final double C2_HYDRO_SHRED_DURATION = 4.0;

    private int normalAttackStep = 0;
    private final java.util.Map<BuffId, Double> triggerCooldowns = new java.util.HashMap<>();
    private int raincutterWaveCount = 0;
    private int raincutterBurstGeneration = 0;
    private PeriodicDamageEvent raincutterOrbitalEvent;

    /**
     * Constructs Xingqiu with the shared talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     */
    public Xingqiu(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    /**
     * Constructs Xingqiu with an explicit talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent values backing this character
     */
    public Xingqiu(Weapon weapon, ArtifactSet artifacts, TalentDataSource talentData) {
        super(talentData);
        this.name = "Xingqiu";
        this.characterId = CharacterId.XINGQIU;

        double baseAtk = getTalentValue("Base ATK", 202);
        double ascAtk = getTalentValue("Ascension ATK%", 0.24);

        baseStats.set(StatType.BASE_ATK, baseAtk);
        baseStats.add(StatType.ATK_PERCENT, ascAtk);
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.element = Element.HYDRO;
        this.constellation = (int) getTalentValue("Constellation", 6.0);
        setSkillCD(21.0);
        setBurstCD(20.0);
    }

    /**
     * Returns Xingqiu's burst energy cost.
     *
     * @return burst cost in energy
     */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80);
    }

    /**
     * Returns whether Raincutter is currently active.
     *
     * @param currentTime current simulation time in seconds
     * @return {@code true} while Xingqiu's burst duration remains
     */
    @Override
    public boolean isFormActive(double currentTime) {
        return currentTime - getLastBurstTime() < getRaincutterDuration();
    }

    /**
     * Applies Xingqiu's passive Hydro damage bonus.
     *
     * @param stats stats container to modify
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        double hydroBonus = getTalentValue("Hydro Bonus", 0.20);
        stats.add(StatType.HYDRO_DMG_BONUS, hydroBonus);
    }

    /**
     * Executes the requested combat action for Xingqiu.
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
        if (constellation >= 4 && isFormActive(sim.getCurrentTime())) {
            mvMulti = 1.5;
            System.out.println("   [Xingqiu] C4 Activation: Skill DMG x1.5");
        }

        double mv1 = getTalentValue("Rain Screen Hit 1", 2.86);
        AttackAction hit1 = new AttackAction("Fatal Rainscreen Hit 1", mv1 * mvMulti, Element.HYDRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.5, ActionType.SKILL);
        hit1.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);
        sim.performAction(this.characterId, hit1);

        double mv2 = getTalentValue("Rain Screen Hit 2", 3.25);
        AttackAction hit2 = new AttackAction("Fatal Rainscreen Hit 2", mv2 * mvMulti, Element.HYDRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.5, ActionType.SKILL);
        hit2.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);
        sim.performAction(this.characterId, hit2);

        // Generate 5 Hydro Particles
        sim.getEnergyDistributor().distributeParticles(Element.HYDRO, 5.0, mechanics.energy.ParticleType.PARTICLE);
    }

    private void burst(CombatSimulator sim) {
        raincutterBurstGeneration++;
        int burstGeneration = raincutterBurstGeneration;
        raincutterWaveCount = 0;
        triggerCooldowns.clear();

        AttackAction cast = new AttackAction("Raincutter Cast", 0.0, Element.HYDRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 1.0, ActionType.BURST);
        sim.performAction(this.characterId, cast);

        double raincutterDuration = getRaincutterDuration();
        sim.applyTeamBuffNoStack(new mechanics.buff.SimpleBuff(
                "Raincutter", BuffId.RAINCUTTER, raincutterDuration, sim.getCurrentTime(), stats -> {
                }).sourcedBy(characterId));

        AttackAction orbital = new AttackAction("Raincutter Orbital", 0.0, Element.HYDRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.0, false, ActionType.OTHER);
        // The contact pulse's 2.25s event cadence is its ICD; every pulse applies 1U Hydro.
        orbital.setICD(ICDType.None, ICDTag.Xingqiu_Orbital, 1.0);

        if (raincutterOrbitalEvent != null) {
            raincutterOrbitalEvent.cancel();
        }
        raincutterOrbitalEvent = new PeriodicDamageEvent(
                "Xingqiu",
                orbital,
                sim.getCurrentTime(),
                ORBITAL_APPLICATION_INTERVAL,
                raincutterDuration);
        sim.registerEvent(raincutterOrbitalEvent);

        // Register Raincutter Trigger (Self-contained)
        double expiryTime = sim.getCurrentTime() + raincutterDuration;
        final Xingqiu self = this;

        sim.addListener((actor, action, time) -> {
            if (burstGeneration != self.raincutterBurstGeneration || time >= expiryTime) {
                return;
            }

            // Trigger on Normal Attacks (Atomic)
            if (action.getActionType() == model.type.ActionType.NORMAL) {
                // Check internal CD (1.0s)
                Double lastTrigger = self.triggerCooldowns.get(BuffId.RAINCUTTER);
                if (lastTrigger == null || time - lastTrigger >= RAINCUTTER_TRIGGER_COOLDOWN) {
                    // Fire Raincutter (Multiple Swords)
                    int waveCount = self.raincutterWaveCount++;
                    java.util.List<AttackAction> rainSwords = self.getRaincutterAttack(waveCount);

                    System.out.println(
                            "   [Trigger] Raincutter Wave (" + rainSwords.size() + " Swords) on " + action.getName());

                    // Fire swords
                    for (AttackAction sword : rainSwords) {
                        sim.performActionWithoutTimeAdvance(self.getCharacterId(), sword);
                    }

                    if (self.constellation >= 2) {
                        sim.applyTeamBuffNoStack(new mechanics.buff.SimpleBuff(
                                "Xingqiu C2 Hydro RES Shred",
                                BuffId.XINGQIU_C2_HYDRO_SHRED,
                                C2_HYDRO_SHRED_DURATION,
                                time,
                                stats -> stats.add(StatType.HYDRO_RES_SHRED, 0.15))
                                .sourcedBy(self.getCharacterId()));
                    }
                    if (self.constellation >= 6 && waveCount % 3 == 2) {
                        self.receiveFlatEnergy(3.0);
                        System.out.println("   [Energy] Xingqiu C6 restored 3 Energy");
                    }
                    self.triggerCooldowns.put(BuffId.RAINCUTTER, time);
                }
            }
        });
    }

    private double getRaincutterDuration() {
        return constellation >= 2 ? RAINCUTTER_C2_DURATION : RAINCUTTER_BASE_DURATION;
    }

    /**
     * Builds the next Raincutter sword wave for the given burst trigger count.
     *
     * @param waveCount zero-based Raincutter trigger count
     * @return attack actions to fire for that wave
     */
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

        double mvPerSword = getTalentValue("Raincutter Sword", 0.923);

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
            double mv1 = getTalentValue("N3_1", 0.525);
            double mv2 = getTalentValue("N3_2", 0.525);

            AttackAction hit1 = new AttackAction(name + "_1", mv1, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, 0.15, ActionType.NORMAL);
            hit1.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performActionWithoutTimeAdvance(this.name, hit1);

            AttackAction hit2 = new AttackAction(name + "_2", mv2, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, 0.2, ActionType.NORMAL);
            hit2.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performAction(this.characterId, hit2);

        } else if (normalAttackStep == 4) { // N5
            double mv1 = getTalentValue("N5_1", 0.659);
            double mv2 = getTalentValue("N5_2", 0.659);

            AttackAction hit1 = new AttackAction(name + "_1", mv1, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, 0.2, ActionType.NORMAL);
            hit1.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performActionWithoutTimeAdvance(this.name, hit1);

            AttackAction hit2 = new AttackAction(name + "_2", mv2, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, 0.3, ActionType.NORMAL);
            hit2.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performAction(this.characterId, hit2);

        } else {
            // Single Hit
            double mv = getTalentValue(baseKey, 0.5);
            AttackAction hit = new AttackAction(name, mv, Element.PHYSICAL, StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS, dur, ActionType.NORMAL);
            hit.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
            sim.performAction(this.characterId, hit);
        }

        normalAttackStep++;
        if (normalAttackStep >= 5)
            normalAttackStep = 0;
    }

    private void chargeAttack(CombatSimulator sim) {
        double mv1 = getTalentValue("CA_1", 0.869);
        double mv2 = getTalentValue("CA_2", 1.03);

        AttackAction hit1 = new AttackAction("Xingqiu CA_1", mv1, Element.PHYSICAL, StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS, 0.2, ActionType.CHARGE);
        hit1.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performActionWithoutTimeAdvance(this.name, hit1);

        AttackAction hit2 = new AttackAction("Xingqiu CA_2", mv2, Element.PHYSICAL, StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS, 0.6, ActionType.CHARGE);
        hit2.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performAction(this.characterId, hit2);

        normalAttackStep = 0;
    }

    private void plunge(CombatSimulator sim) {
        double mv = getTalentValue("Plunge High", 2.93);
        AttackAction p = new AttackAction("Xingqiu Plunge", mv, Element.PHYSICAL, StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS, 1.0, ActionType.PLUNGE);
        p.setICD(ICDType.Standard, ICDTag.None, 1.0);
        sim.performAction(this.characterId, p);
    }
}
