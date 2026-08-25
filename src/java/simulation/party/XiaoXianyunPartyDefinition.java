package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Xiao, Xianyun, Bennett, and C0 Faruzan Plunge scenario. */
public final class XiaoXianyunPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public XiaoXianyunPartyDefinition() {
        super(
                "XiaoXianyun",
                DatasetSplit.TRAIN,
                31.0,
                List.of(
                        CuratedCharacters::xiao,
                        CuratedCharacters::xianyun,
                        CuratedCharacters::bennett,
                        CuratedCharacters::faruzanC0),
                actions(
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.CHARGE, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0),
                requires(
                        CharacterId.XIAO,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE,
                        PolicyAction.BURST));
    }
}
