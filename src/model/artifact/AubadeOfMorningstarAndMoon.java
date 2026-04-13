package model.artifact;

import mechanics.buff.BuffId;
import model.stats.StatsContainer;
import model.type.StatType;
import mechanics.buff.Buff;
import simulation.CombatSimulator;
import simulation.CombatSimulator.Moonsign;

public class AubadeOfMorningstarAndMoon extends model.entity.ArtifactSet {

    public AubadeOfMorningstarAndMoon() {
        super("Aubade of Morningstar and Moon", new StatsContainer());
        this.getStats().add(StatType.ATK_PERCENT, 0.18);
    }

    public AubadeOfMorningstarAndMoon(StatsContainer stats) {
        super("Aubade of Morningstar and Moon", stats);
        this.getStats().add(StatType.ATK_PERCENT, 0.18);
    }

    @Override
    public void onSwitchOut(CombatSimulator sim, model.entity.Character owner) {
        // When off-field:
        // Lunar Reaction DMG +20% (Always)
        // If Ascendant Gleam: +40% additional (Total 60%)

        // Remove existing "On-Field" or "Lingering" buffs if any?
        // Actually, we can just apply a permanent "Off-Field" buff that checks
        // conditions dynamically?
        // Or we use a specific "Aubade: Off-field" buff.

        // Remove lingering buff first
        // (Implementation detail: How to remove specific buff by name?
        // Character.removeBuff?
        // Currently Character doesn't support removeBuff nicely, usually we wait for
        // expiration.
        // But for state toggling, we usually add a new buff that overwrites or we
        // manage state.)

        // Since we don't have explicit `removeBuff`, we can add a short duration buff
        // OR a long one.
        // For off-field, it lasts until switch-in.
        // We can add a "Aubade: Off-field" buff with MAX_DURATION.
        // And when switching IN, we let it stay? No, effect says "disappear after...
        // active for 3s".
        // So upon Switch IN, we should replace it with a 3s duration buff.

        // Strategy:
        // Switch Out -> Add "Aubade: Dynamic" (Duration: Infinite)
        // Switch In -> Add "Aubade: Dynamic" (Duration: 3.0s) -> Effectively overwrites
        // or refreshes with short timer.

        // Issue: If we add a new buff, does it replace the old one?
        // My simple `Buff` system might stack them.
        // Check `Character.getEffectiveStats` loop. It sums all active buffs.
        // So we might duplicate if we are not careful.
        // But `Buff` class doesn't have unique ID.
        // We should probably check if we can remove or just rely on logic inside the
        // Buff to expire itself?
        // No, `isExpired` is time based.

        // If we can't remove, we might need a "State Manager" buff that is added ONCE
        // at start and handles everything?
        // But Artifact is stateless mostly.

        // Let's assume standard behavior:
        // We add a buff. If we add another with same name, it's just another buff in
        // the list.
        // We need to avoid stacking.
        // `Character.java` doesn't seem to have de-duplication.

        // Optimized approach:
        // On Equip (Constructor? No, we don't have onEquip hook yet, effectively).
        // Initialization?

        // Given current constraints:
        // We can implement `applyPassive` which is called every frame/calc?
        // `ArtifactSet.applyPassive(StatsContainer)` IS called in
        // `Character.getEffectiveStats`.
        // BUT `applyPassive` doesn't receive `Sim`, so we can't check Moonsign or
        // Active State easily.

        // So we MUST use Buffs.
        // To avoid Stacking:
        // We can create a `AubadeBuff` class that implements `equals`?
        // `Character` uses `List<Buff>`. Arrays/Lists allow duplicates.

        // HACK: In `onSwitchOut`, we can try to find and modify existing buff in
        // `owner.getBuffs()`?
        // `Character` usually doesn't expose mutable list.

        // Alternative:
        // The Buff is "Aubade State". It has a `setMode(OFF_FIELD / LINGERING)` and
        // `setExpiry`.
        // We keep a reference to this Buff in the Artifact instance?
        // `ArtifactSet` is shared between characters? NO, usually instantiated per
        // character in `TestColumbina`.
        // `new Aubade...`

        // So `AubadeOfMorningstarAndMoon` instance is unique to the character.
        // We can store the `Buff` instance as a field!

        updateBuffState(sim, owner, false);
    }

    @Override
    public void onSwitchIn(CombatSimulator sim, model.entity.Character owner) {
        // When switching in:
        // Effect persists for 3s.
        updateBuffState(sim, owner, true);
    }

    // State
    private AubadeBuff activeBuff;

    private void updateBuffState(CombatSimulator sim, model.entity.Character owner, boolean isSwitchingIn) {
        if (activeBuff == null) {
            activeBuff = new AubadeBuff("Aubade Bonus", sim);
            owner.addBuff(activeBuff);
        }

        if (isSwitchingIn) {
            // Set expire time to Now + 3s
            activeBuff.setExpiration(sim.getCurrentTime() + 3.0);
        } else {
            // Set expire time to Infinite
            activeBuff.setExpiration(Double.MAX_VALUE);
        }
    }

    // Inner Dynamic Buff Class
    private static class AubadeBuff extends Buff {
        private CombatSimulator sim;

        public AubadeBuff(String name, CombatSimulator sim) {
            super(name, BuffId.AUBADE_BONUS); // Infinite by default, but we control via setExpiration
            this.sim = sim;
        }

        // Expose setter for expiration
        public void setExpiration(double time) {
            this.expirationTime = time;
        }

        @Override
        protected void applyStats(StatsContainer stats, double currentTime) {
            // Logic:
            // +20% Base
            // +40% if Ascendant
            double bonus = 0.20;
            if (sim.getMoonsign() == Moonsign.ASCENDANT_GLEAM) {
                bonus += 0.40;
            }

            // Add to all Lunar types
            // Currently assuming LUNAR_CHARGED, LUNAR_BLOOM, LUNAR_CRYSTALLIZE
            stats.add(StatType.LUNAR_CHARGED_DMG_BONUS, bonus);
            stats.add(StatType.LUNAR_BLOOM_DMG_BONUS, bonus);
            stats.add(StatType.LUNAR_CRYSTALLIZE_DMG_BONUS, bonus);
        }
    }
}
