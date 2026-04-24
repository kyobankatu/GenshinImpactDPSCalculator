package model.type;

public enum CharacterId {
    BENNETT("Bennett"),
    COLUMBINA("Columbina"),
    FLINS("Flins"),
    INEFFA("Ineffa"),
    RAIDEN_SHOGUN("Raiden Shogun"),
    SUCROSE("Sucrose"),
    XIANGLING("Xiangling"),
    XINGQIU("Xingqiu"),
    UNKNOWN("Unknown");

    private final String displayName;

    CharacterId(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CharacterId fromName(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        for (CharacterId id : values()) {
            if (id.displayName.equals(name)) {
                return id;
            }
        }
        return UNKNOWN;
    }
}
