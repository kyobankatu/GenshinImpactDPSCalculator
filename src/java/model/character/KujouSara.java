package model.character;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Kujou Sara's stationary Crowfeather support slice through C6.
 *
 * <p>Lv. 90 stats, Talent 9/12 values, frame timings, particles, application,
 * and constellation rules follow pinned gcsim {@code ef41805d} and KQM TCL
 * {@code 80ba6241}. Skill snapshots the future Ambush, while Burst snapshots
 * at frame 47. Tengu Juurai grants one six-second recipient-fixed ATK buff;
 * C6 is resolved live per hit so deployable snapshots cannot retain it.</p>
 *
 * <p>Physical aimed shots, weak-point activation, aiming, movement, taunt
 * behavior, multi-target geometry, and exact Stormcluster branch travel are
 * outside this slice. One representative Stormcluster hit is resolved for the
 * stationary target, so C4 does not manufacture geometry-dependent damage.</p>
 */
public final class KujouSara extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[] NORMAL_RELEASES = { 14, 13, 19, 19, 32 };
    private static final int[] NORMAL_DURATIONS = { 25, 28, 37, 38, 45 };
    private static final double[] NORMAL_MULTIPLIERS = {
        0.67782, 0.711, 0.89112, 0.92588, 1.0665
    };
    private static final double COVER_DURATION = 18.0;
    private static final double TENGU_BUFF_DURATION = 6.0;
    private static final double A4_COOLDOWN = 3.0;
    private static final double C1_COOLDOWN = 3.0;
    /**
     * Full-aim weak-point hitlag from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile AIMED_HEADSHOT_HITLAG =
            new HitlagProfile(0.12, 0.01, false, true, true);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private long latestBuffSerial;
    private double coverExpirationTime = Double.NEGATIVE_INFINITY;
    private StatsContainer coverSnapshot;
    private double coverBaseAttack;
    private double nextA4Time = Double.NEGATIVE_INFINITY;
    private double nextC1Time = Double.NEGATIVE_INFINITY;
    private final Map<CharacterId, Long> recipientBuffSerials =
            new EnumMap<>(CharacterId.class);
    private final Map<CharacterId, Double> recipientBuffExpirations =
            new EnumMap<>(CharacterId.class);
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Kujou Sara. */
    public KujouSara(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Kujou Sara at an explicit constellation. */
    public KujouSara(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Kujou Sara with injectable talent data and constellation. */
    public KujouSara(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Kujou Sara constellation must be between 0 and 6");
        }
        name = "Kujou Sara";
        characterId = CharacterId.KUJOU_SARA;
        element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9570.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 195.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 628.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK", 0.24));
        setSkillCD(10.0);
        setBurstCD(20.0);
    }

    /** Binds Sara's owned events and live C6 resolver to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Kujou Sara simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Kujou Sara cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures cover, gates, recipient ownership, and future events. */
    @Override
    public State captureCharacterState() {
        return new KujouSaraState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                latestBuffSerial,
                coverExpirationTime,
                coverSnapshot,
                coverBaseAttack,
                nextA4Time,
                nextC1Time,
                recipientBuffSerials,
                recipientBuffExpirations,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Sara instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof KujouSaraState
                && ((KujouSaraState) state).owner == this;
    }

    /** Restores surviving Sara-owned events exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Kujou Sara character state");
        }
        initializeForSimulator(simulator);
        KujouSaraState restored = (KujouSaraState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        latestBuffSerial = restored.latestBuffSerial;
        coverExpirationTime = restored.coverExpirationTime;
        coverSnapshot = copyStats(restored.coverSnapshot);
        coverBaseAttack = restored.coverBaseAttack;
        nextA4Time = restored.nextA4Time;
        nextC1Time = restored.nextC1Time;
        recipientBuffSerials.clear();
        recipientBuffSerials.putAll(restored.recipientBuffSerials);
        recipientBuffExpirations.clear();
        recipientBuffExpirations.putAll(restored.recipientBuffExpirations);
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

    /** Returns Sara's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Sara's represented ascension stat is loaded structurally. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Exploration and unrepresented geometry passives do not add stats.
    }

    /** Resets Sara's Normal string while preserving Crowfeather Cover. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether Crowfeather Cover is live at the supplied time. */
    public boolean hasCrowfeatherCover(double currentTime) {
        return coverSnapshot != null
                && currentTime < coverExpirationTime;
    }

    /** Returns one recipient's live Tengu Juurai expiration. */
    public double getTenguBuffExpiration(CharacterId recipientId) {
        return recipientBuffExpirations.getOrDefault(
                recipientId, Double.NEGATIVE_INFINITY);
    }

    /** Applies C6 after snapshot resolution to the actual buff recipient. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            model.entity.Enemy target,
            AttackAction action,
            double currentTime) {
        if (constellation < 6
                || initializedSimulator == null
                || attacker == null
                || !initializedSimulator.getPartyMembers().contains(attacker)
                || target == null
                || action == null
                || currentTime >= getTenguBuffExpiration(
                        attacker.getCharacterId())) {
            return;
        }
        double bonus = getTalentValue("C6 Electro CRIT DMG", 0.60);
        if (isLunarCharged(action)) {
            stats.add(StatType.LUNAR_REACTION_CRIT_DMG, bonus);
        } else if (!action.isLunarConsidered()
                && action.getElement() == Element.ELECTRO) {
            stats.add(StatType.ELECTRO_CRIT_DMG, bonus);
        }
    }

    private static boolean isLunarCharged(AttackAction action) {
        if (action.getLunarReactionType()
                == AttackAction.LunarReactionType.CHARGED) {
            return true;
        }
        // Legacy Flins actions predate typed Lunar subtypes but are all
        // Electro Lunar-Charged direct hits.
        return action.getLunarReactionType() == null
                && action.isLunarConsidered()
                && action.getElement() == Element.ELECTRO;
    }

    /** Dispatches Normal, fully charged, Press Skill, and Burst requests. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Kujou Sara action is required");
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
                fullyChargedShot(simulator);
                break;
            case SKILL:
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Kujou Sara Hold Skill is unsupported");
                }
                tenguStormcall(simulator);
                break;
            case BURST:
                subjugationKoukouSendou(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Kujou Sara: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + (NORMAL_RELEASES[step] + 10.0) * FRAME,
                HitKind.NORMAL,
                step,
                0L,
                captureActionStats(simulator, castTime),
                0.0));
        normalAttackStep = (normalAttackStep + 1) % 5;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void fullyChargedShot(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean covered = hasCrowfeatherCover(castTime);
        double hitTime = castTime + (covered ? 60.0 : 96.0) * FRAME;
        queueHit(simulator, new PendingHit(
                hitTime,
                HitKind.CHARGED,
                0,
                0L,
                captureActionStats(simulator, castTime),
                0.0));
        if (covered) {
            StatsContainer ambushSnapshot = copyStats(coverSnapshot);
            double baseAttack = coverBaseAttack;
            coverSnapshot = null;
            coverExpirationTime = Double.NEGATIVE_INFINITY;
            queueCommand(simulator, new PendingCommand(
                    hitTime + 89.0 * FRAME,
                    CommandKind.AMBUSH_BUFF,
                    0L,
                    baseAttack));
            queueHit(simulator, new PendingHit(
                    hitTime + 90.0 * FRAME,
                    HitKind.AMBUSH,
                    0,
                    0L,
                    ambushSnapshot,
                    baseAttack));
        }
        simulator.advanceTime(hitTime - castTime);
    }

    private void tenguStormcall(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        StatsContainer snapshot = captureActionStats(simulator, castTime);
        coverSnapshot = copyStats(snapshot);
        coverBaseAttack = getBaseAttack();
        coverExpirationTime = castTime + COVER_DURATION;
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation));
        if (constellation >= 2) {
            queueCommand(simulator, new PendingCommand(
                    castTime + 102.0 * FRAME,
                    CommandKind.AMBUSH_BUFF,
                    0L,
                    getBaseAttack()));
            queueHit(simulator, new PendingHit(
                    castTime + 103.0 * FRAME,
                    HitKind.C2_AMBUSH,
                    0,
                    0L,
                    snapshot,
                    getBaseAttack()));
        }
        simulator.advanceTime(52.0 * FRAME);
    }

    private void subjugationKoukouSendou(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        queueCommand(simulator, new PendingCommand(
                castTime + 47.0 * FRAME,
                CommandKind.BURST_SNAPSHOT,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + 50.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation));
        simulator.advanceTime(80.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        if ((hit.kind == HitKind.BURST_INITIAL
                || hit.kind == HitKind.STORMCLUSTER)
                && hit.generation != burstGeneration) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                resolveCharged(simulator, hit);
                break;
            case AMBUSH:
                resolveAmbush(simulator, hit, false);
                break;
            case C2_AMBUSH:
                resolveAmbush(simulator, hit, true);
                break;
            case BURST_INITIAL:
                resolveBurstHit(simulator, hit, false);
                break;
            case STORMCLUSTER:
                resolveBurstHit(simulator, hit, true);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Kujou Sara hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        int step = hit.index;
        AttackAction normal = attack(
                "Tengu Bowmanship N" + (step + 1),
                getTalentValue("N" + (step + 1),
                        NORMAL_MULTIPLIERS[step]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.None,
                ICDTag.None,
                0.0);
        normal.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, normal);
    }

    private void resolveCharged(CombatSimulator simulator, PendingHit hit) {
        AttackAction charged = attack(
                "Tengu Bowmanship Fully Charged",
                getTalentValue("Fully Charged", 2.108),
                Element.ELECTRO,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.None,
                ICDTag.ChargedAttack,
                1.0);
        charged.setStatSnapshot(hit.snapshot);
        charged.setHitlagProfile(AIMED_HEADSHOT_HITLAG);
        simulator.performActionWithoutTimeAdvance(characterId, charged);
    }

    private void resolveAmbush(
            CombatSimulator simulator,
            PendingHit hit,
            boolean c2) {
        double multiplier = getTalentValue(
                constellation >= 5 ? "Ambush C5" : "Ambush",
                constellation >= 5 ? 2.5152 : 2.13792);
        if (c2) {
            multiplier *= getTalentValue("C2 Ambush Ratio", 0.30);
        }
        AttackAction ambush = attack(
                c2 ? "Tengu Juurai: Ambush C2" : "Tengu Juurai: Ambush",
                multiplier,
                Element.ELECTRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0);
        ambush.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, ambush);
        if (simulator.getEnemy() != null) {
            triggerA4(simulator);
            if (!c2) {
                queueCommand(simulator, new PendingCommand(
                        simulator.getCurrentTime() + PARTICLE_TRAVEL,
                        CommandKind.PARTICLE,
                        0L));
            }
        }
    }

    private void resolveBurstHit(
            CombatSimulator simulator,
            PendingHit hit,
            boolean cluster) {
        String key;
        double fallback;
        if (cluster) {
            key = constellation >= 3
                    ? "Stormcluster C3" : "Stormcluster";
            fallback = constellation >= 3 ? 0.6824 : 0.58004;
        } else {
            key = constellation >= 3
                    ? "Titanbreaker C3" : "Titanbreaker";
            fallback = constellation >= 3 ? 8.192 : 6.9632;
        }
        AttackAction burst = attack(
                cluster
                        ? "Subjugation: Koukou Sendou Stormcluster"
                        : "Subjugation: Koukou Sendou Titanbreaker",
                getTalentValue(key, fallback),
                Element.ELECTRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                cluster ? ICDType.Standard : ICDType.None,
                cluster ? ICDTag.ElementalBurst : ICDTag.None,
                1.0);
        burst.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, burst);
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime() + FRAME,
                CommandKind.TENGU_BUFF,
                hit.generation,
                hit.baseAttack));
    }

    private void applyTenguBuff(
            CombatSimulator simulator,
            double baseAttack) {
        double currentTime = simulator.getCurrentTime();
        Character recipient = simulator.getActiveCharacter();
        if (recipient == null) {
            return;
        }
        CharacterId recipientId = recipient.getCharacterId();
        long serial = ++latestBuffSerial;
        recipientBuffSerials.put(recipientId, serial);
        recipientBuffExpirations.put(
                recipientId, currentTime + TENGU_BUFF_DURATION);
        double ratio = getTalentValue(
                constellation >= 5
                        ? "ATK Bonus Ratio C5" : "ATK Bonus Ratio",
                constellation >= 5 ? 0.8592 : 0.73032);
        double attackBonus = baseAttack * ratio;
        SimpleBuff buff = new SimpleBuff(
                "Kujou Sara Tengu Juurai",
                BuffId.KUJOU_SARA_TENGU_JUURAI,
                TENGU_BUFF_DURATION,
                currentTime,
                stats -> {
                    Long current = recipientBuffSerials.get(recipientId);
                    if (current != null && current.longValue() == serial) {
                        stats.add(StatType.ATK_FLAT, attackBonus);
                    }
                });
        List<CharacterId> excluded = new ArrayList<>();
        for (Character member : simulator.getPartyMembers()) {
            if (member.getCharacterId() != recipientId) {
                excluded.add(member.getCharacterId());
            }
        }
        buff.exclude(excluded.toArray(new CharacterId[0]));
        simulator.applyTeamBuff(buff.sourcedBy(characterId));
        triggerC1(currentTime);
    }

    private void triggerA4(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        if (currentTime + EPSILON < nextA4Time) {
            return;
        }
        double structuralEr = getStructuralStats(currentTime).get(
                StatType.ENERGY_RECHARGE);
        double amount = getTalentValue("A4 Energy Per ER", 1.2)
                * structuralEr;
        simulator.getEnergyDistributor().distributeFlatEnergy(amount);
        nextA4Time = currentTime + A4_COOLDOWN;
    }

    private void triggerC1(double currentTime) {
        if (constellation < 1
                || currentTime + EPSILON < nextC1Time) {
            return;
        }
        reduceSkillCooldown(
                currentTime,
                getTalentValue("C1 Cooldown Reduction", 1.0));
        nextC1Time = currentTime + C1_COOLDOWN;
    }

    private double getBaseAttack() {
        double amount = baseStats.get(StatType.BASE_ATK);
        if (weapon != null && weapon.getStats() != null) {
            amount += weapon.getStats().get(StatType.BASE_ATK);
        }
        return amount;
    }

    private StatsContainer captureActionStats(
            CombatSimulator simulator,
            double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
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
                            Element.ELECTRO,
                            3.0,
                            ParticleType.PARTICLE);
                    break;
                case BURST_SNAPSHOT:
                    if (command.generation == burstGeneration) {
                        resolveBurstSnapshot(
                                activeSimulator, command.generation);
                    }
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case TENGU_BUFF:
                    if (command.generation == burstGeneration) {
                        applyTenguBuff(activeSimulator, command.value);
                    }
                    break;
                case AMBUSH_BUFF:
                    applyTenguBuff(activeSimulator, command.value);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Kujou Sara command kind");
            }
        });
    }

    private void resolveBurstSnapshot(
            CombatSimulator simulator,
            long generation) {
        double snapshotTime = simulator.getCurrentTime();
        markBurstCooldownUsed(
                snapshotTime, simulator.getApplicableBuffs(this));
        StatsContainer snapshot = captureActionStats(
                simulator, snapshotTime);
        double baseAttack = getBaseAttack();
        double castTime = snapshotTime - 47.0 * FRAME;
        queueHit(simulator, new PendingHit(
                castTime + 51.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                generation,
                snapshot,
                baseAttack));
        queueHit(simulator, new PendingHit(
                castTime + 100.0 * FRAME,
                HitKind.STORMCLUSTER,
                0,
                generation,
                snapshot,
                baseAttack));
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

    private static StatsContainer copyStats(StatsContainer stats) {
        return stats == null ? null : stats.merge(null);
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
        AMBUSH,
        C2_AMBUSH,
        BURST_INITIAL,
        STORMCLUSTER
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        PARTICLE,
        BURST_SNAPSHOT,
        BURST_ENERGY,
        TENGU_BUFF,
        AMBUSH_BUFF
    }

    /** Immutable delayed Sara hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final long generation;
        private final StatsContainer snapshot;
        private final double baseAttack;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                long generation,
                StatsContainer snapshot,
                double baseAttack) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.snapshot = copyStats(snapshot);
            this.baseAttack = baseAttack;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    generation,
                    snapshot,
                    baseAttack);
        }
    }

    /** Immutable delayed Sara command description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final double value;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation) {
            this(time, kind, generation, 0.0);
        }

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

    /** Immutable Sara-owned simulator snapshot payload. */
    private static final class KujouSaraState implements State {
        private final KujouSara owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final long latestBuffSerial;
        private final double coverExpirationTime;
        private final StatsContainer coverSnapshot;
        private final double coverBaseAttack;
        private final double nextA4Time;
        private final double nextC1Time;
        private final Map<CharacterId, Long> recipientBuffSerials;
        private final Map<CharacterId, Double> recipientBuffExpirations;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private KujouSaraState(
                KujouSara owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                long latestBuffSerial,
                double coverExpirationTime,
                StatsContainer coverSnapshot,
                double coverBaseAttack,
                double nextA4Time,
                double nextC1Time,
                Map<CharacterId, Long> recipientBuffSerials,
                Map<CharacterId, Double> recipientBuffExpirations,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.latestBuffSerial = latestBuffSerial;
            this.coverExpirationTime = coverExpirationTime;
            this.coverSnapshot = copyStats(coverSnapshot);
            this.coverBaseAttack = coverBaseAttack;
            this.nextA4Time = nextA4Time;
            this.nextC1Time = nextC1Time;
            this.recipientBuffSerials = new EnumMap<>(CharacterId.class);
            this.recipientBuffSerials.putAll(recipientBuffSerials);
            this.recipientBuffExpirations =
                    new EnumMap<>(CharacterId.class);
            this.recipientBuffExpirations.putAll(recipientBuffExpirations);
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
