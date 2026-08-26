package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Holdout-split Alhaitham split-field Spread scenario. */
public final class AlhaithamSpreadPartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public AlhaithamSpreadPartyDefinition() {
        super(
                "AlhaithamSpread",
                DatasetSplit.HOLDOUT,
                26.0,
                List.of(
                        CuratedCharacters::alhaitham,
                        CuratedCharacters::fischl,
                        CuratedCharacters::nahida,
                        CuratedCharacters::bennett),
                actions(
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.CHARGE, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.BURST,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.CHARGE),
                requires(CharacterId.ALHAITHAM,
                        PolicyAction.CHARGE, PolicyAction.SKILL_PRESS));
    }
}
