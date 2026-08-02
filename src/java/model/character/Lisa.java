package model.character;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SwitchAwareCharacter;
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
import simulation.action.CharacterActionRequest;
import simulation.event.SimpleTimerEvent;

/**
 * Lisa character implementation for stationary single-target combat.
 *
 * <p>The repository's single Skill key selects Hold Violet Arc. Charged hits
 * add independently expiring Conductive stacks that the Hold consumes. C1,
 * C3, and C5 are represented. Press Skill, enemy DEF state for A4, incoming
 * damage for C2 and multi-target C4 remain outside this slice. C6 applies
 * three typed Conductive markers on eligible standard switch-in.
 */
public class Lisa extends Character implements SwitchAwareCharacter {
    private static final double CONDUCTIVE_DURATION = 15.0;
    private static final int MAX_CONDUCTIVE_STACKS = 3;
    private static final int BURST_DISCHARGE_COUNT = 29;
    private static final double BURST_DISCHARGE_INTERVAL = 0.5;

    private int normalAttackStep;

    /**
     * Constructs Lisa with the shared talent data source.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     */
    public Lisa(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance());
    }

    /**
     * Constructs Lisa with an explicit talent source for deterministic tests.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     */
    public Lisa(Weapon weapon, ArtifactSet artifacts, TalentDataSource talentData) {
        super(talentData);
        this.name = "Lisa";
        this.characterId = CharacterId.LISA;
        this.element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = (int) getTalentValue("Constellation", 6.0);

        baseStats.set(StatType.BASE_HP, getTalentValue("Base HP", 9570.0));
        baseStats.set(StatType.BASE_ATK, getTalentValue("Base ATK", 232.0));
        baseStats.set(StatType.BASE_DEF, getTalentValue("Base DEF", 573.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Ascension EM", 96.0));
        setSkillCD(16.0);
        setBurstCD(20.0);
    }

    /**
     * Returns Lisa's 80-Energy Burst cost.
     *
     * @return current Burst cost
     */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /**
     * Lisa has no unconditional static combat-stat passive in this slice.
     *
     * @param stats assembled stats container
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A1 is modeled as Conductive application on Charged hits.
    }

    /**
     * Lisa has no switch-out effect; this method satisfies the shared switch
     * capability used by her C6 switch-in behavior.
     *
     * @param sim active simulator whose party still has Lisa active
     */
    @Override
    public void onSwitchOut(CombatSimulator sim) {
    }

    /**
     * Applies C6 Pulsating Witch on an eligible in-combat standard switch.
     *
     * <p>The single-target simulator stores Conductive on Lisa as three typed
     * markers. Replacing them together models the sourced simultaneous stack
     * application and refreshes all expiries to 15 seconds. A typed five-second
     * marker preserves the in-combat cooldown across simulator snapshots.
     *
     * @param sim active simulator whose party already has Lisa active
     */
    @Override
    public void onSwitchIn(CombatSimulator sim) {
        double currentTime = sim.getCurrentTime();
        if (constellation < 6
                || sim.getEnemy() == null
                || hasActiveMarker(
                        BuffId.LISA_C6_PULSATING_WITCH_COOLDOWN,
                        currentTime)) {
            return;
        }

        replaceConductiveStacks(currentTime, MAX_CONDUCTIVE_STACKS);
        removeBuff(BuffId.LISA_C6_PULSATING_WITCH_COOLDOWN);
        addBuff(createMarker(
                "Pulsating Witch Cooldown",
                BuffId.LISA_C6_PULSATING_WITCH_COOLDOWN,
                5.0,
                currentTime));
    }

    /**
     * Dispatches Lisa's typed player actions.
     *
     * @param request requested action
     * @param sim active combat simulator
     */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(sim);
                break;
            case CHARGE:
                chargedAttack(sim);
                break;
            case SKILL:
                markSkillUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                holdVioletArc(sim);
                break;
            case BURST:
                markBurstUsed(sim.getCurrentTime(), sim.getApplicableBuffs(this));
                lightningRose(sim);
                break;
            case DASH:
                normalAttackStep = 0;
                sim.advanceTime(22.0 / 60.0);
                break;
            case PLUNGE:
                plunge(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Lisa: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        String key = "N" + (normalAttackStep + 1);
        double[] durations = {
                30.0 / 60.0, 20.0 / 60.0, 34.0 / 60.0, 57.0 / 60.0
        };
        double[] defaults = { 0.6732, 0.6106, 0.7276, 0.9343 };
        AttackAction normal = new AttackAction(
                "Lisa " + key,
                getTalentValue(key, defaults[normalAttackStep]),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                durations[normalAttackStep],
                ActionType.NORMAL);
        normal.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
        sim.performAction(characterId, normal);

        normalAttackStep++;
        if (normalAttackStep >= defaults.length) {
            normalAttackStep = 0;
        }
    }

    private void chargedAttack(CombatSimulator sim) {
        AttackAction charged = new AttackAction(
                "Lisa Charged Attack",
                getTalentValue("Charged Attack", 3.0110),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                91.0 / 60.0,
                ActionType.CHARGE);
        // The 91-frame action interval always exceeds Lisa's sourced 0.5s ICD.
        charged.setICD(ICDType.None, ICDTag.ChargedAttack, 1.0);
        sim.performAction(characterId, charged);
        addConductiveStack(sim.getCurrentTime());
        normalAttackStep = 0;
    }

    private void holdVioletArc(CombatSimulator sim) {
        int stacks = consumeConductiveStacks(sim.getCurrentTime());
        double[] baseMultipliers = { 5.4400, 6.2560, 7.2080, 8.2824 };
        double[] c5Multipliers = { 6.4000, 7.3600, 8.4800, 9.7400 };
        double defaultMultiplier = constellation >= 5
                ? c5Multipliers[stacks]
                : baseMultipliers[stacks];
        AttackAction hold = new AttackAction(
                "Violet Arc Hold (" + stacks + " Conductive)",
                getTalentValue("Violet Arc Hold " + stacks, defaultMultiplier),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                141.0 / 60.0,
                ActionType.SKILL);
        hold.setICD(ICDType.None, ICDTag.ElementalSkill, 2.0);
        sim.performAction(characterId, hold);
        sim.getEnergyDistributor().distributeParticles(
                Element.ELECTRO, 5.0, ParticleType.PARTICLE);
        if (constellation >= 1) {
            receiveFlatEnergy(2.0);
        }
        normalAttackStep = 0;
    }

    private void lightningRose(CombatSimulator sim) {
        captureSnapshot(sim.getCurrentTime(), sim.getApplicableBuffs(this));

        AttackAction summon = new AttackAction(
                "Lightning Rose Summon",
                0.10,
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                true,
                ActionType.BURST);
        summon.setICD(ICDType.None, ICDTag.ElementalBurst, 0.0);
        sim.performActionWithoutTimeAdvance(characterId, summon);

        double defaultMultiplier = constellation >= 3 ? 0.7310 : 0.6215;
        AttackAction discharge = new AttackAction(
                "Lightning Rose Discharge",
                getTalentValue("Lightning Rose Discharge", defaultMultiplier),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                0.0,
                true,
                ActionType.BURST);
        discharge.setICD(ICDType.Standard, ICDTag.ElementalBurst, 1.0);
        sim.registerEvent(new SimpleTimerEvent(
                sim.getCurrentTime() + BURST_DISCHARGE_INTERVAL,
                BURST_DISCHARGE_INTERVAL) {
            private int remainingDischarges = BURST_DISCHARGE_COUNT;

            @Override
            public void onTick(CombatSimulator activeSim) {
                activeSim.performActionWithoutTimeAdvance(Lisa.this.characterId, discharge);
                remainingDischarges--;
                if (remainingDischarges == 0) {
                    finish();
                }
            }
        });
        sim.advanceTime(88.0 / 60.0);
        normalAttackStep = 0;
    }

    private void plunge(CombatSimulator sim) {
        AttackAction plunge = new AttackAction(
                "Lisa High Plunge",
                getTalentValue("Plunge High", 2.6076),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                1.0,
                ActionType.PLUNGE);
        plunge.setICD(ICDType.Standard, ICDTag.None, 1.0);
        sim.performAction(characterId, plunge);
        normalAttackStep = 0;
    }

    private void addConductiveStack(double currentTime) {
        pruneConductiveStacks(currentTime);
        int activeStacks = countActiveConductiveStacks(currentTime);
        if (activeStacks >= MAX_CONDUCTIVE_STACKS) {
            Buff oldestStack = null;
            for (Buff buff : getActiveBuffs()) {
                if (buff.getId() == BuffId.LISA_CONDUCTIVE_STACK
                        && !buff.isExpired(currentTime)) {
                    oldestStack = buff;
                    break;
                }
            }
            if (oldestStack != null) {
                getActiveBuffs().remove(oldestStack);
            }
        }
        addBuff(createMarker(
                "Conductive Stack",
                BuffId.LISA_CONDUCTIVE_STACK,
                CONDUCTIVE_DURATION,
                currentTime));
    }

    private int consumeConductiveStacks(double currentTime) {
        pruneConductiveStacks(currentTime);
        int stacks = countActiveConductiveStacks(currentTime);
        removeBuff(BuffId.LISA_CONDUCTIVE_STACK);
        return stacks;
    }

    private void pruneConductiveStacks(double currentTime) {
        getActiveBuffs().removeIf(buff ->
                buff.getId() == BuffId.LISA_CONDUCTIVE_STACK
                        && buff.isExpired(currentTime));
    }

    private int countActiveConductiveStacks(double currentTime) {
        int count = 0;
        for (Buff buff : getActiveBuffs()) {
            if (buff.getId() == BuffId.LISA_CONDUCTIVE_STACK
                    && !buff.isExpired(currentTime)) {
                count++;
            }
        }
        return count;
    }

    private void replaceConductiveStacks(double currentTime, int count) {
        removeBuff(BuffId.LISA_CONDUCTIVE_STACK);
        for (int i = 0; i < count; i++) {
            addBuff(createMarker(
                    "Conductive Stack",
                    BuffId.LISA_CONDUCTIVE_STACK,
                    CONDUCTIVE_DURATION,
                    currentTime));
        }
    }

    private boolean hasActiveMarker(BuffId id, double currentTime) {
        for (Buff buff : getActiveBuffs()) {
            if (buff.getId() == id && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
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
}
