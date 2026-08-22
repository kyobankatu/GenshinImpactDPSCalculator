package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
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
import simulation.action.HitlagProfile;
import simulation.event.SimpleTimerEvent;

/**
 * Thoma's fixed-target offensive and Fiery Collapse slice through C5.
 *
 * <p>Swiftshatter Spear, Blazing Blessing, Crimson Ooyoroi, particles,
 * A4, C2-C5, and the one-second Fiery Collapse trigger gate follow pinned
 * gcsim {@code ef41805d}. High Plunge uses the pinned shared polearm table.
 * Delayed hits capture Thoma's stats when gcsim queues the corresponding
 * attack and survive simulator rollback.</p>
 *
 * <p>Shield creation, absorption, and refresh, A1, shield-hit C1 cooldown
 * reduction, shield-gated C6, geometry, multi-target behavior, stamina, and
 * complete hitlag coverage is intentionally excluded. No offensive approximation is created
 * for those defensive branches.</p>
 */
public final class Thoma extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile NORMAL_ONE_HITLAG =
            new HitlagProfile(0.06, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_TWO_HITLAG =
            new HitlagProfile(0.09, 0.01, true, false, false);
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.0, 0.01, true, true, false);
    private static final HitlagProfile SKILL_HITLAG =
            new HitlagProfile(0.06, 0.01, true, false, false);
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 13 }, { 18 }, { 10, 23 }, { 20 }
    };
    private static final int[] NORMAL_DURATIONS = { 21, 27, 32, 58 };
    private static final double[][] NORMAL_T9 = {
        { 0.815596 }, { 0.801534 }, { 0.492170, 0.492170 },
        { 1.237456 }
    };

    private final DoubleSupplier particleRandom;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextFieryCollapseAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Thoma. */
    public Thoma(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Thoma at an explicit constellation. */
    public Thoma(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Thoma with injectable talent data and particle draw source.
     *
     * @param weapon equipped polearm, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param particleRandom source of draws in {@code [0, 1)}
     */
    public Thoma(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleRandom) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Thoma constellation must be between 0 and 6");
        }
        if (particleRandom == null) {
            throw new IllegalArgumentException(
                    "Thoma particle random source is required");
        }
        name = "Thoma";
        characterId = CharacterId.THOMA;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleRandom = particleRandom;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10331.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 202.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 751.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 20.0));
    }

    /** Binds Fiery Collapse observation and delayed work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Thoma simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Thoma must belong to the simulator party");
        }
        if (initializedSimulator != null) {
            if (initializedSimulator != simulator) {
                throw new IllegalStateException(
                        "Thoma cannot be reused across simulators");
            }
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                handleFieryCollapseTrigger(
                        simulator, actor, action, damage, time));
    }

    /** Captures combo, Burst gates, and all reconstructable future work. */
    @Override
    public State captureCharacterState() {
        return new ThomaState(
                this,
                normalAttackStep,
                burstExpirationTime,
                nextFieryCollapseAllowedTime,
                nextParticleAllowedTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Thoma instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof ThomaState
                && ((ThomaState) state).owner == this;
    }

    /** Restores state and re-registers each surviving future event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Thoma state");
        }
        initializeForSimulator(simulator);
        ThomaState restored = (ThomaState) state;
        normalAttackStep = restored.normalAttackStep;
        burstExpirationTime = restored.burstExpirationTime;
        nextFieryCollapseAllowedTime =
                restored.nextFieryCollapseAllowedTime;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
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

    /** Returns Thoma's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Thoma has no unconditional represented offensive stat passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets Swiftshatter Spear progression when Thoma leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether Crimson Ooyoroi accepts triggers at this timestamp. */
    public boolean isCrimsonOoyoroiActive(double currentTime) {
        return currentTime + EPSILON < burstExpirationTime;
    }

    /** Returns the next timestamp accepted by the Fiery Collapse ICD. */
    public double getNextFieryCollapseAllowedTime() {
        return nextFieryCollapseAllowedTime;
    }

    /** Returns the number of unresolved Thoma-owned damage hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Thoma's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Thoma action is required");
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
                blazingBlessing(simulator);
                break;
            case BURST:
                crimsonOoyoroi(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Thoma: "
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
                    null,
                    0.0));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 27.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                null,
                0.0));
        normalAttackStep = 0;
        simulator.advanceTime(64.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 46.0 * FRAME,
                HitKind.PLUNGE,
                0,
                0,
                null,
                0.0));
        normalAttackStep = 0;
        simulator.advanceTime(77.0 * FRAME);
    }

    private void blazingBlessing(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 9.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 11.0 * FRAME,
                HitKind.SKILL,
                0,
                0,
                snapshot,
                0.0));
        simulator.advanceTime(46.0 * FRAME);
    }

    private void crimsonOoyoroi(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        burstExpirationTime = castTime + getTalentValue(
                constellation >= 2
                        ? "C2 Burst Duration" : "Burst Duration",
                constellation >= 2 ? 18.0 : 15.0);
        nextFieryCollapseAllowedTime = Double.NEGATIVE_INFINITY;
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0));
        if (constellation >= 4) {
            queueCommand(simulator, new PendingCommand(
                    castTime + 8.0 * FRAME,
                    CommandKind.C4_ENERGY,
                    getTalentValue("C4 Energy", 15.0)));
        }
        queueHit(simulator, new PendingHit(
                castTime + 40.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                0,
                snapshot,
                0.0));
        simulator.advanceTime(58.0 * FRAME);
    }

    private void handleFieryCollapseTrigger(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (actor == null
                || simulator.getActiveCharacter() != actor
                || !isCrimsonOoyoroiActive(time)
                || action == null
                || !(damage > 0.0)
                || !action.isHitEffectTrigger()
                || action.getActionType() != ActionType.NORMAL
                || time + EPSILON < nextFieryCollapseAllowedTime) {
            return;
        }
        nextFieryCollapseAllowedTime = time + getTalentValue(
                "Fiery Collapse ICD", 1.0);
        StatsContainer snapshot = captureLiveStats(time);
        double a4FlatDamage = snapshot.getTotalHp()
                * getTalentValue("A4 Max HP Ratio", 0.022);
        queueHit(simulator, new PendingHit(
                time + 11.0 * FRAME,
                HitKind.FIERY_COLLAPSE,
                0,
                0,
                snapshot,
                a4FlatDamage));
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Swiftshatter Spear N" + (hit.index + 1)
                                + (NORMAL_HIT_FRAMES[hit.index].length > 1
                                        ? " Hit " + (hit.variant + 1) : ""),
                        normalValue(hit.index, hit.variant),
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
                        "Swiftshatter Spear Charged Attack",
                        getTalentValue("Charged Attack", 2.071380),
                        Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        0.0);
                break;
            case PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Swiftshatter Spear High Plunge",
                        getTalentValue("High Plunge", 2.933586),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        0.0);
                break;
            case SKILL:
                resolveSkillHit(simulator, hit);
                break;
            case BURST_INITIAL:
                performHit(
                        simulator,
                        hit,
                        "Crimson Ooyoroi Initial",
                        burstValue(
                                "Crimson Ooyoroi Initial",
                                1.496000,
                                1.760000),
                        Element.PYRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        2.0);
                break;
            case FIERY_COLLAPSE:
                performHit(
                        simulator,
                        hit,
                        "Crimson Ooyoroi Fiery Collapse",
                        burstValue("Fiery Collapse", 0.986000, 1.160000),
                        Element.PYRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Thoma hit kind " + hit.kind);
        }
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            PendingHit hit) {
        performHit(
                simulator,
                hit,
                "Blazing Blessing",
                skillValue(),
                Element.PYRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0);
        if (simulator.getEnemy() == null
                || hit.time + EPSILON < nextParticleAllowedTime) {
            return;
        }
        nextParticleAllowedTime = hit.time
                + getTalentValue("Particle ICD", 0.3);
        queueCommand(simulator, new PendingCommand(
                hit.time + PARTICLE_TRAVEL,
                CommandKind.PARTICLE,
                particleCount()));
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
            double gaugeUnits) {
        AttackAction action = hit.flatDamage == 0.0
                ? new AttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        StatType.BASE_ATK,
                        bonusStat,
                        0.0,
                        actionType)
                : new ThomaAttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        bonusStat,
                        actionType,
                        hit.flatDamage);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setHitlagProfile(hitlagProfile(hit));
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile hitlagProfile(PendingHit hit) {
        if (hit.kind == HitKind.CHARGED) {
            return CHARGED_HITLAG;
        }
        if (hit.kind == HitKind.SKILL) {
            return SKILL_HITLAG;
        }
        if (hit.kind == HitKind.NORMAL && hit.index == 0) {
            return NORMAL_ONE_HITLAG;
        }
        if (hit.kind == HitKind.NORMAL && hit.index == 1) {
            return NORMAL_TWO_HITLAG;
        }
        return HitlagProfile.none();
    }

    private double normalValue(int step, int variant) {
        String key = "N" + (step + 1);
        if (NORMAL_HIT_FRAMES[step].length > 1) {
            key += " Hit " + (variant + 1);
        }
        return getTalentValue(key, NORMAL_T9[step][variant]);
    }

    private double skillValue() {
        String key = constellation >= 3
                ? "Blazing Blessing C3" : "Blazing Blessing";
        return getTalentValue(key,
                constellation >= 3 ? 2.928000 : 2.488800);
    }

    private double burstValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(key + suffix,
                constellation >= 5 ? talentTwelve : talentNine);
    }

    private double particleCount() {
        double draw = particleRandom.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Thoma particle random draw must be in [0, 1)");
        }
        return draw < 0.5
                ? getTalentValue("Particle Count Max", 4.0)
                : getTalentValue("Particle Count Min", 3.0);
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
                case C4_ENERGY:
                    receiveFlatEnergy(command.value);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.PYRO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Thoma command kind " + command.kind);
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
        PLUNGE,
        SKILL,
        BURST_INITIAL,
        FIERY_COLLAPSE
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        C4_ENERGY,
        PARTICLE
    }

    /**
     * Preserves A4's fixed HP addition through the resolver's Catalyze reset.
     * Pyro cannot trigger Aggravate or Spread, so the two meanings cannot
     * conflict on a Fiery Collapse hit.
     */
    private static final class ThomaAttackAction extends AttackAction {
        private final double fixedAdditiveBaseDamage;

        private ThomaAttackAction(
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
            // The generic resolver only clears Catalyze-owned additions.
        }

        @Override
        public double getAdditiveBaseDmgBonus() {
            return fixedAdditiveBaseDamage;
        }
    }

    /** Immutable future damage hit with queue-time stats and A4 flat damage. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final StatsContainer snapshot;
        private final double flatDamage;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                StatsContainer snapshot,
                double flatDamage) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.flatDamage = flatDamage;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, variant, snapshot, flatDamage);
        }
    }

    /** Immutable future state-only command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final double value;

        private PendingCommand(
                double time,
                CommandKind kind,
                double value) {
            this.time = time;
            this.kind = kind;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value);
        }
    }

    /** Immutable snapshot of all mutable Thoma-owned simulator state. */
    private static final class ThomaState implements State {
        private final Thoma owner;
        private final int normalAttackStep;
        private final double burstExpirationTime;
        private final double nextFieryCollapseAllowedTime;
        private final double nextParticleAllowedTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private ThomaState(
                Thoma owner,
                int normalAttackStep,
                double burstExpirationTime,
                double nextFieryCollapseAllowedTime,
                double nextParticleAllowedTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.burstExpirationTime = burstExpirationTime;
            this.nextFieryCollapseAllowedTime =
                    nextFieryCollapseAllowedTime;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
