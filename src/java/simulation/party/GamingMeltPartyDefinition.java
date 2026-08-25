package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split initial Burst and Melt Plunging scenario. */
public final class GamingMeltPartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic C0/base-loadout scenario. */
    public GamingMeltPartyDefinition() {
        super(
                "GamingMelt",
                DatasetSplit.TRAIN,
                22.0,
                List.of(
                        CuratedCharacters::gaming,
                        CuratedCharacters::rosaria,
                        CuratedCharacters::xianyun,
                        CuratedCharacters::layla),
                actions(
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.SWAP_SLOT_0,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
                        PolicyAction.PLUNGE, PolicyAction.NORMAL),
                requires(CharacterId.GAMING, PolicyAction.PLUNGE,
                        PolicyAction.SKILL_PRESS));
    }
}
