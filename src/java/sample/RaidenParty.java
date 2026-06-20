package sample;

import simulation.CombatSimulator;
import model.entity.Enemy;

import model.type.StatType;
import model.type.CharacterId;
import model.character.*;
import model.weapon.*;

import mechanics.optimization.OptimizerPipeline;
import mechanics.optimization.TotalOptimizationResult;
import mechanics.energy.EnergyManager;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Sample entry point that runs the classic Raiden National team rotation.
 *
 * <p>Optimizes artifacts with {@link OptimizerPipeline}, executes a fixed
 * 21-second rotation, then writes a text report plus an interactive HTML
 * report under {@code output/}.
 */
public class RaidenParty {
    /**
     * Application entry point.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        System.out.println("Genshin DPS Calculator: Raiden National Simulation (Refactored)");

        // 1. Run Optimization Phase (ER Calibration + Joint Crit Optimization)
        // 1. Run Optimization Phase (ER Calibration + Joint Crit Optimization)
        java.util.Map<CharacterId, java.util.List<StatType>> optimizationTargets = new java.util.HashMap<>();

        // Raiden: Crit & ATK
        optimizationTargets.put(CharacterId.RAIDEN_SHOGUN,
                java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT));

        // Xingqiu: Crit & ATK
        optimizationTargets.put(CharacterId.XINGQIU,
                java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT));

        // Xiangling: Crit, ATK, EM
        optimizationTargets.put(CharacterId.XIANGLING, java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG,
                StatType.ATK_PERCENT, StatType.ELEMENTAL_MASTERY));

        TotalOptimizationResult optimization = OptimizerPipeline.run(
                RaidenParty::createSimulator,
                RaidenParty::executeRotation,
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

    /**
     * Creates and configures a fresh {@link CombatSimulator} for one optimization
     * or final-execution pass.
     *
     * @param erTargets         per-character ER targets (may be {@code null})
     * @param partyManualRolls  per-character manual artifact roll overrides
     *                          (may be {@code null})
     * @return a configured simulator with party and enemy set up
     */
    private static CombatSimulator createSimulator(
            java.util.Map<CharacterId, Double> erTargets,
            java.util.Map<CharacterId, java.util.Map<model.type.StatType, Integer>> partyManualRolls) {
        CombatSimulator s = new CombatSimulator();
        s.setEnemy(new Enemy(90));
        setupParty(s, erTargets != null ? erTargets : new java.util.HashMap<>(),
                partyManualRolls != null ? partyManualRolls : new java.util.HashMap<>());
        return s;
    }

    /**
     * Runs the scripted Raiden National rotation against the given simulator.
     *
     * @param sim simulator to act upon
     */
    private static void executeRotation(CombatSimulator sim) {
        // Rotation: (Raiden E) > Xingqiu E Q N0 > Bennett Q N0 E > Xiangling Q E N0
        // > Raiden Q N3Cx3 N1C N0 E > Bennett E > Xiangling funnel
        // (Using previous fixed rotation logic)

        // 1. Raiden E
        sim.switchCharacter(CharacterId.RAIDEN_SHOGUN);
        sim.getEnergyDistributor().scheduleKQMSEnemyParticles(); // Add Enemy Particles (Delegated)
        skill(sim, CharacterId.RAIDEN_SHOGUN);

        // 2. Xingqiu: Q E E
        sim.switchCharacter(CharacterId.XINGQIU);
        burst(sim, CharacterId.XINGQIU); // Q
        skill(sim, CharacterId.XINGQIU); // E
        normal(sim, CharacterId.XINGQIU); // N0 (Drive Raincutter)

        // 3. Bennett: Q N0 E
        sim.switchCharacter(CharacterId.BENNETT);
        burst(sim, CharacterId.BENNETT); // Q
        normal(sim, CharacterId.BENNETT); // N0
        skill(sim, CharacterId.BENNETT); // E

        // 4. Xiangling: Q N0 E N0 (Optimized to Q E N0)
        sim.switchCharacter(CharacterId.XIANGLING);
        burst(sim, CharacterId.XIANGLING); // Q
        normal(sim, CharacterId.XIANGLING); // N0
        skill(sim, CharacterId.XIANGLING); // E
        normal(sim, CharacterId.XIANGLING); // N0

        // 5. Raiden: Q N3Cx3 N1C N0 E
        sim.switchCharacter(CharacterId.RAIDEN_SHOGUN);
        burst(sim, CharacterId.RAIDEN_SHOGUN); // Q

        // N3Cx3
        for (int i = 0; i < 3; i++) {
            normal(sim, CharacterId.RAIDEN_SHOGUN);
            normal(sim, CharacterId.RAIDEN_SHOGUN);
            normal(sim, CharacterId.RAIDEN_SHOGUN);
            charge(sim, CharacterId.RAIDEN_SHOGUN);
        }

        // N1C
        normal(sim, CharacterId.RAIDEN_SHOGUN);
        charge(sim, CharacterId.RAIDEN_SHOGUN);

        // N0 E (End)
        sim.advanceTime(0.1);
        normal(sim, CharacterId.RAIDEN_SHOGUN);
        skill(sim, CharacterId.RAIDEN_SHOGUN); // E Refresh

        // 6. Bennett E
        sim.switchCharacter(CharacterId.BENNETT);
        skill(sim, CharacterId.BENNETT); // E
        normal(sim, CharacterId.BENNETT); // N

        // 7. Xiangling Funnel
        sim.switchCharacter(CharacterId.XIANGLING);
        normal(sim, CharacterId.XIANGLING); // N

        // 8. Bennet E
        sim.switchCharacter(CharacterId.BENNETT);
        skill(sim, CharacterId.BENNETT); // E
        normal(sim, CharacterId.BENNETT); // N

        // 9. Xiangling Funnel
        sim.switchCharacter(CharacterId.XIANGLING);
        normal(sim, CharacterId.XIANGLING); // N

        // Pad to full 21s rotation
        double remaining = 21.0 - sim.getCurrentTime();
        if (remaining > 0) {
            sim.advanceTime(remaining);
        }
    }

