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
 * Hu Tao's fixed-target Paramita Papilio offensive slice through C5.
 *
 * <p>Secret Spear of Wangsheng, Guide to Afterlife's Max-HP ATK conversion,
 * Pyro infusion, particle gate, Blood Blossom, Spirit Soother, A1, and the
 * representable C2/C3/C5 branches follow pinned gcsim {@code ef41805d}.
 * Conversion uses the final Max HP visible at snapshot time and preserves the
 * exact 400% base-ATK cap.</p>
 *
 * <p>Player HP drain/healing and low-HP branches, stamina-only C1, enemy-death
 * C4, C6 survival, low Plunge, collision, and geometry are excluded
 * without approximation.</p>
 */
public final class HuTao extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 12 }, { 9 }, { 17 }, { 22 }, { 16, 26 }, { 23 }
    };
    private static final int[] NORMAL_DURATIONS = {
        20, 16, 26, 31, 48, 72
    };
    private static final double[][] NORMAL_T9 = {
        { 0.788544 }, { 0.811543 }, { 1.026750 }, { 1.103962 },
        { 0.559603, 0.592000 }, { 1.445664 }
    };

    /**
     * Per-hit metadata from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_HITLAG_SHORT =
            new HitlagProfile(0.01, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_MEDIUM =
            new HitlagProfile(0.02, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_MEDIUM_NO_DEFENSE =
            new HitlagProfile(0.02, 0.01, false, false, false);
    private static final HitlagProfile NORMAL_HITLAG_LONG =
            new HitlagProfile(0.04, 0.01, true, false, false);
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.0, 0.01, true, true, false);

    private final DoubleSupplier particleRandom;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long paramitaGeneration;
    private long bloodBlossomGeneration;
    private double paramitaExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double bloodBlossomExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean a1Pending;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Hu Tao. */
    public HuTao(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Hu Tao at an explicit constellation. */
    public HuTao(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Hu Tao with injectable talent data and particle randomness.
     *
     * @param weapon equipped polearm, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param particleRandom source of draws in {@code [0, 1)}
     */
    public HuTao(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleRandom) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Hu Tao constellation must be between 0 and 6");
        }
        if (particleRandom == null) {
            throw new IllegalArgumentException(
                    "Hu Tao particle random source is required");
        }
        name = "Hu Tao";
        characterId = CharacterId.HU_TAO;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleRandom = particleRandom;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 15552.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 106.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 876.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 16.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds delayed Paramita and Blood Blossom work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Hu Tao simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Hu Tao must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Hu Tao cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures all owner state and reconstructable future events. */
    @Override
    public State captureCharacterState() {
        return new HuTaoState(
                this,
                normalAttackStep,
                paramitaGeneration,
                bloodBlossomGeneration,
                paramitaExpirationTime,
                nextParticleAllowedTime,
                bloodBlossomExpirationTime,
                a1Pending,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Hu Tao instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof HuTaoState
                && ((HuTaoState) state).owner == this;
    }

    /** Restores owner state and registers each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Hu Tao state");
        }
        initializeForSimulator(simulator);
        HuTaoState restored = (HuTaoState) state;
        normalAttackStep = restored.normalAttackStep;
        paramitaGeneration = restored.paramitaGeneration;
        bloodBlossomGeneration = restored.bloodBlossomGeneration;
        paramitaExpirationTime = restored.paramitaExpirationTime;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        bloodBlossomExpirationTime = restored.bloodBlossomExpirationTime;
        a1Pending = restored.a1Pending;
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

    /** Returns Hu Tao's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Hu Tao has no unconditional stat passive in the represented slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Ends Paramita early and grants A1 when Hu Tao leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        if (isParamitaActive(simulator.getCurrentTime())) {
            endParamita(simulator, paramitaGeneration);
        }
    }

    /** Returns whether Paramita Papilio is active at a half-open boundary. */
    public boolean isParamitaActive(double currentTime) {
        return currentTime + EPSILON < paramitaExpirationTime;
    }

    /** Returns Blood Blossom's current fixed-target expiration timestamp. */
    public double getBloodBlossomExpirationTime() {
        return bloodBlossomExpirationTime;
    }

    /** Returns the next timestamp at which an infused hit can make particles. */
    public double getNextParticleAllowedTime() {
        return nextParticleAllowedTime;
    }

    /** Returns the number of unresolved Hu Tao-owned damage hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Hu Tao's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Hu Tao action is required");
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
                guideToAfterlife(simulator);
                break;
            case BURST:
                spiritSoother(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Hu Tao: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        boolean infused = isParamitaActive(castTime);
        StatsContainer snapshot = captureLiveStats(castTime, infused);
        for (int variant = 0;
                variant < NORMAL_HIT_FRAMES[step].length;
                variant++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HIT_FRAMES[step][variant] * FRAME,
                    HitKind.NORMAL,
                    step,
                    variant,
                    infused,
                    snapshot));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean infused = isParamitaActive(castTime);
        queueHit(simulator, new PendingHit(
                castTime + (infused ? 3.0 : 19.0) * FRAME,
                HitKind.CHARGED,
                0,
                0,
                infused,
                captureLiveStats(castTime, infused)));
        simulator.advanceTime((infused ? 42.0 : 62.0) * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean infused = isParamitaActive(castTime);
        queueHit(simulator, new PendingHit(
                castTime + (infused ? 46.0 : 45.0) * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                0,
                infused,
                captureLiveStats(castTime, infused)));
        simulator.advanceTime((infused ? 77.0 : 76.0) * FRAME);
    }

    private void guideToAfterlife(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++paramitaGeneration;
        paramitaExpirationTime = castTime
                + getTalentValue("Paramita Duration Frames", 554.0) * FRAME;
        a1Pending = true;
        queueCommand(simulator, new PendingCommand(
                castTime + 14.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0.0,
                generation));
        queueCommand(simulator, new PendingCommand(
                paramitaExpirationTime,
                CommandKind.PARAMITA_END,
                0.0,
                generation));
        simulator.advanceTime(52.0 * FRAME);
    }

    private void spiritSoother(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(
                castTime + 62.0 * FRAME,
                simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 68.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0,
                0L));
        queueHit(simulator, new PendingHit(
                castTime + 66.0 * FRAME,
                HitKind.BURST,
                0,
                0,
                false,
                captureLiveStats(castTime, isParamitaActive(castTime))));
        simulator.advanceTime(98.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Secret Spear of Wangsheng N" + (hit.index + 1),
                        normalValue(hit.index, hit.variant),
                        hit.infused ? Element.PYRO : Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        hit.infused ? 1.0 : 0.0,
                        0.0);
                triggerParticles(simulator, hit);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Secret Spear of Wangsheng Charged",
                        getTalentValue("Charged Attack", 2.286600),
                        hit.infused ? Element.PYRO : Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        hit.infused ? 1.0 : 0.0,
                        0.0);
                triggerParticles(simulator, hit);
                if (hit.infused && simulator.getEnemy() != null) {
                    applyBloodBlossom(simulator, hit.time);
                }
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Secret Spear of Wangsheng High Plunge",
                        getTalentValue("High Plunge", 2.747916),
                        hit.infused ? Element.PYRO : Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        hit.infused ? 1.0 : 0.0,
                        0.0);
                triggerParticles(simulator, hit);
                break;
            case BLOOD_BLOSSOM:
                resolveBloodBlossom(simulator, hit);
                break;
            case BURST:
                performHit(
                        simulator,
                        hit,
                        "Spirit Soother",
                        burstValue(),
                        Element.PYRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        2.0,
                        0.0);
                if (constellation >= 2 && simulator.getEnemy() != null) {
                    applyBloodBlossom(simulator, hit.time);
                }
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Hu Tao hit kind " + hit.kind);
        }
    }

    private void resolveBloodBlossom(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.generation != bloodBlossomGeneration
                || hit.time + EPSILON >= bloodBlossomExpirationTime) {
            return;
        }
        StatsContainer snapshot = captureLiveStats(
                simulator.getCurrentTime(),
                isParamitaActive(simulator.getCurrentTime()));
        double flatDamage = constellation >= 2
                ? snapshot.getTotalHp()
                        * getTalentValue("C2 Max HP Flat DMG", 0.10)
                : 0.0;
        PendingHit resolved = new PendingHit(
                hit.time,
                HitKind.BLOOD_BLOSSOM,
                0,
                0,
                false,
                snapshot,
                hit.generation);
        performHit(
                simulator,
                resolved,
                "Blood Blossom",
                bloodBlossomValue(),
                Element.PYRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0,
                flatDamage);
        double nextTime = hit.time
                + getTalentValue("Blood Blossom Interval", 4.0);
        if (nextTime + EPSILON < bloodBlossomExpirationTime) {
            queueHit(simulator, new PendingHit(
                    nextTime,
                    HitKind.BLOOD_BLOSSOM,
                    0,
                    0,
                    false,
                    null,
                    hit.generation));
        }
    }

    private void triggerParticles(
            CombatSimulator simulator,
            PendingHit hit) {
        if (!hit.infused
                || simulator.getEnemy() == null
                || hit.time + EPSILON < nextParticleAllowedTime) {
            return;
        }
        double draw = particleRandom.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Hu Tao particle random draw must be in [0, 1)");
        }
        nextParticleAllowedTime = hit.time
                + getTalentValue("Particle Cooldown", 5.0);
        double count = draw < 0.5
                ? getTalentValue("Particle Count Max", 3.0)
                : getTalentValue("Particle Count Min", 2.0);
        queueCommand(simulator, new PendingCommand(
                hit.time
                        + getTalentValue("Particle Travel Frames", 80.0)
                                * FRAME,
                CommandKind.PARTICLE,
                count,
                0L));
    }

    private void applyBloodBlossom(
            CombatSimulator simulator,
            double applicationTime) {
        long generation = ++bloodBlossomGeneration;
        bloodBlossomExpirationTime = applicationTime
                + getTalentValue("Blood Blossom Duration", 9.5);
        queueHit(simulator, new PendingHit(
                applicationTime
                        + getTalentValue("Blood Blossom Interval", 4.0),
                HitKind.BLOOD_BLOSSOM,
                0,
                0,
                false,
                null,
                generation));
    }

    private void endParamita(
            CombatSimulator simulator,
            long generation) {
        if (generation != paramitaGeneration || !a1Pending) {
            return;
        }
        paramitaExpirationTime = Double.NEGATIVE_INFINITY;
        a1Pending = false;
        double currentTime = simulator.getCurrentTime();
        for (Character member : simulator.getPartyMembers()) {
            if (member == this) {
                continue;
            }
            member.removeBuff(BuffId.HU_TAO_A1_PARTY_CRIT);
            member.addBuff(new SimpleBuff(
                    "Hu Tao Flutter By",
                    BuffId.HU_TAO_A1_PARTY_CRIT,
                    getTalentValue("A1 Duration", 8.0),
                    currentTime,
                    stats -> stats.add(
                            StatType.CRIT_RATE,
                            getTalentValue(
                                    "A1 Party CRIT Rate", 0.12)))
                    .sourcedBy(characterId));
        }
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
            double gaugeUnits,
            double flatDamage) {
        AttackAction action = flatDamage == 0.0
                ? new AttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        StatType.BASE_ATK,
                        bonusStat,
                        0.0,
                        actionType)
                : new HuTaoAttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        bonusStat,
                        actionType,
                        flatDamage);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (hit.kind == HitKind.NORMAL) {
            action.setHitlagProfile(normalHitlag(hit.index, hit.variant));
        } else if (hit.kind == HitKind.CHARGED) {
            action.setHitlagProfile(CHARGED_HITLAG);
        }
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(
                        simulator.getCurrentTime(),
                        isParamitaActive(simulator.getCurrentTime()))
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile normalHitlag(int step, int variant) {
        if (step <= 2) {
            return NORMAL_HITLAG_SHORT;
        }
        if (step == 3) {
            return NORMAL_HITLAG_MEDIUM;
        }
        if (step == 4) {
            return variant == 0
                    ? NORMAL_HITLAG_MEDIUM_NO_DEFENSE
                    : NORMAL_HITLAG_MEDIUM;
        }
        return NORMAL_HITLAG_LONG;
    }

    private double normalValue(int step, int variant) {
        String key = "N" + (step + 1);
        if (NORMAL_HIT_FRAMES[step].length > 1) {
            key += " Hit " + (variant + 1);
        }
        return getTalentValue(key, NORMAL_T9[step][variant]);
    }

    private double paramitaRatio() {
        return getTalentValue(
                constellation >= 3
                        ? "Paramita ATK Ratio C3"
                        : "Paramita ATK Ratio",
                constellation >= 3 ? 0.068540 : 0.059570);
    }

    private double bloodBlossomValue() {
        return getTalentValue(
                constellation >= 3
                        ? "Blood Blossom C3" : "Blood Blossom",
                constellation >= 3 ? 1.280000 : 1.088000);
    }

    private double burstValue() {
        return getTalentValue(
                constellation >= 5
                        ? "Spirit Soother C5" : "Spirit Soother",
                constellation >= 5 ? 5.411680 : 4.703440);
    }

    private StatsContainer captureLiveStats(
            double currentTime,
            boolean includeParamita) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        if (includeParamita) {
            double desired = stats.getTotalHp() * paramitaRatio();
            double cap = stats.get(StatType.BASE_ATK)
                    * getTalentValue(
                            "Paramita ATK Cap Base ATK", 4.0);
            stats.add(StatType.ATK_FLAT, Math.min(desired, cap));
        }
        return stats;
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
                case SKILL_COOLDOWN:
                    if (command.generation == paramitaGeneration) {
                        markSkillUsed(
                                activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case PARAMITA_END:
                    endParamita(activeSimulator, command.generation);
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
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
                            "Unknown Hu Tao command kind " + command.kind);
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
        HIGH_PLUNGE,
        BLOOD_BLOSSOM,
        BURST
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        PARAMITA_END,
        BURST_ENERGY,
        PARTICLE
    }

    /** Preserves C2's fixed Max-HP addition through reaction resolution. */
    private static final class HuTaoAttackAction extends AttackAction {
        private final double fixedAdditiveBaseDamage;

        private HuTaoAttackAction(
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

    /** Immutable future hit with queue-time infusion and stat state. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final boolean infused;
        private final StatsContainer snapshot;
        private final long generation;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                boolean infused,
                StatsContainer snapshot) {
            this(time, kind, index, variant, infused, snapshot, 0L);
        }

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                boolean infused,
                StatsContainer snapshot,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.infused = infused;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.generation = generation;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    variant,
                    infused,
                    snapshot,
                    generation);
        }
    }

    /** Immutable delayed state command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final double value;
        private final long generation;

        private PendingCommand(
                double time,
                CommandKind kind,
                double value,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.value = value;
            this.generation = generation;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value, generation);
        }
    }

    /** Immutable snapshot of all Hu Tao-owned mutable runtime state. */
    private static final class HuTaoState implements State {
        private final HuTao owner;
        private final int normalAttackStep;
        private final long paramitaGeneration;
        private final long bloodBlossomGeneration;
        private final double paramitaExpirationTime;
        private final double nextParticleAllowedTime;
        private final double bloodBlossomExpirationTime;
        private final boolean a1Pending;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private HuTaoState(
                HuTao owner,
                int normalAttackStep,
                long paramitaGeneration,
                long bloodBlossomGeneration,
                double paramitaExpirationTime,
                double nextParticleAllowedTime,
                double bloodBlossomExpirationTime,
                boolean a1Pending,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.paramitaGeneration = paramitaGeneration;
            this.bloodBlossomGeneration = bloodBlossomGeneration;
            this.paramitaExpirationTime = paramitaExpirationTime;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.bloodBlossomExpirationTime = bloodBlossomExpirationTime;
            this.a1Pending = a1Pending;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
