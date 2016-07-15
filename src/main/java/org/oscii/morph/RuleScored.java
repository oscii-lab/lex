package org.oscii.morph;

import org.oscii.neural.EmbeddingContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * A rule scored by the embeddings of its support.
 */
public class RuleScored {
    public final static int MAX_SUPPORT_SAMPLE_SIZE = 1000;
    public final static int RANK_THRESHOLD = 100;

    final Rule sub;
    final List<RuleLexicalized> support;
    List<RuleLexicalized> sample;
    List<Integer> ranks;
    double hitRate;

    public RuleScored(Rule sub, List<RuleLexicalized> support) {
        this.sub = sub;
        this.support = support;
    }

    public double score(EmbeddingContainer embeddings) {
        List<RuleLexicalized> embedded = support.stream().filter(r -> embedded(r, embeddings)).collect(toList());
        if (embedded.size() > MAX_SUPPORT_SAMPLE_SIZE) {
            sample = new ArrayList<>(embedded);
            Collections.shuffle(sample);
            sample = sample.subList(0, MAX_SUPPORT_SAMPLE_SIZE);
        } else {
            sample = embedded;
        }
        int n = sample.size();
        if (n == 0) {
            hitRate = 0.0;
            return hitRate;
        }

        ranks = new ArrayList<>(n * (n-1));
        for (RuleLexicalized r : sample) {
            for (RuleLexicalized s : sample) {
                if (r == s) continue;
                ranks.add(rank(r, s, embeddings));
            }
        }
        hitRate = ranks.stream().filter(r -> r <= RANK_THRESHOLD).count() / (double) ranks.size();
        return hitRate;
    }

    private boolean embedded(RuleLexicalized r, EmbeddingContainer embeddings) {
        for (String w : Arrays.asList(new String[]{r.pair.input, r.pair.output})) {
            if (!embeddings.contains(w)) {
                return false;
            }
        }
        return true;
    }

    private static float[] add(float[] x, float[] y) {
        float[] z = new float[x.length];
        for (int i = 0; i < z.length; i++) {
            z[i] = x[i] + y[i];
        }
        return z;
    }

    private static float[] subtract(float[] x, float[] y) {
        float[] z = new float[x.length];
        for (int i = 0; i < z.length; i++) {
            z[i] = x[i] - y[i];
        }
        return z;
    }

    private static int rank(RuleLexicalized r, RuleLexicalized s, EmbeddingContainer vs) {
        float[] direction = subtract(vs.getRawVector(r.pair.output), vs.getRawVector(r.pair.input));
        float[] got = add(direction, vs.getRawVector(s.pair.input));
        List<String> neighbors = vs.neighbors(got, RANK_THRESHOLD);
        int index = neighbors.indexOf(s.pair.output);
        return (index == -1) ? RANK_THRESHOLD + 1 : index + 1;
    }

    public String toString() {
        return String.format("%s [%d ; %f]", sub.toString(), support.size(), hitRate);

    }
}
