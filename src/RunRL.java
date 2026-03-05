import mechanics.rl.RLServer;
import simulation.CombatSimulator;
import model.entity.Enemy;
import model.type.StatType;
import model.character.RaidenShogun;

public class RunRL {
        public static void main(String[] args) {
                // Setup Logging Silencer
                mechanics.rl.RLServer.originalOut = System.out;
                java.io.PrintStream dummy = new java.io.PrintStream(new java.io.OutputStream() {
                        public void write(int b) {
                                // Muted
                        }
                });

                // Mute EVERYTHING except when verbose is true
                // But System.setOut doesn't check dynamic flag easily unless we subclass
                // PrintStream.
                // Simpler: Redirect to dummy by default.
                // Any ESSENTIAL log should use RLServer.originalOut.println().
                System.setOut(dummy);

                mechanics.rl.RLServer.originalOut.println("Starting Genshin RL Server...");

                // Factory to create fresh simulation instances for each episode
                java.util.function.Supplier<CombatSimulator> simFactory = () -> {
                        CombatSimulator sim = new CombatSimulator();
                        sim.setEnemy(new Enemy(90));
                        // Use default ER targets or hardcoded ones for training stability
                        setupParty(sim);
                        return sim;
                };

                RLServer server = new RLServer(5000, simFactory);
                server.start();
        }

        // Copied & Simplified from Main.java
        // Focusing on standard KQM standards for training
        private static void setupParty(CombatSimulator sim) {
                // 1. Raiden Shogun (Emblem)
                RaidenShogun raiden = new RaidenShogun(new model.weapon.SkywardSpine(), null);
                mechanics.optimization.ArtifactOptimizer.OptimizationConfig raidenConfig = new mechanics.optimization.ArtifactOptimizer.OptimizationConfig();
                raidenConfig.mainStatSands = StatType.ENERGY_RECHARGE;
                raidenConfig.mainStatGoblet = StatType.ELECTRO_DMG_BONUS;
                raidenConfig.mainStatCirclet = StatType.CRIT_RATE;
                raidenConfig.subStatPriority = java.util.Arrays.asList(StatType.ENERGY_RECHARGE, StatType.CRIT_RATE,
                                StatType.CRIT_DMG, StatType.ATK_PERCENT);
                raidenConfig.minER = 2.50;

                mechanics.optimization.ArtifactOptimizer.OptimizationResult resultRaiden = mechanics.optimization.ArtifactOptimizer
                                .generate(
                                                raidenConfig, raiden.getBaseStats(), raiden.getWeapon().getStats(),
                                                raiden.getWeapon().getStats().merge(new model.stats.StatsContainer()));
                raiden.setArtifacts(new model.artifact.EmblemOfSeveredFate(resultRaiden.stats));
                raiden.setArtifactRolls(resultRaiden.rolls);
                sim.addCharacter(raiden);

                // 2. Xingqiu (Emblem)
                model.character.Xingqiu xq = new model.character.Xingqiu(new model.weapon.WolfFang(), null);
                mechanics.optimization.ArtifactOptimizer.OptimizationConfig xqConfig = new mechanics.optimization.ArtifactOptimizer.OptimizationConfig();
                xqConfig.mainStatSands = StatType.ATK_PERCENT;
                xqConfig.mainStatGoblet = StatType.HYDRO_DMG_BONUS;
                xqConfig.mainStatCirclet = StatType.CRIT_RATE;
                xqConfig.subStatPriority = java.util.Arrays.asList(StatType.ENERGY_RECHARGE, StatType.CRIT_RATE,
                                StatType.CRIT_DMG, StatType.ATK_PERCENT);
                xqConfig.minER = 1.80;

                mechanics.optimization.ArtifactOptimizer.OptimizationResult resultXq = mechanics.optimization.ArtifactOptimizer
                                .generate(
                                                xqConfig, xq.getBaseStats(), xq.getWeapon().getStats(),
                                                new model.stats.StatsContainer());
                xq.setArtifacts(new model.artifact.EmblemOfSeveredFate(resultXq.stats));
                xq.setArtifactRolls(resultXq.rolls);
                sim.addCharacter(xq);

                // 3. Xiangling (Emblem)
                model.character.Xiangling xl = new model.character.Xiangling(new model.weapon.TheCatch(), null);
                mechanics.optimization.ArtifactOptimizer.OptimizationConfig xlConfig = new mechanics.optimization.ArtifactOptimizer.OptimizationConfig();
                xlConfig.mainStatSands = StatType.ATK_PERCENT;
                xlConfig.mainStatGoblet = StatType.PYRO_DMG_BONUS;
                xlConfig.mainStatCirclet = StatType.CRIT_RATE;
                xlConfig.subStatPriority = java.util.Arrays.asList(StatType.ENERGY_RECHARGE, StatType.CRIT_RATE,
                                StatType.CRIT_DMG, StatType.ATK_PERCENT, StatType.ELEMENTAL_MASTERY);
                xlConfig.minER = 2.00;

                mechanics.optimization.ArtifactOptimizer.OptimizationResult resultXl = mechanics.optimization.ArtifactOptimizer
                                .generate(
                                                xlConfig, xl.getBaseStats(), xl.getWeapon().getStats(),
                                                new model.stats.StatsContainer());
                xl.setArtifacts(new model.artifact.EmblemOfSeveredFate(resultXl.stats));
                xl.setArtifactRolls(resultXl.rolls);
                sim.addCharacter(xl);

                // 4. Bennett (Noblesse)
                model.character.Bennett bennett = new model.character.Bennett(new model.weapon.SkywardBlade(), null);
                mechanics.optimization.ArtifactOptimizer.OptimizationConfig bennyConfig = new mechanics.optimization.ArtifactOptimizer.OptimizationConfig();
                bennyConfig.mainStatSands = StatType.ENERGY_RECHARGE;
                bennyConfig.mainStatGoblet = StatType.HP_PERCENT;
                bennyConfig.mainStatCirclet = StatType.HP_PERCENT;
                bennyConfig.subStatPriority = java.util.Arrays.asList(StatType.ENERGY_RECHARGE, StatType.HP_PERCENT,
                                StatType.HP_FLAT);
                bennyConfig.minER = 2.20;

                mechanics.optimization.ArtifactOptimizer.OptimizationResult resultBenny = mechanics.optimization.ArtifactOptimizer
                                .generate(
                                                bennyConfig, bennett.getBaseStats(), bennett.getWeapon().getStats(),
                                                new model.stats.StatsContainer());
                bennett.setArtifacts(new model.artifact.NoblesseOblige(resultBenny.stats));
                bennett.setArtifactRolls(resultBenny.rolls);
                sim.addCharacter(bennett);

                // Elemental Resonance
                mechanics.element.ResonanceManager.applyResonances(sim);
        }
}
