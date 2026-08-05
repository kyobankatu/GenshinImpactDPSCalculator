package model.character;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import model.entity.TargetDependentTeamEffect;
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
 * Yun Jin's stationary Flying Cloud Normal Attack support slice through C6.
 *
 * <p>Lv. 90 stats, Talent 9/12 values, frame timings, particles, gauges, and
 * support rules follow pinned gcsim {@code ef41805d} and KQM TCL
 * {@code 80ba6241}. Each Formation recipient owns 30 fixed-target Normal hit
 * quotas. The added damage reads Yun Jin's live DEF per hit and is consumed
 * only after that hit resolves successfully.</p>
 *
 * <p>Press and full Charge Level 2 are mapped to typed Press/Hold requests.
 * Skill shields, incoming-hit perfect counters, intermediate Hold, hitlag
 * extension, multi-target quota use, and geometry are intentionally excluded.</p>
 */
public final class YunJin extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double FORMATION_DURATION = 12.0;
    private static final int FORMATION_QUOTA = 30;
    private static final int[][] NORMAL_HITMARKS = {
        { 15 }, { 13 }, { 8, 23 }, { 11, 23 }, { 15 }
    };
    private static final int[] NORMAL_DURATIONS = { 20, 22, 31, 45, 67 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3-1", "N3-2" },
        { "N4-1", "N4-2" }, { "N5" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.74418 }, { 0.73944 }, { 0.42186, 0.5056 },
        { 0.44082, 0.5293 }, { 1.23714 }
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private double formationExpirationTime = Double.NEGATIVE_INFINITY;
    private final Map<CharacterId, Integer> formationQuotas =
            new EnumMap<>(CharacterId.class);
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Yun Jin. */
    public YunJin(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Yun Jin at an explicit constellation. */
    public YunJin(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Yun Jin with injectable talent data and constellation. */
    public YunJin(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Yun Jin constellation must be between 0 and 6");
        }
        name = "Yun Jin";
        characterId = CharacterId.YUN_JIN;
        element = Element.GEO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10657.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 191.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 734.0));
        baseStats.add(StatType.ENERGY_RECHARGE,
                getTalentValue("Ascension Energy Recharge", 0.2667));
        setSkillCD(constellation >= 1 ? 443.0 * FRAME : 9.0);
        setBurstCD(15.0);
    }

    /** Binds quota consumption and C4 reaction handling once. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Yun Jin simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Yun Jin cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                consumeFormationQuota(actor, action, damage, time));
        simulator.addReactionListener(this);
    }

    /** Captures the Normal chain, quotas, generations, and future events. */
    @Override
    public State captureCharacterState() {
        return new YunJinState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                formationExpirationTime,
                formationQuotas,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Yun Jin instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof YunJinState
                && ((YunJinState) state).owner == this;
    }

    /** Restores surviving Yun Jin events and recipient quotas exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Yun Jin character state");
        }
        initializeForSimulator(simulator);
        YunJinState restored = (YunJinState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        formationExpirationTime = restored.formationExpirationTime;
        formationQuotas.clear();
        formationQuotas.putAll(restored.formationQuotas);
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

    /** Returns Yun Jin's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Yun Jin's represented ascension stat is loaded structurally. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A4 depends on party composition and is resolved by Formation.
    }

    /** Supports both Charge Level 0 Press and full Charge Level 2 Hold. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS
                || mode == SkillActionMode.HOLD;
    }

    /** Resets Yun Jin's Normal string without clearing Formation. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns a recipient's remaining Formation quota at the supplied time. */
    public int getFormationQuota(
            CharacterId recipientId,
            double currentTime) {
        if (currentTime >= formationExpirationTime) {
            return 0;
        }
        return formationQuotas.getOrDefault(recipientId, 0);
    }

    /** Returns the half-open Formation expiration timestamp. */
    public double getFormationExpirationTime() {
        return formationExpirationTime;
    }

    /** Adds live DEF-scaled Formation damage before one eligible hit. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            model.entity.Enemy target,
            AttackAction action,
            double currentTime) {
        if (initializedSimulator == null
                || attacker == null
                || !initializedSimulator.getPartyMembers().contains(attacker)
                || target == null
                || action == null
                || action.getActionType() != ActionType.NORMAL
                || getFormationQuota(
                        attacker.getCharacterId(), currentTime) <= 0) {
            return;
        }
        double liveDef = captureLiveStats(currentTime).getTotalDef();
        stats.add(StatType.FLAT_DMG_BONUS,
                liveDef * getFormationDefRatio());
    }

    /** Applies C4 only for Yun Jin-triggered standard or Lunar Crystallize. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || constellation < 4
                || source != this
                || result == null
                || (result.getKind() != ReactionResult.Kind.CRYSTALLIZE
                        && result.getKind()
                                != ReactionResult.Kind.LUNAR_CRYSTALLIZE)) {
            return;
        }
        removeBuff(BuffId.YUN_JIN_C4_DEF);
        addBuff(new SimpleBuff(
                "Yun Jin Flower and a Fighter",
                BuffId.YUN_JIN_C4_DEF,
                12.0,
                time,
                stats -> stats.add(
                        StatType.DEF_PERCENT,
                        getTalentValue("C4 DEF Bonus", 0.20))));
    }

    /** Dispatches basic attacks, Press/full-Hold Skill, and Burst. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Yun Jin action is required");
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
                openingFlourish(simulator, request.getSkillMode());
                break;
            case BURST:
                cliffbreakersBanner(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Yun Jin: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        double speedScale = 1.0 + normalAttackSpeed(castTime);
        for (int hit = 0; hit < NORMAL_HITMARKS[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HITMARKS[step][hit]
                            * FRAME / speedScale,
                    HitKind.NORMAL,
                    step,
                    hit,
                    0L));
        }
        normalAttackStep = (normalAttackStep + 1) % 5;
        simulator.advanceTime(
                NORMAL_DURATIONS[step] * FRAME / speedScale);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 25.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                0L));
        simulator.advanceTime(59.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 43.0 * FRAME,
                HitKind.PLUNGE,
                0,
                0,
                0L));
        simulator.advanceTime(80.0 * FRAME);
    }

    private void openingFlourish(
            CombatSimulator simulator,
            SkillActionMode mode) {
        if (mode != SkillActionMode.PRESS
                && mode != SkillActionMode.HOLD) {
            throw new IllegalArgumentException(
                    "Unsupported Yun Jin Skill mode: " + mode);
        }
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        boolean hold = mode == SkillActionMode.HOLD;
        int hitFrame = hold ? 93 : 13;
        int cooldownFrame = hold ? 90 : 11;
        queueCommand(simulator, new PendingCommand(
                castTime + cooldownFrame * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + hitFrame * FRAME,
                HitKind.SKILL,
                hold ? 2 : 0,
                0,
                generation));
        simulator.advanceTime((hold ? 141.0 : 62.0) * FRAME);
    }

    private void cliffbreakersBanner(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        clearFormation(simulator);
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 35.0 * FRAME,
                CommandKind.BURST_ACTIVATION,
                generation,
                0.0));
        simulator.advanceTime(57.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        if (hit.kind == HitKind.SKILL
                && hit.generation != skillGeneration) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                resolveCharged(simulator, hit);
                break;
            case PLUNGE:
                resolvePlunge(simulator, hit);
                break;
            case SKILL:
                resolveSkill(simulator, hit);
                break;
            default:
                throw new IllegalStateException("Unknown Yun Jin hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        String key = NORMAL_KEYS[hit.index][hit.subIndex];
        AttackAction normal = attack(
                "Cloud-Grazing Strike " + key,
                getTalentValue(
                        key, NORMAL_MULTIPLIERS[hit.index][hit.subIndex]),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0,
                false);
        simulator.performActionWithoutTimeAdvance(characterId, normal);
    }

    private void resolveCharged(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction charged = attack(
                "Cloud-Grazing Strike Charged",
                getTalentValue("Charged", 2.2357),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.ChargedAttack,
                0.0,
                false);
        simulator.performActionWithoutTimeAdvance(characterId, charged);
    }

    private void resolvePlunge(CombatSimulator simulator, PendingHit hit) {
        AttackAction plunge = attack(
                "Cloud-Grazing Strike High Plunge",
                getTalentValue("High Plunge", 2.933586),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                0.0,
                true);
        simulator.performActionWithoutTimeAdvance(characterId, plunge);
    }

    private void resolveSkill(CombatSimulator simulator, PendingHit hit) {
        boolean hold = hit.index == 2;
        String key;
        double fallback;
        if (hold) {
            key = constellation >= 5
                    ? "Hold Level 2 C5" : "Hold Level 2";
            fallback = constellation >= 5 ? 7.456 : 6.3376;
        } else {
            key = constellation >= 5 ? "Press C5" : "Press";
            fallback = constellation >= 5 ? 2.9824 : 2.53504;
        }
        AttackAction skill = attack(
                hold
                        ? "Opening Flourish Hold Level 2"
                        : "Opening Flourish Press",
                getTalentValue(key, fallback),
                Element.GEO,
                StatType.BASE_DEF,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                hold ? 4.0 : 2.0,
                true);
        simulator.performActionWithoutTimeAdvance(characterId, skill);
        if (simulator.getEnemy() != null) {
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime() + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    hit.generation,
                    hold ? 3.0 : 2.0));
        }
    }

    private void resolveBurstActivation(CombatSimulator simulator) {
        AttackAction burst = attack(
                "Cliffbreaker's Banner",
                getTalentValue(
                        constellation >= 3
                                ? "Burst DMG C3" : "Burst DMG",
                        constellation >= 3 ? 4.88 : 4.148),
                Element.GEO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                2.0,
                true);
        simulator.performActionWithoutTimeAdvance(characterId, burst);
        startFormation(simulator);
    }

    private void startFormation(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        formationExpirationTime = currentTime + FORMATION_DURATION;
        formationQuotas.clear();
        for (Character member : simulator.getPartyMembers()) {
            formationQuotas.put(member.getCharacterId(), FORMATION_QUOTA);
        }
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Yun Jin Flying Cloud Flag Formation",
                BuffId.YUN_JIN_FLYING_CLOUD_FORMATION,
                FORMATION_DURATION,
                currentTime,
                stats -> {
                    // Quota damage is applied per hit by the target hook.
                }).sourcedBy(characterId));
        if (constellation >= 2) {
            simulator.applyTeamBuffNoStack(new SimpleBuff(
                    "Yun Jin Myriad Mise-En-Scene",
                    BuffId.YUN_JIN_C2_NORMAL_DMG,
                    FORMATION_DURATION,
                    currentTime,
                    stats -> stats.add(
                            StatType.NORMAL_ATTACK_DMG_BONUS,
                            getTalentValue(
                                    "C2 Normal DMG Bonus", 0.15)))
                    .sourcedBy(characterId));
        }
        if (constellation >= 6) {
            applyRecipientSpeedBuffs(simulator, currentTime);
        }
    }

    private void applyRecipientSpeedBuffs(
            CombatSimulator simulator,
            double currentTime) {
        for (Character recipient : simulator.getPartyMembers()) {
            CharacterId recipientId = recipient.getCharacterId();
            List<CharacterId> excluded = new ArrayList<>();
            for (Character member : simulator.getPartyMembers()) {
                if (member != recipient) {
                    excluded.add(member.getCharacterId());
                }
            }
            Buff speedBuff = new SimpleBuff(
                    "Yun Jin Decorous Harmony ("
                            + recipient.getName() + ")",
                    BuffId.YUN_JIN_C6_NORMAL_SPEED,
                    FORMATION_DURATION,
                    currentTime,
                    stats -> {
                        if (getFormationQuota(
                                recipientId,
                                initializedSimulator.getCurrentTime()) > 0) {
                            stats.add(
                                    StatType.NORMAL_ATTACK_SPD,
                                    getTalentValue(
                                            "C6 Normal ATK SPD", 0.12));
                        }
                    });
            speedBuff.exclude(excluded.toArray(new CharacterId[0]));
            simulator.applyTeamBuff(speedBuff.sourcedBy(characterId));
        }
    }

    private void clearFormation(CombatSimulator simulator) {
        formationExpirationTime = Double.NEGATIVE_INFINITY;
        formationQuotas.clear();
        simulator.removeTeamBuffsById(
                BuffId.YUN_JIN_FLYING_CLOUD_FORMATION);
        simulator.removeTeamBuffsById(BuffId.YUN_JIN_C2_NORMAL_DMG);
        simulator.removeTeamBuffsById(BuffId.YUN_JIN_C6_NORMAL_SPEED);
    }

    private void consumeFormationQuota(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (actor == null
                || action == null
                || action.getActionType() != ActionType.NORMAL
                || damage <= 0.0
                || getFormationQuota(actor.getCharacterId(), time) <= 0) {
            return;
        }
        formationQuotas.computeIfPresent(
                actor.getCharacterId(),
                (id, remaining) -> Math.max(0, remaining - 1));
    }

    private double getFormationDefRatio() {
        double talentRatio = getTalentValue(
                constellation >= 3
                        ? "Formation DEF Ratio C3"
                        : "Formation DEF Ratio",
                constellation >= 3 ? 0.6432 : 0.54672);
        int elementCount = countPartyElements();
        String key;
        double fallback;
        if (elementCount >= 4) {
            key = "A4 Four Elements";
            fallback = 0.115;
        } else if (elementCount == 3) {
            key = "A4 Three Elements";
            fallback = 0.075;
        } else if (elementCount == 2) {
            key = "A4 Two Elements";
            fallback = 0.05;
        } else {
            key = "A4 One Element";
            fallback = 0.025;
        }
        return talentRatio + getTalentValue(key, fallback);
    }

    private int countPartyElements() {
        Set<Element> elements = new HashSet<>();
        if (initializedSimulator != null) {
            for (Character member
                    : initializedSimulator.getPartyMembers()) {
                elements.add(member.getElement());
            }
        }
        return Math.max(1, elements.size());
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private double normalAttackSpeed(double currentTime) {
        StatsContainer stats = captureLiveStats(currentTime);
        double speed = stats.get(StatType.ATK_SPD)
                + stats.get(StatType.NORMAL_ATTACK_SPD);
        return Math.min(0.60, Math.max(0.0, speed));
    }

    private static AttackAction attack(
            String displayName,
            double multiplier,
            Element element,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            boolean blunt) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                element,
                scalingStat,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setShatterTrigger(blunt);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        return action;
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
                    if (command.generation == skillGeneration) {
                        markSkillUsed(
                                activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.GEO,
                            command.value,
                            ParticleType.PARTICLE);
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case BURST_ACTIVATION:
                    if (command.generation == burstGeneration) {
                        resolveBurstActivation(activeSimulator);
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Yun Jin command kind");
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
        SKILL
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        PARTICLE,
        BURST_ENERGY,
        BURST_ACTIVATION
    }

    /** Immutable delayed Yun Jin hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final long generation;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.generation = generation;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    subIndex,
                    generation);
        }
    }

    /** Immutable delayed Yun Jin command description. */
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

    /** Immutable Yun Jin-owned simulator snapshot payload. */
    private static final class YunJinState implements State {
        private final YunJin owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final double formationExpirationTime;
        private final Map<CharacterId, Integer> formationQuotas;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private YunJinState(
                YunJin owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                double formationExpirationTime,
                Map<CharacterId, Integer> formationQuotas,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.formationExpirationTime = formationExpirationTime;
            this.formationQuotas = new EnumMap<>(CharacterId.class);
            this.formationQuotas.putAll(formationQuotas);
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
