package model.entity;

/**
 * Capability for characters that expose a meaningful burst-active window.
 */
public interface BurstStateProvider {
    boolean isBurstActive(double currentTime);
}
