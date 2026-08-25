package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Gaming, Furina, Xianyun, and Bennett Vaporize scenario. */
public final class GamingFurinaVaporizePartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public GamingFurinaVaporizePartyDefinition() {
        super(
                "GamingFurinaVaporize",
                DatasetSplit.TRAIN,
                37.0,
                List.of(
                        CuratedCharacters::gaming,
                        CuratedCharacters::furina,
                        CuratedCharacters::xianyun,
                        CuratedCharacters::bennett),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.BURST,
                        PolicyAction.PLUNGE,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.NORMAL, PolicyAction.NORMAL),
                requires(
                        CharacterId.GAMING,
                        PolicyAction.NORMAL,
                        PolicyAction.PLUNGE,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }
}
