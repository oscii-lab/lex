package org.oscii.morph;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.corpus.Corpus;
import org.oscii.neural.EmbeddingContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * A collection of substitution rules.
 */
public class Substitutor {
    private final EmbeddingContainer embeddings;
    private Map<String, List<RuleLexicalized>> substitutions;
    private List<RuleScored> scored;
    private final static Logger log = LogManager.getLogger(Substitutor.class);
    private int numExtracted = 0;
    private int numScored = 0;

    public Substitutor(EmbeddingContainer embeddings) {
        this.embeddings = embeddings;
    }

    /**
     * Extract all substitutions from the vocabulary of the corpus.
     *  @param corpus a monolingual corpus.
     * @param vocab  allowed vocabulary
     * @param minStemLength
     */
    public void extractAll(Corpus corpus, Set<String> vocab, int minStemLength) {
        if (vocab == null) {
            vocab = corpus.vocab();
        }
        log.info("Extracting rules");
        Stream<RuleLexicalized> p = IndexByStem(vocab, minStemLength, Substitutor::getPrefix).values()
                .parallelStream().flatMap(x -> getRulesLexicalized(x, Rule.Prefix::new));
        Stream<RuleLexicalized> s = IndexByStem(vocab, minStemLength, Substitutor::getSuffix).values()
                .parallelStream().flatMap(x -> getRulesLexicalized(x, Rule.Suffix::new));
        substitutions = Stream.concat(p, s).parallel().collect(groupingBy(t -> t.sub.toString()));
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
        Map<String, List<RuleLexicalized>> a, b, c;

        log.info("Pruning substitution rules: minPairCount of {}", minPairCount);
        a = enforceMinPairCount(substitutions, minPairCount);

        log.info("Pruning substitution rules: maxSplitsPerPair of {}", maxSplitsPerPair);
        b = a.values()
                .parallelStream().flatMap(ts -> ts.stream())
                .collect(groupingBy(t -> t.pair))
                .values().parallelStream()
                .flatMap(ts -> mostFrequent(ts, maxSplitsPerPair))
                .collect(groupingBy(t -> t.sub.toString()));

        log.info("Pruning substitution rules: minPairCount of {} (again)", minPairCount);
        c = enforceMinPairCount(b, minPairCount);

        substitutions = c;
    }

    public void scoreRules(RuleScored.ScoringParams params) {
        log.info("Scoring substitutions for {} rules", substitutions.size());
        scored = substitutions.values().parallelStream()
                .filter(rs -> !rs.isEmpty())
                .map(rs -> scoreRule(rs.get(0).sub, rs, params))
                .sorted((r, s) -> Double.compare(s.hitRate, r.hitRate))
                .collect(toList());
    }

    private RuleScored scoreRule(Rule rule, List<RuleLexicalized> support, RuleScored.ScoringParams params) {
        RuleScored rs = new RuleScored(rule, support);
        long start = System.nanoTime();
        rs.score(embeddings, params);
        double elapsed = (System.nanoTime() - start) / 1e9;
        log.info("Scored rule #{}, {}, using {} pairs in {} seconds",
                ++numScored, rs.rule, rs.sample.size(), String.format("%.1f", elapsed));
        return rs;
    }

    private Stream<RuleLexicalized> mostFrequent(List<RuleLexicalized> ts, int k) {
        if (ts.size() <= k) return ts.stream();
        return ts.stream().sorted(comparingInt(t -> substitutions.get(t.sub.toString()).size())).limit(k);
    }

    // All transformations (stored in RuleLexicalized objects)
    // for a list of segmentations with a common stem.
    private Stream<RuleLexicalized> getRulesLexicalized(List<Segmentation> segs,
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
    Map<String, List<Segmentation>> IndexByStem(Collection<String> vocab,
                                                int minStemLength,
                                                BiFunction<String, Integer, Segmentation> segment) {
        return vocab.parallelStream().filter(w -> w.length() > minStemLength).flatMap(w -> {
            int n = w.length();
            int affixMax = Math.min(6, n - minStemLength);
            List<Segmentation> entries = new ArrayList<>(affixMax);
            for (int k = 0; k <= affixMax; k++) {
                entries.add(segment.apply(w, k));
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

    private Map<String, List<RuleLexicalized>> enforceMinPairCount(Map<String, List<RuleLexicalized>> m,
                                                                   int minPairCount) {
        return m.values().parallelStream().filter(ts -> ts.size() >= minPairCount)
                .flatMap(ts -> ts.stream()).collect(groupingBy(t -> t.sub.toString()));
    }

    public List<RuleScored> getScored() {
        return scored;
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
