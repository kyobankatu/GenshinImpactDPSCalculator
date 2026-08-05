package sample;

import mechanics.reaction.ReactionEffectScheduler;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.ProspectorShovel;
import simulation.CombatSimulator;

/** Focused metadata, Moonsign, refinement, and binding checks. */
public final class ProspectorShovelRegressionTest {
    private static final double[] ELECTRO_CHARGED_BONUSES = {
        0.48, 0.60, 0.72, 0.84, 0.96
    };
    private static final double[] LUNAR_CHARGED_BONUSES = {
        0.12, 0.15, 0.18, 0.21, 0.24
    };

    private ProspectorShovelRegressionTest() {
    }

    /** Runs all refinement, Moonsign, stat-isolation, and binding cases. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testMoonsignScalingWithoutOwnerAction();
        testLunarDamageUsesOnlyLunarBonus();
        testUnrelatedStatsAndArbitraryTimes();
        testBindingGuards();
        System.out.println("ProspectorShovelRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new ProspectorShovel().getRefinement(),
                "Prospector's Shovel default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            ProspectorShovel weapon = new ProspectorShovel(refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    "Prospector's Shovel", weapon.getName(),
                    "Prospector's Shovel name R" + refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    WeaponType.POLEARM, weapon.getWeaponType(),
                    "Prospector's Shovel weapon type R" + refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    refinement, weapon.getRefinement(),
                    "Prospector's Shovel refinement R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    510.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Prospector's Shovel base ATK R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.413, weapon.getStats().get(StatType.ATK_PERCENT),
                    "Prospector's Shovel ATK substat R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    ELECTRO_CHARGED_BONUSES[refinement - 1],
                    weapon.getElectroChargedDamageBonus(),
                    "Prospector's Shovel Electro-Charged bonus R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    LUNAR_CHARGED_BONUSES[refinement - 1],
                    weapon.getLunarChargedDamageBonus(),
                    "Prospector's Shovel Lunar-Charged bonus R" + refinement);
        }

        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectorShovel(0),
                "Prospector's Shovel rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectorShovel(6),
                "Prospector's Shovel rejects R6");
    }

    private static void testMoonsignScalingWithoutOwnerAction() {
        for (int refinement = 1; refinement <= 5; refinement++) {
            ProspectorShovel weapon = new ProspectorShovel(refinement);
            StatefulWeaponRegressionSupport.TestCharacter owner =
                    StatefulWeaponRegressionSupport.character(
                            CharacterId.RAIDEN_SHOGUN, weapon);
            CombatSimulator sim =
                    StatefulWeaponRegressionSupport.simulatorWith(owner);
            double electroCharged = ELECTRO_CHARGED_BONUSES[refinement - 1];
            double lunarCharged = LUNAR_CHARGED_BONUSES[refinement - 1];

            sim.setMoonsign(CombatSimulator.Moonsign.NONE);
            assertReactionBonuses(
                    owner.getEffectiveStats(0.0),
                    electroCharged,
                    lunarCharged,
                    "Prospector's Shovel NONE R" + refinement);

            sim.setMoonsign(CombatSimulator.Moonsign.NASCENT_GLEAM);
            assertReactionBonuses(
                    owner.getEffectiveStats(7.25),
                    electroCharged,
                    lunarCharged,
                    "Prospector's Shovel NASCENT R" + refinement);

            sim.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);
            assertReactionBonuses(
                    owner.getEffectiveStats(91.0),
                    electroCharged,
                    2.0 * lunarCharged,
                    "Prospector's Shovel ASCENDANT R" + refinement);
        }
    }

    private static void testUnrelatedStatsAndArbitraryTimes() {
        ProspectorShovel weapon = new ProspectorShovel(3);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        sim.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);

        StatsContainer early = owner.getEffectiveStats(-123.5);
        StatsContainer late = owner.getEffectiveStats(1_000_000.0);
        assertReactionBonuses(early, 0.72, 0.36,
                "Prospector's Shovel arbitrary negative time");
        assertReactionBonuses(late, 0.72, 0.36,
                "Prospector's Shovel arbitrary late time");
        StatefulWeaponRegressionSupport.assertClose(
                0.0, late.get(StatType.ELEMENTAL_MASTERY),
                "Prospector's Shovel does not grant Elemental Mastery");
        StatefulWeaponRegressionSupport.assertClose(
                0.0, late.get(StatType.DMG_BONUS_ALL),
                "Prospector's Shovel does not grant all-DMG bonus");
        StatefulWeaponRegressionSupport.assertClose(
                0.0, late.get(StatType.HYPERBLOOM_DMG_BONUS),
                "Prospector's Shovel does not grant unrelated reaction bonus");
        StatefulWeaponRegressionSupport.assertClose(
                0.413, late.get(StatType.ATK_PERCENT),
                "Prospector's Shovel preserves only its structural ATK substat");
    }

    private static void testLunarDamageUsesOnlyLunarBonus() {
        ProspectorShovel weapon = new ProspectorShovel(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAIDEN_SHOGUN, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        ReactionEffectScheduler scheduler = new ReactionEffectScheduler(sim);
        double resistedBase = 1.8 * 1446.85 * 0.9;

        sim.setMoonsign(CombatSimulator.Moonsign.NONE);
        StatefulWeaponRegressionSupport.assertClose(
                resistedBase * 1.12,
                scheduler.computeInitialLunarChargedDamage(),
                "R1 Lunar damage excludes the 48% standard EC bonus");

        sim.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);
        StatefulWeaponRegressionSupport.assertClose(
                resistedBase * 1.24,
                scheduler.computeInitialLunarChargedDamage(),
                "R1 Ascendant Lunar damage uses exactly two 12% copies");
    }

    private static void testBindingGuards() {
        ProspectorShovel weapon = new ProspectorShovel(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAIDEN_SHOGUN, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);

        weapon.initializeForSimulator(owner, sim);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Prospector's Shovel rejects cross-simulator binding");
        StatefulWeaponRegressionSupport.TestCharacter anotherOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, weapon);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(anotherOwner, sim),
                "Prospector's Shovel rejects cross-owner binding");

        ProspectorShovel unbound = new ProspectorShovel(1);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unbound.initializeForSimulator(null, sim),
                "Prospector's Shovel rejects null owner");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unbound.initializeForSimulator(owner, null),
                "Prospector's Shovel rejects null simulator");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unbound.initializeForSimulator(owner, sim),
                "Prospector's Shovel rejects unequipped owner");

        ProspectorShovel outsideWeapon = new ProspectorShovel(1);
        StatefulWeaponRegressionSupport.TestCharacter outsideOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, outsideWeapon);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> outsideWeapon.initializeForSimulator(
                        outsideOwner, new CombatSimulator()),
                "Prospector's Shovel rejects owner outside simulator party");
    }

    private static void assertReactionBonuses(
            StatsContainer stats,
            double expectedElectroCharged,
            double expectedLunarCharged,
            String message) {
        StatefulWeaponRegressionSupport.assertClose(
                expectedElectroCharged,
                stats.get(StatType.ELECTRO_CHARGED_DMG_BONUS),
                message + " Electro-Charged bonus");
        StatefulWeaponRegressionSupport.assertClose(
                expectedLunarCharged,
                stats.get(StatType.LUNAR_CHARGED_DMG_BONUS),
                message + " Lunar-Charged bonus");
    }
}
