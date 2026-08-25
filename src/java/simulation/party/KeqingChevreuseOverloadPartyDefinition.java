package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Keqing and Chevreuse two-part Overloaded scenario. */
public final class KeqingChevreuseOverloadPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public KeqingChevreuseOverloadPartyDefinition() {
        super(
                "KeqingChevreuseOverload",
                DatasetSplit.TRAIN,
                40.0,
                List.of(
                        CuratedCharacters::keqing,
                        CuratedCharacters::chevreuse,
                        CuratedCharacters::fischl,
                        CuratedCharacters::xiangling),
                actions(
                        PolicyAction.SWAP_SLOT_1, PolicyAction.BURST,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.BURST,
                        PolicyAction.SKILL_PRESS, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.BURST,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.CHARGE),
                requires(
                        CharacterId.KEQING,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.CHARGE,
                        PolicyAction.BURST));
    }
}
