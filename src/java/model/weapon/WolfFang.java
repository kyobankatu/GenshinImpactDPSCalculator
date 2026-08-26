package model.weapon;

import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.ActionType;
import model.type.WeaponType;
import simulation.action.AttackAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * Wolf-Fang sword with stacking skill and burst CRIT bonuses.
 */
public class WolfFang extends Weapon implements
        DamageTriggeredWeaponEffect,
        SnapshotAwareWeaponEffect {
    /**
     * Constructs Wolf-Fang with Lv 90 base stats.
     */
    public WolfFang() {
        super("Wolf-Fang", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 510);
        s.add(StatType.CRIT_RATE, 0.276);
        this.weaponType = WeaponType.SWORD;
    }

    private final List<Double> skillStacks = new ArrayList<>();
    private final List<Double> burstStacks = new ArrayList<>();

    private double lastSkillStackTime = -1.0;
    private double lastBurstStackTime = -1.0;

    /**
     * Applies the weapon's constant skill and burst damage bonuses and any
     * active CRIT Rate stacks.
     *
     * @param stats the stats container to mutate in-place
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // R5: +32% Skill/Burst DMG
        stats.add(StatType.SKILL_DMG_BONUS, 0.32);
        stats.add(StatType.BURST_DMG_BONUS, 0.32);

        // Count Valid Stacks
        long sCount = skillStacks.stream().filter(t -> t > currentTime).count();
        long bCount = burstStacks.stream().filter(t -> t > currentTime).count();

        // Cap at 4
        if (sCount > 4) {
            sCount = 4;
        }
        if (bCount > 4) {
            bCount = 4;
        }

        if (sCount > 0) {
            stats.add(StatType.SKILL_CRIT_RATE, sCount * 0.04);
        }
        if (bCount > 0) {
            stats.add(StatType.BURST_CRIT_RATE, bCount * 0.04);
        }
    }

    /**
     * Adds or refreshes Wolf-Fang stacks when on-field skill or burst damage is
     * dealt.
     *
     * @param user the character who dealt the damage
     * @param action the triggering attack action
     * @param currentTime simulation time in seconds at the damage event
     * @param sim the active combat simulator
     */
    @Override
    public void onDamage(Character user, AttackAction action, double currentTime, simulation.CombatSimulator sim) {
        // Wolf-Fang Requirement: Character must be on-field to gain stacks.
        if (sim.getActiveCharacter() != user) {
            return;
        }

        boolean isSkill = action.getActionType() == ActionType.SKILL || action.isCountsAsSkillDmg();
        boolean isBurst = action.getActionType() == ActionType.BURST || action.isCountsAsBurstDmg();

        if (isSkill) {
            if (currentTime - lastSkillStackTime >= 0.1) {
                // Add stack (expires in 10s)
                skillStacks.add(currentTime + 10.0);
                lastSkillStackTime = currentTime;

                // Cleanup old
                cleanup(skillStacks, currentTime);
            }
        }

        if (isBurst) {
            if (currentTime - lastBurstStackTime >= 0.1) {
                burstStacks.add(currentTime + 10.0);
                lastBurstStackTime = currentTime;

                cleanup(burstStacks, currentTime);
            }
        }
    }

    /** Captures both stack timelines and their acquisition cooldowns. */
    @Override
    public State captureWeaponState() {
        return new WolfFangState(
                this,
                skillStacks,
                burstStacks,
                lastSkillStackTime,
                lastBurstStackTime);
    }

    /** Restores state captured from this exact Wolf-Fang instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof WolfFangState)) {
            throw new IllegalArgumentException("Wolf-Fang state type is invalid");
        }
        WolfFangState restored = (WolfFangState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Wolf-Fang state belongs to another instance");
        }
        skillStacks.clear();
        skillStacks.addAll(restored.skillStacks);
        burstStacks.clear();
        burstStacks.addAll(restored.burstStacks);
        lastSkillStackTime = restored.lastSkillStackTime;
        lastBurstStackTime = restored.lastBurstStackTime;
    }

    private void cleanup(List<Double> stacks, double currentTime) {
        Iterator<Double> it = stacks.iterator();
        while (it.hasNext()) {
            if (it.next() <= currentTime) {
                it.remove();
            }
        }
    }

    /** Immutable Wolf-Fang stack snapshot. */
    private static final class WolfFangState implements State {
        private final WolfFang source;
        private final List<Double> skillStacks;
        private final List<Double> burstStacks;
        private final double lastSkillStackTime;
        private final double lastBurstStackTime;

        private WolfFangState(
                WolfFang source,
                List<Double> skillStacks,
                List<Double> burstStacks,
                double lastSkillStackTime,
                double lastBurstStackTime) {
            this.source = source;
            this.skillStacks = List.copyOf(skillStacks);
            this.burstStacks = List.copyOf(burstStacks);
            this.lastSkillStackTime = lastSkillStackTime;
            this.lastBurstStackTime = lastBurstStackTime;
        }
    }
}
