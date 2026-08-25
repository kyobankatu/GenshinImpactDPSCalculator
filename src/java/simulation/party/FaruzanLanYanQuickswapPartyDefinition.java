package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Faruzan, Furina, Bennett, and C6 Lan Yan quickswap scenario. */
public final class FaruzanLanYanQuickswapPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public FaruzanLanYanQuickswapPartyDefinition() {
        super(
                "FaruzanLanYanQuickswap",
                DatasetSplit.TRAIN,
                30.0,
                List.of(
                        CuratedCharacters::faruzan,
                        CuratedCharacters::furina,
                        CuratedCharacters::bennett,
                        CuratedCharacters::lanYanSacrificial),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.CHARGE, PolicyAction.CHARGE,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS, PolicyAction.NORMAL,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.CHARGE, PolicyAction.CHARGE),
                requires(
                        CharacterId.FARUZAN,
                        PolicyAction.CHARGE,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }
}
