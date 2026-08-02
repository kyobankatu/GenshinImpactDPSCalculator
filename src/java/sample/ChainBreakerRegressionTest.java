package sample;

import model.entity.Character;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.ChainBreaker;
import simulation.CombatSimulator;

/** Focused metadata, union, threshold, cap, and binding checks. */
public final class ChainBreakerRegressionTest {
    private ChainBreakerRegressionTest() {
    }

    /** Runs all Chain Breaker regression cases. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testUnionRule();
        testThreeMemberThresholdAndCap();
        testUnknownAndBindingGuards();
        System.out.println("ChainBreakerRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new ChainBreaker().getRefinement(),
                "Chain Breaker default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            ChainBreaker weapon = new ChainBreaker(refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    "Chain Breaker", weapon.getName(),
                    "Chain Breaker name R" + refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    WeaponType.BOW, weapon.getWeaponType(),
                    "Chain Breaker type R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    565.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Chain Breaker base ATK R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.276, weapon.getStats().get(StatType.ATK_PERCENT),
                    "Chain Breaker secondary ATK R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new ChainBreaker(0),
                "Chain Breaker refinement zero");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new ChainBreaker(6),
                "Chain Breaker refinement six");
    }

    private static void testUnionRule() {
        StatefulWeaponRegressionSupport.assertTrue(
                ChainBreaker.isQualifyingMember(
                        CharacterRegion.NATLAN, Element.PYRO, Element.PYRO),
                "Natlan same-element member qualifies");
        StatefulWeaponRegressionSupport.assertTrue(
                ChainBreaker.isQualifyingMember(
                        CharacterRegion.NATLAN, Element.PYRO, Element.CRYO),
                "Natlan different-element member is counted once by union");
        StatefulWeaponRegressionSupport.assertTrue(
                ChainBreaker.isQualifyingMember(
                        CharacterRegion.UNKNOWN, Element.PYRO, Element.CRYO),
                "UNKNOWN different-element member qualifies by element");
        StatefulWeaponRegressionSupport.assertTrue(
                !ChainBreaker.isQualifyingMember(
                        CharacterRegion.UNKNOWN, Element.PYRO, Element.PYRO),
                "UNKNOWN same-element member fails closed");
        StatefulWeaponRegressionSupport.assertTrue(
                ChainBreaker.isQualifyingMember(
                        CharacterRegion.NATLAN, null, null),
                "Natlan membership does not require element metadata");
        StatefulWeaponRegressionSupport.assertTrue(
                !ChainBreaker.isQualifyingMember(null, null, null),
                "missing region and elements fail closed");
    }

    private static void testThreeMemberThresholdAndCap() {
        ChainBreaker weapon = new ChainBreaker(1);
        TestCharacter owner = character(
                CharacterId.BENNETT, Element.PYRO, weapon);
        CombatSimulator sim = simulatorWith(
                owner,
                character(CharacterId.GANYU, Element.CRYO, null),
                character(CharacterId.KEQING, Element.ELECTRO, null),
                character(CharacterId.XINGQIU, Element.HYDRO, null));
        StatefulWeaponRegressionSupport.assertEquals(
                3, weapon.getQualifyingMemberCount(),
                "Chain Breaker exactly three qualifying members");
        StatsContainer stats = owner.getEffectiveStats(sim.getCurrentTime());
        StatefulWeaponRegressionSupport.assertClose(
                0.276 + 3.0 * 0.048,
                stats.get(StatType.ATK_PERCENT),
                "Chain Breaker three-member ATK");
        StatefulWeaponRegressionSupport.assertClose(
                24.0, stats.get(StatType.ELEMENTAL_MASTERY),
                "Chain Breaker three-member EM threshold");

        ChainBreaker r5Weapon = new ChainBreaker(5);
        TestCharacter r5Owner = character(
                CharacterId.BENNETT, Element.PYRO, r5Weapon);
        CombatSimulator r5Sim = simulatorWith(
                r5Owner,
                character(CharacterId.GANYU, Element.CRYO, null),
                character(CharacterId.KEQING, Element.ELECTRO, null),
                character(CharacterId.XINGQIU, Element.HYDRO, null));
        StatsContainer r5Stats =
                r5Owner.getEffectiveStats(r5Sim.getCurrentTime());
        StatefulWeaponRegressionSupport.assertClose(
                0.276 + 3.0 * 0.096,
                r5Stats.get(StatType.ATK_PERCENT),
                "Chain Breaker R5 three-member ATK");
        StatefulWeaponRegressionSupport.assertClose(
                48.0, r5Stats.get(StatType.ELEMENTAL_MASTERY),
                "Chain Breaker R5 three-member EM");

        sim.addCharacter(character(CharacterId.NINGGUANG, Element.GEO, null));
        sim.addCharacter(character(CharacterId.ROSARIA, Element.CRYO, null));
        StatefulWeaponRegressionSupport.assertEquals(
                4, weapon.getQualifyingMemberCount(),
                "Chain Breaker qualifying count caps at four");
    }

    private static void testUnknownAndBindingGuards() {
        ChainBreaker weapon = new ChainBreaker(5);
        TestCharacter owner = character(
                CharacterId.BENNETT, Element.PYRO, weapon);
        TestCharacter unknown = character(
                CharacterId.UNKNOWN, Element.PYRO, null);
        CombatSimulator sim = simulatorWith(owner, unknown);
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getQualifyingMemberCount(),
                "UNKNOWN same-element member does not qualify");
        weapon.initializeForSimulator(owner, sim);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Chain Breaker rejects another simulator");
        ChainBreaker unequipped = new ChainBreaker(1);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(owner, sim),
                "Chain Breaker rejects unequipped owner");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(null, sim),
                "Chain Breaker rejects null owner");
    }

    private static TestCharacter character(
            CharacterId id,
            Element element,
            model.entity.Weapon weapon) {
        return new TestCharacter(id, element, weapon);
    }

    private static CombatSimulator simulatorWith(TestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        for (TestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    /** Minimal character with configurable element for composition tests. */
    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element characterElement,
                model.entity.Weapon equippedWeapon) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
            weapon = equippedWeapon;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }
    }
}
