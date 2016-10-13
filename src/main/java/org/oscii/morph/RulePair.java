package org.oscii.morph;

import com.google.gson.annotations.Expose;
import no.uib.cipr.matrix.Vector;
import org.oscii.neural.EmbeddingContainer;

/**
 * Two words for which the input can be transformed into the output.
 */
public class RulePair {
    @Expose
    final String input;
    @Expose
    final String output;
    Vector direction;

    public RulePair(String input, String output) {
        assert input != null && output != null;
        this.input = input;
        this.output = output;
    }

    /**
     * Get and cache the direction of a word pair.
     */
    public Vector getDirection(EmbeddingContainer embeddings) {
        if (direction == null) {
            Vector in = embeddings.getRawVector(input);
            Vector out = embeddings.getRawVector(output);
            if (in != null && out != null) {
                direction = out.copy().add(-1, in);
            }
        }
        return direction;
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
