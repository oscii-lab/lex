package org.oscii.concordance;

import org.oscii.lex.Expression;

import java.util.List;
import java.util.function.Function;

/**
 * Interface to access corpus statistics and examples
 */
public interface AlignedCorpus {
    /*
     * Return a function that takes phrases in another language and returns translation frequencies.
     */
    Function<Expression, Double> translationFrequencies(Expression source);

    /*
     * Return examples for a phrase translated into a target language.
     */
    List<AlignedSentence> examples(String query, String source, String target, int max);
}
