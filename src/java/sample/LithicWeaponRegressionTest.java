package sample;

import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.LithicBlade;
import model.weapon.LithicSpear;
import simulation.CombatSimulator;

/** Focused metadata, Liyue-count, cap, and binding checks. */
public final class LithicWeaponRegressionTest {
    private LithicWeaponRegressionTest() {
    }

    /** Runs all Lithic family regression cases. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testLiyueCompositionAndCap();
        testUnknownAndIndependentInstances();
        testBindingGuards();
        System.out.println("LithicWeaponRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new LithicBlade().getRefinement(),
                "Lithic Blade default refinement");
        StatefulWeaponRegressionSupport.assertEquals(
                5, new LithicSpear().getRefinement(),
                "Lithic Spear default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            LithicBlade blade = new LithicBlade(refinement);
            LithicSpear spear = new LithicSpear(refinement);
            assertMetadata(blade, "Lithic Blade", WeaponType.CLAYMORE,
                    510.0, 0.413, refinement);
            assertMetadata(spear, "Lithic Spear", WeaponType.POLEARM,
                    565.0, 0.276, refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new LithicBlade(0), "Lithic Blade refinement zero");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new LithicSpear(6), "Lithic Spear refinement six");
    }

    private static void assertMetadata(
            model.entity.Weapon weapon,
            String name,
            WeaponType type,
            double baseAtk,
            double atkPercent,
            int refinement) {
        StatefulWeaponRegressionSupport.assertEquals(
                name, weapon.getName(), name + " name R" + refinement);
        StatefulWeaponRegressionSupport.assertEquals(
                type, weapon.getWeaponType(), name + " type R" + refinement);
        StatefulWeaponRegressionSupport.assertClose(
                baseAtk, weapon.getStats().get(StatType.BASE_ATK),
                name + " base ATK R" + refinement);
        StatefulWeaponRegressionSupport.assertClose(
                atkPercent, weapon.getStats().get(StatType.ATK_PERCENT),
                name + " secondary ATK R" + refinement);
    }

    private static void testLiyueCompositionAndCap() {
        LithicBlade weapon = new LithicBlade(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.DILUC, weapon);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(
                owner,
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, null),
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XINGQIU, null),
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING, null),
                StatefulWeaponRegressionSupport.character(
                        CharacterId.GANYU, null),
                StatefulWeaponRegressionSupport.character(
                        CharacterId.NINGGUANG, null));
        StatefulWeaponRegressionSupport.assertEquals(
                4, weapon.getLiyueMemberCount(),
                "Lithic family caps at four Liyue members");
        StatsContainer stats = StatefulWeaponRegressionSupport.stats(owner, sim);
        StatefulWeaponRegressionSupport.assertClose(
                0.413 + 4.0 * 0.07,
                stats.get(StatType.ATK_PERCENT),
                "Lithic Blade four-stack ATK");
        StatefulWeaponRegressionSupport.assertClose(
                4.0 * 0.03,
                stats.get(StatType.CRIT_RATE),
                "Lithic Blade four-stack CRIT Rate");
    }

    private static void testUnknownAndIndependentInstances() {
        LithicSpear first = new LithicSpear(5);
        StatefulWeaponRegressionSupport.TestCharacter firstOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.UNKNOWN, first);
        CombatSimulator firstSim = StatefulWeaponRegressionSupport.simulatorWith(
                firstOwner,
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, null));
        StatefulWeaponRegressionSupport.assertEquals(
                1, first.getLiyueMemberCount(),
                "UNKNOWN region does not create a Lithic stack");
        StatsContainer firstStats =
                StatefulWeaponRegressionSupport.stats(firstOwner, firstSim);
        StatefulWeaponRegressionSupport.assertClose(
                0.276 + 0.11,
                firstStats.get(StatType.ATK_PERCENT),
                "Lithic Spear R5 one-stack ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.07,
                firstStats.get(StatType.CRIT_RATE),
                "Lithic Spear R5 one-stack CRIT Rate");

        LithicSpear second = new LithicSpear(5);
        StatefulWeaponRegressionSupport.TestCharacter secondOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ROSARIA, second);
        StatefulWeaponRegressionSupport.simulatorWith(secondOwner);
        StatefulWeaponRegressionSupport.assertEquals(
                0, second.getLiyueMemberCount(),
                "independent Lithic instance has independent composition");
        StatefulWeaponRegressionSupport.assertEquals(
                1, first.getLiyueMemberCount(),
                "first Lithic instance retains its composition");
        StatefulWeaponRegressionSupport.assertTrue(
                firstSim.getPartyMembers().contains(firstOwner),
                "first Lithic simulator remains bound");
    }

    private static void testBindingGuards() {
        LithicBlade weapon = new LithicBlade(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.DILUC, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        weapon.initializeForSimulator(owner, sim);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Lithic family rejects another simulator");
        LithicBlade unequipped = new LithicBlade(1);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(owner, sim),
                "Lithic family rejects unequipped owner");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(null, sim),
                "Lithic family rejects null owner");
    }
}
