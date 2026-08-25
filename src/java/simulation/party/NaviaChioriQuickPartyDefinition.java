package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Navia, Bennett, Fischl, and Chiori quickswap scenario. */
public final class NaviaChioriQuickPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public NaviaChioriQuickPartyDefinition() {
        super(
                "NaviaChioriQuick",
                DatasetSplit.TRAIN,
                25.0,
                List.of(
                        CuratedCharacters::navia,
                        CuratedCharacters::bennett,
                        CuratedCharacters::fischlFavonius,
                        CuratedCharacters::chiori),
                actions(
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS),
                requires(
                        CharacterId.CHIORI,
                        PolicyAction.SKILL_PRESS));
    }
}
