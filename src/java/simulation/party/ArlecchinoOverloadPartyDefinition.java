package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Pyro infusion and Overloaded timing scenario. */
public final class ArlecchinoOverloadPartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic C0/base-loadout scenario. */
    public ArlecchinoOverloadPartyDefinition() {
        super(
                "ArlecchinoOverload",
                DatasetSplit.TRAIN,
                22.0,
                List.of(
                        CuratedCharacters::arlecchino,
                        CuratedCharacters::fischl,
                        CuratedCharacters::chevreuse,
                        CuratedCharacters::bennett),
                actions(
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.SWAP_SLOT_2,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.BURST,
                        PolicyAction.SKILL_PRESS, PolicyAction.SWAP_SLOT_0,
                        PolicyAction.SKILL_PRESS, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.BURST),
                requires(CharacterId.ARLECCHINO, PolicyAction.SKILL_PRESS,
                        PolicyAction.CHARGE));
    }
}
