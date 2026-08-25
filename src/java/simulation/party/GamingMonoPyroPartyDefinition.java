package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Gaming, Xiangling, Xianyun, and Bennett Mono Pyro scenario. */
public final class GamingMonoPyroPartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public GamingMonoPyroPartyDefinition() {
        super(
                "GamingMonoPyro",
                DatasetSplit.TRAIN,
                37.0,
                List.of(
                        CuratedCharacters::gaming,
                        CuratedCharacters::xiangling,
                        CuratedCharacters::xianyun,
                        CuratedCharacters::bennett),
                actions(
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
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
                        PolicyAction.PLUNGE,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE,
                        PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.WAIT_SHORT, PolicyAction.WAIT_SHORT,
                        PolicyAction.SKILL_PRESS, PolicyAction.PLUNGE,
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0),
                requires(
                        CharacterId.GAMING,
                        PolicyAction.NORMAL,
                        PolicyAction.PLUNGE,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }
}
