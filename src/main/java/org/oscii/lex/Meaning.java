package org.oscii.lex;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A collection of information related to a meaning of an expression
 */
public class Meaning {
    public final Expression expression;
    public final List<String> pos = new ArrayList<>();
    public final List<Definition> definitions = new ArrayList<>();
    public final List<Translation> translations = new ArrayList<>();
    public final List<Expression> synonyms = new ArrayList<>();

    @Override
    public String toString() {
        return "Meaning{" +
                "expression=" + expression +
                ", definitions=" + definitions +
                ", translations=" + translations +
                '}';
    }

    public Meaning(Expression expression) {
        this.expression = expression;
    }
}
