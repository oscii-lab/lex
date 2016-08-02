package org.oscii.morph;

import org.oscii.neural.EmbeddingContainer;

/**
 * A lexicalized rule scored by a direction vector.
 */
public class Transformation {
    final RulePair rule;
    final RulePair direction;
    final int rank; // Rank of output in the k-nearest neighbors of input + direction
    // Note: The rank is truncated: it stores min(actual rank, rank threshold + 1)
    final double cosine; // Cosine similarity of output to input + direction

    public Transformation(RulePair rule, RulePair direction, int rank, double cosine) {
        this.rule = rule;
        this.direction = direction;
        this.rank = rank;
        this.cosine = cosine;
    }

    @Override
    public String toString() {
        return "Transformation{" +
                "rule=" + rule +
                ", direction=" + direction +
                ", rank=" + rank +
                ", cosine=" + cosine +
                '}';
    }
}
