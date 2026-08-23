package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Dendro and Hyperbloom driver scenario. */
public final class AlhaithamHyperbloomPartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic C0/base-loadout scenario. */
    public AlhaithamHyperbloomPartyDefinition() {
        super(
                "AlhaithamHyperbloom",
                DatasetSplit.TRAIN,
                24.0,
                List.of(
                        CuratedCharacters::alhaitham,
                        CuratedCharacters::nahida,
                        CuratedCharacters::xingqiu,
                        CuratedCharacters::kukiShinobu),
                actions(
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.BURST,
                        PolicyAction.SKILL_PRESS, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.BURST,
                        PolicyAction.SKILL_PRESS, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL),
                requires(CharacterId.ALHAITHAM, PolicyAction.CHARGE,
                        PolicyAction.SKILL_PRESS));
    }
}
