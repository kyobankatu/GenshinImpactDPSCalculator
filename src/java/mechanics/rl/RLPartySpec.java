package mechanics.rl;

import java.util.function.Supplier;

import model.type.CharacterId;
import simulation.CombatSimulator;

/**
 * Declarative description of one RL-usable party variant.
 */
public final class RLPartySpec {
    private final String partyName;
    private final CharacterId[] partyOrder;
    private final Supplier<CombatSimulator> simulatorSupplier;

    /**
     * Creates one declarative RL party specification.
     */
    public RLPartySpec(String partyName, CharacterId[] partyOrder, Supplier<CombatSimulator> simulatorSupplier) {
        this.partyName = partyName;
        this.partyOrder = partyOrder.clone();
        this.simulatorSupplier = simulatorSupplier;
    }

    /**
     * Returns the registry-facing party name.
     */
    public String getPartyName() {
        return partyName;
    }

    /**
     * Returns the configured character order for episode metadata.
     */
    public CharacterId[] getPartyOrder() {
        return partyOrder.clone();
    }

    /**
     * Returns the simulator supplier for this party variant.
     */
    public Supplier<CombatSimulator> getSimulatorSupplier() {
        return simulatorSupplier;
    }
}
