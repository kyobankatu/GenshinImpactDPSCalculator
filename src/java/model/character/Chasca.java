package model.character;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import mechanics.buff.Buff;
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
 * Chasca's fixed-target Spirit Reins and Shadowhunt offensive slice.
 *
 * <p>Bow basics, local Nightsoul drain, Multitarget Fire, deterministic
 * party-ordered Shadowhunt conversion, Soul Reaper's Fatal Round, particles,
 * A1/A4, and representable C1-C6 branches follow pinned gcsim
 * {@code ef41805d855a60b9e1035293584b85c085dc69e7}. Random PHEC selection is
 * deliberately replaced only where the source outcome is guaranteed; other
 * shell selection follows stable party order.</p>
 *
 * <p>Flight and movement, aim geometry, random targets, multi-target
 * distribution, automatic Nightsoul Burst team plumbing, hitlag extension, stamina,
 * weak points, and low Plunge are excluded rather than approximated.</p>
 */
public final class Chasca extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double[] NORMAL_T9 = {
        0.882003, 0.819183, 0.545606, 0.467885
    };
    private static final int[] NORMAL_DURATIONS = { 32, 29, 53, 62 };
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 15 }, { 8 }, { 14, 20 }, { 39 }
    };
    private static final int[] VOLLEY_LOAD_FRAMES = {
        21, 38, 56, 70, 91, 108
    };
    private static final int[] VOLLEY_HIT_FRAMES = {
        4, 7, 10, 13, 16, 19
    };
    private static final int[] BURST_SHELL_FRAMES = {
        103, 139, 147, 153, 157, 160
    };

    /**
     * Aimed-shot hitlag from gcsim config YAML pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile AIMED_HEADSHOT_HITLAG =
            new HitlagProfile(0.12, 0.01, false, true, true);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private long c6Generation;
    private boolean nightsoulActive;
    private double nightsoulPoints;
    private boolean particleGenerated;
    private double c6ReadyUntil = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Chasca. */
    public Chasca(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Chasca at an explicit constellation. */
    public Chasca(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Chasca with injectable static data.
     *
     * @param weapon equipped bow, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Chasca(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Chasca constellation must be between 0 and 6");
        }
        name = "Chasca";
        characterId = CharacterId.CHASCA;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9797.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 347.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 615.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(getTalentValue("Skill Cooldown", 6.5));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Chasca-owned delayed work to one simulator instance. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Chasca simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Chasca must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Chasca cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures all local resources and reconstructable delayed work. */
    @Override
    public State captureCharacterState() {
        return new ChascaState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                c6Generation,
                nightsoulActive,
                nightsoulPoints,
                particleGenerated,
                c6ReadyUntil,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Chasca instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof ChascaState
                && ((ChascaState) state).owner == this;
    }

    /** Restores local state and registers each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Chasca state");
        }
        initializeForSimulator(simulator);
        ChascaState restored = (ChascaState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        c6Generation = restored.c6Generation;
        nightsoulActive = restored.nightsoulActive;
        nightsoulPoints = restored.nightsoulPoints;
        particleGenerated = restored.particleGenerated;
        c6ReadyUntil = restored.c6ReadyUntil;
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

    /** Returns Chasca's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Chasca has no unconditional represented stat passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Ends Nightsoul and begins the deferred Skill cooldown on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        if (nightsoulActive) {
            endNightsoul(simulator, simulator.getCurrentTime());
        }
    }

    /** Resets Chasca's Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether local Nightsoul's Blessing is active. */
    public boolean isNightsoulActive() {
        return nightsoulActive;
    }

    /** Returns the current local Nightsoul-point balance. */
    public double getNightsoulPoints() {
        return nightsoulPoints;
    }

    /** Returns the number of unresolved Chasca-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Returns whether the next represented volley has Chasca's C6 bonus. */
    public boolean isC6VolleyReady(double currentTime) {
        return constellation >= 6
                && currentTime <= c6ReadyUntil + EPSILON;
    }

    /**
     * Represents one externally confirmed team Nightsoul Burst for A4.
     *
     * @param simulator bound simulator where the event occurred
     * @return {@code true} when an A4 shell was queued
     */
    public boolean notifyExternallyConfirmedNightsoulBurst(
            CombatSimulator simulator) {
        initializeForSimulator(simulator);
        if (simulator.getEnemy() == null) {
            return false;
        }
        List<Element> elements = uniquePhecElements(simulator);
        Element selected = elements.isEmpty()
                ? Element.ANEMO : elements.get(0);
        double multiplier = selected == Element.ANEMO
                ? skillTalentValue("Shadowhunt Shell", 0.8296, 0.976)
                : skillTalentValue(
                        "Shining Shadowhunt Shell", 2.831724, 3.33144);
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + 1.0,
                HitKind.A4_SHELL,
                selected,
                multiplier * 1.5,
                0L,
                false,
                false,
                null));
        return true;
    }

    /** Reports that automatic Nightsoul Burst team plumbing is unavailable. */
    public boolean isNightsoulBurstTeamPlumbingRepresented() {
        return false;
    }

    /** Reports that random PHEC selection is unavailable. */
    public boolean isRandomPhecSelectionRepresented() {
        return false;
    }

    /** Reports that movement and flight are unavailable. */
    public boolean isMovementAndFlightRepresented() {
        return false;
    }

    /** Reports that aim and enemy geometry are unavailable. */
    public boolean isAimGeometryRepresented() {
        return false;
    }

    /** Reports that multi-target shell distribution is unavailable. */
    public boolean isMultiTargetDistributionRepresented() {
        return false;
    }

    /** Reports that hitlag and stamina are unavailable. */
    public boolean isHitlagAndStaminaRepresented() {
        return false;
    }

    /** Dispatches Chasca's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Chasca action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Chasca supports Press Skill only");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                if (nightsoulActive) {
                    nightsoulNormal(simulator);
                } else {
                    normalAttack(simulator);
                }
                break;
            case CHARGE:
                if (nightsoulActive) {
                    shadowhuntVolley(simulator);
                } else {
                    fullyChargedAimedShot(simulator);
                }
                break;
            case PLUNGE:
                if (nightsoulActive) {
                    throw new IllegalStateException(
                            "Chasca cannot Plunge during Nightsoul");
                }
                highPlunge(simulator);
                break;
            case SKILL:
                if (nightsoulActive) {
                    endNightsoul(simulator, simulator.getCurrentTime());
                    simulator.advanceTime(frameValue(
                            "Skill Cancel Duration Frames", 40) * FRAME);
                } else {
                    enterNightsoul(simulator);
                }
                break;
            case BURST:
                fatalRound(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Chasca: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        int[] hitFrames = NORMAL_HIT_FRAMES[step];
        for (int hitIndex = 0; hitIndex < hitFrames.length; hitIndex++) {
            queueHit(simulator, new PendingHit(
                    castTime + hitFrames[hitIndex] * FRAME,
                    HitKind.NORMAL,
                    Element.PHYSICAL,
                    getTalentValue(
                            "N" + (step + 1)
                                    + (step == 2 ? " Hit" : ""),
                            NORMAL_T9[step]),
                    0L,
                    false,
                    false,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        simulator.advanceTime(frameValue(
                "N" + (step + 1) + " Duration Frames",
                NORMAL_DURATIONS[step]) * FRAME);
    }

    private void nightsoulNormal(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + frameValue(
                        "Nightsoul Normal Hit Frames", 11) * FRAME,
                HitKind.NIGHTSOUL_NORMAL,
                Element.ANEMO,
                skillTalentValue("Multitarget Fire", 0.612, 0.72),
                skillGeneration,
                false,
                false,
                null));
        simulator.advanceTime(frameValue(
                "Nightsoul Normal Duration Frames", 39) * FRAME);
    }

    private void fullyChargedAimedShot(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + frameValue("Charged Hit Frames", 86) * FRAME,
                HitKind.CHARGED,
                Element.ANEMO,
                getTalentValue("Fully-Charged Aimed Shot", 2.108),
                0L,
                false,
                false,
                null));
        simulator.advanceTime(frameValue(
                "Charged Duration Frames", 96) * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + frameValue("High Plunge Hit Frames", 45) * FRAME,
                HitKind.HIGH_PLUNGE,
                Element.PHYSICAL,
                getTalentValue("High Plunge", 2.607632),
                0L,
                false,
                false,
                null));
        simulator.advanceTime(frameValue(
                "High Plunge Duration Frames", 68) * FRAME);
    }

    private void enterNightsoul(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        nightsoulActive = true;
        nightsoulPoints = getTalentValue(
                "Nightsoul Maximum Points", 80.0);
        particleGenerated = false;
        queueHit(simulator, new PendingHit(
                castTime + frameValue("Skill Hit Frames", 3) * FRAME,
                HitKind.SKILL_INITIAL,
                Element.ANEMO,
                skillTalentValue("Resonance", 1.02, 1.2),
                generation,
                false,
                false,
                null));
        queueCommand(simulator, new PendingCommand(
                castTime + frameValue(
                        "Nightsoul Drain Interval Frames", 6) * FRAME,
                CommandKind.NIGHTSOUL_DRAIN,
                generation,
                0));
        simulator.advanceTime(frameValue(
                "Skill Duration Frames", 27) * FRAME);
    }

    private void endNightsoul(
            CombatSimulator simulator,
            double currentTime) {
        if (!nightsoulActive) {
            return;
        }
        nightsoulActive = false;
        nightsoulPoints = 0.0;
        normalAttackStep = 0;
        markSkillUsed(currentTime, simulator.getApplicableBuffs(this));
    }

    private void drainNightsoul(
            CombatSimulator simulator,
            long generation) {
        if (generation != skillGeneration || !nightsoulActive) {
            return;
        }
        nightsoulPoints = Math.max(0.0,
                nightsoulPoints - getTalentValue(
                        "Nightsoul Drain Per Tick", 0.8));
        if (nightsoulPoints <= EPSILON) {
            endNightsoul(simulator, simulator.getCurrentTime());
            return;
        }
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime()
                        + frameValue(
                                "Nightsoul Drain Interval Frames", 6)
                                * FRAME,
                CommandKind.NIGHTSOUL_DRAIN,
                generation,
                0));
    }

    private void shadowhuntVolley(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean c6Buffed = isC6VolleyReady(castTime);
        if (c6Buffed) {
            c6ReadyUntil = Double.NEGATIVE_INFINITY;
        }
        List<Element> bullets = buildVolleyElements(simulator);
        int windup = c6Buffed
                ? 0 : frameValue("Volley Windup Frames", 11);
        int loadFrames = c6Buffed
                ? 4 : frameValue("Volley Load 6 Frames", 108);
        double fireTime = castTime + (windup + loadFrames) * FRAME;
        boolean c2Available = constellation >= 2;
        boolean converted = false;
        for (int index = 0; index < bullets.size(); index++) {
            Element shotElement = bullets.get(bullets.size() - 1 - index);
            boolean shining = shotElement != Element.ANEMO;
            boolean c2Extra = c2Available && shining;
            if (c2Extra) {
                c2Available = false;
            }
            converted |= shining;
            double multiplier = shining
                    ? skillTalentValue(
                            "Shining Shadowhunt Shell", 2.831724, 3.33144)
                    : skillTalentValue(
                            "Shadowhunt Shell", 0.8296, 0.976);
            queueHit(simulator, new PendingHit(
                    fireTime + VOLLEY_HIT_FRAMES[index] * FRAME,
                    HitKind.SHADOWHUNT,
                    shotElement,
                    multiplier,
                    skillGeneration,
                    c6Buffed,
                    c2Extra,
                    null));
        }
        if (constellation >= 6 && converted) {
            c6ReadyUntil = fireTime
                    + getTalentValue("C6 Window", 3.0);
            long generation = ++c6Generation;
            queueCommand(simulator, new PendingCommand(
                    c6ReadyUntil,
                    CommandKind.C6_EXPIRE,
                    generation,
                    0));
        }
        simulator.advanceTime((windup + loadFrames
                + frameValue("Volley Recovery Frames", 19)) * FRAME);
    }

    private List<Element> buildVolleyElements(
            CombatSimulator simulator) {
        List<Element> partyElements = phecElements(simulator);
        List<Element> uniqueElements = uniquePhecElements(simulator);
        List<Element> bullets = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            bullets.add(Element.ANEMO);
        }
        boolean guaranteedA1 = uniqueElements.size() >= 3
                || constellation >= 1 && uniqueElements.size() >= 2;
        if (guaranteedA1) {
            bullets.set(2, uniqueElements.get(0));
            if (constellation >= 1) {
                bullets.set(1, uniqueElements.get(
                        Math.min(1, uniqueElements.size() - 1)));
            }
        }
        List<Element> pool = new ArrayList<>(partyElements);
        pool.addAll(partyElements);
        int cursor = 0;
        if (partyElements.size() >= 3) {
            bullets.set(3, pool.get(cursor++ % pool.size()));
        }
        if (partyElements.size() >= 2) {
            bullets.set(4, pool.get(cursor++ % pool.size()));
        }
        if (!partyElements.isEmpty()) {
            bullets.set(5, pool.get(cursor % pool.size()));
        }
        return bullets;
    }

    private void fatalRound(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + frameValue("Burst Energy Frames", 3) * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0));
        queueHit(simulator, new PendingHit(
                castTime + frameValue(
                        "Burst Initial Hit Frames", 96) * FRAME,
                HitKind.BURST_INITIAL,
                Element.ANEMO,
                burstTalentValue(
                        "Galesplitting Soulseeker Shell", 1.496, 1.76),
                generation,
                false,
                false,
                null));
        List<Element> radiant = phecElements(simulator);
        radiant.addAll(new ArrayList<>(radiant));
        boolean c4ExtraAvailable = constellation >= 4;
        for (int index = 0; index < BURST_SHELL_FRAMES.length; index++) {
            Element shellElement = index < radiant.size()
                    ? radiant.get(index) : Element.ANEMO;
            boolean converted = shellElement != Element.ANEMO;
            boolean c4Extra = c4ExtraAvailable && converted;
            if (c4Extra) {
                c4ExtraAvailable = false;
            }
            double multiplier = converted
                    ? burstTalentValue(
                            "Radiant Soulseeker Shell", 3.5156, 4.136)
                    : burstTalentValue(
                            "Soulseeker Shell", 1.7578, 2.068);
            queueHit(simulator, new PendingHit(
                    castTime + BURST_SHELL_FRAMES[index] * FRAME,
                    HitKind.BURST_SHELL,
                    shellElement,
                    multiplier,
                    generation,
                    false,
                    c4Extra,
                    null));
        }
        simulator.advanceTime(frameValue(
                "Burst Duration Frames", 114) * FRAME);
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.kind.isSkillOwned()
                && hit.generation != skillGeneration) {
            return;
        }
        if (hit.kind.isBurstOwned()
                && hit.generation != burstGeneration) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                performHit(simulator, hit,
                        "Phantom Feather Flurry",
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.None,
                        ICDTag.None);
                break;
            case NIGHTSOUL_NORMAL:
                performHit(simulator, hit,
                        "Multitarget Fire",
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.ChascaAlternating,
                        ICDTag.Chasca_Tap);
                break;
            case CHARGED:
                performHit(simulator, hit,
                        "Fully-Charged Aimed Shot",
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None);
                break;
            case HIGH_PLUNGE:
                performHit(simulator, hit,
                        "Phantom Feather Flurry High Plunge",
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None);
                break;
            case SKILL_INITIAL:
                performHit(simulator, hit,
                        "Spirit Reins, Shadow Hunt",
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None);
                break;
            case SHADOWHUNT:
                resolveShadowhunt(simulator, hit);
                break;
            case BURST_INITIAL:
                performHit(simulator, hit,
                        "Galesplitting Soulseeker Shell",
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None);
                break;
            case BURST_SHELL:
                resolveBurstShell(simulator, hit);
                break;
            case A4_SHELL:
                performHit(simulator, hit,
                        hit.element == Element.ANEMO
                                ? "Burning Shadowhunt Shell"
                                : "Burning Shining Shadowhunt Shell",
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Chasca hit kind " + hit.kind);
        }
    }

    private void resolveShadowhunt(
            CombatSimulator simulator,
            PendingHit hit) {
        boolean shining = hit.element != Element.ANEMO;
        performHit(simulator, hit,
                shining
                        ? "Shining Shadowhunt Shell"
                        : "Shadowhunt Shell",
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.ChascaAlternating,
                shining
                        ? ICDTag.Chasca_Shining
                        : ICDTag.Chasca_Shadowhunt);
        if (!particleGenerated && simulator.getEnemy() != null) {
            particleGenerated = true;
            queueCommand(simulator, new PendingCommand(
                    hit.time
                            + frameValue(
                                    "Particle Travel Frames", 100) * FRAME,
                    CommandKind.PARTICLE,
                    0L,
                    frameValue("Particle Count", 5)));
        }
        if (hit.extra) {
            queueHit(simulator, new PendingHit(
                    hit.time + FRAME,
                    HitKind.C2_EXTRA,
                    hit.element,
                    getTalentValue("C2 Additional Multiplier", 4.0),
                    hit.generation,
                    false,
                    false,
                    hit.snapshot));
        }
    }

    private void resolveBurstShell(
            CombatSimulator simulator,
            PendingHit hit) {
        boolean radiant = hit.element != Element.ANEMO;
        performHit(simulator, hit,
                radiant
                        ? "Radiant Soulseeker Shell"
                        : "Soulseeker Shell",
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.ChascaAlternating,
                ICDTag.Chasca_Burst);
        if (radiant && constellation >= 4) {
            receiveFlatEnergy(getTalentValue(
                    "C4 Energy Per Radiant Shell", 1.5));
        }
        if (hit.extra) {
            queueHit(simulator, new PendingHit(
                    hit.time + FRAME,
                    HitKind.C4_EXTRA,
                    hit.element,
                    getTalentValue("C4 Additional Multiplier", 4.0),
                    hit.generation,
                    false,
                    false,
                    hit.snapshot));
        }
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag) {
        AttackAction action = new AttackAction(
                displayName,
                hit.multiplier,
                hit.element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, 1.0);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (hit.kind == HitKind.CHARGED) {
            action.setHitlagProfile(AIMED_HEADSHOT_HITLAG);
        }
        if (hit.element != Element.ANEMO
                && hit.kind == HitKind.SHADOWHUNT) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    a1ShiningBonus(simulator));
        }
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot.merge(null);
        if (hit.c6Buffed) {
            snapshot.add(StatType.CRIT_DMG,
                    getTalentValue("C6 CRIT DMG", 1.2));
        }
        action.setStatSnapshot(snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private double a1ShiningBonus(CombatSimulator simulator) {
        int stacks = uniquePhecElements(simulator).size();
        if (constellation >= 2) {
            stacks++;
        }
        if (stacks <= 0) {
            return 0.0;
        }
        if (stacks == 1) {
            return getTalentValue("A1 Shining Bonus 1", 0.15);
        }
        if (stacks == 2) {
            return getTalentValue("A1 Shining Bonus 2", 0.35);
        }
        return getTalentValue("A1 Shining Bonus 3", 0.65);
    }

    private List<Element> phecElements(CombatSimulator simulator) {
        List<Element> elements = new ArrayList<>();
        for (Character member : simulator.getPartyMembers()) {
            if (member == this) {
                continue;
            }
            Element memberElement = member.getElement();
            if (isPhec(memberElement)) {
                elements.add(memberElement);
            }
        }
        return elements;
    }

    private List<Element> uniquePhecElements(
            CombatSimulator simulator) {
        Set<Element> unique = new LinkedHashSet<>(phecElements(simulator));
        return new ArrayList<>(unique);
    }

    private static boolean isPhec(Element element) {
        return element == Element.PYRO
                || element == Element.HYDRO
                || element == Element.ELECTRO
                || element == Element.CRYO;
    }

    private double skillTalentValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue(
                key + suffix,
                constellation >= 3 ? talentTwelve : talentNine);
    }

    private double burstTalentValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(
                key + suffix,
                constellation >= 5 ? talentTwelve : talentNine);
    }

    private int frameValue(String key, int defaultValue) {
        return (int) Math.round(getTalentValue(key, defaultValue));
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
            if (hit.kind == HitKind.C2_EXTRA) {
                if (hit.generation != skillGeneration) {
                    return;
                }
                performHit(activeSimulator, hit,
                        "Shining Shadowhunt Shell C2",
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None);
            } else if (hit.kind == HitKind.C4_EXTRA) {
                if (hit.generation != burstGeneration) {
                    return;
                }
                performHit(activeSimulator, hit,
                        "Radiant Soulseeker Shell C4",
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None);
            } else {
                resolveHit(activeSimulator, hit);
            }
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
                case NIGHTSOUL_DRAIN:
                    drainNightsoul(activeSimulator, command.generation);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.ANEMO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(command.time);
                    }
                    break;
                case C6_EXPIRE:
                    if (command.generation == c6Generation) {
                        c6ReadyUntil = Double.NEGATIVE_INFINITY;
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Chasca command kind " + command.kind);
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
        NIGHTSOUL_NORMAL,
        CHARGED,
        HIGH_PLUNGE,
        SKILL_INITIAL,
        SHADOWHUNT,
        BURST_INITIAL,
        BURST_SHELL,
        A4_SHELL,
        C2_EXTRA,
        C4_EXTRA;

        private boolean isSkillOwned() {
            return this == NIGHTSOUL_NORMAL
                    || this == SKILL_INITIAL
                    || this == SHADOWHUNT
                    || this == C2_EXTRA;
        }

        private boolean isBurstOwned() {
            return this == BURST_INITIAL
                    || this == BURST_SHELL
                    || this == C4_EXTRA;
        }
    }

    private enum CommandKind {
        NIGHTSOUL_DRAIN,
        PARTICLE,
        BURST_ENERGY,
        C6_EXPIRE
    }

    /** Immutable queued impact with source generation and optional snapshot. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final Element element;
        private final double multiplier;
        private final long generation;
        private final boolean c6Buffed;
        private final boolean extra;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                Element element,
                double multiplier,
                long generation,
                boolean c6Buffed,
                boolean extra,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.element = element;
            this.multiplier = multiplier;
            this.generation = generation;
            this.c6Buffed = c6Buffed;
            this.extra = extra;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    element,
                    multiplier,
                    generation,
                    c6Buffed,
                    extra,
                    snapshot);
        }
    }

    /** Immutable delayed state command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final int value;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                int value) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, generation, value);
        }
    }

    /** Immutable snapshot of all Chasca-owned mutable runtime state. */
    private static final class ChascaState implements State {
        private final Chasca owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final long c6Generation;
        private final boolean nightsoulActive;
        private final double nightsoulPoints;
        private final boolean particleGenerated;
        private final double c6ReadyUntil;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private ChascaState(
                Chasca owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                long c6Generation,
                boolean nightsoulActive,
                double nightsoulPoints,
                boolean particleGenerated,
                double c6ReadyUntil,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.c6Generation = c6Generation;
            this.nightsoulActive = nightsoulActive;
            this.nightsoulPoints = nightsoulPoints;
            this.particleGenerated = particleGenerated;
            this.c6ReadyUntil = c6ReadyUntil;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}
