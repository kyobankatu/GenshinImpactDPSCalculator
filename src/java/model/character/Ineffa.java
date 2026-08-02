package model.character;

import model.entity.FormStateProvider;
import model.entity.Character;
import model.entity.CharacterTeamBuffProvider;
import model.entity.ReactionAwareCharacter;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.Weapon;
import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.ICDType;
import model.type.ICDTag;
import model.type.ActionType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.event.PeriodicDamageEvent;
import mechanics.buff.SimpleBuff;
import mechanics.buff.Buff;
import mechanics.buff.ActiveCharacterBuff;
import mechanics.buff.BuffId;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.EnergyManager;
import mechanics.energy.ParticleType;
import mechanics.formula.DamageCalculator;
import mechanics.reaction.ReactionResult;

/**
 * Custom "Lunar" Electro character implementation.
 *
 * <p><b>Mechanics overview:</b>
 * <ul>
 *   <li><b>Enhanced Cleaning Module (Skill)</b> — Electro Skill hit; generates a shield
 *       scaled on ATK; summons Birgitta who fires periodic Electro hits every 2 s for
 *       20 s.  If Thundercloud is active when Birgitta fires, an additional Lunar-Charged
 *       hit (65% ATK) is triggered.</li>
 *   <li><b>Supreme Instruction (Burst)</b> — large Electro Burst hit; refreshes
 *       Birgitta and triggers the Reconstruction Protocol (P2) passive which grants
 *       6% of Ineffa's ATK as flat EM to all party members for 20 s (on-field only
 *       for non-Ineffa members).</li>
 *   <li><b>Lunar Base Bonus team buff</b> — provides {@code LUNAR_BASE_BONUS} scaling
 *       with ATK: {@code min(0.14, ATK / 100 * 0.007)}.</li>
 * </ul>
 *
 * <p>Ineffa is a Lunar character ({@link #isLunarCharacter()} returns {@code true}).
 */
