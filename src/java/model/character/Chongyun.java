package model.character;

import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.FormStateProvider;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.event.SimpleTimerEvent;

/**
 * Chongyun's offensive mechanics for stationary single-target combat.
 *
 * <p>The implementation follows the fixed KQM TCL and gcsim evidence selected
 * for B-165. It represents Chongyun's four-hit Normal string, steady claymore
 * Charged hit, high Plunge, Layered Frost, Spirit Blade field, Cloud-Parting
 * Star, deterministic particles, and the representable effects of A1, A4, and
 * C1-C6. The delayed A4 blade owns an independent cast-time snapshot while
 * the initial Skill hit resolves dynamically at impact.
 *
 * <p>The shared action model cannot rewrite another character's already-built
 * attack element. The field therefore grants eligible teammates' Normal Attack
 * speed and C2 cooldown reduction, while exact Cryo infusion is applied to
 * Chongyun's own eligible attacks. Geometry, displacement, healing, stamina,
 * hitlag, and C6's target-versus-player HP comparison are intentionally out of
 * scope.
 */
public class Chongyun extends Character implements
        FormStateProvider,
        SimulatorInitializedCharacterEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double SKILL_COOLDOWN = 15.0;
    private static final double BURST_COOLDOWN = 12.0;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double FIELD_DURATION = 10.0;
    private static final double A4_DELAY = 655.0 * FRAME;
    private static final double A4_RES_DURATION = 8.0;

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long fieldGeneration;

    /** Constructs the repository-default C6 Chongyun. */
    public Chongyun(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /**
     * Constructs Chongyun at an explicit constellation.
     *
     * @param weapon equipped claymore
     * @param artifacts equipped artifact set
     * @param constellation constellation in the inclusive range 0-6
     */
    public Chongyun(
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
     * Constructs Chongyun with injectable talent data.
     *
     * @param weapon equipped claymore
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Chongyun(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Chongyun constellation must be between 0 and 6");
        }
        this.name = "Chongyun";
        this.characterId = CharacterId.CHONGYUN;
        this.element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(
                StatType.BASE_HP,
                getTalentValue("Base HP", 10984.0));
        baseStats.set(
                StatType.BASE_ATK,
                getTalentValue("Base ATK", 223.0));
        baseStats.set(
                StatType.BASE_DEF,
                getTalentValue("Base DEF", 648.0));
        baseStats.add(
                StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK", 0.24));
        setSkillCD(SKILL_COOLDOWN);
        setBurstCD(BURST_COOLDOWN);
    }

    /** Binds Chongyun's hit listener and mutable field state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator sim) {
        if (initializedSimulator != null && initializedSimulator != sim) {
            throw new IllegalStateException(
                    "Chongyun cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == sim) {
            return;
        }
        initializedSimulator = sim;
        sim.addDamageListener((actor, action, damage, time) -> {
            if (constellation >= 4
                    && actor == Chongyun.this
                    && sim.getActiveCharacter() == Chongyun.this
                    && sim.getEnemy() != null
                    && sim.getEnemy().getAuraUnits(Element.CRYO, time) > 0.0
                    && findActiveBuff(C4CooldownBuff.class, time) == null) {
                receiveFlatEnergy(getTalentValue("C4 Energy", 1.0));
                removeBuffType(C4CooldownBuff.class);
                addBuff(new C4CooldownBuff(
                        getTalentValue("C4 Cooldown", 2.0), time));
            }
        });
    }

    /** Returns Chongyun's 40-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 40.0);
    }

    /** Chongyun has no unconditional static offensive passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // The modeled passives are field- or hit-dependent.
    }

    /** Reports whether the current Spirit Blade field is active. */
    @Override
    public boolean isFormActive(double currentTime) {
        return findActiveBuff(FieldStateBuff.class, currentTime) != null;
    }

    /** Dispatches Chongyun's supported offensive actions. */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        initializeForSimulator(sim);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(sim);
                break;
            case CHARGE:
                chargedSpin(sim);
                break;
            case PLUNGE:
                highPlunge(sim);
                break;
            case SKILL:
                layeredFrost(sim);
                break;
            case BURST:
                cloudPartingStar(sim);
                break;
            case DASH:
                sim.advanceTime(21.0 * FRAME);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Chongyun: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        double[] defaults = { 1.28612, 1.15972, 1.47572, 1.85966 };
        int[] hitmarks = { 26, 24, 41, 53 };
        int[] durations = { 30, 36, 57, 101 };
        int step = normalAttackStep;
        double attackSpeed = normalAttackSpeed(sim, castTime);
        schedule(
                sim,
                castTime + hitmarks[step] * FRAME,
                activeSim -> {
                    Element attackElement = infusedElement(
                            activeSim.getCurrentTime());
                    AttackAction normal = attack(
                            "Demonbane N" + (step + 1),
                            getTalentValue(
                                    "N" + (step + 1), defaults[step]),
                            attackElement,
                            StatType.NORMAL_ATTACK_DMG_BONUS,
                            ActionType.NORMAL,
                            ICDType.Standard,
                            ICDTag.NormalAttack,
                            attackElement == Element.CRYO ? 1.0 : 0.0,
                            true);
                    normal.setStatSnapshot(captureActionStats(
                            activeSim,
                            activeSim.getCurrentTime()));
                    activeSim.performActionWithoutTimeAdvance(
                            characterId, normal);
                    if (step == 3 && constellation >= 1) {
                        scheduleC1Blades(activeSim);
                    }
        });
        normalAttackStep = (normalAttackStep + 1) % defaults.length;
        sim.advanceTime(adjustAttackFrames(
                durations[step], attackSpeed) * FRAME);
    }

    private void scheduleC1Blades(CombatSimulator sim) {
        double impactTime = sim.getCurrentTime();
        for (int blade = 0; blade < 3; blade++) {
            int bladeIndex = blade;
            schedule(sim, impactTime + blade * 5.0 * FRAME, activeSim -> {
                AttackAction action = attack(
                        "Ice Unleashed Blade " + (bladeIndex + 1),
                        getTalentValue("C1 Ice Blade", 0.50),
                        Element.CRYO,
                        null,
                        ActionType.OTHER,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        false);
                activeSim.performActionWithoutTimeAdvance(
                        characterId, action);
            });
        }
    }

    private void chargedSpin(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        Element attackElement = infusedElement(castTime);
        AttackAction charged = attack(
                "Demonbane Charged Spin",
                getTalentValue("Charged Spin", 1.0341),
                attackElement,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.NormalAttack,
                attackElement == Element.CRYO ? 1.0 : 0.0,
                true);
        charged.setAnimationDuration(23.0 * FRAME);
        sim.performAction(characterId, charged);
    }

    private void highPlunge(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        schedule(
                sim,
                castTime + 47.0 * FRAME,
                activeSim -> {
                    Element attackElement = infusedElement(
                            activeSim.getCurrentTime());
                    AttackAction plunge = attack(
                            "Demonbane High Plunge",
                            getTalentValue("Plunge High", 3.422517),
                            attackElement,
                            StatType.PLUNGING_ATTACK_DMG_BONUS,
                            ActionType.PLUNGE,
                            ICDType.None,
                            ICDTag.PlungeAttack,
                            attackElement == Element.CRYO ? 1.0 : 0.0,
                            true);
                    plunge.setStatSnapshot(captureActionStats(
                            activeSim,
                            activeSim.getCurrentTime()));
                    activeSim.performActionWithoutTimeAdvance(
                            characterId, plunge);
                });
        sim.advanceTime(87.0 * FRAME);
    }

    private void layeredFrost(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        double multiplier = getSkillMultiplier();
        StatsContainer a4Snapshot = captureActionStats(sim, castTime);
        FieldStateBuff replacedField = findActiveBuff(
                FieldStateBuff.class, castTime);
        long generation = ++fieldGeneration;
        if (replacedField != null) {
            schedule(
                    sim,
                    castTime + 81.0 * FRAME,
                    activeSim -> resolveA4Blade(
                            activeSim,
                            replacedField.getMultiplier(),
                            replacedField.getA4Snapshot()));
        }
        AttackAction skill = attack(
                "Spirit Blade: Chonghua's Layered Frost",
                multiplier,
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                2.0,
                true);
        skill.setStatSnapshot(a4Snapshot);

        double fieldStart = castTime + 36.0 * FRAME;
        schedule(sim, castTime + 34.0 * FRAME, activeSim ->
                markSkillUsed(
                        activeSim.getCurrentTime(),
                        activeSim.getApplicableBuffs(this)));
        schedule(sim, fieldStart, activeSim -> {
            activeSim.performActionWithoutTimeAdvance(characterId, skill);
            removeBuffType(FieldStateBuff.class);
            addBuff(new FieldStateBuff(
                    getTalentValue("Field Duration", FIELD_DURATION),
                    activeSim.getCurrentTime(),
                    generation,
                    multiplier,
                    a4Snapshot));
            if (activeSim.getEnemy() != null) {
                schedule(
                        activeSim,
                        activeSim.getCurrentTime() + PARTICLE_TRAVEL,
                        particleSim -> particleSim.getEnergyDistributor()
                                .distributeParticles(
                                        Element.CRYO,
                                        getTalentValue(
                                                "Skill Particles", 4.0),
                                        ParticleType.PARTICLE));
            }
        });
        scheduleFieldTicks(sim, fieldStart, generation);
        scheduleA4Blade(
                sim, castTime, generation, multiplier, a4Snapshot);
        sim.advanceTime(52.0 * FRAME);
    }

    private double getSkillMultiplier() {
        if (constellation >= 5) {
            return getTalentValue("Layered Frost C5", 3.4408);
        }
        return getTalentValue("Layered Frost", 2.92468);
    }

    private void scheduleFieldTicks(
            CombatSimulator sim,
            double fieldStart,
            long generation) {
        for (int second = 0; second <= 10; second++) {
            schedule(
                    sim,
                    fieldStart + second,
                    activeSim -> {
                        if (generation == fieldGeneration) {
                            applyFieldToActiveCharacter(activeSim);
                        }
                    });
        }
    }

    private void applyFieldToActiveCharacter(CombatSimulator sim) {
        Character recipient = sim.getActiveCharacter();
        if (recipient == null) {
            return;
        }
        recipient.getActiveBuffs().removeIf(
                buff -> buff instanceof FrostFieldRecipientBuff
                        && buff.getSourceCharacterId()
                                == CharacterId.CHONGYUN);
        boolean weaponEligible = recipient == this
                || isInfusionWeapon(recipient.getWeapon());
        double duration = getTalentValue(
                constellation >= 5
                        ? "Infusion Duration C5"
                        : "Infusion Duration",
                constellation >= 5 ? 3.0 : 2.8);
        FrostFieldRecipientBuff buff = new FrostFieldRecipientBuff(
                duration,
                sim.getCurrentTime(),
                weaponEligible,
                constellation >= 2);
        buff.sourcedBy(characterId);
        recipient.addBuff(buff);
    }

    private void scheduleA4Blade(
            CombatSimulator sim,
            double castTime,
            long generation,
            double multiplier,
            StatsContainer snapshot) {
        schedule(sim, castTime + A4_DELAY, activeSim -> {
            if (generation != fieldGeneration) {
                return;
            }
            resolveA4Blade(activeSim, multiplier, snapshot);
        });
    }

    private void resolveA4Blade(
            CombatSimulator sim,
            double multiplier,
            StatsContainer snapshot) {
        AttackAction blade = attack(
                "Rimechaser Blade",
                multiplier,
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0,
                true);
        blade.setStatSnapshot(snapshot);
        sim.performActionWithoutTimeAdvance(characterId, blade);
        replaceA4ResistanceShred(sim);
    }

    private void replaceA4ResistanceShred(CombatSimulator sim) {
        sim.getTeamBuffList().removeIf(
                buff -> buff instanceof RimechaserResistanceBuff
                        && buff.getSourceCharacterId()
                                == CharacterId.CHONGYUN);
        RimechaserResistanceBuff shred = new RimechaserResistanceBuff(
                getTalentValue(
                        "A4 RES Shred Duration", A4_RES_DURATION),
                sim.getCurrentTime(),
                getTalentValue("A4 Cryo RES Shred", 0.10));
        shred.sourcedBy(characterId);
        sim.applyTeamBuff(shred);
    }

    private void cloudPartingStar(CombatSimulator sim) {
        double castTime = sim.getCurrentTime();
        markBurstCooldownUsed(castTime, sim.getApplicableBuffs(this));
        schedule(sim, castTime + 6.0 * FRAME, activeSim ->
                spendBurstEnergy(activeSim.getCurrentTime()));
        int[] hitmarks = constellation >= 6
                ? new int[] { 50, 59, 67, 77 }
                : new int[] { 50, 59, 67 };
        for (int blade = 0; blade < hitmarks.length; blade++) {
            int bladeIndex = blade;
            schedule(
                    sim,
                    castTime + hitmarks[blade] * FRAME,
                    activeSim -> {
                        AttackAction burst = attack(
                                "Cloud-Parting Star Blade "
                                        + (bladeIndex + 1),
                                getBurstMultiplier(),
                                Element.CRYO,
                                StatType.BURST_DMG_BONUS,
                                ActionType.BURST,
                                ICDType.None,
                                ICDTag.ElementalBurst,
                                1.0,
                                true);
                        activeSim.performActionWithoutTimeAdvance(
                                characterId, burst);
                    });
        }
        sim.advanceTime(79.0 * FRAME);
    }

    private double getBurstMultiplier() {
        if (constellation >= 3) {
            return getTalentValue("Cloud-Parting Star C3", 2.8480);
        }
        return getTalentValue("Cloud-Parting Star", 2.4208);
    }

    private Element infusedElement(double currentTime) {
        FrostFieldRecipientBuff buff = findActiveFieldBuff(currentTime);
        if (buff != null && buff.isInfusionEligible()) {
            return Element.CRYO;
        }
        return Element.PHYSICAL;
    }

    private FrostFieldRecipientBuff findActiveFieldBuff(
            double currentTime) {
        for (Buff buff : getActiveBuffs()) {
            if (buff instanceof FrostFieldRecipientBuff
                    && buff.getSourceCharacterId()
                            == CharacterId.CHONGYUN
                    && !buff.isExpired(currentTime)) {
                return (FrostFieldRecipientBuff) buff;
            }
        }
        return null;
    }

    private <T extends Buff> T findActiveBuff(
            Class<T> buffType,
            double currentTime) {
        for (Buff buff : getActiveBuffs()) {
            if (buffType.isInstance(buff) && !buff.isExpired(currentTime)) {
                return buffType.cast(buff);
            }
        }
        return null;
    }

    private void removeBuffType(Class<? extends Buff> buffType) {
        getActiveBuffs().removeIf(buffType::isInstance);
    }

    private double normalAttackSpeed(
            CombatSimulator sim,
            double currentTime) {
        StatsContainer stats = captureActionStats(sim, currentTime);
        double speed = Math.min(
                0.60,
                stats.get(StatType.ATK_SPD)
                        + stats.get(StatType.NORMAL_ATTACK_SPD));
        return Math.max(0.0, speed);
    }

    private static int adjustAttackFrames(
            int frames,
            double attackSpeed) {
        double effectiveSpeed = Math.min(
                attackSpeed,
                0.1 + (attackSpeed - 0.1) / 2.0);
        return frames - (int) (effectiveSpeed * frames);
    }

    private StatsContainer captureActionStats(
            CombatSimulator sim,
            double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        List<Buff> buffs = sim.getApplicableBuffs(this);
        for (Buff buff : buffs) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static boolean isInfusionWeapon(Weapon candidate) {
        if (candidate == null) {
            return false;
        }
        WeaponType type = candidate.getWeaponType();
        return type == WeaponType.SWORD
                || type == WeaponType.CLAYMORE
                || type == WeaponType.POLEARM;
    }

    private static AttackAction attack(
            String name,
            double multiplier,
            Element element,
            StatType damageBonus,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            boolean shatter) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                damageBonus,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setShatterTrigger(shatter);
        return action;
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

    /** Per-recipient field refresh with weapon-gated A1 and team-wide C2. */
    private static final class FrostFieldRecipientBuff extends SimpleBuff {
        private final boolean infusionEligible;

        private FrostFieldRecipientBuff(
                double duration,
                double currentTime,
                boolean weaponEligible,
                boolean cooldownReduction) {
            super(
                    "Chonghua Frost Field",
                    duration,
                    currentTime,
                    stats -> {
                        if (weaponEligible) {
                            stats.add(
                                    StatType.NORMAL_ATTACK_SPD,
                                    0.08);
                        }
                        if (cooldownReduction) {
                            stats.add(StatType.CD_REDUCTION, 0.15);
                        }
                    });
            infusionEligible = weaponEligible;
        }

        private boolean isInfusionEligible() {
            return infusionEligible;
        }
    }

    /** A4's non-stacking party-visible Cryo resistance reduction window. */
    private static final class RimechaserResistanceBuff extends SimpleBuff {
        private RimechaserResistanceBuff(
                double duration,
                double currentTime,
                double amount) {
            super(
                    "Chongyun Rimechaser Cryo RES Shred",
                    duration,
                    currentTime,
                    stats -> stats.add(
                            StatType.CRYO_RES_SHRED, amount));
        }
    }

    /** Snapshot-restorable marker for the current field and its A4 payload. */
    private static final class FieldStateBuff extends SimpleBuff {
        private final long generation;
        private final double multiplier;
        private final StatsContainer a4Snapshot;

        private FieldStateBuff(
                double duration,
                double currentTime,
                long generation,
                double multiplier,
                StatsContainer a4Snapshot) {
            super(
                    "Chonghua Field State",
                    duration,
                    currentTime,
                    stats -> {
                        // Marker only; recipient buffs carry field stats.
                    });
            this.generation = generation;
            this.multiplier = multiplier;
            this.a4Snapshot = a4Snapshot.merge(null);
        }

        private long getGeneration() {
            return generation;
        }

        private double getMultiplier() {
            return multiplier;
        }

        private StatsContainer getA4Snapshot() {
            return a4Snapshot.merge(null);
        }
    }

    /** Snapshot-restorable marker for C4's two-second internal cooldown. */
    private static final class C4CooldownBuff extends SimpleBuff {
        private C4CooldownBuff(double duration, double currentTime) {
            super(
                    "Chongyun C4 Energy Cooldown",
                    duration,
                    currentTime,
                    stats -> {
                        // Marker only.
                    });
        }
    }
}
