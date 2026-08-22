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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Kamisato Ayato's stationary single-target Namisen kit through C6.
 *
 * <p>Shunsuiken conversion, Namisen ordering, particle cadence, Burst
 * snapshots, A1/A4, and representable constellations follow pinned gcsim
 * {@code ef41805d} and maintained KQM evidence.</p>
 *
 * <p>Enemy-HP-dependent C1, multi-target Burst selection, geometry,
 * stamina, interruption, and player-damage behavior are excluded.</p>
 */
public final class KamisatoAyato extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 12 }, { 18 }, { 20 }, { 22, 25 }, { 41 }
    };
    private static final int[] NORMAL_DURATIONS = { 15, 27, 30, 27, 63 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3" },
        { "N4-1", "N4-2" }, { "N5" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.826040 }, { 0.866377 }, { 1.076833 },
        { 0.541031, 0.541031 }, { 1.389010 }
    };
    private static final double[] SHUNSUIKEN_MULTIPLIERS = {
        0.9717, 1.0823, 1.1929
    };
    private static final double[] SHUNSUIKEN_C3_MULTIPLIERS = {
        1.1931, 1.3289, 1.4647
    };
    /**
     * Per-hit metadata from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_HITLAG_SHORT =
            new HitlagProfile(0.03, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N3 =
            new HitlagProfile(0.06, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N5 =
            new HitlagProfile(0.08, 0.01, true, false, false);
    private static final HitlagProfile SHUNSUIKEN_HITLAG =
            new HitlagProfile(0.03, 0.01, false, false, false);
    private static final HitlagProfile C6_HITLAG =
            new HitlagProfile(0.03, 0.01, false, true, false);

    private final DoubleSupplier particleDrawSource;
    private final List<Double> particleDrawTape = new ArrayList<>();
    private CombatSimulator initializedSimulator;
    private int particleDrawCursor;
    private int normalAttackStep;
    private int shunsuikenStep;
    private int namisenStacks;
    private double skillExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private long a4Generation;
    private boolean c6Ready;
    private AttackAction resolvingShunsuikenAction;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Kamisato Ayato. */
    public KamisatoAyato(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Kamisato Ayato at an explicit constellation. */
    public KamisatoAyato(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                Math::random);
    }

    /** Constructs Ayato with an injectable particle draw source. */
    public KamisatoAyato(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation,
            DoubleSupplier particleDrawSource) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                particleDrawSource);
    }

    /** Constructs Ayato with injectable talent data and particle draws. */
    public KamisatoAyato(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleDrawSource) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Kamisato Ayato constellation must be between 0 and 6");
        }
        if (particleDrawSource == null) {
            throw new IllegalArgumentException(
                    "Kamisato Ayato particle draw source is required");
        }
        name = "Kamisato Ayato";
        characterId = CharacterId.KAMISATO_AYATO;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleDrawSource = particleDrawSource;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13715.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 299.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 769.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 12.0));
        setBurstCD(20.0);
    }

    /** Binds Ayato's accepted-hit listener to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Kamisato Ayato simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Kamisato Ayato cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Kamisato Ayato must belong to the target simulator party");
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor == this
                    && action == resolvingShunsuikenAction
                    && damage > 0.0) {
                handleShunsuikenHit(simulator, time);
            }
        });
        if (simulator.getActiveCharacter() != this) {
            scheduleA4(simulator, simulator.getCurrentTime());
        }
    }

    /** Captures Namisen, particle draws, and all delayed owner work. */
    @Override
    public State captureCharacterState() {
        return new AyatoState(
                this,
                particleDrawCursor,
                normalAttackStep,
                shunsuikenStep,
                namisenStacks,
                skillExpirationTime,
                nextParticleTime,
                a4Generation,
                c6Ready,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Ayato instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof AyatoState
                && ((AyatoState) state).owner == this;
    }

    /** Restores owner state and reconstructs each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Kamisato Ayato state");
        }
        initializeForSimulator(simulator);
        AyatoState restored = (AyatoState) state;
        particleDrawCursor = restored.particleDrawCursor;
        normalAttackStep = restored.normalAttackStep;
        shunsuikenStep = restored.shunsuikenStep;
        namisenStacks = restored.namisenStacks;
        skillExpirationTime = restored.skillExpirationTime;
        nextParticleTime = restored.nextParticleTime;
        a4Generation = restored.a4Generation;
        c6Ready = restored.c6Ready;
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

    /** Returns Ayato's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Applies C2's Max-HP increase only at three or more stacks. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (constellation >= 2 && namisenStacks >= 3) {
            stats.add(StatType.HP_PERCENT,
                    getTalentValue("C2 HP Bonus", 0.50));
        }
    }

    /** Clears Soukai Kanka and starts the off-field A4 check. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        skillExpirationTime = Double.NEGATIVE_INFINITY;
        namisenStacks = 0;
        normalAttackStep = 0;
        shunsuikenStep = 0;
        c6Ready = false;
        scheduleA4(simulator, simulator.getCurrentTime());
    }

    /** Stops the current off-field A4 chain and resets both strings. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        a4Generation++;
        normalAttackStep = 0;
        shunsuikenStep = 0;
    }

    /** Returns whether Soukai Kanka is active at the supplied time. */
    public boolean isSkillActive(double currentTime) {
        return currentTime < skillExpirationTime;
    }

    /** Returns the current Namisen stack count. */
    public int getNamisenStacks() {
        return namisenStacks;
    }

    /** Returns the current Soukai Kanka expiration timestamp. */
    public double getSkillExpirationTime() {
        return skillExpirationTime;
    }

    /** Reports the explicit fixed-target boundary for enemy-HP C1. */
    public boolean isC1ConditionRepresented() {
        return false;
    }

    /** Dispatches Ayato's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Kamisato Ayato action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Kamisato Ayato supports Press Skill only");
        }
        if (request.getKey() == CharacterActionKey.CHARGE) {
            requireOutsideSkill(simulator, "Charged Attack");
        }
        if (request.getKey() == CharacterActionKey.PLUNGE) {
            requireOutsideSkill(simulator, "Plunge");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
            shunsuikenStep = 0;
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
                kyouka(simulator);
                break;
            case BURST:
                suiyuu(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Kamisato Ayato: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        if (isSkillActive(simulator.getCurrentTime())) {
            shunsuiken(simulator);
            return;
        }
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + resolveNormalDuration(
                            simulator,
                            NORMAL_HIT_FRAMES[step][hit] * FRAME),
                    HitKind.NORMAL,
                    step,
                    hit,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1) % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(resolveNormalDuration(
                simulator, NORMAL_DURATIONS[step] * FRAME));
    }

    private void shunsuiken(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = shunsuikenStep;
        StatsContainer snapshot = captureLiveStats(castTime);
        String namisenKey = constellation >= 3
                ? "Namisen Per Stack C3" : "Namisen Per Stack";
        double namisenRatio = getTalentValue(
                namisenKey, constellation >= 3 ? 0.012657 : 0.010308);
        snapshot.add(StatType.FLAT_DMG_BONUS,
                snapshot.getTotalHp() * namisenRatio * namisenStacks);
        queueHit(simulator, new PendingHit(
                castTime + resolveNormalDuration(
                        simulator,
                        getTalentValue("Shunsuiken Hit Frames", 5.0)
                                * FRAME),
                HitKind.SHUNSUIKEN,
                step,
                0,
                snapshot));
        shunsuikenStep = (shunsuikenStep + 1)
                % SHUNSUIKEN_MULTIPLIERS.length;
        simulator.advanceTime(resolveNormalDuration(
                simulator,
                getTalentValue("Shunsuiken Duration Frames", 23.0)
                        * FRAME));
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 24.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                null));
        simulator.advanceTime(55.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 47.0 * FRAME,
                HitKind.PLUNGE,
                0,
                0,
                null));
        simulator.advanceTime(76.0 * FRAME);
    }

    private void kyouka(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        skillExpirationTime = castTime
                + getTalentValue("Skill Duration", 6.0);
        namisenStacks = 2;
        normalAttackStep = 0;
        shunsuikenStep = 0;
        c6Ready = constellation >= 6;
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Illusion Hit Frames", 35.0) * FRAME,
                HitKind.ILLUSION,
                0,
                0,
                null));
        simulator.advanceTime(21.0 * FRAME);
    }

    private void suiyuu(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        if (constellation >= 4) {
            applyC4(simulator, castTime);
        }
        queueCommand(simulator, new PendingCommand(
                castTime + 5.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0L));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Burst Snapshot Frames", 101.0) * FRAME,
                CommandKind.BURST_SNAPSHOT,
                0L));
        simulator.advanceTime(123.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                resolveCharged(simulator);
                break;
            case PLUNGE:
                resolvePlunge(simulator);
                break;
            case SHUNSUIKEN:
                resolveShunsuiken(simulator, hit);
                break;
            case ILLUSION:
                resolveIllusion(simulator);
                break;
            case C6:
                resolveC6(simulator, hit);
                break;
            case BURST:
                resolveBurst(simulator, hit);
                break;
            default:
                throw new IllegalStateException("Unknown Kamisato Ayato hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        String key = NORMAL_KEYS[hit.index][hit.subIndex];
        AttackAction action = attack(
                "Kamisato Art: Marobashi " + key,
                getTalentValue(
                        key,
                        NORMAL_MULTIPLIERS[hit.index][hit.subIndex]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        HitlagProfile hitlagProfile = normalHitlag(hit.index);
        if (hitlagProfile != null) {
            action.setHitlagProfile(hitlagProfile);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveCharged(CombatSimulator simulator) {
        AttackAction action = attack(
                "Kamisato Art: Marobashi Charged Attack",
                getTalentValue("Charged Attack", 2.379731),
                Element.PHYSICAL,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.None,
                ICDTag.ChargedAttack,
                0.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolvePlunge(CombatSimulator simulator) {
        AttackAction action = attack(
                "Kamisato Art: Marobashi High Plunge",
                getTalentValue("High Plunge", 2.933586),
                Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                0.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveShunsuiken(
            CombatSimulator simulator,
            PendingHit hit) {
        int step = hit.index;
        String key = "Shunsuiken N" + (step + 1)
                + (constellation >= 3 ? " C3" : "");
        double fallback = constellation >= 3
                ? SHUNSUIKEN_C3_MULTIPLIERS[step]
                : SHUNSUIKEN_MULTIPLIERS[step];
        AttackAction action = attack(
                "Kamisato Art: Kyouka " + key,
                getTalentValue(key, fallback),
                Element.HYDRO,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        action.setHitlagProfile(SHUNSUIKEN_HITLAG);
        resolvingShunsuikenAction = action;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingShunsuikenAction = null;
        }
    }

    private void resolveIllusion(CombatSimulator simulator) {
        String key = constellation >= 3
                ? "Water Illusion C3" : "Water Illusion";
        AttackAction action = attack(
                "Kamisato Art: Kyouka Water Illusion",
                getTalentValue(key, constellation >= 3 ? 2.2892 : 1.8644),
                Element.HYDRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
        namisenStacks = constellation >= 2 ? 5 : 4;
    }

    private void resolveC6(CombatSimulator simulator, PendingHit hit) {
        AttackAction action = attack(
                "Boundless Origin (C6) " + (hit.index + 1),
                getTalentValue("C6 Multiplier", 4.5),
                Element.HYDRO,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.None,
                ICDTag.NormalAttack,
                1.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        action.setHitlagProfile(C6_HITLAG);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile normalHitlag(int step) {
        if (step <= 1) {
            return NORMAL_HITLAG_SHORT;
        }
        if (step == 2) {
            return NORMAL_HITLAG_N3;
        }
        if (step == 4) {
            return NORMAL_HITLAG_N5;
        }
        return null;
    }

    private void resolveBurst(
            CombatSimulator simulator,
            PendingHit hit) {
        String key = constellation >= 5
                ? "Bloomwater Blade C5" : "Bloomwater Blade";
        AttackAction action = attack(
                "Kamisato Art: Suiyuu Bloomwater Blade "
                        + (hit.index + 1),
                getTalentValue(key, constellation >= 5
                        ? 1.32912 : 1.129752),
                Element.HYDRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void handleShunsuikenHit(
            CombatSimulator simulator,
            double currentTime) {
        int maximum = constellation >= 2 ? 5 : 4;
        if (namisenStacks < maximum) {
            namisenStacks++;
        }
        if (currentTime + EPSILON >= nextParticleTime) {
            nextParticleTime = currentTime
                    + getTalentValue("Particle Cooldown", 1.9);
            int count = nextParticleCount();
            queueCommand(simulator, new PendingCommand(
                    currentTime + getTalentValue(
                            "Particle Travel Frames", 80.0) * FRAME,
                    CommandKind.PARTICLE,
                    count));
        }
        if (constellation >= 6 && c6Ready) {
            c6Ready = false;
            queueHit(simulator, new PendingHit(
                    currentTime + 20.0 * FRAME,
                    HitKind.C6,
                    0,
                    0,
                    null));
            queueHit(simulator, new PendingHit(
                    currentTime + 22.0 * FRAME,
                    HitKind.C6,
                    1,
                    0,
                    null));
        }
    }

    private void snapshotBurst(CombatSimulator simulator) {
        double snapshotTime = simulator.getCurrentTime();
        double castTime = snapshotTime
                - getTalentValue("Burst Snapshot Frames", 101.0) * FRAME;
        StatsContainer snapshot = captureLiveStats(snapshotTime);
        applyBurstBuff(simulator, snapshotTime);
        int count = (int) getTalentValue("Burst Hit Count", 12.0);
        for (int index = 0; index < count; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + (getTalentValue(
                            "Burst First Impact Frames", 139.0)
                            + index * getTalentValue(
                                    "Burst Impact Interval Frames", 90.0))
                            * FRAME,
                    HitKind.BURST,
                    index,
                    0,
                    snapshot));
        }
    }

    private void applyBurstBuff(
            CombatSimulator simulator,
            double currentTime) {
        String key = constellation >= 5
                ? "Normal DMG Bonus C5" : "Normal DMG Bonus";
        double value = getTalentValue(
                key, constellation >= 5 ? 0.20 : 0.19);
        for (Character member : simulator.getPartyMembers()) {
            member.removeBuff(BuffId.AYATO_BURST_NORMAL_DMG);
            member.addBuff(new SimpleBuff(
                    "Kamisato Art: Suiyuu Normal DMG",
                    BuffId.AYATO_BURST_NORMAL_DMG,
                    getTalentValue("Burst Duration", 18.0),
                    currentTime,
                    stats -> stats.add(
                            StatType.NORMAL_ATTACK_DMG_BONUS, value))
                    .sourcedBy(characterId));
        }
    }

    private void applyC4(CombatSimulator simulator, double currentTime) {
        double value = getTalentValue("C4 Normal Attack Speed", 0.15);
        for (Character member : simulator.getPartyMembers()) {
            member.removeBuff(BuffId.AYATO_C4_NORMAL_SPEED);
            member.addBuff(new SimpleBuff(
                    "Kamisato Ayato C4 Normal Attack Speed",
                    BuffId.AYATO_C4_NORMAL_SPEED,
                    getTalentValue("C4 Duration", 15.0),
                    currentTime,
                    stats -> stats.add(
                            StatType.NORMAL_ATTACK_SPD, value))
                    .sourcedBy(characterId));
        }
    }

    private void scheduleA4(
            CombatSimulator simulator,
            double currentTime) {
        long generation = ++a4Generation;
        queueCommand(simulator, new PendingCommand(
                currentTime + 1.0,
                CommandKind.A4,
                generation));
    }

    private void resolveA4(
            CombatSimulator simulator,
            long generation) {
        if (generation != a4Generation
                || simulator.getActiveCharacter() == this
                || getCurrentEnergy() >= getTalentValue(
                        "A4 Energy Threshold", 40.0)) {
            return;
        }
        receiveEnergy(getTalentValue("A4 Energy", 2.0));
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime() + 1.0,
                CommandKind.A4,
                generation));
    }

    private int nextParticleCount() {
        double draw;
        if (particleDrawCursor < particleDrawTape.size()) {
            draw = particleDrawTape.get(particleDrawCursor++);
        } else {
            draw = particleDrawSource.getAsDouble();
            if (draw < 0.0 || draw > 1.0 || Double.isNaN(draw)) {
                throw new IllegalStateException(
                        "Kamisato Ayato particle draw must be in [0, 1]");
            }
            particleDrawTape.add(draw);
            particleDrawCursor++;
        }
        return draw < 0.5 ? 2 : 1;
    }

    private void requireOutsideSkill(
            CombatSimulator simulator,
            String actionName) {
        if (isSkillActive(simulator.getCurrentTime())) {
            throw new IllegalStateException(
                    "Kamisato Ayato cannot use " + actionName
                            + " during Soukai Kanka");
        }
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
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case BURST_SNAPSHOT:
                    snapshotBurst(activeSimulator);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.HYDRO,
                            (int) command.value,
                            ParticleType.PARTICLE);
                    break;
                case A4:
                    resolveA4(activeSimulator, command.value);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Kamisato Ayato command kind");
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
        SHUNSUIKEN,
        ILLUSION,
        C6,
        BURST
    }

    private enum CommandKind {
        BURST_ENERGY,
        BURST_SNAPSHOT,
        PARTICLE,
        A4
    }

    /** Immutable delayed Ayato hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, subIndex, snapshot);
        }
    }

    /** Immutable delayed Ayato command description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long value;

        private PendingCommand(
                double time,
                CommandKind kind,
                long value) {
            this.time = time;
            this.kind = kind;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value);
        }
    }

    /** Immutable owner-bound Ayato rollback payload. */
    private static final class AyatoState implements State {
        private final KamisatoAyato owner;
        private final int particleDrawCursor;
        private final int normalAttackStep;
        private final int shunsuikenStep;
        private final int namisenStacks;
        private final double skillExpirationTime;
        private final double nextParticleTime;
        private final long a4Generation;
        private final boolean c6Ready;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private AyatoState(
                KamisatoAyato owner,
                int particleDrawCursor,
                int normalAttackStep,
                int shunsuikenStep,
                int namisenStacks,
                double skillExpirationTime,
                double nextParticleTime,
                long a4Generation,
                boolean c6Ready,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.particleDrawCursor = particleDrawCursor;
            this.normalAttackStep = normalAttackStep;
            this.shunsuikenStep = shunsuikenStep;
            this.namisenStacks = namisenStacks;
            this.skillExpirationTime = skillExpirationTime;
            this.nextParticleTime = nextParticleTime;
            this.a4Generation = a4Generation;
            this.c6Ready = c6Ready;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
