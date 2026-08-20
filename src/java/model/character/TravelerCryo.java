package model.character;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.StellarReactionProvider;
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

/**
 * Cryo Traveler's source-backed fixed-target Version 7.0 slice.
 *
 * <p>Level-90 stats and level-9 multipliers follow Genshin Optimizer revision
 * {@code d791814a}. The represented mechanics include the five-hit sword
 * string, gender-specific two-hit Charged Attack, Frostpierce Star and manual
 * ice crystals, Frostglow consumption, both Stellar Burst variants,
 * Icepoint, A1/A4, and the offensive C1-C6 effects.</p>
 *
 * <p>The public {@link #fireIceCrystal(CombatSimulator)} method is an explicit
 * integration point because the published static data does not define the
 * summon firing cadence or hitmarks. Animation frames, elemental gauge, ICD,
 * particles, stamina, geometry, and automatic summon scheduling therefore
 * fail closed instead of being inferred.</p>
 */
public final class TravelerCryo extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        StellarReactionProvider {
    private static final double EPSILON = 1e-9;
    private static final double[] NORMAL_T9 = {
        0.816860, 0.797900, 0.973280, 1.071240, 1.300340
    };

    /** Selects the sourced second Charged Attack multiplier. */
    public enum Gender {
        /** Lumine's sword string. */
        FEMALE,
        /** Aether's sword string. */
        MALE
    }

    private final Gender gender;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int frostglowStacks;
    private int icepointStacks;
    private double starActiveUntil = Double.NEGATIVE_INFINITY;
    private double nextIcepointTime = Double.NEGATIVE_INFINITY;
    private double nextC1Time = Double.NEGATIVE_INFINITY;
    private double nextSpecialChargedTime = Double.NEGATIVE_INFINITY;
    private CharacterId c2Recipient = CharacterId.UNKNOWN;
    private double c2ActiveUntil = Double.NEGATIVE_INFINITY;
    private boolean c2Upgraded;
    private double c6ActiveUntil = Double.NEGATIVE_INFINITY;
    private double c6StellarDamageBonus;
    private AttackAction resolvingIceCrystal;

