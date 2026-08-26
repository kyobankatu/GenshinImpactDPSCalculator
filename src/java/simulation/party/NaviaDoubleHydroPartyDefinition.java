package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Train-split Navia, Xilonen, Furina, and Yelan scenario. */
public final class NaviaDoubleHydroPartyDefinition
        extends CuratedPartyDefinition {
    /** Creates the deterministic source-matched loadout scenario. */
    public NaviaDoubleHydroPartyDefinition() {
        super(
                "NaviaDoubleHydro",
                DatasetSplit.TRAIN,
                30.0,
                List.of(
                        CuratedCharacters::navia,
                        CuratedCharacters::xilonen,
                        CuratedCharacters::furina,
                        CuratedCharacters::yelan),
                actions(
                        PolicyAction.SWAP_SLOT_1, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.BURST, PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_0,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL,
                        PolicyAction.NORMAL,
                        PolicyAction.SWAP_SLOT_3,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.NORMAL, PolicyAction.NORMAL),
                requires(
                        CharacterId.NAVIA,
                        PolicyAction.NORMAL,
                        PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST));
    }

    @Override
    public boolean supportsExactSnapshotRestore() {
        return true;
    }
}
