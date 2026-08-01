package mechanics.formula;

import java.util.List;

import mechanics.buff.Buff;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;

/**
 * Centralizes enemy resistance and resistance-shred math for outgoing damage.
 */
public final class ResistanceCalculator {
    private ResistanceCalculator() {
    }

    /**
     * Returns the total resistance shred applicable to the given element.
     *
     * @param stats   attacker stats after buffs
     * @param element damage element being resolved
     * @return generic plus element-specific resistance shred
     */
    public static double getTotalResShred(StatsContainer stats, Element element) {
        double resShred = stats.get(StatType.RES_SHRED);
        switch (element) {
            case PYRO:
                return resShred + stats.get(StatType.PYRO_RES_SHRED);
            case HYDRO:
                return resShred + stats.get(StatType.HYDRO_RES_SHRED);
            case CRYO:
                return resShred + stats.get(StatType.CRYO_RES_SHRED);
            case ELECTRO:
                return resShred + stats.get(StatType.ELECTRO_RES_SHRED);
            case ANEMO:
                return resShred + stats.get(StatType.ANEMO_RES_SHRED);
            case GEO:
                return resShred + stats.get(StatType.GEO_RES_SHRED);
            case DENDRO:
                return resShred + stats.get(StatType.DENDRO_RES_SHRED);
            case PHYSICAL:
                return resShred + stats.get(StatType.PHYS_RES_SHRED);
            default:
                return resShred;
        }
    }

    /**
     * Resolves enemy-facing resistance reduction from buffs active at impact.
     *
     * <p>Resistance reduction is not an attacker stat and therefore must not be
     * read from a stored attacker snapshot. Applying the captured impact-time
     * buff list to an empty container extracts only its live generic and
     * element-specific reduction values without mutating the snapshot.
     *
     * @param activeBuffs buffs captured for the current damage impact
     * @param currentTime simulation time of the impact
     * @param element     damage element being resolved
     * @return live generic plus matching element-specific resistance reduction
     */
    public static double getTotalResShred(
            List<Buff> activeBuffs, double currentTime, Element element) {
        StatsContainer liveReduction = new StatsContainer();
        if (activeBuffs != null) {
            for (Buff buff : activeBuffs) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(liveReduction, currentTime);
                }
            }
        }
        return getTotalResShred(liveReduction, element);
    }

    /**
     * Resolves the final resistance multiplier for the specified target and element.
     *
     * @param target  enemy being hit
     * @param stats   attacker stats after buffs
     * @param element damage element being resolved
     * @return outgoing damage multiplier after resistance and shred
     */
    public static double calculateMultiplier(Enemy target, StatsContainer stats, Element element) {
        double baseRes = target.getRes(element.getBonusStatType());
        return calculateResMulti(baseRes, getTotalResShred(stats, element));
    }

    /**
     * Resolves the target multiplier from enemy-facing buffs active at impact.
     *
     * @param target      enemy being hit
     * @param activeBuffs buffs captured for the current damage impact
     * @param currentTime simulation time of the impact
     * @param element     damage element being resolved
     * @return outgoing damage multiplier after live resistance reduction
     */
    public static double calculateMultiplier(
            Enemy target, List<Buff> activeBuffs, double currentTime, Element element) {
        double baseRes = target.getRes(element.getBonusStatType());
        return calculateResMulti(baseRes, getTotalResShred(activeBuffs, currentTime, element));
    }

    /**
     * Computes the resistance multiplier after shred has been applied.
     *
     * @param baseRes  enemy base resistance
     * @param resShred total resistance shred
     * @return outgoing damage multiplier
     */
    public static double calculateResMulti(double baseRes, double resShred) {
        double finalRes = baseRes - resShred;
        if (finalRes < 0) {
            return 1.0 - (finalRes / 2.0);
        } else if (finalRes < 0.75) {
            return 1.0 - finalRes;
        } else {
            return 1.0 / (1.0 + 4.0 * finalRes);
        }
    }
}
