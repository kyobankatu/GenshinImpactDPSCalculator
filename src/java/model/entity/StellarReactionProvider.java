package model.entity;

/**
 * Party capability that converts eligible elemental reactions into Stellar reactions.
 */
public interface StellarReactionProvider {
    /** Returns whether this party member enables Stellar-Conduct conversion. */
    boolean enablesStellarConduct();

    /** Returns whether this party member enables Stellar-Swirl conversion. */
    boolean enablesStellarSwirl();
}
