package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Navia, Chiori, Xianyun, and Bennett plunge scenario. */
public final class NaviaChioriPlungePartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public NaviaChioriPlungePartyDefinition() {
        super(
                "NaviaChioriPlunge",
                DatasetSplit.TRAIN,
                31.0,
                List.of(
                        CuratedCharacters::navia,
                        CuratedCharacters::chiori,
                        CuratedCharacters::xianyun,
                        CuratedCharacters::bennett),
                actions(
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE),
                requires(
                        CharacterId.NAVIA,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE,
                        PolicyAction.BURST));
    }
}
