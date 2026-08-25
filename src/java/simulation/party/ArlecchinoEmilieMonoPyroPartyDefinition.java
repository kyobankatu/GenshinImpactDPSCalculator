package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Arlecchino and Emilie Mono Pyro scenario. */
public final class ArlecchinoEmilieMonoPyroPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public ArlecchinoEmilieMonoPyroPartyDefinition() {
        super(
                "ArlecchinoEmilieMonoPyro",
                DatasetSplit.TRAIN,
                30.0,
                List.of(
                        CuratedCharacters::arlecchino,
                        CuratedCharacters::bennett,
                        CuratedCharacters::emilie,
                        CuratedCharacters::lanYan),
                actions(
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
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
                        PolicyAction.NORMAL, PolicyAction.NORMAL),
                requires(
                        CharacterId.ARLECCHINO,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.CHARGE));
    }
}
