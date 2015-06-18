package org.oscii.detokenize;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.TokenSequence;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Feature extractor for Tokens.
 */
public class FeaturePipe extends Pipe {
  @Override
  public Instance pipe(Instance inst) {
    Instance result = inst.shallowCopy();
    Token token = (Token) result.getData();
    List<String> words = Arrays.asList(token.current(), token.next());
    result.setData(new TokenSequence(
            words.stream().map(w -> new cc.mallet.types.Token(w)).collect(toList())));
    return result;
  }
}
