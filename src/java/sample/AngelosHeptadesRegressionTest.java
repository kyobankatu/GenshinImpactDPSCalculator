package sample;

import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.AngelosHeptades;

/** Focused metadata, refinement, and unavailable-shield boundary checks. */
public final class AngelosHeptadesRegressionTest {
    private AngelosHeptadesRegressionTest() {
    }

    /** Runs exact metadata, static branch, isolation, and guard cases. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testPermanentAttackAndInactiveShieldBranches();
        testInvalidRefinement();
        System.out.println("AngelosHeptadesRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        AngelosHeptades defaultWeapon = new AngelosHeptades();
        StatefulWeaponRegressionSupport.assertEquals(
                "Angelos Heptades", defaultWeapon.getName(),
                "Angelos Heptades display name");
        StatefulWeaponRegressionSupport.assertEquals(
                WeaponType.CATALYST, defaultWeapon.getWeaponType(),
                "Angelos Heptades weapon type");
        StatefulWeaponRegressionSupport.assertEquals(
                5, defaultWeapon.getRefinement(),
                "Angelos Heptades default refinement");
        StatefulWeaponRegressionSupport.assertClose(
                741.0, defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Angelos Heptades base ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.165,
                defaultWeapon.getStats().get(StatType.ATK_PERCENT),
                "Angelos Heptades ATK substat");

        for (int refinement = 1; refinement <= 5; refinement++) {
            AngelosHeptades weapon = new AngelosHeptades(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.09 + 0.03 * refinement,
                    weapon.getPermanentAttackBonus(),
                    "Angelos Heptades permanent ATK R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    13.0 + refinement,
                    weapon.getShieldEnergyRecovery(),
                    "Angelos Heptades shield Energy R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.07 + 0.03 * refinement,
                    weapon.getDamageBonusPerThousandAttack(),
                    "Angelos Heptades damage scaling R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.18 + 0.08 * refinement,
                    weapon.getMaximumDamageBonus(),
                    "Angelos Heptades damage cap R" + refinement);
        }
    }

    private static void testPermanentAttackAndInactiveShieldBranches() {
        for (int refinement = 1; refinement <= 5; refinement++) {
            AngelosHeptades weapon = new AngelosHeptades(refinement);
            StatsContainer early = seededStats();
            weapon.applyPassive(early, -100.0);
            StatefulWeaponRegressionSupport.assertClose(
                    0.20 + 0.09 + 0.03 * refinement,
                    early.get(StatType.ATK_PERCENT),
                    "Angelos Heptades permanent ATK R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.30, early.get(StatType.DMG_BONUS_ALL),
                    "Angelos Heptades does not synthesize shield damage R"
                            + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    7.0, early.get(StatType.ENERGY_RECHARGE),
                    "Angelos Heptades does not misuse Energy Recharge R"
                            + refinement);

            StatsContainer late = seededStats();
            weapon.applyPassive(late, 1_000_000.0);
            StatefulWeaponRegressionSupport.assertClose(
                    early.get(StatType.ATK_PERCENT),
                    late.get(StatType.ATK_PERCENT),
                    "Angelos Heptades static branch is time-invariant R"
                            + refinement);
        }
    }

    private static void testInvalidRefinement() {
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new AngelosHeptades(0),
                "Angelos Heptades rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new AngelosHeptades(6),
                "Angelos Heptades rejects R6");
    }

    private static StatsContainer seededStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.ATK_PERCENT, 0.20);
        stats.set(StatType.DMG_BONUS_ALL, 0.30);
        stats.set(StatType.ENERGY_RECHARGE, 7.0);
        return stats;
    }
}
