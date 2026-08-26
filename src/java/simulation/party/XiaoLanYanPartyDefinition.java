package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Xiao, Furina, Xianyun, and C6 Lan Yan Plunge scenario. */
public final class XiaoLanYanPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public XiaoLanYanPartyDefinition() {
        super(
                "XiaoLanYan",
                DatasetSplit.TRAIN,
                31.0,
                List.of(
                        CuratedCharacters::xiao,
                        CuratedCharacters::furina,
                        CuratedCharacters::xianyun,
                        CuratedCharacters::lanYan),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.BURST,
                        PolicyAction.SKILL_PRESS, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE),
                requires(
                        CharacterId.XIAO,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE,
                        PolicyAction.BURST));
    }

    @Override
    public boolean supportsExactSnapshotRestore() {
        return true;
    }
}
