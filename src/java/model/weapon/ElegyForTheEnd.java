package model.weapon;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/** Elegy for the End bow with off-field sigils and a timed party song. */
public class ElegyForTheEnd extends Weapon
        implements DamageTriggeredWeaponEffect, SimulatorInitializedWeaponEffect {
    private static final int REQUIRED_SIGILS = 4;
    private static final double SIGIL_COOLDOWN = 0.2;
    private static final double SONG_DURATION = 12.0;
    private static final double SONG_LOCK_DURATION = 20.0;

    private final int refinement;
    private final double passiveElementalMastery;
    private final double songElementalMastery;
    private final double songAttackBonus;
    private Character owner;
    private CombatSimulator simulator;
    private int sigilCount;
    private double nextSigilTime = Double.NEGATIVE_INFINITY;
    private double sigilLockUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Elegy for the End at refinement rank five. */
    public ElegyForTheEnd() {
        this(5);
    }

    /**
     * Constructs Elegy for the End at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public ElegyForTheEnd(int refinement) {
        super("Elegy for the End", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.passiveElementalMastery = 45.0 + 15.0 * refinement;
        this.songElementalMastery = 75.0 + 25.0 * refinement;
        this.songAttackBonus = 0.15 + 0.05 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.551);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the currently retained Sigils of Remembrance. */
    public int getSigilCount() {
        return sigilCount;
    }

    /** Binds the owner while preserving off-field damage-hook eligibility. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Elegy for the End is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Applies the weapon's unconditional 60-120 Elemental Mastery. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ELEMENTAL_MASTERY, passiveElementalMastery);
    }

    /** Records eligible Skill/Burst hits after their damage has resolved. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim != simulator || user != owner || !isSkillOrBurstDamage(action)) {
            return;
        }
        if (currentTime < sigilLockUntil || currentTime < nextSigilTime) {
            return;
        }
        nextSigilTime = currentTime + SIGIL_COOLDOWN;
        sigilCount++;
        if (sigilCount < REQUIRED_SIGILS) {
            return;
        }

        sigilCount = 0;
        sigilLockUntil = currentTime + SONG_LOCK_DURATION;
        SimpleBuff song = new SimpleBuff(
                "Millennial Movement: Farewell Song",
                BuffId.ELEGY_FAREWELL_SONG,
                SONG_DURATION,
                currentTime,
                stats -> {
                    stats.add(StatType.ELEMENTAL_MASTERY, songElementalMastery);
                    stats.add(StatType.ATK_PERCENT, songAttackBonus);
                });
        song.sourcedBy(owner.getCharacterId());
        sim.applyTeamBuffNoStack(song);
    }

    private static boolean isSkillOrBurstDamage(AttackAction action) {
        return action.getActionType() == ActionType.SKILL
                || action.getActionType() == ActionType.BURST
                || action.isCountsAsSkillDmg()
                || action.isCountsAsBurstDmg();
    }
}
