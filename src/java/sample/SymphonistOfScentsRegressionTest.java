package sample;

import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.SymphonistOfScents;
import simulation.CombatSimulator;

/** Focused checks for Symphonist of Scents' field-aware ATK contract. */
public final class SymphonistOfScentsRegressionTest {
    private SymphonistOfScentsRegressionTest() {
    }

    /** Runs metadata, field routing, unavailable healing, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testUnboundPermanentTier();
        testLiveFieldRoutingAndIsolation();
        testBindingGuards();
        System.out.println("SymphonistOfScentsRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        SymphonistOfScents defaultWeapon =
                new SymphonistOfScents();
        StatefulWeaponRegressionSupport.assertEquals(
                "Symphonist of Scents", defaultWeapon.getName(),
                "Symphonist display name");
        StatefulWeaponRegressionSupport.assertEquals(
                WeaponType.POLEARM, defaultWeapon.getWeaponType(),
                "Symphonist weapon type");
        StatefulWeaponRegressionSupport.assertEquals(
                5, defaultWeapon.getRefinement(),
                "Symphonist default refinement");
        StatefulWeaponRegressionSupport.assertClose(
                608.0, defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Symphonist base ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.662, defaultWeapon.getStats().get(StatType.CRIT_DMG),
                "Symphonist CRIT DMG");
        for (int refinement = 1; refinement <= 5; refinement++) {
            SymphonistOfScents weapon =
                    new SymphonistOfScents(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.09 + 0.03 * refinement,
                    weapon.getAttackTier(),
                    "Symphonist ATK tier R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.24 + 0.08 * refinement,
                    weapon.getUnavailableHealingAttackBonus(),
                    "Symphonist healing-window value R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new SymphonistOfScents(0),
                "Symphonist rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new SymphonistOfScents(6),
                "Symphonist rejects R6");
    }

    private static void testUnboundPermanentTier() {
        SymphonistOfScents weapon = new SymphonistOfScents(1);
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.ATK_PERCENT, 0.10);
        weapon.applyPassive(stats, -1_000.0);
        StatefulWeaponRegressionSupport.assertClose(
                0.22, stats.get(StatType.ATK_PERCENT),
                "Unbound Symphonist applies only permanent R1 tier");
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isOwnerOffField(),
                "Unbound Symphonist does not invent field state");
    }

    private static void testLiveFieldRoutingAndIsolation() {
        SymphonistOfScents weapon = new SymphonistOfScents(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.BENNETT, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        StatefulWeaponRegressionSupport.assertClose(
                0.12,
                owner.getEffectiveStats(0.0).get(StatType.ATK_PERCENT),
                "On-field Symphonist owner receives one R1 tier");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                ally.getEffectiveStats(0.0).get(StatType.ATK_PERCENT),
                "Symphonist does not buff allies without healing event");

        simulator.setActiveCharacter(CharacterId.BENNETT);
        StatefulWeaponRegressionSupport.assertTrue(
                weapon.isOwnerOffField(),
                "Symphonist observes owner switch-out live");
        StatefulWeaponRegressionSupport.assertClose(
                0.24,
                owner.getEffectiveStats(0.0).get(StatType.ATK_PERCENT),
                "Off-field Symphonist owner receives two R1 tiers");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                ally.getEffectiveStats(0.0).get(StatType.ATK_PERCENT),
                "Inactive healing branch remains isolated from active ally");

        simulator.setActiveCharacter(CharacterId.XIANGLING);
        StatefulWeaponRegressionSupport.assertClose(
                0.12,
                owner.getEffectiveStats(0.0).get(StatType.ATK_PERCENT),
                "Symphonist removes off-field tier immediately on return");
    }

    private static void testBindingGuards() {
        SymphonistOfScents weapon = new SymphonistOfScents(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Symphonist rejects cross-simulator reuse");

        SymphonistOfScents unequipped = new SymphonistOfScents(1);
        StatefulWeaponRegressionSupport.TestCharacter bare =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, null);
        CombatSimulator bareSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(bare);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        bare, bareSimulator),
                "Symphonist rejects unequipped binding");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new SymphonistOfScents(1)
                        .initializeForSimulator(null, simulator),
                "Symphonist rejects null owner binding");

        SymphonistOfScents absent = new SymphonistOfScents(1);
        StatefulWeaponRegressionSupport.TestCharacter outsider =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, absent);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> absent.initializeForSimulator(
                        outsider, new CombatSimulator()),
                "Symphonist rejects owner outside target party");
    }
}
