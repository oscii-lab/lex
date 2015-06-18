package org.oscii.detokenize;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.TokenSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Feature extractor for a Token. Data is expected to be set to a TokenSequence.
 */
public class FeaturePipe extends Pipe {
  private static final int AFFIX_LENGTH = 2;

  @Override
  public Instance pipe(Instance inst) {
    Instance result = inst.shallowCopy();
    Token token = (Token) result.getData();
    result.setData(extract(token));
    return result;
  }

  TokenSequence extract(Token token) {
    Stream<Integer> offsets = IntStream.range(0, 3).boxed();
    Stream<String> features = offsets.flatMap(k -> featuresFor(token.after(k)).map(s -> s + k));
    return new TokenSequence(features.map(cc.mallet.types.Token::new).collect(toList()));
  }

  private Stream<String> featuresFor(String word) {
    List<String> features = new ArrayList<>();
    for (int i = 1; i <= AFFIX_LENGTH; i++) {
      if (word.length() <= i * 2) {
        features.add(word);
        break;
      } else {
        features.add(word.substring(0, i) + "_");
        features.add("_" + word.substring(word.length() - i));
        features.add(word.substring(0, i) + "_" + word.substring(word.length() - i));
      }
    }
    return features.stream();
  }
}
