package sample;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.BladeOfAtonement;
import model.weapon.EchoesOfTheHeart;
import model.weapon.Emberwell;
import model.weapon.SongOfTheVigil;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;

/** Focused regression checks for Version 7.0 reaction-triggered weapons. */
public final class VersionSevenReactionWeaponRegressionTest {
    private static final double EPSILON = 1e-8;

    private VersionSevenReactionWeaponRegressionTest() {
    }

    /** Runs metadata, trigger, boundary, Energy, and snapshot checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testOrdinaryAndStellarConditions();
        testOwnerAttributionAndOffFieldTriggers();
        testHalfOpenWindowsAndSnapshotRestore();
        testSongEnergyCooldownAndSnapshotRestore();
        testBindingAndStateGuards();
        System.out.println("VersionSevenReactionWeaponRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        assertMetadata(
                new Emberwell(),
                "Emberwell",
                WeaponType.SWORD,
                510.0,
                StatType.ELEMENTAL_MASTERY,
                165.0);
        assertMetadata(
                new BladeOfAtonement(),
                "Blade of Atonement",
                WeaponType.CLAYMORE,
                565.0,
                StatType.ATK_PERCENT,
                0.276);
        assertMetadata(
                new EchoesOfTheHeart(),
                "Echoes of the Heart",
                WeaponType.CATALYST,
                565.0,
                StatType.ATK_PERCENT,
                0.276);
        assertMetadata(
                new SongOfTheVigil(),
                "Song of the Vigil",
                WeaponType.POLEARM,
                565.0,
                StatType.ELEMENTAL_MASTERY,
                110.0);

        for (int refinement = 1; refinement <= 5; refinement++) {
            Emberwell emberwell = new Emberwell(refinement);
            assertEquals(refinement, emberwell.getRefinement(),
                    "Emberwell refinement");
            assertClose(0.12 + 0.04 * refinement,
                    emberwell.getOrdinaryBonus(),
                    "Emberwell ATK R" + refinement);
            assertClose(0.12 + 0.04 * refinement,
                    emberwell.getStellarBonus(),
                    "Emberwell Stellar DMG R" + refinement);

            BladeOfAtonement blade = new BladeOfAtonement(refinement);
            assertEquals(refinement, blade.getRefinement(),
                    "Blade refinement");
            assertClose(48.0 + 16.0 * refinement,
                    blade.getOrdinaryBonus(),
                    "Blade EM R" + refinement);
            assertClose(0.12 + 0.04 * refinement,
                    blade.getStellarBonus(),
                    "Blade ATK R" + refinement);

            EchoesOfTheHeart echoes = new EchoesOfTheHeart(refinement);
            assertEquals(refinement, echoes.getRefinement(),
                    "Echoes refinement");
            assertClose(45.0 + 15.0 * refinement,
                    echoes.getOrdinaryBonus(),
                    "Echoes EM R" + refinement);
            assertClose(0.12 + 0.04 * refinement,
                    echoes.getStellarBonus(),
                    "Echoes Stellar DMG R" + refinement);

            SongOfTheVigil song = new SongOfTheVigil(refinement);
            assertEquals(refinement, song.getRefinement(),
                    "Song refinement");
            assertClose(3.0 + refinement, song.getEnergyRecovery(),
                    "Song Energy R" + refinement);
            assertClose(0.15 + 0.05 * refinement,
                    song.getStellarBonus(),
                    "Song ATK R" + refinement);
        }

        assertThrows(IllegalArgumentException.class,
                () -> new Emberwell(0), "Emberwell rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new BladeOfAtonement(6), "Blade rejects R6");
        assertThrows(IllegalArgumentException.class,
                () -> new EchoesOfTheHeart(0), "Echoes rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new SongOfTheVigil(6), "Song rejects R6");
    }

    private static void testOrdinaryAndStellarConditions() {
        Emberwell ordinaryEmberwell = new Emberwell(1);
        TestCharacter ordinaryEmberOwner = owner(ordinaryEmberwell);
        CombatSimulator ordinaryEmberSim = simulator(ordinaryEmberOwner);
        ordinaryEmberSim.notifyReaction(ordinaryReaction(), ordinaryEmberOwner);
        StatsContainer ordinaryEmberStats = stats(ordinaryEmberOwner, ordinaryEmberSim);
        assertClose(0.16, ordinaryEmberStats.get(StatType.ATK_PERCENT),
                "Emberwell ordinary reaction ATK");
        assertClose(0.0,
                ordinaryEmberStats.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Emberwell ordinary reaction does not open Stellar window");

        Emberwell stellarEmberwell = new Emberwell(1);
        TestCharacter stellarEmberOwner = owner(stellarEmberwell);
        CombatSimulator stellarEmberSim = simulator(stellarEmberOwner);
        stellarEmberSim.notifyReaction(stellarConduct(), stellarEmberOwner);
        StatsContainer stellarEmberStats = stats(stellarEmberOwner, stellarEmberSim);
        assertClose(0.16, stellarEmberStats.get(StatType.ATK_PERCENT),
                "Stellar reaction also opens Emberwell ordinary window");
        assertClose(0.16,
                stellarEmberStats.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Emberwell Stellar-Conduct bonus");
        assertClose(0.16,
                stellarEmberStats.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                "Emberwell Stellar-Swirl bonus");

        BladeOfAtonement blade = new BladeOfAtonement(1);
        TestCharacter bladeOwner = owner(blade);
        CombatSimulator bladeSim = simulator(bladeOwner);
        bladeSim.notifyReaction(stellarSwirl(), bladeOwner);
        StatsContainer bladeStats = stats(bladeOwner, bladeSim);
        assertClose(64.0, bladeStats.get(StatType.ELEMENTAL_MASTERY),
                "Blade ordinary reaction EM");
        assertClose(0.276 + 0.16, bladeStats.get(StatType.ATK_PERCENT),
                "Blade Stellar reaction ATK");

        EchoesOfTheHeart echoes = new EchoesOfTheHeart(1);
        TestCharacter echoesOwner = owner(echoes);
        CombatSimulator echoesSim = simulator(echoesOwner);
        echoesSim.notifyReaction(stellarSwirl(), echoesOwner);
        StatsContainer echoesStats = stats(echoesOwner, echoesSim);
        assertClose(60.0, echoesStats.get(StatType.ELEMENTAL_MASTERY),
                "Echoes ordinary reaction EM");
        assertClose(0.16,
                echoesStats.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Echoes Stellar-Conduct bonus");
        assertClose(0.16,
                echoesStats.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                "Echoes Stellar-Swirl bonus");

        SongOfTheVigil song = new SongOfTheVigil(1);
        TestCharacter songOwner = owner(song);
        CombatSimulator songSim = simulator(songOwner);
        songSim.notifyReaction(ordinaryReaction(), songOwner);
        assertClose(0.0,
                stats(songOwner, songSim).get(StatType.ATK_PERCENT),
                "Song ordinary reaction does not grant ATK");
        songSim.notifyReaction(stellarConduct(), songOwner);
        assertClose(0.20,
                stats(songOwner, songSim).get(StatType.ATK_PERCENT),
                "Song Stellar reaction grants ATK");
    }

    private static void testOwnerAttributionAndOffFieldTriggers() {
        for (Weapon weapon : weaponsAtRefinementOne()) {
            TestCharacter equippedOwner = owner(weapon);
            TestCharacter ally = character(CharacterId.AMBER, null);
            CombatSimulator sim = simulator(equippedOwner, ally);

            sim.notifyReaction(stellarConduct(), ally);
            sim.notifyDerivedReaction(stellarSwirl(), equippedOwner);
            sim.notifyReaction(ReactionResult.none(), equippedOwner);
            assertTrue(!isOrdinaryWindowActive(weapon, 0.0),
                    weapon.getName() + " rejects foreign, derived, and NONE events");
            assertTrue(!isStellarWindowActive(weapon, 0.0),
                    weapon.getName() + " keeps Stellar window closed");

            sim.setActiveCharacter(CharacterId.AMBER);
            sim.notifyReaction(stellarConduct(), equippedOwner);
            assertTrue(isOrdinaryWindowActive(weapon, 0.0),
                    weapon.getName() + " triggers while owner is off field");
            assertTrue(isStellarWindowActive(weapon, 0.0),
                    weapon.getName() + " opens Stellar window off field");
        }
    }

    private static void testHalfOpenWindowsAndSnapshotRestore() {
        for (Weapon weapon : weaponsAtRefinementOne()) {
            TestCharacter equippedOwner = owner(weapon);
            CombatSimulator sim = simulator(equippedOwner);
            sim.notifyReaction(stellarConduct(), equippedOwner);
            SimulatorSnapshot snapshot = sim.saveSnapshot();

            assertTrue(isOrdinaryWindowActive(weapon, 12.0 - EPSILON),
                    weapon.getName() + " ordinary window before expiry");
            assertTrue(!isOrdinaryWindowActive(weapon, 12.0),
                    weapon.getName() + " ordinary window is half-open");
            assertTrue(isStellarWindowActive(weapon, 12.0 - EPSILON),
                    weapon.getName() + " Stellar window before expiry");
            assertTrue(!isStellarWindowActive(weapon, 12.0),
                    weapon.getName() + " Stellar window is half-open");

            sim.advanceTime(5.0);
            sim.notifyReaction(stellarSwirl(), equippedOwner);
            assertClose(17.0, ordinaryWindowUntil(weapon),
                    weapon.getName() + " refreshes ordinary window");
            assertClose(17.0, stellarWindowUntil(weapon),
                    weapon.getName() + " refreshes Stellar window");
            sim.restoreSnapshot(snapshot);
            assertClose(12.0, ordinaryWindowUntil(weapon),
                    weapon.getName() + " snapshot restores ordinary window");
            assertClose(12.0, stellarWindowUntil(weapon),
                    weapon.getName() + " snapshot restores Stellar window");
        }
    }

    private static void testSongEnergyCooldownAndSnapshotRestore() {
        SongOfTheVigil song = new SongOfTheVigil(1);
        TestCharacter equippedOwner = owner(song);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulator(equippedOwner, ally);
        sim.setActiveCharacter(CharacterId.AMBER);
        equippedOwner.spendEnergy(60.0);

        sim.notifyReaction(ordinaryReaction(), equippedOwner);
        assertClose(4.0, equippedOwner.getCurrentEnergy(),
                "Song restores flat Energy off field");
        assertClose(9.0, song.getNextEnergyRecoveryAt(),
                "Song starts nine-second Energy cooldown");
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        sim.notifyReaction(stellarConduct(), equippedOwner);
        assertClose(4.0, equippedOwner.getCurrentEnergy(),
                "Song Energy cooldown rejects same-time Stellar reaction");
        sim.advanceTime(9.0 - EPSILON);
        sim.notifyReaction(ordinaryReaction(), equippedOwner);
        assertClose(4.0, equippedOwner.getCurrentEnergy(),
                "Song Energy cooldown remains closed before boundary");
        sim.advanceTime(EPSILON);
        sim.notifyReaction(ordinaryReaction(), equippedOwner);
        assertClose(8.0, equippedOwner.getCurrentEnergy(),
                "Song Energy cooldown opens exactly at nine seconds");

        sim.restoreSnapshot(snapshot);
        assertClose(4.0, equippedOwner.getCurrentEnergy(),
                "Song snapshot restores owner Energy");
        assertClose(9.0, song.getNextEnergyRecoveryAt(),
                "Song snapshot restores Energy cooldown");
        sim.notifyReaction(ordinaryReaction(), equippedOwner);
        assertClose(4.0, equippedOwner.getCurrentEnergy(),
                "Song restored cooldown remains active");
        sim.advanceTime(9.0);
        sim.notifyReaction(ordinaryReaction(), equippedOwner);
        assertClose(8.0, equippedOwner.getCurrentEnergy(),
                "Song restored cooldown reopens at its original boundary");
    }

    private static void testBindingAndStateGuards() {
        Emberwell weapon = new Emberwell(1);
        TestCharacter equippedOwner = owner(weapon);
        CombatSimulator sim = simulator(equippedOwner);
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        equippedOwner, new CombatSimulator()),
                "reaction weapon rejects cross-simulator reuse");

        Emberwell unequipped = new Emberwell(1);
        TestCharacter bareOwner = owner(null);
        CombatSimulator bareSim = simulator(bareOwner);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(bareOwner, bareSim),
                "reaction weapon rejects unequipped binding");

        SnapshotAwareWeaponEffect.State foreign =
                new Emberwell(1).captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreign),
                "reaction weapon rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "reaction weapon rejects null state");
    }

    private static void assertMetadata(
            Weapon weapon,
            String expectedName,
            WeaponType expectedType,
            double expectedBaseAtk,
            StatType secondaryStat,
            double expectedSecondaryValue) {
        assertEquals(expectedName, weapon.getName(), expectedName + " name");
        assertEquals(expectedType, weapon.getWeaponType(), expectedName + " type");
        assertClose(expectedBaseAtk, weapon.getStats().get(StatType.BASE_ATK),
                expectedName + " base ATK");
        assertClose(expectedSecondaryValue, weapon.getStats().get(secondaryStat),
                expectedName + " secondary stat");
    }

    private static Weapon[] weaponsAtRefinementOne() {
        return new Weapon[] {
                new Emberwell(1),
                new BladeOfAtonement(1),
                new EchoesOfTheHeart(1),
                new SongOfTheVigil(1)
        };
    }

    private static ReactionResult ordinaryReaction() {
        return ReactionResult.transform(
                1.0,
                "Overloaded",
                ReactionResult.Kind.OVERLOAD,
                Element.PYRO,
                Element.PYRO);
    }

    private static ReactionResult stellarConduct() {
        return ReactionResult.stellar(
                0.0,
                ReactionResult.Kind.STELLAR_CONDUCT,
                Element.CRYO,
                Element.CRYO,
                true);
    }

    private static ReactionResult stellarSwirl() {
        return ReactionResult.stellar(
                1.0,
                ReactionResult.Kind.STELLAR_SWIRL,
                Element.CRYO,
                Element.ANEMO,
                false);
    }

    private static TestCharacter owner(Weapon weapon) {
        return character(CharacterId.FISCHL, weapon);
    }

    private static TestCharacter character(CharacterId id, Weapon weapon) {
        return new TestCharacter(id, weapon);
    }

    private static CombatSimulator simulator(TestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (TestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    private static StatsContainer stats(
            TestCharacter character,
            CombatSimulator sim) {
        return character.getEffectiveStats(sim.getCurrentTime());
    }

    private static boolean isOrdinaryWindowActive(
            Weapon weapon,
            double currentTime) {
        if (weapon instanceof Emberwell) {
            return ((Emberwell) weapon).isOrdinaryWindowActive(currentTime);
        }
        if (weapon instanceof BladeOfAtonement) {
            return ((BladeOfAtonement) weapon).isOrdinaryWindowActive(currentTime);
        }
        if (weapon instanceof EchoesOfTheHeart) {
            return ((EchoesOfTheHeart) weapon).isOrdinaryWindowActive(currentTime);
        }
        return ((SongOfTheVigil) weapon).isOrdinaryWindowActive(currentTime);
    }

    private static boolean isStellarWindowActive(
            Weapon weapon,
            double currentTime) {
        if (weapon instanceof Emberwell) {
            return ((Emberwell) weapon).isStellarWindowActive(currentTime);
        }
        if (weapon instanceof BladeOfAtonement) {
            return ((BladeOfAtonement) weapon).isStellarWindowActive(currentTime);
        }
        if (weapon instanceof EchoesOfTheHeart) {
            return ((EchoesOfTheHeart) weapon).isStellarWindowActive(currentTime);
        }
        return ((SongOfTheVigil) weapon).isStellarWindowActive(currentTime);
    }

    private static double ordinaryWindowUntil(Weapon weapon) {
        if (weapon instanceof Emberwell) {
            return ((Emberwell) weapon).getOrdinaryWindowUntil();
        }
        if (weapon instanceof BladeOfAtonement) {
            return ((BladeOfAtonement) weapon).getOrdinaryWindowUntil();
        }
        if (weapon instanceof EchoesOfTheHeart) {
            return ((EchoesOfTheHeart) weapon).getOrdinaryWindowUntil();
        }
        return ((SongOfTheVigil) weapon).getOrdinaryWindowUntil();
    }

    private static double stellarWindowUntil(Weapon weapon) {
        if (weapon instanceof Emberwell) {
            return ((Emberwell) weapon).getStellarWindowUntil();
        }
        if (weapon instanceof BladeOfAtonement) {
            return ((BladeOfAtonement) weapon).getStellarWindowUntil();
        }
        if (weapon instanceof EchoesOfTheHeart) {
            return ((EchoesOfTheHeart) weapon).getStellarWindowUntil();
        }
        return ((SongOfTheVigil) weapon).getStellarWindowUntil();
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(
            Class<? extends Throwable> expected,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    /** Minimal deterministic weapon owner with a nonzero Energy cap. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Weapon equippedWeapon) {
            characterId = id;
            name = id.getDisplayName();
            element = Element.ELECTRO;
            weapon = equippedWeapon;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
        }

        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }
    }
}
