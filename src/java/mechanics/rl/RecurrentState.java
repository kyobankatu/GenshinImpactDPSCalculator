package mechanics.rl;

import java.util.Arrays;

/**
 * Mutable recurrent hidden state.
 */
public class RecurrentState {
    private final double[] hidden;

    public RecurrentState(int hiddenSize) {
        this.hidden = new double[hiddenSize];
    }

    public RecurrentState(double[] hidden) {
        this.hidden = hidden.clone();
    }

    public double[] values() {
        return hidden;
    }

    public RecurrentState copy() {
        return new RecurrentState(hidden);
    }

    public void reset() {
        Arrays.fill(hidden, 0.0);
    }
}
