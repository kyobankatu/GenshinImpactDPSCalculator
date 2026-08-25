package simulation.party;

import java.util.List;
import java.util.Map;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Ayato, Bennett, Xiangling, and Sucrose Vaporize scenario. */
public final class AyatoSucroseVaporizePartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public AyatoSucroseVaporizePartyDefinition() {
        super(
                "AyatoSucroseVaporize",
                DatasetSplit.TRAIN,
                36.0,
                List.of(
                        CuratedCharacters::kamisatoAyato,
                        CuratedCharacters::bennett,
                        CuratedCharacters::xiangling,
                        CuratedCharacters::sucrose),
                actions(
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL),
                requires(
                        CharacterId.KAMISATO_AYATO,
                        PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST),
                Map.of(
                        CharacterId.KAMISATO_AYATO, 1.56,
                        CharacterId.XIANGLING, 2.15));
    }
}
