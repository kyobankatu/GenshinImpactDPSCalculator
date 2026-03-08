package sample;

import mechanics.rl.FlinsParty2Rotation;
import mechanics.rl.RLServer;
import mechanics.rl.RotationPhase;
import model.entity.Enemy;
import model.type.StatType;
import simulation.CombatSimulator;

import java.util.List;

/**
 * Entry point for the FlinsParty2 RL training server.
 * Constructs the party, defines the sim factory, and starts {@link RLServer}.
 *
 * Run: {@code ./gradlew RunRL}
 * Then in a separate terminal: {@code ./gradlew TrainRL} or {@code ./gradlew EnjoyRL}
 */
public class RunRL {
    public static void main(String[] args) {
        // Capture stdout before muting so RLServer can log essential messages
        RLServer.originalOut = System.out;
        java.io.PrintStream dummy = new java.io.PrintStream(new java.io.OutputStream() {
            public void write(int b) {
                // Muted
            }
        });
        System.setOut(dummy);

        RLServer.originalOut.println("Starting Genshin RL Server...");

        // Factory: creates a fresh sim for each episode
        java.util.function.Supplier<CombatSimulator> simFactory = () -> {
            CombatSimulator sim = new CombatSimulator();
            sim.setEnemy(new Enemy(90));
            setupParty(sim);
            return sim;
        };

        List<RotationPhase> rotation = FlinsParty2Rotation.build();
        RLServer server = new RLServer(5000, simFactory, rotation);
        server.start();
    }

    /**
     * Sets up FlinsParty2 with static ER targets for training stability.
     * Mirrors {@code sample.FlinsParty2} but skips the optimizer pipeline.
     */
    private static void setupParty(CombatSimulator sim) {
        // 1. Flins (Night of the Sky's Unveiling)
        model.character.Flins flins = new model.character.Flins(new model.weapon.PrimordialJadeWingedSpear(), null);
        mechanics.optimization.ArtifactOptimizer.OptimizationConfig flinsConfig = new mechanics.optimization.ArtifactOptimizer.OptimizationConfig();
        flinsConfig.mainStatSands = StatType.ATK_PERCENT;
        flinsConfig.mainStatGoblet = StatType.ATK_PERCENT;
        flinsConfig.mainStatCirclet = StatType.CRIT_DMG;
        flinsConfig.subStatPriority = java.util.Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE);
        flinsConfig.minER = 1.0;

        mechanics.optimization.ArtifactOptimizer.OptimizationResult resultFlins = mechanics.optimization.ArtifactOptimizer
                .generate(flinsConfig, flins.getBaseStats(), flins.getWeapon().getStats(),
                        new model.stats.StatsContainer());
        flins.setArtifacts(new model.artifact.NightOfTheSkysUnveiling(resultFlins.stats));
        flins.setArtifactRolls(resultFlins.rolls);
        sim.addCharacter(flins);

        // 2. Ineffa (Silken Moons Serenade)
        model.character.Ineffa ineffa = new model.character.Ineffa(new model.weapon.CalamityQueller(), null);
        mechanics.optimization.ArtifactOptimizer.OptimizationConfig ineffaConfig = new mechanics.optimization.ArtifactOptimizer.OptimizationConfig();
        ineffaConfig.mainStatSands = StatType.ATK_PERCENT;
        ineffaConfig.mainStatGoblet = StatType.ATK_PERCENT;
        ineffaConfig.mainStatCirclet = StatType.CRIT_RATE;
        ineffaConfig.subStatPriority = java.util.Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE);
        ineffaConfig.minER = 1.2;

        mechanics.optimization.ArtifactOptimizer.OptimizationResult resultIneffa = mechanics.optimization.ArtifactOptimizer
                .generate(ineffaConfig, ineffa.getBaseStats(), ineffa.getWeapon().getStats(),
                        new model.stats.StatsContainer());
        ineffa.setArtifacts(new model.artifact.SilkenMoonsSerenade(resultIneffa.stats));
        ineffa.setArtifactRolls(resultIneffa.rolls);
        sim.addCharacter(ineffa);

        // 3. Columbina (Aubade of Morningstar and Moon)
        model.character.Columbina columbina = new model.character.Columbina(new model.weapon.NocturnesCurtainCall(), null);
        mechanics.optimization.ArtifactOptimizer.OptimizationConfig colConfig = new mechanics.optimization.ArtifactOptimizer.OptimizationConfig();
        colConfig.mainStatSands = StatType.ENERGY_RECHARGE;
        colConfig.mainStatGoblet = StatType.HP_PERCENT;
        colConfig.mainStatCirclet = StatType.CRIT_RATE;
        colConfig.subStatPriority = java.util.Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.HP_PERCENT, StatType.ENERGY_RECHARGE);
        colConfig.minER = 1.6;

        mechanics.optimization.ArtifactOptimizer.OptimizationResult resultCol = mechanics.optimization.ArtifactOptimizer
                .generate(colConfig, columbina.getBaseStats(), columbina.getWeapon().getStats(),
                        new model.stats.StatsContainer());
        columbina.setArtifacts(new model.artifact.AubadeOfMorningstarAndMoon(resultCol.stats));
        columbina.setArtifactRolls(resultCol.rolls);
        sim.addCharacter(columbina);

        // 4. Sucrose (Viridescent Venerer)
        model.character.Sucrose sucrose = new model.character.Sucrose(new model.weapon.SunnyMorningSleepIn(), null);
        mechanics.optimization.ArtifactOptimizer.OptimizationConfig sucConfig = new mechanics.optimization.ArtifactOptimizer.OptimizationConfig();
        sucConfig.mainStatSands = StatType.ELEMENTAL_MASTERY;
        sucConfig.mainStatGoblet = StatType.ELEMENTAL_MASTERY;
        sucConfig.mainStatCirclet = StatType.ELEMENTAL_MASTERY;
        sucConfig.subStatPriority = java.util.Arrays.asList(StatType.ELEMENTAL_MASTERY,
                StatType.ENERGY_RECHARGE);
        sucConfig.minER = 1.4;

        mechanics.optimization.ArtifactOptimizer.OptimizationResult resultSuc = mechanics.optimization.ArtifactOptimizer
                .generate(sucConfig, sucrose.getBaseStats(), sucrose.getWeapon().getStats(),
                        new model.stats.StatsContainer());
        sucrose.setArtifacts(new model.artifact.ViridescentVenerer(resultSuc.stats));
        sucrose.setArtifactRolls(resultSuc.rolls);
        sim.addCharacter(sucrose);

        // Elemental Resonance + Moonsign
        mechanics.element.ResonanceManager.applyResonances(sim);
        sim.updateMoonsign();
    }
}
