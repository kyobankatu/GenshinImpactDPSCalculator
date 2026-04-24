package mechanics.optimization;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses named action profiles from a plain-text profile source.
 *
 * <p>The source may contain one or more named profiles in the following format:
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
 * <p>File resolution is intentionally kept outside this parser so runtime code
 * can stay independent from external profile-file naming conventions.
 */
public class ProfileLoader {
    public static final String DEFAULT_PROFILE_NAME = "Default(Skill)";
    public static final String FALLBACK_PROFILE_NAME = "Fallback";

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
        public List<ProfileAction> actions;

        /**
         * @param name    profile name
         * @param actions ordered action commands
         */
        public ActionProfile(String name, List<ProfileAction> actions) {
            this.name = name;
            this.actions = actions;
        }

        @Override
        public String toString() {
            return name + actions;
        }
    }

    /**
     * Parses all {@link ActionProfile}s from the provided character profile text.
     *
     * <p>Blocks are delimited by {@code PROFILE: <name>} headers. Lines that are
     * blank or contain only whitespace are ignored. If the source contains no
     * valid profiles, a single fallback profile is returned.
     *
     * @param reader source containing profile definitions
     * @return a non-empty list of {@link ActionProfile}s; never {@code null}
     */
    public static List<ActionProfile> loadProfiles(Reader reader) {
        List<ActionProfile> profiles = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            String currentName = null;
            List<ProfileAction> currentActions = new ArrayList<>();

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
                    currentActions.add(ProfileAction.parse(line));
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
            return defaultSkillFallback(FALLBACK_PROFILE_NAME);
        }

        return profiles;
    }

    public static List<ActionProfile> defaultSkillFallback(String profileName) {
        List<ActionProfile> profiles = new ArrayList<>();
        List<ProfileAction> acts = new ArrayList<>();
        acts.add(ProfileAction.SKILL);
        profiles.add(new ActionProfile(profileName, acts));
        return profiles;
    }
}
