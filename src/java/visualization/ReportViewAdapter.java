package visualization;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mechanics.analysis.StatsSnapshot;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.standards.KQMSConstants;
import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Adapts typed simulation/analysis data into report-facing labels and DOM keys.
 *
 * <p>Display names, HTML-safe row keys, and report-oriented stat aggregates are
 * boundary concerns owned by the visualization layer, not by combat logic.
 */
public final class ReportViewAdapter {
    private ReportViewAdapter() {
    }

    /**
     * Builds report-facing views for every active party member.
     *
     * @param sim source combat simulator; may be {@code null}
     * @return an ordered list of {@link ReportCharacterView} entries, or an empty
     *         list when {@code sim} is {@code null}
     */
    public static List<ReportCharacterView> partyCharacters(CombatSimulator sim) {
        return partyCharacters(sim, ReportAssetMode.OUTPUT);
    }

    /**
     * Builds report-facing views for every active party member using paths for the
     * requested report destination.
     *
     * @param sim       source combat simulator; may be {@code null}
     * @param assetMode output asset path mode
     * @return an ordered list of {@link ReportCharacterView} entries, or an empty
     *         list when {@code sim} is {@code null}
     */
    public static List<ReportCharacterView> partyCharacters(CombatSimulator sim, ReportAssetMode assetMode) {
        List<ReportCharacterView> characters = new ArrayList<>();
        if (sim == null) {
            return characters;
        }
        ReportAssetMode mode = assetMode != null ? assetMode : ReportAssetMode.OUTPUT;
        for (Character character : sim.getPartyMembers()) {
            CharacterId characterId = character.getCharacterId();
            String displayName = character.getName();
            Path localFacePath = localFacePath(displayName);
            characters.add(new ReportCharacterView(characterId,
                    domKey(characterId),
                    displayName,
                    character.getElement(),
                    KQMSConstants.CHAR_LEVEL,
                    normalizedConstellation(character.getConstellation()),
                    weaponName(character),
                    weaponIconPath(character, mode),
                    hasWeaponIcon(character),
                    artifactSetViews(character, mode),
                    reportFacePath(displayName, mode),
                    Files.isRegularFile(localFacePath),
                    fallbackText(displayName)));
        }
        return characters;
    }

    /**
     * Adapts analysis stat snapshots into report-friendly snapshots keyed by DOM
     * identifier.
     *
     * @param statsHistory raw stat snapshots from the analysis layer; may be
     *                     {@code null}
     * @return a list of adapted snapshots, or an empty list when no history is
     *         provided
     */
    public static List<ReportStatsSnapshot> statsHistory(List<StatsSnapshot> statsHistory) {
        if (statsHistory == null) {
            return Collections.emptyList();
        }

        List<ReportStatsSnapshot> adapted = new ArrayList<>();
        for (StatsSnapshot snapshot : statsHistory) {
            Map<String, ReportCharacterStats> reportCharacters = new LinkedHashMap<>();
            for (Map.Entry<CharacterId, Map<StatType, Double>> entry : snapshot.characterStats.entrySet()) {
                CharacterId characterId = entry.getKey();
                Map<StatType, Double> stats = entry.getValue();
                List<String> buffs = snapshot.characterBuffs != null
                        ? snapshot.characterBuffs.getOrDefault(characterId, Collections.emptyList())
                        : Collections.emptyList();
                double energyPercent = snapshot.characterEnergyPercent != null
                        ? snapshot.characterEnergyPercent.getOrDefault(characterId, 0.0)
                        : 0.0;
                reportCharacters.put(domKey(characterId), new ReportCharacterStats(stats, buffs, energyPercent));
            }
            adapted.add(new ReportStatsSnapshot(snapshot.time, reportCharacters));
        }
        return adapted;
    }

