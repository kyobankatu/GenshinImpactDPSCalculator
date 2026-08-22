package model.character;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Ororon's source-backed fixed-target Hypersense offensive slice.
 *
 * <p>Three physical Normals, a fully charged Electro shot, high Plunge,
 * one-target Spirit Orb and particles, Ritual and Soundwave timing, A1
 * Electro-Charged/Lunar-Charged Hypersense, A4 Energy, and representable
 * C1-C6 branches follow pinned gcsim {@code ef41805d}. Soundwaves keep their
 * private three-second application gate and all delayed work is reconstructable
 * after a simulator snapshot restore.</p>
 *
 * <p>Nightsoul Burst team plumbing and generic Nightsoul-aligned hit tags are
 * absent from this simulator and fail closed. Movement, geometry, bounces and
 * multi-target selection, taunt, complete hitlag coverage, stamina, low
 * Plunge, aim weakspots, and target-position selection are excluded instead
 * of approximated.</p>
 */
public final class Ororon extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 22, 20, 30 };
    private static final int[] NORMAL_DURATIONS = { 32, 53, 70 };
    private static final double[] NORMAL_T9 = {
        0.930399, 0.815233, 1.282755
    };
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile AIMED_HEADSHOT_HITLAG =
            new HitlagProfile(0.12, 0.01, false, true, true);
    private static final HitlagProfile HYPERSENSE_HITLAG =
            new HitlagProfile(0.0, 0.01, true, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double a1SkillWindowUntil = Double.NEGATIVE_INFINITY;
    private double nextA1PointAllowedTime = Double.NEGATIVE_INFINITY;
    private int a1PointGainHitCount;
    private double nightsoulPoints;
    private double nextHypersenseAllowedTime = Double.NEGATIVE_INFINITY;
    private double c1MarkUntil = Double.NEGATIVE_INFINITY;
    private double a4WindowUntil = Double.NEGATIVE_INFINITY;
    private double nextA4AllowedTime = Double.NEGATIVE_INFINITY;
    private int a4TriggerCount;
    private double c2ExpirationTime = Double.NEGATIVE_INFINITY;
    private int c2Stacks;
    private double[] c6StackExpirations = {
        Double.NEGATIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.NEGATIVE_INFINITY
    };
    private AttackAction resolvingAction;
    private boolean resolvingSpiritOrb;
    private boolean resolvingBurstHit;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Ororon. */
    public Ororon(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Ororon at an explicit constellation. */
    public Ororon(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Ororon with injectable static talent data.
     *
     * @param weapon equipped bow, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Ororon(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Ororon constellation must be between 0 and 6");
        }
        name = "Ororon";
        characterId = CharacterId.ORORON;
        element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9244.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 244.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 587.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds listeners and the C6 active-character buff to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Ororon simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Ororon must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Ororon cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
        simulator.addDamageListener((actor, action, damage, time) ->
                handleAcceptedDamage(
                        simulator, actor, action, damage, time));
        if (constellation >= 6) {
            simulator.applyFieldBuff(new OroronC6ActiveBuff(this)
                    .sourcedBy(characterId));
        }
    }

    /** Captures resources, gates, constellation stacks, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new OroronState(
                this,
                normalAttackStep,
                a1SkillWindowUntil,
                nextA1PointAllowedTime,
                a1PointGainHitCount,
                nightsoulPoints,
                nextHypersenseAllowedTime,
                c1MarkUntil,
                a4WindowUntil,
                nextA4AllowedTime,
                a4TriggerCount,
                c2ExpirationTime,
                c2Stacks,
                c6StackExpirations,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Ororon instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof OroronState
                && ((OroronState) state).owner == this;
    }

    /** Restores surviving Ororon-owned delayed work exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Ororon state");
        }
        initializeForSimulator(simulator);
        OroronState restored = (OroronState) state;
        normalAttackStep = restored.normalAttackStep;
        a1SkillWindowUntil = restored.a1SkillWindowUntil;
        nextA1PointAllowedTime = restored.nextA1PointAllowedTime;
        a1PointGainHitCount = restored.a1PointGainHitCount;
        nightsoulPoints = restored.nightsoulPoints;
        nextHypersenseAllowedTime = restored.nextHypersenseAllowedTime;
        c1MarkUntil = restored.c1MarkUntil;
        a4WindowUntil = restored.a4WindowUntil;
        nextA4AllowedTime = restored.nextA4AllowedTime;
        a4TriggerCount = restored.a4TriggerCount;
        c2ExpirationTime = restored.c2ExpirationTime;
        c2Stacks = restored.c2Stacks;
        c6StackExpirations = restored.c6StackExpirations.clone();
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        resolvingAction = null;
        resolvingSpiritOrb = false;
        resolvingBurstHit = false;
        double currentTime = simulator.getCurrentTime();
        pendingHits.removeIf(hit -> hit.time < currentTime - EPSILON);
        pendingCommands.removeIf(command ->
                command.time < currentTime - EPSILON);
        for (PendingHit hit : new ArrayList<>(pendingHits)) {
            scheduleHit(simulator, hit);
        }
        for (PendingCommand command
                : new ArrayList<>(pendingCommands)) {
            scheduleCommand(simulator, command);
        }
    }

    /** Returns Ororon's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Ororon has no unconditional represented passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets Ororon's three-hit Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets Ororon's three-hit Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the represented A1 Nightsoul-point balance. */
    public double getNightsoulPoints() {
        return nightsoulPoints;
    }

    /** Returns A1 point-granting hits consumed by the current Skill window. */
    public int getA1PointGainHitCount() {
        return a1PointGainHitCount;
    }

    /** Returns A4 Energy triggers consumed by the current Spirit Orb window. */
    public int getA4TriggerCount() {
        return a4TriggerCount;
    }

    /** Returns the active C2 Electro DMG stack count. */
    public int getC2Stacks(double currentTime) {
        if (constellation < 2
                || currentTime + EPSILON >= c2ExpirationTime) {
            return 0;
        }
        return c2Stacks;
    }

    /** Returns the active C6 ATK stack count with independent expiries. */
    public int getC6AtkStackCount(double currentTime) {
        if (constellation < 6) {
            return 0;
        }
        int count = 0;
        for (double expiration : c6StackExpirations) {
            if (currentTime + EPSILON < expiration) {
                count++;
            }
        }
        return count;
    }

    /** Returns whether the fixed target carries C1's Hypersense mark. */
    public boolean isC1MarkActive(double currentTime) {
        return constellation >= 1
                && currentTime + EPSILON < c1MarkUntil;
    }

    /** Returns the number of unresolved Ororon-owned impacts. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Reports that Nightsoul Burst team plumbing is unavailable. */
    public boolean isNightsoulBurstTeamPlumbingRepresented() {
        return false;
    }

    /** Reports that generic Nightsoul-aligned damage triggers are unavailable. */
    public boolean isNightsoulAlignedDamageTriggerRepresented() {
        return false;
    }

    /** Reports that movement and geometry are excluded. */
    public boolean isMovementGeometryRepresented() {
        return false;
    }

    /** Reports that Spirit Orb bounces and multi-target hits are excluded. */
    public boolean isMultiTargetBounceRepresented() {
        return false;
    }

    /** Reports that Supersonic Oculus taunt is excluded. */
    public boolean isTauntRepresented() {
        return false;
    }

    /** Reports that full hitlag coverage remains excluded. */
    public boolean isHitlagRepresented() {
        return false;
    }

    /** Reports that stamina consumption is excluded. */
    public boolean isStaminaRepresented() {
        return false;
    }

    /** Reports that low Plunge is excluded. */
    public boolean isLowPlungeRepresented() {
        return false;
    }

    /** Reports that aimed-shot weakspots are excluded. */
    public boolean isAimWeakspotRepresented() {
        return false;
    }

    /** Reports that target-position selection is excluded. */
    public boolean isTargetPositionSelectionRepresented() {
        return false;
    }

    /** Dispatches Ororon's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Ororon action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Ororon supports Press Skill only");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case CHARGE:
                fullyChargedAimedShot(simulator);
                break;
            case PLUNGE:
                highPlunge(simulator);
                break;
            case SKILL:
                nightsSling(simulator);
                break;
            case BURST:
                darkVoicesEcho(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Ororon: " + request.getKey());
        }
    }

    /** Triggers fixed-target Hypersense from represented charged reactions. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || result == null
                || source == null
                || nightsoulPoints + EPSILON < getTalentValue(
                        "A1 Hypersense Cost", 10.0)
                || time + EPSILON < nextHypersenseAllowedTime) {
            return;
        }
        if (result.getKind() != ReactionResult.Kind.ELECTRO_CHARGED
                && result.getKind()
                        != ReactionResult.Kind.LUNAR_CHARGED) {
            return;
        }
        nightsoulPoints -= getTalentValue("A1 Hypersense Cost", 10.0);
        nextHypersenseAllowedTime = time + getTalentValue(
                "A1 Hypersense Cooldown", 1.8);
        queueHypersense(
                simulator,
                time,
                HitKind.HYPERSENSE);
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void fullyChargedAimedShot(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 94.0 * FRAME,
                HitKind.CHARGED,
                0));
        simulator.advanceTime(85.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        AttackAction action = createAction(
                "Spiritvessel Snapshot High Plunge",
                getTalentValue("High Plunge", 2.6086),
                Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                0.0,
                58.0 * FRAME);
        simulator.performAction(characterId, action);
    }

    private void nightsSling(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueCommand(simulator, new PendingCommand(
                castTime + 14.0 * FRAME,
                CommandKind.SKILL_COOLDOWN));
        a1SkillWindowUntil = castTime + getTalentValue(
                "A1 Skill Point Window", 15.0);
        nextA1PointAllowedTime = castTime;
        a1PointGainHitCount = 0;
        queueHit(simulator, new PendingHit(
                castTime + 41.0 * FRAME,
                HitKind.SPIRIT_ORB,
                0));
        simulator.advanceTime(31.0 * FRAME);
    }

    private void darkVoicesEcho(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 3.0 * FRAME,
                CommandKind.BURST_ENERGY));
        queueHit(simulator, new PendingHit(
                castTime + 36.0 * FRAME,
                HitKind.RITUAL,
                0));
        if (constellation >= 4) {
            queueCommand(simulator, new PendingCommand(
                    castTime + 36.0 * FRAME,
                    CommandKind.C4_ENERGY));
        }
        if (constellation >= 2) {
            c2Stacks = 1;
            c2ExpirationTime = castTime
                    + getTalentValue("C2 Duration", 9.0);
        }
        double duration = getTalentValue("Burst Duration", 9.0);
        double interval = constellation >= 4
                ? getTalentValue("C4 Soundwave Interval", 0.75)
                : getTalentValue("Soundwave Interval", 1.0);
        int tick = 0;
        for (double offset = interval;
                offset + EPSILON < duration;
                offset += interval) {
            queueHit(simulator, new PendingHit(
                    castTime + offset,
                    HitKind.SOUNDWAVE,
                    tick++));
        }
        if (constellation >= 6) {
            queueHypersense(
                    simulator,
                    castTime,
                    HitKind.C6_HYPERSENSE);
        }
        simulator.advanceTime(62.0 * FRAME);
    }

    private void queueHypersense(
            CombatSimulator simulator,
            double triggerTime,
            HitKind kind) {
        if (constellation >= 6) {
            addC6AtkStack(triggerTime);
        }
        queueHit(simulator, new PendingHit(
                triggerTime + getTalentValue(
                        "A1 Hypersense Delay Frames", 12.0) * FRAME,
                kind,
                0));
    }

    private void addC6AtkStack(double currentTime) {
        double duration = getTalentValue("C6 Stack Duration", 9.0);
        int maximum = Math.min(
                c6StackExpirations.length,
                (int) getTalentValue("C6 Maximum Stacks", 3.0));
        int selected = 0;
        for (int i = 0; i < maximum; i++) {
            if (currentTime + EPSILON >= c6StackExpirations[i]) {
                c6StackExpirations[i] = Double.NEGATIVE_INFINITY;
            }
            if (c6StackExpirations[i] < c6StackExpirations[selected]) {
                selected = i;
            }
        }
        c6StackExpirations[selected] = currentTime + duration;
    }

    private void handleAcceptedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (damage <= 0.0 || actor == null || action == null) {
            return;
        }
        grantA1Points(actor, action, time);
        triggerA4Energy(simulator, actor, action, time);
        if (actor != this || action != resolvingAction) {
            return;
        }
        if (resolvingSpiritOrb) {
            if (constellation >= 1) {
                c1MarkUntil = time
                        + getTalentValue("C1 Mark Duration", 12.0);
            }
            a4WindowUntil = time
                    + getTalentValue("A4 Energy Window", 15.0);
            nextA4AllowedTime = time;
            a4TriggerCount = 0;
            queueCommand(simulator, new PendingCommand(
                    time + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE));
        }
        if (resolvingBurstHit && constellation >= 2) {
            c2Stacks = Math.min(
                    (int) getTalentValue("C2 Maximum Stacks", 4.0),
                    c2Stacks + 1);
        }
    }

    private void grantA1Points(
            Character actor,
            AttackAction action,
            double time) {
        if (actor == this
                || time + EPSILON >= a1SkillWindowUntil
                || time + EPSILON < nextA1PointAllowedTime
                || a1PointGainHitCount >= (int) getTalentValue(
                        "A1 Point Gain Hit Cap", 10.0)
                || (action.getElement() != Element.HYDRO
                        && action.getElement() != Element.ELECTRO)) {
            return;
        }
        a1PointGainHitCount++;
        nextA1PointAllowedTime = time + getTalentValue(
                "A1 Point Gain Cooldown", 0.3);
        nightsoulPoints = Math.min(
                getTalentValue("A1 Maximum Nightsoul Points", 80.0),
                nightsoulPoints + getTalentValue(
                        "A1 Points Per Hit", 5.0));
        if (a1PointGainHitCount >= (int) getTalentValue(
                "A1 Point Gain Hit Cap", 10.0)) {
            a1SkillWindowUntil = Double.NEGATIVE_INFINITY;
        }
    }

    private void triggerA4Energy(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double time) {
        if (actor != simulator.getActiveCharacter()
                || time + EPSILON >= a4WindowUntil
                || time + EPSILON < nextA4AllowedTime
                || a4TriggerCount >= (int) getTalentValue(
                        "A4 Trigger Cap", 3.0)
                || (action.getActionType() != ActionType.NORMAL
                        && action.getActionType() != ActionType.CHARGE
                        && action.getActionType() != ActionType.PLUNGE)) {
            return;
        }
        double energy = getTalentValue("A4 Energy", 3.0);
        actor.receiveFlatEnergy(energy);
        if (actor != this) {
            receiveFlatEnergy(energy);
        }
        a4TriggerCount++;
        nextA4AllowedTime = time + getTalentValue(
                "A4 Trigger Cooldown", 1.0);
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Spiritvessel Snapshot N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Spiritvessel Snapshot Fully Charged Aimed Shot",
                        getTalentValue(
                                "Fully Charged Aimed Shot", 2.108),
                        Element.ELECTRO,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case SPIRIT_ORB:
                performHit(
                        simulator,
                        hit,
                        "Night's Sling Spirit Orb",
                        skillValue(),
                        Element.ELECTRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        1.0);
                break;
            case RITUAL:
                performHit(
                        simulator,
                        hit,
                        "Dark Voices Echo Ritual",
                        burstValue("Ritual", 2.964528, 3.487680),
                        Element.ELECTRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            case SOUNDWAVE:
                performHit(
                        simulator,
                        hit,
                        "Supersonic Oculus Soundwave " + (hit.index + 1),
                        burstValue(
                                "Soundwave Collision", 0.564400, 0.664000),
                        Element.ELECTRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.OroronSoundwave,
                        ICDTag.Ororon_Soundwave,
                        1.0);
                break;
            case HYPERSENSE:
                performHit(
                        simulator,
                        hit,
                        "Hypersense",
                        getTalentValue("A1 Hypersense Multiplier", 1.6),
                        Element.ELECTRO,
                        null,
                        ActionType.OTHER,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            case C6_HYPERSENSE:
                performHit(
                        simulator,
                        hit,
                        "Hypersense C6",
                        getTalentValue("C6 Hypersense Multiplier", 3.2),
                        Element.ELECTRO,
                        null,
                        ActionType.OTHER,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            default:
                throw new IllegalStateException("Unknown Ororon hit kind");
        }
    }

    private double skillValue() {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue(
                "Spirit Orb" + suffix,
                constellation >= 3 ? 3.952000 : 3.359200);
    }

    private double burstValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(
                key + suffix,
                constellation >= 5 ? talentTwelve : talentNine);
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = createAction(
                displayName,
                multiplier,
                hitElement,
                bonusStat,
                actionType,
                icdType,
                icdTag,
                gauge,
                0.0);
        if (hit.kind == HitKind.CHARGED) {
            action.setHitlagProfile(AIMED_HEADSHOT_HITLAG);
        } else if (hit.kind == HitKind.HYPERSENSE
                || hit.kind == HitKind.C6_HYPERSENSE) {
            action.setHitlagProfile(HYPERSENSE_HITLAG);
        }
        if (getC2Stacks(hit.time) > 0) {
            action.addBonusStat(
                    StatType.ELECTRO_DMG_BONUS,
                    getC2Stacks(hit.time) * getTalentValue(
                            "C2 Electro DMG Per Stack", 0.08));
        }
        if ((hit.kind == HitKind.HYPERSENSE
                || hit.kind == HitKind.C6_HYPERSENSE)
                && isC1MarkActive(hit.time)) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    getTalentValue("C1 Hypersense DMG Bonus", 0.5));
        }
        resolvingAction = action;
        resolvingSpiritOrb = hit.kind == HitKind.SPIRIT_ORB;
        resolvingBurstHit = hit.kind == HitKind.RITUAL
                || hit.kind == HitKind.SOUNDWAVE;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingSpiritOrb = false;
            resolvingBurstHit = false;
        }
    }

    private static AttackAction createAction(
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            double duration) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                duration,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        return action;
    }

    private void queueHit(
            CombatSimulator simulator,
            PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(
            CombatSimulator simulator,
            PendingHit hit) {
        schedule(simulator, hit.time, activeSimulator -> {
            if (!pendingHits.remove(hit)) {
                return;
            }
            resolveHit(activeSimulator, hit);
        });
    }

    private void queueCommand(
            CombatSimulator simulator,
            PendingCommand command) {
        pendingCommands.add(command);
        scheduleCommand(simulator, command);
    }

    private void scheduleCommand(
            CombatSimulator simulator,
            PendingCommand command) {
        schedule(simulator, command.time, activeSimulator -> {
            if (!pendingCommands.remove(command)) {
                return;
            }
            switch (command.kind) {
                case SKILL_COOLDOWN:
                    markSkillUsed(
                            activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.ELECTRO,
                                    getTalentValue("Particle Count", 3.0),
                                    ParticleType.PARTICLE);
                    break;
                case C4_ENERGY:
                    receiveFlatEnergy(getTalentValue("C4 Energy", 8.0));
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Ororon command kind");
            }
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

    private static List<PendingHit> copyHits(List<PendingHit> source) {
        List<PendingHit> copy = new ArrayList<>();
        for (PendingHit hit : source) {
            copy.add(hit.copy());
        }
        return copy;
    }

    private static List<PendingCommand> copyCommands(
            List<PendingCommand> source) {
        List<PendingCommand> copy = new ArrayList<>();
        for (PendingCommand command : source) {
            copy.add(command.copy());
        }
        return copy;
    }

    private enum HitKind {
        NORMAL,
        CHARGED,
        SPIRIT_ORB,
        RITUAL,
        SOUNDWAVE,
        HYPERSENSE,
        C6_HYPERSENSE
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE,
        C4_ENERGY
    }

    /** Immutable delayed Ororon hit. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;

        private PendingHit(
                double time,
                HitKind kind,
                int index) {
            this.time = time;
            this.kind = kind;
            this.index = index;
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index);
        }
    }

    /** Immutable delayed Ororon state command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;

        private PendingCommand(double time, CommandKind kind) {
            this.time = time;
            this.kind = kind;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind);
        }
    }

    /** Dynamic permanent field buff backed by independently expiring C6 stacks. */
    private static final class OroronC6ActiveBuff extends Buff {
        private final Ororon owner;

        private OroronC6ActiveBuff(Ororon owner) {
            super("Night's End Active ATK", BuffId.ORORON_C6_ACTIVE_ATK);
            this.owner = owner;
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            stats.add(
                    StatType.ATK_PERCENT,
                    owner.getC6AtkStackCount(currentTime)
                            * owner.getTalentValue(
                                    "C6 ATK Per Stack", 0.1));
        }
    }

    /** Immutable snapshot of all mutable Ororon-owned simulator state. */
    private static final class OroronState implements State {
        private final Ororon owner;
        private final int normalAttackStep;
        private final double a1SkillWindowUntil;
        private final double nextA1PointAllowedTime;
        private final int a1PointGainHitCount;
        private final double nightsoulPoints;
        private final double nextHypersenseAllowedTime;
        private final double c1MarkUntil;
        private final double a4WindowUntil;
        private final double nextA4AllowedTime;
        private final int a4TriggerCount;
        private final double c2ExpirationTime;
        private final int c2Stacks;
        private final double[] c6StackExpirations;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private OroronState(
                Ororon owner,
                int normalAttackStep,
                double a1SkillWindowUntil,
                double nextA1PointAllowedTime,
                int a1PointGainHitCount,
                double nightsoulPoints,
                double nextHypersenseAllowedTime,
                double c1MarkUntil,
                double a4WindowUntil,
                double nextA4AllowedTime,
                int a4TriggerCount,
                double c2ExpirationTime,
                int c2Stacks,
                double[] c6StackExpirations,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.a1SkillWindowUntil = a1SkillWindowUntil;
            this.nextA1PointAllowedTime = nextA1PointAllowedTime;
            this.a1PointGainHitCount = a1PointGainHitCount;
            this.nightsoulPoints = nightsoulPoints;
            this.nextHypersenseAllowedTime = nextHypersenseAllowedTime;
            this.c1MarkUntil = c1MarkUntil;
            this.a4WindowUntil = a4WindowUntil;
            this.nextA4AllowedTime = nextA4AllowedTime;
            this.a4TriggerCount = a4TriggerCount;
            this.c2ExpirationTime = c2ExpirationTime;
            this.c2Stacks = c2Stacks;
            this.c6StackExpirations = Arrays.copyOf(
                    c6StackExpirations,
                    c6StackExpirations.length);
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