    /**
     * Issues a normal attack action for the given character.
     *
     * @param sim           simulator
     * @param characterId character id
     */
    private static void normal(CombatSimulator sim, CharacterId characterId) {
        sim.performAction(characterId, CharacterActionRequest.of(CharacterActionKey.NORMAL));
    }

    /**
     * Issues a charged attack action for the given character.
     *
     * @param sim           simulator
     * @param characterId character id
     */
    private static void charge(CombatSimulator sim, CharacterId characterId) {
        sim.performAction(characterId, CharacterActionRequest.of(CharacterActionKey.CHARGE));
    }

    /**
     * Issues an elemental skill action for the given character.
     *
     * @param sim           simulator
     * @param characterId character id
     */
    private static void skill(CombatSimulator sim, CharacterId characterId) {
        sim.performAction(characterId, CharacterActionRequest.of(CharacterActionKey.SKILL));
    }

    /**
     * Issues an elemental burst action for the given character.
     *
     * @param sim           simulator
     * @param characterId character id
     */
    private static void burst(CombatSimulator sim, CharacterId characterId) {
        sim.performAction(characterId, CharacterActionRequest.of(CharacterActionKey.BURST));
    }

    /**
     * Builds and registers all four party members, applying ER and roll
     * overrides and elemental resonances.
     *
     * @param sim              simulator to populate
     * @param erTargets        per-character minimum ER targets
     * @param partyManualRolls per-character manual artifact substat roll overrides
     */
    private static void setupParty(CombatSimulator sim, java.util.Map<CharacterId, Double> erTargets,
            java.util.Map<CharacterId, java.util.Map<model.type.StatType, Integer>> partyManualRolls) {
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
        Double calcER = erTargets.getOrDefault(CharacterId.RAIDEN_SHOGUN, 1.0);
        System.out.println(
                "   [Setup] Raiden Shogun Calculated ER: " + String.format("%.1f", calcER * 100) + "%");
        // Force minimum 250% for Emblem/DPS
        raidenConfig.minER = Math.max(calcER, 2.50);

        // Manual Rolls Injection
        if (partyManualRolls.containsKey(CharacterId.RAIDEN_SHOGUN)) {
            raidenConfig.manualRolls = partyManualRolls.get(CharacterId.RAIDEN_SHOGUN);
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

        xqConfig.minER = erTargets.getOrDefault(CharacterId.XINGQIU, 1.0);

        if (partyManualRolls.containsKey(CharacterId.XINGQIU)) {
            xqConfig.manualRolls = partyManualRolls.get(CharacterId.XINGQIU);
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
        xlConfig.minER = erTargets.getOrDefault(CharacterId.XIANGLING, 1.0);

        if (partyManualRolls.containsKey(CharacterId.XIANGLING)) {
            xlConfig.manualRolls = partyManualRolls.get(CharacterId.XIANGLING);
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

        bennyConfig.minER = erTargets.getOrDefault(CharacterId.BENNETT, 1.0);

        if (partyManualRolls.containsKey(CharacterId.BENNETT)) {
            bennyConfig.manualRolls = partyManualRolls.get(CharacterId.BENNETT);
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
