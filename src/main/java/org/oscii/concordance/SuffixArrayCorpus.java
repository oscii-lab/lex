package org.oscii.concordance;

import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.SampledRule;
import edu.stanford.nlp.mt.util.ParallelCorpus;
import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import edu.stanford.nlp.mt.util.Vocabulary;
import gnu.trove.map.hash.THashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.lex.Expression;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.*;

/**
 * Corpus backed by a suffix array.
 */
public class SuffixArrayCorpus extends AlignedCorpus {
    // source language -> target language -> suffix array
    Map<String, Map<String, ParallelSuffixArray>> suffixes = new HashMap<>();
    private int maxSamples = 100;
    private int maxTargetPhrase = 5;

    private final static Logger log = LogManager.getLogger(SuffixArrayCorpus.class);

    @Override
    public void read(String path, String sourceLanguage, String targetLanguage, int max) throws IOException {
        log.info("Building suffix array");
        ParallelCorpus corpus = readCorpus(path, sourceLanguage, targetLanguage, max);
        ParallelSuffixArray suffixArray = new ParallelSuffixArray(corpus);
        corpus = null;  // Drop reference to corpus for possible garbage collection
        suffixArray.build();
        registerSuffixArray(suffixArray, sourceLanguage, targetLanguage);
        registerSuffixArray(suffixArray.swapLanguages(), targetLanguage, sourceLanguage);
    }

    /*
     * Build a parallel corpus
     */
    private ParallelCorpus readCorpus(String path, String sourceLanguage, String targetLanguage, int max) throws IOException {
        // TODO(denero) Limit the corpus size
        log.info("Reading sentences: " + sourceLanguage + "-" + targetLanguage);
        ParallelFiles paths = paths(path, sourceLanguage, targetLanguage);
        final int expectedSize = 100000;
        ParallelCorpus corpus = ParallelCorpus.loadCorpusFromFiles(paths.sourceSentences.toString(),
                paths.targetSentences.toString(), paths.alignments.toString(), expectedSize);
        return corpus;
    }

    /*
     * Register a new suffixArray.
     */
    private void registerSuffixArray(ParallelSuffixArray suffixArray,
                                     String sourceLanguage,
                                     String targetLanguage) {
        Map<String, ParallelSuffixArray> bySource = suffixes.get(sourceLanguage);
        if (bySource == null) {
            bySource = new THashMap<>();
            suffixes.put(sourceLanguage, bySource);
        }
        if (bySource.containsKey(targetLanguage)) {
            throw new RuntimeException("Multiple corpora for a language pair: "
                    + sourceLanguage + ", " + targetLanguage);
        }
        bySource.put(targetLanguage, suffixArray);
    }

    @Override
    public Function<Expression, Double> translationFrequencies(Expression source) {
        Map<String, Map<String, Long>> counts = new THashMap<>();
        Map<String, ParallelSuffixArray> arrays = suffixes.get(source.language);
        if (arrays == null) return AlignedCorpus::zeroFrequency;
        arrays.entrySet().forEach(kv -> counts.put(kv.getKey(), countAll(source.text, kv.getValue())));
        return normalizeByLanguage(counts);
    }

    /*
     * Count translations of text (sampled).
     */
    private Map<String, Long> countAll(String text, ParallelSuffixArray suffixArray) {
        final Vocabulary vocab = suffixArray.getVocabulary();
        int[] phrase = toPhrase(text, vocab);
        if (phrase == null) return emptyMap();
        List<ParallelSuffixArray.SentencePair> samples = suffixArray.sample(phrase, maxSamples).samples;

        Stream<SampledRule> rules = samples.stream().flatMap(
                s -> DynamicTranslationModel.extractRules(s, phrase.length, maxTargetPhrase).stream());
        return rules.collect(groupingBy(rule -> toText(rule.tgt, vocab), counting()));
    }

    /*
     * Return an array of word indices or null if text contains an unknown word.
     */
    private static int[] toPhrase(String text, Vocabulary vocab) {
        // TODO(denero) Tokenizer should be applied to text
        String[] words = text.trim().split("\\s+");
        int[] phrase = new int[words.length];
        for (int i = 0; i < phrase.length; i++) {
            phrase[i] = vocab.indexOf(words[i]);
            if (phrase[i] == -1) {
                return null;
            }
        }
        return phrase;
    }

    /*
     * Return a string containing the words of a phrase.
     */
    private static String toText(int[] phrase, Vocabulary vocab) {
        // TODO(denero) Detokenizer should be applied to text
        return String.join(" ", toWords(phrase, vocab));
    }

    /*
     * Return an array of words for a phrase.
     */
    private static String[] toWords(int[] phrase, Vocabulary vocab) {
        String[] words = new String[phrase.length];
        for (int i = 0; i < phrase.length; i++) {
            words[i] = vocab.get(phrase[i]);
        }
        return words;
    }

    @Override
    public List<AlignedSentence> examples(String text, String source, String target, int max) {
        final Map<String, ParallelSuffixArray> arrays = suffixes.get(source);
        if (arrays == null) return emptyList();
        final ParallelSuffixArray suffixArray = arrays.get(target);
        if (suffixArray == null) return emptyList();
        final Vocabulary vocab = suffixArray.getVocabulary();
        final int[] phrase = toPhrase(text, vocab);
        if (phrase == null) return emptyList();
        List<ParallelSuffixArray.SentencePair> samples = suffixArray.sample(phrase, maxSamples).samples;
        return samples.stream().map(s -> convertSentence(s, vocab, source, target)).collect(toList());
    }

    /*
     * Convert between sentence pair representations.
     */
    private static AlignedSentence convertSentence(ParallelSuffixArray.SentencePair pair,
                                                   final Vocabulary vocab,
                                                   String sourceLanguage,
                                                   String targetLanguage) {
        final String[] source = new String[pair.sourceLength()];
        IntStream.range(0, pair.sourceLength()).forEach(i -> source[i] = vocab.get(pair.source(i)));
        final String[] target = new String[pair.targetLength()];
        IntStream.range(0, pair.targetLength()).forEach(i -> target[i] = vocab.get(pair.target(i)));
        List<String> links = new ArrayList<>();
        for (int i = 0; i < pair.sourceLength(); i++) {
            for (int j : pair.f2e(i)) {
                links.add(String.format("%d-%d", i, j));
            }
        }
        String[] alignment = new String[links.size()];
        links.toArray(alignment);
        return AlignedSentence.parse(source, target, alignment, sourceLanguage, targetLanguage).get(0);
    }
}
