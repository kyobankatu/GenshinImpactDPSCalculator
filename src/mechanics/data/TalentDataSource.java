package mechanics.data;

/**
 * Read-only source of character talent and base-stat values.
 */
public interface TalentDataSource {
    double get(String charName, String key, double defaultValue);
}
