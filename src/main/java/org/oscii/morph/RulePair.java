package org.oscii.morph;

import com.google.gson.annotations.Expose;
import org.oscii.neural.EmbeddingContainer;

/**
 * Two words for which the input can be transformed into the output.
 */
public class RulePair {
    @Expose
    final String input;
    @Expose
    final String output;
    float[] direction;

    public RulePair(String input, String output) {
        assert input != null && output != null;
        this.input = input;
        this.output = output;
    }

    /**
     * Get and cache the direction of a word pair.
     */
    public float[] getDirection(EmbeddingContainer embeddings) {
        if (direction == null) {
            float[] in = embeddings.getRawVector(input);
            float[] out = embeddings.getRawVector(output);
            if (in != null && out != null) {
                direction =subtract(out, in);
            }
        }
        return direction;
    }

    private static float[] subtract(float[] x, float[] y) {
        float[] z = new float[x.length];
        for (int i = 0; i < z.length; i++) {
            z[i] = x[i] - y[i];
        }
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RulePair wordPair = (RulePair) o;

        if (!input.equals(wordPair.input)) return false;
        return output.equals(wordPair.output);

    }

    @Override
    public int hashCode() {
        int result = input.hashCode();
        result = 31 * result + output.hashCode();
        return result;
    }

    public String toString() {
        return String.format("%s->%s", input, output);
    }
}
