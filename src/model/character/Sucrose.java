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
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.event.PeriodicDamageEvent;
import mechanics.buff.SimpleBuff;
import mechanics.energy.EnergyManager;
import mechanics.energy.ParticleType;

/**
 * Anemo Catalyst support character (Sucrose) implementation.
 *
 * <p><b>Mechanics overview:</b>
 * <ul>
 *   <li><b>Astable Anemohypostasis Creation-6308 (Skill)</b> — Anemo Skill hit;
 *       generates 4 Anemo particles; triggers A1 and A4 passives.</li>
 *   <li><b>Forbidden Creation-Isomer 75 / Type II (Burst)</b> — 6 s (8 s at C2)
 *       periodic Anemo DoT; absorbs an element from the enemy aura on first tick and
 *       fires an additional absorbed-element hit each subsequent tick.  Applies C6
 *       elemental DMG bonus when absorbed. A4 and A1 passives fire each tick.</li>
 *   <li><b>Mollis Favonius (A4)</b> — on Skill/Burst use Sucrose transfers 20% of her
 *       own EM to all non-Sucrose party members for 8 s.</li>
 *   <li><b>Catalyst Conversion (A1)</b> — if a swirl occurs, party members matching
 *       the swirled element gain +50 EM for 8 s.</li>
 *   <li><b>C6</b> — absorbed element gains +20% elemental DMG for the remaining
 *       burst duration.</li>
 * </ul>
 *
 * <p>C1 grants a second Skill charge (max 2); C2 extends burst duration to 8 s.
 */
public class Sucrose extends Character implements BurstStateProvider {

    private int normalAttackStep = 0;

    // Burst State
    private Element absorbedElement = null;

    /**
     * Constructs Sucrose with the given weapon and artifact set.
     * Initialises level-90 base stats (loaded from CSV), Anemo element,
     * constellation 6, and cooldowns.  C1 sets {@code skillMaxCharges} to 2.
     *
     * @param weapon    equipped weapon
     * @param artifacts equipped artifact set
     */
    public Sucrose(Weapon weapon, ArtifactSet artifacts) {
        super();
        this.name = "Sucrose";
        this.characterId = CharacterId.SUCROSE;

        // Level 90 Base Stats
        baseStats.set(StatType.BASE_HP, mechanics.data.TalentDataManager.getInstance().get(this.name, "Base HP", 9244));
        baseStats.set(StatType.BASE_ATK,
                mechanics.data.TalentDataManager.getInstance().get(this.name, "Base ATK", 170));
        baseStats.set(StatType.BASE_DEF,
                mechanics.data.TalentDataManager.getInstance().get(this.name, "Base DEF", 703));
        baseStats.set(StatType.ANEMO_DMG_BONUS,
                mechanics.data.TalentDataManager.getInstance().get(this.name, "Ascension Anemo", 0.24));

        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.element = Element.ANEMO;

        // Defaults
        this.constellation = 6;

        setSkillCD(15.0);
        setBurstCD(20.0);

        // C1: +1 charge (max 2); each charge has its own 15s CD
        setSkillMaxCharges((this.constellation >= 1) ? 2 : 1);
    }

    /**
     * Returns the burst energy cost (80).
     */
    @Override
    public double getEnergyCost() {
        return 80;
    }

    /**
     * Returns {@code true} while the burst field is active.  Duration is 6 s
     * normally or 8 s at C2+.
     *
     * @param currentTime current simulation time in seconds
     */
    @Override
    public boolean isBurstActive(double currentTime) {
        // Approximate check: Burst lasts 6s (8s C2)
        double duration = (this.constellation >= 2) ? 8.0 : 6.0;
        return (currentTime - getLastBurstTime()) < duration;
    }

    /**
     * No static passive stat modifications for Sucrose.
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        // No static passives affecting self stats
    }

    /**
     * Handles typed action requests dispatched by the combat simulator.
     *
     * <p>Supported actions:
     * <ul>
     *   <li>{@link CharacterActionKey#SKILL} — casts the Skill.</li>
     *   <li>{@link CharacterActionKey#BURST} — casts the Burst.</li>
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
     * Executes the current normal attack step (4-hit Anemo Catalyst combo).
     *
     * @param sim the combat simulator context
     */
    private void normalAttack(CombatSimulator sim) {
        String key = "N" + (normalAttackStep + 1);
        String name = "Sucrose " + key;

        // Default multipliers if Config missing
        double defaultMv = 0.5;
        switch (normalAttackStep) {
            case 0:
                defaultMv = 0.569;
                break;
            case 1:
                defaultMv = 0.520;
                break;
            case 2:
                defaultMv = 0.654;
                break;
            case 3:
                defaultMv = 0.815;
                break;
        }

        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, key, defaultMv);
        double dur = 0;

