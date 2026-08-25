package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Furina, Xingqiu, Xianyun, and Bennett Plunge scenario. */
public final class BennettXianyunVaporizePartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public BennettXianyunVaporizePartyDefinition() {
        super(
                "BennettXianyunVaporize",
                DatasetSplit.TRAIN,
                31.0,
                List.of(
                        CuratedCharacters::furina,
                        CuratedCharacters::xingqiu,
                        CuratedCharacters::xianyun,
                        CuratedCharacters::bennett),
                actions(
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.BURST,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.NORMAL,
                        PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.NORMAL,
                        PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.NORMAL,
                        PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.NORMAL,
                        PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0),
                requires(
                        CharacterId.BENNETT,
                        PolicyAction.NORMAL,
                        PolicyAction.PLUNGE,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }
}
