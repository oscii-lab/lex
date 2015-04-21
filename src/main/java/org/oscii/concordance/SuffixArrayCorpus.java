package org.oscii.concordance;

import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.UniformScorer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.train.DynamicTMBuilder;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;
import org.oscii.lex.Expression;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Corpus backed by a suffix array.
 */
public class SuffixArrayCorpus extends AlignedCorpus {
    // TODO(denero) Use frequency feature when it's set
    private static final String FREQUENCY_FEATURE = "";
    DynamicTranslationModel<String> translationModel;
    int translationFrequencyFeatureIndex;
    final Scorer<String> scorer = new UniformScorer<>();
    final InputProperties inputProperties = new InputProperties();



    @Override
    public void read(String path, String sourceLanguage, String targetLanguage, int max) throws IOException {
        ParallelFiles paths = paths(path, sourceLanguage, targetLanguage);
        translationModel = new DynamicTMBuilder(
                paths.sourceSentences.toString(),
                paths.targetSentences.toString(),
                paths.alignments.toString(),
                0, false).getModel();
        translationModel.setFeatureTemplate(DynamicTranslationModel.FeatureTemplate.DENSE);
        for (int i = 0; i < translationModel.getFeatureNames().size(); i++) {
            if (translationModel.getFeatureNames().get(i).equals(FREQUENCY_FEATURE)) {
                translationFrequencyFeatureIndex = i;
            }
        }
    }

    @Override
    public Function<Expression, Double> translationFrequencies(Expression source) {
        Sequence<IString> sequence = IStrings.tokenize(source.text);
        List<ConcreteRule<IString, String>> rules = translationModel
                .getRules(sequence, inputProperties, null, 0, scorer);
        Map<String, Double> frequencies = new HashMap<>();
        rules.stream().forEach(r -> frequencies.put(
                join(r.abstractRule.target),
                getTranslationFrequency(r.abstractRule)));
        return exp -> frequencies.getOrDefault(exp.text, 0.0);
    }

    private double getTranslationFrequency(Rule<IString> rule) {
        for (int i = 0; i < rule.scores.length; i++) {
            if (i.)
        }

    }


    private static String join(Sequence<IString> phrase) {

    }

    @Override
    public List<AlignedSentence> examples(String query, String source, String target, int max) {
        return null;
    }
}
