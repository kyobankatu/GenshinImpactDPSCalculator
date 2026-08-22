package model.character;

import java.util.function.Consumer;

import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedCharacterEffect;
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
import simulation.event.SimpleTimerEvent;

/**
 * Ningguang's legacy offensive kit for stationary single-target combat.
 *
 * <p>
 * Sparkling Scatter cycles deterministically through the current gcsim Left,
 * Right, and Twirl releases. Both projectiles together grant one Star Jade,
 * capped at three, while the next Charged Attack consumes all owned Jades.
 * Jade Screen persists for 30 seconds and contributes six idealized Starshatter
 * gems at frame 154 when consumed.
 *
 * <p>
 * C1, C2, C3, C5, and C6 are represented. Stamina, A4 Screen traversal,
 * defensive C4, projectile tracking failures, construct collision/health,
 * random animation selection, multi-target behavior, and pending-event
 * snapshot restore are intentionally excluded. C1 has no single-target damage
 * difference. Screen-gem timing is a documented stationary-target idealization
 * because the maintained reference leaves those hitmarks unresolved.
 */
public class Ningguang extends Character implements
        SimulatorInitializedCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double PROJECTILE_TRAVEL = 10.0 * FRAME;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double SKILL_COOLDOWN = 12.0;
    private static final double BURST_COOLDOWN = 12.0;
    private static final double SCREEN_DURATION = 30.0;
    private static final double PARTICLE_COOLDOWN = 6.0;
    private static final double C2_RESET_COOLDOWN = 6.0;
    private static final int MAX_STAR_JADES = 3;
    private static final int C6_STAR_JADES = 7;

    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile STAR_JADE_HITLAG =
            new HitlagProfile(0.0, 0.0, true, true, false);
    private static final HitlagProfile SKILL_HITLAG =
            new HitlagProfile(0.05, 0.05, true, true, false);
    private static final HitlagProfile BURST_GEM_HITLAG =
            new HitlagProfile(0.0, 0.0, true, true, false);

    private static final int[] NORMAL_RELEASE_FRAMES = { 29, 19, 27 };
    private static final int[] NORMAL_ACTION_FRAMES = { 61, 56, 66 };
    private static final int[] CHARGED_RELEASE_FRAMES = { 35, 58, 66 };
    private static final int[] JADE_RELEASE_FRAMES = { 45, 66, 74 };
    private static final int[] C6_JADE_RELEASE_FRAMES = { 40, 58, 67 };
    private static final int[] CHARGED_ACTION_FRAMES = { 52, 74, 82 };
    private static final int[] BURST_HIT_FRAMES = {
            62, 97, 106, 110, 116, 124
    };

    private CombatSimulator initializedSimulator;
    private int starJades;
    private int normalVariant;
    private int lastNormalVariant;
    private boolean screenActive;
    private double screenExpiresAt = Double.NEGATIVE_INFINITY;
    private StatsContainer screenSnapshot;
    private long screenGeneration;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private double nextC2ResetTime = Double.NEGATIVE_INFINITY;

    /** Constructs the repository-default C6 Ningguang. */
    public Ningguang(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Ningguang at an explicit constellation. */
    public Ningguang(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Ningguang with injectable talent data and constellation state.
     *
     * @param weapon equipped weapon
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Ningguang(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Ningguang constellation must be between 0 and 6");
        }
        this.name = "Ningguang";
        this.characterId = CharacterId.NINGGUANG;
        this.element = Element.GEO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9787.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 212.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 573.0));
        baseStats.add(StatType.GEO_DMG_BONUS,
                getTalentValue("Ascension Geo DMG", 0.24));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds local construct and projectile state to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Ningguang cannot be reused across CombatSimulator instances");
        }
        initializedSimulator = sim;
    }

    /** Returns Ningguang's 40-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 40.0);
    }

    /** Ningguang has no unconditional offensive passive in this slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A1 affects stamina and A4 requires Screen traversal geometry.
    }

    /** Returns the number of currently owned Star Jades. */
    public int getStarJadeCount() {
        return starJades;
    }

    /** Returns whether Jade Screen remains active. */
    public boolean isJadeScreenActive(double currentTime) {
        expireScreenIfNeeded(currentTime);
        return screenActive;
    }

    /** Clears Star Jades when Ningguang leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator sim) {
        starJades = 0;
    }

    /** Dispatches Ningguang's supported typed offensive actions. */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        initializeForSimulator(sim);
        expireScreenIfNeeded(sim.getCurrentTime());
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(sim);
                break;
            case CHARGE:
                chargedAttack(sim);
                break;
            case PLUNGE:
                highPlunge(sim);
                break;
            case SKILL:
                jadeScreen(sim);
                break;
            case BURST:
                starshatter(sim);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Ningguang: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        int variant = normalVariant;
        lastNormalVariant = variant;
        normalVariant = (normalVariant + 1) % NORMAL_RELEASE_FRAMES.length;
        AttackAction projectile = attack(
                "Sparkling Scatter Normal " + variantName(variant),
                getTalentValue("N1", 0.476),
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0,
                false);
        schedule(sim,
                castTime + NORMAL_RELEASE_FRAMES[variant] * FRAME
                        + PROJECTILE_TRAVEL,
                activeSim -> {
                    activeSim.performActionWithoutTimeAdvance(
                            characterId, projectile);
                    activeSim.performActionWithoutTimeAdvance(
                            characterId, projectile);
                    if (activeSim.getActiveCharacter() == Ningguang.this
                            && starJades != C6_STAR_JADES) {
                        starJades = Math.min(MAX_STAR_JADES, starJades + 1);
                    }
                });
        sim.advanceTime(NORMAL_ACTION_FRAMES[variant] * FRAME);
    }

    private void chargedAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        int variant = chargedVariant(lastNormalVariant);
        int ownedJades = starJades;
        starJades = 0;

        AttackAction charged = attack(
                "Sparkling Scatter Charged " + variantName(variant),
                getTalentValue("Charged Attack", 2.95936),
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.ChargedAttack,
                1.0,
                false);
        schedule(sim,
                castTime + CHARGED_RELEASE_FRAMES[variant] * FRAME
                        + PROJECTILE_TRAVEL,
                activeSim -> activeSim.performActionWithoutTimeAdvance(
                        characterId, charged));

        int jadeFrame = ownedJades == C6_STAR_JADES
                ? C6_JADE_RELEASE_FRAMES[variant]
                : JADE_RELEASE_FRAMES[variant];
        for (int jade = 0; jade < ownedJades; jade++) {
            AttackAction starJade = attack(
                    "Sparkling Scatter Star Jade " + (jade + 1),
                    getTalentValue("Star Jade", 0.8432),
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    ActionType.CHARGE,
                    ICDType.Standard,
                    ICDTag.ChargedAttack,
                    2.0,
                    false);
            starJade.setHitlagProfile(STAR_JADE_HITLAG);
            schedule(sim,
                    castTime + jadeFrame * FRAME + PROJECTILE_TRAVEL,
                    activeSim -> activeSim.performActionWithoutTimeAdvance(
                            characterId, starJade));
        }
        sim.advanceTime(CHARGED_ACTION_FRAMES[variant] * FRAME);
    }

    private void highPlunge(CombatSimulator sim) {
        AttackAction plunge = attack(
                "Sparkling Scatter High Plunge",
                getTalentValue("Plunge High", 2.6076),
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                1.0,
                false);
        plunge.setAnimationDuration(75.0 * FRAME);
        sim.performAction(characterId, plunge);
    }

    private void jadeScreen(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        boolean replacedScreen = screenActive;
        markSkillUsed(castTime, sim.getApplicableBuffs(this));
        if (replacedScreen) {
            destroyScreen(sim, castTime);
        }
        activateScreen(sim, castTime);

        AttackAction screen = attack(
                "Jade Screen",
                getTalentValue(
                        constellation >= 5 ? "Skill C5" : "Skill",
                        constellation >= 5 ? 4.608 : 3.9168),
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0,
                true);
        screen.setHitlagProfile(SKILL_HITLAG);
        schedule(sim, castTime + 17.0 * FRAME, activeSim -> {
            captureSnapshot(
                    activeSim.getCurrentTime(),
                    activeSim.getApplicableBuffs(this));
            screenSnapshot = getSnapshot().merge(null);
            activeSim.performActionWithoutTimeAdvance(characterId, screen);
            if (activeSim.getCurrentTime() >= nextParticleTime) {
                nextParticleTime = activeSim.getCurrentTime()
                        + PARTICLE_COOLDOWN;
                schedule(activeSim,
                        activeSim.getCurrentTime() + PARTICLE_TRAVEL,
                        particleSim -> particleSim.getEnergyDistributor()
                                .distributeParticles(
                                        Element.GEO,
                                        3.4,
                                        ParticleType.PARTICLE));
            }
        });
        sim.advanceTime(62.0 * FRAME);
    }

    private void starshatter(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        markBurstUsed(castTime, sim.getApplicableBuffs(this));
        boolean consumedScreen = screenActive;
        StatsContainer consumedScreenSnapshot = consumedScreen
                && screenSnapshot != null
                        ? screenSnapshot.merge(null)
                        : null;
        if (consumedScreen) {
            destroyScreen(sim, castTime);
        }
        captureSnapshot(castTime, sim.getApplicableBuffs(this));
        StatsContainer burstSnapshot = getSnapshot().merge(null);
        boolean c3 = constellation >= 3;
        double multiplier = getTalentValue(
                c3 ? "Burst C3" : "Burst",
                c3 ? 1.7392 : 1.47832);
        for (int gem = 0; gem < BURST_HIT_FRAMES.length; gem++) {
            AttackAction burstGem = attack(
                    "Starshatter Gem " + (gem + 1),
                    multiplier,
                    StatType.BURST_DMG_BONUS,
                    ActionType.BURST,
                    ICDType.Standard,
                    ICDTag.ElementalBurst,
                    2.0,
                    true);
            burstGem.setStatSnapshot(burstSnapshot);
            burstGem.setHitlagProfile(BURST_GEM_HITLAG);
            schedule(sim,
                    castTime + BURST_HIT_FRAMES[gem] * FRAME,
                    activeSim -> activeSim.performActionWithoutTimeAdvance(
                            characterId, burstGem));
        }
        if (consumedScreen) {
            for (int gem = 0; gem < 6; gem++) {
                AttackAction screenGem = attack(
                        "Starshatter Jade Screen Gem " + (gem + 1),
                        multiplier,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        2.0,
                        true);
                screenGem.setStatSnapshot(consumedScreenSnapshot);
                screenGem.setHitlagProfile(BURST_GEM_HITLAG);
                schedule(sim, castTime + 154.0 * FRAME,
                        activeSim -> activeSim.performActionWithoutTimeAdvance(
                                characterId, screenGem));
            }
        }
        if (constellation >= 6) {
            starJades = C6_STAR_JADES;
        }
        sim.advanceTime(127.0 * FRAME);
    }

    private void activateScreen(CombatSimulator sim, double currentTime) {
        screenActive = true;
        screenExpiresAt = currentTime + SCREEN_DURATION;
        long generation = ++screenGeneration;
        schedule(sim, screenExpiresAt, activeSim -> {
            if (generation == screenGeneration && screenActive) {
                destroyScreen(activeSim, activeSim.getCurrentTime());
            }
        });
    }

    private void destroyScreen(
            CombatSimulator sim,
            double currentTime) {
        if (!screenActive) {
            return;
        }
        screenActive = false;
        screenExpiresAt = Double.NEGATIVE_INFINITY;
        screenGeneration++;
        if (constellation >= 2
                && currentTime >= nextC2ResetTime
                && getSkillCDRemaining(currentTime) > 0.0) {
            resetSkillCooldown(currentTime);
            nextC2ResetTime = currentTime + C2_RESET_COOLDOWN;
        }
    }

    private void expireScreenIfNeeded(double currentTime) {
        if (screenActive && currentTime >= screenExpiresAt) {
            screenActive = false;
            screenExpiresAt = Double.NEGATIVE_INFINITY;
            screenGeneration++;
        }
    }

    private static AttackAction attack(
            String actionName,
            double multiplier,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            boolean snapshot) {
        AttackAction action = new AttackAction(
                actionName,
                multiplier,
                Element.GEO,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                snapshot,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setShatterTrigger(true);
        return action;
    }

    private static int chargedVariant(int previousVariant) {
        return previousVariant == 1 ? 1 : 0;
    }

    private static String variantName(int variant) {
        switch (variant) {
            case 0:
                return "Left";
            case 1:
                return "Right";
            case 2:
                return "Twirl";
            default:
                throw new IllegalArgumentException(
                        "Unknown Ningguang animation variant: " + variant);
        }
    }

    private static void schedule(
            CombatSimulator sim,
            double time,
            Consumer<CombatSimulator> effect) {
        sim.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                effect.accept(activeSim);
            }
        });
    }
}
