package org.oscii.lex;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by denero on 2/24/15.
 */ // A collection of information related to a meaning of an expression
public class Meaning {
    public final Expression expression;
    final List<Definition> definitions;
    public final List<Translation> translations;

    @Override
    public String toString() {
        return "Meaning{" +
                "expression=" + expression +
                ", definitions=" + definitions +
                ", translations=" + translations +
                '}';
    }

    public Meaning(Expression expression, List<Expression> translations) {
        this.expression = expression;
        this.definitions = null;
        this.translations = translations.stream()
                .map(e -> new Translation(e, 0))
                .collect(Collectors.toList());
    }

}
