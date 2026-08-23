package simulation.party;

/** Dataset partition assigned to one immutable party/loadout scenario. */
public enum DatasetSplit {
    TRAIN("train"),
    VALIDATION("validation"),
    HOLDOUT("holdout");

    private final String wireName;

    DatasetSplit(String wireName) {
        this.wireName = wireName;
    }

    /** Returns the stable dataset wire value. */
    public String getWireName() {
        return wireName;
    }
}
