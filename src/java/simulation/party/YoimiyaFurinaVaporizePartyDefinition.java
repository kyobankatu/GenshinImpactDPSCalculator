package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Yoimiya, Furina, Bennett, and Xiangling scenario. */
public final class YoimiyaFurinaVaporizePartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public YoimiyaFurinaVaporizePartyDefinition() {
        super(
                "YoimiyaFurinaVaporize",
                DatasetSplit.TRAIN,
                30.0,
                List.of(
                        CuratedCharacters::yoimiya,
                        CuratedCharacters::furina,
                        CuratedCharacters::bennett,
                        CuratedCharacters::xiangling),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
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
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.NORMAL, PolicyAction.NORMAL),
                requires(
                        CharacterId.YOIMIYA,
                        PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS));
    }
}
