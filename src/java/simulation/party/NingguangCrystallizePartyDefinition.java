package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Holdout-split Geo Charged and Crystallize scenario without shard pickup. */
public final class NingguangCrystallizePartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic C0/base-loadout scenario. */
    public NingguangCrystallizePartyDefinition() {
        super(
                "NingguangCrystallize",
                DatasetSplit.HOLDOUT,
                22.0,
                List.of(
                        CuratedCharacters::ningguang,
                        CuratedCharacters::yaeMiko,
                        CuratedCharacters::ororon,
                        CuratedCharacters::kirara),
                actions(
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.CHARGE, PolicyAction.BURST),
                requires(CharacterId.NINGGUANG, PolicyAction.CHARGE,
                        PolicyAction.SKILL_PRESS));
    }
}
