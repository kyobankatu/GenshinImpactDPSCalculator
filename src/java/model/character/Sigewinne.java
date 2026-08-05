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
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
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
 * Sigewinne's stationary fixed-target Hydrotherapy offensive slice.
 *
 * <p>Level-90 data, three Physical Normal Attacks, Press and maximum-Hold
 * Bubblebalm sequences, particles, Surging Blades, Burst pulses, A1, and
 * representable C1-C5 behavior follow pinned gcsim {@code ef41805d} and KQM
 * TCL {@code 80ba6241}. Bounce targets are adapted to the simulator's one
 * fixed enemy; no random or multi-target selection is synthesized.</p>
 *
 * <p>Healing, player HP, Bond of Life and Sourcewater Droplets, C2 shielding,
 * healing-triggered C6, movement and geometry, underwater behavior, hitlag,
 * stamina, aimed shots, and Plunge attacks are excluded explicitly. Skill
 * Bubblebalms retain their cast-time snapshot while Burst pulses use live
 * stats at each pulse.</p>
 */
public final class Sigewinne extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 12, 14, 38 };
    private static final int[] NORMAL_DURATIONS = { 20, 36, 82 };
    private static final double[] NORMAL_T9 = {
        0.966628, 0.938283, 1.438369
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private boolean particleGenerated;
    private int convalescenceStacks;
    private double convalescenceExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextSurgingBladeAllowedTime = Double.NEGATIVE_INFINITY;
    private boolean resolvingParticleEligible;
    private boolean resolvingC2Eligible;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Sigewinne. */
    public Sigewinne(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Sigewinne at an explicit constellation. */
    public Sigewinne(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Sigewinne with injectable static talent data.
     *
     * @param weapon equipped bow, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Sigewinne(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Sigewinne constellation must be between 0 and 6");
        }
        name = "Sigewinne";
        characterId = CharacterId.SIGEWINNE;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13348.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 193.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 500.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP Percent", 0.288));
        setSkillCD(getTalentValue("Skill Cooldown", 18.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
    }

    /** Binds A1 and C2 hit listeners to exactly one owning simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Sigewinne simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Sigewinne must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Sigewinne cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener(this::handleAcceptedDamage);
    }

    /** Captures offensive resources, private gates, and reconstructable work. */
    @Override
    public State captureCharacterState() {
        return new SigewinneState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                particleGenerated,
                convalescenceStacks,
                convalescenceExpirationTime,
                nextSurgingBladeAllowedTime,
                pendingEvents);
    }

    /** Accepts state captured from this exact Sigewinne instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof SigewinneState
                && ((SigewinneState) state).owner == this;
    }

    /** Restores Sigewinne-owned delayed hits and particles exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Sigewinne state");
        }
        initializeForSimulator(simulator);
        SigewinneState restored = (SigewinneState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        particleGenerated = restored.particleGenerated;
        convalescenceStacks = restored.convalescenceStacks;
        convalescenceExpirationTime =
                restored.convalescenceExpirationTime;
        nextSurgingBladeAllowedTime =
                restored.nextSurgingBladeAllowedTime;
        pendingEvents = copyEvents(restored.pendingEvents);
        resolvingParticleEligible = false;
        resolvingC2Eligible = false;
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            scheduleEvent(simulator, event);
        }
        expireConvalescence(currentTime);
    }

    /** Returns Sigewinne's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Sigewinne's permanent HP ascension is loaded structurally. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Represented combat passives are activated by Skill use and hits.
    }

    /** Supports Press and the source-backed maximum-Hold Skill tier. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS
                || mode == SkillActionMode.HOLD;
    }

    /** Resets the three-hit Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets the three-hit Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the remaining A1/C1 Convalescence stack count. */
    public int getConvalescenceStacks(double currentTime) {
        expireConvalescence(currentTime);
        return convalescenceStacks;
    }

    /** Returns the absolute half-open Convalescence expiration timestamp. */
    public double getConvalescenceExpirationTime() {
        return convalescenceExpirationTime;
    }

    /** Returns the next Bubblebalm timestamp eligible to call a Surging Blade. */
    public double getNextSurgingBladeAllowedTime() {
        return nextSurgingBladeAllowedTime;
    }

    /** Returns the number of unresolved Sigewinne-owned events. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /** Reports that healing and healing-triggered effects are excluded. */
    public boolean isHealingRepresented() {
        return false;
    }

    /** Reports that player current-HP state is excluded. */
    public boolean isPlayerHpRepresented() {
        return false;
    }

    /** Reports that Bond of Life and Sourcewater Droplets are excluded. */
    public boolean isBondOfLifeRepresented() {
        return false;
    }

    /** Reports that movement and target geometry are excluded. */
    public boolean isMovementGeometryRepresented() {
        return false;
    }

    /** Reports that random and multi-target bounce selection is excluded. */
    public boolean isRandomBounceTargetingRepresented() {
        return false;
    }

    /** Reports that underwater mechanics are excluded. */
    public boolean isUnderwaterRepresented() {
        return false;
    }

    /** Reports that hitlag and stamina state are excluded. */
    public boolean isHitlagStaminaRepresented() {
        return false;
    }

    /** Reports that healing-triggered C6 Burst CRIT is excluded. */
    public boolean isC6CritRepresented() {
        return false;
    }

    /** Applies A1's live-HP flat addition to eligible off-field Skill hits. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            Enemy target,
            AttackAction action,
            double currentTime) {
        if (!qualifiesForConvalescence(
                attacker, target, action, currentTime)) {
            return;
        }
        stats.add(StatType.FLAT_DMG_BONUS,
                convalescenceFlatDamage(currentTime));
    }

    /** Dispatches Sigewinne's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Sigewinne action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                targetedTreatment(simulator);
                break;
            case SKILL:
                reboundHydrotherapy(simulator, request.getSkillMode());
                break;
            case BURST:
                superSaturatedSyringing(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Sigewinne: "
                                + request.getKey());
        }
    }

    private void targetedTreatment(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueEvent(simulator, new PendingEvent(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                EventKind.NORMAL,
                0L,
                step,
                0,
                captureLiveStats(castTime)));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void reboundHydrotherapy(
            CombatSimulator simulator,
            SkillActionMode mode) {
        if (mode != SkillActionMode.PRESS
                && mode != SkillActionMode.HOLD) {
            throw new IllegalArgumentException(
                    "Unsupported Sigewinne Skill mode: " + mode);
        }
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        boolean hold = mode == SkillActionMode.HOLD;
        int firstHitFrame = hold ? 90 : 35;
        int cooldownStartFrame = hold ? 66 : 16;
        int animationFrames = hold ? 89 : 41;
        int bubbleCount = constellation >= 1 ? 8 : 5;
        int bubbleTier = hold ? 2 : 0;
        StatsContainer skillSnapshot = captureLiveStats(castTime);

        particleGenerated = false;
        activateAppropriateRest(castTime);
        queueEvent(simulator, new PendingEvent(
                castTime + cooldownStartFrame * FRAME,
                EventKind.SKILL_COOLDOWN,
                generation,
                0,
                0,
                null));
        double intervalFrames = getTalentValue(
                "Bubble Interval Frames", 107.0);
        for (int index = 0; index < bubbleCount; index++) {
            double bubbleTime = castTime
                    + (firstHitFrame + index * intervalFrames) * FRAME;
            queueEvent(simulator, new PendingEvent(
                    bubbleTime,
                    EventKind.BUBBLE,
                    generation,
                    index,
                    bubbleTier,
                    skillSnapshot));
            if (constellation < 1 || index > 2) {
                bubbleTier = Math.max(0, bubbleTier - 1);
            }
        }
        simulator.advanceTime(animationFrames * FRAME);
    }

    private void superSaturatedSyringing(
            CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        int pulseCount = constellation >= 4 ? 14 : 6;
        int animationFrames = constellation >= 4 ? 425 : 241;
        queueEvent(simulator, new PendingEvent(
                castTime + FRAME,
                EventKind.BURST_COOLDOWN,
                generation,
                0,
                0,
                null));
        queueEvent(simulator, new PendingEvent(
                castTime + 5.0 * FRAME,
                EventKind.BURST_ENERGY,
                generation,
                0,
                0,
                null));
        for (int index = 0; index < pulseCount; index++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + (99.0 + index * 25.0) * FRAME,
                    EventKind.BURST,
                    generation,
                    index,
                    0,
                    null));
        }
        simulator.advanceTime(animationFrames * FRAME);
    }

    private void activateAppropriateRest(double castTime) {
        convalescenceStacks = (int) getTalentValue(
                "A1 Base Stacks", 10.0);
        convalescenceExpirationTime = castTime
                + getTalentValue("Skill Duration", 18.0);
        removeBuff(BuffId.SIGEWINNE_A1_HYDRO_DMG_BONUS);
        addBuff(new SimpleBuff(
                "Sigewinne Semi-Strict Bedrest",
                BuffId.SIGEWINNE_A1_HYDRO_DMG_BONUS,
                getTalentValue("Skill Duration", 18.0),
                castTime,
                stats -> stats.add(
                        StatType.HYDRO_DMG_BONUS,
                        getTalentValue(
                                "A1 Hydro DMG Bonus", 0.08))));
    }

    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        event,
                        "Targeted Treatment N" + (event.index + 1),
                        getTalentValue(
                                "N" + (event.index + 1),
                                NORMAL_T9[event.index]),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.None,
                        ICDTag.NormalAttack,
                        0.0,
                        false,
                        false);
                break;
            case BUBBLE:
                if (event.generation != skillGeneration) {
                    break;
                }
                resolveBubble(simulator, event);
                break;
            case SURGING_BLADE:
                if (event.generation != skillGeneration) {
                    break;
                }
                performHit(
                        simulator,
                        event,
                        "Rebound Hydrotherapy: Surging Blade",
                        skillTalentValue(
                                "Surging Blade", 0.011628, 0.01368),
                        Element.HYDRO,
                        StatType.BASE_HP,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        0.0,
                        false,
                        false);
                break;
            case BURST:
                performHit(
                        simulator,
                        event,
                        "Super Saturated Syringing Pulse "
                                + (event.index + 1),
                        burstTalentValue(
                                "Super Saturated Syringing",
                                0.200104,
                                0.235416),
                        Element.HYDRO,
                        StatType.BASE_HP,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.SigewinneBurst,
                        ICDTag.Sigewinne_Burst,
                        1.0,
                        false,
                        true);
                break;
            case SKILL_COOLDOWN:
                if (event.generation == skillGeneration) {
                    markSkillUsed(event.time,
                            simulator.getApplicableBuffs(this));
                }
                break;
            case BURST_COOLDOWN:
                if (event.generation == burstGeneration) {
                    markBurstCooldownUsed(event.time,
                            simulator.getApplicableBuffs(this));
                }
                break;
            case BURST_ENERGY:
                if (event.generation == burstGeneration) {
                    spendBurstEnergy(event.time);
                }
                break;
            case PARTICLE:
                simulator.getEnergyDistributor().distributeParticles(
                        Element.HYDRO,
                        event.index,
                        ParticleType.PARTICLE);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Sigewinne event kind");
        }
    }

    private void resolveBubble(
            CombatSimulator simulator,
            PendingEvent event) {
        performHit(
                simulator,
                event,
                "Rebound Hydrotherapy Bubble " + (event.index + 1),
                skillTalentValue("Bubblebalm", 0.03876, 0.0456),
                Element.HYDRO,
                StatType.BASE_HP,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.SigewinneBubblebalm,
                ICDTag.Sigewinne_Bubblebalm,
                1.0,
                true,
                true);
        if (event.time + EPSILON >= nextSurgingBladeAllowedTime) {
            nextSurgingBladeAllowedTime = event.time
                    + getTalentValue("Surging Blade Interval", 10.0);
            queueEvent(simulator, new PendingEvent(
                    event.time + 40.0 * FRAME,
                    EventKind.SURGING_BLADE,
                    event.generation,
                    event.index,
                    0,
                    null));
        }
        if (constellation >= 1
                && event.generation == skillGeneration
                && event.time < convalescenceExpirationTime) {
            convalescenceStacks++;
        }
    }

    private void performHit(
            CombatSimulator simulator,
            PendingEvent event,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            boolean particleEligible,
            boolean c2Eligible) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                scalingStat,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (event.tier > 0) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    event.tier * getTalentValue(
                            "Hold Tier DMG Bonus", 0.05));
        }
        action.setStatSnapshot(event.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : event.snapshot);
        resolvingParticleEligible = particleEligible
                && !particleGenerated;
        resolvingC2Eligible = c2Eligible && constellation >= 2;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingParticleEligible = false;
            resolvingC2Eligible = false;
        }
    }

    private void handleAcceptedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (initializedSimulator == null || damage <= 0.0) {
            return;
        }
        if (actor == this) {
            if (resolvingParticleEligible && !particleGenerated) {
                particleGenerated = true;
                queueEvent(initializedSimulator, new PendingEvent(
                        time + getTalentValue(
                                "Particle Travel Frames", 100.0) * FRAME,
                        EventKind.PARTICLE,
                        skillGeneration,
                        (int) getTalentValue("Particle Count", 4.0),
                        0,
                        null));
            }
            if (resolvingC2Eligible) {
                applyC2Shred(time);
            }
            return;
        }
        if (qualifiesForConvalescence(
                actor,
                initializedSimulator.getEnemy(),
                action,
                time)) {
            convalescenceStacks--;
        }
    }

    private void applyC2Shred(double currentTime) {
        initializedSimulator.applyTeamBuffNoStack(new SimpleBuff(
                "Sigewinne C2 Hydro RES Shred",
                BuffId.SIGEWINNE_C2_HYDRO_RES_SHRED,
                getTalentValue("C2 Duration", 8.0),
                currentTime,
                stats -> stats.add(
                        StatType.HYDRO_RES_SHRED,
                        getTalentValue(
                                "C2 Hydro RES Shred", 0.35)))
                .sourcedBy(characterId));
    }

    private boolean qualifiesForConvalescence(
            Character attacker,
            Enemy target,
            AttackAction action,
            double currentTime) {
        expireConvalescence(currentTime);
        return initializedSimulator != null
                && target != null
                && attacker != null
                && attacker != this
                && action != null
                && action.getActionType() == ActionType.SKILL
                && action.isHitEffectTrigger()
                && convalescenceStacks > 0
                && currentTime < convalescenceExpirationTime
                && initializedSimulator.getPartyMembers().contains(attacker)
                && initializedSimulator.getActiveCharacter() != attacker;
    }

    private double convalescenceFlatDamage(double currentTime) {
        double maxHp = captureLiveStats(currentTime).getTotalHp();
        double excessHp = Math.max(0.0, maxHp
                - getTalentValue("A1 Base HP Threshold", 30000.0));
        double ratio = constellation >= 1
                ? getTalentValue("C1 Flat DMG Per HP", 0.1)
                : getTalentValue("A1 Flat DMG Per HP", 0.08);
        double cap = constellation >= 1
                ? getTalentValue("C1 Flat DMG Cap", 3500.0)
                : getTalentValue("A1 Flat DMG Cap", 2800.0);
        return Math.min(cap, excessHp * ratio);
    }

    private void expireConvalescence(double currentTime) {
        if (currentTime + EPSILON >= convalescenceExpirationTime) {
            convalescenceStacks = 0;
        }
    }

    private double skillTalentValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue(key + suffix,
                constellation >= 3 ? talentTwelve : talentNine);
    }

    private double burstTalentValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(key + suffix,
                constellation >= 5 ? talentTwelve : talentNine);
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

    private void queueEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        pendingEvents.add(event);
        scheduleEvent(simulator, event);
    }

    private void scheduleEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        schedule(simulator, event.time, activeSimulator -> {
            if (!pendingEvents.remove(event)) {
                return;
            }
            resolveEvent(activeSimulator, event);
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

    private static List<PendingEvent> copyEvents(
            List<PendingEvent> source) {
        List<PendingEvent> copy = new ArrayList<>();
        for (PendingEvent event : source) {
            copy.add(event.copy());
        }
        return copy;
    }

    private enum EventKind {
        NORMAL,
        BUBBLE,
        SURGING_BLADE,
        BURST,
        SKILL_COOLDOWN,
        BURST_COOLDOWN,
        BURST_ENERGY,
        PARTICLE
    }

    private static final class PendingEvent {
        private final double time;
        private final EventKind kind;
        private final long generation;
        private final int index;
        private final int tier;
        private final StatsContainer snapshot;

        private PendingEvent(
                double time,
                EventKind kind,
                long generation,
                int index,
                int tier,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.index = index;
            this.tier = tier;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time,
                    kind,
                    generation,
                    index,
                    tier,
                    snapshot);
        }
    }

    private static final class SigewinneState implements State {
        private final Sigewinne owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final boolean particleGenerated;
        private final int convalescenceStacks;
        private final double convalescenceExpirationTime;
        private final double nextSurgingBladeAllowedTime;
        private final List<PendingEvent> pendingEvents;

        private SigewinneState(
                Sigewinne owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                boolean particleGenerated,
                int convalescenceStacks,
                double convalescenceExpirationTime,
                double nextSurgingBladeAllowedTime,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.particleGenerated = particleGenerated;
            this.convalescenceStacks = convalescenceStacks;
            this.convalescenceExpirationTime =
                    convalescenceExpirationTime;
            this.nextSurgingBladeAllowedTime =
                    nextSurgingBladeAllowedTime;
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}
