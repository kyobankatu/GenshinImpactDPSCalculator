package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Navia, Zhongli, Fischl, and Bennett scenario. */
public final class NaviaZhongliPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public NaviaZhongliPartyDefinition() {
        super(
                "NaviaZhongli",
                DatasetSplit.TRAIN,
                25.0,
                List.of(
                        CuratedCharacters::navia,
                        CuratedCharacters::zhongli,
                        CuratedCharacters::bennett,
                        CuratedCharacters::fischlFavonius),
                actions(
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0),
                requires(
                        CharacterId.NAVIA,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }
}
