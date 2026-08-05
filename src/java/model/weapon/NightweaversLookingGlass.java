package model.weapon;

import java.util.Collections;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.ElementalReactionTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.entity.WeaponTeamBuffProvider;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Nightweaver's Looking Glass with independent Prayer and New Moon windows.
 *
 * <p>Hydro or Dendro Skill damage by the owner opens Prayer for 4.5 seconds.
 * An actual Lunar-Bloom triggered by any party member opens New Moon for ten
 * seconds. Each window grants one owner EM copy; while both windows from this
 * instance overlap, one canonical active copy grants four party reaction
 * bonuses.</p>
 *
 * <p>Hitlag extension is unavailable, so exact half-open simulation-time
 * windows are used. Multiple active copies do not stack: the highest
 * refinement wins and party order breaks ties.</p>
 */
public final class NightweaversLookingGlass extends Weapon implements
        DamageTriggeredWeaponEffect,
        ElementalReactionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect,
        WeaponTeamBuffProvider {
    private static final double PRAYER_DURATION = 4.5;
    private static final double NEW_MOON_DURATION = 10.0;

    private final int refinement;
    private final double elementalMasteryPerWindow;
    private final double bloomDamageBonus;
    private final double hyperbloomAndBurgeonDamageBonus;
    private final double lunarBloomDamageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private Buff teamBuff;
    private double prayerFrom = Double.POSITIVE_INFINITY;
    private double prayerUntil = Double.NEGATIVE_INFINITY;
    private double newMoonFrom = Double.POSITIVE_INFINITY;
    private double newMoonUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Nightweaver's Looking Glass at refinement rank five. */
    public NightweaversLookingGlass() {
        this(5);
    }

    /** Constructs Nightweaver's Looking Glass at the selected refinement. */
    public NightweaversLookingGlass(int refinement) {
        super("Nightweaver's Looking Glass", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Nightweaver's Looking Glass refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        elementalMasteryPerWindow = 45.0 + 15.0 * refinement;
        bloomDamageBonus = 0.90 + 0.30 * refinement;
        hyperbloomAndBurgeonDamageBonus = 0.60 + 0.20 * refinement;
        lunarBloomDamageBonus = 0.30 + 0.10 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.ELEMENTAL_MASTERY, 265.0);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the Elemental Mastery granted by each active owner window. */
    public double getElementalMasteryPerWindow() {
        return elementalMasteryPerWindow;
    }

    /** Returns the simultaneous-window Bloom damage bonus. */
    public double getBloomDamageBonus() {
        return bloomDamageBonus;
    }

    /** Returns the simultaneous-window Hyperbloom and Burgeon damage bonus. */
    public double getHyperbloomAndBurgeonDamageBonus() {
        return hyperbloomAndBurgeonDamageBonus;
    }

    /** Returns the simultaneous-window Lunar-Bloom damage bonus. */
    public double getLunarBloomDamageBonus() {
        return lunarBloomDamageBonus;
    }

    /** Returns whether Prayer is active at the exact timestamp. */
    public boolean isPrayerActive(double currentTime) {
        return currentTime >= prayerFrom && currentTime < prayerUntil;
    }

    /** Returns whether New Moon is active at the exact timestamp. */
    public boolean isNewMoonActive(double currentTime) {
        return currentTime >= newMoonFrom && currentTime < newMoonUntil;
    }

    /** Binds one exact equipped owner and registers actual reaction handling. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Nightweaver owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Nightweaver's Looking Glass is already bound elsewhere");
            }
            return;
        }
        validateBinding(equippedOwner, activeSimulator);
        owner = equippedOwner;
        simulator = activeSimulator;
        teamBuff = new SimpleBuff(
                "Nightweaver's Looking Glass (Party)",
                BuffId.NIGHTWEAVERS_LOOKING_GLASS_TEAM_REACTION_DMG,
                Double.MAX_VALUE,
                0.0,
                stats -> {
                    stats.add(StatType.BLOOM_DMG_BONUS, bloomDamageBonus);
                    stats.add(
                            StatType.HYPERBLOOM_DMG_BONUS,
                            hyperbloomAndBurgeonDamageBonus);
                    stats.add(
                            StatType.BURGEON_DMG_BONUS,
                            hyperbloomAndBurgeonDamageBonus);
                    stats.add(
                            StatType.LUNAR_BLOOM_DMG_BONUS,
                            lunarBloomDamageBonus);
                });
        teamBuff.sourcedBy(owner.getCharacterId());
        activeSimulator.addElementalReactionTriggeredWeaponEffect(this);
    }

    /** Opens Prayer after the bound owner's real Hydro or Dendro Skill hit. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator activeSimulator) {
        if (!isBoundOwner(user, activeSimulator)
                || action == null
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0
                || (action.getActionType() != ActionType.SKILL
                        && !action.isCountsAsSkillDmg())
                || (action.getElement() != Element.HYDRO
                        && action.getElement() != Element.DENDRO)) {
            return;
        }
        prayerFrom = currentTime;
        prayerUntil = currentTime + PRAYER_DURATION;
    }

    /** Opens New Moon after an actual party-attributed Lunar-Bloom reaction. */
    @Override
    public void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator activeSimulator) {
        if (simulator == null
                || activeSimulator != simulator
                || owner.getWeapon() != this
                || result == null
                || result.getKind() != ReactionResult.Kind.LUNAR_BLOOM
                || source == null
                || !simulator.getPartyMembers().contains(source)) {
            return;
        }
        newMoonFrom = time;
        newMoonUntil = time + NEW_MOON_DURATION;
    }

    /** Applies one owner EM copy for each independently active window. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (owner == null || owner.getWeapon() != this) {
            return;
        }
        if (isPrayerActive(currentTime)) {
            stats.add(StatType.ELEMENTAL_MASTERY, elementalMasteryPerWindow);
        }
        if (isNewMoonActive(currentTime)) {
            stats.add(StatType.ELEMENTAL_MASTERY, elementalMasteryPerWindow);
        }
    }

    /** Returns one canonical simultaneous-window team bonus, or no duplicate. */
    @Override
    public List<Buff> getTeamBuffs(Character equippedOwner) {
        if (equippedOwner != owner
                || simulator == null
                || teamBuff == null
                || findCanonicalActiveProvider(
                        simulator,
                        simulator.getCurrentTime()) != this) {
            return Collections.emptyList();
        }
        return Collections.singletonList(teamBuff);
    }

    /** Captures both independent window boundaries. */
    @Override
    public State captureWeaponState() {
        return new NightweaverState(
                this,
                prayerFrom,
                prayerUntil,
                newMoonFrom,
                newMoonUntil);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof NightweaverState)) {
            throw new IllegalArgumentException("Nightweaver state type is invalid");
        }
        NightweaverState restored = (NightweaverState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Nightweaver state belongs to another instance");
        }
        prayerFrom = restored.prayerFrom;
        prayerUntil = restored.prayerUntil;
        newMoonFrom = restored.newMoonFrom;
        newMoonUntil = restored.newMoonUntil;
    }

    private boolean isBoundOwner(
            Character candidate,
            CombatSimulator activeSimulator) {
        return simulator != null
                && activeSimulator == simulator
                && candidate == owner
                && owner.getWeapon() == this;
    }

    private void validateBinding(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have this Nightweaver's Looking Glass equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
    }

    private static NightweaversLookingGlass findCanonicalActiveProvider(
            CombatSimulator activeSimulator,
            double currentTime) {
        NightweaversLookingGlass selected = null;
        for (Character member : activeSimulator.getPartyMembers()) {
            if (!(member.getWeapon() instanceof NightweaversLookingGlass)) {
                continue;
            }
            NightweaversLookingGlass candidate =
                    (NightweaversLookingGlass) member.getWeapon();
            if (candidate.simulator != activeSimulator
                    || candidate.owner != member
                    || !candidate.isPrayerActive(currentTime)
                    || !candidate.isNewMoonActive(currentTime)) {
                continue;
            }
            if (selected == null
                    || candidate.refinement > selected.refinement) {
                selected = candidate;
            }
        }
        return selected;
    }

    /** Immutable four-boundary state tied to one weapon instance. */
    private static final class NightweaverState implements State {
        private final NightweaversLookingGlass source;
        private final double prayerFrom;
        private final double prayerUntil;
        private final double newMoonFrom;
        private final double newMoonUntil;

        private NightweaverState(
                NightweaversLookingGlass source,
                double prayerFrom,
                double prayerUntil,
                double newMoonFrom,
                double newMoonUntil) {
            this.source = source;
            this.prayerFrom = prayerFrom;
            this.prayerUntil = prayerUntil;
            this.newMoonFrom = newMoonFrom;
            this.newMoonUntil = newMoonUntil;
        }
    }
}
