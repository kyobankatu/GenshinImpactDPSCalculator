package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Navia, Chiori, Fischl, and Bennett scenario. */
public final class NaviaChioriPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public NaviaChioriPartyDefinition() {
        super(
                "NaviaChiori",
                DatasetSplit.TRAIN,
                25.0,
                List.of(
                        CuratedCharacters::navia,
                        CuratedCharacters::bennett,
                        CuratedCharacters::chiori,
                        CuratedCharacters::fischlFavonius),
                actions(
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0),
                requires(
                        CharacterId.CHIORI,
                        PolicyAction.SKILL_PRESS));
    }
}
