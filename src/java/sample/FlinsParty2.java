package sample;

import simulation.CombatSimulator;
import model.entity.Enemy;

import model.type.StatType;
import model.type.CharacterId;
import model.character.*;
import model.weapon.*;
import model.artifact.*;

import mechanics.optimization.OptimizerPipeline;
import mechanics.optimization.TotalOptimizationResult;
import mechanics.optimization.ArtifactOptimizer;
import mechanics.element.ResonanceManager;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Variant of {@link FlinsParty} using a different weapon set and a slightly
 * different scripted rotation (two cycles, three burst windows each).
 */
public class FlinsParty2 {
    /**
     * Application entry point.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        // Run main logic in try-catch to ensure reports are generated even if runtime
        // errors occur
        try {
            System.out.println("Genshin DPS Calculator: Flins Party Simulation (Refactored)");

            // 1. Run Optimization Phase (ER Calibration + Joint Crit Optimization)
            java.util.Map<CharacterId, java.util.List<StatType>> optimizationTargets = new java.util.HashMap<>();

            // Flins: Crit, ATK (ER is pre-reserved separately via computeMinERRolls)
            optimizationTargets.put(CharacterId.FLINS,
                    java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG,
                            StatType.ATK_PERCENT));

            // Ineffa: Crit, ATK
            optimizationTargets.put(CharacterId.INEFFA,
                    java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG,
                            StatType.ATK_PERCENT));

            // Columbina: Crit, HP
            optimizationTargets.put(CharacterId.COLUMBINA,
                    java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG,
                            StatType.HP_PERCENT));

            // Sucrose: EM (Swirl)
            optimizationTargets.put(CharacterId.SUCROSE,
                    java.util.Arrays.asList(StatType.ELEMENTAL_MASTERY));

            TotalOptimizationResult optimization = OptimizerPipeline.run(
                    FlinsParty2::createSimulator,
                    FlinsParty2::executeRotation,
                    optimizationTargets);

            // 2. Final Execution with Optimized Stats
            System.out.println("\n--- Starting Final Simulation ---");
            visualization.VisualLogger.getInstance().clear();

            // Create Sim with Final Config
            CombatSimulator sim = createSimulator(optimization.erTargets, optimization.partyRolls);

            // Setup Stats Recorder
            mechanics.analysis.StatsRecorder recorder = new mechanics.analysis.StatsRecorder(sim, 0.1);
            recorder.startRecording();

            executeRotation(sim);

            // 3. Print & Generate Reports
            sim.printReport();
            visualization.HtmlReportGenerator.generate("simulation_report.html",
                    visualization.VisualLogger.getInstance().getRecords(), sim,
                    recorder.getSnapshots());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // --- Helper Methods ---

    private static CombatSimulator createSimulator(
            java.util.Map<CharacterId, Double> erTargets,
            java.util.Map<CharacterId, java.util.Map<model.type.StatType, Integer>> partyManualRolls) {
        CombatSimulator s = new CombatSimulator();
        s.setEnemy(new Enemy(100)); // Enemy Lv 100
        // s.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM); // Removed manual
        // set
        setupParty(s, erTargets != null ? erTargets : new java.util.HashMap<>(),
                partyManualRolls != null ? partyManualRolls : new java.util.HashMap<>());
        s.updateMoonsign(); // Auto-detect
        return s;
    }

    private static void executeRotation(CombatSimulator sim) {
        // Rotation:
        // Ineffa Skill -> Burst
        // Columbina Skill -> Burst
        // Sucrose Skill -> Burst
        // Flins Skill -> Skill -> SpecialBurst -> Skill -> SpecialBurst

        for (int j = 0; j < 2; j++) {

            // 1. Ineffa
            sim.switchCharacter(CharacterId.INEFFA);
            skill(sim, CharacterId.INEFFA);
            burst(sim, CharacterId.INEFFA);

            // 2. Columbina
            sim.switchCharacter(CharacterId.COLUMBINA);
            skill(sim, CharacterId.COLUMBINA);
            burst(sim, CharacterId.COLUMBINA);

            // 3. Sucrose
            sim.switchCharacter(CharacterId.SUCROSE);
            skill(sim, CharacterId.SUCROSE);
            burst(sim, CharacterId.SUCROSE);

            // 4. Flins
            sim.switchCharacter(CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS); // SpecialBurst 1
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS); // SpecialBurst 2
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS); // SpecialBurst 3
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);

            // 5. Sucrose
            sim.switchCharacter(CharacterId.SUCROSE);
            skill(sim, CharacterId.SUCROSE);
            normal(sim, CharacterId.SUCROSE);
            normal(sim, CharacterId.SUCROSE);
            normal(sim, CharacterId.SUCROSE);

            // 1. Ineffa
            sim.switchCharacter(CharacterId.INEFFA);
            skill(sim, CharacterId.INEFFA);

            // 2. Columbina
            sim.switchCharacter(CharacterId.COLUMBINA);
            skill(sim, CharacterId.COLUMBINA);
            burst(sim, CharacterId.COLUMBINA);

            // 3. Sucrose
            sim.switchCharacter(CharacterId.SUCROSE);
            skill(sim, CharacterId.SUCROSE);

            // 4. Flins
            sim.switchCharacter(CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS); // SpecialBurst 1
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS); // SpecialBurst 2
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS); // SpecialBurst 3
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);

            sim.switchCharacter(CharacterId.SUCROSE);
            normal(sim, CharacterId.SUCROSE);
            normal(sim, CharacterId.SUCROSE);

        }
    }

    private static void normal(CombatSimulator sim, CharacterId characterId) {
        sim.performAction(characterId, CharacterActionRequest.of(CharacterActionKey.NORMAL));
    }

    private static void skill(CombatSimulator sim, CharacterId characterId) {
        sim.performAction(characterId, CharacterActionRequest.of(CharacterActionKey.SKILL));
    }

    private static void burst(CombatSimulator sim, CharacterId characterId) {
        sim.performAction(characterId, CharacterActionRequest.of(CharacterActionKey.BURST));
    }

    private static void setupParty(CombatSimulator sim, java.util.Map<CharacterId, Double> erTargets,
            java.util.Map<CharacterId, java.util.Map<model.type.StatType, Integer>> partyManualRolls) {

        // --- 1. Flins (Night of the Sky's Unveiling) ---
        Flins flins = new Flins(new PrimordialJadeWingedSpear(), null);

        ArtifactOptimizer.OptimizationConfig flinsConfig = new ArtifactOptimizer.OptimizationConfig();
        flinsConfig.mainStatSands = StatType.ATK_PERCENT;
        flinsConfig.mainStatGoblet = StatType.ATK_PERCENT;
        flinsConfig.mainStatCirclet = StatType.CRIT_DMG;
        flinsConfig.subStatPriority = java.util.Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE);

        flinsConfig.minER = erTargets.getOrDefault(CharacterId.FLINS, 1.0);

        if (partyManualRolls.containsKey(CharacterId.FLINS)) {
            flinsConfig.manualRolls = partyManualRolls.get(CharacterId.FLINS);
        }

        ArtifactOptimizer.OptimizationResult resultFlins = ArtifactOptimizer.generate(
                flinsConfig, flins.getBaseStats(), flins.getWeapon().getStats(),
                new model.stats.StatsContainer());

        flins.setArtifacts(new NightOfTheSkysUnveiling(resultFlins.stats));
        flins.setArtifactRolls(resultFlins.rolls);
        sim.addCharacter(flins);

        // --- 2. Ineffa (Silken Moons) ---
        Ineffa ineffa = new Ineffa(new CalamityQueller(), null);
        ArtifactOptimizer.OptimizationConfig ineffaConfig = new ArtifactOptimizer.OptimizationConfig();
        ineffaConfig.mainStatSands = StatType.ATK_PERCENT;
        ineffaConfig.mainStatGoblet = StatType.ATK_PERCENT;
        ineffaConfig.mainStatCirclet = StatType.CRIT_RATE;
        ineffaConfig.subStatPriority = java.util.Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE);

        ineffaConfig.minER = erTargets.getOrDefault(CharacterId.INEFFA, 1.0);

        if (partyManualRolls.containsKey(CharacterId.INEFFA)) {
            ineffaConfig.manualRolls = partyManualRolls.get(CharacterId.INEFFA);
        }

        ArtifactOptimizer.OptimizationResult resultIneffa = ArtifactOptimizer.generate(
                ineffaConfig, ineffa.getBaseStats(), ineffa.getWeapon().getStats(),
                new model.stats.StatsContainer());

        ineffa.setArtifacts(new SilkenMoonsSerenade(resultIneffa.stats));
        ineffa.setArtifactRolls(resultIneffa.rolls);
        sim.addCharacter(ineffa);

        // --- 3. Columbina (Aubade) ---
        Columbina columbina = new Columbina(new NocturnesCurtainCall(), null);
        ArtifactOptimizer.OptimizationConfig colConfig = new ArtifactOptimizer.OptimizationConfig();
        colConfig.mainStatSands = StatType.ENERGY_RECHARGE;
        colConfig.mainStatGoblet = StatType.HP_PERCENT;
        colConfig.mainStatCirclet = StatType.CRIT_RATE;
        colConfig.subStatPriority = java.util.Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.HP_PERCENT, StatType.ENERGY_RECHARGE);

        colConfig.minER = erTargets.getOrDefault(CharacterId.COLUMBINA, 1.0);

        if (partyManualRolls.containsKey(CharacterId.COLUMBINA)) {
            colConfig.manualRolls = partyManualRolls.get(CharacterId.COLUMBINA);
        }

        ArtifactOptimizer.OptimizationResult resultCol = ArtifactOptimizer.generate(
                colConfig, columbina.getBaseStats(), columbina.getWeapon().getStats(),
                new model.stats.StatsContainer());

        columbina.setArtifacts(new AubadeOfMorningstarAndMoon(resultCol.stats));
        columbina.setArtifactRolls(resultCol.rolls);
        sim.addCharacter(columbina);

        // --- 4. Sucrose (VV) ---
        Sucrose sucrose = new Sucrose(new SunnyMorningSleepIn(), null);
        ArtifactOptimizer.OptimizationConfig sucConfig = new ArtifactOptimizer.OptimizationConfig();
        sucConfig.mainStatSands = StatType.ELEMENTAL_MASTERY;
        sucConfig.mainStatGoblet = StatType.ELEMENTAL_MASTERY;
        sucConfig.mainStatCirclet = StatType.ELEMENTAL_MASTERY;
        sucConfig.subStatPriority = java.util.Arrays.asList(StatType.ELEMENTAL_MASTERY,
                StatType.ENERGY_RECHARGE);

        sucConfig.minER = erTargets.getOrDefault(CharacterId.SUCROSE, 1.0);

        if (partyManualRolls.containsKey(CharacterId.SUCROSE)) {
            sucConfig.manualRolls = partyManualRolls.get(CharacterId.SUCROSE);
        }

        ArtifactOptimizer.OptimizationResult resultSuc = ArtifactOptimizer.generate(
                sucConfig, sucrose.getBaseStats(), sucrose.getWeapon().getStats(),
                new model.stats.StatsContainer());

        sucrose.setArtifacts(new ViridescentVenerer(resultSuc.stats));
        sucrose.setArtifactRolls(resultSuc.rolls);
        sim.addCharacter(sucrose);

        // --- Resonance ---
        mechanics.element.ResonanceManager.applyResonances(sim);

    }

}
