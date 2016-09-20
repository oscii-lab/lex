package org.oscii.morph;

import org.oscii.lex.Lexicon;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * Indexes and applies rules from a substitutor.
 */
public class Stemmer {
    private final List<String> vocab;
    private final Lexicon lexicon;
    private final String language;
    private final List<RuleScored> rules;
    private Map<String, List<Transformation>> lexicalizedIndex;

    public Stemmer(List<RuleScored> rulesScored, List<String> embeddingVocab, Lexicon lexicon, String language) {
        this.rules = rulesScored;
        this.vocab = embeddingVocab;
        this.lexicon = lexicon;
        this.language = language;
        lexicalizedIndex = rulesScored.stream()
                .flatMap(this::getTransformations)
                .collect(groupingBy(t -> Lexicon.degrade(t.rule.input)));
    }

    Stream<Transformation> getTransformations(RuleScored r) {
        if (r == null) {
            return Stream.empty();
        } else if (r.getTransformations() == null) {
            return Stream.empty();
        } else {
            return r.getTransformations().stream();
        }
    }

    /**
     * Return stems in the lexicon according to the subber.
     */
    public List<String> getKnownStems(String query) {
        String degraded = Lexicon.degrade(query);
        List<Transformation> lexicalized = lexicalizedIndex.get(degraded);
        if (lexicalized != null) {
            lexicalized.sort((t, u) -> Double.compare(u.cosine, t.cosine));
            return lexicalized.stream()
                    .map(t -> t.rule.output)
                    .distinct()
                    .filter(w -> !lexicon.lookup(w, language).isEmpty())
                    .collect(toList());
        }
        if (!vocab.contains(degraded)) {
            return rules.stream()
                    .filter(r -> r.rule.applies(query))
                    .sorted((r, s) -> Double.compare(s.hitRate, r.hitRate))
                    .map(r -> r.rule.apply(query))
                    .distinct()
                    .filter(w -> !lexicon.lookup(w, language).isEmpty())
                    .collect(toList());
        }
        return Collections.emptyList();
    }

}
