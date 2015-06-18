package org.oscii.detokenize;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;

/**
 * Infer label sequence from a preprocessor applied to a string.
 * <p>
 * TODO(denero) Infer capitalization and replacement
 */
public class Labeler {
  public List<TokenLabel> getLabels(String sentence, List<String> tokens) {
    List<TokenLabel> labels = new ArrayList<>(tokens.size());
    int t = 0; // token number
    PeekingIterator<Integer> chars = Iterators.peekingIterator(sentence.codePoints().iterator());
    PrimitiveIterator.OfInt token = null;
    while (t < tokens.size()) {
      // Check for beginning/end of token
      if (token == null) {
        token = tokens.get(t).codePoints().iterator();
      }
      if (!token.hasNext()) {
        t += 1;
        token = null;
        continue;
      }

      // Check tokenization consistency
      if (chars.next() != token.next()) {
        throw new RuntimeException("Invalid tokenization: " + sentence + " ; " + tokens);
      }

      // Add separator
      if (!token.hasNext()) {
        boolean hasSpace = false;
        while (chars.hasNext() && chars.peek() == ' ') {
          chars.next();
          hasSpace = true;
        }
        String following = hasSpace ? " " : "";
        labels.add(new TokenLabel(false, following, ""));
      }
    }
    return labels;
  }
}
