package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Yoimiya and Chevreuse Overloaded scenario. */
public final class YoimiyaChevreuseOverloadPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public YoimiyaChevreuseOverloadPartyDefinition() {
        super(
                "YoimiyaChevreuseOverload",
                DatasetSplit.TRAIN,
                25.0,
                List.of(
                        CuratedCharacters::yoimiya,
                        CuratedCharacters::chevreuseFavonius,
                        CuratedCharacters::bennett,
                        CuratedCharacters::fischl),
                actions(
                        PolicyAction.SWAP_SLOT_1, PolicyAction.BURST,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.NORMAL,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS),
                requires(
                        CharacterId.YOIMIYA,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL));
    }
}
