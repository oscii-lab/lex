package org.oscii.detokenize;

import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.en.EnglishPreprocessor;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by denero on 6/17/15.
 */
public class DetokenizerTest {

  @Test
  public void testPredictLabels() throws Exception {
    List<String> train = Arrays.asList(
            "\"What's to-do around here?\"",
            "What is this place?",
            "This \"place\" is scary.",
            "\"Is it this place\"",
            "Is it this place?",
            "This's the place.",
            "Why this one?",
            "This is it.",
            "Here it is"
    );
    List<String> test = Arrays.asList(
            "\"Where's this place?\"",
            "What is this place?",
            "Is this place it?",
            "This is it"
    );

    Preprocessor pp = new EnglishPreprocessor(true);
    Detokenizer detokenizer = Detokenizer.train(pp, train.iterator());

    // Test on training instances
    for (String expected : train) {
      List<String> tokenized = Detokenizer.tokenize(pp, expected);
      List<TokenLabel> labels = detokenizer.predictLabels(tokenized);
      assertEquals(expected, TokenLabel.render(tokenized, labels));
    }

    // Test on held-out instances
    for (String expected : test) {
      List<String> tokenized = Detokenizer.tokenize(pp, expected);
      List<TokenLabel> labels = detokenizer.predictLabels(tokenized);
      assertEquals(expected, TokenLabel.render(tokenized, labels));
    }
  }
}