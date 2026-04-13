package model.character;

import model.entity.Character;
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
import mechanics.energy.EnergyManager;
import mechanics.energy.ParticleType;

/**
 * Custom "Lunar" Electro character implementation.
 *
 * <p><b>Mechanics overview:</b>
 * <ul>
 *   <li><b>Enhanced Cleaning Module (Skill)</b> — Electro Skill hit; generates a shield
 *       scaled on ATK; summons Birgitta who fires periodic Electro hits every 2 s for
 *       20 s.  If Thundercloud is active when Birgitta fires, an additional Lunar-Charged
 *       hit (65% ATK) is triggered.</li>
 *   <li><b>Supreme Instruction (Burst)</b> — large Electro Burst hit; triggers the
 *       Reconstruction Protocol (P2) passive which grants 6% of Ineffa's ATK as flat EM
 *       to all party members for 20 s (on-field only for non-Ineffa members).</li>
 *   <li><b>Lunar Base Bonus team buff</b> — provides {@code LUNAR_BASE_BONUS} scaling
 *       with ATK: {@code min(0.14, ATK / 100 * 0.007)}.</li>
 * </ul>
 *
 * <p>Ineffa is a Lunar character ({@link #isLunarCharacter()} returns {@code true}).
 */
public class Ineffa extends Character {

    private int normalAttackStep = 0;
    private double shieldHealth = 0;

    /**
     * Constructs Ineffa with the given weapon and artifact set.
     * Initialises level-90 base stats (loaded from CSV), Electro element,
     * and cooldowns.
     *
     * @param weapon    equipped weapon
     * @param artifacts equipped artifact set
     */
    public Ineffa(Weapon weapon, ArtifactSet artifacts) {
        super();
        this.name = "Ineffa";
        this.characterId = CharacterId.INEFFA;

        // Stats to be filled by User via CSV
        baseStats.set(StatType.BASE_HP,
                mechanics.data.TalentDataManager.getInstance().get(this.name, "Base HP", 12613));
        baseStats.set(StatType.BASE_ATK,
                mechanics.data.TalentDataManager.getInstance().get(this.name, "Base ATK", 330));
        baseStats.set(StatType.BASE_DEF,
                mechanics.data.TalentDataManager.getInstance().get(this.name, "Base DEF", 828));
        baseStats.set(StatType.CRIT_RATE,
                mechanics.data.TalentDataManager.getInstance().get(this.name, "Base CR", 0.192));

        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.element = Element.ELECTRO;

        // Defaults
        this.constellation = 0;

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
    public boolean isBurstActive(double currentTime) {
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

        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, key, defaultMv);

        AttackAction hit = new AttackAction(name, mv, Element.PHYSICAL, StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS, 0.0, ActionType.NORMAL);

        hit.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
        hit.setAnimationDuration(0.3);
        sim.performAction(this.name, hit);

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
        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Skill DMG", 1.4688);

        AttackAction hit = new AttackAction("Enhanced Cleaning Module", mv, Element.ELECTRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL);
        hit.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0);
        hit.setAnimationDuration(0.6); // Cast Time
        sim.performAction(this.name, hit);

        // Shield Logic
        double atk = this.getEffectiveStats(sim.getCurrentTime()).getTotalAtk();
        double shieldRatio = mechanics.data.TalentDataManager.getInstance().get(this.name, "Shield Ratio", 3.76);
        double shieldFlat = mechanics.data.TalentDataManager.getInstance().get(this.name, "Shield Flat", 2820);
        this.shieldHealth = atk * shieldRatio + shieldFlat;
        if (sim.isLoggingEnabled()) {
            System.out.println("Ineffa Shield Generated: " + (int) this.shieldHealth + " HP");
        }

        // Summon Birgitta (20s, tick 2s)
        // Birgitta Discharge DMG: 163.2%
        double birgittaMv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Birgitta DMG", 1.632);

        sim.registerEvent(new PeriodicDamageEvent(
                this.name, // Source must be a registered character
                new AttackAction("Birgitta Discharge", birgittaMv, Element.ELECTRO, StatType.BASE_ATK,
                        StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL),
                sim.getCurrentTime() + 2.0,
                2.0,
                20.0,
                s -> {
                    // Passive 1 (Overclocking Circuit)
                    // Condition: Thundercloud is active (time-based state from Lunar-Charged).
                    if (s.isThundercloudActive()) {

                        // "Initiate an additional attack... 65% ATK... considered Lunar-Charged DMG"
                        double ocMv = 0.65;
                        AttackAction oc = new AttackAction("Overclock (Lunar)", ocMv, Element.ELECTRO,
                                StatType.BASE_ATK, StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL);

                        // Critical: Mark as Lunar-Charged
                        oc.setLunarReactionType(AttackAction.LunarReactionType.CHARGED);

                        s.performAction(this.name, oc);
                    }

                    // Generate Particles (1 per hit)
                    EnergyManager.distributeParticles(Element.ELECTRO, 0.667, ParticleType.PARTICLE, s);
                }));
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
        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Burst DMG", 11.506); // Lv9

        AttackAction hit = new AttackAction("Supreme Instruction", mv, Element.ELECTRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.0, false, ActionType.BURST);
        hit.setICD(ICDType.None, ICDTag.ElementalBurst, 2.0); // 2U usually
        hit.setAnimationDuration(1.7);
        sim.performAction(this.name, hit);

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
                });
            } else {
                buffToApply = new ActiveCharacterBuff("Reconstruction Protocol (P2)",
                        BuffId.RECONSTRUCTION_PROTOCOL_P2, 20.0, sim.getCurrentTime(),
                        sim, m, st -> {
                            st.add(StatType.ELEMENTAL_MASTERY, buffVal);
                        });
            }
            m.addBuff(buffToApply);
        }
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
        });
        return buffs;
    }
}
