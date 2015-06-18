package org.oscii.lex;

import java.util.List;

/**
 * A translation of an expression
 */
public class Translation {
    public final Expression translation;
    public final List<String> pos;
    public double frequency;

    public Translation(Expression expression, List<String> pos) {
        this.translation = expression;
        this.pos = pos;
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
