package org.oscii.concordance;

import edu.stanford.nlp.mt.tm.TranslationModel;
import edu.stanford.nlp.mt.util.IString;
import org.oscii.lex.Expression;

import java.util.List;
import java.util.function.Function;

/**
 * Corpus backed by a suffix array.
 */
public class SuffixArrayCorpus implements AlignedCorpus {
    TranslationModel<IString, Double> translationModel;

    @Override
    public Function<Expression, Double> translationFrequencies(Expression source) {
        return null;
    }

    @Override
    public List<AlignedSentence> examples(String query, String source, String target, int max) {
        return null;
    }
}
