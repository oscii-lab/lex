package org.oscii.lex;

import java.util.List;

/**
 * A translation of an expression
 */
public class Translation {
    public final Expression translation;
    public final List<String> pos;
    public double frequency;
    String source;

    public Translation(Expression expression, List<String> pos) {
        this(expression, pos, "");
    }

    public Translation(Expression expression, List<String> pos, String source) {
        this.translation = expression;
        this.pos = pos;
        this.frequency = 0.0;
        this.source = source;
    }

    @Override
    public String toString() {
        return "Translation{" +
                "translation=" + translation +
                ", frequency=" + frequency +
                '}';
    }
}
