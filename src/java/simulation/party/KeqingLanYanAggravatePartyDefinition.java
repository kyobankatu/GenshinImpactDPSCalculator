package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Keqing, Lan Yan, Nahida, and Fischl Aggravate scenario. */
public final class KeqingLanYanAggravatePartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public KeqingLanYanAggravatePartyDefinition() {
        super(
                "KeqingLanYanAggravate",
                DatasetSplit.TRAIN,
                25.0,
                List.of(
                        CuratedCharacters::keqing,
                        CuratedCharacters::lanYan,
                        CuratedCharacters::nahida,
                        CuratedCharacters::fischl),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.CHARGE),
                requires(
                        CharacterId.KEQING,
                        PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }
}
