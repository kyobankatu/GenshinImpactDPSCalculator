package sample;

import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.CalamityOfEshu;
import model.weapon.CashflowSupervision;
import model.weapon.CrimsonMoonsSemblance;
import model.weapon.FlowerWreathedFeathers;
import model.weapon.SplendorOfTranquilWaters;

/** Regression checks for recent HP, shield, aim, and static weapon branches. */
public final class RecentBoundaryWeaponRegressionTest {
    private RecentBoundaryWeaponRegressionTest() {
    }

    /** Runs exact metadata, refinement values, no-op boundaries, and guards. */
    public static void main(String[] args) {
        testCashflowSupervision();
        testCrimsonMoonsSemblance();
        testCalamityOfEshu();
        testSplendorOfTranquilWaters();
        testFlowerWreathedFeathers();
        System.out.println("RecentBoundaryWeaponRegressionTest passed");
    }

    private static void testCashflowSupervision() {
        CashflowSupervision defaultWeapon = new CashflowSupervision();
        assertMetadata(defaultWeapon.getName(), "Cashflow Supervision",
                defaultWeapon.getWeaponType(), WeaponType.CATALYST,
                defaultWeapon.getStats(), 674.0, StatType.CRIT_RATE, 0.221);
        for (int refinement = 1; refinement <= 5; refinement++) {
            CashflowSupervision weapon =
                    new CashflowSupervision(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.12 + 0.04 * refinement,
                    weapon.getAttackBonus(),
                    "Cashflow ATK R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    weapon.getAttackBonus(),
                    weapon.getNormalDamagePerStack(),
                    "Cashflow Normal stack R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.105 + 0.035 * refinement,
                    weapon.getChargedDamagePerStack(),
                    "Cashflow Charged stack R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.06 + 0.02 * refinement,
                    weapon.getAttackSpeedBonus(),
                    "Cashflow ATK SPD R" + refinement);
            StatsContainer stats = sentinels();
            weapon.applyPassive(stats, 100.0);
            StatefulWeaponRegressionSupport.assertClose(
                    0.25 + weapon.getAttackBonus(),
                    stats.get(StatType.ATK_PERCENT),
                    "Cashflow applies only unconditional ATK R" + refinement);
            assertBoundarySentinels(stats, "Cashflow R" + refinement);
        }
        assertInvalid(CashflowSupervision::new, "Cashflow");
    }

    private static void testCrimsonMoonsSemblance() {
        CrimsonMoonsSemblance defaultWeapon =
                new CrimsonMoonsSemblance();
        assertMetadata(defaultWeapon.getName(), "Crimson Moon's Semblance",
                defaultWeapon.getWeaponType(), WeaponType.POLEARM,
                defaultWeapon.getStats(), 674.0, StatType.CRIT_RATE, 0.221);
        for (int refinement = 1; refinement <= 5; refinement++) {
            CrimsonMoonsSemblance weapon =
                    new CrimsonMoonsSemblance(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.08 + 0.04 * refinement,
                    weapon.getFirstBondDamageBonus(),
                    "Crimson first Bond tier R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.16 + 0.08 * refinement,
                    weapon.getThresholdBondDamageBonus(),
                    "Crimson threshold Bond tier R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.25, weapon.getBondIncreaseRatio(),
                    "Crimson Bond increase ratio");
            assertInactive(weapon, "Crimson R" + refinement);
        }
        assertInvalid(CrimsonMoonsSemblance::new, "Crimson");
    }

    private static void testCalamityOfEshu() {
        CalamityOfEshu defaultWeapon = new CalamityOfEshu();
        assertMetadata(defaultWeapon.getName(), "Calamity of Eshu",
                defaultWeapon.getWeaponType(), WeaponType.SWORD,
                defaultWeapon.getStats(), 565.0,
                StatType.ATK_PERCENT, 0.276);
        for (int refinement = 1; refinement <= 5; refinement++) {
            CalamityOfEshu weapon = new CalamityOfEshu(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.15 + 0.05 * refinement,
                    weapon.getShieldedDamageBonus(),
                    "Calamity shielded DMG R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.06 + 0.02 * refinement,
                    weapon.getShieldedCriticalRate(),
                    "Calamity shielded CR R" + refinement);
            assertInactive(weapon, "Calamity R" + refinement);
        }
        assertInvalid(CalamityOfEshu::new, "Calamity");
    }

