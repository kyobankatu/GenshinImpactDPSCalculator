package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Gaming, Furina, Bennett, and Xilonen Vaporize scenario. */
public final class GamingXilonenVaporizePartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public GamingXilonenVaporizePartyDefinition() {
        super(
                "GamingXilonenVaporize",
                DatasetSplit.TRAIN,
                30.0,
                List.of(
                        CuratedCharacters::gaming,
                        CuratedCharacters::furinaFavonius,
                        CuratedCharacters::bennett,
                        CuratedCharacters::xilonen),
                actions(
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
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
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE),
                requires(
                        CharacterId.GAMING,
                        PolicyAction.PLUNGE,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }
}
