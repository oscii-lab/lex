package org.oscii.detokenize;

import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.en.EnglishPreprocessor;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by denero on 6/17/15.
 */
public class DetokenizerTest {

  @Test
  public void testLabel() throws Exception {
    List<String> train = Arrays.asList("\"What's to-do around here?\"");
    Preprocessor pp = new EnglishPreprocessor(true);
    Detokenizer detokenizer = Detokenizer.train(pp, train);

    // Test on training instance
    String expected = train.get(0);
    List<String> tokenized = Detokenizer.tokenize(pp, expected);
    List<TokenLabel> labels = detokenizer.label(tokenized);
    assertEquals(train.get(0), TokenLabel.render(tokenized, labels));
  }
}