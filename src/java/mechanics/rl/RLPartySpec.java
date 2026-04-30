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

    public RLPartySpec(String partyName, CharacterId[] partyOrder, Supplier<CombatSimulator> simulatorSupplier) {
        this.partyName = partyName;
        this.partyOrder = partyOrder.clone();
        this.simulatorSupplier = simulatorSupplier;
    }

    public String getPartyName() {
        return partyName;
    }

    public CharacterId[] getPartyOrder() {
        return partyOrder.clone();
    }

    public Supplier<CombatSimulator> getSimulatorSupplier() {
        return simulatorSupplier;
    }
}
