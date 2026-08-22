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
import simulation.action.HitlagProfile;
import simulation.event.SimpleTimerEvent;

/**
 * Sethos's fixed-target Shadowpiercing and Dusk Bolt offensive slice.
 *
 * <p>Normal, aimed, Skill, Burst, Energy, particle, passive, and represented
 * constellation values follow pinned gcsim {@code ef41805d}. The generic
 * {@link CharacterActionKey#CHARGE} route selects Shadowpiercing Shot, matching
 * gcsim's default hold level; {@link #performFullyChargedAimedShot} exposes the
 * distinct level-one charged shot without adding a repository-wide aim mode.</p>
 *
 * <p>Weak points, aiming input, projectile geometry, piercing additional
 * targets, movement, hitlag, and the two-target C4 trigger are intentionally
 * excluded. C4 is not approximated in this single-target runtime.</p>
 */
public final class Sethos extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PROJECTILE_TRAVEL = 10.0 * FRAME;
    /** Weak-point hitlag pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile AIMED_HEADSHOT_HITLAG =
            new HitlagProfile(0.12, 0.01, false, true, true);
    private static final int[][] NORMAL_RELEASE_FRAMES = {
        { 10 }, { 12, 15 }, { 39 }
    };
    private static final int[] NORMAL_DURATIONS = { 19, 35, 69 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2 Hit 1", "N2 Hit 2" }, { "N3" }
    };
    private static final double[][] NORMAL_T9 = {
        { 0.966628 }, { 0.437186, 0.488852 }, { 1.359290 }
    };
    private static final double[][] NORMAL_C3 = {
        { 1.186873 }, { 0.536798, 0.600236 }, { 1.669001 }
    };
    private static final Set<ReactionResult.Kind> SKILL_REFUND_REACTIONS =
            EnumSet.of(
                    ReactionResult.Kind.OVERLOAD,
                    ReactionResult.Kind.OVERLOADED,
                    ReactionResult.Kind.ELECTRO_CHARGED,
                    ReactionResult.Kind.LUNAR_CHARGED,
                    ReactionResult.Kind.SUPERCONDUCT,
                    ReactionResult.Kind.HYPERBLOOM,
                    ReactionResult.Kind.QUICKEN,
                    ReactionResult.Kind.AGGRAVATE);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double burstStartTime = Double.POSITIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private double c2ConsumingExpirationTime = Double.NEGATIVE_INFINITY;
    private double c2RegainingExpirationTime = Double.NEGATIVE_INFINITY;
    private double c2BurstExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean a4Available = true;
    private int a4HitCount;
    private double a4ExpirationTime = Double.POSITIVE_INFINITY;
    private double a4RefreshTime = Double.NEGATIVE_INFINITY;
    private double c6NextAllowedTime = Double.NEGATIVE_INFINITY;
    private boolean resolvingSkillHit;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Sethos. */
    public Sethos(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Sethos at an explicit constellation. */
    public Sethos(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Sethos with injectable talent data and constellation. */
    public Sethos(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Sethos constellation must be between 0 and 6");
        }
        name = "Sethos";
        characterId = CharacterId.SETHOS;
        element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9787.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 227.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 560.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Ascension Elemental Mastery", 96.0));
        setSkillCD(getTalentValue("Skill Cooldown", 8.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Sethos reaction and delayed-event state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Sethos simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Sethos cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Sethos must belong to the target simulator party");
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
    }

    /** Captures all Sethos-owned windows, counters, and pending work. */
    @Override
    public State captureCharacterState() {
        return new SethosState(
                this,
                normalAttackStep,
                burstStartTime,
                burstExpirationTime,
                c2ConsumingExpirationTime,
                c2RegainingExpirationTime,
                c2BurstExpirationTime,
                a4Available,
                a4HitCount,
                a4ExpirationTime,
                a4RefreshTime,
                c6NextAllowedTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Sethos instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof SethosState
                && ((SethosState) state).owner == this;
    }

    /** Restores mutable state and re-registers each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Sethos state");
        }
        initializeForSimulator(simulator);
        SethosState restored = (SethosState) state;
        normalAttackStep = restored.normalAttackStep;
        burstStartTime = restored.burstStartTime;
        burstExpirationTime = restored.burstExpirationTime;
        c2ConsumingExpirationTime = restored.c2ConsumingExpirationTime;
        c2RegainingExpirationTime = restored.c2RegainingExpirationTime;
        c2BurstExpirationTime = restored.c2BurstExpirationTime;
        a4Available = restored.a4Available;
        a4HitCount = restored.a4HitCount;
        a4ExpirationTime = restored.a4ExpirationTime;
        a4RefreshTime = restored.a4RefreshTime;
        c6NextAllowedTime = restored.c6NextAllowedTime;
        resolvingSkillHit = false;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        double currentTime = simulator.getCurrentTime();
        pendingHits.removeIf(hit -> hit.time < currentTime - EPSILON);
        pendingCommands.removeIf(command ->
                command.time < currentTime - EPSILON);
        for (PendingHit hit : new ArrayList<>(pendingHits)) {
            scheduleHit(simulator, hit);
        }
        for (PendingCommand command : new ArrayList<>(pendingCommands)) {
            scheduleCommand(simulator, command);
        }
    }

    /** Returns Sethos's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Sethos's represented passives are evaluated at action boundaries. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Cancels Twilight Meditation and resets the Normal string. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        burstStartTime = Double.POSITIVE_INFINITY;
        burstExpirationTime = Double.NEGATIVE_INFINITY;
    }

    /** Returns whether Twilight Meditation is active at this timestamp. */
    public boolean isBurstActive(double currentTime) {
        return currentTime + EPSILON >= burstStartTime
                && currentTime + EPSILON < burstExpirationTime;
    }

    /** Returns the active C2 stack count, capped at two. */
    public int getC2Stacks(double currentTime) {
        if (constellation < 2) {
            return 0;
        }
        int stacks = 0;
        if (currentTime + EPSILON < c2ConsumingExpirationTime) {
            stacks++;
        }
        if (currentTime + EPSILON < c2RegainingExpirationTime) {
            stacks++;
        }
        if (currentTime + EPSILON < c2BurstExpirationTime) {
            stacks++;
        }
        return Math.min(
                (int) getTalentValue("C2 Maximum Stacks", 2.0),
                stacks);
    }

    /** Returns how many A4-enhanced Shadowpiercing hits have connected. */
    public int getA4HitCount() {
        return a4HitCount;
    }

    /** Returns the next timestamp at which C6 may restore Energy. */
    public double getC6NextAllowedTime() {
        return c6NextAllowedTime;
    }

    /** Returns the number of unresolved Sethos-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /**
     * Executes the distinct level-one fully charged aimed shot.
     *
     * <p>The shared action request has one Charged key, which is reserved for
     * Shadowpiercing because that is gcsim's default hold mode.</p>
     */
    public void performFullyChargedAimedShot(CombatSimulator simulator) {
        initializeForSimulator(simulator);
        if (isBurstActive(simulator.getCurrentTime())) {
            throw new IllegalStateException(
                    "Sethos cannot aim during Twilight Meditation");
        }
        normalAttackStep = 0;
        queueAimedShot(simulator, false);
    }

    /** Applies the 12 flat Energy refund for an eligible Skill reaction. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || source != this
                || !resolvingSkillHit
                || result == null
                || !SKILL_REFUND_REACTIONS.contains(result.getKind())) {
            return;
        }
        receiveFlatEnergy(getTalentValue(
                "Skill Energy Regeneration", 12.0));
        if (constellation >= 2) {
            c2RegainingExpirationTime = time
                    + getTalentValue("C2 Stack Duration", 10.0);
        }
    }

    /** Dispatches Normal, Shadowpiercing, Press Skill, and Burst. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Sethos action is required");
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
                if (isBurstActive(simulator.getCurrentTime())) {
                    throw new IllegalStateException(
                            "Sethos cannot aim during Twilight Meditation");
                }
                queueAimedShot(simulator, true);
                break;
            case SKILL:
                ancientRite(simulator);
                break;
            case BURST:
                secretRite(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Sethos: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_RELEASE_FRAMES[step].length; hit++) {
            queueCommand(simulator, new PendingCommand(
                    castTime + NORMAL_RELEASE_FRAMES[step][hit] * FRAME,
                    CommandKind.NORMAL_RELEASE,
                    step,
                    hit,
                    0.0));
        }
        normalAttackStep = (normalAttackStep + 1) % 3;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void queueAimedShot(
            CombatSimulator simulator,
            boolean shadowpiercing) {
        double castTime = simulator.getCurrentTime();
        double consideredEnergy = Math.min(
                getCurrentEnergy(),
                getTalentValue("A1 Energy Cap", 20.0));
        int reductionFrames = (int) Math.floor(
                getTalentValue(
                        "A1 Charge Reduction Per Energy", 0.285)
                        * consideredEnergy * 60.0
                        + EPSILON);
        int hitmark = shadowpiercing ? 368 : 74;
        int duration = shadowpiercing ? 379 : 83;
        int maximumReduction = hitmark - 16;
        reductionFrames = Math.min(reductionFrames, maximumReduction);
        queueCommand(simulator, new PendingCommand(
                castTime + (hitmark - reductionFrames) * FRAME,
                shadowpiercing
                        ? CommandKind.SHADOW_RELEASE
                        : CommandKind.CHARGED_RELEASE,
                0,
                0,
                consideredEnergy));
        simulator.advanceTime((duration - reductionFrames) * FRAME);
    }

    private void ancientRite(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueCommand(simulator, new PendingCommand(
                castTime + 10.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0,
                0,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 13.0 * FRAME,
                CommandKind.SKILL_RELEASE,
                0,
                0,
                0.0));
        simulator.advanceTime(38.0 * FRAME);
    }

    private void secretRite(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        burstStartTime = castTime;
        burstExpirationTime = castTime
                + getTalentValue("Burst Duration", 8.0);
        if (constellation >= 2) {
            c2BurstExpirationTime = castTime
                    + getTalentValue("C2 Stack Duration", 10.0);
        }
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0,
                0,
                0.0));
        simulator.advanceTime(50.0 * FRAME);
    }

    private void releaseNormal(
            CombatSimulator simulator,
            int step,
            int hitIndex) {
        boolean enhanced = isBurstActive(simulator.getCurrentTime());
        StatsContainer snapshot = captureActionStats(
                simulator.getCurrentTime());
        if (enhanced) {
            double elementalMastery = snapshot.get(
                    StatType.ELEMENTAL_MASTERY);
            snapshot.add(
                    StatType.FLAT_DMG_BONUS,
                    duskBoltElementalMasteryRatio() * elementalMastery);
        }
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + PROJECTILE_TRAVEL,
                HitKind.NORMAL,
                step,
                hitIndex,
                enhanced,
                false,
                0.0,
                snapshot));
    }

    private void releaseAimedShot(
            CombatSimulator simulator,
            boolean shadowpiercing,
            double consideredEnergy) {
        double ratio = getTalentValue(
                shadowpiercing
                        ? "A1 Shadowpiercing Energy Ratio"
                        : "A1 Fully Charged Energy Ratio",
                shadowpiercing ? 1.0 : 0.5);
        double consumedEnergy = consideredEnergy * ratio;
        spendEnergy(consumedEnergy);
        if (constellation >= 2) {
            c2ConsumingExpirationTime = simulator.getCurrentTime()
                    + getTalentValue("C2 Stack Duration", 10.0);
        }
        StatsContainer snapshot = captureActionStats(
                simulator.getCurrentTime());
        boolean a4Applied = false;
        if (shadowpiercing) {
            refreshA4(simulator.getCurrentTime());
            a4Applied = a4Available;
            double elementalMastery = snapshot.get(
                    StatType.ELEMENTAL_MASTERY);
            snapshot.add(
                    StatType.FLAT_DMG_BONUS,
                    shadowpiercingElementalMasteryRatio()
                            * elementalMastery);
            if (a4Applied) {
                snapshot.add(
                        StatType.FLAT_DMG_BONUS,
                        getTalentValue(
                                "A4 Elemental Mastery Ratio", 7.0)
                                * elementalMastery);
            }
        }
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + PROJECTILE_TRAVEL,
                shadowpiercing ? HitKind.SHADOW : HitKind.CHARGED,
                0,
                0,
                false,
                a4Applied,
                consumedEnergy,
                snapshot));
    }

    private void releaseSkill(CombatSimulator simulator) {
        resolveHit(simulator, new PendingHit(
                simulator.getCurrentTime(),
                HitKind.SKILL,
                0,
                0,
                false,
                false,
                0.0,
                captureActionStats(simulator.getCurrentTime())));
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        AttackAction action;
        switch (hit.kind) {
            case NORMAL:
                action = normalAction(hit);
                break;
            case CHARGED:
                action = attack(
                        "Royal Reed Archery Fully Charged Aimed Shot",
                        fullyChargedMultiplier(),
                        Element.ELECTRO,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            case SHADOW:
                action = attack(
                        "Royal Reed Archery Shadowpiercing Shot",
                        shadowpiercingAttackMultiplier(),
                        Element.ELECTRO,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None,
                        2.0);
                if (constellation >= 1) {
                    action.addBonusStat(
                            StatType.CRIT_RATE,
                            getTalentValue(
                                    "C1 Shadowpiercing CRIT Rate", 0.15));
                }
                break;
            case SKILL:
                action = attack(
                        "Ancient Rite: Thunderous Roar of Sand",
                        getTalentValue("Ancient Rite", 1.965200),
                        Element.ELECTRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Sethos hit kind " + hit.kind);
        }
        action.setStatSnapshot(hit.snapshot);
        if (hit.kind == HitKind.CHARGED || hit.kind == HitKind.SHADOW) {
            action.setHitlagProfile(AIMED_HEADSHOT_HITLAG);
        }
        if (hit.kind == HitKind.SKILL) {
            resolvingSkillHit = true;
        }
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingSkillHit = false;
        }
        if (hit.kind == HitKind.SKILL && simulator.getEnemy() != null) {
            queueCommand(simulator, new PendingCommand(
                    hit.time + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE,
                    0,
                    0,
                    getTalentValue("Skill Particle Count", 2.0)));
        }
        if (hit.kind == HitKind.SHADOW) {
            consumeA4OnHit(hit.a4Applied, hit.time);
            restoreC6Energy(hit.consumedEnergy, hit.time);
        }
    }

    private AttackAction normalAction(PendingHit hit) {
        String key = NORMAL_KEYS[hit.index][hit.subIndex];
        double multiplier = constellation >= 3
                ? NORMAL_C3[hit.index][hit.subIndex]
                : NORMAL_T9[hit.index][hit.subIndex];
        String dataKey = constellation >= 3 ? key + " C3" : key;
        return attack(
                hit.burstEnhanced
                        ? "Dusk Bolt " + key
                        : "Royal Reed Archery " + key,
                getTalentValue(dataKey, multiplier),
                hit.burstEnhanced ? Element.ELECTRO : Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                hit.burstEnhanced
                        ? ICDTag.ElementalBurst : ICDTag.None,
                hit.burstEnhanced ? 1.0 : 0.0);
    }

    private void consumeA4OnHit(boolean applied, double currentTime) {
        refreshA4(currentTime);
        if (!applied || !a4Available) {
            return;
        }
        if (a4HitCount == 0) {
            a4ExpirationTime = currentTime
                    + getTalentValue("A4 Window Duration", 5.0);
            a4RefreshTime = currentTime
                    + getTalentValue("A4 Refresh Cooldown", 15.0);
        }
        a4HitCount++;
        if (a4HitCount >= (int) getTalentValue("A4 Shot Count", 4.0)) {
            a4Available = false;
        }
    }

    private void refreshA4(double currentTime) {
        if (!a4Available
                && currentTime + EPSILON >= a4RefreshTime) {
            a4Available = true;
            a4HitCount = 0;
            a4ExpirationTime = Double.POSITIVE_INFINITY;
            return;
        }
        if (a4Available
                && a4HitCount > 0
                && currentTime + EPSILON >= a4ExpirationTime) {
            a4Available = false;
        }
    }

    private void restoreC6Energy(
            double consumedEnergy,
            double currentTime) {
        if (constellation < 6
                || currentTime + EPSILON < c6NextAllowedTime) {
            return;
        }
        c6NextAllowedTime = currentTime
                + getTalentValue("C6 Energy Restore Cooldown", 15.0);
        receiveFlatEnergy(consumedEnergy);
    }

    private StatsContainer captureActionStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        int c2Stacks = getC2Stacks(currentTime);
        if (c2Stacks > 0) {
            stats.add(
                    StatType.ELECTRO_DMG_BONUS,
                    c2Stacks * getTalentValue(
                            "C2 Electro DMG Per Stack", 0.15));
        }
        return stats;
    }

    private double fullyChargedMultiplier() {
        return getTalentValue(
                constellation >= 3
                        ? "Fully Charged Aimed Shot C3"
                        : "Fully Charged Aimed Shot",
                constellation >= 3 ? 2.480000 : 2.108000);
    }

    private double shadowpiercingAttackMultiplier() {
        return getTalentValue(
                constellation >= 3
                        ? "Shadowpiercing ATK C3"
                        : "Shadowpiercing ATK",
                constellation >= 3 ? 2.800000 : 2.380000);
    }

    private double shadowpiercingElementalMasteryRatio() {
        return getTalentValue(
                constellation >= 3
                        ? "Shadowpiercing Elemental Mastery C3"
                        : "Shadowpiercing Elemental Mastery",
                constellation >= 3 ? 2.691200 : 2.287520);
    }

    private double duskBoltElementalMasteryRatio() {
        return getTalentValue(
                constellation >= 5
                        ? "Dusk Bolt Elemental Mastery C5"
                        : "Dusk Bolt Elemental Mastery",
                constellation >= 5 ? 3.923200 : 3.334720);
    }

    private void queueHit(CombatSimulator simulator, PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(CombatSimulator simulator, PendingHit hit) {
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
                case NORMAL_RELEASE:
                    releaseNormal(
                            activeSimulator,
                            command.index,
                            command.subIndex);
                    break;
                case CHARGED_RELEASE:
                    releaseAimedShot(
                            activeSimulator, false, command.value);
                    break;
                case SHADOW_RELEASE:
                    releaseAimedShot(
                            activeSimulator, true, command.value);
                    break;
                case SKILL_COOLDOWN:
                    markSkillUsed(
                            activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case SKILL_RELEASE:
                    releaseSkill(activeSimulator);
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.ELECTRO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Sethos command kind " + command.kind);
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

    private static AttackAction attack(
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        return action;
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
        SHADOW,
        SKILL
    }

    private enum CommandKind {
        NORMAL_RELEASE,
        CHARGED_RELEASE,
        SHADOW_RELEASE,
        SKILL_COOLDOWN,
        SKILL_RELEASE,
        BURST_ENERGY,
        PARTICLE
    }

    /** Immutable future Sethos damage hit with release-time stats. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final boolean burstEnhanced;
        private final boolean a4Applied;
        private final double consumedEnergy;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                boolean burstEnhanced,
                boolean a4Applied,
                double consumedEnergy,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.burstEnhanced = burstEnhanced;
            this.a4Applied = a4Applied;
            this.consumedEnergy = consumedEnergy;
            this.snapshot = snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    subIndex,
                    burstEnhanced,
                    a4Applied,
                    consumedEnergy,
                    snapshot);
        }
    }

    /** Immutable future Sethos state command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final int index;
        private final int subIndex;
        private final double value;

        private PendingCommand(
                double time,
                CommandKind kind,
                int index,
                int subIndex,
                double value) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time, kind, index, subIndex, value);
        }
    }

    /** Immutable snapshot of all mutable Sethos-owned simulator state. */
    private static final class SethosState implements State {
        private final Sethos owner;
        private final int normalAttackStep;
        private final double burstStartTime;
        private final double burstExpirationTime;
        private final double c2ConsumingExpirationTime;
        private final double c2RegainingExpirationTime;
        private final double c2BurstExpirationTime;
        private final boolean a4Available;
        private final int a4HitCount;
        private final double a4ExpirationTime;
        private final double a4RefreshTime;
        private final double c6NextAllowedTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private SethosState(
                Sethos owner,
                int normalAttackStep,
                double burstStartTime,
                double burstExpirationTime,
                double c2ConsumingExpirationTime,
                double c2RegainingExpirationTime,
                double c2BurstExpirationTime,
                boolean a4Available,
                int a4HitCount,
                double a4ExpirationTime,
                double a4RefreshTime,
                double c6NextAllowedTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.burstStartTime = burstStartTime;
            this.burstExpirationTime = burstExpirationTime;
            this.c2ConsumingExpirationTime = c2ConsumingExpirationTime;
            this.c2RegainingExpirationTime = c2RegainingExpirationTime;
            this.c2BurstExpirationTime = c2BurstExpirationTime;
            this.a4Available = a4Available;
            this.a4HitCount = a4HitCount;
            this.a4ExpirationTime = a4ExpirationTime;
            this.a4RefreshTime = a4RefreshTime;
            this.c6NextAllowedTime = c6NextAllowedTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
