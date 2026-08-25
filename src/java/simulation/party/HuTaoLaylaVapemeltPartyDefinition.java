package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Hu Tao, Xingqiu, Yelan, and Layla Vapemelt scenario. */
public final class HuTaoLaylaVapemeltPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public HuTaoLaylaVapemeltPartyDefinition() {
        super(
                "HuTaoLaylaVapemelt",
                DatasetSplit.TRAIN,
                30.0,
                List.of(
                        CuratedCharacters::huTao,
                        CuratedCharacters::xingqiuFavonius,
                        CuratedCharacters::yelan,
                        CuratedCharacters::layla),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL),
                requires(
                        CharacterId.HU_TAO,
                        PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.SKILL_PRESS));
    }
}
