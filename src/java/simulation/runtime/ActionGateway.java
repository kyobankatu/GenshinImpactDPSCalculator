package simulation.runtime;

import model.entity.ActionTriggeredArtifactEffect;
import model.entity.ActionTriggeredWeaponEffect;
import model.entity.ArtifactSet;
import model.entity.BurstTriggeredArtifactEffect;
import model.entity.Character;
import model.type.CharacterId;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Owns cooldown gating and dispatch for typed character actions.
 */
public class ActionGateway {
    /** Owning simulator. */
    private final CombatSimulator sim;

    /**
     * Creates an action gateway bound to the given simulator.
     *
     * @param sim active simulator
     */
    public ActionGateway(CombatSimulator sim) {
        this.sim = sim;
    }

    /**
     * Executes a typed character action after applying cooldown and energy gates.
     *
     * @param characterId acting character id
     * @param request typed action request
     */
    public void performAction(CharacterId characterId, CharacterActionRequest request) {
        Character character = sim.getCharacter(characterId);
        if (character == null) {
            throw new RuntimeException("Character not found: " + characterId);
        }
        character.validateActionRequest(request);
        String charName = character.getName();

        if (request.getKey() == CharacterActionKey.SKILL) {
            double wait = character.getSkillCDRemaining(sim.getCurrentTime());
            if (wait > 1e-9) {
                if (sim.isLoggingEnabled()) {
                    System.out.println(String.format(
                            "[T=%.1f] %s Skill CD: waiting %.2fs",
                            sim.getCurrentTime(), charName, wait));
                }
                sim.advanceTime(wait);
            }
        }

        if (request.getKey() == CharacterActionKey.BURST) {
            double wait = character.getBurstCDRemaining(sim.getCurrentTime());
            if (wait > 1e-9) {
                if (sim.isLoggingEnabled()) {
                    System.out.println(String.format(
                            "[T=%.1f] %s Burst CD: waiting %.2fs",
                            sim.getCurrentTime(), charName, wait));
                }
                sim.advanceTime(wait);
            }
            if (character.getCurrentEnergy() < character.getEnergyCost()) {
                if (sim.isLoggingEnabled()) {
                    System.out.println(String.format(
                            "[T=%.1f] WARNING: %s burst skipped due to insufficient energy (%.1f/%.1f)",
                            sim.getCurrentTime(), charName, character.getCurrentEnergy(), character.getEnergyCost()));
                }
                character.recordMissedBurst();
                return;
            }
        }

        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("[T=%.1f] %s triggers action: %s",
                    sim.getCurrentTime(), charName, request.getLogLabel()));
        }

        sim.notifyActionRequest(character, request);

        sim.pushBuffSource(characterId);
        boolean ownsHitlagScope = sim.beginOwnerHitlagAction(characterId);
        try {
            if (character.getWeapon() instanceof ActionTriggeredWeaponEffect) {
                ((ActionTriggeredWeaponEffect) character.getWeapon()).onAction(character, request, sim);
            }

            dispatchArtifactActionEffects(character, request);
            dispatchBurstArtifactEffects(character, request);

            sim.beginActionDirectDamageCapture(characterId);
            try {
                character.onAction(request, sim);
            } finally {
                sim.endActionDirectDamageCapture();
            }
        } finally {
            try {
                if (ownsHitlagScope) {
                    sim.finishOwnerHitlagAction(characterId);
                }
            } finally {
                sim.popBuffSource();
            }
        }
        sim.setRotationTime(sim.getCurrentTime());
    }

    /** Dispatches action-use passives for every equipped artifact set in order. */
    private void dispatchArtifactActionEffects(
            Character character,
            CharacterActionRequest request) {
        ArtifactSet[] artifacts = character.getArtifacts();
        if (artifacts == null) {
            return;
        }
        for (ArtifactSet artifact : artifacts) {
            if (artifact instanceof ActionTriggeredArtifactEffect) {
                ((ActionTriggeredArtifactEffect) artifact).onAction(character, request, sim);
            }
        }
    }

    /** Dispatches post-gate Burst-use capabilities before character Burst logic. */
    private void dispatchBurstArtifactEffects(
            Character character,
            CharacterActionRequest request) {
        if (request.getKey() != CharacterActionKey.BURST) {
            return;
        }
        ArtifactSet[] artifacts = character.getArtifacts();
        if (artifacts == null) {
            return;
        }
        for (ArtifactSet artifact : artifacts) {
            if (artifact instanceof BurstTriggeredArtifactEffect) {
                ((BurstTriggeredArtifactEffect) artifact).onBurst(sim);
            }
        }
    }
}
