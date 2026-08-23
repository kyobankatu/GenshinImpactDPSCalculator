package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Validation-split Freeze and Charged-shot scenario without dash state. */
public final class GanyuFreezePartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic C0/base-loadout scenario. */
    public GanyuFreezePartyDefinition() {
        super(
                "GanyuFreeze",
                DatasetSplit.VALIDATION,
                24.0,
                List.of(
                        CuratedCharacters::ganyu,
                        CuratedCharacters::shenhe,
                        CuratedCharacters::sangonomiyaKokomi,
                        CuratedCharacters::kaedeharaKazuha),
                actions(
                        PolicyAction.SWAP_SLOT_2, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.SWAP_SLOT_0,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
                        PolicyAction.CHARGE, PolicyAction.CHARGE,
                        PolicyAction.CHARGE),
                requires(CharacterId.GANYU, PolicyAction.CHARGE,
                        PolicyAction.BURST));
    }
}
