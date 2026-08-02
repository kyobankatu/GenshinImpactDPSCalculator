package model.character;

import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.SnapshotAwareCharacterEffect;
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
import simulation.event.SimpleTimerEvent;

/**
 * Qiqi's classic offensive kit for stationary single-target combat.
 *
 * <p>This bounded slice models the five-step sword string, two-hit Charged
 * Attack, high Plunge, snapshotted Herald initial hit and nine maintained
 * gcsim swipes, and one Burst-applied Fortune-Preserving Talisman. C1, C2,
 * C3, and C5 are represented where they affect this offensive policy.</p>
 *
 * <p>Healing, A1, random A4, C4 enemy ATK reduction, C6 revival, and
 * multi-target talismans are excluded. Witch's Revelation, Polestar, and
 * Stellar-Conduct are part of the deferred Hexerei system; this baseline
 * intentionally retains the classic 30-second Skill cooldown and does not
 * partially model their coordinated attacks.</p>
 */
public final class Qiqi extends Character
        implements SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EVENT_EPSILON = 1e-9;
    private static final double SKILL_DURATION = 15.0;
    private static final double SKILL_COOLDOWN = 30.0;
    private static final double BURST_COOLDOWN = 20.0;
    private static final double TALISMAN_DURATION = 15.0;
    private static final int SKILL_HITMARK_FRAME = 32;
    private static final int SKILL_DURATION_FRAMES = 57;
    private static final int BURST_TALISMAN_FRAME = 40;
    private static final int BURST_HITMARK_FRAME = 82;
    private static final int BURST_DURATION_FRAMES = 115;
    private static final double[] HERALD_SWIPE_OFFSETS = {
            1.5, 3.75, 4.75, 7.0, 8.0,
            10.25, 11.25, 13.5, 14.5
    };
    private static final double[] NORMAL_MULTIPLIERS = {
            0.69362, 0.71416, 0.44398, 0.45346, 1.15814
    };
    private static final int[][] NORMAL_HITMARK_FRAMES = {
            { 11 }, { 10 }, { 9, 20 }, { 8, 18 }, { 16 }
    };
    private static final int[] NORMAL_DURATION_FRAMES = {
            21, 22, 33, 28, 53
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long heraldGeneration;
    private double heraldExpirationTime = Double.NEGATIVE_INFINITY;
    private PendingHerald pendingHerald;
    private double talismanExpirationTime = Double.NEGATIVE_INFINITY;

    /** Constructs repository-default C6 Qiqi. */
    public Qiqi(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Qiqi at an explicit constellation. */
    public Qiqi(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Qiqi with injectable talent data and constellation state.
     *
     * @param weapon equipped sword
     * @param artifacts equipped artifact set
     * @param talentData static character-data source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Qiqi(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Qiqi constellation must be between 0 and 6");
        }
        name = "Qiqi";
        characterId = CharacterId.QIQI;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12368.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 287.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 922.0));
        baseStats.add(StatType.HEALING_BONUS,
                getTalentValue("Ascension Healing Bonus", 0.2215));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds Qiqi's mutable event state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Qiqi simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Qiqi cannot be reused across CombatSimulator instances");
        }
        initializedSimulator = simulator;
    }

    /** Captures Normal progression, current Herald work, and talisman expiry. */
    @Override
    public State captureCharacterState() {
        return new QiqiState(
                normalAttackStep,
                heraldGeneration,
                heraldExpirationTime,
                pendingHerald,
                talismanExpirationTime);
    }

    /** Reports whether a snapshot payload belongs to Qiqi. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof QiqiState;
    }

    /**
     * Restores Qiqi-owned state and reconstructs only the next Herald swipe.
     *
     * <p>Deadlines equal to the restored time remain pending because an earlier
     * same-time event can capture a snapshot before the swipe executes.</p>
     *
     * @param state immutable Qiqi payload
     * @param simulator restored simulator receiving future swipes
     */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Qiqi character state");
        }
        QiqiState restored = (QiqiState) state;
        normalAttackStep = restored.normalAttackStep;
        heraldGeneration = restored.heraldGeneration;
        heraldExpirationTime = restored.heraldExpirationTime;
        pendingHerald = restored.pendingHerald == null
                ? null
                : restored.pendingHerald.copy();
        talismanExpirationTime = restored.talismanExpirationTime;
        normalizePendingHerald(simulator.getCurrentTime());
        if (pendingHerald != null) {
            if (pendingHerald.snapshot == null) {
                scheduleHeraldInitialization(simulator, pendingHerald);
            } else {
                scheduleNextHeraldSwipe(simulator, pendingHerald);
            }
        }
    }

    /** Returns Qiqi's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Qiqi has no unconditional offensive stat passive in this slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Every represented constellation is action- or target-state-specific.
    }

    /** Returns whether the classic Herald remains active at this time. */
    public boolean isHeraldActive(double currentTime) {
        return currentTime < heraldExpirationTime;
    }

    /** Returns whether the modeled target currently carries Qiqi's talisman. */
    public boolean isTalismanActive(double currentTime) {
        return currentTime < talismanExpirationTime;
    }

    /** Dispatches Qiqi's supported typed offensive actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
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
                heraldOfFrost(simulator);
                break;
            case BURST:
                preserverOfFortune(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Qiqi: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0;
                hit < NORMAL_HITMARK_FRAMES[step].length;
                hit++) {
            int hitNumber = hit + 1;
            AttackAction action = attack(
                    "Ancient Sword Art N" + (step + 1)
                            + (NORMAL_HITMARK_FRAMES[step].length > 1
                                    ? " Hit " + hitNumber
                                    : ""),
                    getTalentValue(
                            "N" + (step + 1),
                            NORMAL_MULTIPLIERS[step]),
                    Element.PHYSICAL,
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    ActionType.NORMAL,
                    ICDType.Standard,
                    ICDTag.NormalAttack,
                    0.0,
                    false);
            schedule(
                    simulator,
                    castTime + NORMAL_HITMARK_FRAMES[step][hit] * FRAME,
                    activeSim -> resolveNacaImpact(activeSim, action));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        simulator.advanceTime(NORMAL_DURATION_FRAMES[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        for (int hit = 0; hit < 2; hit++) {
            int hitNumber = hit + 1;
            AttackAction action = attack(
                    "Ancient Sword Art Charged Hit " + hitNumber,
                    getTalentValue("Charged Attack", 1.18184),
                    Element.PHYSICAL,
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    ActionType.CHARGE,
                    ICDType.Standard,
                    ICDTag.ChargedAttack,
                    0.0,
                    false);
            double impactTime = castTime
                    + (hit == 0 ? 15.0 : 29.0) * FRAME;
            schedule(
                    simulator,
                    impactTime,
                    activeSim -> resolveNacaImpact(activeSim, action));
        }
        simulator.advanceTime(76.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        AttackAction plunge = attack(
                "Ancient Sword Art High Plunge",
                getTalentValue("High Plunge", 2.933586),
                Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                0.0,
                true);
        schedule(
                simulator,
                castTime + 46.0 * FRAME,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, plunge));
        simulator.advanceTime(77.0 * FRAME);
    }

    private void heraldOfFrost(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++heraldGeneration;
        heraldExpirationTime = castTime + SKILL_DURATION;
        pendingHerald = new PendingHerald(
                generation,
                castTime,
                null,
                0,
                constellation >= 5);

        schedule(simulator, castTime + 3.0 * FRAME, activeSim -> {
            if (generation == heraldGeneration) {
                markSkillUsed(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this));
            }
        });
        scheduleHeraldInitialization(simulator, pendingHerald);
        simulator.advanceTime(SKILL_DURATION_FRAMES * FRAME);
    }

    private void scheduleHeraldInitialization(
            CombatSimulator simulator,
            PendingHerald herald) {
        double initializationTime = herald.castTime
                + SKILL_HITMARK_FRAME * FRAME;
        if (initializationTime < simulator.getCurrentTime()) {
            pendingHerald = null;
            return;
        }
        schedule(simulator, initializationTime, activeSim -> {
            if (pendingHerald != herald) {
                return;
            }
            StatsContainer snapshot = captureActionSnapshot(
                    activeSim, activeSim.getCurrentTime());
            AttackAction initial = attack(
                    "Adeptus Art: Herald of Frost Initial",
                    getTalentValue(
                            herald.c5
                                    ? "Herald Initial C5"
                                    : "Herald Initial",
                            herald.c5 ? 1.920 : 1.632),
                    Element.CRYO,
                    StatType.SKILL_DMG_BONUS,
                    ActionType.SKILL,
                    ICDType.Standard,
                    ICDTag.ElementalSkill,
                    1.0,
                    false);
            initial.setStatSnapshot(snapshot);
            activeSim.performActionWithoutTimeAdvance(
                    characterId, initial);
            pendingHerald = herald.withSnapshot(snapshot);
            scheduleNextHeraldSwipe(activeSim, pendingHerald);
        });
    }

    private void scheduleNextHeraldSwipe(
            CombatSimulator simulator,
            PendingHerald herald) {
        if (herald.nextSwipeIndex >= HERALD_SWIPE_OFFSETS.length) {
            return;
        }
        int swipeIndex = herald.nextSwipeIndex;
        double swipeTime = herald.castTime
                + HERALD_SWIPE_OFFSETS[swipeIndex];
        schedule(simulator, swipeTime, activeSim -> {
            if (pendingHerald != herald) {
                return;
            }
            AttackAction swipe = attack(
                    "Herald of Frost Swipe " + (swipeIndex + 1),
                    getTalentValue(
                            herald.c5
                                    ? "Herald Swipe C5"
                                    : "Herald Swipe",
                            herald.c5 ? 0.720 : 0.612),
                    Element.CRYO,
                    StatType.SKILL_DMG_BONUS,
                    ActionType.SKILL,
                    ICDType.Standard,
                    ICDTag.ElementalSkill,
                    1.0,
                    false);
            swipe.setStatSnapshot(herald.snapshot);
            activeSim.performActionWithoutTimeAdvance(characterId, swipe);
            if (constellation >= 1
                    && isTalismanActive(activeSim.getCurrentTime())) {
                receiveFlatEnergy(2.0);
            }
            if (swipeIndex + 1 >= HERALD_SWIPE_OFFSETS.length) {
                pendingHerald = null;
            } else {
                pendingHerald = herald.withNextSwipe(swipeIndex + 1);
                scheduleNextHeraldSwipe(activeSim, pendingHerald);
            }
        });
    }

    private void preserverOfFortune(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstUsed(castTime, simulator.getApplicableBuffs(this));
        schedule(
                simulator,
                castTime + BURST_TALISMAN_FRAME * FRAME,
                activeSim -> talismanExpirationTime =
                        activeSim.getCurrentTime() + TALISMAN_DURATION);
        AttackAction burst = attack(
                "Adeptus Art: Preserver of Fortune",
                getTalentValue(
                        constellation >= 3
                                ? "Preserver of Fortune C3"
                                : "Preserver of Fortune",
                        constellation >= 3 ? 5.696 : 4.8416),
                Element.CRYO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                2.0,
                false);
        schedule(
                simulator,
                castTime + BURST_HITMARK_FRAME * FRAME,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, burst));
        simulator.advanceTime(BURST_DURATION_FRAMES * FRAME);
    }

    private void resolveNacaImpact(
            CombatSimulator simulator,
            AttackAction action) {
        if (constellation >= 2 && hasCryoOrFrozenTarget(simulator)) {
            action.addBonusStat(action.getActionType() == ActionType.NORMAL
                    ? StatType.NORMAL_ATTACK_DMG_BONUS
                    : StatType.CHARGED_ATTACK_DMG_BONUS, 0.15);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private boolean hasCryoOrFrozenTarget(CombatSimulator simulator) {
        if (simulator.getEnemy() == null) {
            return false;
        }
        double currentTime = simulator.getCurrentTime();
        return simulator.getEnemy().isFrozen(currentTime)
                || simulator.getEnemy().getAuraUnits(
                        Element.CRYO, currentTime) > 0.0;
    }

    private void normalizePendingHerald(double currentTime) {
        if (pendingHerald == null) {
            return;
        }
        if (pendingHerald.snapshot == null) {
            if (pendingHerald.castTime
                    + SKILL_HITMARK_FRAME * FRAME < currentTime) {
                pendingHerald = null;
            }
            return;
        }
        int nextIndex = pendingHerald.nextSwipeIndex;
        while (nextIndex < HERALD_SWIPE_OFFSETS.length
                && pendingHerald.castTime
                        + HERALD_SWIPE_OFFSETS[nextIndex]
                        < currentTime - EVENT_EPSILON) {
            nextIndex++;
        }
        if (nextIndex >= HERALD_SWIPE_OFFSETS.length) {
            pendingHerald = null;
        } else if (nextIndex != pendingHerald.nextSwipeIndex) {
            pendingHerald = pendingHerald.withNextSwipe(nextIndex);
        }
    }

    private StatsContainer captureActionSnapshot(
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
            String name,
            double multiplier,
            Element element,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            boolean shatter) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setShatterTrigger(shatter);
        return action;
    }

    private static void schedule(
            CombatSimulator simulator,
            double time,
            Consumer<CombatSimulator> effect) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                effect.accept(activeSim);
            }
        });
    }

    /** Immutable snapshot of Qiqi-owned combat progression. */
    private static final class QiqiState implements State {
        private final int normalAttackStep;
        private final long heraldGeneration;
        private final double heraldExpirationTime;
        private final PendingHerald pendingHerald;
        private final double talismanExpirationTime;

        private QiqiState(
                int normalAttackStep,
                long heraldGeneration,
                double heraldExpirationTime,
                PendingHerald pendingHerald,
                double talismanExpirationTime) {
            this.normalAttackStep = normalAttackStep;
            this.heraldGeneration = heraldGeneration;
            this.heraldExpirationTime = heraldExpirationTime;
            this.pendingHerald = pendingHerald == null
                    ? null
                    : pendingHerald.copy();
            this.talismanExpirationTime = talismanExpirationTime;
        }
    }

    /** Immutable payload for the next snapshot-restorable Herald swipe. */
    private static final class PendingHerald {
        private final long generation;
        private final double castTime;
        private final StatsContainer snapshot;
        private final int nextSwipeIndex;
        private final boolean c5;

        private PendingHerald(
                long generation,
                double castTime,
                StatsContainer snapshot,
                int nextSwipeIndex,
                boolean c5) {
            this.generation = generation;
            this.castTime = castTime;
            this.snapshot = snapshot == null
                    ? null
                    : snapshot.merge(null);
            this.nextSwipeIndex = nextSwipeIndex;
            this.c5 = c5;
        }

        private PendingHerald withSnapshot(StatsContainer capturedSnapshot) {
            return new PendingHerald(
                    generation,
                    castTime,
                    capturedSnapshot,
                    nextSwipeIndex,
                    c5);
        }

        private PendingHerald withNextSwipe(int nextIndex) {
            return new PendingHerald(
                    generation,
                    castTime,
                    snapshot,
                    nextIndex,
                    c5);
        }

        private PendingHerald copy() {
            return withNextSwipe(nextSwipeIndex);
        }
    }
}
