package org.oscii;

import org.oscii.lex.Expression;
import org.oscii.lex.Meaning;
import org.oscii.lex.Translation;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A map from expressions to meanings.
 */
public class Lexicon {
    Map<Expression, List<Meaning>> lexicon = new HashMap<>();
    List<Meaning> empty = Collections.emptyList();

    // Logging
    private final static Logger logger = Logger.getLogger(Lexicon.class.getName());

    static {
        logger.setLevel(Level.INFO);
    }

    public void add(Meaning meaning) {
        if (!lexicon.containsKey(meaning.expression)) {
            lexicon.put(meaning.expression, new ArrayList<>());
        }
        lexicon.get(meaning.expression).add(meaning);
        logger.info(meaning.toString());
    }

    public List<Meaning> lookup(String query, String language) {
        // TODO(denero) Query using degraded text
        logger.info(String.format("Looking up %s %s", query, language));
        return lexicon.getOrDefault(new Expression(query, language), empty);
    }

    public List<Translation> translate(String query, String source, String target) {
        logger.info(String.format("Translating %s from %s to %s", query, source, target));
        return lookup(query, source).stream()
                // Aggregate and filter by target language
                .flatMap(m -> m.translations.stream()
                        .filter(t -> t.translation.language.equals(target)))
                        // Remove textual duplicates, choosing the first of each group
                .collect(Collectors.groupingBy((Translation t) -> t.translation.text))
                .values().stream().map(ts -> ts.iterator().next())
                .collect(Collectors.toList());
    }

    // A mock lexicon that always translates "adult" to "adulto".
    public static class Mock extends Lexicon {
        @Override
        public List<Meaning> lookup(String query, String language) {
            Expression adult = new Expression("adulto", "en");
            Expression adulto = new Expression("adulto", "es");
            return Arrays.asList(new Meaning(adult, Arrays.asList(adulto)));
        }
    }
}