        switch(normalAttackStep) {
            case 0:
                dur = 0.1;
                break;
            case 1:
                dur = 0.1;
                break;
            case 2:
                dur = 0.2;
                break;
            case 3:
                dur = 0.4;
                break;
        }

        // Sucrose is Catalyst, deals Anemo DMG on Normals
        AttackAction hit = new AttackAction(name, mv, Element.ANEMO, StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS, 0.0, ActionType.NORMAL);

        hit.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
        hit.setAnimationDuration(dur);
        sim.performAction(this.name, hit);

        normalAttackStep++;
        if (normalAttackStep >= 4)
            normalAttackStep = 0;
    }

    /**
     * Casts the Skill: fires an Anemo hit, generates 4 Anemo particles, and
     * triggers the A4 and A1 passives.
     *
     * @param sim the combat simulator context
     */
    private void skill(CombatSimulator sim) {
        // Talent Lv 12 (Base 9 + 3): 4.22
        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Skill DMG", 4.22);

        AttackAction hit = new AttackAction("Astable Anemohypostasis Creation - 6308", mv, Element.ANEMO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL);
        hit.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0);
        hit.setAnimationDuration(0.5);
        sim.performAction(this.name, hit);

        EnergyManager.distributeParticles(Element.ANEMO, 4.0, ParticleType.PARTICLE, sim);

        applyA4Passive(sim);
        applyA1PassiveIfSwirled(sim);
    }

    /**
     * Casts the Burst: registers a periodic Anemo DoT event.  On the first
     * tick the absorbed element is determined from the enemy aura; once absorbed,
     * an additional hit of that element fires each tick.  C6 applies a +20%
     * elemental DMG team buff on absorption.
     *
     * @param sim the combat simulator context
     */
    private void burst(CombatSimulator sim) {
        this.absorbedElement = null;

        double dotMv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Burst DoT", 2.96);
        double absorbMv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Burst Absorb", 0.88);

        // Create dummy action for initial cast time
        AttackAction cast = new AttackAction("Forbidden Creation - Isomer 75 (Cast)", 0.0, Element.ANEMO,
                StatType.BASE_ATK,
                null, 1.5, ActionType.BURST);
        cast.setAnimationDuration(0.3);
        sim.performAction(this.name, cast);

        double duration = (this.constellation >= 2) ? 8.0 : 6.0;

        sim.registerEvent(new PeriodicDamageEvent(
                this.name,
                new AttackAction("Forbidden Creation - Isomer 75 / Type II (DoT)", dotMv, Element.ANEMO,
                        StatType.BASE_ATK, StatType.BURST_DMG_BONUS, 0.0, false, ActionType.BURST),
                sim.getCurrentTime() + 2.0,
                2.0,
                duration,
                s -> {
                    if (this.absorbedElement == null) {
                        model.entity.Enemy enemy = s.getEnemy();
                        if (enemy != null) {
                            if (enemy.getAuraUnits(Element.PYRO) > 0)
                                this.absorbedElement = Element.PYRO;
                            else if (enemy.getAuraUnits(Element.HYDRO) > 0)
                                this.absorbedElement = Element.HYDRO;
                            else if (enemy.getAuraUnits(Element.ELECTRO) > 0)
                                this.absorbedElement = Element.ELECTRO;
                            else if (enemy.getAuraUnits(Element.CRYO) > 0)
                                this.absorbedElement = Element.CRYO;
                        }

                        if (this.absorbedElement != null) {
                            if (this.constellation >= 6) {
                                double remainingTime = duration - (s.getCurrentTime() - getLastBurstTime());
                                applyC6Buff(s, this.absorbedElement, remainingTime > 0 ? remainingTime : 0.1);
                            }
                        }
                    }

                    if (this.absorbedElement != null) {
                        AttackAction extra = new AttackAction("Forbidden Creation - Isomer 75 (Absorb)", absorbMv,
                                this.absorbedElement, StatType.BASE_ATK, StatType.BURST_DMG_BONUS, 0.0, false,
                                ActionType.BURST);
                        extra.setICD(ICDType.Standard, ICDTag.ElementalBurst, 1.0);
                        s.performAction(this.name, extra);
                    }

                    applyA4Passive(s);
                    applyA1PassiveIfSwirled(s);
                }));
    }

    /**
     * Applies the Mollis Favonius (A4) passive: grants all party members except
     * Sucrose herself 20% of Sucrose's current EM as flat EM for 8 s.
     *
     * @param sim the combat simulator context
     */
    private void applyA4Passive(CombatSimulator sim) {
        double myEm = this.getEffectiveStats(sim.getCurrentTime()).get(StatType.ELEMENTAL_MASTERY);
        double buffVal = myEm * 0.20;

        sim.applyTeamBuffNoStack(
                new SimpleBuff("Mollis Favonius (A4)", BuffId.SUCROSE_MOLLIS_FAVONIUS_A4, 8.0, sim.getCurrentTime(), st -> {
                    st.add(StatType.ELEMENTAL_MASTERY, buffVal);
                }).exclude(this.name));
    }

    /**
     * Applies the Catalyst Conversion (A1) passive: if the enemy currently has
     * a Pyro, Hydro, Electro, or Cryo aura, grants +50 EM to all party members
     * of the matching element for 8 s.
     *
     * @param sim the combat simulator context
     */
    private void applyA1PassiveIfSwirled(CombatSimulator sim) {
        model.entity.Enemy enemy = sim.getEnemy();
        if (enemy == null)
            return;

        Element swirled = null;
        if (enemy.getAuraUnits(Element.PYRO) > 0)
            swirled = Element.PYRO;
        else if (enemy.getAuraUnits(Element.HYDRO) > 0)
            swirled = Element.HYDRO;
        else if (enemy.getAuraUnits(Element.ELECTRO) > 0)
            swirled = Element.ELECTRO;
        else if (enemy.getAuraUnits(Element.CRYO) > 0)
            swirled = Element.CRYO;

        if (swirled != null) {
            final Element swirledElem = swirled;
            final BuffId buffId = getCatalystConversionBuffId(swirledElem);
            sim.applyTeamBuffNoStack(
                    new SimpleBuff("Catalyst Conversion (A1) [" + swirled.name() + "]", buffId, 8.0, sim.getCurrentTime(),
                            st -> {
                                st.add(StatType.ELEMENTAL_MASTERY, 50.0);
                            }).forElement(swirledElem));
        }
    }

    private BuffId getCatalystConversionBuffId(Element element) {
        switch (element) {
            case PYRO:
                return BuffId.SUCROSE_CATALYST_CONVERSION_A1_PYRO;
            case HYDRO:
                return BuffId.SUCROSE_CATALYST_CONVERSION_A1_HYDRO;
            case ELECTRO:
                return BuffId.SUCROSE_CATALYST_CONVERSION_A1_ELECTRO;
            case CRYO:
                return BuffId.SUCROSE_CATALYST_CONVERSION_A1_CRYO;
            default:
                throw new IllegalArgumentException("Unsupported catalyst conversion element: " + element);
        }
    }

    /**
     * Applies the C6 elemental DMG bonus (+20%) to all party members for the
     * absorbed element for the specified duration.
     *
     * @param sim      the combat simulator context
     * @param elem     the absorbed element type
     * @param duration remaining burst duration in seconds
     */
    private void applyC6Buff(CombatSimulator sim, Element elem, double duration) {
        sim.applyTeamBuff(new SimpleBuff("Sucrose C6 Bonus", BuffId.SUCROSE_C6_BONUS, duration, sim.getCurrentTime(), st -> {
            switch (elem) {
                case PYRO:
                    st.add(StatType.PYRO_DMG_BONUS, 0.20);
                    break;
                case HYDRO:
                    st.add(StatType.HYDRO_DMG_BONUS, 0.20);
                    break;
                case ELECTRO:
                    st.add(StatType.ELECTRO_DMG_BONUS, 0.20);
                    break;
                case CRYO:
                    st.add(StatType.CRYO_DMG_BONUS, 0.20);
                    break;
                case DENDRO:
                case GEO:
                case ANEMO:
                case PHYSICAL:
                    // C6 only buffs absorbed element (Pyro/Hydro/Electro/Cryo)
                    break;
            }
        }));
    }

}