    /** Constructs repository-default C6 female Cryo Traveler. */
    public TravelerCryo(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, Gender.FEMALE, 6);
    }

    /** Constructs Cryo Traveler with explicit gender and constellation. */
    public TravelerCryo(
            Weapon weapon,
            ArtifactSet artifacts,
            Gender gender,
            int constellation) {
        this(weapon, artifacts, gender, TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Cryo Traveler with injectable static talent data.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param gender twin used for the second Charged Attack multiplier
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public TravelerCryo(
            Weapon weapon,
            ArtifactSet artifacts,
            Gender gender,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (gender == null) {
            throw new IllegalArgumentException("Cryo Traveler gender is required");
        }
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Cryo Traveler constellation must be between 0 and 6");
        }
        name = "TravelerCryo";
        characterId = CharacterId.TRAVELER;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.gender = gender;
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10874.9149));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 212.3972));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 682.5215));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds damage gates and permanent dynamic support once. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Cryo Traveler simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Cryo Traveler must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Cryo Traveler cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener(this::handleAcceptedDamage);
        simulator.applyTeamBuffNoStack(createStellarSupportBuff());
        simulator.applyTeamBuffNoStack(createC2Buff());
        simulator.applyTeamBuffNoStack(createC6Buff());
    }

    /** Captures every Traveler-owned stack, cooldown gate, and support window. */
    @Override
    public State captureCharacterState() {
        return new TravelerCryoState(
                this,
                normalAttackStep,
                frostglowStacks,
                icepointStacks,
                starActiveUntil,
                nextIcepointTime,
                nextC1Time,
                nextSpecialChargedTime,
                c2Recipient,
                c2ActiveUntil,
                c2Upgraded,
                c6ActiveUntil,
                c6StellarDamageBonus);
    }

    /** Accepts state captured from this exact Traveler instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof TravelerCryoState
                && ((TravelerCryoState) state).owner == this;
    }

    /** Restores all represented Cryo Traveler state. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Cryo Traveler state");
        }
        initializeForSimulator(simulator);
        TravelerCryoState restored = (TravelerCryoState) state;
        normalAttackStep = restored.normalAttackStep;
        frostglowStacks = restored.frostglowStacks;
        icepointStacks = restored.icepointStacks;
        starActiveUntil = restored.starActiveUntil;
        nextIcepointTime = restored.nextIcepointTime;
        nextC1Time = restored.nextC1Time;
        nextSpecialChargedTime = restored.nextSpecialChargedTime;
        c2Recipient = restored.c2Recipient;
        c2ActiveUntil = restored.c2ActiveUntil;
        c2Upgraded = restored.c2Upgraded;
        c6ActiveUntil = restored.c6ActiveUntil;
        c6StellarDamageBonus = restored.c6StellarDamageBonus;
        resolvingIceCrystal = null;
    }

    /** Returns the sourced 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Applies A4's ATK-derived Elemental Mastery, capped at 160. */
    @Override
    public void applyPassive(StatsContainer stats) {
        double ratio = getTalentValue("A4 ATK to EM Ratio", 0.08);
        double cap = getTalentValue("A4 Maximum EM", 160.0);
        stats.add(StatType.ELEMENTAL_MASTERY,
                Math.min(cap, stats.getTotalAtk() * ratio));
    }

    /** Enables Superconduct conversion for the party. */
    @Override
    public boolean enablesStellarConduct() {
        return true;
    }

    /** Enables Cryo Swirl conversion for the party. */
    @Override
    public boolean enablesStellarSwirl() {
        return true;
    }

    /** Returns the selected twin. */
    public Gender getGender() {
        return gender;
    }

    /** Returns the current Frostglow stack count in {@code [0, 8]}. */
    public int getFrostglowStacks() {
        return frostglowStacks;
    }

    /** Returns the current Icepoint stack count in {@code [0, 3]}. */
    public int getIcepointStacks() {
        return icepointStacks;
    }

    /** Returns whether the Frostpierce Star is active. */
    public boolean isFrostpierceStarActive(double currentTime) {
        return currentTime + EPSILON < starActiveUntil;
    }

    /** Reports that automatic summon cadence and action frames are excluded. */
    public boolean isAutomaticTimingRepresented() {
        return false;
    }

    /** Dispatches the represented zero-animation action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Cryo Traveler action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case CHARGE:
                chargedAttack(simulator);
                break;
            case SKILL:
                skill(simulator);
                break;
            case BURST:
                burst(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Cryo Traveler: "
                                + request.getKey());
        }
    }

    /**
     * Fires one source-backed Frostpierce Star ice crystal immediately.
     *
     * @param simulator active simulator containing this Traveler
     * @throws IllegalStateException when the Star has expired
     */
    public void fireIceCrystal(CombatSimulator simulator) {
        initializeForSimulator(simulator);
        if (!isFrostpierceStarActive(simulator.getCurrentTime())) {
            throw new IllegalStateException(
                    "Frostpierce Star is not active");
        }
        AttackAction action = createAction(
                "Frostpierce Star Ice Crystal",
                getTalentValue(
                        constellation >= 5
                                ? "Ice Crystal DMG C5"
                                : "Ice Crystal DMG",
                        constellation >= 5 ? 0.427840 : 0.363664),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                null);
        resolvingIceCrystal = action;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingIceCrystal = null;
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        int step = normalAttackStep;
        boolean infused = hasConductStar(simulator);
        performHit(
                simulator,
                "Foreign Frostglint N" + (step + 1),
                NORMAL_T9[step] + (infused ? 0.8 : 0.0),
                infused ? Element.CRYO : Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                null);
        normalAttackStep = (step + 1) % NORMAL_T9.length;
    }

    private void chargedAttack(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        boolean special = icepointStacks >= 3
                && currentTime + EPSILON >= nextSpecialChargedTime;
        if (special) {
            icepointStacks = 0;
            nextSpecialChargedTime = currentTime
                    + getTalentValue("Freezing Ice Cooldown", 15.0);
            frostglowStacks = Math.min(8, frostglowStacks + 2);
        }
        AttackAction.StellarReactionType stellarType = special
                ? currentRadiance(simulator) : null;
        boolean infused = special || hasConductStar(simulator);
        double a1Bonus = !special && infused ? 0.8 : 0.0;
        double specialBonus = special
                ? getTalentValue("Freezing Ice ATK Bonus", 1.40) : 0.0;
        double secondMultiplier = gender == Gender.FEMALE
                ? getTalentValue("Charged Attack Hit 2 Female", 1.327200)
                : getTalentValue("Charged Attack Hit 2 Male", 1.115480);
        performHit(
                simulator,
                special ? "Charged Attack Freezing Ice 1"
                        : "Foreign Frostglint Charged 1",
                getTalentValue("Charged Attack Hit 1", 1.027000)
                        + a1Bonus + specialBonus,
                infused ? Element.CRYO : Element.PHYSICAL,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                stellarType);
        performHit(
                simulator,
                special ? "Charged Attack Freezing Ice 2"
                        : "Foreign Frostglint Charged 2",
                secondMultiplier + a1Bonus + specialBonus,
                infused ? Element.CRYO : Element.PHYSICAL,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                stellarType);
    }

    private void skill(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        markSkillUsed(currentTime, simulator.getApplicableBuffs(this));
        starActiveUntil = currentTime
                + getTalentValue("Frostpierce Star Duration", 12.0)
                        * (constellation >= 4 ? 1.25 : 1.0);
        performHit(
                simulator,
                "Ice Fog Piercer",
                getTalentValue(
                        constellation >= 5 ? "Skill DMG C5" : "Skill DMG",
                        constellation >= 5 ? 1.833600 : 1.558560),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                null);
    }

    private void burst(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        markBurstUsed(currentTime, simulator.getApplicableBuffs(this));
        int consumed = frostglowStacks;
        frostglowStacks = 0;
        AttackAction.StellarReactionType stellarType =
                currentRadiance(simulator);
        int strikes = 3 + (consumed >= 8 ? 2 : 0);
        double baseMultiplier;
        double stackMultiplier;
        if (stellarType == AttackAction.StellarReactionType.CONDUCT) {
            baseMultiplier = getTalentValue(
                    constellation >= 3
                            ? "Burst Stellar Conduct DMG C3"
                            : "Burst Stellar Conduct DMG",
                    constellation >= 3 ? 0.735080 : 0.624818);
            stackMultiplier = getTalentValue(
                    constellation >= 3
                            ? "Burst Stellar Conduct Frostglow Bonus C3"
                            : "Burst Stellar Conduct Frostglow Bonus",
                    constellation >= 3 ? 0.036754 : 0.031241);
        } else {
            baseMultiplier = getTalentValue(
                    constellation >= 3 ? "Burst DMG C3" : "Burst DMG",
                    constellation >= 3 ? 1.102620 : 0.937227);
            stackMultiplier = getTalentValue(
                    constellation >= 3
                            ? "Burst Frostglow Bonus C3"
                            : "Burst Frostglow Bonus",
                    constellation >= 3 ? 0.055131 : 0.046861);
        }
        if (stellarType == AttackAction.StellarReactionType.SWIRL) {
            baseMultiplier = getTalentValue(
                    constellation >= 3
                            ? "Burst Stellar Swirl DMG C3"
                            : "Burst Stellar Swirl DMG",
                    constellation >= 3 ? 1.102620 : 0.937227);
            stackMultiplier = getTalentValue(
                    constellation >= 3
                            ? "Burst Stellar Swirl Frostglow Bonus C3"
                            : "Burst Stellar Swirl Frostglow Bonus",
                    constellation >= 3 ? 0.055131 : 0.046861);
        }
        if (constellation >= 6) {
            c6StellarDamageBonus = consumed
                    * getTalentValue("C6 Stellar DMG Per Stack", 0.05);
            c6ActiveUntil = currentTime
                    + getTalentValue("C6 Duration", 15.0);
        }
        for (int strike = 0; strike < strikes; strike++) {
            performHit(
                    simulator,
                    "Frostbound Javelin " + (strike + 1),
                    baseMultiplier + stackMultiplier * consumed,
                    Element.CRYO,
                    StatType.BURST_DMG_BONUS,
                    ActionType.BURST,
                    stellarType);
        }
    }

    private void performHit(
            CombatSimulator simulator,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            AttackAction.StellarReactionType stellarType) {
        AttackAction action = createAction(
                displayName,
                multiplier,
                hitElement,
                bonusStat,
                actionType,
                stellarType);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private AttackAction createAction(
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            AttackAction.StellarReactionType stellarType) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setStellarReactionType(stellarType);
        return action;
    }

    private void handleAcceptedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        if (damage <= 0.0 || action == null) {
            return;
        }
        if (actor == this && action == resolvingIceCrystal) {
            frostglowStacks = Math.min(8, frostglowStacks + 1);
            if (constellation >= 2) {
                Character active = initializedSimulator.getActiveCharacter();
                c2Recipient = active == null
                        ? CharacterId.UNKNOWN : active.getCharacterId();
                c2ActiveUntil = currentTime
                        + getTalentValue("C2 Duration", 5.0);
                c2Upgraded = false;
            }
        }
        if (!action.isStellarConsidered()) {
            return;
        }
        if (currentTime + EPSILON >= nextIcepointTime) {
            icepointStacks = Math.min(3, icepointStacks + 1);
            nextIcepointTime = currentTime
                    + getTalentValue("Icepoint Cooldown", 2.0);
        }
        if (constellation >= 1
                && actor == this
                && currentTime + EPSILON >= nextC1Time) {
            receiveFlatEnergy(getTalentValue("C1 Flat Energy", 5.0));
            nextC1Time = currentTime
                    + getTalentValue("C1 Cooldown", 0.5);
        }
        if (constellation >= 2
                && actor == initializedSimulator.getActiveCharacter()
                && actor.getCharacterId() == c2Recipient
                && currentTime + EPSILON < c2ActiveUntil) {
            c2Upgraded = true;
        }
    }

    private boolean hasConductStar(CombatSimulator simulator) {
        return isFrostpierceStarActive(simulator.getCurrentTime())
                && simulator.getStellarReactionManager()
                        .hasStellarConductRadiance(
                                simulator.getCurrentTime());
    }

    private AttackAction.StellarReactionType currentRadiance(
            CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        if (simulator.getStellarReactionManager()
                .hasStellarConductRadiance(currentTime)) {
            return AttackAction.StellarReactionType.CONDUCT;
        }
        if (simulator.getStellarReactionManager()
                .hasStellarSwirlRadiance(currentTime)) {
            return AttackAction.StellarReactionType.SWIRL;
        }
        return null;
    }

    private Buff createStellarSupportBuff() {
        return new Buff(
                "Cryo Traveler Stellar Jubilee",
                BuffId.TRAVELER_CRYO_STELLAR_SUPPORT,
                Double.MAX_VALUE,
                0.0) {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                double sourceAttack = TravelerCryo.this
                        .getEffectiveStats(currentTime).getTotalAtk();
                double bonus = Math.min(
                        getTalentValue("Stellar Base DMG Cap", 0.07),
                        sourceAttack * getTalentValue(
                                "Stellar Base DMG Per ATK", 0.000035));
                stats.add(StatType.STELLAR_CONDUCT_BASE_DMG_BONUS, bonus);
                stats.add(StatType.STELLAR_SWIRL_BASE_DMG_BONUS, bonus);
            }
        }.sourcedBy(characterId);
    }

    private Buff createC2Buff() {
        return new Buff(
                "Cryo Traveler C2 Frostfall Reverberation",
                BuffId.TRAVELER_CRYO_C2_EM,
                Double.MAX_VALUE,
                0.0) {
            @Override
            public boolean appliesToCharacter(Character character) {
                return character != null
                        && character.getCharacterId() == c2Recipient;
            }

            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                if (constellation >= 2
                        && currentTime + EPSILON < c2ActiveUntil) {
                    stats.add(StatType.ELEMENTAL_MASTERY,
                            c2Upgraded ? 120.0 : 60.0);
                }
            }
        }.sourcedBy(characterId);
    }

    private Buff createC6Buff() {
        return new Buff(
                "Cryo Traveler C6 Brumal Grimfrost",
                BuffId.TRAVELER_CRYO_C6_STELLAR_DMG,
                Double.MAX_VALUE,
                0.0) {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                if (constellation >= 6
                        && currentTime + EPSILON < c6ActiveUntil) {
                    stats.add(StatType.STELLAR_CONDUCT_DMG_BONUS,
                            c6StellarDamageBonus);
                    stats.add(StatType.STELLAR_SWIRL_DMG_BONUS,
                            c6StellarDamageBonus);
                }
            }
        }.exclude(characterId).sourcedBy(characterId);
    }

    /** Immutable owner-bound snapshot payload. */
    private static final class TravelerCryoState implements State {
        private final TravelerCryo owner;
        private final int normalAttackStep;
        private final int frostglowStacks;
        private final int icepointStacks;
        private final double starActiveUntil;
        private final double nextIcepointTime;
        private final double nextC1Time;
        private final double nextSpecialChargedTime;
        private final CharacterId c2Recipient;
        private final double c2ActiveUntil;
        private final boolean c2Upgraded;
        private final double c6ActiveUntil;
        private final double c6StellarDamageBonus;

        private TravelerCryoState(
                TravelerCryo owner,
                int normalAttackStep,
                int frostglowStacks,
                int icepointStacks,
                double starActiveUntil,
                double nextIcepointTime,
                double nextC1Time,
                double nextSpecialChargedTime,
                CharacterId c2Recipient,
                double c2ActiveUntil,
                boolean c2Upgraded,
                double c6ActiveUntil,
                double c6StellarDamageBonus) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.frostglowStacks = frostglowStacks;
            this.icepointStacks = icepointStacks;
            this.starActiveUntil = starActiveUntil;
            this.nextIcepointTime = nextIcepointTime;
            this.nextC1Time = nextC1Time;
            this.nextSpecialChargedTime = nextSpecialChargedTime;
            this.c2Recipient = c2Recipient;
            this.c2ActiveUntil = c2ActiveUntil;
            this.c2Upgraded = c2Upgraded;
            this.c6ActiveUntil = c6ActiveUntil;
            this.c6StellarDamageBonus = c6StellarDamageBonus;
        }
    }
}
