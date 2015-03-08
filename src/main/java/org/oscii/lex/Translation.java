package org.oscii.lex;

/**
 * A translation of an expression
 */
public class Translation {
    public Expression translation;
    public double frequency;

    public Translation(Expression expression) {
        this.translation = expression;
        this.frequency = 0.0;
    }

    @Override
    public String toString() {
        return "Translation{" +
                "translation=" + translation +
                ", frequency=" + frequency +
                '}';
    }
}
