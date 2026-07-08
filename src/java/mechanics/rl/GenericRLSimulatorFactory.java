package mechanics.rl;

import java.util.function.Supplier;

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

    public static Supplier<CombatSimulator> supplier(PartyDefinition definition) {
        return () -> create(definition);
    }

    public static CombatSimulator create(PartyDefinition definition) {
        CombatSimulator sim = definition.createSimulator(null, null);
        sim.setLoggingEnabled(false);
        return sim;
    }
}
