package org.oscii.morph;

import org.oscii.neural.EmbeddingContainer;

/**
 * A lexicalized rule scored by a direction vector.
 */
public class Transformation {
    final RulePair rule;
    final RulePair direction;
    final int rank;
    final double cosine;

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
