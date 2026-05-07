package model.entity;

/**
 * Capability for characters that have a meaningful time-limited active form.
 *
 * <p>A "form" is any timed state during which the character should remain
 * on-field to deliver value — whether triggered by burst (e.g. Raiden Shogun's
 * Musou Isshin) or by skill (e.g. Flins' Manifest Flame).
 */
public interface FormStateProvider {
    boolean isFormActive(double currentTime);
}
