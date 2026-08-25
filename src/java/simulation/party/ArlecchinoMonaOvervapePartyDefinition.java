package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Arlecchino, Mona, and Fischl Overvape scenario. */
public final class ArlecchinoMonaOvervapePartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public ArlecchinoMonaOvervapePartyDefinition() {
        super(
                "ArlecchinoMonaOvervape",
                DatasetSplit.TRAIN,
                30.0,
                List.of(
                        CuratedCharacters::arlecchino,
                        CuratedCharacters::bennett,
                        CuratedCharacters::fischl,
                        CuratedCharacters::mona),
                actions(
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.BURST,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL),
                requires(
                        CharacterId.ARLECCHINO,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.CHARGE));
    }
}