    /**
     * Converts a {@link CharacterId} into the HTML-safe key used as the DOM row
     * identifier in the report.
     *
     * @param characterId character identifier (must be non-null)
     * @return the lowercased enum name suitable for use as a DOM id suffix
     */
    public static String domKey(CharacterId characterId) {
        return characterId.name().toLowerCase();
    }

    private static Path localFacePath(String displayName) {
        return Path.of("config", "characters", assetKey(displayName), "face.png");
    }

    private static String reportFacePath(String displayName, ReportAssetMode mode) {
        String key = urlPathSegment(assetKey(displayName));
        if (mode == ReportAssetMode.DOCS) {
            return "assets/report/characters/" + key + "/face.png";
        }
        return "../config/characters/" + key + "/face.png";
    }

    private static int normalizedConstellation(int constellation) {
        return Math.max(0, Math.min(6, constellation));
    }

    private static String weaponName(Character character) {
        if (character.getWeapon() == null || character.getWeapon().getName() == null
                || character.getWeapon().getName().isBlank()) {
            return "-";
        }
        return character.getWeapon().getName();
    }

    private static String weaponIconPath(Character character, ReportAssetMode mode) {
        String key = urlPathSegment(assetKey(weaponName(character)));
        if (mode == ReportAssetMode.DOCS) {
            return "assets/report/weapons/" + key + "/icon.png";
        }
        return "../config/weapons/" + key + "/icon.png";
    }

    private static boolean hasWeaponIcon(Character character) {
        String name = weaponName(character);
        return !"-".equals(name) && Files.isRegularFile(Path.of("config", "weapons", assetKey(name), "icon.png"));
    }

    private static List<ReportAssetView> artifactSetViews(Character character, ReportAssetMode mode) {
        if (character.getArtifacts() == null) {
            return Collections.emptyList();
        }
        List<ReportAssetView> artifacts = new ArrayList<>();
        for (ArtifactSet artifact : character.getArtifacts()) {
            if (artifact == null || artifact.getName() == null || artifact.getName().isBlank()) {
                continue;
            }
            String name = artifact.getName();
            String key = assetKey(name);
            String imagePath = mode == ReportAssetMode.DOCS
                    ? "assets/report/artifacts/" + urlPathSegment(key) + "/flower.png"
                    : "../config/artifacts/" + key + "/flower.png";
            artifacts.add(new ReportAssetView(
                    name,
                    imagePath,
                    Files.isRegularFile(Path.of("config", "artifacts", key, "flower.png"))));
        }
        return artifacts;
    }

    static String assetKey(String displayName) {
        if (displayName == null || displayName.isBlank() || "-".equals(displayName)) {
            return "";
        }
        StringBuilder key = new StringBuilder();
        boolean wordStart = true;
        for (int i = 0; i < displayName.length(); i++) {
            char ch = displayName.charAt(i);
            if (java.lang.Character.isLetterOrDigit(ch)) {
                key.append(wordStart ? java.lang.Character.toUpperCase(ch) : ch);
                wordStart = false;
            } else {
                wordStart = true;
            }
        }
        return key.toString();
    }

    enum ReportAssetMode {
        OUTPUT,
        DOCS
    }

