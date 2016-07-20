package org.oscii.morph;

import org.oscii.math.VectorMath;
import org.oscii.neural.EmbeddingContainer;

import java.util.*;
import java.util.function.Function;

import static java.lang.Math.toIntExact;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * A rule scored by the embeddings of its support.
 */
public class RuleScored {


    /**
     * Parameters for scoring.
     */
    public static class ScoringParams {
        public final int maxSupportSize;
        public final int maxRankRule;
        public final int maxRankTransformation;
        public final double minCosineTransformation;
        public final int minSizeDirection;

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


    final Rule sub;
    final List<RuleLexicalized> support;

    // Populated by scoring
    List<RuleLexicalized> embedded;
    List<RuleLexicalized> sample;
    Map<RulePair, Queue<Transformation>> candidates;
    int hits = 0;
    int comparisons = 0;
    double hitRate;

    // Populated by filtering
    List<RulePair> topDirections;
    List<Transformation> transformations;

    public RuleScored(Rule sub, List<RuleLexicalized> support) {
        this.sub = sub;
        this.support = support;
    }

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

        candidates = new HashMap<>();
        for (RuleLexicalized r : sample) {
            List<Transformation> trans = new ArrayList<>(sample.size());
            for (RuleLexicalized s : sample) {
                Transformation t = rank(r.pair, s.pair, embeddings, params.maxRankRule);
                comparisons++;
                if (t.rank <= params.maxRankRule) {
                    hits++;
                    trans.add(t);
                }
            }
            trans.sort((t, u) -> Integer.compare(t.rank, u.rank));
            candidates.put(r.pair, new LinkedList<>(trans));
        }
        hitRate = (double) hits / (double) comparisons;
        filterTransformations(params);
        return hitRate;
    }

    private void filterTransformations(ScoringParams params) {
        topDirections = new ArrayList<>();
        transformations = new ArrayList<>();
        while(true) {
            Map.Entry<RulePair, List<Transformation>> mostCommon = candidates.values().parallelStream()
                    .map(ts -> ts.peek())
                    .collect(groupingBy(t -> t.direction))
                    .entrySet().parallelStream()
                    .max((s, t) -> Long.compare(s.getValue().size(), t.getValue().size())).get();
            if (mostCommon.getValue().size() >= params.minSizeDirection) {
                topDirections.add(mostCommon.getKey());
                mostCommon.getValue().forEach(t -> {
                    candidates.get(t.rule).remove();
                    if (t.rank <= params.maxRankTransformation && t.cosine >= params.minCosineTransformation) {
                        transformations.add(t);
                    }
                });
            } else {
                break;
            }
        }
        transformations.sort((t, u) -> Double.compare(t.rank - t.cosine, u.rank - u.cosine));
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

    static float[] add(float[] x, float[] y) {
        float[] z = new float[x.length];
        for (int i = 0; i < z.length; i++) {
            z[i] = x[i] + y[i];
        }
        return z;
    }

    private static Transformation rank(RulePair r, RulePair d, EmbeddingContainer vs, int rankThreshold) {
        float[] transformed = add(d.getDirection(vs), vs.getRawVector(r.input));
        double cosine = VectorMath.cosineSimilarity(d.getDirection(vs), r.getDirection(vs));
        List<String> neighbors = vs.neighbors(transformed, rankThreshold);
        int index = neighbors.indexOf(r.output);
        int rank = (index == -1) ? rankThreshold + 1 : index + 1;
        return new Transformation(r, d, rank, cosine);
    }

    public String toString() {
        return String.format("%s [%d ; %f ; %s]", sub.toString(), support.size(), hitRate, support.get(0).pair.toString());
    }
}
