package org.oscii.morph;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.corpus.Corpus;
import org.oscii.neural.Word2VecManager;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.*;

/**
 * A collection of substitution rules.
 */
public class Substitutor {
    private final Word2VecManager embeddings;
    private Map<Rule, List<RuleLexicalized>> substitutions;
    private List<RuleScored> scored;
    private final static Logger log = LogManager.getLogger(Substitutor.class);

    public Substitutor(Word2VecManager embeddings) {
        this.embeddings = embeddings;
    }

    /**
     * Extract all substitutions from the vocabulary of the corpus.
     *
     * @param corpus a monolingual corpus.
     * @param vocab allowed vocabulary
     */
    public void extractAll(Corpus corpus, Set<String> vocab) {
        log.info("Extracting substitution rules");
        if (vocab == null) {
            vocab = corpus.vocab();
        }
        Stream<RuleLexicalized> p = IndexByStem(vocab, Substitutor::getPrefix).values()
                .parallelStream().flatMap(x -> transformations(x, Rule.Prefix::new));
        Stream<RuleLexicalized> s = IndexByStem(vocab, Substitutor::getSuffix).values()
                .parallelStream().flatMap(x -> transformations(x, Rule.Suffix::new));
        substitutions = Stream.concat(p, s).collect(groupingBy(t -> t.sub));
    }

    /**
     * Limit the splits for each word pair to the K most frequent substitutions and keeps only
     * substitutions supported by a minimum number of word pairs.
     * <p>
     * A word pair is split by a substitution rule. Multiple rules can split the
     * same word pair. E.g., "wind" and "winded" can be split by
     * <p>
     * s/ε/ed, s/d/ded, s/nd/nded, s/ind/inded, and p/win/winde
     * <p>
     * Pruning to the most frequent substitution would keep e.g. only s/ε/ed.
     * <p>
     * This pruning step is not part of Soricut & Och 2015.
     *
     * @param maxSplitsPerPair k most frequent splits to keep
     */
    public void prune(int maxSplitsPerPair, int minPairCount) {
        Map<Rule, List<RuleLexicalized>> a, b, c;

        log.info("Pruning substitution rules: minPairCount of {}", minPairCount);
        a = enforceMinPairCount(substitutions, minPairCount);

        log.info("Pruning substitution rules: maxSplitsPerPair of {}", maxSplitsPerPair);
        b = a.values()
                .parallelStream().flatMap(ts -> ts.stream())
                .collect(groupingBy(t -> t.pair))
                .values().parallelStream()
                .flatMap(ts -> mostFrequent(ts, maxSplitsPerPair))
                .collect(groupingBy(t -> t.sub));

        log.info("Pruning substitution rules: minPairCount of {} (again)", minPairCount);
        c = enforceMinPairCount(b, minPairCount);

        substitutions = c;
    }

    public void scoreRules() {
        log.info("Scoring substitutions");

    }

    private Stream<RuleLexicalized> mostFrequent(List<RuleLexicalized> ts, int k) {
        if (ts.size() <= k) return ts.stream();
        return ts.stream().sorted(comparingInt(t -> substitutions.get(t.sub).size())).limit(k);
    }

    // All transformations for a list of segmentations with a common stem.
    private Stream<RuleLexicalized> transformations(List<Segmentation> segs,
                                                    BiFunction<String, String, ? extends Rule> newSub) {
        final int n = segs.size();
        if (n < 2) return Stream.empty(); // Optimization
        List<RuleLexicalized> ts = new ArrayList<>(n * (n - 1));
        for (Segmentation input : segs) {
            for (Segmentation output : segs) {
                if (input == output) continue;
                Rule sub = newSub.apply(input.affix, output.affix).intern();
                ts.add(new RuleLexicalized(input.word, output.word, sub));
            }
        }
        return ts.stream();
    }

    // Index all segmented words by the remaining stem.
    Map<String, List<Segmentation>> IndexByStem(Collection<String> vocab, BiFunction<String, Integer, Segmentation> f) {
        return vocab.parallelStream().flatMap(w -> {
            int n = w.length();
            int affixMax = Math.min(6, n - 1);
            List<Segmentation> entries = new ArrayList<>(affixMax);
            for (int k = 0; k <= affixMax; k++) {
                entries.add(f.apply(w, k));
            }
            return entries.stream();
        }).collect(groupingBy(p -> p.stem));
    }

    private static Segmentation getPrefix(String word, int k) {
        String prefix = word.substring(0, k);
        String stem = word.substring(k, word.length());
        return new Segmentation(stem, prefix, word);
    }

    private static Segmentation getSuffix(String word, int k) {
        final int n = word.length();
        String suffix = word.substring(n - k, n);
        String stem = word.substring(0, n - k);
        return new Segmentation(stem, suffix, word);
    }

    private Map<Rule, List<RuleLexicalized>> enforceMinPairCount(Map<Rule, List<RuleLexicalized>> m,
                                                                 int minPairCount) {
        return m.values().parallelStream().filter(ts -> ts.size() >= minPairCount)
                .flatMap(ts -> ts.stream()).collect(groupingBy(t -> t.sub));
    }

    /* Helper classes */

    // Splitting a word into stem and affix
    private static class Segmentation {
        private final String word;
        private final String stem;
        private final String affix;

        private Segmentation(String stem, String affix, String word) {
            assert stem != null && affix != null && word != null;
            this.stem = stem;
            this.affix = affix;
            this.word = word;
        }
    }


}
