package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Illuga's deterministic fixed-target Nightingale's Song support slice.
 *
 * <p>Static values follow Genshin Optimizer {@code 61c5556a}; hitmarks,
 * recovery, cooldown starts, particles, and represented talent behavior follow
 * gcsim PR #2677 head {@code 1dabbc1b}. Skill and Burst damage use their
 * sourced EM-plus-DEF additive base formulas, while Nightingale's Song adds
 * Illuga-EM-derived flat base damage to accepted active-character Geo or
 * direct Lunar-Crystallize hits.</p>
 *
 * <p>The source's random four-or-five particles use the deterministic expected
 * value 4.5. Geometry, hitlag, stamina, Plunge, Geo construct distance and
 * spawn tracking, and indirect Harmony-style Lunar-Crystallize support are
 * unavailable in this fixed-target runtime and fail closed. Direct typed
 * Lunar-Crystallize attacks receive the represented A1 and Song effects.</p>
 */
public final class Illuga extends Character implements
        CombatSimulator.ReactionListener,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 13 }, { 16 }, { 20, 32 }, { 43 }
    };
    private static final int[] NORMAL_DURATION_FRAMES = { 30, 33, 57, 80 };
    private static final double[][] NORMAL_T9 = {
        { 0.870217 }, { 0.891515 },
        { 0.577490, 0.577490 }, { 1.401397 }
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long eventGeneration;
    private double a1ActiveUntil = Double.NEGATIVE_INFINITY;
    private double burstActiveUntil = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC1AllowedTime = Double.NEGATIVE_INFINITY;
    private int nightingalesSongStacks;
    private int c2ConsumedStacks;
    private AttackAction pendingSongAction;
    private Character pendingSongActor;
    private AttackAction resolvingAction;
    private boolean resolvingSkillHit;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Illuga. */
    public Illuga(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Illuga at an explicit constellation. */
    public Illuga(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Illuga with injectable static talent data.
     *
     * @param weapon equipped polearm, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Illuga(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Illuga constellation must be between 0 and 6");
        }
        name = "Illuga";
        characterId = CharacterId.ILLUGA;
        element = Element.GEO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11962.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 191.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 813.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Ascension Elemental Mastery", 96.0));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Illuga's reaction and accepted-damage listeners once. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Illuga simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Illuga must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Illuga cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
        simulator.addDamageListener((actor, action, damage, time) ->
                handleAcceptedDamage(
                        simulator, actor, action, damage, time));
    }

    /** Captures every Illuga-owned timer, stack, and delayed event. */
    @Override
    public State captureCharacterState() {
        return new IllugaState(
                this,
                normalAttackStep,
                eventGeneration,
                a1ActiveUntil,
                burstActiveUntil,
                nextParticleAllowedTime,
                nextC1AllowedTime,
                nightingalesSongStacks,
                c2ConsumedStacks,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Illuga instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof IllugaState
                && ((IllugaState) state).owner == this;
    }

    /** Restores Illuga state while invalidating every older queued callback. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Illuga state");
        }
        initializeForSimulator(simulator);
        IllugaState restored = (IllugaState) state;
        normalAttackStep = restored.normalAttackStep;
        eventGeneration = Math.max(
                eventGeneration, restored.eventGeneration) + 1L;
        a1ActiveUntil = restored.a1ActiveUntil;
        burstActiveUntil = restored.burstActiveUntil;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        nextC1AllowedTime = restored.nextC1AllowedTime;
        nightingalesSongStacks = restored.nightingalesSongStacks;
        c2ConsumedStacks = restored.c2ConsumedStacks;
        pendingSongAction = null;
        pendingSongActor = null;
        resolvingAction = null;
        resolvingSkillHit = false;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        double currentTime = simulator.getCurrentTime();
        pendingHits.removeIf(hit -> hit.time < currentTime - EPSILON);
        pendingCommands.removeIf(command ->
                command.time < currentTime - EPSILON);
        for (PendingHit hit : new ArrayList<>(pendingHits)) {
            scheduleHit(simulator, hit);
        }
        for (PendingCommand command
                : new ArrayList<>(pendingCommands)) {
            scheduleCommand(simulator, command);
        }
    }

    /** Returns Illuga's sourced 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Illuga has no unconditional passive beyond ascension EM. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Reports Illuga's typed Moonsign contribution. */
    @Override
    public boolean isLunarCharacter() {
        return true;
    }

    /** Resets the four-stage Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets the four-stage Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Accepts both sourced Press and Hold Skill variants. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS
                || mode == SkillActionMode.HOLD;
    }

    /** Returns the live Nightingale's Song stack count. */
    public int getNightingalesSongStacks() {
        return nightingalesSongStacks;
    }

    /** Returns the exact Haunted Night's Oriole-Song expiration. */
    public double getBurstActiveUntil() {
        return burstActiveUntil;
    }

    /** Returns whether A1 is active at the supplied time. */
    public boolean isA1Active(double currentTime) {
        return currentTime + EPSILON < a1ActiveUntil;
    }

    /** Returns C4's active-character DEF amount at the supplied time. */
    public double getC4DefenseBonus(double currentTime) {
        return constellation >= 4
                && currentTime + EPSILON < burstActiveUntil
                ? getTalentValue("C4 Active DEF", 200.0) : 0.0;
    }

    /** Reports that Geo construct distance and spawn tracking are excluded. */
    public boolean isGeoConstructTrackingRepresented() {
        return false;
    }

    /** Reports that indirect Harmony Lunar support is excluded. */
    public boolean isIndirectLunarSupportRepresented() {
        return false;
    }

    /** Dispatches Illuga's represented Normal, Charged, Skill, and Burst set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Illuga action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() != CharacterActionKey.NORMAL
                && request.getKey() != CharacterActionKey.CHARGE) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case CHARGE:
                chargedAttack(simulator);
                break;
            case SKILL:
                skill(simulator, request.getSkillMode());
                break;
            case BURST:
                burst(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Illuga: "
                                + request.getKey());
        }
    }

    /** Grants C1 only for Illuga's on-field Crystallize reaction. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || constellation < 1
                || result == null
                || source != this
                || simulator.getActiveCharacter() != this
                || time + EPSILON < nextC1AllowedTime
                || (result.getKind() != ReactionResult.Kind.CRYSTALLIZE
                        && result.getKind()
                                != ReactionResult.Kind.LUNAR_CRYSTALLIZE)) {
            return;
        }
        nextC1AllowedTime = time
                + getTalentValue("C1 Cooldown", 15.0);
        receiveFlatEnergy(getTalentValue("C1 Flat Energy", 12.0));
    }

    /**
     * Applies A1, C4, and one live Song quill to a direct fixed-target hit.
     *
     * <p>The Song candidate is consumed only by the subsequent accepted damage
     * callback, so no-enemy and rejected hits leave the quota unchanged.</p>
     */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            model.entity.Enemy target,
            AttackAction action,
            double currentTime) {
        if (initializedSimulator == null
                || target == null
                || attacker == null
                || action == null
                || !initializedSimulator.getPartyMembers().contains(attacker)) {
            return;
        }
        boolean directLunarCrystallize = isDirectLunarCrystallize(action);
        boolean geoDamage = action.getElement() == Element.GEO;
        if (attacker != this
                && isA1Active(currentTime)
                && initializedSimulator.getMoonsign()
                        == CombatSimulator.Moonsign.ASCENDANT_GLEAM) {
            stats.add(StatType.ELEMENTAL_MASTERY,
                    a1ElementalMastery());
        }
        if (attacker != this
                && isA1Active(currentTime)
                && (geoDamage || directLunarCrystallize)) {
            stats.add(StatType.CRIT_RATE, a1CritRate());
            stats.add(StatType.CRIT_DMG, a1CritDamage());
        }
        if (attacker != this
                && attacker == initializedSimulator.getActiveCharacter()) {
            stats.add(StatType.DEF_FLAT,
                    getC4DefenseBonus(currentTime));
        }
        if (attacker != initializedSimulator.getActiveCharacter()
                || currentTime + EPSILON >= burstActiveUntil
                || nightingalesSongStacks < 1
                || (!geoDamage && !directLunarCrystallize)) {
            return;
        }
        double ratio = directLunarCrystallize
                ? lunarSongRatio() + a4LunarRatio()
                : geoSongRatio() + a4GeoRatio();
        stats.add(StatType.FLAT_DMG_BONUS,
                currentElementalMastery(currentTime) * ratio);
        pendingSongAction = action;
        pendingSongActor = attacker;
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
                    0.0,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATION_FRAMES[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        if (normalAttackStep == 0) {
            throw new IllegalStateException(
                    "Illuga Charged Attack requires a preceding Normal Attack");
        }
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 23.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                0.0,
                null));
        normalAttackStep = 0;
        simulator.advanceTime(66.0 * FRAME);
    }

    private void skill(
            CombatSimulator simulator,
            SkillActionMode mode) {
        if (mode != SkillActionMode.PRESS
                && mode != SkillActionMode.HOLD) {
            throw new IllegalArgumentException(
                    "Illuga supports Press or Hold Skill only");
        }
        boolean hold = mode == SkillActionMode.HOLD;
        double castTime = simulator.getCurrentTime();
        activateA1(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + (hold ? 33.0 : 24.0) * FRAME,
                CommandKind.SKILL_COOLDOWN));
        queueHit(simulator, new PendingHit(
                castTime + (hold ? 36.0 : 27.0) * FRAME,
                hold ? HitKind.SKILL_HOLD : HitKind.SKILL_PRESS,
                0,
                0,
                0.0,
                null));
        simulator.advanceTime((hold ? 58.0 : 47.0) * FRAME);
    }

    private void burst(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        activateA1(castTime);
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 6.0 * FRAME,
                CommandKind.BURST_ENERGY));
        nightingalesSongStacks = (int) getTalentValue(
                "Burst Initial Stacks", 21.0);
        c2ConsumedStacks = 0;
        burstActiveUntil = castTime
                + getTalentValue("Burst Duration", 20.0);
        queueHit(simulator, new PendingHit(
                castTime + 48.0 * FRAME,
                HitKind.BURST,
                0,
                0,
                0.0,
                null));
        simulator.advanceTime(65.0 * FRAME);
    }

    private void activateA1(double currentTime) {
        a1ActiveUntil = currentTime
                + getTalentValue("A1 Duration", 20.0);
    }

    private void handleAcceptedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (simulator != initializedSimulator || damage <= 0.0) {
            return;
        }
        if (actor == this
                && action == resolvingAction
                && resolvingSkillHit
                && time + EPSILON >= nextParticleAllowedTime) {
            nextParticleAllowedTime = time
                    + getTalentValue("Particle ICD", 0.5);
            queueCommand(simulator, new PendingCommand(
                    time + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE));
        }
        if (action != pendingSongAction || actor != pendingSongActor) {
            return;
        }
        pendingSongAction = null;
        pendingSongActor = null;
        nightingalesSongStacks--;
        if (constellation < 2) {
            return;
        }
        c2ConsumedStacks++;
        int threshold = (int) getTalentValue(
                "C2 Stack Threshold", 7.0);
        if (c2ConsumedStacks < threshold) {
            return;
        }
        c2ConsumedStacks -= threshold;
        StatsContainer snapshot = captureLiveStats(time);
        double flatDamage = snapshot.get(StatType.ELEMENTAL_MASTERY)
                * getTalentValue("C2 EM Ratio", 4.0)
                + snapshot.getTotalDef()
                        * getTalentValue("C2 DEF Ratio", 2.0);
        queueHit(simulator, new PendingHit(
                time + getTalentValue(
                        "C2 Delay Frames", 50.0) * FRAME,
                HitKind.C2,
                0,
                0,
                flatDamage,
                snapshot));
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if (simulator.getEnemy() == null) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Oathkeeper's Spear N" + (hit.index + 1)
                                + (hit.variant > 0
                                        ? "-" + (hit.variant + 1) : ""),
                        NORMAL_T9[hit.index][hit.variant],
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0,
                        false,
                        false);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Oathkeeper's Spear Charged Attack",
                        getTalentValue("Charged Attack", 2.039780),
                        Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None,
                        0.0,
                        false,
                        false);
                break;
            case SKILL_PRESS:
            case SKILL_HOLD:
                resolveSkillHit(simulator, hit);
                break;
            case BURST:
                resolveBurstHit(simulator, hit);
                break;
            case C2:
                performHit(
                        simulator,
                        hit,
                        "Aedon C2 Hit",
                        0.0,
                        Element.GEO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        true,
                        false);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Illuga hit kind " + hit.kind);
        }
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            PendingHit hit) {
        boolean hold = hit.kind == HitKind.SKILL_HOLD;
        boolean c5 = constellation >= 5;
        StatsContainer snapshot = captureLiveStats(hit.time);
        double emRatio = getTalentValue(
                (hold ? "Hold" : "Press") + " EM Ratio"
                        + (c5 ? " C5" : ""),
                hold ? (c5 ? 12.064000 : 10.254400)
                        : (c5 ? 9.651200 : 8.203520));
        double defRatio = getTalentValue(
                (hold ? "Hold" : "Press") + " DEF Ratio"
                        + (c5 ? " C5" : ""),
                hold ? (c5 ? 6.032000 : 5.127200)
                        : (c5 ? 4.825600 : 4.101760));
        PendingHit resolved = hit.withDamage(
                snapshot.get(StatType.ELEMENTAL_MASTERY) * emRatio
                        + snapshot.getTotalDef() * defRatio,
                snapshot);
        performHit(
                simulator,
                resolved,
                hold ? "Dawnbearing Songbird Hold"
                        : "Dawnbearing Songbird Press",
                0.0,
                Element.GEO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0,
                true,
                true);
    }

    private void resolveBurstHit(
            CombatSimulator simulator,
            PendingHit hit) {
        boolean c3 = constellation >= 3;
        StatsContainer snapshot = captureLiveStats(hit.time);
        double emRatio = getTalentValue(
                "Burst EM Ratio" + (c3 ? " C3" : ""),
                c3 ? 16.544000 : 14.062400);
        double defRatio = getTalentValue(
                "Burst DEF Ratio" + (c3 ? " C3" : ""),
                c3 ? 8.272000 : 7.031200);
        PendingHit resolved = hit.withDamage(
                snapshot.get(StatType.ELEMENTAL_MASTERY) * emRatio
                        + snapshot.getTotalDef() * defRatio,
                snapshot);
        performHit(
                simulator,
                resolved,
                "Shadowless Reflection",
                0.0,
                Element.GEO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                1.0,
                true,
                false);
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
            double gauge,
            boolean shatter,
            boolean skillHit) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setShatterTrigger(shatter);
        if (multiplier == 0.0 && hit.additiveDamage > 0.0) {
            action.setHitEffectTrigger(true);
        }
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot.merge(null);
        snapshot.add(StatType.FLAT_DMG_BONUS, hit.additiveDamage);
        action.setStatSnapshot(snapshot);
        resolvingAction = action;
        resolvingSkillHit = skillHit;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingSkillHit = false;
        }
    }

    private double currentElementalMastery(double currentTime) {
        return captureLiveStats(currentTime).get(
                StatType.ELEMENTAL_MASTERY);
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

    private double a1CritRate() {
        return getTalentValue(
                constellation >= 6 ? "C6 CRIT Rate" : "A1 CRIT Rate",
                constellation >= 6 ? 0.10 : 0.05);
    }

    private double a1CritDamage() {
        return getTalentValue(
                constellation >= 6 ? "C6 CRIT DMG" : "A1 CRIT DMG",
                constellation >= 6 ? 0.30 : 0.10);
    }

    private double a1ElementalMastery() {
        return getTalentValue(
                constellation >= 6
                        ? "C6 Ascendant Elemental Mastery"
                        : "A1 Ascendant Elemental Mastery",
                constellation >= 6 ? 80.0 : 50.0);
    }

    private double geoSongRatio() {
        boolean c3 = constellation >= 3;
        return getTalentValue(
                "Geo Flat Bonus Ratio" + (c3 ? " C3" : ""),
                c3 ? 0.672000 : 0.571200);
    }

    private double lunarSongRatio() {
        boolean c3 = constellation >= 3;
        return getTalentValue(
                "Lunar Crystallize Flat Bonus Ratio"
                        + (c3 ? " C3" : ""),
                c3 ? 4.518400 : 3.840640);
    }

    private int hydroGeoCount() {
        if (initializedSimulator == null) {
            return 1;
        }
        int count = 0;
        for (Character member : initializedSimulator.getPartyMembers()) {
            if (member.getElement() == Element.GEO
                    || member.getElement() == Element.HYDRO) {
                count++;
            }
        }
        return Math.min(count, 3);
    }

    private double a4GeoRatio() {
        switch (hydroGeoCount()) {
            case 1:
                return getTalentValue("A4 One Geo Hydro Ratio", 0.07);
            case 2:
                return getTalentValue("A4 Two Geo Hydro Ratio", 0.14);
            default:
                return getTalentValue("A4 Three Geo Hydro Ratio", 0.24);
        }
    }

    private double a4LunarRatio() {
        switch (hydroGeoCount()) {
            case 1:
                return getTalentValue("A4 One Lunar Ratio", 0.48);
            case 2:
                return getTalentValue("A4 Two Lunar Ratio", 0.96);
            default:
                return getTalentValue("A4 Three Lunar Ratio", 1.60);
        }
    }

    private static boolean isDirectLunarCrystallize(AttackAction action) {
        return action.getLunarReactionType()
                == AttackAction.LunarReactionType.CRYSTALLIZE;
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
        long scheduledGeneration = eventGeneration;
        schedule(simulator, hit.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingHits.remove(hit)) {
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
        long scheduledGeneration = eventGeneration;
        schedule(simulator, command.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingCommands.remove(command)) {
                return;
            }
            switch (command.kind) {
                case SKILL_COOLDOWN:
                    markSkillUsed(
                            command.time,
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(command.time);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.GEO,
                                    getTalentValue(
                                            "Particle Expected Count", 4.5),
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Illuga command " + command.kind);
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
        SKILL_PRESS,
        SKILL_HOLD,
        BURST,
        C2
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final double additiveDamage;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                double additiveDamage,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.additiveDamage = additiveDamage;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit withDamage(
                double damage,
                StatsContainer stats) {
            return new PendingHit(
                    time, kind, index, variant, damage, stats);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, variant,
                    additiveDamage, snapshot);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;

        private PendingCommand(double time, CommandKind kind) {
            this.time = time;
            this.kind = kind;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind);
        }
    }

    private static final class IllugaState implements State {
        private final Illuga owner;
        private final int normalAttackStep;
        private final long eventGeneration;
        private final double a1ActiveUntil;
        private final double burstActiveUntil;
        private final double nextParticleAllowedTime;
        private final double nextC1AllowedTime;
        private final int nightingalesSongStacks;
        private final int c2ConsumedStacks;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private IllugaState(
                Illuga owner,
                int normalAttackStep,
                long eventGeneration,
                double a1ActiveUntil,
                double burstActiveUntil,
                double nextParticleAllowedTime,
                double nextC1AllowedTime,
                int nightingalesSongStacks,
                int c2ConsumedStacks,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.eventGeneration = eventGeneration;
            this.a1ActiveUntil = a1ActiveUntil;
            this.burstActiveUntil = burstActiveUntil;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.nextC1AllowedTime = nextC1AllowedTime;
            this.nightingalesSongStacks = nightingalesSongStacks;
            this.c2ConsumedStacks = c2ConsumedStacks;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }

}
