package org.oscii.morph;

import no.uib.cipr.matrix.Vector;
import org.junit.Test;
import org.oscii.neural.EmbeddingContainer;
import org.oscii.neural.FloatVector;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class RuleScoredTest {

    @Test
    public void score() throws Exception {
        Rule rule = Rule.makeSuffix("ed", "ing");
        RuleScored scored = new RuleScored(rule, Arrays.asList(new RuleLexicalized[]{
                new RuleLexicalized(new RulePair("hopped", "hopping"), rule),
                new RuleLexicalized(new RulePair("shopped", "shopping"), rule),
        }));
        String[] vocab = new String[]{"hopped", "hopping", "shopped", "shopping"};
        Vector[] vectors = new Vector[]{
                new FloatVector(new float[]{1, 2}),
                new FloatVector(new float[]{1, 3}),
                new FloatVector(new float[]{2, 1}),
                new FloatVector(new float[]{2, 2.1f})
        };
        EmbeddingContainer embeddings = new EmbeddingContainer(vocab, vectors);
        RuleScored.ScoringParams params = new RuleScored.ScoringParams();
        params.minSizeDirection = 2;
        scored.score(embeddings, params);
        assertEquals(2, scored.transformations.size());
        assertEquals(1.0, scored.transformations.get(0).cosine, 1e-6);
    }
}