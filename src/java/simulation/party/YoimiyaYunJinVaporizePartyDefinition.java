package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Yoimiya, Yelan, Yun Jin, and Zhongli Vaporize scenario. */
public final class YoimiyaYunJinVaporizePartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public YoimiyaYunJinVaporizePartyDefinition() {
        super(
                "YoimiyaYunJinVaporize",
                DatasetSplit.TRAIN,
                25.0,
                List.of(
                        CuratedCharacters::yoimiya,
                        CuratedCharacters::yelan,
                        CuratedCharacters::yunJin,
                        CuratedCharacters::zhongli),
                actions(
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2,
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
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS),
                requires(
                        CharacterId.YOIMIYA,
                        PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS));
    }
}
