package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Wanderer, Faruzan, Layla, and Bennett scenario. */
public final class WandererLaylaPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public WandererLaylaPartyDefinition() {
        super(
                "WandererLayla",
                DatasetSplit.TRAIN,
                25.0,
                List.of(
                        CuratedCharacters::wanderer,
                        CuratedCharacters::faruzan,
                        CuratedCharacters::layla,
                        CuratedCharacters::bennett),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS),
                requires(
                        CharacterId.WANDERER,
                        PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS));
    }
}
