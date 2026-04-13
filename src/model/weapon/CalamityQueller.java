package model.weapon;

import model.entity.Weapon;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Calamity Queller (5-star Polearm).
 *
 * <p>
 * <b>Base stats (Lv 90):</b> 741 Base ATK, 16.5% ATK (substat).
 *
 * <p>
 * <b>Extinguishing Precept (R1):</b>
 * <ul>
 * <li>Gain 12% All Elemental DMG Bonus (constant, via
 * {@link #applyPassive}).</li>
 * <li>After using an Elemental Skill, gain Consummation for 20 s:
 * +1 stack/s on-field or +2 stacks/s off-field, capped at 6 stacks.
 * Each stack grants +3.2% ATK.</li>
 * </ul>
 *
 * <p>
 * Consummation is modelled as a {@link mechanics.buff.Buff} registered on the
 * owner character so that it appears in Active Buffs in the HTML report.
 * A {@link simulation.event.SimpleTimerEvent} ticks every 1 s to increment
 * {@link #accumulatedStacks} based on the current active character.
 * A {@link #consummationId} generation counter ensures stale timer events
 * self-terminate on re-cast.
 */
public class CalamityQueller extends Weapon {

    private int accumulatedStacks = 0;
    /** Incremented on each skill cast; captured by the timer to detect re-casts. */
    private int consummationId = 0;

    private Character ownerRef = null;

    private static final double DURATION = 20.0;
    private static final int MAX_STACKS = 6;
    private static final double ATK_PER_STACK = 0.032; // 3.2% per stack
    private static final int ON_FIELD_GAIN = 1; // stacks per tick on-field
    private static final int OFF_FIELD_GAIN = 2; // stacks per tick off-field

    public CalamityQueller() {
        super("CalamityQueller", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 741);
        s.add(StatType.ATK_PERCENT, 0.165);
        this.weaponType = WeaponType.POLEARM;
    }

    /**
     * On Elemental Skill use:
     * <ol>
     * <li>Resets {@link #accumulatedStacks} to 0 and increments the generation
     * counter.</li>
     * <li>Registers a "Consummation" {@link mechanics.buff.Buff} on the owner
     * character
     * (replacing any existing one) so it appears in the Active Buffs report.</li>
     * <li>Registers a 1 s {@link simulation.event.SimpleTimerEvent} that increments
     * stacks by {@link #ON_FIELD_GAIN} or {@link #OFF_FIELD_GAIN} each tick.</li>
     * </ol>
     */
    @Override
    public void onAction(Character user, CharacterActionRequest request, simulation.CombatSimulator sim) {
        if (ownerRef == null) {
            ownerRef = user;
        }

        if (request.getKey() == CharacterActionKey.SKILL) {
            double t = sim.getCurrentTime();
            accumulatedStacks = 0;
            consummationId++;

            final int myId = consummationId;
            final double endTime = t + DURATION;

            // Register as a visible Buff on the character (shows in Active Buffs report).
            // The Buff's applyStats reads accumulatedStacks from this weapon instance.
            user.removeBuff("Calamity Queller"); // Remove existing buff if present to reset duration
            user.addBuff(new mechanics.buff.Buff("Calamity Queller", DURATION, t) {
                @Override
                protected void applyStats(StatsContainer stats, double currentTime) {
                    stats.add(StatType.ATK_PERCENT, accumulatedStacks * ATK_PER_STACK);
                }
            });

            // Timer: fires every 1 s to add stacks based on current field state
            sim.registerEvent(new simulation.event.SimpleTimerEvent(t + 1.0, 1.0) {
                @Override
                public void onTick(simulation.CombatSimulator s) {
                    if (myId != consummationId
                            || s.getCurrentTime() > endTime
                            || accumulatedStacks >= MAX_STACKS) {
                        finish();
                        return;
                    }
                    boolean onField = (ownerRef != null && s.getActiveCharacter() == ownerRef);
                    int gain = onField ? ON_FIELD_GAIN : OFF_FIELD_GAIN;
                    accumulatedStacks = Math.min(MAX_STACKS, accumulatedStacks + gain);
                }
            });
        }
    }

    /**
     * Applies the constant +12% All Elemental DMG Bonus.
     * The Consummation ATK% bonus is applied through the {@code "Consummation"}
     * {@link mechanics.buff.Buff} registered on the owner character, not here,
     * to avoid double-counting and to ensure visibility in the Active Buffs report.
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.DMG_BONUS_ALL, 0.12);
    }
}
