package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
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
 * Prune's deterministic fixed-target Witchlure Bell support slice.
 *
 * <p>Static data follows Genshin Optimizer {@code 61c5556a}; hitmarks,
 * recovery, gauges, ICD, particles, recast timing, Burst cadence, and the
 * represented A1/C1-C6 branches follow gcsim PR #2712 head
 * {@code dda13f4f}. The initial Skill may open one converted recast for the
 * Swirled PHEC element, and converted hammer packets establish Tolling Rally
 * for every party member except Prune.</p>
 *
 * <p>Hexerei homework, Stellar-Conduct, geometry, random or multi-target
 * selection, movement, hitlag, stamina, low/high Plunge, and defensive player
 * state fail closed. C4 therefore ricochets only to the simulator's fixed
 * target, matching the source fallback when no second target exists.</p>
 */
public final class Prune extends Character implements
        CombatSimulator.ReactionListener,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 19, 24, 42 };
    private static final int[] NORMAL_DURATION_FRAMES = { 23, 49, 63 };
    private static final double[] NORMAL_T9 = {
        0.826554, 0.820774, 1.155606
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long eventGeneration;
    private double skillRecastUntil = Double.NEGATIVE_INFINITY;
    private Element convertedElement = Element.PHYSICAL;
    private double burstActiveUntil = Double.NEGATIVE_INFINITY;
    private double nextA1AllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC1AllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC4AllowedTime = Double.NEGATIVE_INFINITY;
    private double c2AttackPercent;
    private HitKind resolvingHitKind;
    private AttackAction resolvingAction;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Prune. */
    public Prune(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Prune at an explicit constellation. */
    public Prune(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Prune with injectable static talent data.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Prune(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Prune constellation must be between 0 and 6");
        }
        name = "Prune";
        characterId = CharacterId.PRUNE;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9679.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 221.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 580.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
    }

    /** Binds Prune's reaction and accepted-damage listeners once. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Prune simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Prune must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Prune cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
        simulator.addDamageListener((actor, action, damage, time) ->
                handleAcceptedDamage(
                        simulator, actor, action, damage, time));
    }

    /** Captures every Prune-owned timer, gate, and reconstructable event. */
    @Override
    public State captureCharacterState() {
        return new PruneState(
                this,
                normalAttackStep,
                eventGeneration,
                skillRecastUntil,
                convertedElement,
                burstActiveUntil,
                nextA1AllowedTime,
                nextC1AllowedTime,
                nextC4AllowedTime,
                c2AttackPercent,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Prune instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof PruneState
                && ((PruneState) state).owner == this;
    }

    /** Restores Prune-owned state and reconstructs future work. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Prune state");
        }
        initializeForSimulator(simulator);
        PruneState restored = (PruneState) state;
        normalAttackStep = restored.normalAttackStep;
        eventGeneration = Math.max(
                eventGeneration, restored.eventGeneration) + 1L;
        skillRecastUntil = restored.skillRecastUntil;
        convertedElement = restored.convertedElement;
        burstActiveUntil = restored.burstActiveUntil;
        nextA1AllowedTime = restored.nextA1AllowedTime;
        nextC1AllowedTime = restored.nextC1AllowedTime;
        nextC4AllowedTime = restored.nextC4AllowedTime;
        c2AttackPercent = restored.c2AttackPercent;
        resolvingHitKind = null;
        resolvingAction = null;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
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

    /** Returns Prune's sourced 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies Prune's live C2 owner ATK state. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (constellation >= 2
                && initializedSimulator != null
                && initializedSimulator.getCurrentTime()
                        < burstActiveUntil) {
            stats.add(StatType.ATK_PERCENT, c2AttackPercent);
        }
    }

    /** Allows one converted recast to bypass the original Skill cooldown. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (currentTime < skillRecastUntil
                && isConvertibleElement(convertedElement)) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Resets only the Normal sequence on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets only the Normal sequence on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Reports that Hexerei homework is unavailable in the typed runtime. */
    public boolean isHexereiHomeworkRepresented() {
        return false;
    }

    /** Reports that random multi-target C4 selection is excluded. */
    public boolean isRandomMultiTargetRepresented() {
        return false;
    }

    /** Returns the exact converted-Skill recast expiration timestamp. */
    public double getSkillRecastUntil() {
        return skillRecastUntil;
    }

    /** Returns the element stored by the most recent initial-Skill Swirl. */
    public Element getConvertedElement() {
        return convertedElement;
    }

    /** Returns Prune's currently live C2 ATK percent. */
    public double getC2AttackPercent(double currentTime) {
        return currentTime < burstActiveUntil ? c2AttackPercent : 0.0;
    }

    /** Returns the number of unresolved Prune-owned damage packets. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Prune's represented Normal, Charged, Skill, and Burst set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Prune action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Prune supports Press Skill only");
        }
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
                        "Unsupported action for Prune: "
                                + request.getKey());
        }
    }

    /** Captures Swirl conversion, A1, and representable C6 reaction state. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || result == null
                || result.getKind() == ReactionResult.Kind.NONE) {
            return;
        }
        if (source == this
                && result.getKind() == ReactionResult.Kind.SWIRL) {
            if (resolvingHitKind == HitKind.SKILL_INITIAL
                    && isConvertibleElement(result.getRelatedElement())) {
                convertedElement = result.getRelatedElement();
                skillRecastUntil = time + getTalentValue(
                        "Skill Recast Window Frames", 364.0) * FRAME;
            }
            if (resolvingHitKind == HitKind.BURST_TICK
                    && isConvertibleElement(result.getRelatedElement())
                    && time + EPSILON >= nextA1AllowedTime) {
                nextA1AllowedTime = time
                        + getTalentValue("A1 Cooldown", 1.2);
                queueHit(simulator, new PendingHit(
                        time + getTalentValue(
                                "A1 Delay Frames", 45.0) * FRAME,
                        HitKind.A1_HAMMER,
                        0,
                        result.getRelatedElement()));
            }
        }
        if (constellation >= 6
                && source != null
                && hasActiveBuff(
                        source, BuffId.PRUNE_TOLLING_RALLY, time)) {
            applyC6(simulator, time);
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                Element.ANEMO));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        simulator.advanceTime(NORMAL_DURATION_FRAMES[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 46.0 * FRAME,
                HitKind.CHARGED,
                0,
                Element.ANEMO));
        simulator.advanceTime(95.0 * FRAME);
    }

    private void skill(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        if (castTime < skillRecastUntil
                && isConvertibleElement(convertedElement)) {
            Element hitElement = convertedElement;
            skillRecastUntil = Double.NEGATIVE_INFINITY;
            convertedElement = Element.PHYSICAL;
            queueHit(simulator, new PendingHit(
                    castTime + 30.0 * FRAME,
                    HitKind.SKILL_CONVERTED,
                    0,
                    hitElement));
            simulator.advanceTime(65.0 * FRAME);
            return;
        }

        convertedElement = Element.PHYSICAL;
        skillRecastUntil = Double.NEGATIVE_INFINITY;
        queueCommand(simulator, new PendingCommand(
                castTime + 25.0 * FRAME,
                CommandKind.SKILL_COOLDOWN));
        queueHit(simulator, new PendingHit(
                castTime + 26.0 * FRAME,
                HitKind.SKILL_INITIAL,
                0,
                Element.ANEMO));
        simulator.advanceTime(27.0 * FRAME);
    }

    private void burst(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 16.0 * FRAME,
                CommandKind.BURST_ENERGY));
        queueHit(simulator, new PendingHit(
                castTime + 34.0 * FRAME,
                HitKind.BURST_CAST,
                0,
                Element.ANEMO));
        int durationFrames = constellation >= 6 ? 1053 : 813;
        burstActiveUntil = castTime + durationFrames * FRAME;
        c2AttackPercent = constellation >= 2
                ? getTalentValue(
                        "C2 Initial ATK Percent", 0.10) : 0.0;
        for (int frame = 137; frame < durationFrames; frame += 117) {
            queueHit(simulator, new PendingHit(
                    castTime + frame * FRAME,
                    HitKind.BURST_TICK,
                    0,
                    Element.ANEMO));
        }
        simulator.advanceTime(71.0 * FRAME);
    }

    private void handleAcceptedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (simulator != initializedSimulator
                || actor != this
                || action != resolvingAction
                || damage <= 0.0
                || resolvingHitKind == null) {
            return;
        }
        if (resolvingHitKind == HitKind.SKILL_INITIAL) {
            queueCommand(simulator, new PendingCommand(
                    time + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE));
            return;
        }
        if (!isHammer(resolvingHitKind)) {
            return;
        }
        applyTollingRally(simulator, time);
        applyC1(time);
        applyC2(time);
        if (constellation >= 4
                && resolvingHitKind != HitKind.C4_RICOCHET_SKILL
                && resolvingHitKind != HitKind.C4_RICOCHET_BURST
                && time + EPSILON >= nextC4AllowedTime) {
            nextC4AllowedTime = time
                    + getTalentValue("C4 Cooldown", 0.1);
            queueHit(simulator, new PendingHit(
                    time + getTalentValue(
                            "C4 Delay Frames", 63.0) * FRAME,
                    resolvingHitKind == HitKind.SKILL_CONVERTED
                            ? HitKind.C4_RICOCHET_SKILL
                            : HitKind.C4_RICOCHET_BURST,
                    0,
                    action.getElement()));
        }
    }

    private void applyTollingRally(
            CombatSimulator simulator,
            double currentTime) {
        StatsContainer stats = captureLiveStats(currentTime);
        double threshold = getTalentValue("A4 ATK Threshold", 2000.0);
        double ratio = getTalentValue("A4 Bonus Per ATK", 0.00025);
        double cap = getTalentValue("A4 Maximum Bonus", 0.5);
        double bonus = Math.min(
                cap,
                Math.max(0.0, stats.getTotalAtk() - threshold) * ratio);
        double duration = getTalentValue("A4 Duration", 5.0);
        for (Character member : simulator.getPartyMembers()) {
            if (member == this) {
                continue;
            }
            member.removeBuff(BuffId.PRUNE_TOLLING_RALLY);
            member.addBuff(new SimpleBuff(
                    "Prune Tolling Rally",
                    BuffId.PRUNE_TOLLING_RALLY,
                    duration,
                    currentTime,
                    target -> target.add(
                            StatType.DMG_BONUS_ALL, bonus))
                    .sourcedBy(characterId));
        }
    }

    private void applyC1(double currentTime) {
        if (constellation < 1
                || currentTime + EPSILON < nextC1AllowedTime) {
            return;
        }
        nextC1AllowedTime = currentTime
                + getTalentValue("C1 Cooldown", 1.8);
        receiveFlatEnergy(getTalentValue("C1 Flat Energy", 2.0));
    }

    private void applyC2(double currentTime) {
        if (constellation < 2 || currentTime >= burstActiveUntil) {
            return;
        }
        c2AttackPercent = Math.min(
                getTalentValue("C2 Maximum ATK Percent", 0.4),
                c2AttackPercent + getTalentValue(
                        "C2 ATK Percent Per Hit", 0.05));
    }

    private void applyC6(
            CombatSimulator simulator,
            double currentTime) {
        double amount = getTalentValue("C6 Flat ATK", 350.0);
        double duration = getTalentValue("C6 Duration", 5.0);
        for (Character member : simulator.getPartyMembers()) {
            member.removeBuff(BuffId.PRUNE_C6_ATK);
            if (member == this) {
                member.addBuff(new SimpleBuff(
                        "Prune C6 ATK",
                        BuffId.PRUNE_C6_ATK,
                        duration,
                        currentTime,
                        stats -> stats.add(StatType.ATK_FLAT, amount))
                        .sourcedBy(characterId));
                continue;
            }
            Character target = member;
            member.addBuff(new Buff(
                    "Prune C6 Active ATK",
                    BuffId.PRUNE_C6_ATK,
                    duration,
                    currentTime) {
                @Override
                protected void applyStats(
                        StatsContainer stats,
                        double time) {
                    if (simulator.getActiveCharacter() == target
                            && hasActiveBuff(
                                    target,
                                    BuffId.PRUNE_TOLLING_RALLY,
                                    time)) {
                        stats.add(StatType.ATK_FLAT, amount);
                    }
                }
            }.sourcedBy(characterId));
        }
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if (simulator.getEnemy() == null) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator, hit,
                        "Badaboom Hexbuster Hammer N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0);
                break;
            case CHARGED:
                performHit(
                        simulator, hit,
                        "Badaboom Hexbuster Hammer Charged Attack",
                        getTalentValue("Charged Attack", 2.269840),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            case SKILL_INITIAL:
                performHit(
                        simulator, hit,
                        "Ring-A-Ding-Ding Hexhunter Chime",
                        skillValue(false),
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            case SKILL_CONVERTED:
                performHit(
                        simulator, hit,
                        "Clang Clang Witch-tribution Comes",
                        skillValue(true),
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            case BURST_CAST:
                performHit(
                        simulator, hit,
                        "The Bell Tolls The Hunt Is On",
                        burstValue(false),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            case BURST_TICK:
                performHit(
                        simulator, hit,
                        "Witchlure Bell",
                        burstValue(true),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            case A1_HAMMER:
                performHit(
                        simulator, hit,
                        "Verdict and Punishment",
                        getTalentValue("A1 ATK Ratio", 1.5),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        0.0);
                break;
            case C4_RICOCHET_SKILL:
                performHit(
                        simulator, hit,
                        "Banehunter Oathhammer Ricochet",
                        getTalentValue("C4 ATK Ratio", 0.8),
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None,
                        0.0);
                break;
            case C4_RICOCHET_BURST:
                performHit(
                        simulator, hit,
                        "Banehunter Oathhammer Ricochet",
                        getTalentValue("C4 ATK Ratio", 0.8),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        0.0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Prune hit kind " + hit.kind);
        }
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hit.element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setStatSnapshot(captureLiveStats(
                simulator.getCurrentTime()));
        resolvingHitKind = hit.kind;
        resolvingAction = action;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingHitKind = null;
            resolvingAction = null;
        }
    }

    private double skillValue(boolean converted) {
        boolean c5 = constellation >= 5;
        if (converted) {
            return getTalentValue(
                    c5 ? "Witch-tribution C5" : "Witch-tribution",
                    c5 ? 4.091200 : 3.477520);
        }
        return getTalentValue(
                c5 ? "Hexhunter Chime C5" : "Hexhunter Chime",
                c5 ? 3.348800 : 2.846480);
    }

    private double burstValue(boolean periodic) {
        boolean c3 = constellation >= 3;
        if (periodic) {
            return getTalentValue(
                    c3 ? "Witchlure Bell C3" : "Witchlure Bell",
                    c3 ? 1.408800 : 1.197480);
        }
        return getTalentValue(
                c3 ? "Bell Cast C3" : "Bell Cast",
                c3 ? 1.939200 : 1.648320);
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator == null) {
            return stats;
        }
        for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
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
        long scheduledGeneration = eventGeneration;
        schedule(simulator, hit.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingHits.remove(hit)) {
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
        long scheduledGeneration = eventGeneration;
        schedule(simulator, command.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingCommands.remove(command)) {
                return;
            }
            switch (command.kind) {
                case SKILL_COOLDOWN:
                    markSkillUsed(
                            command.time,
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(command.time);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.ANEMO,
                                    getTalentValue(
                                            "Particle Count", 5.0),
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Prune command " + command.kind);
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

    private static boolean isHammer(HitKind kind) {
        return kind == HitKind.SKILL_CONVERTED
                || kind == HitKind.A1_HAMMER
                || kind == HitKind.C4_RICOCHET_SKILL
                || kind == HitKind.C4_RICOCHET_BURST;
    }

    private static boolean isConvertibleElement(Element element) {
        return element == Element.PYRO
                || element == Element.HYDRO
                || element == Element.ELECTRO
                || element == Element.CRYO;
    }

    private static boolean hasActiveBuff(
            Character character,
            BuffId id,
            double currentTime) {
        for (Buff buff : character.getActiveBuffs()) {
            if (buff.getId() == id && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
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
        SKILL_INITIAL,
        SKILL_CONVERTED,
        BURST_CAST,
        BURST_TICK,
        A1_HAMMER,
        C4_RICOCHET_SKILL,
        C4_RICOCHET_BURST
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final Element element;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                Element element) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.element = element;
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, element);
        }
    }

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

    private static final class PruneState implements State {
        private final Prune owner;
        private final int normalAttackStep;
        private final long eventGeneration;
        private final double skillRecastUntil;
        private final Element convertedElement;
        private final double burstActiveUntil;
        private final double nextA1AllowedTime;
        private final double nextC1AllowedTime;
        private final double nextC4AllowedTime;
        private final double c2AttackPercent;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private PruneState(
                Prune owner,
                int normalAttackStep,
                long eventGeneration,
                double skillRecastUntil,
                Element convertedElement,
                double burstActiveUntil,
                double nextA1AllowedTime,
                double nextC1AllowedTime,
                double nextC4AllowedTime,
                double c2AttackPercent,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.eventGeneration = eventGeneration;
            this.skillRecastUntil = skillRecastUntil;
            this.convertedElement = convertedElement;
            this.burstActiveUntil = burstActiveUntil;
            this.nextA1AllowedTime = nextA1AllowedTime;
            this.nextC1AllowedTime = nextC1AllowedTime;
            this.nextC4AllowedTime = nextC4AllowedTime;
            this.c2AttackPercent = c2AttackPercent;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
