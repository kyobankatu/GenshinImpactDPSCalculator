package mechanics.optimization;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads named action profiles for a character from a plain-text profile file.
 *
 * <p>Profile files are located at {@code profiles/<CharacterName>.txt}.  Each
 * file may contain one or more named profiles in the following format:
 * <pre>
 * PROFILE: BurstCombo
 * SKILL
 * BURST
 * ATTACK_UNTIL_END
 *
 * PROFILE: SkillOnly
 * SKILL
 * ATTACK
 * </pre>
 *
 * <p>If no profile file exists for a character, or if the file is empty,
 * {@link #loadProfiles} returns a single default {@code "Default(Skill)"}
 * profile containing only the {@code SKILL} action.
 */
public class ProfileLoader {

    /**
     * A named sequence of action commands for one character.
     *
     * <p>Action strings are matched case-insensitively in the rotation evaluator
     * and correspond to standard commands such as {@code SKILL}, {@code BURST},
     * {@code ATTACK}, and {@code ATTACK_UNTIL_END}.
     */
    public static class ActionProfile {
        /** Human-readable name of this profile (e.g. {@code "BurstCombo"}). */
        public String name;

        /** Ordered list of action command strings to execute in sequence. */
        public List<String> actions;

        /**
         * @param name    profile name
         * @param actions ordered action commands
         */
        public ActionProfile(String name, List<String> actions) {
            this.name = name;
            this.actions = actions;
        }

        @Override
        public String toString() {
            return name + actions;
        }
    }

    /**
     * Loads all {@link ActionProfile}s defined for {@code charName}.
     *
     * <p>Reads {@code profiles/<charName>.txt}, parsing blocks delimited by
     * {@code PROFILE: <name>} headers.  Lines that are blank or contain only
     * whitespace are ignored.  If the file does not exist or contains no valid
     * profiles, a single fallback profile is returned.
     *
     * @param charName the character name used to locate the profile file
     * @return a non-empty list of {@link ActionProfile}s; never {@code null}
     */
    public static List<ActionProfile> loadProfiles(String charName) {
        List<ActionProfile> profiles = new ArrayList<>();
        File file = new File("profiles/" + charName + ".txt");

        if (!file.exists()) {
            // Return default "Skill Only" if no file found
            List<String> acts = new ArrayList<>();
            acts.add("SKILL");
            profiles.add(new ActionProfile("Default(Skill)", acts));
            return profiles;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            String currentName = null;
            List<String> currentActions = new ArrayList<>();

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                if (line.startsWith("PROFILE:")) {
                    if (currentName != null) {
                        profiles.add(new ActionProfile(currentName, new ArrayList<>(currentActions)));
                    }
                    currentName = line.substring(8).trim();
                    currentActions.clear();
                } else {
                    currentActions.add(line);
                }
            }
            // Add last one
            if (currentName != null) {
                profiles.add(new ActionProfile(currentName, new ArrayList<>(currentActions)));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Ensure at least one profile
        if (profiles.isEmpty()) {
            List<String> acts = new ArrayList<>();
            acts.add("SKILL");
            profiles.add(new ActionProfile("Fallback", acts));
        }

        return profiles;
    }
}
