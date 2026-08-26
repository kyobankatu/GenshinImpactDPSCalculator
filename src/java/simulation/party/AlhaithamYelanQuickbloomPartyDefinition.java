package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Alhaitham, Nahida, Yelan, and Shinobu Quickbloom scenario. */
public final class AlhaithamYelanQuickbloomPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public AlhaithamYelanQuickbloomPartyDefinition() {
        super(
                "AlhaithamYelanQuickbloom",
                DatasetSplit.TRAIN,
                35.0,
                List.of(
                        CuratedCharacters::alhaitham,
                        CuratedCharacters::nahida,
                        CuratedCharacters::yelan,
                        CuratedCharacters::kukiShinobu),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE),
                requires(
                        CharacterId.ALHAITHAM,
                        PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }

    @Override
    public boolean supportsExactSnapshotRestore() {
        return true;
    }
}
