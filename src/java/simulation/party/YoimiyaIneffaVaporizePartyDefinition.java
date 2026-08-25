package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Yoimiya, Ineffa, Yelan, and Bennett scenario. */
public final class YoimiyaIneffaVaporizePartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public YoimiyaIneffaVaporizePartyDefinition() {
        super(
                "YoimiyaIneffaVaporize",
                DatasetSplit.TRAIN,
                30.0,
                List.of(
                        CuratedCharacters::yoimiya,
                        CuratedCharacters::ineffa,
                        CuratedCharacters::yelan,
                        CuratedCharacters::bennett),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS),
                requires(
                        CharacterId.YOIMIYA,
                        PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS));
    }
}
