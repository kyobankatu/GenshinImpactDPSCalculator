package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Diluc, Xingqiu, Xianyun, and Bennett Plunge scenario. */
public final class DilucXianyunVaporizePartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public DilucXianyunVaporizePartyDefinition() {
        super(
                "DilucXianyunVaporize",
                DatasetSplit.TRAIN,
                40.0,
                List.of(
                        CuratedCharacters::diluc,
                        CuratedCharacters::xingqiu,
                        CuratedCharacters::xianyun,
                        CuratedCharacters::bennett),
                actions(
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.NORMAL,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.NORMAL, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0),
                requires(
                        CharacterId.DILUC,
                        PolicyAction.NORMAL,
                        PolicyAction.PLUNGE,
                        PolicyAction.SKILL_PRESS));
    }
}
