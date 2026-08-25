package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Furina, Xilonen, Yelan, and Ayato Mono Hydro scenario. */
public final class AyatoXilonenMonoHydroPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public AyatoXilonenMonoHydroPartyDefinition() {
        super(
                "AyatoXilonenMonoHydro",
                DatasetSplit.TRAIN,
                40.0,
                List.of(
                        CuratedCharacters::furina,
                        CuratedCharacters::xilonen,
                        CuratedCharacters::yelan,
                        CuratedCharacters::kamisatoAyato),
                actions(
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0),
                requires(
                        CharacterId.KAMISATO_AYATO,
                        PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }
}
