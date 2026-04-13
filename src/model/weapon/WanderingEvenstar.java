package model.weapon;

import model.entity.Weapon;
import model.entity.Character;
import mechanics.buff.BuffId;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import mechanics.buff.Buff;
import java.util.ArrayList;
import java.util.List;

public class WanderingEvenstar extends Weapon {
    public WanderingEvenstar() {
        super("Wandering Evenstar", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 510);
        s.add(StatType.ELEMENTAL_MASTERY, 165);
        this.weaponType = WeaponType.CATALYST;
    }

    @Override
    public List<Buff> getTeamBuffs(Character owner) {
        List<Buff> buffs = new ArrayList<>();

        // Wildling Nightstar Buff
        // R5: Gain 48% of EM as ATK. Team gains 30% of this bonus.
        // Duration 12s, CD 10s (effectively permanent uptime).
        buffs.add(new Buff("Wildling Nightstar", BuffId.WANDERING_EVENSTAR_WILDLING_NIGHTSTAR) {
            @Override
            protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                // Calculate Owner's EM safely to avoid recursion
                // We use Base + Artifacts + Weapon Stats.
                // NOTE: This intentionally excludes other Team Buffs (like Sucrose) to prevent
                // loops,
                // and because usually direct stat conversion passives don't chain "shared"
                // stats.

                double ownerEM = 0.0;
                ownerEM += owner.getBaseStats().get(StatType.ELEMENTAL_MASTERY);
                ownerEM += owner.getWeapon().getStats().get(StatType.ELEMENTAL_MASTERY);
                if (owner.getArtifacts() != null) {
                    for (model.entity.ArtifactSet a : owner.getArtifacts()) {
                        if (a != null)
                            ownerEM += a.getStats().get(StatType.ELEMENTAL_MASTERY);
                    }
                }

                // Calculate Bonus
                double selfBonus = ownerEM * 0.48;

                // Same logic as before: Team receives 30% of Self Bonus.
                // WE are using Option 2: This Buff provides the 30% Part for EVERYONE.
                // Self also gets the remaining 70% from 'applyPassive'.

                double shareBonus = selfBonus * 0.30;
                stats.add(StatType.ATK_FLAT, shareBonus);
            }
        });
        return buffs;
    }

    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // Self Bonus (The remaining 70%)
        // Because 30% comes from the Team Buff which applies to us too.

        // Note: ownerEM calculation duplicated here.
        // We assume 'stats' passed here contains the relevant EM components
        // (Base+Arty).
        // Safest to calculate manually again to match 'getTeamBuffs' logic exactly.

        double em = stats.get(StatType.ELEMENTAL_MASTERY);
        // Using 'em' from stats includes everything processed so far.
        // This is actually BETTER than manual calc for self, but implies inconsistency
        // with Team Buff.
        // Let's use the same 'manual' calc approach for consistency if possible,
        // OR rely on the fact that for Self, we want max accuracy.
        // But to ensure 70% + 30% = 100% strictly, we should use same basis.
        // But 'stats' here is partial.
        // Let's limit scope: Use the 'Manual Base+Arty' approach for logical
        // consistency.
        // Or simple: 48% (Self) - 30% (Share) isn't inherently linked if bases differ.
        // It's acceptable for Self to use "Better EM" and Team to use "Safe EM".
        // But strict "gain 30% of THIS buff" implies strict derivation.
        // Let's stick to Safe EM for both.

        // BUT wait. 'applyPassive' is called inside a Character context.
        // We don't have easy access to 'Character' here (only stats).
        // Wait, 'applyPassive(stats, time)'. No Character.
        // 'Weapon.java' doesn't have owner reference!
        // Usage in 'Character.getEffectiveStats': 'weapon.applyPassive(stats,
        // currentTime)'.
        // So we can't traverse to Owner.

        // We can only use 'stats'.
        // So:
        double emSafe = stats.get(StatType.ELEMENTAL_MASTERY);
        // This EM includes Base + Weapon + Arty (so far).
        // This is exactly what we want.

        double totalSelfBonus = emSafe * 0.48;
        double teamPart = emSafe * 0.48 * 0.30;
        double remaingPart = totalSelfBonus - teamPart;

        stats.add(StatType.ATK_FLAT, remaingPart);
    }
}
