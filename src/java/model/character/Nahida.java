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
 * Nahida's fixed-target offensive and Shrine support slice through C6.
 *
 * <p>Timings, talent values, Seed of Skandha, Tri-Karma Purification,
 * Shrine element counts, particles, A1/A4, and representable constellations
 * follow pinned gcsim {@code ef41805d}. The single represented enemy receives
 * one refreshed 25-second Seed from Tap Skill.</p>
 *
 * <p>Hold selection, linked multi-target propagation, geometry, C2 reaction
 * CRIT and target-local same-hit DEF reduction, exploration collection, and
 * defensive state are excluded rather than approximated.</p>
 */
public final class Nahida extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 23, 15, 26, 40 };
    private static final int[] NORMAL_DURATIONS = { 35, 31, 45, 71 };
    private static final double[] NORMAL_T9 = {
        0.685182, 0.628565, 0.779865, 0.992909
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double seedExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextTriKarmaAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private long burstGeneration;
    private double shrineStartTime = Double.NEGATIVE_INFINITY;
    private double shrineEffectStartTime = Double.NEGATIVE_INFINITY;
    private double shrineExpirationTime = Double.NEGATIVE_INFINITY;
    private int shrinePyroCount;
    private int shrineElectroCount;
    private int shrineHydroCount;
    private double a1ElementalMastery;
    private double c6ExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextC6AllowedTime = Double.NEGATIVE_INFINITY;
    private int c6TriggerCount;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Nahida. */
    public Nahida(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Nahida at an explicit constellation. */
    public Nahida(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation);
    }

    /** Constructs Nahida with injectable talent data and constellation state. */
    public Nahida(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Nahida constellation must be between 0 and 6");
        }
        name = "Nahida";
        characterId = CharacterId.NAHIDA;
        element = Element.DENDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10360.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 299.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 630.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Ascension Elemental Mastery", 115.2));
        setSkillCD(getTalentValue("Skill Cooldown", 5.0));
        setBurstCD(getTalentValue("Burst Cooldown", 13.5));
    }

    /** Binds reaction, damage, and delayed-work state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Nahida simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Nahida must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Nahida cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
        simulator.addDamageListener((actor, action, damage, time) ->
                handleC6Trigger(actor, action, damage, time, simulator));
    }

    /** Captures Nahida-owned windows, counters, and reconstructable work. */
    @Override
    public State captureCharacterState() {
        return new NahidaState(
                this,
                normalAttackStep,
                seedExpirationTime,
                nextTriKarmaAllowedTime,
                nextParticleAllowedTime,
                burstGeneration,
                shrineStartTime,
                shrineEffectStartTime,
                shrineExpirationTime,
                shrinePyroCount,
                shrineElectroCount,
                shrineHydroCount,
                a1ElementalMastery,
                c6ExpirationTime,
                nextC6AllowedTime,
                c6TriggerCount,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Nahida instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof NahidaState
                && ((NahidaState) state).owner == this;
    }

    /** Restores all Nahida-owned state and schedules future work once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Nahida state");
        }
        initializeForSimulator(simulator);
        NahidaState restored = (NahidaState) state;
        normalAttackStep = restored.normalAttackStep;
        seedExpirationTime = restored.seedExpirationTime;
        nextTriKarmaAllowedTime = restored.nextTriKarmaAllowedTime;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        burstGeneration = restored.burstGeneration;
        shrineStartTime = restored.shrineStartTime;
        shrineEffectStartTime = restored.shrineEffectStartTime;
        shrineExpirationTime = restored.shrineExpirationTime;
        shrinePyroCount = restored.shrinePyroCount;
        shrineElectroCount = restored.shrineElectroCount;
        shrineHydroCount = restored.shrineHydroCount;
        a1ElementalMastery = restored.a1ElementalMastery;
        c6ExpirationTime = restored.c6ExpirationTime;
        nextC6AllowedTime = restored.nextC6AllowedTime;
        c6TriggerCount = restored.c6TriggerCount;
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

    /** Returns Nahida's 50-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 50.0);
    }

    /** Applies fixed-target C4 while the represented enemy remains marked. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (constellation >= 4
                && initializedSimulator != null
                && isSeedActive(initializedSimulator.getCurrentTime())) {
            stats.add(StatType.ELEMENTAL_MASTERY,
                    getTalentValue("C4 Elemental Mastery", 100.0));
        }
    }

    /** Resets Nahida's Normal Attack sequence on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether the represented enemy has an unexpired Seed. */
    public boolean isSeedActive(double currentTime) {
        return currentTime + EPSILON < seedExpirationTime;
    }

    /** Returns the absolute Seed expiry time. */
    public double getSeedExpirationTime() {
        return seedExpirationTime;
    }

    /** Returns whether Shrine element effects are active at this timestamp. */
    public boolean isShrineEffectActive(double currentTime) {
        return currentTime + EPSILON >= shrineEffectStartTime
                && currentTime + EPSILON < shrineExpirationTime;
    }

    /** Returns the current Shrine expiry time. */
    public double getShrineExpirationTime() {
        return shrineExpirationTime;
    }

    /** Returns the current C6 Karmic Oblivion trigger count. */
    public int getC6TriggerCount() {
        return c6TriggerCount;
    }

    /** Dispatches Nahida's represented typed actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Nahida action is required");
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
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Nahida Hold Skill is outside this slice");
                }
                allSchemesToKnow(simulator);
                break;
            case BURST:
                illusoryHeart(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Nahida: "
                                + request.getKey());
        }
    }

    /** Triggers one Tri-Karma sequence for an eligible reaction. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || result == null
                || result.getKind() == ReactionResult.Kind.NONE
                || !isSeedActive(time)
                || time + EPSILON < nextTriKarmaAllowedTime) {
            return;
        }
        nextTriKarmaAllowedTime = time + triKarmaInterval(time);
        StatsContainer triggerSnapshot = captureLiveStats(time);
        if (isShrineEffectActive(time)) {
            triggerSnapshot.add(StatType.SKILL_DMG_BONUS, pyroBonus());
        }
        queueHit(simulator, new PendingHit(
                time + getTalentValue(
                        "Tri-Karma Delay Frames", 3.0) * FRAME,
                HitKind.TRI_KARMA,
                0,
                triggerSnapshot));
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                null));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 65.0 * FRAME,
                HitKind.CHARGED,
                0,
                null));
        simulator.advanceTime(65.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 46.0 * FRAME,
                HitKind.PLUNGE,
                0,
                null));
        simulator.advanceTime(68.0 * FRAME);
    }

    private void allSchemesToKnow(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueCommand(simulator, new PendingCommand(
                castTime + 11.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0L,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 13.0 * FRAME,
                HitKind.SKILL_PRESS,
                0,
                null));
        simulator.advanceTime(32.0 * FRAME);
    }

    private void illusoryHeart(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        countShrineElements(simulator);
        double duration = 15.0 + hydroExtension();
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 5.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 66.0 * FRAME,
                CommandKind.BURST_START,
                generation,
                duration));
        queueCommand(simulator, new PendingCommand(
                castTime + 96.0 * FRAME,
                CommandKind.BURST_FIELD_EFFECT,
                generation,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 66.0 * FRAME + duration,
                CommandKind.BURST_EXPIRE,
                generation,
                0.0));
        simulator.advanceTime(112.0 * FRAME);
    }

    private void countShrineElements(CombatSimulator simulator) {
        int pyro = 0;
        int electro = 0;
        int hydro = 0;
        for (Character member : simulator.getPartyMembers()) {
            if (member.getElement() == Element.PYRO) {
                pyro++;
            } else if (member.getElement() == Element.ELECTRO) {
                electro++;
            } else if (member.getElement() == Element.HYDRO) {
                hydro++;
            }
        }
        int c1Bonus = constellation >= 1 ? 1 : 0;
        shrinePyroCount = Math.min(2, pyro + c1Bonus);
        shrineElectroCount = Math.min(2, electro + c1Bonus);
        shrineHydroCount = Math.min(2, hydro + c1Bonus);
    }

    private void startShrine(
            CombatSimulator simulator,
            long generation,
            double duration) {
        if (generation != burstGeneration) {
            return;
        }
        shrineStartTime = simulator.getCurrentTime();
        shrineEffectStartTime = shrineStartTime + 30.0 * FRAME;
        shrineExpirationTime = shrineStartTime + duration;
        a1ElementalMastery = calculateA1ElementalMastery(
                simulator, shrineStartTime);
        if (constellation >= 6) {
            c6ExpirationTime = shrineStartTime
                    + getTalentValue("C6 Duration", 10.0);
            nextC6AllowedTime = Double.NEGATIVE_INFINITY;
            c6TriggerCount = 0;
        }
    }

    private double calculateA1ElementalMastery(
            CombatSimulator simulator,
            double currentTime) {
        double highest = 0.0;
        for (Character member : simulator.getPartyMembers()) {
            highest = Math.max(
                    highest,
                    member.getStructuralStats(currentTime).get(
                            StatType.ELEMENTAL_MASTERY));
        }
        return Math.min(
                getTalentValue("A1 EM Share Cap", 250.0),
                highest * getTalentValue("A1 EM Share Ratio", 0.25));
    }

    private void applyA1Field(CombatSimulator simulator, long generation) {
        double currentTime = simulator.getCurrentTime();
        if (generation != burstGeneration
                || !isShrineEffectActive(currentTime)) {
            return;
        }
        simulator.getFieldBuffList().removeIf(buff ->
                buff.getId()
                        == BuffId.NAHIDA_A1_ACTIVE_ELEMENTAL_MASTERY);
        simulator.applyFieldBuff(new SimpleBuff(
                "Nahida Compassion Illuminated",
                BuffId.NAHIDA_A1_ACTIVE_ELEMENTAL_MASTERY,
                shrineExpirationTime - currentTime,
                currentTime,
                stats -> stats.add(
                        StatType.ELEMENTAL_MASTERY,
                        a1ElementalMastery))
                .sourcedBy(characterId));
    }

    private void handleC6Trigger(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator simulator) {
        if (constellation < 6
                || actor != this
                || action == null
                || (action.getActionType() != ActionType.NORMAL
                        && action.getActionType() != ActionType.CHARGE)
                || damage <= 0.0
                || !isSeedActive(time)
                || time + EPSILON >= c6ExpirationTime
                || c6TriggerCount >= (int) getTalentValue(
                        "C6 Trigger Count", 6.0)
                || time + EPSILON < nextC6AllowedTime) {
            return;
        }
        nextC6AllowedTime = time
                + getTalentValue("C6 Trigger Interval", 0.2);
        c6TriggerCount++;
        queueHit(simulator, new PendingHit(
                time + FRAME,
                HitKind.C6_KARMIC_OBLIVION,
                c6TriggerCount,
                captureLiveStats(time)));
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Akara N" + (hit.index + 1),
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
                        simulator,
                        hit,
                        "Akara Charged Attack",
                        getTalentValue("Charged Attack", 2.244),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Akara High Plunge",
                        getTalentValue("High Plunge", 2.607632),
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        1.0);
                break;
            case SKILL_PRESS:
                resolveSkillPress(simulator, hit);
                break;
            case TRI_KARMA:
                resolveTriKarma(simulator, hit, false);
                break;
            case C6_KARMIC_OBLIVION:
                resolveTriKarma(simulator, hit, true);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Nahida hit kind " + hit.kind);
        }
    }

    private void resolveSkillPress(
            CombatSimulator simulator,
            PendingHit hit) {
        String key = constellation >= 3
                ? "All Schemes to Know Press C3"
                : "All Schemes to Know Press";
        double multiplier = constellation >= 3 ? 1.968 : 1.6728;
        performHit(
                simulator,
                hit,
                "All Schemes to Know Press",
                getTalentValue(key, multiplier),
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0);
        seedExpirationTime = simulator.getCurrentTime()
                + getTalentValue("Seed Duration", 25.0);
    }

    private void resolveTriKarma(
            CombatSimulator simulator,
            PendingHit hit,
            boolean c6) {
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot.merge(null);
        double atkRatio;
        double emRatio;
        String displayName;
        ICDType icdType;
        ICDTag icdTag;
        if (c6) {
            atkRatio = getTalentValue("C6 ATK Ratio", 2.0);
            emRatio = getTalentValue("C6 EM Ratio", 4.0);
            displayName = "Tri-Karma Purification: Karmic Oblivion";
            icdType = ICDType.Standard;
            icdTag = ICDTag.Nahida_C6;
        } else {
            String suffix = constellation >= 3 ? " C3" : "";
            atkRatio = getTalentValue(
                    "Tri-Karma ATK" + suffix,
                    constellation >= 3 ? 2.064 : 1.7544);
            emRatio = getTalentValue(
                    "Tri-Karma EM" + suffix,
                    constellation >= 3 ? 4.128 : 3.5088);
            displayName = "Tri-Karma Purification";
            icdType = ICDType.NahidaTriKarma;
            icdTag = ICDTag.Nahida_TriKarma;
        }
        snapshot.add(
                StatType.FLAT_DMG_BONUS,
                snapshot.get(StatType.ELEMENTAL_MASTERY) * emRatio);
        applyA4(snapshot);
        PendingHit resolved = new PendingHit(
                hit.time, hit.kind, hit.index, snapshot);
        performHit(
                simulator,
                resolved,
                displayName,
                atkRatio,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                icdType,
                icdTag,
                1.0);
        if (!c6
                && hit.time + EPSILON >= nextParticleAllowedTime) {
            nextParticleAllowedTime = hit.time
                    + getTalentValue("Particle ICD", 7.0);
            queueCommand(simulator, new PendingCommand(
                    hit.time + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE,
                    0L,
                    getTalentValue("Particle Count", 3.0)));
        }
    }

    private void applyA4(StatsContainer snapshot) {
        double excess = Math.max(
                0.0,
                snapshot.get(StatType.ELEMENTAL_MASTERY)
                        - getTalentValue("A4 EM Threshold", 200.0));
        double damageBonus = Math.min(
                getTalentValue("A4 DMG Cap", 0.8),
                excess * getTalentValue("A4 DMG Per EM", 0.001));
        double critRate = Math.min(
                getTalentValue("A4 CRIT Cap", 0.24),
                excess * getTalentValue("A4 CRIT Per EM", 0.0003));
        snapshot.add(StatType.SKILL_DMG_BONUS, damageBonus);
        snapshot.add(StatType.SKILL_CRIT_RATE, critRate);
    }

    private double triKarmaInterval(double currentTime) {
        double interval = getTalentValue(
                "Tri-Karma Base Interval", 2.5);
        if (isShrineEffectActive(currentTime)
                && shrineElectroCount > 0) {
            interval -= electroReduction();
        }
        return interval;
    }

    private double pyroBonus() {
        if (shrinePyroCount == 0) {
            return 0.0;
        }
        String suffix = constellation >= 5 ? " C5" : "";
        String key = "Pyro Bonus " + shrinePyroCount + suffix;
        if (shrinePyroCount == 1) {
            return getTalentValue(
                    key, constellation >= 5 ? 0.2976 : 0.25296);
        }
        return getTalentValue(
                key, constellation >= 5 ? 0.4464 : 0.37944);
    }

    private double electroReduction() {
        String suffix = constellation >= 5 ? " C5" : "";
        String key = "Electro Reduction "
                + shrineElectroCount + suffix;
        if (shrineElectroCount == 1) {
            return getTalentValue(
                    key, constellation >= 5 ? 0.496 : 0.4216);
        }
        return getTalentValue(
                key, constellation >= 5 ? 0.744 : 0.6324);
    }

    private double hydroExtension() {
        if (shrineHydroCount == 0) {
            return 0.0;
        }
        String suffix = constellation >= 5 ? " C5" : "";
        String key = "Hydro Extension "
                + shrineHydroCount + suffix;
        if (shrineHydroCount == 1) {
            return getTalentValue(
                    key, constellation >= 5 ? 6.688 : 5.6848);
        }
        return getTalentValue(
                key, constellation >= 5 ? 10.032 : 8.5272);
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
            double gaugeUnits) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                Element.DENDRO,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        if (hit.snapshot != null) {
            action.setStatSnapshot(hit.snapshot);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
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
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case BURST_START:
                    startShrine(
                            activeSimulator,
                            command.generation,
                            command.value);
                    break;
                case BURST_FIELD_EFFECT:
                    applyA1Field(activeSimulator, command.generation);
                    break;
                case BURST_EXPIRE:
                    if (command.generation == burstGeneration) {
                        shrineStartTime = Double.NEGATIVE_INFINITY;
                        shrineEffectStartTime = Double.NEGATIVE_INFINITY;
                        shrineExpirationTime = Double.NEGATIVE_INFINITY;
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.DENDRO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Nahida command kind " + command.kind);
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
        SKILL_PRESS,
        TRI_KARMA,
        C6_KARMIC_OBLIVION
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        BURST_START,
        BURST_FIELD_EFFECT,
        BURST_EXPIRE,
        PARTICLE
    }

    /** Immutable delayed Nahida hit with optional trigger-time stats. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, snapshot);
        }
    }

    /** Immutable delayed state command. */
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

    /** Immutable snapshot of all mutable Nahida-owned simulator state. */
    private static final class NahidaState implements State {
        private final Nahida owner;
        private final int normalAttackStep;
        private final double seedExpirationTime;
        private final double nextTriKarmaAllowedTime;
        private final double nextParticleAllowedTime;
        private final long burstGeneration;
        private final double shrineStartTime;
        private final double shrineEffectStartTime;
        private final double shrineExpirationTime;
        private final int shrinePyroCount;
        private final int shrineElectroCount;
        private final int shrineHydroCount;
        private final double a1ElementalMastery;
        private final double c6ExpirationTime;
        private final double nextC6AllowedTime;
        private final int c6TriggerCount;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private NahidaState(
                Nahida owner,
                int normalAttackStep,
                double seedExpirationTime,
                double nextTriKarmaAllowedTime,
                double nextParticleAllowedTime,
                long burstGeneration,
                double shrineStartTime,
                double shrineEffectStartTime,
                double shrineExpirationTime,
                int shrinePyroCount,
                int shrineElectroCount,
                int shrineHydroCount,
                double a1ElementalMastery,
                double c6ExpirationTime,
                double nextC6AllowedTime,
                int c6TriggerCount,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.seedExpirationTime = seedExpirationTime;
            this.nextTriKarmaAllowedTime = nextTriKarmaAllowedTime;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.burstGeneration = burstGeneration;
            this.shrineStartTime = shrineStartTime;
            this.shrineEffectStartTime = shrineEffectStartTime;
            this.shrineExpirationTime = shrineExpirationTime;
            this.shrinePyroCount = shrinePyroCount;
            this.shrineElectroCount = shrineElectroCount;
            this.shrineHydroCount = shrineHydroCount;
            this.a1ElementalMastery = a1ElementalMastery;
            this.c6ExpirationTime = c6ExpirationTime;
            this.nextC6AllowedTime = nextC6AllowedTime;
            this.c6TriggerCount = c6TriggerCount;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
