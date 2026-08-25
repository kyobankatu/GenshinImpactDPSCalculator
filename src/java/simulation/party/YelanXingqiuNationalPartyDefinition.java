package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Yelan, Xingqiu, Bennett, and Xiangling Vaporize scenario. */
public final class YelanXingqiuNationalPartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public YelanXingqiuNationalPartyDefinition() {
        super(
                "YelanXingqiuNational",
                DatasetSplit.TRAIN,
                36.0,
                List.of(
                        CuratedCharacters::yelan,
                        CuratedCharacters::xingqiu,
                        CuratedCharacters::bennett,
                        CuratedCharacters::xiangling),
                actions(
                        PolicyAction.BURST,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.NORMAL,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.NORMAL, PolicyAction.BURST,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0),
                requires(
                        CharacterId.YELAN,
                        PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }
}
