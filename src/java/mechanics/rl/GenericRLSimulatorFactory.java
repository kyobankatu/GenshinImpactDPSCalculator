package mechanics.rl;

import java.util.function.Supplier;

import mechanics.optimization.PartyBuildResolver;
import mechanics.optimization.TotalOptimizationResult;
import simulation.CombatSimulator;
import simulation.party.PartyDefinition;

/**
 * Creates RL simulators from shared party definitions.
 */
public final class GenericRLSimulatorFactory {
    private GenericRLSimulatorFactory() {
    }

    public static RLPartySpec spec(PartyDefinition definition) {
        return new RLPartySpec(definition.name(), definition.partyOrder(), supplier(definition));
    }

    /** Creates an RL spec backed by an already frozen build. */
    public static RLPartySpec spec(PartyDefinition definition, TotalOptimizationResult build) {
        return new RLPartySpec(definition.name(), definition.partyOrder(), supplier(definition, build));
    }

    public static Supplier<CombatSimulator> supplier(PartyDefinition definition) {
        return () -> create(definition);
    }

    /** Returns a supplier that reuses exact immutable build inputs. */
    public static Supplier<CombatSimulator> supplier(
            PartyDefinition definition,
            TotalOptimizationResult build) {
        if (definition == null || build == null) {
            throw new IllegalArgumentException("Party definition and optimized build are required");
        }
        return () -> create(definition, build);
    }

    public static CombatSimulator create(PartyDefinition definition) {
        return create(definition, PartyBuildResolver.require(definition));
    }

    /** Creates a fresh simulator from one frozen optimized build. */
    public static CombatSimulator create(
            PartyDefinition definition,
            TotalOptimizationResult build) {
        if (definition == null || build == null) {
            throw new IllegalArgumentException("Party definition and optimized build are required");
        }
        CombatSimulator sim = definition.createSimulator(build.erTargets, build.partyRolls);
        sim.setLoggingEnabled(false);
        return sim;
    }
}
