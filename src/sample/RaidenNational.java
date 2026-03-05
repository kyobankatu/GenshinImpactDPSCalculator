package sample;

import simulation.CombatSimulator;
import model.entity.Enemy;

import model.type.StatType;
import model.character.*;
import model.weapon.*;

import mechanics.optimization.OptimizerPipeline;
import mechanics.optimization.TotalOptimizationResult;
import mechanics.energy.EnergyManager;

public class RaidenNational {
    public static void main(String[] args) {
        System.out.println("Genshin DPS Calculator: Raiden National Simulation (Refactored)");

        // 1. Run Optimization Phase (ER Calibration + Joint Crit Optimization)
        // 1. Run Optimization Phase (ER Calibration + Joint Crit Optimization)
        java.util.Map<String, java.util.List<StatType>> optimizationTargets = new java.util.HashMap<>();

        // Raiden: Crit & ATK
        optimizationTargets.put("Raiden Shogun",
                java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT));

        // Xingqiu: Crit & ATK
        optimizationTargets.put("Xingqiu",
                java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT));

        // Xiangling: Crit, ATK, EM
        optimizationTargets.put("Xiangling", java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG,
                StatType.ATK_PERCENT, StatType.ELEMENTAL_MASTERY));

        TotalOptimizationResult optimization = OptimizerPipeline.run(
                RaidenNational::createSimulator,
                RaidenNational::executeRotation,
                optimizationTargets);

        // 2. Final Execution with Optimized Stats
        System.out.println("\n--- Starting Final Simulation ---");
        visualization.VisualLogger.getInstance().clear();

        // Create Sim with Final Config
        CombatSimulator sim = createSimulator(optimization.erTargets, optimization.partyRolls);

        // Setup Stats Recorder
        mechanics.analysis.StatsRecorder recorder = new mechanics.analysis.StatsRecorder(sim, 0.5);
        recorder.startRecording();

        executeRotation(sim);

        // 3. Print & Generate Reports
        sim.printReport();
        visualization.HtmlReportGenerator.generate("simulation_report.html",
                visualization.VisualLogger.getInstance().getRecords(), sim,
                recorder.getSnapshots());
    }

    // --- Helper Methods ---

    private static CombatSimulator createSimulator(
            java.util.Map<String, Double> erTargets,
            java.util.Map<String, java.util.Map<model.type.StatType, Integer>> partyManualRolls) {
        CombatSimulator s = new CombatSimulator();
        s.setEnemy(new Enemy(90));
        setupParty(s, erTargets != null ? erTargets : new java.util.HashMap<>(),
                partyManualRolls != null ? partyManualRolls : new java.util.HashMap<>());
        return s;
    }

    private static void executeRotation(CombatSimulator sim) {
        // Rotation: (Raiden E) > Xingqiu E Q N0 > Bennett Q N0 E > Xiangling Q E N0
        // > Raiden Q N3Cx3 N1C N0 E > Bennett E > Xiangling funnel
        // (Using previous fixed rotation logic)

        // 1. Raiden E
        sim.switchCharacter("Raiden Shogun");
        EnergyManager.scheduleKQMSEnemyParticles(sim); // Add Enemy Particles (Delegated)
        sim.performAction("Raiden Shogun", "skill");

        // 2. Xingqiu: Q E E
        sim.switchCharacter("Xingqiu");
        sim.performAction("Xingqiu", "burst"); // Q
        sim.performAction("Xingqiu", "skill"); // E
        sim.performAction("Xingqiu", "attack"); // N0 (Drive Raincutter)

        // 3. Bennett: Q N0 E
        sim.switchCharacter("Bennett");
        sim.performAction("Bennett", "burst"); // Q
        sim.performAction("Bennett", "attack"); // N0
        sim.performAction("Bennett", "skill"); // E

        // 4. Xiangling: Q N0 E N0 (Optimized to Q E N0)
        sim.switchCharacter("Xiangling");
        sim.performAction("Xiangling", "burst"); // Q
        sim.performAction("Xiangling", "attack"); // N0
        sim.performAction("Xiangling", "skill"); // E
        sim.performAction("Xiangling", "attack"); // N0

        // 5. Raiden: Q N3Cx3 N1C N0 E
        sim.switchCharacter("Raiden Shogun");
        sim.performAction("Raiden Shogun", "burst"); // Q

        // N3Cx3
        for (int i = 0; i < 3; i++) {
            sim.performAction("Raiden Shogun", "attack");
            sim.performAction("Raiden Shogun", "attack");
            sim.performAction("Raiden Shogun", "attack");
            sim.performAction("Raiden Shogun", "charge");
        }

        // N1C
        sim.performAction("Raiden Shogun", "attack");
        sim.performAction("Raiden Shogun", "charge");

        // N0 E (End)
        sim.advanceTime(0.1);
        sim.performAction("Raiden Shogun", "attack");
        sim.performAction("Raiden Shogun", "skill"); // E Refresh

        // 6. Bennett E
        sim.switchCharacter("Bennett");
        sim.performAction("Bennett", "skill"); // E
        sim.performAction("Bennett", "attack"); // N

        // 7. Xiangling Funnel
        sim.switchCharacter("Xiangling");
        sim.performAction("Xiangling", "attack"); // N

        // 8. Bennet E
        sim.switchCharacter("Bennett");
        sim.performAction("Bennett", "skill"); // E
        sim.performAction("Bennett", "attack"); // N

        // 9. Xiangling Funnel
        sim.switchCharacter("Xiangling");
        sim.performAction("Xiangling", "attack"); // N

        // Pad to full 21s rotation
        double remaining = 21.0 - sim.getCurrentTime();
        if (remaining > 0) {
            sim.advanceTime(remaining);
        }
    }

    private static void setupParty(CombatSimulator sim, java.util.Map<String, Double> erTargets,
            java.util.Map<String, java.util.Map<model.type.StatType, Integer>> partyManualRolls) {
        // --- KQMS Optimization ---

        // 1. Raiden Shogun (Emblem)
        RaidenShogun raiden = new RaidenShogun(new model.weapon.SkywardSpine(), null);
        mechanics.optimization.ArtifactOptimizer.OptimizationConfig raidenConfig = new mechanics.optimization.ArtifactOptimizer.OptimizationConfig();
        raidenConfig.mainStatSands = StatType.ENERGY_RECHARGE;
        raidenConfig.mainStatGoblet = StatType.ELECTRO_DMG_BONUS;
        raidenConfig.mainStatCirclet = StatType.CRIT_RATE;
        raidenConfig.subStatPriority = java.util.Arrays.asList(StatType.ENERGY_RECHARGE, StatType.CRIT_RATE,
                StatType.CRIT_DMG, StatType.ATK_PERCENT);

        // Dynamic ER Target
        Double calcER = erTargets.getOrDefault("Raiden Shogun", 1.0);
        System.out.println(
                "   [Setup] Raiden Shogun Calculated ER: " + String.format("%.1f", calcER * 100) + "%");
        // Force minimum 250% for Emblem/DPS
        raidenConfig.minER = Math.max(calcER, 2.50);

        // Manual Rolls Injection
        if (partyManualRolls.containsKey("Raiden Shogun")) {
            raidenConfig.manualRolls = partyManualRolls.get("Raiden Shogun");
        }

        mechanics.optimization.ArtifactOptimizer.OptimizationResult resultRaiden = mechanics.optimization.ArtifactOptimizer
                .generate(
                        raidenConfig,
                        raiden.getBaseStats(),
                        raiden.getWeapon().getStats(),
                        raiden.getWeapon().getStats().merge(new model.stats.StatsContainer()));
        // Create specific set directly with stats
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

        xqConfig.minER = erTargets.getOrDefault("Xingqiu", 1.0);

        if (partyManualRolls.containsKey("Xingqiu")) {
            xqConfig.manualRolls = partyManualRolls.get("Xingqiu");
        }

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
        xlConfig.minER = erTargets.getOrDefault("Xiangling", 1.0);

        if (partyManualRolls.containsKey("Xiangling")) {
            xlConfig.manualRolls = partyManualRolls.get("Xiangling");
        }

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
        // Bennett cares about ER and HP mostly
        bennyConfig.mainStatSands = StatType.ENERGY_RECHARGE;
        bennyConfig.mainStatGoblet = StatType.HP_PERCENT;
        bennyConfig.mainStatCirclet = StatType.HP_PERCENT;
        bennyConfig.subStatPriority = java.util.Arrays.asList(StatType.ENERGY_RECHARGE, StatType.HP_PERCENT,
                StatType.HP_FLAT);

        bennyConfig.minER = erTargets.getOrDefault("Bennett", 1.0);

        if (partyManualRolls.containsKey("Bennett")) {
            bennyConfig.manualRolls = partyManualRolls.get("Bennett");
        }

        mechanics.optimization.ArtifactOptimizer.OptimizationResult resultBenny = mechanics.optimization.ArtifactOptimizer
                .generate(
                        bennyConfig, bennett.getBaseStats(), bennett.getWeapon().getStats(),
                        new model.stats.StatsContainer());
        bennett.setArtifacts(new model.artifact.NoblesseOblige(resultBenny.stats));
        bennett.setArtifactRolls(resultBenny.rolls);
        sim.addCharacter(bennett);

        // --- Elemental Resonance ---
        mechanics.element.ResonanceManager.applyResonances(sim);
    }
}