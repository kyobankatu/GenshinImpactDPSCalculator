package mechanics.rl;

import java.util.Arrays;

/**
 * Mutable recurrent hidden state.
 */
public class RecurrentState {
    private final double[] hidden;

    /**
     * Creates a zero-initialized recurrent state.
     */
    public RecurrentState(int hiddenSize) {
        this.hidden = new double[hiddenSize];
    }

    /**
     * Creates a recurrent state from the provided hidden values.
     */
    public RecurrentState(double[] hidden) {
        this.hidden = hidden.clone();
    }

    /**
     * Returns the mutable hidden-state buffer.
     */
    public double[] values() {
        return hidden;
    }

    /**
     * Returns a defensive copy of this recurrent state.
     */
    public RecurrentState copy() {
        return new RecurrentState(hidden);
    }

    /**
     * Resets all hidden-state values to zero.
     */
    public void reset() {
        Arrays.fill(hidden, 0.0);
    }
}
