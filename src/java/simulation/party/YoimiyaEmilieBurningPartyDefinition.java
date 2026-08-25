package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Yoimiya, Bennett, Emilie, and Fischl Burning scenario. */
public final class YoimiyaEmilieBurningPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public YoimiyaEmilieBurningPartyDefinition() {
        super(
                "YoimiyaEmilieBurning",
                DatasetSplit.TRAIN,
                30.0,
                List.of(
                        CuratedCharacters::yoimiya,
                        CuratedCharacters::bennett,
                        CuratedCharacters::emilie,
                        CuratedCharacters::fischl),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
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
                        PolicyAction.BURST),
                requires(
                        CharacterId.YOIMIYA,
                        PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS));
    }
}
