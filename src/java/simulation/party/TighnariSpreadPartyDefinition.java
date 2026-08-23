package simulation.party;

import java.util.List;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/** Holdout-split quick Charged and Spread scenario. */
public final class TighnariSpreadPartyDefinition extends CuratedPartyDefinition {
    /** Creates the deterministic C0/base-loadout scenario. */
    public TighnariSpreadPartyDefinition() {
        super(
                "TighnariSpread",
                DatasetSplit.HOLDOUT,
                20.0,
                List.of(
                        CuratedCharacters::tighnari,
                        CuratedCharacters::yaeMiko,
                        CuratedCharacters::ororon,
                        CuratedCharacters::kirara),
                actions(
                        PolicyAction.SWAP_SLOT_3, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.SWAP_SLOT_1,
                        PolicyAction.SKILL_PRESS, PolicyAction.SKILL_PRESS,
                        PolicyAction.BURST, PolicyAction.SWAP_SLOT_2,
                        PolicyAction.SKILL_PRESS, PolicyAction.BURST,
                        PolicyAction.SWAP_SLOT_0, PolicyAction.SKILL_PRESS,
                        PolicyAction.CHARGE, PolicyAction.CHARGE,
                        PolicyAction.CHARGE, PolicyAction.BURST),
                requires(CharacterId.TIGHNARI, PolicyAction.CHARGE,
                        PolicyAction.SKILL_PRESS));
    }
}
