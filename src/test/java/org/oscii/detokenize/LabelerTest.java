package org.oscii.detokenize;

import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.en.EnglishPreprocessor;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

/**
 * Created by denero on 6/17/15.
 */
public class LabelerTest {

  @Test
  public void test() throws Exception {
    List<String> examples = Arrays.asList(
            "\"What's to-do?\"",
            "; . ... U.S.!");
    List<List<TokenLabel>> expected = Arrays.asList(
            toLabels(Arrays.asList("", "", " ", "", "", "")),
            toLabels(Arrays.asList(" ", " ", " ", "", "")));
    Preprocessor preprocessor = new EnglishPreprocessor(true);
    Labeler labeler = new Labeler();
    for (int i = 0; i < examples.size(); i++) {
      List<String> tokens = Detokenizer.tokenize(preprocessor, examples.get(i));
      List<TokenLabel> labels = labeler.getLabels(examples.get(i), tokens);
      assertEquals(labels, expected.get(i));
    }
  }

  private List<TokenLabel> toLabels(List<String> strings) {
    return strings.stream().map(s -> new TokenLabel(false, s, "")).collect(toList());
  }
}