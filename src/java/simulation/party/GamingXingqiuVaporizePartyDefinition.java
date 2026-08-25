package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Gaming, Xingqiu, Sucrose, and Bennett Vaporize scenario. */
public final class GamingXingqiuVaporizePartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public GamingXingqiuVaporizePartyDefinition() {
        super(
                "GamingXingqiuVaporize",
                DatasetSplit.TRAIN,
                35.0,
                List.of(
                        CuratedCharacters::gaming,
                        CuratedCharacters::xingqiuFavonius,
                        CuratedCharacters::sucrose,
                        CuratedCharacters::bennett),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.BURST,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.NORMAL, PolicyAction.NORMAL),
                requires(
                        CharacterId.GAMING,
                        PolicyAction.NORMAL,
                        PolicyAction.PLUNGE,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }
}
