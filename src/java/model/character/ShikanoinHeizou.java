package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
 * Shikanoin Heizou's stationary single-target Anemo kit through C6.
 *
 * <p>Attack frames, Declension, Conviction, particles, Iris behavior, and
 * constellations follow pinned gcsim {@code ef41805d}. Hold Skill releases at
 * four stacks, while Burst tags the one simulated target and captures its live
 * aura before the Anemo impact.</p>
 *
 * <p>C2 suction, multi-target Iris tagging, stamina, geometry, hitlag, and
 * airborne Plunge validation are outside this vertical slice.</p>
 */
public final class ShikanoinHeizou extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[][] NORMAL_HITLAG = {
        { new HitlagProfile(0.03, 0.01, true, false, false) },
        { new HitlagProfile(0.03, 0.01, true, false, false) },
        { new HitlagProfile(0.06, 0.01, true, false, false) },
        {
            HitlagProfile.none(), HitlagProfile.none(),
            new HitlagProfile(0.09, 0.01, true, false, false)
        },
        { new HitlagProfile(0.12, 0.01, true, false, false) }
    };
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.09, 0.01, false, false, false);
    private static final HitlagProfile SKILL_HITLAG =
            new HitlagProfile(0.09, 0.01, false, false, false);
    private static final HitlagProfile MAX_SKILL_HITLAG =
            new HitlagProfile(0.12, 0.01, false, false, false);
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 12 }, { 13 }, { 21 }, { 13, 19, 27 }, { 31 }
    };
    private static final int[] NORMAL_DURATION_FRAMES = { 21, 21, 46, 38, 66 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3" },
        { "N4-1", "N4-2", "N4-3" }, { "N5" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.637051 }, { 0.626484 }, { 0.868020 },
        { 0.251301, 0.276434, 0.326699 }, { 1.044643 }
    };
    private static final Element[] IRIS_PRIORITY = {
        Element.PYRO, Element.HYDRO, Element.ELECTRO, Element.CRYO
    };

    private final DoubleSupplier particleDrawSource;
    private final List<Double> particleDrawTape = new ArrayList<>();
    private CombatSimulator initializedSimulator;
    private int particleDrawCursor;
    private int normalAttackStep;
    private int declensionStacks;
    private double nextA1Time = Double.NEGATIVE_INFINITY;
    private double nextC1Time = Double.NEGATIVE_INFINITY;
    private AttackAction resolvingSkillAction;
    private Element lastIrisElement;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Heizou. */
    public ShikanoinHeizou(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Heizou at an explicit constellation. */
    public ShikanoinHeizou(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                Math::random);
    }

    /** Constructs Heizou with an injectable particle draw source. */
    public ShikanoinHeizou(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation,
            DoubleSupplier particleDrawSource) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                particleDrawSource);
    }

    /** Constructs Heizou with injectable talent data and particle draws. */
    public ShikanoinHeizou(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleDrawSource) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Shikanoin Heizou constellation must be between 0 and 6");
        }
        if (particleDrawSource == null) {
            throw new IllegalArgumentException(
                    "Shikanoin Heizou particle draw source is required");
        }
        name = "Shikanoin Heizou";
        characterId = CharacterId.SHIKANOIN_HEIZOU;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleDrawSource = particleDrawSource;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10657.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 225.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 684.0));
        baseStats.add(StatType.ANEMO_DMG_BONUS,
                getTalentValue("Ascension Anemo DMG Bonus", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 10.0));
        setBurstCD(getTalentValue("Burst Cooldown", 12.0));
    }

    /** Binds reaction, accepted-hit, and delayed-work state once. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Shikanoin Heizou simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Shikanoin Heizou cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Shikanoin Heizou must belong to the target simulator party");
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor == this
                    && action == resolvingSkillAction
                    && damage > 0.0) {
                applyA4(simulator, time);
            }
        });
    }

    /** Captures counters, gates, random draws, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new HeizouState(
                this,
                particleDrawCursor,
                normalAttackStep,
                declensionStacks,
                nextA1Time,
                nextC1Time,
                lastIrisElement,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Heizou instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof HeizouState
                && ((HeizouState) state).owner == this;
    }

    /** Restores owner state and reconstructs each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Shikanoin Heizou state");
        }
        initializeForSimulator(simulator);
        HeizouState restored = (HeizouState) state;
        particleDrawCursor = restored.particleDrawCursor;
        normalAttackStep = restored.normalAttackStep;
        declensionStacks = restored.declensionStacks;
        nextA1Time = restored.nextA1Time;
        nextC1Time = restored.nextC1Time;
        lastIrisElement = restored.lastIrisElement;
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

    /** Returns Heizou's 40-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 40.0);
    }

    /** Heizou's permanent Anemo ascension is loaded into base stats. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A1/A4 and constellations depend on runtime events.
    }

    /** Supports Press and Hold Heartstopper Strike inputs. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS || mode == SkillActionMode.HOLD;
    }

    /** Resets the five-step Normal string when Heizou leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Applies C1 on entry with its independent ten-second gate. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        if (constellation < 1
                || simulator != initializedSimulator
                || simulator.getCurrentTime() < nextC1Time) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        nextC1Time = currentTime + getTalentValue("C1 Cooldown", 10.0);
        addDeclensionStack();
        removeBuff(BuffId.HEIZOU_C1_NORMAL_ATTACK_SPEED);
        addBuff(new SimpleBuff(
                "Shikanoin Heizou C1 Normal Attack Speed",
                BuffId.HEIZOU_C1_NORMAL_ATTACK_SPEED,
                getTalentValue("C1 Duration", 5.0),
                currentTime,
                stats -> stats.add(
                        StatType.NORMAL_ATTACK_SPD,
                        getTalentValue("C1 Normal Attack Speed", 0.15)))
                .sourcedBy(characterId));
    }

    /** Gains one Declension stack from an active-owner Swirl on a 0.1s gate. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || source != this
                || result == null
                || !result.isSwirl()
                || simulator.getActiveCharacter() != this
                || time < nextA1Time) {
            return;
        }
        nextA1Time = time + getTalentValue("A1 Cooldown", 0.1);
        addDeclensionStack();
    }

    /** Returns the current Declension stack count. */
    public int getDeclensionStacks() {
        return declensionStacks;
    }

    /** Returns the next timestamp at which A1 may gain a stack. */
    public double getNextA1Time() {
        return nextA1Time;
    }

    /** Returns the next timestamp at which C1 may activate on entry. */
    public double getNextC1Time() {
        return nextC1Time;
    }

    /** Returns the element selected by the latest valid Windmuster Iris. */
    public Element getLastIrisElement() {
        return lastIrisElement;
    }

    /** Reports the explicit fixed-target boundary for C2 suction. */
    public boolean isC2SuctionRepresented() {
        return false;
    }

    /** Dispatches Heizou's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Shikanoin Heizou action is required");
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
            case PLUNGE:
                highPlunge(simulator);
                break;
            case SKILL:
                heartstopperStrike(simulator, request.getSkillMode());
                break;
            case BURST:
                windmusterKick(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Shikanoin Heizou: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    HitKind.NORMAL,
                    step,
                    hit,
                    0,
                    null,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1) % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(resolveNormalDuration(
                simulator, NORMAL_DURATION_FRAMES[step] * FRAME));
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 24.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                0,
                null,
                null));
        simulator.advanceTime(46.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 47.0 * FRAME,
                HitKind.PLUNGE,
                0,
                0,
                0,
                null,
                null));
        simulator.advanceTime(78.0 * FRAME);
    }

    private void heartstopperStrike(
            CombatSimulator simulator,
            SkillActionMode mode) {
        int missingStacks = mode == SkillActionMode.HOLD
                ? Math.max(0, 4 - declensionStacks) : 0;
        int fullStackPenalty = mode == SkillActionMode.HOLD
                && declensionStacks == 4 ? 17 : 0;
        double releaseTime = simulator.getCurrentTime()
                + (18.0 + 45.0 * missingStacks + fullStackPenalty) * FRAME;
        queueCommand(simulator, new PendingCommand(
                releaseTime,
                CommandKind.SKILL_RELEASE,
                mode == SkillActionMode.HOLD ? 1 : 0));
        simulator.advanceTime((39.0 + 45.0 * missingStacks
                + fullStackPenalty) * FRAME);
    }

    private void windmusterKick(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 3.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0));
        queueHit(simulator, new PendingHit(
                castTime + 34.0 * FRAME,
                HitKind.BURST,
                0,
                0,
                0,
                null,
                null));
        simulator.advanceTime(72.0 * FRAME);
    }

    private void releaseSkill(CombatSimulator simulator, boolean hold) {
        double currentTime = simulator.getCurrentTime();
        markSkillUsed(currentTime, simulator.getApplicableBuffs(this));
        int stacks = hold ? 4 : declensionStacks;
        StatsContainer snapshot = captureLiveStats(currentTime);
        if (constellation >= 6) {
            snapshot.add(StatType.CRIT_RATE, 0.04 * stacks);
            if (stacks == 4) {
                snapshot.add(StatType.CRIT_DMG,
                        getTalentValue("C6 Conviction CRIT DMG", 0.32));
            }
        }
        queueHit(simulator, new PendingHit(
                currentTime + 2.0 * FRAME,
                HitKind.SKILL,
                0,
                0,
                stacks,
                snapshot,
                null));
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                performBasicHit(simulator, "Charged Attack",
                        getTalentValue("Charged Attack", 1.241000),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        CHARGED_HITLAG);
                break;
            case PLUNGE:
                performBasicHit(simulator, "High Plunge",
                        getTalentValue("High Plunge", 2.607632),
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        HitlagProfile.none());
                break;
            case SKILL:
                resolveSkill(simulator, hit);
                break;
            case BURST:
                resolveBurst(simulator);
                break;
            case IRIS:
                resolveIris(simulator, hit);
                break;
            default:
                throw new IllegalStateException("Unknown Heizou hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        performBasicHit(
                simulator,
                NORMAL_KEYS[hit.index][hit.subIndex],
                getTalentValue(
                        NORMAL_KEYS[hit.index][hit.subIndex],
                        NORMAL_MULTIPLIERS[hit.index][hit.subIndex]),
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                NORMAL_HITLAG[hit.index][hit.subIndex]);
    }

    private void performBasicHit(
            CombatSimulator simulator,
            String key,
            double multiplier,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            HitlagProfile hitlagProfile) {
        AttackAction action = attack(
                "Fudou Style Martial Arts " + key,
                multiplier,
                Element.ANEMO,
                bonusStat,
                actionType,
                icdType,
                icdTag,
                actionType == ActionType.PLUNGE ? 0.0 : 1.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        action.setHitlagProfile(hitlagProfile);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveSkill(CombatSimulator simulator, PendingHit hit) {
        String suffix = constellation >= 3 ? " C3" : "";
        double base = getTalentValue(
                "Skill" + suffix,
                constellation >= 3 ? 4.550400 : 3.867840);
        double perStack = getTalentValue(
                "Declension Per Stack" + suffix,
                constellation >= 3 ? 1.137600 : 0.966960);
        double conviction = hit.stacks == 4
                ? getTalentValue(
                        "Conviction Bonus" + suffix,
                        constellation >= 3 ? 2.275200 : 1.933920)
                : 0.0;
        AttackAction action = attack(
                hit.stacks == 4
                        ? "Heartstopper Strike (Max Stacks)"
                        : "Heartstopper Strike",
                base + perStack * hit.stacks + conviction,
                Element.ANEMO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                2.0);
        action.setStatSnapshot(hit.snapshot);
        action.setHitlagProfile(
                hit.stacks == 4 ? MAX_SKILL_HITLAG : SKILL_HITLAG);
        resolvingSkillAction = action;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingSkillAction = null;
        }
        declensionStacks = 0;
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime() + PARTICLE_TRAVEL,
                CommandKind.PARTICLE,
                particleCount(hit.stacks)));
    }

    private void resolveBurst(CombatSimulator simulator) {
        Element irisElement = selectIrisElement(simulator);
        String suffix = constellation >= 5 ? " C5" : "";
        AttackAction action = attack(
                "Fudou Style Vacuum Slugger",
                getTalentValue(
                        "Windmuster Kick" + suffix,
                        constellation >= 5 ? 6.293760 : 5.349696),
                Element.ANEMO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                1.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
        lastIrisElement = irisElement;
        if (irisElement == null) {
            return;
        }
        if (constellation >= 4) {
            receiveFlatEnergy(getTalentValue("C4 First Iris Energy", 9.0));
        }
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + 40.0 * FRAME,
                HitKind.IRIS,
                0,
                0,
                0,
                captureLiveStats(simulator.getCurrentTime()),
                irisElement));
    }

    private void resolveIris(CombatSimulator simulator, PendingHit hit) {
        String suffix = constellation >= 5 ? " C5" : "";
        AttackAction action = attack(
                "Windmuster Iris " + hit.element.name(),
                getTalentValue(
                        "Windmuster Iris" + suffix,
                        constellation >= 5 ? 0.429120 : 0.364752),
                hit.element,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void applyA4(CombatSimulator simulator, double currentTime) {
        for (Character member : simulator.getPartyMembers()) {
            if (member == this) {
                continue;
            }
            member.removeBuff(BuffId.HEIZOU_A4_PARTY_ELEMENTAL_MASTERY);
            member.addBuff(new SimpleBuff(
                    "Shikanoin Heizou A4 Elemental Mastery",
                    BuffId.HEIZOU_A4_PARTY_ELEMENTAL_MASTERY,
                    getTalentValue("A4 Duration", 10.0),
                    currentTime,
                    stats -> stats.add(
                            StatType.ELEMENTAL_MASTERY,
                            getTalentValue("A4 Elemental Mastery", 80.0)))
                    .sourcedBy(characterId));
        }
    }

    private void addDeclensionStack() {
        declensionStacks = Math.min(4, declensionStacks + 1);
    }

    private int particleCount(int stacks) {
        if (stacks >= 4) {
            return 3;
        }
        if (stacks < 2) {
            return 2;
        }
        return nextParticleDraw() < 0.5 ? 3 : 2;
    }

    private double nextParticleDraw() {
        double draw;
        if (particleDrawCursor < particleDrawTape.size()) {
            draw = particleDrawTape.get(particleDrawCursor++);
        } else {
            draw = particleDrawSource.getAsDouble();
            if (draw < 0.0 || draw > 1.0 || Double.isNaN(draw)) {
                throw new IllegalStateException(
                        "Shikanoin Heizou particle draw must be in [0, 1]");
            }
            particleDrawTape.add(draw);
            particleDrawCursor++;
        }
        return draw;
    }

    private Element selectIrisElement(CombatSimulator simulator) {
        Enemy enemy = simulator.getEnemy();
        if (enemy == null) {
            return null;
        }
        double currentTime = simulator.getCurrentTime();
        for (Element candidate : IRIS_PRIORITY) {
            if (enemy.getAuraUnits(candidate, currentTime) > 0.0) {
                return candidate;
            }
        }
        return null;
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

    private double resolveNormalDuration(
            CombatSimulator simulator,
            double duration) {
        StatsContainer stats = captureLiveStats(simulator.getCurrentTime());
        double speed = Math.min(
                0.60,
                stats.get(StatType.ATK_SPD)
                        + stats.get(StatType.NORMAL_ATTACK_SPD));
        return speed <= 0.0 ? duration : duration / (1.0 + speed);
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
                case SKILL_RELEASE:
                    releaseSkill(activeSimulator, command.value != 0);
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.ANEMO,
                            command.value,
                            ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Shikanoin Heizou command kind");
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
            Element element,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
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
        PLUNGE,
        SKILL,
        BURST,
        IRIS
    }

    private enum CommandKind {
        SKILL_RELEASE,
        BURST_ENERGY,
        PARTICLE
    }

    /** Immutable delayed Heizou hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final int stacks;
        private final StatsContainer snapshot;
        private final Element element;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                int stacks,
                StatsContainer snapshot,
                Element element) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.stacks = stacks;
            this.snapshot = snapshot == null ? null : snapshot.merge(null);
            this.element = element;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, subIndex, stacks, snapshot, element);
        }
    }

    /** Immutable delayed Heizou command description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final int value;

        private PendingCommand(
                double time,
                CommandKind kind,
                int value) {
            this.time = time;
            this.kind = kind;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value);
        }
    }

    /** Immutable owner-bound Heizou rollback payload. */
    private static final class HeizouState implements State {
        private final ShikanoinHeizou owner;
        private final int particleDrawCursor;
        private final int normalAttackStep;
        private final int declensionStacks;
        private final double nextA1Time;
        private final double nextC1Time;
        private final Element lastIrisElement;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private HeizouState(
                ShikanoinHeizou owner,
                int particleDrawCursor,
                int normalAttackStep,
                int declensionStacks,
                double nextA1Time,
                double nextC1Time,
                Element lastIrisElement,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.particleDrawCursor = particleDrawCursor;
            this.normalAttackStep = normalAttackStep;
            this.declensionStacks = declensionStacks;
            this.nextA1Time = nextA1Time;
            this.nextC1Time = nextC1Time;
            this.lastIrisElement = lastIrisElement;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
