package model.character;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.ReactionAwareCharacter;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.SnapshotAwareCharacterEffect;
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
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Clorinde's deterministic fixed-target Night Vigil offensive slice.
 *
 * <p>Level-90 data, sword basics, high Plunge, Night Vigil, Swift Hunt,
 * Impale the Night, local Bond of Life, Last Lightfall, particles, private
 * ICD, A1/A4, and representable C1-C6 offense follow pinned gcsim
 * {@code ef41805d}. Bond, independent passive stacks, cooldown gates, and
 * delayed attacks are owner-bound and reconstructable after rollback.</p>
 *
 * <p>Player current HP and actual healing, external Bond integrations,
 * movement and geometry, multi-target or random selection, hitlag, stamina,
 * low Plunge, exploration, interruption resistance, damage reduction, and
 * reactive defensive C6 Shades are excluded. Impale clears the local Bond
 * deterministically without fabricating player healing.</p>
 */
public final class Clorinde extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 18 }, { 12 }, { 23, 32 }, { 12, 18, 23 }, { 21 }
    };
    private static final int[] NORMAL_DURATIONS = { 24, 27, 42, 35, 60 };
    private static final double[][] NORMAL_T9 = {
        { 0.993188 },
        { 0.948521 },
        { 0.628050, 0.628050 },
        { 0.425020, 0.425020, 0.425020 },
        { 1.653675 }
    };
    private static final int[] SWIFT_HIT_FRAMES = { 8, 8, 9 };
    private static final int[] SWIFT_DURATIONS = { 18, 17, 20 };
    private static final int[] BURST_HIT_FRAMES = { 97, 103, 109, 115, 121 };
    private static final Set<ReactionResult.Kind> ELECTRO_REACTIONS =
            EnumSet.of(
                    ReactionResult.Kind.OVERLOAD,
                    ReactionResult.Kind.OVERLOADED,
                    ReactionResult.Kind.ELECTRO_CHARGED,
                    ReactionResult.Kind.LUNAR_CHARGED,
                    ReactionResult.Kind.SUPERCONDUCT,
                    ReactionResult.Kind.QUICKEN,
                    ReactionResult.Kind.AGGRAVATE,
                    ReactionResult.Kind.HYPERBLOOM);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int swiftHuntStep;
    private double nightVigilExpirationTime = Double.NEGATIVE_INFINITY;
    private double bondRatio;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextSurgingBladeAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC1AllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC6ShadeAllowedTime = Double.NEGATIVE_INFINITY;
    private double c6BuffExpirationTime = Double.NEGATIVE_INFINITY;
    private int c6ShadesRemaining;
    private double[] a1StackExpirations = emptyExpirations(3);
    private double[] a4StackExpirations = emptyExpirations(2);
    private AttackAction resolvingAction;
    private boolean resolvingParticleEligible;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Clorinde. */
    public Clorinde(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Clorinde at an explicit constellation. */
    public Clorinde(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Clorinde with injectable static talent data.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Clorinde(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Clorinde constellation must be between 0 and 6");
        }
        name = "Clorinde";
        characterId = CharacterId.CLORINDE;
        element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12956.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 337.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 784.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(getTalentValue("Skill Cooldown", 16.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds reaction and accepted-damage listeners to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Clorinde simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Clorinde must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Clorinde cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
        simulator.addDamageListener(this::handleAcceptedDamage);
    }

    /** Captures local Bond, stacks, gates, combo state, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new ClorindeState(
                this,
                normalAttackStep,
                swiftHuntStep,
                nightVigilExpirationTime,
                bondRatio,
                nextParticleAllowedTime,
                nextSurgingBladeAllowedTime,
                nextC1AllowedTime,
                nextC6ShadeAllowedTime,
                c6BuffExpirationTime,
                c6ShadesRemaining,
                a1StackExpirations,
                a4StackExpirations,
                pendingEvents);
    }

    /** Accepts state captured from this exact Clorinde instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof ClorindeState
                && ((ClorindeState) state).owner == this;
    }

    /** Restores all surviving owner events exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Clorinde state");
        }
        initializeForSimulator(simulator);
        ClorindeState restored = (ClorindeState) state;
        normalAttackStep = restored.normalAttackStep;
        swiftHuntStep = restored.swiftHuntStep;
        nightVigilExpirationTime = restored.nightVigilExpirationTime;
        bondRatio = restored.bondRatio;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        nextSurgingBladeAllowedTime =
                restored.nextSurgingBladeAllowedTime;
        nextC1AllowedTime = restored.nextC1AllowedTime;
        nextC6ShadeAllowedTime = restored.nextC6ShadeAllowedTime;
        c6BuffExpirationTime = restored.c6BuffExpirationTime;
        c6ShadesRemaining = restored.c6ShadesRemaining;
        a1StackExpirations = restored.a1StackExpirations.clone();
        a4StackExpirations = restored.a4StackExpirations.clone();
        pendingEvents = copyEvents(restored.pendingEvents);
        resolvingAction = null;
        resolvingParticleEligible = false;
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            scheduleEvent(simulator, event);
        }
    }

    /** Returns Clorinde's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Clorinde's represented passives are reaction or action conditional. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Exposes Impale while Night Vigil is active despite the base cooldown. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (isNightVigilActive(currentTime)) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Returns whether fresh Night Vigil or its Impale replacement is ready. */
    @Override
    public boolean canSkill(double currentTime) {
        return getSkillCDRemaining(currentTime) <= EPSILON;
    }

    /** Ends Night Vigil and resets both attack strings on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        swiftHuntStep = 0;
        nightVigilExpirationTime = simulator.getCurrentTime();
    }

    /** Resets both attack strings on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
        swiftHuntStep = 0;
    }

    /** Returns whether Night Vigil is live at a half-open timestamp. */
    public boolean isNightVigilActive(double currentTime) {
        return currentTime + EPSILON < nightVigilExpirationTime;
    }

    /** Returns the locally tracked Bond as a Max-HP ratio. */
    public double getBondOfLifeRatio() {
        return bondRatio;
    }

    /** Returns the active independent A1 stack count. */
    public int getA1StackCount(double currentTime) {
        return activeStackCount(a1StackExpirations, currentTime);
    }

    /** Returns the active independent A4 CRIT stack count. */
    public int getA4StackCount(double currentTime) {
        return activeStackCount(a4StackExpirations, currentTime);
    }

    /** Returns C6's remaining fixed offensive Shade charges. */
    public int getC6ShadesRemaining() {
        return c6ShadesRemaining;
    }

    /** Returns the number of unresolved Clorinde-owned events. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /** Reports that player HP and actual healing are excluded. */
    public boolean isPlayerHpHealingRepresented() {
        return false;
    }

    /** Reports that weapon, artifact, and foreign Bond changes are excluded. */
    public boolean isExternalBondOfLifeRepresented() {
        return false;
    }

    /** Reports that movement and target geometry are excluded. */
    public boolean isMovementGeometryRepresented() {
        return false;
    }

    /** Reports that multi-target and random selection are excluded. */
    public boolean isMultiTargetSelectionRepresented() {
        return false;
    }

    /** Reports that hitlag is excluded. */
    public boolean isHitlagRepresented() {
        return false;
    }

    /** Reports that stamina is excluded. */
    public boolean isStaminaRepresented() {
        return false;
    }

    /** Reports that low Plunge is excluded. */
    public boolean isLowPlungeRepresented() {
        return false;
    }

    /** Reports that exploration state is excluded. */
    public boolean isExplorationStateRepresented() {
        return false;
    }

    /** Reports that interruption, mitigation, and defensive C6 are excluded. */
    public boolean isDefensiveStateRepresented() {
        return false;
    }

    /** Dispatches the represented typed action set and replacement inputs. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Clorinde action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Clorinde only supports Press Skill in this slice");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                if (isNightVigilActive(simulator.getCurrentTime())) {
                    swiftHunt(simulator);
                } else {
                    normalAttack(simulator);
                }
                break;
            case CHARGE:
                chargedAttack(simulator);
                swiftHuntStep = 0;
                break;
            case PLUNGE:
                highPlunge(simulator);
                swiftHuntStep = 0;
                break;
            case SKILL:
                if (isNightVigilActive(simulator.getCurrentTime())) {
                    impaleTheNight(simulator);
                } else {
                    huntersVigil(simulator);
                }
                break;
            case BURST:
                lastLightfall(simulator);
                swiftHuntStep = 0;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Clorinde: "
                                + request.getKey());
        }
    }

    /** Adds one independent A1 stack for an Electro-related party reaction. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || source == null
                || !isElectroRelatedReaction(result)) {
            return;
        }
        addIndependentStack(
                a1StackExpirations,
                time,
                getTalentValue("A1 Duration", 15.0));
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int variant = 0;
                variant < NORMAL_HIT_FRAMES[step].length;
                variant++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + NORMAL_HIT_FRAMES[step][variant] * FRAME,
                    EventKind.NORMAL,
                    step,
                    variant,
                    Branch.NONE,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueEvent(simulator, new PendingEvent(
                castTime + 38.0 * FRAME,
                EventKind.CHARGED,
                0,
                0,
                Branch.NONE,
                null));
        simulator.advanceTime(44.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueEvent(simulator, new PendingEvent(
                castTime + 48.0 * FRAME,
                EventKind.HIGH_PLUNGE,
                0,
                0,
                Branch.NONE,
                null));
        simulator.advanceTime(81.0 * FRAME);
    }

    /** Starts Night Vigil and its delayed sixteen-second cooldown. */
    private void huntersVigil(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        normalAttackStep = 0;
        swiftHuntStep = 0;
        nightVigilExpirationTime = castTime
                + 6.0 * FRAME
                + getTalentValue("Night Vigil Duration", 7.5);
        if (constellation >= 6) {
            c6ShadesRemaining = (int) getTalentValue(
                    "C6 Shade Count", 6.0);
            c6BuffExpirationTime = castTime
                    + getTalentValue("C6 Duration", 12.0);
        }
        queueEvent(simulator, new PendingEvent(
                castTime + 6.0 * FRAME,
                EventKind.SKILL_COOLDOWN,
                0,
                0,
                Branch.NONE,
                null));
        simulator.advanceTime(33.0 * FRAME);
    }

    /** Queues one of the three source-backed Swift Hunt attacks. */
    private void swiftHunt(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = swiftHuntStep;
        Branch branch = bondRatio + EPSILON < 1.0
                ? Branch.SWIFT_ENHANCED : Branch.SWIFT_NORMAL;
        queueEvent(simulator, new PendingEvent(
                castTime + SWIFT_HIT_FRAMES[step] * FRAME,
                EventKind.SWIFT_HUNT,
                step,
                0,
                branch,
                null));
        swiftHuntStep = (swiftHuntStep + 1) % SWIFT_HIT_FRAMES.length;
        simulator.advanceTime(SWIFT_DURATIONS[step] * FRAME);
    }

    /** Queues Bond clearing, the selected Impale branch, and C6 offense. */
    private void impaleTheNight(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        swiftHuntStep = 0;
        Branch branch;
        int hitCount;
        if (bondRatio <= EPSILON) {
            branch = Branch.IMPALE_NONE;
            hitCount = 1;
        } else if (bondRatio + EPSILON < 1.0) {
            branch = Branch.IMPALE_LOW;
            hitCount = 1;
        } else {
            branch = Branch.IMPALE_FULL;
            hitCount = 3;
        }
        if (branch != Branch.IMPALE_NONE) {
            queueEvent(simulator, new PendingEvent(
                    castTime + 6.0 * FRAME,
                    EventKind.CLEAR_BOND,
                    0,
                    0,
                    branch,
                    null));
        }
        for (int hit = 0; hit < hitCount; hit++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + 11.0 * FRAME,
                    EventKind.IMPALE,
                    0,
                    hit,
                    branch,
                    null));
        }
        if (branch == Branch.IMPALE_FULL && constellation >= 6) {
            queueEvent(simulator, new PendingEvent(
                    castTime + 37.0 * FRAME,
                    EventKind.C6_SHADE,
                    0,
                    0,
                    branch,
                    null));
        }
        simulator.advanceTime(43.0 * FRAME);
    }

    /** Queues Burst Bond, Energy, and five cast-snapshot damage packets. */
    private void lastLightfall(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueEvent(simulator, new PendingEvent(
                castTime + 13.0 * FRAME,
                EventKind.BURST_BOND,
                0,
                0,
                Branch.NONE,
                null));
        queueEvent(simulator, new PendingEvent(
                castTime + 14.0 * FRAME,
                EventKind.BURST_ENERGY,
                0,
                0,
                Branch.NONE,
                null));
        for (int index = 0; index < BURST_HIT_FRAMES.length; index++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + BURST_HIT_FRAMES[index] * FRAME,
                    EventKind.BURST,
                    index,
                    0,
                    Branch.NONE,
                    snapshot));
        }
        simulator.advanceTime(128.0 * FRAME);
    }

    /** Resolves one queued owner event. */
    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.kind) {
            case NORMAL:
                resolveNormal(simulator, event);
                break;
            case CHARGED:
                resolveSimpleHit(
                        simulator,
                        event,
                        "Oath of Hunting Shadows Charged",
                        getTalentValue("Charged Attack", 2.354200),
                        Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0,
                        false);
                break;
            case HIGH_PLUNGE:
                resolveSimpleHit(
                        simulator,
                        event,
                        "Oath of Hunting Shadows High Plunge",
                        getTalentValue("High Plunge", 2.933586),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        0.0,
                        false);
                break;
            case SKILL_COOLDOWN:
                markSkillUsed(
                        event.time,
                        simulator.getApplicableBuffs(this));
                break;
            case SWIFT_HUNT:
                resolveSwiftHunt(simulator, event);
                break;
            case CLEAR_BOND:
                setBondRatio(0.0, event.time);
                break;
            case IMPALE:
                resolveImpale(simulator, event);
                break;
            case SURGING_BLADE:
                resolveSurgingBlade(simulator, event);
                break;
            case PARTICLE:
                simulator.getEnergyDistributor().distributeParticles(
                        Element.ELECTRO,
                        (int) getTalentValue("Particle Count", 1.0),
                        ParticleType.PARTICLE);
                break;
            case C1_SHADE:
                resolveShade(simulator, event, false);
                break;
            case C6_SHADE:
                resolveC6Shade(simulator, event);
                break;
            case BURST_BOND:
                addBondRatio(burstBondValue(), event.time);
                break;
            case BURST_ENERGY:
                spendBurstEnergy(event.time);
                break;
            case BURST:
                resolveBurst(simulator, event);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Clorinde event kind");
        }
    }

    private void resolveNormal(
            CombatSimulator simulator,
            PendingEvent event) {
        String key = "N" + (event.index + 1);
        if (NORMAL_HIT_FRAMES[event.index].length > 1) {
            key += " Hit " + (event.variant + 1);
        }
        resolveSimpleHit(
                simulator,
                event,
                "Oath of Hunting Shadows N" + (event.index + 1),
                getTalentValue(
                        key,
                        NORMAL_T9[event.index][event.variant]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0,
                false);
    }

    private void resolveSwiftHunt(
            CombatSimulator simulator,
            PendingEvent event) {
        boolean enhanced = event.branch == Branch.SWIFT_ENHANCED;
        resolveSimpleHit(
                simulator,
                event,
                enhanced
                        ? "Swift Hunt (Piercing Shot) " + (event.index + 1)
                        : "Swift Hunt (Normal Shot) " + (event.index + 1),
                skillTalentValue(
                        enhanced ? "Swift Hunt Enhanced" : "Swift Hunt",
                        enhanced ? 0.712580 : 0.491696,
                        enhanced ? 0.874940 : 0.603728),
                Element.ELECTRO,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0,
                true);
        if (enhanced) {
            addBondRatio(
                    getTalentValue("Swift Hunt Bond Gain", 0.35),
                    event.time);
        }
        if (event.time + EPSILON >= nextSurgingBladeAllowedTime) {
            nextSurgingBladeAllowedTime = event.time
                    + getTalentValue("Surging Blade Interval", 10.0);
            queueEvent(simulator, new PendingEvent(
                    event.time + 42.0 * FRAME,
                    EventKind.SURGING_BLADE,
                    0,
                    0,
                    Branch.NONE,
                    null));
        }
    }

    private void resolveImpale(
            CombatSimulator simulator,
            PendingEvent event) {
        String key;
        double talentNine;
        double talentTwelve;
        switch (event.branch) {
            case IMPALE_NONE:
                key = "Impale No Bond";
                talentNine = 0.605772;
                talentTwelve = 0.743796;
                break;
            case IMPALE_LOW:
                key = "Impale Low Bond";
                talentNine = 0.807696;
                talentTwelve = 0.991728;
                break;
            case IMPALE_FULL:
                key = "Impale Full Bond";
                talentNine = 0.461360;
                talentTwelve = 0.566480;
                break;
            default:
                throw new IllegalStateException("Unexpected Impale branch");
        }
        resolveSimpleHit(
                simulator,
                event,
                "Impale the Night (" + impaleLabel(event.branch) + ")",
                skillTalentValue(
                        key,
                        talentNine,
                        talentTwelve),
                Element.ELECTRO,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0,
                true);
    }

    private void resolveSurgingBlade(
            CombatSimulator simulator,
            PendingEvent event) {
        resolveSimpleHit(
                simulator,
                event,
                "Surging Blade",
                skillTalentValue(
                        "Surging Blade", 0.734400, 0.864000),
                Element.ELECTRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                0.0,
                false);
    }

    private void resolveShade(
            CombatSimulator simulator,
            PendingEvent event,
            boolean c6) {
        resolveSimpleHit(
                simulator,
                event,
                c6 ? "Glimbright Shade (C6)" : "Nightvigil Shade (C1)",
                getTalentValue(
                        c6
                                ? "C6 Shade Multiplier"
                                : "C1 Shade Multiplier",
                        c6 ? 2.0 : 0.30),
                Element.ELECTRO,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.ClorindeElementalArt,
                ICDTag.Clorinde_ElementalArt,
                1.0,
                !c6);
    }

    private void resolveC6Shade(
            CombatSimulator simulator,
            PendingEvent event) {
        if (constellation < 6
                || !isNightVigilActive(event.time)
                || c6ShadesRemaining <= 0
                || event.time + EPSILON < nextC6ShadeAllowedTime) {
            return;
        }
        c6ShadesRemaining--;
        nextC6ShadeAllowedTime = event.time
                + getTalentValue("C6 Shade Gate", 1.0);
        resolveShade(simulator, event, true);
    }

    private void resolveBurst(
            CombatSimulator simulator,
            PendingEvent event) {
        double bonus = constellation >= 4
                ? Math.min(
                        getTalentValue("C4 Burst Bonus Cap", 2.0),
                        bondRatio * getTalentValue(
                                "C4 Burst Bonus Per Bond Ratio", 2.0))
                : 0.0;
        AttackAction action = createAction(
                "Last Lightfall " + (event.index + 1),
                burstDamageValue(),
                Element.ELECTRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0,
                event.snapshot,
                a1AdditiveDamage(event.time, event.snapshot));
        action.setCountsAsBurstDmg(true);
        action.addBonusStat(StatType.DMG_BONUS_ALL, bonus);
        performResolvedAction(simulator, action, false);
    }

    /** Creates and resolves a fixed-target hit using event-time owner state. */
    private void resolveSimpleHit(
            CombatSimulator simulator,
            PendingEvent event,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            boolean particleEligible) {
        StatsContainer snapshot = event.snapshot == null
                ? captureLiveStats(event.time) : event.snapshot;
        double additiveDamage = hitElement == Element.ELECTRO
                && (actionType == ActionType.NORMAL
                        || actionType == ActionType.BURST)
                ? a1AdditiveDamage(event.time, snapshot) : 0.0;
        AttackAction action = createAction(
                displayName,
                multiplier,
                hitElement,
                bonusStat,
                actionType,
                icdType,
                icdTag,
                gauge,
                snapshot,
                additiveDamage);
        if (actionType == ActionType.SKILL) {
            action.setCountsAsSkillDmg(true);
        }
        performResolvedAction(simulator, action, particleEligible);
    }

    private void performResolvedAction(
            CombatSimulator simulator,
            AttackAction action,
            boolean particleEligible) {
        if (simulator.getEnemy() == null) {
            return;
        }
        resolvingAction = action;
        resolvingParticleEligible = particleEligible;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingParticleEligible = false;
        }
    }

    /** Handles accepted particle and constellation triggers. */
    private void handleAcceptedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (initializedSimulator == null
                || actor != this
                || action == null
                || action != resolvingAction
                || damage <= 0.0) {
            return;
        }
        if (resolvingParticleEligible
                && time + EPSILON >= nextParticleAllowedTime) {
            nextParticleAllowedTime = time
                    + getTalentValue("Particle Gate", 2.0);
            queueEvent(initializedSimulator, new PendingEvent(
                    time + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    EventKind.PARTICLE,
                    0,
                    0,
                    Branch.NONE,
                    null));
        }
        if (constellation >= 1
                && isNightVigilActive(time)
                && action.getActionType() == ActionType.NORMAL
                && action.getElement() == Element.ELECTRO
                && time + EPSILON >= nextC1AllowedTime) {
            nextC1AllowedTime = time
                    + getTalentValue("C1 Trigger Gate", 1.2);
            for (int hit = 0; hit < 2; hit++) {
                queueEvent(initializedSimulator, new PendingEvent(
                        time + FRAME,
                        EventKind.C1_SHADE,
                        0,
                        hit,
                        Branch.NONE,
                        null));
            }
        }
    }

    /** Updates local Bond and sources A4 from a prior 100%+ state. */
    private void setBondRatio(double newRatio, double time) {
        double clamped = Math.max(0.0, Math.min(2.0, newRatio));
        if (Math.abs(clamped - bondRatio) <= EPSILON) {
            return;
        }
        if (bondRatio + EPSILON >= 1.0) {
            addIndependentStack(
                    a4StackExpirations,
                    time,
                    getTalentValue("A4 Duration", 15.0));
        }
        bondRatio = clamped;
    }

    private void addBondRatio(double amount, double time) {
        setBondRatio(bondRatio + amount, time);
    }

    private double a1AdditiveDamage(
            double time,
            StatsContainer snapshot) {
        int stacks = getA1StackCount(time);
        if (stacks == 0 || snapshot == null) {
            return 0.0;
        }
        double ratio = getTalentValue(
                constellation >= 2
                        ? "C2 A1 ATK Ratio" : "A1 ATK Ratio",
                constellation >= 2 ? 0.30 : 0.20);
        double cap = getTalentValue(
                constellation >= 2
                        ? "C2 A1 Flat Cap" : "A1 Flat Cap",
                constellation >= 2 ? 2700.0 : 1800.0);
        return Math.min(
                cap,
                snapshot.getTotalAtk() * ratio * stacks);
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        stats.add(
                StatType.CRIT_RATE,
                getA4StackCount(currentTime)
                        * getTalentValue("A4 CRIT Rate", 0.10));
        if (constellation >= 6
                && currentTime + EPSILON < c6BuffExpirationTime) {
            stats.add(
                    StatType.CRIT_RATE,
                    getTalentValue("C6 CRIT Rate", 0.10));
            stats.add(
                    StatType.CRIT_DMG,
                    getTalentValue("C6 CRIT DMG", 0.70));
        }
        return stats;
    }

    /** Creates one action with immutable additive damage and stat snapshot. */
    private AttackAction createAction(
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            StatsContainer snapshot,
            double additiveDamage) {
        AttackAction action = additiveDamage == 0.0
                ? new AttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        StatType.BASE_ATK,
                        bonusStat,
                        0.0,
                        actionType)
                : new ClorindeAttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        bonusStat,
                        actionType,
                        additiveDamage);
        action.setICD(icdType, icdTag, gauge);
        action.setStatSnapshot(snapshot);
        return action;
    }

    private double skillTalentValue(
            String key,
            double talentNine,
            double talentTwelve) {
        return getTalentValue(
                constellation >= 3 ? key + " C3" : key,
                constellation >= 3 ? talentTwelve : talentNine);
    }

    private double burstDamageValue() {
        return getTalentValue(
                constellation >= 5
                        ? "Last Lightfall C5" : "Last Lightfall",
                constellation >= 5 ? 2.537600 : 2.156960);
    }

    private double burstBondValue() {
        return getTalentValue(
                constellation >= 5 ? "Bond Gain C5" : "Bond Gain",
                constellation >= 5 ? 1.320000 : 1.140000);
    }

    private boolean isElectroRelatedReaction(ReactionResult result) {
        if (result == null || result.getKind() == ReactionResult.Kind.NONE) {
            return false;
        }
        if (ELECTRO_REACTIONS.contains(result.getKind())) {
            return true;
        }
        return (result.getKind() == ReactionResult.Kind.SWIRL
                || result.getKind() == ReactionResult.Kind.CRYSTALLIZE)
                && result.getRelatedElement() == Element.ELECTRO;
    }

    private static String impaleLabel(Branch branch) {
        switch (branch) {
            case IMPALE_NONE:
                return "0% Bond";
            case IMPALE_LOW:
                return "<100% Bond";
            case IMPALE_FULL:
                return "100%+ Bond";
            default:
                throw new IllegalArgumentException(
                        "Unexpected Impale label branch");
        }
    }

    private static double[] emptyExpirations(int count) {
        double[] expirations = new double[count];
        for (int index = 0; index < count; index++) {
            expirations[index] = Double.NEGATIVE_INFINITY;
        }
        return expirations;
    }

    private static int activeStackCount(
            double[] expirations,
            double time) {
        int count = 0;
        for (double expiration : expirations) {
            if (time + EPSILON < expiration) {
                count++;
            }
        }
        return count;
    }

    /** Adds a stack, replacing the independently oldest expiry at the cap. */
    private static void addIndependentStack(
            double[] expirations,
            double time,
            double duration) {
        int selected = 0;
        for (int index = 0; index < expirations.length; index++) {
            if (time + EPSILON >= expirations[index]) {
                expirations[index] = Double.NEGATIVE_INFINITY;
            }
            if (expirations[index] < expirations[selected]) {
                selected = index;
            }
        }
        expirations[selected] = time + duration;
    }

    private void queueEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        pendingEvents.add(event);
        scheduleEvent(simulator, event);
    }

    private void scheduleEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        schedule(simulator, event.time, activeSimulator -> {
            if (!pendingEvents.remove(event)) {
                return;
            }
            resolveEvent(activeSimulator, event);
        });
    }

    private static void schedule(
            CombatSimulator simulator,
            double time,
            Consumer<CombatSimulator> effect) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                effect.accept(activeSimulator);
            }
        });
    }

    private static List<PendingEvent> copyEvents(
            List<PendingEvent> source) {
        List<PendingEvent> copy = new ArrayList<>();
        for (PendingEvent event : source) {
            copy.add(event.copy());
        }
        return copy;
    }

    private enum EventKind {
        NORMAL,
        CHARGED,
        HIGH_PLUNGE,
        SKILL_COOLDOWN,
        SWIFT_HUNT,
        CLEAR_BOND,
        IMPALE,
        SURGING_BLADE,
        PARTICLE,
        C1_SHADE,
        C6_SHADE,
        BURST_BOND,
        BURST_ENERGY,
        BURST
    }

    private enum Branch {
        NONE,
        SWIFT_NORMAL,
        SWIFT_ENHANCED,
        IMPALE_NONE,
        IMPALE_LOW,
        IMPALE_FULL
    }

    /** Preserves A1's fixed additive base damage through resolution. */
    private static final class ClorindeAttackAction extends AttackAction {
        private final double fixedAdditiveBaseDamage;

        private ClorindeAttackAction(
                String displayName,
                double multiplier,
                Element element,
                StatType bonusStat,
                ActionType actionType,
                double fixedAdditiveBaseDamage) {
            super(
                    displayName,
                    multiplier,
                    element,
                    StatType.BASE_ATK,
                    bonusStat,
                    0.0,
                    actionType);
            this.fixedAdditiveBaseDamage = fixedAdditiveBaseDamage;
        }

        @Override
        public void setAdditiveBaseDmgBonus(double value) {
        }

        @Override
        public double getAdditiveBaseDmgBonus() {
            return fixedAdditiveBaseDamage;
        }
    }

    /** Immutable delayed owner event with an optional cast snapshot. */
    private static final class PendingEvent {
        private final double time;
        private final EventKind kind;
        private final int index;
        private final int variant;
        private final Branch branch;
        private final StatsContainer snapshot;

        private PendingEvent(
                double time,
                EventKind kind,
                int index,
                int variant,
                Branch branch,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.branch = branch;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time,
                    kind,
                    index,
                    variant,
                    branch,
                    snapshot);
        }
    }

    /** Immutable snapshot of all Clorinde-owned mutable runtime state. */
    private static final class ClorindeState implements State {
        private final Clorinde owner;
        private final int normalAttackStep;
        private final int swiftHuntStep;
        private final double nightVigilExpirationTime;
        private final double bondRatio;
        private final double nextParticleAllowedTime;
        private final double nextSurgingBladeAllowedTime;
        private final double nextC1AllowedTime;
        private final double nextC6ShadeAllowedTime;
        private final double c6BuffExpirationTime;
        private final int c6ShadesRemaining;
        private final double[] a1StackExpirations;
        private final double[] a4StackExpirations;
        private final List<PendingEvent> pendingEvents;

        private ClorindeState(
                Clorinde owner,
                int normalAttackStep,
                int swiftHuntStep,
                double nightVigilExpirationTime,
                double bondRatio,
                double nextParticleAllowedTime,
                double nextSurgingBladeAllowedTime,
                double nextC1AllowedTime,
                double nextC6ShadeAllowedTime,
                double c6BuffExpirationTime,
                int c6ShadesRemaining,
                double[] a1StackExpirations,
                double[] a4StackExpirations,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.swiftHuntStep = swiftHuntStep;
            this.nightVigilExpirationTime = nightVigilExpirationTime;
            this.bondRatio = bondRatio;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.nextSurgingBladeAllowedTime =
                    nextSurgingBladeAllowedTime;
            this.nextC1AllowedTime = nextC1AllowedTime;
            this.nextC6ShadeAllowedTime = nextC6ShadeAllowedTime;
            this.c6BuffExpirationTime = c6BuffExpirationTime;
            this.c6ShadesRemaining = c6ShadesRemaining;
            this.a1StackExpirations = a1StackExpirations.clone();
            this.a4StackExpirations = a4StackExpirations.clone();
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}
