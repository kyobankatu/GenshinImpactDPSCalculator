package model.character;

import java.util.Objects;
import java.util.function.DoubleSupplier;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.FormStateProvider;
import model.entity.ReactionAwareCharacter;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.event.PeriodicDamageEvent;

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
 *   <li><b>C4</b> — every seventh resolved Normal/Charged hit reduces only the
 *       earliest Skill charge cooldown by an injected integer from 1-7 s.</li>
 *   <li><b>C6</b> — absorbed element gains +20% elemental DMG for 10 s from
 *       absorption.</li>
 * </ul>
 *
 * <p>C1 grants a second Skill charge (max 2); C2 extends burst duration to 8 s.
 */
public class Sucrose extends Character implements
        FormStateProvider,
        SimulatorInitializedCharacterEffect,
        ReactionAwareCharacter {
    private static final double ALCHEMANIA_COUNT_COOLDOWN = 0.1;
    private static final int ALCHEMANIA_HIT_THRESHOLD = 7;
    private static final Element[] BURST_ABSORPTION_PRIORITY = {
            Element.PYRO,
            Element.HYDRO,
            Element.ELECTRO,
            Element.CRYO
    };

    private int normalAttackStep = 0;
    private final DoubleSupplier alchemaniaReductionDraw;
    private CombatSimulator initializedSimulator;

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
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                6,
                Sucrose::randomAlchemaniaReduction);
    }

    /**
     * Constructs C6 Sucrose with explicit talent data and stochastic C4 draws.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent value source
     */
    public Sucrose(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData) {
        this(weapon, artifacts, talentData, 6, Sucrose::randomAlchemaniaReduction);
    }

    /**
     * Constructs C6 Sucrose with explicit C4 reduction draws.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param alchemaniaReductionDraw source of integer reductions in [1, 7]
     */
    public Sucrose(
            Weapon weapon,
            ArtifactSet artifacts,
            DoubleSupplier alchemaniaReductionDraw) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                6,
                alchemaniaReductionDraw);
    }

    /**
     * Constructs C6 Sucrose with explicit talent data and C4 draws.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent value source
     * @param alchemaniaReductionDraw source of integer reductions in [1, 7]
     */
    public Sucrose(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            DoubleSupplier alchemaniaReductionDraw) {
        this(weapon, artifacts, talentData, 6, alchemaniaReductionDraw);
    }

    /**
     * Constructs Sucrose with explicit constellation and C4 reduction draws.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param constellation constellation level in the inclusive range 0-6
     * @param alchemaniaReductionDraw source of integer reductions in [1, 7]
     */
    public Sucrose(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation,
            DoubleSupplier alchemaniaReductionDraw) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation,
                alchemaniaReductionDraw);
    }

    /**
     * Constructs Sucrose with explicit talent data, constellation, and C4 draw.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent value source
     * @param constellation constellation level in the inclusive range 0-6
     * @param alchemaniaReductionDraw source of integer reductions in [1, 7]
     */
    public Sucrose(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier alchemaniaReductionDraw) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Sucrose constellation must be between 0 and 6");
        }
        this.alchemaniaReductionDraw = Objects.requireNonNull(
                alchemaniaReductionDraw,
                "Alchemania reduction draw source is required");
        this.name = "Sucrose";
        this.characterId = CharacterId.SUCROSE;

        // Level 90 Base Stats
        baseStats.set(StatType.BASE_HP, getTalentValue("Base HP", 9244));
        baseStats.set(StatType.BASE_ATK, getTalentValue("Base ATK", 170));
        baseStats.set(StatType.BASE_DEF, getTalentValue("Base DEF", 703));
        baseStats.set(StatType.ANEMO_DMG_BONUS, getTalentValue("Ascension Anemo", 0.24));

        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.element = Element.ANEMO;

        this.constellation = constellation;

        setSkillCD(15.0);
        setBurstCD(20.0);

        // C1: +1 charge (max 2); each charge has its own 15s CD
        setSkillMaxCharges((this.constellation >= 1) ? 2 : 1);
    }

    /**
     * Binds the A1 reaction and C4 damage listeners to exactly one simulator.
     *
     * @param sim simulator receiving Sucrose
     * @throws IllegalStateException if this instance is reused across simulators
     */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Sucrose cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;
        sim.addReactionListener(this);
        if (constellation >= 4) {
            sim.addDamageListener((actor, action, damage, time) -> {
                if (actor != this
                        || damage <= 0.0
                        || sim.getEnemy() == null
                        || (action.getActionType() != ActionType.NORMAL
                                && action.getActionType() != ActionType.CHARGE)) {
                    return;
                }
                recordAlchemaniaHit(time);
            });
        }
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
    public boolean isFormActive(double currentTime) {
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
     *   <li>{@link CharacterActionKey#CHARGE} — casts one Charged Attack.</li>
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
            case CHARGE:
                chargedAttack(sim);
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

        double mv = getTalentValue(key, defaultMv);
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
        sim.performAction(this.characterId, hit);

        normalAttackStep++;
        if (normalAttackStep >= 4) {
            normalAttackStep = 0;
        }
    }

    /**
     * Executes Sucrose's level-9 Charged Attack.
     *
     * @param sim active simulator
     */
    private void chargedAttack(CombatSimulator sim) {
        AttackAction charged = new AttackAction(
                "Sucrose Charged Attack",
                getTalentValue("Charged Attack", 2.0427),
                Element.ANEMO,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                69.0 / 60.0,
                ActionType.CHARGE);
        charged.setICD(ICDType.Standard, ICDTag.ChargedAttack, 1.0);
        sim.performAction(this.characterId, charged);
        normalAttackStep = 0;
    }

    /**
     * Casts the Skill: fires an Anemo hit, generates 4 Anemo particles, and
     * triggers the A4 passive. A1 is driven by the resolved reaction listener.
     *
     * @param sim the combat simulator context
     */
    private void skill(CombatSimulator sim) {
        // Talent Lv 12 (Base 9 + 3): 4.22
        double mv = getTalentValue("Skill DMG", 4.22);

        AttackAction hit = new AttackAction("Astable Anemohypostasis Creation - 6308", mv, Element.ANEMO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL);
        hit.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);
        hit.setAnimationDuration(0.5);
        sim.performAction(this.characterId, hit);

        sim.getEnergyDistributor().distributeParticles(Element.ANEMO, 4.0, ParticleType.PARTICLE);

        applyA4Passive(sim);
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

        double dotMv = getTalentValue("Burst DoT", 2.96);
        double absorbMv = getTalentValue("Burst Absorb", 0.88);

        // Create dummy action for initial cast time
        AttackAction cast = new AttackAction("Forbidden Creation - Isomer 75 (Cast)", 0.0, Element.ANEMO,
                StatType.BASE_ATK,
                null, 1.5, ActionType.BURST);
        cast.setICD(ICDType.None, ICDTag.ElementalBurst, 0.0);
        cast.setAnimationDuration(0.3);
        sim.performAction(this.characterId, cast);

        double duration = (this.constellation >= 2) ? 8.0 : 6.0;

        AttackAction burstTick = new AttackAction("Forbidden Creation - Isomer 75 / Type II (DoT)", dotMv,
                Element.ANEMO, StatType.BASE_ATK, StatType.BURST_DMG_BONUS, 0.0, false, ActionType.BURST);
        burstTick.setICD(ICDType.None, ICDTag.ElementalBurst, 1.0);

        sim.registerEvent(new PeriodicDamageEvent(
                this.name,
                burstTick,
                sim.getCurrentTime() + 2.0,
                2.0,
                duration,
                this::captureBurstAbsorption,
                s -> {
                    if (this.absorbedElement != null) {
                        AttackAction extra = new AttackAction("Forbidden Creation - Isomer 75 (Absorb)", absorbMv,
                                this.absorbedElement, StatType.BASE_ATK, StatType.BURST_DMG_BONUS, 0.0, false,
                                ActionType.BURST);
                        extra.setICD(ICDType.None, ICDTag.ElementalBurst, 1.0);
                        s.performAction(this.characterId, extra);
                    }

                    applyA4Passive(s);
                }));
    }

    /**
     * Captures the first supported aura immediately before a Burst tick.
     *
     * @param sim simulator about to resolve the Burst's Anemo damage
     */
    private void captureBurstAbsorption(CombatSimulator sim) {
        Enemy enemy = sim.getEnemy();
        if (absorbedElement != null || enemy == null) {
            return;
        }
        double time = sim.getCurrentTime();
        for (Element candidate : BURST_ABSORPTION_PRIORITY) {
            if (enemy.getAuraUnits(candidate, time) <= 0.0) {
                continue;
            }
            absorbedElement = candidate;
            if (constellation >= 6) {
                applyC6Buff(sim, absorbedElement);
            }
            return;
        }
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
                }).exclude(this.characterId).sourcedBy(this.getCharacterId()));
    }

    /**
     * Applies Catalyst Conversion from an actual Sucrose-triggered Swirl.
     *
     * @param result resolved reaction carrying the swirled element
     * @param source character that triggered the reaction
     * @param time reaction time
     * @param sim simulator dispatching the reaction
     */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (sim != initializedSimulator
                || source != this
                || result == null
                || !result.isSwirl()) {
            return;
        }

        Element swirledElement = result.getSwirlElement();
        BuffId buffId = getCatalystConversionBuffId(swirledElement);
        if (buffId == null) {
            return;
        }
        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Catalyst Conversion (A1) [" + swirledElement.name() + "]",
                buffId,
                8.0,
                time,
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, 50.0))
                .forElement(swirledElement)
                .exclude(characterId)
                .sourcedBy(characterId));
    }

    private BuffId getCatalystConversionBuffId(Element element) {
        if (element == null) {
            return null;
        }
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
                return null;
        }
    }

    /**
     * Applies the C6 elemental DMG bonus (+20%) for ten seconds from absorption.
     *
     * @param sim the combat simulator context
     * @param elem the absorbed element type
     */
    private void applyC6Buff(CombatSimulator sim, Element elem) {
        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Sucrose C6 Bonus",
                BuffId.SUCROSE_C6_BONUS,
                10.0,
                sim.getCurrentTime(),
                st -> {
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
                            // C6 only buffs absorbed element.
                            break;
                    }
                }).sourcedBy(characterId));
    }

    private void recordAlchemaniaHit(double hitTime) {
        if (hasActiveMarker(
                BuffId.SUCROSE_C4_ALCHEMANIA_COUNT_COOLDOWN,
                hitTime)) {
            return;
        }

        int countedHits = countActiveMarkers(
                BuffId.SUCROSE_C4_ALCHEMANIA_HIT,
                hitTime);
        if (countedHits + 1 < ALCHEMANIA_HIT_THRESHOLD) {
            addBuff(createMarker(
                    "Alchemania Counted Hit",
                    BuffId.SUCROSE_C4_ALCHEMANIA_HIT,
                    Double.MAX_VALUE,
                    hitTime));
        } else {
            double reduction = alchemaniaReductionDraw.getAsDouble();
            if (!Double.isFinite(reduction)
                    || reduction < 1.0
                    || reduction > 7.0
                    || reduction != Math.rint(reduction)) {
                throw new IllegalArgumentException(
                        "Alchemania reduction draw must be an integer between 1 and 7");
            }
            removeBuff(BuffId.SUCROSE_C4_ALCHEMANIA_HIT);
            reduceSkillCooldown(hitTime, reduction);
        }

        removeBuff(BuffId.SUCROSE_C4_ALCHEMANIA_COUNT_COOLDOWN);
        addBuff(createMarker(
                "Alchemania Count Cooldown",
                BuffId.SUCROSE_C4_ALCHEMANIA_COUNT_COOLDOWN,
                ALCHEMANIA_COUNT_COOLDOWN,
                hitTime));
    }

    private int countActiveMarkers(BuffId id, double currentTime) {
        int count = 0;
        for (Buff buff : getActiveBuffs()) {
            if (buff.getId() == id && !buff.isExpired(currentTime)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasActiveMarker(BuffId id, double currentTime) {
        return countActiveMarkers(id, currentTime) > 0;
    }

    private Buff createMarker(
            String markerName,
            BuffId id,
            double duration,
            double currentTime) {
        return new SimpleBuff(
                markerName,
                id,
                duration,
                currentTime,
                stats -> {
                }).sourcedBy(characterId);
    }

    private static double randomAlchemaniaReduction() {
        return 1.0 + Math.floor(Math.random() * 7.0);
    }

}
