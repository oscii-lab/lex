package org.oscii.lex;

/**
 * A translation of an expression
 */
public class Translation {
    public Expression translation;
    public double frequency;

    public Translation(Expression expression, double frequency) {
        this.translation = expression;
        this.frequency = frequency;
    }

    @Override
    public String toString() {
        return "Translation{" +
                "expression=" + translation +
                '}';
    }
}
