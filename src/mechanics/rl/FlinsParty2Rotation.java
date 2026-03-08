package mechanics.rl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Defines the teacher rotation for the FlinsParty2 team
 * (Flins / Ineffa / Columbina / Sucrose).
 *
 * One half-rotation per episode (~40 s):
 * <ol>
 *   <li>Ineffa  — Skill → Burst</li>
 *   <li>Columbina — Skill → Burst</li>
 *   <li>Sucrose — Skill → Burst</li>
 *   <li>Flins   — carry phase (Skill×2, Attack×4, Burst, Attack×5, Skill, Burst, Attack×8, Skill, Burst, Attack×3)</li>
 *   <li>Sucrose — Skill → Attack×3</li>
 * </ol>
 */
public class FlinsParty2Rotation {

    /** Returns the ordered list of {@link RotationPhase} for this team. */
    public static List<RotationPhase> build() {
        List<RotationPhase> rotation = new ArrayList<>();

        // Phase 0: Ineffa Skill -> Burst
        rotation.add(new RotationPhase("Ineffa", Arrays.asList("SKILL", "BURST")));

        // Phase 1: Columbina Skill -> Burst
        rotation.add(new RotationPhase("Columbina", Arrays.asList("SKILL", "BURST")));

        // Phase 2: Sucrose Skill -> Burst
        rotation.add(new RotationPhase("Sucrose", Arrays.asList("SKILL", "BURST")));

        // Phase 3: Flins carry phase
        List<String> flinsActs = new ArrayList<>();
        flinsActs.add("SKILL");
        flinsActs.add("SKILL");
        for (int i = 0; i < 4; i++) { flinsActs.add("ATTACK"); }
        flinsActs.add("BURST");
        for (int i = 0; i < 5; i++) { flinsActs.add("ATTACK"); }
        flinsActs.add("SKILL");
        flinsActs.add("BURST");
        for (int i = 0; i < 8; i++) { flinsActs.add("ATTACK"); }
        flinsActs.add("SKILL");
        flinsActs.add("BURST");
        for (int i = 0; i < 3; i++) { flinsActs.add("ATTACK"); }
        rotation.add(new RotationPhase("Flins", flinsActs));

        // Phase 4: Sucrose Skill -> Attack x3
        List<String> sucroseEnd = new ArrayList<>();
        sucroseEnd.add("SKILL");
        for (int i = 0; i < 3; i++) { sucroseEnd.add("ATTACK"); }
        rotation.add(new RotationPhase("Sucrose", sucroseEnd));

        return rotation;
    }

    private FlinsParty2Rotation() {}
}
