package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Anemo Burst and Plunging scenario. */
public final class XiaoPlungePartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic C0/base-loadout scenario. */
    public XiaoPlungePartyDefinition() {
        super(
                "XiaoPlunge",
                DatasetSplit.TRAIN,
                22.0,
                List.of(
                        CuratedCharacters::xiao,
                        CuratedCharacters::faruzan,
                        CuratedCharacters::bennett,
                        CuratedCharacters::zhongli),
                actions(
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.SWAP_SLOT_0,
                        PolicyAction.SKILL_PRESS, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE,
                        PolicyAction.PLUNGE, PolicyAction.PLUNGE),
                requires(CharacterId.XIAO, PolicyAction.PLUNGE,
                        PolicyAction.BURST));
    }
}
