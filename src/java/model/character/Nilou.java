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
 * Nilou's fixed-target Dance of Haftkarsvar offensive slice through C6.
 *
 * <p>Normal and Charged Attacks, the initial Skill, mixed Sword Dance and
 * Whirling Steps, Lunar Prayer, Tranquility Aura application, Burst's two
 * hits, particles, Golden Chalice composition gating, and representable
 * constellations follow pinned gcsim {@code ef41805d}. Skill and Burst damage
 * use Max HP snapshots, while the sustained aura uses Nilou's 1.9-second
 * application gate.</p>
 *
 * <p>Bountiful Core replacement, core lifetime and area, player self-damage,
 * the A1 Dendro-hit EM trigger, A4's Bountiful-Core-only bonus, C2's
 * Bountiful-Core Dendro shred, movement, geometry, and multi-target behavior
 * are excluded rather than mapped onto ordinary Bloom.</p>
 */
public final class Nilou extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 12, 9, 17 };
    private static final int[] NORMAL_DURATIONS = { 24, 27, 58 };
    private static final int[] SWORD_HIT_FRAMES = { 14, 12, 35 };
    private static final int[] SWORD_DURATIONS = { 20, 23, 60 };
    private static final int[] WHIRLING_HIT_FRAMES = { 21, 29, 43 };
    private static final int[] WHIRLING_DURATIONS = { 33, 62, 63 };
    private static final double[] NORMAL_T9 = {
        0.924253, 0.834809, 1.292551
    };
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[] NORMAL_HITLAG = {
        new HitlagProfile(0.03, 0.01, true, false, false),
        new HitlagProfile(0.03, 0.01, true, false, false),
        new HitlagProfile(0.06, 0.01, true, false, false)
    };
    private static final double[] SWORD_T9 = {
        0.077392, 0.087456, 0.121870
    };
    private static final double[] SWORD_T12 = {
        0.091050, 0.102890, 0.143376
    };
    private static final double[] WHIRLING_T9 = {
        0.055453, 0.067328, 0.086047
    };
    private static final double[] WHIRLING_T12 = {
        0.065238, 0.079210, 0.101232
    };

    private final DoubleSupplier particleRandom;
    private CombatSimulator initializedSimulator;
    private boolean bloomComposition;
    private int normalAttackStep;
    private int danceStep;
    private long stanceGeneration;
    private long auraGeneration;
    private double pirouetteExpirationTime = Double.NEGATIVE_INFINITY;
    private double lunarPrayerExpirationTime = Double.NEGATIVE_INFINITY;
    private double tranquilityExpirationTime = Double.NEGATIVE_INFINITY;
    private double goldenChaliceExpirationTime = Double.NEGATIVE_INFINITY;
    private double c4BurstBonusExpirationTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Nilou. */
    public Nilou(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Nilou at an explicit constellation. */
    public Nilou(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Nilou with injectable data and particle randomness.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param particleRandom source of initial-particle draws in {@code [0, 1)}
     */
    public Nilou(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleRandom) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Nilou constellation must be between 0 and 6");
        }
        if (particleRandom == null) {
            throw new IllegalArgumentException(
                    "Nilou particle random source is required");
        }
        name = "Nilou";
        characterId = CharacterId.NILOU;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleRandom = particleRandom;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 15185.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 230.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 729.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP Percent", 0.288));
        setSkillCD(getTalentValue("Skill Cooldown", 18.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
    }

    /** Binds composition and C2 damage observation to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Nilou simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Nilou must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Nilou cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        bloomComposition = false;
        if (constellation >= 2) {
            simulator.addDamageListener((actor, action, damage, time) ->
                    handleC2HydroDamage(
                            actor, action, damage, time, simulator));
        }
    }

    /** Captures all Nilou-owned windows and reconstructable future work. */
    @Override
    public State captureCharacterState() {
        return new NilouState(
                this,
                bloomComposition,
                normalAttackStep,
                danceStep,
                stanceGeneration,
                auraGeneration,
                pirouetteExpirationTime,
                lunarPrayerExpirationTime,
                tranquilityExpirationTime,
                goldenChaliceExpirationTime,
                c4BurstBonusExpirationTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Nilou instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof NilouState
                && ((NilouState) state).owner == this;
    }

    /** Restores Nilou-owned state and schedules each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Nilou state");
        }
        initializeForSimulator(simulator);
        NilouState restored = (NilouState) state;
        bloomComposition = restored.bloomComposition;
        normalAttackStep = restored.normalAttackStep;
        danceStep = restored.danceStep;
        stanceGeneration = restored.stanceGeneration;
        auraGeneration = restored.auraGeneration;
        pirouetteExpirationTime = restored.pirouetteExpirationTime;
        lunarPrayerExpirationTime = restored.lunarPrayerExpirationTime;
        tranquilityExpirationTime = restored.tranquilityExpirationTime;
        goldenChaliceExpirationTime =
                restored.goldenChaliceExpirationTime;
        c4BurstBonusExpirationTime =
                restored.c4BurstBonusExpirationTime;
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

    /** Returns Nilou's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies C6's Max-HP-derived CRIT Rate and CRIT DMG. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (constellation < 6) {
            return;
        }
        double hpThousands = stats.getTotalHp() / 1000.0;
        stats.add(StatType.CRIT_RATE, Math.min(
                getTalentValue("C6 CRIT Rate Cap", 0.30),
                hpThousands * getTalentValue(
                        "C6 CRIT Rate Per 1000 HP", 0.006)));
        stats.add(StatType.CRIT_DMG, Math.min(
                getTalentValue("C6 CRIT DMG Cap", 0.60),
                hpThousands * getTalentValue(
                        "C6 CRIT DMG Per 1000 HP", 0.012)));
    }

    /** Lets Pirouette continuation Skills pass the shared cooldown gateway. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        normalizeAt(currentTime);
        if (isPirouetteActive(currentTime)) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Returns whether a continuation or fresh Skill is currently ready. */
    @Override
    public boolean canSkill(double currentTime) {
        return getSkillCDRemaining(currentTime) <= EPSILON;
    }

    /** Clears on-field dance states while preserving Tranquility Aura. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        danceStep = 0;
        stanceGeneration++;
        pirouetteExpirationTime = Double.NEGATIVE_INFINITY;
        lunarPrayerExpirationTime = Double.NEGATIVE_INFINITY;
    }

    /** Returns whether the ten-second Pirouette input window is active. */
    public boolean isPirouetteActive(double currentTime) {
        return currentTime + EPSILON < pirouetteExpirationTime;
    }

    /** Returns whether Lunar Prayer converts Normals into Sword Dance. */
    public boolean isLunarPrayerActive(double currentTime) {
        return currentTime + EPSILON < lunarPrayerExpirationTime;
    }

    /** Returns whether Tranquility Aura is active. */
    public boolean isTranquilityAuraActive(double currentTime) {
        return currentTime + EPSILON < tranquilityExpirationTime;
    }

    /** Returns whether Golden Chalice's Bounty is composition-valid and active. */
    public boolean isGoldenChaliceActive(double currentTime) {
        return bloomComposition
                && currentTime + EPSILON < goldenChaliceExpirationTime;
    }

    /** Reports that Bountiful Core replacement is deliberately unsupported. */
    public boolean isBountifulCoreReplacementRepresented() {
        return false;
    }

    /** Returns the next Pirouette or Lunar Prayer dance-step index. */
    public int getDanceStep() {
        return danceStep;
    }

    /** Returns the active Tranquility Aura expiration timestamp. */
    public double getTranquilityExpirationTime() {
        return tranquilityExpirationTime;
    }

    /** Returns the number of unresolved Nilou-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Nilou's represented fixed-target action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Nilou action is required");
        }
        initializeForSimulator(simulator);
        normalizeAt(simulator.getCurrentTime());
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Nilou supports Press Skill only");
        }
        if ((isPirouetteActive(simulator.getCurrentTime())
                || isLunarPrayerActive(simulator.getCurrentTime()))
                && (request.getKey() == CharacterActionKey.CHARGE
                        || request.getKey() == CharacterActionKey.PLUNGE)) {
            throw new IllegalStateException(
                    "Nilou cannot use Charged or Plunging Attacks during dance state");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                if (isPirouetteActive(simulator.getCurrentTime())) {
                    swordDance(simulator, true);
                } else if (isLunarPrayerActive(
                        simulator.getCurrentTime())) {
                    swordDance(simulator, false);
                } else {
                    normalAttack(simulator);
                }
                break;
            case CHARGE:
                chargedAttack(simulator);
                break;
            case SKILL:
                if (isPirouetteActive(simulator.getCurrentTime())) {
                    whirlingStep(simulator);
                } else {
                    danceOfHaftkarsvar(simulator);
                }
                break;
            case BURST:
                danceOfAbzendegi(simulator);
                break;
            case DASH:
            case PLUNGE:
                throw new IllegalArgumentException(
                        "Nilou movement and Plunge actions are outside the pinned slice");
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Nilou: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                captureLiveStats(castTime),
                0L,
                false));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        queueHit(simulator, new PendingHit(
                castTime + 26.0 * FRAME,
                HitKind.CHARGED,
                0,
                snapshot,
                0L,
                false));
        queueHit(simulator, new PendingHit(
                castTime + 27.0 * FRAME,
                HitKind.CHARGED,
                1,
                snapshot,
                0L,
                false));
        simulator.advanceTime(43.0 * FRAME);
    }

    private void danceOfHaftkarsvar(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++stanceGeneration;
        danceStep = 0;
        pirouetteExpirationTime = castTime
                + getTalentValue("Pirouette Duration", 10.0);
        lunarPrayerExpirationTime = Double.NEGATIVE_INFINITY;
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        queueHit(simulator, new PendingHit(
                castTime + 16.0 * FRAME,
                HitKind.SKILL_INITIAL,
                0,
                captureLiveStats(castTime),
                generation,
                true));
        simulator.advanceTime(22.0 * FRAME);
    }

    private void swordDance(
            CombatSimulator simulator,
            boolean pirouetteStep) {
        double castTime = simulator.getCurrentTime();
        int step = danceStep;
        long generation = stanceGeneration;
        queueHit(simulator, new PendingHit(
                castTime + SWORD_HIT_FRAMES[step] * FRAME,
                HitKind.SWORD_DANCE,
                step,
                captureLiveStats(castTime),
                generation,
                pirouetteStep));
        danceStep = (danceStep + 1) % 3;
        if (pirouetteStep && step == 2) {
            queueCommand(simulator, new PendingCommand(
                    castTime + 30.0 * FRAME,
                    CommandKind.COMPLETE_SWORD_DANCE,
                    generation,
                    0.0));
        }
        simulator.advanceTime(SWORD_DURATIONS[step] * FRAME);
    }

    private void whirlingStep(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = danceStep;
        long generation = stanceGeneration;
        queueHit(simulator, new PendingHit(
                castTime + WHIRLING_HIT_FRAMES[step] * FRAME,
                HitKind.WHIRLING_STEP,
                step,
                captureLiveStats(castTime),
                generation,
                true));
        danceStep = (danceStep + 1) % 3;
        if (step == 2) {
            queueCommand(simulator, new PendingCommand(
                    castTime + 40.0 * FRAME,
                    CommandKind.COMPLETE_WHIRLING_STEPS,
                    generation,
                    0.0));
        }
        simulator.advanceTime(WHIRLING_DURATIONS[step] * FRAME);
    }

    private void danceOfAbzendegi(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        queueHit(simulator, new PendingHit(
                castTime + 91.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                snapshot,
                0L,
                false));
        queueHit(simulator, new PendingHit(
                castTime + 212.0 * FRAME,
                HitKind.BURST_AEON,
                0,
                snapshot,
                0L,
                false));
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0L,
                0.0));
        simulator.advanceTime(110.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Dance of Samser N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        false);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Dance of Samser Charged " + (hit.index + 1),
                        getTalentValue(
                                "Charged Hit " + (hit.index + 1),
                                hit.index == 0 ? 0.922720 : 1.000140),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        false);
                break;
            case SKILL_INITIAL:
                performHit(
                        simulator,
                        hit,
                        "Dance of Haftkarsvar",
                        skillValue("Skill Initial", 0.056761, 0.066778),
                        Element.HYDRO,
                        StatType.BASE_HP,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        false);
                triggerInitialParticles(simulator, hit.time);
                break;
            case SWORD_DANCE:
                resolveSwordDance(simulator, hit);
                break;
            case WHIRLING_STEP:
                resolveWhirlingStep(simulator, hit);
                break;
            case BURST_INITIAL:
                resolveBurst(simulator, hit, false);
                break;
            case BURST_AEON:
                resolveBurst(simulator, hit, true);
                break;
            case TRANQUILITY_AURA:
                resolveTranquilityAura(simulator, hit);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Nilou hit kind " + hit.kind);
        }
    }

    private void resolveSwordDance(
            CombatSimulator simulator,
            PendingHit hit) {
        String[] names = {
            "Sword Dance 1", "Sword Dance 2", "Luminous Illusion"
        };
        double value = skillValue(
                names[hit.index],
                SWORD_T9[hit.index],
                SWORD_T12[hit.index]);
        performHit(
                simulator,
                hit,
                names[hit.index],
                value,
                Element.HYDRO,
                StatType.BASE_HP,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                constellation >= 1 && hit.index == 2);
        finishPirouetteHit(simulator, hit);
    }

    private void resolveWhirlingStep(
            CombatSimulator simulator,
            PendingHit hit) {
        String[] names = {
            "Whirling Step 1", "Whirling Step 2", "Water Wheel"
        };
        double value = skillValue(
                names[hit.index],
                WHIRLING_T9[hit.index],
                WHIRLING_T12[hit.index]);
        performHit(
                simulator,
                hit,
                names[hit.index],
                value,
                Element.HYDRO,
                StatType.BASE_HP,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                false);
        finishPirouetteHit(simulator, hit);
    }

    private void finishPirouetteHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if (!hit.pirouetteStep || simulator.getEnemy() == null) {
            return;
        }
        queueParticle(simulator, hit.time, 1.0);
        if (constellation >= 4 && hit.index == 2) {
            receiveFlatEnergy(getTalentValue("C4 Energy", 15.0));
            c4BurstBonusExpirationTime = hit.time
                    + getTalentValue("C4 Duration", 8.0);
        }
    }

    private void resolveBurst(
            CombatSimulator simulator,
            PendingHit hit,
            boolean lingering) {
        String key = lingering ? "Lingering Aeon" : "Dance of Abzendegi";
        double talentNine = lingering ? 0.382976 : 0.313344;
        double talentTwelve = lingering ? 0.450560 : 0.368640;
        PendingHit resolved = hit;
        performHit(
                simulator,
                resolved,
                key,
                burstValue(key, talentNine, talentTwelve),
                Element.HYDRO,
                StatType.BASE_HP,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                false);
    }

    private void resolveTranquilityAura(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.generation != auraGeneration
                || hit.time + EPSILON >= tranquilityExpirationTime) {
            return;
        }
        performHit(
                simulator,
                hit,
                "Tranquility Aura",
                0.0,
                Element.HYDRO,
                StatType.BASE_HP,
                null,
                ActionType.OTHER,
                ICDType.NilouTranquility,
                ICDTag.Nilou_TranquilityAura,
                false);
        double nextTime = hit.time + 0.5;
        if (nextTime + EPSILON < tranquilityExpirationTime) {
            queueHit(simulator, new PendingHit(
                    nextTime,
                    HitKind.TRANQUILITY_AURA,
                    0,
                    null,
                    hit.generation,
                    false));
        }
    }

    private void completeDance(
            CombatSimulator simulator,
            PendingCommand command,
            boolean swordRoute) {
        if (command.generation != stanceGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        pirouetteExpirationTime = Double.NEGATIVE_INFINITY;
        danceStep = 0;
        if (swordRoute) {
            lunarPrayerExpirationTime = currentTime
                    + getTalentValue("Lunar Prayer Duration", 8.0);
        } else {
            lunarPrayerExpirationTime = Double.NEGATIVE_INFINITY;
            long generation = ++auraGeneration;
            double duration = getTalentValue(
                    "Tranquility Aura Duration", 12.0);
            if (constellation >= 1) {
                duration += getTalentValue(
                        "C1 Tranquility Extension", 6.0);
            }
            tranquilityExpirationTime = currentTime + duration;
            queueHit(simulator, new PendingHit(
                    currentTime + FRAME,
                    HitKind.TRANQUILITY_AURA,
                    0,
                    null,
                    generation,
                    false));
        }
        bloomComposition = hasBloomComposition(simulator);
        if (bloomComposition) {
            goldenChaliceExpirationTime = currentTime
                    + getTalentValue("Golden Chalice Duration", 30.0);
        }
    }

    private void triggerInitialParticles(
            CombatSimulator simulator,
            double hitTime) {
        if (simulator.getEnemy() == null) {
            return;
        }
        double draw = particleRandom.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Nilou particle random draw must be in [0, 1)");
        }
        queueParticle(simulator, hitTime, draw < 0.5 ? 2.0 : 1.0);
    }

    private void queueParticle(
            CombatSimulator simulator,
            double hitTime,
            double count) {
        queueCommand(simulator, new PendingCommand(
                hitTime
                        + getTalentValue(
                                "Particle Travel Frames", 100.0) * FRAME,
                CommandKind.PARTICLE,
                0L,
                count));
    }

    private void handleC2HydroDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || damage <= 0.0
                || action.getElement() != Element.HYDRO
                || !simulator.getPartyMembers().contains(actor)
                || !isGoldenChaliceActive(time)) {
            return;
        }
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Nilou C2 Hydro RES Shred",
                BuffId.NILOU_C2_HYDRO_RES_SHRED,
                getTalentValue("C2 Duration", 10.0),
                time,
                stats -> stats.add(
                        StatType.HYDRO_RES_SHRED,
                        getTalentValue("C2 Hydro RES Shred", 0.35)))
                .sourcedBy(characterId));
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            boolean c1Illusion) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                scalingStat,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, 1.0);
        if (hit.kind == HitKind.NORMAL) {
            action.setHitlagProfile(NORMAL_HITLAG[hit.index]);
        }
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (c1Illusion) {
            action.addBonusStat(
                    StatType.SKILL_DMG_BONUS, 0.65);
        }
        if (actionType == ActionType.BURST
                && hit.time + EPSILON < c4BurstBonusExpirationTime) {
            action.addBonusStat(
                    StatType.BURST_DMG_BONUS,
                    getTalentValue("C4 Burst DMG Bonus", 0.50));
        }
        if (multiplier == 0.0) {
            action.setHitEffectTrigger(false);
        }
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private double skillValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(
                key + suffix,
                constellation >= 5 ? talentTwelve : talentNine);
    }

    private double burstValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue(
                key + suffix,
                constellation >= 3 ? talentTwelve : talentNine);
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
        return stats;
    }

    private boolean hasBloomComposition(CombatSimulator simulator) {
        boolean hasHydro = false;
        boolean hasDendro = false;
        for (Character member : simulator.getPartyMembers()) {
            if (member.getElement() == Element.HYDRO) {
                hasHydro = true;
            } else if (member.getElement() == Element.DENDRO) {
                hasDendro = true;
            } else {
                return false;
            }
        }
        return hasHydro && hasDendro;
    }

    private void normalizeAt(double currentTime) {
        if (pirouetteExpirationTime > Double.NEGATIVE_INFINITY
                && currentTime + EPSILON >= pirouetteExpirationTime) {
            pirouetteExpirationTime = Double.NEGATIVE_INFINITY;
            danceStep = 0;
        }
        if (lunarPrayerExpirationTime > Double.NEGATIVE_INFINITY
                && currentTime + EPSILON >= lunarPrayerExpirationTime) {
            lunarPrayerExpirationTime = Double.NEGATIVE_INFINITY;
            danceStep = 0;
        }
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
                case COMPLETE_SWORD_DANCE:
                    completeDance(activeSimulator, command, true);
                    break;
                case COMPLETE_WHIRLING_STEPS:
                    completeDance(activeSimulator, command, false);
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.HYDRO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Nilou command kind " + command.kind);
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
        SKILL_INITIAL,
        SWORD_DANCE,
        WHIRLING_STEP,
        BURST_INITIAL,
        BURST_AEON,
        TRANQUILITY_AURA
    }

    private enum CommandKind {
        COMPLETE_SWORD_DANCE,
        COMPLETE_WHIRLING_STEPS,
        BURST_ENERGY,
        PARTICLE
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final StatsContainer snapshot;
        private final long generation;
        private final boolean pirouetteStep;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                StatsContainer snapshot,
                long generation,
                boolean pirouetteStep) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.generation = generation;
            this.pirouetteStep = pirouetteStep;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    snapshot,
                    generation,
                    pirouetteStep);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final double value;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                double value) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, generation, value);
        }
    }

    private static final class NilouState implements State {
        private final Nilou owner;
        private final boolean bloomComposition;
        private final int normalAttackStep;
        private final int danceStep;
        private final long stanceGeneration;
        private final long auraGeneration;
        private final double pirouetteExpirationTime;
        private final double lunarPrayerExpirationTime;
        private final double tranquilityExpirationTime;
        private final double goldenChaliceExpirationTime;
        private final double c4BurstBonusExpirationTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private NilouState(
                Nilou owner,
                boolean bloomComposition,
                int normalAttackStep,
                int danceStep,
                long stanceGeneration,
                long auraGeneration,
                double pirouetteExpirationTime,
                double lunarPrayerExpirationTime,
                double tranquilityExpirationTime,
                double goldenChaliceExpirationTime,
                double c4BurstBonusExpirationTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.bloomComposition = bloomComposition;
            this.normalAttackStep = normalAttackStep;
            this.danceStep = danceStep;
            this.stanceGeneration = stanceGeneration;
            this.auraGeneration = auraGeneration;
            this.pirouetteExpirationTime = pirouetteExpirationTime;
            this.lunarPrayerExpirationTime = lunarPrayerExpirationTime;
            this.tranquilityExpirationTime = tranquilityExpirationTime;
            this.goldenChaliceExpirationTime =
                    goldenChaliceExpirationTime;
            this.c4BurstBonusExpirationTime =
                    c4BurstBonusExpirationTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