public class Ineffa extends Character
        implements FormStateProvider, CharacterTeamBuffProvider,
        ReactionAwareCharacter, SimulatorInitializedCharacterEffect {

    private int normalAttackStep = 0;
    private double shieldHealth = 0;
    private PeriodicDamageEvent birgittaEvent;
    private CombatSimulator initializedSimulator;

    /**
     * Constructs Ineffa with the given weapon and artifact set.
     * Initialises level-90 base stats (loaded from CSV), Electro element,
     * and cooldowns.
     *
     * @param weapon    equipped weapon
     * @param artifacts equipped artifact set
     */
    public Ineffa(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    public Ineffa(Weapon weapon, ArtifactSet artifacts, TalentDataSource talentData) {
        super(talentData);
        this.name = "Ineffa";
        this.characterId = CharacterId.INEFFA;

        // Stats to be filled by User via CSV
        baseStats.set(StatType.BASE_HP, getTalentValue("Base HP", 12613));
        baseStats.set(StatType.BASE_ATK, getTalentValue("Base ATK", 330));
        baseStats.set(StatType.BASE_DEF, getTalentValue("Base DEF", 828));
        baseStats.set(StatType.CRIT_RATE, getTalentValue("Base CR", 0.192));

        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.element = Element.ELECTRO;

        this.constellation = (int) getTalentValue("Constellation", 0);

        setSkillCD(16.0);
        setBurstCD(15.0);
    }

    /**
     * Returns the burst energy cost (60).
     */
    @Override
    public double getEnergyCost() {
        return 60;
    }

    /**
     * Returns {@code false}; Ineffa's burst is instant / summon-based with no
     * persistent active state.
     */
    @Override
    public boolean isFormActive(double currentTime) {
        return false; // Burst is instant/summon based
    }

    /**
     * Returns {@code true}; Ineffa is a Lunar character and benefits from
     * Lunar synergy buffs.
     */
    @Override
    public boolean isLunarCharacter() {
        return true;
    }

    /**
     * No static passive stat modifications for Ineffa; reaction modifiers and
     * team buffs are applied on action.
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Passives are mainly reaction modifiers or team buffs applied on action
    }

    /**
     * Binds Ineffa's constellation listeners before the first party action.
     *
     * @param sim simulator receiving this character
     * @throws IllegalStateException if this character is reused across simulators
     */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Ineffa cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;
        if (constellation >= 4) {
            sim.addReactionListener(this);
        }
    }

    /**
     * Handles typed action requests dispatched by the combat simulator.
     *
     * <p>Supported actions:
     * <ul>
     *   <li>{@link CharacterActionKey#SKILL} — casts the Enhanced Cleaning Module.</li>
     *   <li>{@link CharacterActionKey#BURST} — casts Supreme Instruction.</li>
     *   <li>{@link CharacterActionKey#NORMAL} — advances the normal attack combo.</li>
     * </ul>
     *
     * @param request typed action request
     * @param sim the combat simulator context
     */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        initializeForSimulator(sim);
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
            default:
                break;
        }
    }

    /**
     * Executes the current normal attack step (4-hit Physical combo).
     *
     * @param sim the combat simulator context
     */
    private void normalAttack(CombatSimulator sim) {
        String key = "N" + (normalAttackStep + 1);
        String name = "Ineffa " + key;

        double defaultMv = 0.5;
        switch (normalAttackStep) {
            case 0:
                defaultMv = 0.640;
                break; // N1
            case 1:
                defaultMv = 0.629;
                break; // N2
            case 2:
                defaultMv = 0.418;
                break; // N3
            case 3:
                defaultMv = 1.030;
                break; // N4
        }

        double mv = getTalentValue(key, defaultMv);

        AttackAction hit = new AttackAction(name, mv, Element.PHYSICAL, StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS, 0.0, ActionType.NORMAL);

        hit.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
        hit.setAnimationDuration(0.3);
        sim.performAction(this.characterId, hit);

        normalAttackStep++;
        if (normalAttackStep >= 4)
            normalAttackStep = 0;
    }

    /**
     * Casts the Enhanced Cleaning Module Skill: fires an Electro hit, calculates
     * and logs the shield health, then registers Birgitta as a periodic Electro
     * damage source every 2 s for 20 s.  If Thundercloud is active on a Birgitta
     * tick, an additional Lunar-Charged hit is fired.
     *
     * @param sim the combat simulator context
     */
    private void skill(CombatSimulator sim) {
        // Reduced Cleaning Module
        // Skill DMG: 146.88% (Lv9)
        String skillSuffix = constellation >= 3 ? " C3" : "";
        double mv = getTalentValue(
                "Skill DMG" + skillSuffix,
                constellation >= 3 ? 1.7280 : 1.4688);

        AttackAction hit = new AttackAction("Enhanced Cleaning Module", mv, Element.ELECTRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL);
        hit.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);
        hit.setAnimationDuration(0.6); // Cast Time
        sim.performAction(this.characterId, hit);

        activateOpticalFlowShield(sim, hit);

        refreshBirgitta(sim);
    }

    private void activateOpticalFlowShield(
            CombatSimulator sim,
            AttackAction sourceAction) {
        double currentTime = sim.getCurrentTime();
        double atk = DamageCalculator.resolveStats(
                this,
                sourceAction,
                sim.getApplicableBuffs(this),
                currentTime).getTotalAtk();
        String skillSuffix = constellation >= 3 ? " C3" : "";
        double shieldRatio = getTalentValue(
                "Shield Ratio" + skillSuffix,
                constellation >= 3 ? 4.423680 : 3.760128);
        double shieldFlat = getTalentValue(
                "Shield Flat" + skillSuffix,
                constellation >= 3 ? 3547.8796 : 2819.7734);
        shieldHealth = atk * shieldRatio + shieldFlat;
        if (constellation >= 1) {
            double lunarChargedBonus = Math.min(0.50, (atk / 100.0) * 0.025);
            sim.applyTeamBuffNoStack(new SimpleBuff(
                    "Carrier Flow Composite",
                    BuffId.INEFFA_C1_CARRIER_FLOW_COMPOSITE,
                    20.0,
                    currentTime,
                    stats -> stats.add(
                            StatType.LUNAR_CHARGED_DMG_BONUS,
                            lunarChargedBonus))
                    .sourcedBy(characterId));
        }
        if (sim.isLoggingEnabled()) {
            System.out.println("Ineffa Shield Generated: " + (int) shieldHealth + " HP");
        }
    }

    /**
     * Returns the most recently generated Optical Flow Shield absorption.
     *
     * @return shield absorption value
     */
    public double getShieldHealth() {
        return shieldHealth;
    }

    /**
     * Replaces the current Birgitta summon with one ten-hit, 20-second stream.
     *
     * @param sim active combat simulator
     */
    private void refreshBirgitta(CombatSimulator sim) {
        String skillSuffix = constellation >= 3 ? " C3" : "";
        double birgittaMv = getTalentValue(
                "Birgitta DMG" + skillSuffix,
                constellation >= 3 ? 1.9200 : 1.6320);

        AttackAction birgittaDischarge = new AttackAction("Birgitta Discharge", birgittaMv, Element.ELECTRO,
                StatType.BASE_ATK, StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL);
        birgittaDischarge.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);

        if (birgittaEvent != null) {
            birgittaEvent.cancel();
        }
        birgittaEvent = new PeriodicDamageEvent(
                this.name, // Source must be a registered character
                birgittaDischarge,
                sim.getCurrentTime() + 2.0,
                2.0,
                18.0,
                s -> {
                    // Passive 1 (Overclocking Circuit)
                    // Condition: Thundercloud is active (time-based state from Lunar-Charged).
                    if (s.isThundercloudActive()) {

                        // "Initiate an additional attack... 65% ATK... considered Lunar-Charged DMG"
                        double ocMv = 0.65;
                        AttackAction oc = new AttackAction("Overclock (Lunar)", ocMv, Element.ELECTRO,
                                StatType.BASE_ATK, StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL);
                        oc.setICD(ICDType.None, ICDTag.None, 0.0);

                        // Critical: Mark as Lunar-Charged
                        oc.setLunarReactionType(AttackAction.LunarReactionType.CHARGED);

                        s.performAction(this.characterId, oc);
                    }

                    // Generate Particles (1 per hit)
                    s.getEnergyDistributor().distributeParticles(Element.ELECTRO, 0.667, ParticleType.PARTICLE);
                });
        sim.registerEvent(birgittaEvent);
    }

    /**
     * Casts Supreme Instruction: a large Electro Burst hit.  Then applies the
     * Reconstruction Protocol (P2) buff to all party members — a flat EM bonus
     * equal to 6% of Ineffa's current ATK, lasting 20 s.  Non-Ineffa members
     * receive it as an {@link ActiveCharacterBuff} (on-field only).
     *
     * @param sim the combat simulator context
     */
    private void burst(CombatSimulator sim) {
        // Supreme Instruction
        String burstKey = constellation >= 5 ? "Burst DMG C5" : "Burst DMG";
        double mv = getTalentValue(
                burstKey,
                constellation >= 5 ? 13.5360 : 11.5056);

        AttackAction hit = new AttackAction("Supreme Instruction", mv, Element.ELECTRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.0, false, ActionType.BURST);
        hit.setICD(ICDType.None, ICDTag.ElementalBurst, 2.0); // 2U usually
        hit.setAnimationDuration(1.7);
        sim.performAction(this.characterId, hit);

        if (constellation >= 2) {
            // Punishment Edict damage remains blocked until its delay is sourced.
            activateOpticalFlowShield(sim, hit);
        }

        refreshBirgitta(sim);

        // Passive 2: Reconstruction Protocol (Team EM Buff)
        double myAtk = this.getEffectiveStats(sim.getCurrentTime()).getTotalAtk();
        double buffVal = myAtk * 0.06;

        for (model.entity.Character m : sim.getPartyMembers()) {
            if (m.hasBuff(BuffId.RECONSTRUCTION_PROTOCOL_P2)) {
                m.removeBuff(BuffId.RECONSTRUCTION_PROTOCOL_P2);
            }

            Buff buffToApply;
            // Ineffa (the wearer) always gets it.
            // Others only get it while they are the active character.
            if (m == this) {
                buffToApply = new SimpleBuff("Reconstruction Protocol (P2)", BuffId.RECONSTRUCTION_PROTOCOL_P2,
                        20.0, sim.getCurrentTime(), st -> {
                    st.add(StatType.ELEMENTAL_MASTERY, buffVal);
                }).sourcedBy(this.getCharacterId());
            } else {
                buffToApply = new ActiveCharacterBuff("Reconstruction Protocol (P2)",
                        BuffId.RECONSTRUCTION_PROTOCOL_P2, 20.0, sim.getCurrentTime(),
                        sim, m, st -> {
                            st.add(StatType.ELEMENTAL_MASTERY, buffVal);
                        }).sourcedBy(this.getCharacterId());
            }
            m.addBuff(buffToApply);
        }
    }

    /**
     * Restores C4 Energy on real Lunar-Charged reaction triggers.
     *
     * @param result resolved reaction
     * @param source triggering character
     * @param time reaction time
     * @param sim active simulator
     */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (constellation >= 6
                && result.getKind() == ReactionResult.Kind.THUNDERCLOUD_STRIKE
                && result.getTransformDamage() > 0.0
                && hasActiveCarrierFlowComposite(sim, time)
                && !hasActiveBuff(BuffId.INEFFA_C6_FOLLOW_UP_COOLDOWN, time)) {
            triggerC6FollowUp(sim, time);
        }
        if (constellation < 4
                || result.getKind() != ReactionResult.Kind.LUNAR_CHARGED
                || result.getTransformDamage() <= 0.0
                || hasActiveBuff(BuffId.INEFFA_C4_ENERGY_COOLDOWN, time)) {
            return;
        }
        receiveFlatEnergy(5.0);
        removeBuff(BuffId.INEFFA_C4_ENERGY_COOLDOWN);
        addBuff(new SimpleBuff(
                "The Edictless Path Cooldown",
                BuffId.INEFFA_C4_ENERGY_COOLDOWN,
                4.0,
                time,
                stats -> {
                }).sourcedBy(characterId));
    }

    private void triggerC6FollowUp(CombatSimulator sim, double time) {
        removeBuff(BuffId.INEFFA_C6_FOLLOW_UP_COOLDOWN);
        addBuff(new SimpleBuff(
                "A Dawning Morn for You Cooldown",
                BuffId.INEFFA_C6_FOLLOW_UP_COOLDOWN,
                3.5,
                time,
                stats -> {
                }).sourcedBy(characterId));

        AttackAction followUp = new AttackAction(
                "A Dawning Morn for You",
                1.35,
                Element.ELECTRO,
                StatType.BASE_ATK,
                null,
                0.0,
                false,
                ActionType.OTHER);
        followUp.setICD(ICDType.None, ICDTag.None, 0.0);
        followUp.setLunarReactionType(AttackAction.LunarReactionType.CHARGED);
        sim.performActionWithoutTimeAdvance(characterId, followUp);
    }

    private boolean hasActiveCarrierFlowComposite(
            CombatSimulator sim,
            double currentTime) {
        for (Buff buff : sim.getTeamBuffList()) {
            if (buff.getId() == BuffId.INEFFA_C1_CARRIER_FLOW_COMPOSITE
                    && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveBuff(BuffId id, double currentTime) {
        for (Buff buff : getActiveBuffs()) {
            if (buff.getId() == id && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the permanent team buffs provided by Ineffa.
     *
     * <p>Includes a passive {@code LUNAR_BASE_BONUS} buff scaling with ATK:
     * {@code min(0.14, ATK / 100 * 0.007)}.
     *
     * @return list containing the Lunar Base Bonus buff
     */
    @Override
    public java.util.List<mechanics.buff.Buff> getTeamBuffs() {
        java.util.List<mechanics.buff.Buff> buffs = new java.util.ArrayList<>();
        buffs.add(new mechanics.buff.Buff("Ineffa: Lunar Base Bonus", BuffId.INEFFA_LUNAR_BASE_BONUS,
                Double.MAX_VALUE, 0) {
            @Override
            protected void applyStats(StatsContainer stats, double currentTime) {
                // Use structural stats to avoid recursion
                double atk = Ineffa.this.getStructuralStats(currentTime).getTotalAtk();
                double bonus = Math.min(0.14, (atk / 100.0) * 0.007);
                // System.out.println("[INEFFA_DEBUG] ATK:" + atk + " Bonus:" + bonus);
                stats.add(StatType.LUNAR_BASE_BONUS, bonus);
            }
        }.sourcedBy(this.getCharacterId()));
        return buffs;
    }
}
