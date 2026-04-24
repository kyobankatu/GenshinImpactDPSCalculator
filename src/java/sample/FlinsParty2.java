package sample;

import simulation.CombatSimulator;
import model.entity.Enemy;

import model.type.StatType;
import model.character.*;
import model.weapon.*;
import model.artifact.*;

import mechanics.optimization.OptimizerPipeline;
import mechanics.optimization.TotalOptimizationResult;
import mechanics.optimization.ArtifactOptimizer;
import mechanics.element.ResonanceManager;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

public class FlinsParty2 {
    public static void main(String[] args) {
        // Run main logic in try-catch to ensure reports are generated even if runtime
        // errors occur
        try {
            System.out.println("Genshin DPS Calculator: Flins Party Simulation (Refactored)");

            // 1. Run Optimization Phase (ER Calibration + Joint Crit Optimization)
            java.util.Map<String, java.util.List<StatType>> optimizationTargets = new java.util.HashMap<>();

            // Flins: Crit, ATK (ER is pre-reserved separately via computeMinERRolls)
            optimizationTargets.put("Flins",
                    java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG,
                            StatType.ATK_PERCENT));

            // Ineffa: Crit, ATK
            optimizationTargets.put("Ineffa",
                    java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG,
                            StatType.ATK_PERCENT));

            // Columbina: Crit, HP
            optimizationTargets.put("Columbina",
                    java.util.Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG,
                            StatType.HP_PERCENT));

            // Sucrose: EM (Swirl)
            optimizationTargets.put("Sucrose",
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
            mechanics.analysis.StatsRecorder recorder = new mechanics.analysis.StatsRecorder(sim, 0.5);
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
            java.util.Map<String, Double> erTargets,
            java.util.Map<String, java.util.Map<model.type.StatType, Integer>> partyManualRolls) {
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

        for (int j = 0; j < 3; j++) {

            // 1. Ineffa
            sim.switchCharacter("Ineffa");
            skill(sim, "Ineffa");
            burst(sim, "Ineffa");

            // 2. Columbina
            sim.switchCharacter("Columbina");
            skill(sim, "Columbina");
            burst(sim, "Columbina");

            // 3. Sucrose
            sim.switchCharacter("Sucrose");
            skill(sim, "Sucrose");
            burst(sim, "Sucrose");

            // 4. Flins
            sim.switchCharacter("Flins");
            skill(sim, "Flins");
            skill(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            burst(sim, "Flins"); // SpecialBurst 1
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            skill(sim, "Flins");
            burst(sim, "Flins"); // SpecialBurst 2
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            skill(sim, "Flins");
            burst(sim, "Flins"); // SpecialBurst 3
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");

            // 5. Sucrose
            sim.switchCharacter("Sucrose");
            skill(sim, "Sucrose");
            normal(sim, "Sucrose");
            normal(sim, "Sucrose");
            normal(sim, "Sucrose");

            // 1. Ineffa
            sim.switchCharacter("Ineffa");
            skill(sim, "Ineffa");

            // 2. Columbina
            sim.switchCharacter("Columbina");
            skill(sim, "Columbina");
            burst(sim, "Columbina");

            // 3. Sucrose
            sim.switchCharacter("Sucrose");
            skill(sim, "Sucrose");
            burst(sim, "Sucrose");

            // 4. Flins
            sim.switchCharacter("Flins");
            skill(sim, "Flins");
            skill(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            burst(sim, "Flins"); // SpecialBurst 1
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            skill(sim, "Flins");
            burst(sim, "Flins"); // SpecialBurst 2
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");
            skill(sim, "Flins");
            burst(sim, "Flins"); // SpecialBurst 3
            normal(sim, "Flins");
            normal(sim, "Flins");
            normal(sim, "Flins");

            sim.switchCharacter("Sucrose");
            normal(sim, "Sucrose");
            normal(sim, "Sucrose");

        }
    }

    private static void normal(CombatSimulator sim, String characterName) {
        sim.performAction(characterName, CharacterActionRequest.of(CharacterActionKey.NORMAL));
    }

    private static void skill(CombatSimulator sim, String characterName) {
        sim.performAction(characterName, CharacterActionRequest.of(CharacterActionKey.SKILL));
    }

    private static void burst(CombatSimulator sim, String characterName) {
        sim.performAction(characterName, CharacterActionRequest.of(CharacterActionKey.BURST));
    }

    private static void setupParty(CombatSimulator sim, java.util.Map<String, Double> erTargets,
            java.util.Map<String, java.util.Map<model.type.StatType, Integer>> partyManualRolls) {

        // --- 1. Flins (Night of the Sky's Unveiling) ---
        Flins flins = new Flins(new PrimordialJadeWingedSpear(), null);

        ArtifactOptimizer.OptimizationConfig flinsConfig = new ArtifactOptimizer.OptimizationConfig();
        flinsConfig.mainStatSands = StatType.ATK_PERCENT;
        flinsConfig.mainStatGoblet = StatType.ATK_PERCENT;
        flinsConfig.mainStatCirclet = StatType.CRIT_DMG;
        flinsConfig.subStatPriority = java.util.Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE);

        flinsConfig.minER = erTargets.getOrDefault("Flins", 1.0);

        if (partyManualRolls.containsKey("Flins")) {
            flinsConfig.manualRolls = partyManualRolls.get("Flins");
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

        ineffaConfig.minER = erTargets.getOrDefault("Ineffa", 1.0);

        if (partyManualRolls.containsKey("Ineffa")) {
            ineffaConfig.manualRolls = partyManualRolls.get("Ineffa");
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

        colConfig.minER = erTargets.getOrDefault("Columbina", 1.0);

        if (partyManualRolls.containsKey("Columbina")) {
            colConfig.manualRolls = partyManualRolls.get("Columbina");
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

        sucConfig.minER = erTargets.getOrDefault("Sucrose", 1.0);

        if (partyManualRolls.containsKey("Sucrose")) {
            sucConfig.manualRolls = partyManualRolls.get("Sucrose");
        }

        ArtifactOptimizer.OptimizationResult resultSuc = ArtifactOptimizer.generate(
                sucConfig, sucrose.getBaseStats(), sucrose.getWeapon().getStats(),
                new model.stats.StatsContainer());

        sucrose.setArtifacts(new ViridescentVenerer(resultSuc.stats));
        sucrose.setArtifactRolls(resultSuc.rolls);
        sim.addCharacter(sucrose);

        // --- Resonance ---
        mechanics.element.ResonanceManager.applyResonances(sim);

        System.out.println("\n[DEBUG] Stats check after setup:");
        try (java.io.PrintWriter out = new java.io.PrintWriter("stats_dump.txt")) {
            for (model.entity.Character c : sim.getPartyMembers()) {
                model.stats.StatsContainer s = c.getEffectiveStats(0.0);
                out.println(c.getName() + " EM: " + s.get(StatType.ELEMENTAL_MASTERY));
                out.println("     Artifact Rolls: " + c.getArtifactRolls());
                System.out.println("  " + c.getName() + " EM: " + s.get(StatType.ELEMENTAL_MASTERY));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
