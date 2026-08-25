package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Hu Tao, Furina, Yelan, and Xianyun Plunge scenario. */
public final class HuTaoXianyunVaporizePartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public HuTaoXianyunVaporizePartyDefinition() {
        super(
                "HuTaoXianyunVaporize",
                DatasetSplit.TRAIN,
                30.0,
                List.of(
                        CuratedCharacters::huTao,
                        CuratedCharacters::furina,
                        CuratedCharacters::yelan,
                        CuratedCharacters::xianyun),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE, PolicyAction.PLUNGE),
                requires(
                        CharacterId.HU_TAO,
                        PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.PLUNGE,
                        PolicyAction.SKILL_PRESS));
    }
}
