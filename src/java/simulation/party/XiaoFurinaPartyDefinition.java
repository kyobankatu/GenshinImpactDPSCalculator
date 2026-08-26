package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Xiao, Furina, Xianyun, and C0 Faruzan Plunge scenario. */
public final class XiaoFurinaPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public XiaoFurinaPartyDefinition() {
        super(
                "XiaoFurina",
                DatasetSplit.TRAIN,
                31.0,
                List.of(
                        CuratedCharacters::xiao,
                        CuratedCharacters::furina,
                        CuratedCharacters::xianyun,
                        CuratedCharacters::faruzanC0),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.CHARGE, PolicyAction.BURST,
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
