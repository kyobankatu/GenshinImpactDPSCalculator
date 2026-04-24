package mechanics.optimization;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import model.type.CharacterId;

/**
 * Boundary adapter for the plain-text profile file format.
 *
 * <p>This class is the only place in the optimization flow that should know
 * how a {@link CharacterId} maps to {@code profiles/<CharacterName>.txt}.
 * Parsing remains delegated to {@link ProfileLoader}.
 */
public final class ProfileFileAdapter {
    private static final String PROFILE_DIR = "profiles";

    private ProfileFileAdapter() {
    }

    public static List<ProfileLoader.ActionProfile> loadProfiles(CharacterId characterId) {
        File file = resolveProfileFile(characterId);
        if (!file.exists()) {
            return ProfileLoader.defaultSkillFallback(ProfileLoader.DEFAULT_PROFILE_NAME);
        }

        try {
            return ProfileLoader.loadProfiles(new FileReader(file));
        } catch (IOException e) {
            e.printStackTrace();
            return ProfileLoader.defaultSkillFallback(ProfileLoader.FALLBACK_PROFILE_NAME);
        }
    }

    static File resolveProfileFile(CharacterId characterId) {
        return new File(PROFILE_DIR, characterId.getDisplayName() + ".txt");
    }
}
