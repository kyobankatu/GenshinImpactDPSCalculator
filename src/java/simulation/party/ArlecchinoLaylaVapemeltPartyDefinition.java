package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Arlecchino, Yelan, Bennett, and Layla Vapemelt scenario. */
public final class ArlecchinoLaylaVapemeltPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public ArlecchinoLaylaVapemeltPartyDefinition() {
        super(
                "ArlecchinoLaylaVapemelt",
                DatasetSplit.TRAIN,
                30.0,
                List.of(
                        CuratedCharacters::arlecchino,
                        CuratedCharacters::yelan,
                        CuratedCharacters::bennett,
                        CuratedCharacters::layla),
                actions(
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.BURST, PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.CHARGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL),
                requires(
                        CharacterId.ARLECCHINO,
                        PolicyAction.NORMAL,
                        PolicyAction.CHARGE,
                        PolicyAction.SKILL_PRESS));
    }
}
