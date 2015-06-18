package org.oscii.detokenize;

import cc.mallet.types.Instance;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.en.EnglishPreprocessor;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

/**
 * Created by denero on 6/17/15.
 */
public class TokenPipeTest {

  @Test
  public void testNewIteratorFrom() throws Exception {
    List<String> examples = Arrays.asList("\"What's to-do?\"", "; . ... U.S.!");
    List<Instance> unprocessed = examples.stream().map(e ->
            new Instance(e, null, null, null)).collect(toList());
    Preprocessor preprocessor = new EnglishPreprocessor(true);
    TokenPipe pipe = new TokenPipe(preprocessor);
    List<Instance> instances = new ArrayList<>();
    pipe.newIteratorFrom(unprocessed.iterator()).forEachRemaining(instances::add);

    List<String> expected = Arrays.asList(
            "", "", " ", "", "", "",
            " ", " ", " ", "", "");
    assertEquals(expected, instances.stream()
            .map(i -> ((TokenLabel) i.getTarget()).following)
            .collect(toList()));
  }
}