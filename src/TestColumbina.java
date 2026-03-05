import simulation.CombatSimulator;
import model.entity.*;
import model.artifact.*;
import model.character.*;
import model.type.*;
import mechanics.formula.*;
import java.util.*;

public class TestColumbina {
    public static void main(String[] args) {
        System.out.println("Starting Columbina Verification...");

        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(true);

        // Setup Character
        model.stats.StatsContainer wStats = new model.stats.StatsContainer();
        wStats.add(StatType.BASE_ATK, 542.0);
        Weapon dummyWeapon = new Weapon("Dummy Catalyst", wStats);

        ArtifactSet dummyArts = new ArtifactSet("Dummy Set", new model.stats.StatsContainer());

        Columbina columbina = new Columbina(dummyWeapon, dummyArts);
        sim.addCharacter(columbina);

        // Setup Dummy Enemy
        Enemy enemy = new Enemy(90);
        sim.setEnemy(enemy);

        System.out.println("Party Setup Complete.");

        // 1. Start Skill (Ripple)
        System.out.println("\n--- Action: Skill ---");
        columbina.onAction("skill", sim);
        sim.advanceTime(1.0);

        // 2. Simulate Electro-Charged (Lunar-Charged) via Fake Notify/Event
        System.out.println("\n--- Simulating Lunar-Charged Reaction ---");
        // We notify sim manually to test Columbina's Listener logic
        // Columbina listens to "Lunar-Charged" or standard "Electro-Charged"
        sim.notifyReaction(mechanics.reaction.ReactionResult.transform(0.0, "Electro-Charged"), columbina); // Should
                                                                                                            // convert
                                                                                                            // to Lunar
        sim.advanceTime(2.0); // Wait 2s for gravity cooldown

        System.out.println("\n--- Simulating 2 More Lunar-Charged (3 Total -> 60 Gravity -> Interference) ---");
        sim.notifyReaction(mechanics.reaction.ReactionResult.transform(0.0, "Lunar-Charged"), columbina);
        sim.advanceTime(2.0);
        sim.notifyReaction(mechanics.reaction.ReactionResult.transform(0.0, "Lunar-Charged"), columbina);
        // Expect Interference Trigger Log here

        sim.advanceTime(1.0);

        // 3. Burst (Domain)
        System.out.println("\n--- Action: Burst (Domain) ---");
        columbina.onAction("burst", sim);

        // 4. Simulate Bloom (Lunar Bloom) -> Dew Generation
        System.out.println("\n--- Simulating Bloom (Check Dew Gen) ---");
        sim.notifyReaction(mechanics.reaction.ReactionResult.transform(0.0, "Bloom"), columbina);
        sim.advanceTime(2.5);
        sim.notifyReaction(mechanics.reaction.ReactionResult.transform(0.0, "Bloom"), columbina); // Another dew

        // 5. Special CA
        System.out.println("\n--- Action: Special CA (Consume Dew) ---");
        columbina.onAction("attack_charged", sim);

        sim.printReport();
        System.out.println("Verification Complete.");
    }
}
