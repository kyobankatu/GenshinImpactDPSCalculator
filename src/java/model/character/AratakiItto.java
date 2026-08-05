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
import simulation.event.SimpleTimerEvent;

/**
 * Arataki Itto's fixed-target Superlative Superstrength offensive slice.
 *
 * <p>Fight Club Legend normals, Arataki Kesagiri, Ushi, Raging Oni King,
 * A1/A4, and the offensive C1-C6 branches follow pinned gcsim
 * {@code ef41805d}. Burst snapshots DEF into ATK, ends on switch, and all
 * delayed owner work is reconstructable after simulator rollback.</p>
 *
 * <p>Ushi damage-taking stacks, taunt and construct geometry, stamina,
 * movement, resistance reduction, hitlag, and multi-target behavior are
 * excluded rather than approximated.</p>
 */
public final class AratakiItto extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int MAX_STACKS = 5;
    private static final int[] NORMAL_HIT_FRAMES = { 23, 25, 16, 48 };
    private static final int[] NORMAL_DURATIONS = { 41, 51, 57, 83 };
    private static final int[] BURST_NORMAL_DURATIONS = { 33, 36, 43, 83 };
    private static final double[] NORMAL_T9 = {
        1.455654, 1.403040, 1.683648, 2.153666
    };

    private final DoubleSupplier random;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int superlativeStacks;
    private int consecutiveKesagiri;
    private boolean nextComboSlashLeft = true;
    private long ushiGeneration;
    private long burstGeneration;
    private double ushiExpirationTime = Double.NEGATIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private double burstDefAtkBonus;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Itto. */
    public AratakiItto(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Itto at an explicit constellation. */
    public AratakiItto(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Itto with injectable talent data and random draws.
     *
     * @param weapon equipped claymore, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param random particle and C6 draws in {@code [0, 1)}
     */
    public AratakiItto(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier random) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Arataki Itto constellation must be between 0 and 6");
        }
        if (random == null) {
            throw new IllegalArgumentException(
                    "Arataki Itto random source is required");
        }
        name = "Arataki Itto";
        characterId = CharacterId.ARATAKI_ITTO;
        element = Element.GEO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.random = random;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12858.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 227.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 959.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(getTalentValue("Skill Cooldown", 10.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
    }

    /** Binds Itto-owned delayed work to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Arataki Itto simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Arataki Itto must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Arataki Itto cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures combo, stack, Ushi, Burst, random-independent future state. */
    @Override
    public State captureCharacterState() {
        return new IttoState(
                this,
                normalAttackStep,
                superlativeStacks,
                consecutiveKesagiri,
                nextComboSlashLeft,
                ushiGeneration,
                burstGeneration,
                ushiExpirationTime,
                burstExpirationTime,
                burstDefAtkBonus,
                pendingEvents);
    }

    /** Accepts only state captured from this exact Itto instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof IttoState
                && ((IttoState) state).owner == this;
    }

    /** Restores Itto-owned state and re-registers surviving events once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Arataki Itto state");
        }
        initializeForSimulator(simulator);
        IttoState restored = (IttoState) state;
        normalAttackStep = restored.normalAttackStep;
        superlativeStacks = restored.superlativeStacks;
        consecutiveKesagiri = restored.consecutiveKesagiri;
        nextComboSlashLeft = restored.nextComboSlashLeft;
        ushiGeneration = restored.ushiGeneration;
        burstGeneration = restored.burstGeneration;
        ushiExpirationTime = restored.ushiExpirationTime;
        burstExpirationTime = restored.burstExpirationTime;
        burstDefAtkBonus = restored.burstDefAtkBonus;
        pendingEvents = copyEvents(restored.pendingEvents);
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            scheduleEvent(simulator, event);
        }
    }

    /** Returns Itto's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Itto has no unconditional offensive stat passive in this slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Ends Raging Oni King and emits C4 when Itto leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        consecutiveKesagiri = 0;
        if (isBurstActive(simulator.getCurrentTime())) {
            endBurst(simulator, burstGeneration);
        }
    }

    /** Returns the stored Superlative Superstrength count. */
    public int getSuperlativeStackCount() {
        return superlativeStacks;
    }

    /** Returns whether Ushi's source-defined six-second life remains active. */
    public boolean isUshiActive(double currentTime) {
        return ushiGeneration > 0
                && currentTime + EPSILON < ushiExpirationTime;
    }

    /** Returns whether Raging Oni King remains active. */
    public boolean isBurstActive(double currentTime) {
        return burstGeneration > 0
                && currentTime + EPSILON < burstExpirationTime;
    }

    /** Returns the DEF-derived flat ATK snapshotted by the active Burst. */
    public double getBurstDefAtkBonus() {
        return burstDefAtkBonus;
    }

    /** Returns the count of unresolved Itto-owned events. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /** Dispatches Itto's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Arataki Itto action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        if (request.getKey() != CharacterActionKey.CHARGE) {
            consecutiveKesagiri = 0;
            nextComboSlashLeft = true;
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
                masatsuZetsugiAkaushiBurst(simulator);
                break;
            case BURST:
                ragingOniKing(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Arataki Itto: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        boolean burstActive = isBurstActive(castTime);
        StatsContainer snapshot = captureLiveStats(castTime);
        double speed = attackSpeed(
                snapshot, burstActive ? 0.10 : 0.0, true);
        queueEvent(simulator, new PendingEvent(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME / speed,
                EventKind.NORMAL_HIT,
                step,
                burstGeneration,
                snapshot,
                0.0));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        int[] durations = burstActive
                ? BURST_NORMAL_DURATIONS : NORMAL_DURATIONS;
        simulator.advanceTime(durations[step] * FRAME / speed);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        boolean infused = isBurstActive(castTime);
        if (superlativeStacks == 0) {
            queueEvent(simulator, new PendingEvent(
                    castTime + 89.0 * FRAME,
                    EventKind.SAICHIMONJI,
                    0,
                    burstGeneration,
                    snapshot,
                    0.0));
            simulator.advanceTime(131.0 * FRAME);
            return;
        }

        boolean finalSlash = superlativeStacks == 1;
        double a1Bonus = Math.min(
                getTalentValue("A1 Slash Speed Cap", 0.30),
                consecutiveKesagiri
                        * getTalentValue("A1 Slash Speed Per Stack", 0.10));
        double speed = attackSpeed(snapshot, a1Bonus, false);
        int hitFrame;
        int duration;
        EventKind kind;
        if (finalSlash) {
            hitFrame = 71;
            duration = 109;
            kind = EventKind.KESAGIRI_FINAL;
        } else if (nextComboSlashLeft) {
            hitFrame = 51;
            duration = 104;
            kind = EventKind.KESAGIRI_COMBO;
        } else {
            hitFrame = 24;
            duration = 77;
            kind = EventKind.KESAGIRI_COMBO;
        }
        double flatDamage = snapshot.getTotalDef()
                * getTalentValue("A4 DEF Flat DMG Ratio", 0.35);
        queueEvent(simulator, new PendingEvent(
                castTime + hitFrame * FRAME / speed,
                kind,
                0,
                burstGeneration,
                snapshot,
                flatDamage));
        consumeSuperlativeStack();
        if (finalSlash) {
            consecutiveKesagiri = 0;
            nextComboSlashLeft = true;
        } else {
            consecutiveKesagiri = Math.min(3, consecutiveKesagiri + 1);
            nextComboSlashLeft = !nextComboSlashLeft;
        }
        simulator.advanceTime(duration * FRAME / speed);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        queueEvent(simulator, new PendingEvent(
                castTime + 40.0 * FRAME,
                EventKind.HIGH_PLUNGE,
                0,
                burstGeneration,
                snapshot,
                0.0));
        simulator.advanceTime(87.0 * FRAME);
    }

    private void masatsuZetsugiAkaushiBurst(
            CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++ushiGeneration;
        ushiExpirationTime = castTime
                + getTalentValue("Ushi Duration", 6.0);
        StatsContainer snapshot = captureLiveStats(castTime);
        queueEvent(simulator, new PendingEvent(
                castTime + 14.0 * FRAME,
                EventKind.SKILL_COOLDOWN,
                0,
                generation,
                null,
                0.0));
        queueEvent(simulator, new PendingEvent(
                castTime + 18.0 * FRAME,
                EventKind.SKILL_HIT,
                0,
                generation,
                snapshot,
                0.0));
        queueEvent(simulator, new PendingEvent(
                ushiExpirationTime,
                EventKind.USHI_EXIT,
                0,
                generation,
                null,
                0.0));
        simulator.advanceTime(42.0 * FRAME);
    }

    private void ragingOniKing(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer preBurst = captureLiveStats(castTime);
        long generation = ++burstGeneration;
        burstExpirationTime = castTime
                + getTalentValue("Burst Duration Frames", 795.0) * FRAME;
        double ratio = getTalentValue(
                constellation >= 5
                        ? "DEF to ATK Conversion C5"
                        : "DEF to ATK Conversion",
                constellation >= 5 ? 1.152000 : 0.979200);
        burstDefAtkBonus = preBurst.getTotalDef() * ratio;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        if (constellation >= 2) {
            int geoCount = 0;
            for (Character member : simulator.getPartyMembers()) {
                if (member.getElement() == Element.GEO) {
                    geoCount++;
                }
            }
            geoCount = Math.min(3, geoCount);
            queueEvent(simulator, new PendingEvent(
                    castTime + 9.0 * FRAME,
                    EventKind.C2_RESOLVE,
                    geoCount,
                    generation,
                    null,
                    0.0));
        }
        queueEvent(simulator, new PendingEvent(
                castTime + FRAME,
                EventKind.BURST_ENERGY,
                0,
                generation,
                null,
                0.0));
        queueEvent(simulator, new PendingEvent(
                burstExpirationTime,
                EventKind.BURST_EXPIRE,
                0,
                generation,
                null,
                0.0));
        if (constellation >= 1) {
            queueStackEvent(simulator, castTime + 75.0 * FRAME, generation, 2);
            queueStackEvent(simulator, castTime + 135.0 * FRAME, generation, 1);
            queueStackEvent(simulator, castTime + 165.0 * FRAME, generation, 1);
            queueStackEvent(simulator, castTime + 195.0 * FRAME, generation, 1);
        }
        simulator.advanceTime(91.0 * FRAME);
    }

    private void queueStackEvent(
            CombatSimulator simulator,
            double time,
            long generation,
            int count) {
        queueEvent(simulator, new PendingEvent(
                time,
                EventKind.C1_STACK,
                count,
                generation,
                null,
                0.0));
    }

    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.kind) {
            case NORMAL_HIT:
                resolveNormalHit(simulator, event);
                break;
            case SAICHIMONJI:
                performHit(simulator, event,
                        "Fight Club Legend Saichimonji Slash",
                        getTalentValue("Saichimonji Slash", 1.662160),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        false);
                break;
            case KESAGIRI_COMBO:
                performHit(simulator, event,
                        "Arataki Kesagiri Combo Slash",
                        getTalentValue(
                                "Arataki Kesagiri Combo Slash", 1.674800),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        true);
                break;
            case KESAGIRI_FINAL:
                performHit(simulator, event,
                        "Arataki Kesagiri Final Slash",
                        getTalentValue(
                                "Arataki Kesagiri Final Slash", 3.507600),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        true);
                break;
            case HIGH_PLUNGE:
                performHit(simulator, event,
                        "Fight Club Legend High Plunge",
                        getTalentValue("High Plunge", 3.754990),
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        false);
                break;
            case SKILL_COOLDOWN:
                if (event.generation == ushiGeneration) {
                    markSkillUsed(
                            simulator.getCurrentTime(),
                            simulator.getApplicableBuffs(this));
                }
                break;
            case SKILL_HIT:
                resolveSkillHit(simulator, event);
                break;
            case USHI_EXIT:
                if (event.generation == ushiGeneration) {
                    ushiExpirationTime = Double.NEGATIVE_INFINITY;
                    addSuperlativeStacks(1);
                }
                break;
            case BURST_ENERGY:
                if (event.generation == burstGeneration) {
                    spendBurstEnergy(simulator.getCurrentTime());
                }
                break;
            case BURST_EXPIRE:
                if (event.generation == burstGeneration) {
                    endBurst(simulator, event.generation);
                }
                break;
            case C1_STACK:
                if (event.generation == burstGeneration
                        && isBurstActive(simulator.getCurrentTime())) {
                    addSuperlativeStacks(event.index);
                }
                break;
            case C2_RESOLVE:
                if (event.generation == burstGeneration) {
                    receiveFlatEnergy(event.index
                            * getTalentValue("C2 Energy Per Geo", 6.0));
                    reduceBurstCooldown(
                            simulator.getCurrentTime(),
                            event.index * getTalentValue(
                                    "C2 Cooldown Reduction Per Geo", 1.5));
                }
                break;
            case PARTICLE:
                simulator.getEnergyDistributor().distributeParticles(
                        Element.GEO,
                        event.value,
                        ParticleType.PARTICLE);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Arataki Itto event " + event.kind);
        }
    }

    private void resolveNormalHit(
            CombatSimulator simulator,
            PendingEvent event) {
        performHit(simulator, event,
                "Fight Club Legend N" + (event.index + 1),
                getTalentValue(
                        "N" + (event.index + 1),
                        NORMAL_T9[event.index]),
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                false);
        if (event.index == 1) {
            addSuperlativeStacks(1);
        } else if (event.index == 3) {
            addSuperlativeStacks(2);
        } else if ((event.index == 0 || event.index == 2)
                && event.generation == burstGeneration
                && isBurstActive(event.time)) {
            addSuperlativeStacks(1);
        }
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            PendingEvent event) {
        if (event.generation != ushiGeneration) {
            return;
        }
        performHit(simulator, event,
                "Masatsu Zetsugi: Akaushi Burst",
                getTalentValue(
                        constellation >= 3
                                ? "Masatsu Zetsugi Akaushi Burst C3"
                                : "Masatsu Zetsugi Akaushi Burst",
                        constellation >= 3 ? 6.144000 : 5.222400),
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                false);
        addSuperlativeStacks(1);
        if (simulator.getEnemy() == null) {
            return;
        }
        double draw = drawRandom("particle");
        double count = draw < getTalentValue(
                "Particle Chance Four", 0.5) ? 4.0 : 3.0;
        queueEvent(simulator, new PendingEvent(
                simulator.getCurrentTime()
                        + getTalentValue("Particle Travel Frames", 100.0)
                                * FRAME,
                EventKind.PARTICLE,
                0,
                0L,
                null,
                count));
    }

    private void performHit(
            CombatSimulator simulator,
            PendingEvent event,
            String displayName,
            double multiplier,
            StatType bonusStat,
            ActionType actionType,
            boolean kesagiri) {
        boolean infused = event.generation == burstGeneration
                && isBurstActive(event.time);
        boolean elementalSkill = actionType == ActionType.SKILL;
        Element hitElement = elementalSkill || infused
                ? Element.GEO : Element.PHYSICAL;
        AttackAction action = event.value == 0.0
                ? new AttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        StatType.BASE_ATK,
                        bonusStat,
                        0.0,
                        actionType)
                : new IttoAttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        bonusStat,
                        actionType,
                        event.value);
        action.setICD(
                elementalSkill ? ICDType.Standard
                        : infused ? ICDType.None : ICDType.Standard,
                icdTag(actionType),
                elementalSkill ? 2.0 : infused ? 1.0 : 0.0);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setShatterTrigger(true);
        if (kesagiri && constellation >= 6) {
            action.addBonusStat(
                    StatType.CRIT_DMG,
                    getTalentValue("C6 Charged CRIT DMG", 0.70));
        }
        if (event.snapshot != null) {
            action.setStatSnapshot(event.snapshot);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static ICDTag icdTag(ActionType actionType) {
        switch (actionType) {
            case NORMAL:
                return ICDTag.NormalAttack;
            case CHARGE:
                return ICDTag.ChargedAttack;
            case PLUNGE:
                return ICDTag.PlungeAttack;
            case SKILL:
                return ICDTag.ElementalSkill;
            default:
                return ICDTag.None;
        }
    }

    private void consumeSuperlativeStack() {
        if (constellation >= 6
                && drawRandom("C6 stack preservation")
                        < getTalentValue(
                                "C6 Preserve Stack Chance", 0.50)) {
            return;
        }
        superlativeStacks = Math.max(0, superlativeStacks - 1);
    }

    private double drawRandom(String purpose) {
        double draw = random.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Arataki Itto " + purpose
                            + " random draw must be in [0, 1)");
        }
        return draw;
    }

    private void addSuperlativeStacks(int count) {
        superlativeStacks = Math.min(
                MAX_STACKS, superlativeStacks + count);
    }

    private void endBurst(
            CombatSimulator simulator,
            long generation) {
        if (generation != burstGeneration) {
            return;
        }
        burstGeneration++;
        burstExpirationTime = Double.NEGATIVE_INFINITY;
        burstDefAtkBonus = 0.0;
        if (constellation < 4) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        for (Character member : simulator.getPartyMembers()) {
            member.removeBuff(BuffId.ITTO_C4_PARTY_ATK_DEF);
            member.addBuff(new SimpleBuff(
                    "Itto Jailhouse Bread and Butter",
                    BuffId.ITTO_C4_PARTY_ATK_DEF,
                    getTalentValue("C4 Duration", 10.0),
                    currentTime,
                    stats -> {
                        stats.add(
                                StatType.ATK_PERCENT,
                                getTalentValue("C4 Party ATK", 0.20));
                        stats.add(
                                StatType.DEF_PERCENT,
                                getTalentValue("C4 Party DEF", 0.20));
                    }).sourcedBy(characterId));
        }
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff
                    : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        if (isBurstActive(currentTime)) {
            stats.add(StatType.ATK_FLAT, burstDefAtkBonus);
        }
        return stats;
    }

    private static double attackSpeed(
            StatsContainer snapshot,
            double sourceBonus,
            boolean includeNormalAttackSpeed) {
        return 1.0 + Math.min(
                0.60,
                Math.max(0.0,
                        snapshot.get(StatType.ATK_SPD)
                                + (includeNormalAttackSpeed
                                        ? snapshot.get(
                                                StatType.NORMAL_ATTACK_SPD)
                                        : 0.0)
                                + sourceBonus));
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
        NORMAL_HIT,
        SAICHIMONJI,
        KESAGIRI_COMBO,
        KESAGIRI_FINAL,
        HIGH_PLUNGE,
        SKILL_COOLDOWN,
        SKILL_HIT,
        USHI_EXIT,
        BURST_ENERGY,
        BURST_EXPIRE,
        C1_STACK,
        C2_RESOLVE,
        PARTICLE
    }

    /** Preserves A4's cast-time DEF addition through damage resolution. */
    private static final class IttoAttackAction extends AttackAction {
        private final double fixedBaseDamage;

        private IttoAttackAction(
                String displayName,
                double multiplier,
                Element element,
                StatType bonusStat,
                ActionType actionType,
                double fixedBaseDamage) {
            super(
                    displayName,
                    multiplier,
                    element,
                    StatType.BASE_ATK,
                    bonusStat,
                    0.0,
                    actionType);
            this.fixedBaseDamage = fixedBaseDamage;
        }

        @Override
        public void setAdditiveBaseDmgBonus(double value) {
        }

        @Override
        public double getAdditiveBaseDmgBonus() {
            return fixedBaseDamage;
        }
    }

    /** Immutable reconstructable Itto-owned event. */
    private static final class PendingEvent {
        private final double time;
        private final EventKind kind;
        private final int index;
        private final long generation;
        private final StatsContainer snapshot;
        private final double value;

        private PendingEvent(
                double time,
                EventKind kind,
                int index,
                long generation,
                StatsContainer snapshot,
                double value) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.value = value;
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time, kind, index, generation, snapshot, value);
        }
    }

    /** Immutable snapshot of all mutable Itto-owned runtime state. */
    private static final class IttoState implements State {
        private final AratakiItto owner;
        private final int normalAttackStep;
        private final int superlativeStacks;
        private final int consecutiveKesagiri;
        private final boolean nextComboSlashLeft;
        private final long ushiGeneration;
        private final long burstGeneration;
        private final double ushiExpirationTime;
        private final double burstExpirationTime;
        private final double burstDefAtkBonus;
        private final List<PendingEvent> pendingEvents;

        private IttoState(
                AratakiItto owner,
                int normalAttackStep,
                int superlativeStacks,
                int consecutiveKesagiri,
                boolean nextComboSlashLeft,
                long ushiGeneration,
                long burstGeneration,
                double ushiExpirationTime,
                double burstExpirationTime,
                double burstDefAtkBonus,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.superlativeStacks = superlativeStacks;
            this.consecutiveKesagiri = consecutiveKesagiri;
            this.nextComboSlashLeft = nextComboSlashLeft;
            this.ushiGeneration = ushiGeneration;
            this.burstGeneration = burstGeneration;
            this.ushiExpirationTime = ushiExpirationTime;
            this.burstExpirationTime = burstExpirationTime;
            this.burstDefAtkBonus = burstDefAtkBonus;
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}
