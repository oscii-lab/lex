package org.oscii.detokenize;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.TokenSequence;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Feature extractor for a Token. Data is expected to be set to a TokenSequence.
 */
public class FeaturePipe extends Pipe {
  @Override
  public Instance pipe(Instance inst) {
    Instance result = inst.shallowCopy();
    Token token = (Token) result.getData();
    result.setData(extract(token));
    return result;
  }

  TokenSequence extract(Token token) {
    List<String> words = Arrays.asList(token.current(), token.next());
    return new TokenSequence(words.stream().map(cc.mallet.types.Token::new).collect(toList()));
  }
}
