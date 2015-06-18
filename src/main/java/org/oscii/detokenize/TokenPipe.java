package org.oscii.detokenize;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * For each sentence, create a training instance for each word.
 */
public class TokenPipe extends Pipe {

  private final Preprocessor preprocessor;
  Interner<TokenLabel> labels = Interners.newWeakInterner();

  public TokenPipe(Preprocessor preprocessor) {
    this.preprocessor = preprocessor;
  }

  @Override
  public Iterator<Instance> newIteratorFrom(Iterator<Instance> source) {
    Spliterator<Instance> s = Spliterators.spliteratorUnknownSize(source, Spliterator.ORDERED);
    Stream<Instance> examples = StreamSupport.stream(s, false);
    return examples.flatMap(e -> {
      String sentence = (String) e.getData();
      List<String> tokens = tokenize(sentence);
      List<String> separators = inferSeparators(sentence, tokens);
      Stream<Integer> range = IntStream.range(0, tokens.size()).boxed();
      return range.map(i -> {
        // TODO(denero) Infer capitalization and replacement
        TokenLabel label = new TokenLabel(false, separators.get(i), "");
        label = labels.intern(label);
        return Token.labeledInstance(i, tokens, label);
      });
    }).iterator();
  }

  /*
   * Infer separators.
   * TODO(denero) Handle replacements such as " -> '
   */
  private List<String> inferSeparators(String sentence, List<String> tokens) {
    List<String> separators = new ArrayList<>(tokens.size());
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
        separators.add(hasSpace ? " " : "");
      }
    }
    return separators;
  }

  private List<String> tokenize(String sentence) {
    Sequence<IString> tokenSequence = preprocessor.process(sentence);
    List<String> tokens = new ArrayList<>(tokenSequence.size());
    for (int i = 0; i < tokenSequence.size(); i++) {
      tokens.add(tokenSequence.get(i).toString());
    }
    return tokens;
  }
}
