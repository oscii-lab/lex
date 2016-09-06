package org.oscii.morph;

import org.oscii.lex.Lexicon;

import java.util.Collections;
import java.util.List;

/**
 * Indexes and applies rules from a substitutor.
 */
public class Stemmer {
    private final Substitutor subber;
    private final List<String> vocab;
    private final Lexicon lexicon;

    public Stemmer(Substitutor subber, List<String> embeddingVocab, Lexicon lexicon) {
        this.subber = subber;
        this.vocab = embeddingVocab;
        this.lexicon = lexicon;
    }

    /**
     * Return stems in the lexicon according to the subber.
     */
    public List<String> getKnownStems(String query) {
        return Collections.emptyList();
    }
}