    private static String urlPathSegment(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 should always be available", e);
        }
    }

    private static String fallbackText(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "?";
        }
        StringBuilder fallback = new StringBuilder();
        for (String part : displayName.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                fallback.append(java.lang.Character.toUpperCase(part.charAt(0)));
            }
            if (fallback.length() >= 2) {
                break;
            }
        }
        return fallback.length() > 0 ? fallback.toString() : "?";
    }

    /**
     * View object describing a single party member for report rendering.
     */
    public static final class ReportCharacterView {
        /** Underlying character identifier. */
        public final CharacterId id;
        /** DOM-safe key derived from {@link #id}. */
        public final String domKey;
        /** Human-readable name used in tables and legends. */
        public final String displayName;
        /** Primary element used for report color selection. */
        public final model.type.Element element;
        /** Standard character level used by the simulator's report assumptions. */
        public final int level;
        /** Character constellation level, clamped to the valid 0-6 display range. */
        public final int constellation;
        /** Equipped weapon display name. */
        public final String weaponName;
        /** Relative equipped weapon icon path from the generated report. */
        public final String weaponIconPath;
        /** Whether the expected local weapon icon exists. */
        public final boolean hasWeaponIcon;
        /** Equipped artifact set display data. */
        public final List<ReportAssetView> artifactSets;
        /** Relative face image path from the generated report. */
        public final String faceImagePath;
        /** Whether the expected local face image exists. */
        public final boolean hasFaceIcon;
        /** Deterministic text fallback used when no face image is available. */
        public final String faceFallbackText;

        /**
         * Creates a new character view.
         *
         * @param id               underlying character identifier
         * @param domKey           DOM-safe key (typically {@link #domKey(CharacterId)})
         * @param displayName      human-readable display name
         * @param element          primary element used for report color selection
         * @param level            standard character level used in the simulation
         * @param constellation    constellation level
         * @param weaponName       equipped weapon display name
         * @param weaponIconPath   relative weapon icon path for generated reports
         * @param hasWeaponIcon    whether the weapon icon exists locally
         * @param artifactSets     equipped artifact set display data
         * @param faceImagePath    relative face image path for generated reports
         * @param hasFaceIcon      whether the face image exists locally
         * @param faceFallbackText deterministic fallback text
         */
        public ReportCharacterView(CharacterId id, String domKey, String displayName, model.type.Element element,
                int level, int constellation, String weaponName, String weaponIconPath, boolean hasWeaponIcon,
                List<ReportAssetView> artifactSets,
                String faceImagePath, boolean hasFaceIcon, String faceFallbackText) {
            this.id = id;
            this.domKey = domKey;
            this.displayName = displayName;
            this.element = element;
            this.level = level;
            this.constellation = constellation;
            this.weaponName = weaponName;
            this.weaponIconPath = weaponIconPath;
            this.hasWeaponIcon = hasWeaponIcon;
            this.artifactSets = Collections.unmodifiableList(new ArrayList<>(artifactSets));
            this.faceImagePath = faceImagePath;
            this.hasFaceIcon = hasFaceIcon;
            this.faceFallbackText = faceFallbackText;
        }
    }

    /**
     * View object for a report asset with an optional local icon.
     */
    public static final class ReportAssetView {
        /** Human-readable asset name. */
        public final String displayName;
        /** Relative image path from the generated report. */
        public final String imagePath;
        /** Whether the expected image exists locally. */
        public final boolean hasIcon;

        /**
         * Creates a new asset view.
         *
         * @param displayName human-readable asset name
         * @param imagePath   relative image path for generated reports
         * @param hasIcon     whether the image exists locally
         */
        public ReportAssetView(String displayName, String imagePath, boolean hasIcon) {
            this.displayName = displayName;
            this.imagePath = imagePath;
            this.hasIcon = hasIcon;
        }
    }

    /**
     * Adapted snapshot of party stats at a single point in simulation time, keyed
     * by DOM identifier.
     */
    public static final class ReportStatsSnapshot {
        /** Simulation time (seconds) the snapshot was taken at. */
        public final double time;
        /** Per-character stats, keyed by DOM identifier. */
        public final Map<String, ReportCharacterStats> characters;

        /**
         * Creates a new adapted stats snapshot.
         *
         * @param time       simulation time in seconds
         * @param characters per-character stats keyed by DOM identifier
         */
        public ReportStatsSnapshot(double time, Map<String, ReportCharacterStats> characters) {
            this.time = time;
            this.characters = characters;
        }
    }

    /**
     * Pre-computed display-ready stat values (e.g. ATK with bonuses, crit
     * percentages) for a single character at one snapshot.
     */
    public static final class ReportCharacterStats {
        /** Effective ATK after percent and flat bonuses. */
        public final double atk;
        /** Effective HP after percent and flat bonuses. */
        public final double hp;
        /** Effective DEF after percent and flat bonuses. */
        public final double def;
        /** Crit Rate expressed as a percentage value (e.g. 65.0). */
        public final double cr;
        /** Crit DMG expressed as a percentage value (e.g. 180.0). */
        public final double cd;
        /** Energy Recharge expressed as a percentage value (e.g. 150.0). */
        public final double er;
        /** Elemental Mastery as a raw value. */
        public final double em;
        /** Strongest elemental DMG% bonus across all elements, as a percent. */
        public final double dmg;
        /** Current energy as a percentage of burst cost. */
        public final double energy;
        /** Active buff display names at the snapshot time. */
        public final List<String> buffs;

        /**
         * Reduces a raw stat map and buff list into report-ready values.
         *
         * @param stats raw stat map keyed by {@link StatType}; may be {@code null}
         * @param buffs active buff display names for the same snapshot
         */
        public ReportCharacterStats(Map<StatType, Double> stats, List<String> buffs) {
            this(stats, buffs, 0.0);
        }

        /**
         * Reduces a raw stat map, buff list, and energy value into report-ready values.
         *
         * @param stats         raw stat map keyed by {@link StatType}; may be {@code null}
         * @param buffs         active buff display names for the same snapshot
         * @param energyPercent current energy as a percentage of burst cost
         */
        public ReportCharacterStats(Map<StatType, Double> stats, List<String> buffs, double energyPercent) {
            Map<StatType, Double> safeStats = stats != null ? stats : new EnumMap<>(StatType.class);
            this.atk = safeStats.getOrDefault(StatType.BASE_ATK, 0.0)
                    * (1 + safeStats.getOrDefault(StatType.ATK_PERCENT, 0.0))
                    + safeStats.getOrDefault(StatType.ATK_FLAT, 0.0);
            this.hp = safeStats.getOrDefault(StatType.BASE_HP, 0.0)
                    * (1 + safeStats.getOrDefault(StatType.HP_PERCENT, 0.0))
                    + safeStats.getOrDefault(StatType.HP_FLAT, 0.0);
            this.def = safeStats.getOrDefault(StatType.BASE_DEF, 0.0)
                    * (1 + safeStats.getOrDefault(StatType.DEF_PERCENT, 0.0))
                    + safeStats.getOrDefault(StatType.DEF_FLAT, 0.0);
            this.cr = safeStats.getOrDefault(StatType.CRIT_RATE, 0.0) * 100;
            this.cd = safeStats.getOrDefault(StatType.CRIT_DMG, 0.0) * 100;
            this.er = (safeStats.getOrDefault(StatType.ENERGY_RECHARGE, 0.0)
                    + safeStats.getOrDefault(
                            StatType.NON_CONVERTING_ENERGY_RECHARGE, 0.0)) * 100;
            this.em = safeStats.getOrDefault(StatType.ELEMENTAL_MASTERY, 0.0);
            this.dmg = strongestElementalBonusPercent(safeStats);
            this.energy = energyPercent;
            this.buffs = buffs;
        }

        private double strongestElementalBonusPercent(Map<StatType, Double> stats) {
            return Math.max(stats.getOrDefault(StatType.PYRO_DMG_BONUS, 0.0),
                    Math.max(stats.getOrDefault(StatType.HYDRO_DMG_BONUS, 0.0),
                            Math.max(stats.getOrDefault(StatType.ELECTRO_DMG_BONUS, 0.0),
                                    Math.max(stats.getOrDefault(StatType.CRYO_DMG_BONUS, 0.0),
                                            Math.max(stats.getOrDefault(StatType.ANEMO_DMG_BONUS, 0.0),
                                                    Math.max(stats.getOrDefault(StatType.GEO_DMG_BONUS, 0.0),
                                                            stats.getOrDefault(StatType.DENDRO_DMG_BONUS, 0.0)))))))
                    * 100;
        }
    }
}
