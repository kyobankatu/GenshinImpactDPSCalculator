package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Gaming, Furina, Bennett, and Fischl Vaporize scenario. */
public final class GamingFischlVaporizePartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public GamingFischlVaporizePartyDefinition() {
        super(
                "GamingFischlVaporize",
                DatasetSplit.TRAIN,
                35.0,
                List.of(
                        CuratedCharacters::gaming,
                        CuratedCharacters::furinaFavonius,
                        CuratedCharacters::bennett,
                        CuratedCharacters::fischl),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.BURST,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
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
