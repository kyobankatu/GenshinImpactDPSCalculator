package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Furina, Xilonen, Xingqiu, and Yelan Mono Hydro scenario. */
public final class FurinaXilonenMonoHydroPartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched first-cycle scenario. */
    public FurinaXilonenMonoHydroPartyDefinition() {
        super(
                "FurinaXilonenMonoHydro",
                DatasetSplit.TRAIN,
                32.0,
                List.of(
                        CuratedCharacters::furina,
                        CuratedCharacters::xilonen,
                        CuratedCharacters::xingqiu,
                        CuratedCharacters::yelan),
                actions(
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0),
                requires(
                        CharacterId.FURINA,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }
}