    private static void testSplendorOfTranquilWaters() {
        SplendorOfTranquilWaters defaultWeapon =
                new SplendorOfTranquilWaters();
        assertMetadata(defaultWeapon.getName(), "Splendor of Tranquil Waters",
                defaultWeapon.getWeaponType(), WeaponType.SWORD,
                defaultWeapon.getStats(), 542.0, StatType.CRIT_DMG, 0.882);
        for (int refinement = 1; refinement <= 5; refinement++) {
            SplendorOfTranquilWaters weapon =
                    new SplendorOfTranquilWaters(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.06 + 0.02 * refinement,
                    weapon.getSkillDamagePerStack(),
                    "Splendor Skill stack R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.105 + 0.035 * refinement,
                    weapon.getHpBonusPerStack(),
                    "Splendor HP stack R" + refinement);
            assertInactive(weapon, "Splendor R" + refinement);
        }
        assertInvalid(SplendorOfTranquilWaters::new, "Splendor");
    }

    private static void testFlowerWreathedFeathers() {
        FlowerWreathedFeathers defaultWeapon =
                new FlowerWreathedFeathers();
        assertMetadata(defaultWeapon.getName(), "Flower-Wreathed Feathers",
                defaultWeapon.getWeaponType(), WeaponType.BOW,
                defaultWeapon.getStats(), 510.0,
                StatType.ATK_PERCENT, 0.413);
        for (int refinement = 1; refinement <= 5; refinement++) {
            FlowerWreathedFeathers weapon =
                    new FlowerWreathedFeathers(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.045 + 0.015 * refinement,
                    weapon.getChargedDamagePerStack(),
                    "Flower-Wreathed Charged stack R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.15, weapon.getGlidingStaminaReduction(),
                    "Flower-Wreathed gliding reduction");
            assertInactive(weapon, "Flower-Wreathed R" + refinement);
        }
        assertInvalid(FlowerWreathedFeathers::new, "Flower-Wreathed");
    }

    private static void assertInactive(
            model.weapon.BoundaryInactiveWeapon weapon,
            String label) {
        StatsContainer stats = sentinels();
        weapon.applyPassive(stats, -100.0);
        StatefulWeaponRegressionSupport.assertClose(
                0.25, stats.get(StatType.ATK_PERCENT),
                label + " preserves ATK");
        assertBoundarySentinels(stats, label);
    }

    private static void assertBoundarySentinels(
            StatsContainer stats,
            String label) {
        StatefulWeaponRegressionSupport.assertClose(
                0.30, stats.get(StatType.DMG_BONUS_ALL),
                label + " preserves generic DMG");
        StatefulWeaponRegressionSupport.assertClose(
                0.40, stats.get(StatType.SKILL_DMG_BONUS),
                label + " preserves Skill DMG");
        StatefulWeaponRegressionSupport.assertClose(
                0.50, stats.get(StatType.NORMAL_ATTACK_DMG_BONUS),
                label + " preserves Normal DMG");
        StatefulWeaponRegressionSupport.assertClose(
                0.60, stats.get(StatType.CRIT_RATE),
                label + " preserves CRIT Rate");
    }

    private static StatsContainer sentinels() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.ATK_PERCENT, 0.25);
        stats.set(StatType.DMG_BONUS_ALL, 0.30);
        stats.set(StatType.SKILL_DMG_BONUS, 0.40);
        stats.set(StatType.NORMAL_ATTACK_DMG_BONUS, 0.50);
        stats.set(StatType.CRIT_RATE, 0.60);
        return stats;
    }

    private static void assertMetadata(
            String actualName,
            String expectedName,
            WeaponType actualType,
            WeaponType expectedType,
            StatsContainer stats,
            double baseAtk,
            StatType substat,
            double substatValue) {
        StatefulWeaponRegressionSupport.assertEquals(
                expectedName, actualName, expectedName + " display name");
        StatefulWeaponRegressionSupport.assertEquals(
                expectedType, actualType, expectedName + " weapon type");
        StatefulWeaponRegressionSupport.assertClose(
                baseAtk, stats.get(StatType.BASE_ATK),
                expectedName + " base ATK");
        StatefulWeaponRegressionSupport.assertClose(
                substatValue, stats.get(substat),
                expectedName + " substat");
    }

    private static void assertInvalid(
            WeaponFactory factory,
            String label) {
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(0), label + " rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(6), label + " rejects R6");
    }

    @FunctionalInterface
    private interface WeaponFactory {
        model.entity.Weapon create(int refinement);
    }
}
