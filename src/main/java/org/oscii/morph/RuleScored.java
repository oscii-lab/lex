package org.oscii.morph;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.math.VectorMath;
import org.oscii.neural.EmbeddingContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static java.util.stream.Collectors.toList;

/**
 * A rule scored by the embeddings of its support.
 */
public class RuleScored {
    /**
     * Parameters for scoring. Default values are from Soricut & Och '15.
     */
    public static class ScoringParams {
        public int maxSupportSize = 1000;
        public int maxRankRule = 100;
        public int maxRankTransformation = 30;
        public double minCosineTransformation = 0.5;
        public int minSizeDirection = 10;

        public ScoringParams(int maxSupportSize,
                             int maxRankRule,
                             int maxRankTransformation,
                             double minCosineTransformation,
                             int minSizeDirection) {
            this.maxSupportSize = maxSupportSize;
            this.maxRankRule = maxRankRule;
            this.maxRankTransformation = maxRankTransformation;
            this.minCosineTransformation = minCosineTransformation;
            this.minSizeDirection = minSizeDirection;
        }
    }


    private final static Logger log = LogManager.getLogger(RuleScored.class);

    final Rule rule;
    final List<RuleLexicalized> support;

    // Populated by scoring
    List<RuleLexicalized> embedded; // Pairs where both words are embedded
    List<RuleLexicalized> sample; // Pairs sampled from embedded for direction selection
    Map<RulePair, List<Transformation>> hits; // Indexed by word pair
    int comparisons;
    int numHits;
    double hitRate;

    // Populated by filtering
    List<RulePair> topDirections; // Index by direction
    List<Transformation> transformations;

    // Place to add two vectors together, which drastically reduces object creation
    // Note: only one thread can call scorePairAndDirection for this rule at a time.
    float[] added;

    public RuleScored(Rule rule, List<RuleLexicalized> support) {
        this.rule = rule;
        this.support = support;
    }

    /**
     * Find the hit rate, best directions, and all transformations for a rule.
     */
    public double score(EmbeddingContainer embeddings, ScoringParams params) {
        embedded = support.stream().filter(r -> embedded(r, embeddings)).collect(toList());
        if (embedded.size() > params.maxSupportSize) {
            sample = new ArrayList<>(embedded);
            Collections.shuffle(sample);
            sample = sample.subList(0, params.maxSupportSize);
        } else {
            sample = embedded;
        }
        int n = sample.size();
        if (n == 0) {
            hitRate = 0.0;
            return hitRate;
        }

        log.debug("Score {} pairs for {}", sample.size(), rule.toString());
        added = new float[embeddings.dimension()];
        hits = new HashMap<>();
        for (RuleLexicalized r : sample) {
            List<Transformation> hit = new ArrayList<>(sample.size());
            for (RuleLexicalized s : sample) {
                Transformation t = scorePairAndDirection(r.pair, s.pair, embeddings, params.maxRankRule);
                comparisons++;
                if (t.rank <= params.maxRankRule) {
                    numHits++;
                    hit.add(t);
                }
            }
            hit.sort((t, u) -> Integer.compare(t.rank, u.rank));
            if (!hit.isEmpty()) {
                hits.put(r.pair, hit);
            }
        }
        hitRate = (double) numHits / (double) comparisons;
        log.debug("  {} hits / {} comparisons = {} hit rate for {} rules and {} transformations",
                numHits, comparisons, hitRate, hits.size(),
                hits.values().stream().collect(summingInt(List::size)));
        filterTransformations(embeddings, params);
        return hitRate;
    }

    private void filterTransformations(EmbeddingContainer embeddings, ScoringParams params) {
        topDirections = new ArrayList<>();
        transformations = new ArrayList<>();
        HashMap<RulePair, List<Transformation>> remaining = new HashMap<>(hits);
        int previousSize = -1;
        while (topDirections.size() > previousSize) {
            previousSize = topDirections.size();
            // Find the direction that explains the most pairs
            remaining.values().stream()
                    .flatMap(Collection::stream)
                    .collect(groupingBy(t -> t.direction))
                    .entrySet().stream()
                    .max((s, t) -> Long.compare(s.getValue().size(), t.getValue().size()))
                    .ifPresent(mostCommon -> {
                        int size = mostCommon.getValue().size();
                        log.debug("  #{} direction has {} hits", topDirections.size() + 1, size);
                        if (size >= params.minSizeDirection) {
                            topDirections.add(mostCommon.getKey());
                            // Remove all pairs explained by this direction
                            mostCommon.getValue().forEach(t -> remaining.remove(t.rule));
                        }
                    });
        }

        // Find all valid transformations for all embedded rules
        log.debug("  Finding transformations for {} directions", topDirections.size());
        topDirections.forEach(d -> embedded.forEach(r -> {
            Transformation t = scorePairAndDirection(r.pair, d, embeddings, params.maxRankTransformation);
            if (t.rank <= params.maxRankTransformation && t.cosine >= params.minCosineTransformation) {
                transformations.add(t);
            }
        }));
        transformations.sort((t, u) -> Double.compare(t.rank - t.cosine, u.rank - u.cosine));
        log.debug("  {} transformations found", transformations.size());
    }

    public List<Transformation> getTransformations() {
        return transformations;
    }

    private boolean embedded(RuleLexicalized r, EmbeddingContainer embeddings) {
        for (String w : Arrays.asList(new String[]{r.pair.input, r.pair.output})) {
            if (!embeddings.contains(w)) {
                return false;
            }
        }
        return true;
    }

    private void add(float[] x, float[] y, float[] z) {
        for (int i = 0; i < z.length; i++) {
            z[i] = x[i] + y[i];
        }
    }

    // Score a pair of related words using a direction defined by another pair of words with the same relation.
    private Transformation scorePairAndDirection(RulePair r, RulePair d, EmbeddingContainer vs, int rankThreshold) {
        add(d.getDirection(vs), vs.getRawVector(r.input), added);
        double cosine = VectorMath.cosineSimilarity(added, vs.getRawVector(r.output));
        List<String> neighbors = vs.neighbors(added, rankThreshold);
        int index = neighbors.indexOf(r.output);
        int rank = (index == -1) ? rankThreshold + 1 : index + 1;
        return new Transformation(r, d, rank, cosine);
    }

    public String toString() {
        return String.format("%s [%d ; %f ; %s]", rule.toString(), support.size(), hitRate, support.get(0).pair.toString());
    }
}
