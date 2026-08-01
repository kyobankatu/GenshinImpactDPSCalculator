package sample;

import java.util.List;

import mechanics.analysis.StatsRecorder;
import mechanics.optimization.OptimizerPipeline;
import mechanics.optimization.TotalOptimizationResult;
import simulation.CombatSimulator;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;
import visualization.HtmlReportGenerator;
import visualization.SimulationRecord;
import visualization.VisualLogger;

/**
 * Generic sample runner for catalog-registered party definitions.
 */
public final class RunPartySimulation {
    private RunPartySimulation() {
    }

    public static void main(String[] args) {
        String partyName = args.length > 0 ? args[0] : PartyCatalog.require("FlinsParty2").name();
        run(partyName);
    }

    public static void run(String partyName) {
        PartyDefinition definition = PartyCatalog.require(partyName);
        System.out.println(definition.displayName());

        TotalOptimizationResult optimization = OptimizerPipeline.run(
                definition::createSimulator,
                definition::executeRotation,
                definition.optimizationTargets());

        System.out.println("\n--- Starting Final Simulation ---");
        VisualLogger.getInstance().clear();

        CombatSimulator sim = definition.createSimulator(optimization.erTargets, optimization.partyRolls);
        StatsRecorder recorder = new StatsRecorder(sim, 0.1);
        recorder.startRecording();

        definition.executeRotation(sim);

        sim.printReport();
        List<SimulationRecord> records = VisualLogger.getInstance().getRecords();
        List<mechanics.analysis.StatsSnapshot> snapshots = recorder.getSnapshots();
        HtmlReportGenerator.generate("simulation_report.html", records, sim, snapshots);
        if (definition.publishDocsReport()) {
            HtmlReportGenerator.generateForDocs("simulation_report.html", records, sim, snapshots);
        }
    }
}
