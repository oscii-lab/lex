package org.oscii.detokenize;

import cc.mallet.types.Instance;

import java.util.List;

/**
 * A token in a sentence.
 */
public class Token {
  int index;
  List<String> tokens;
  List<String> separators;

  public Token(int index, List<String> tokens, List<String> separators) {
    this.index = index;
    this.tokens = tokens;
    this.separators = separators;
  }

  public static Instance labeledInstance(int index, List<String> tokens, List<String> separators) {
    Token token = new Token(index, tokens, separators);
    return new Instance(token, separators.get(index), null, null);
  }

  public static Instance unlabeledInstance(int index, List<String> tokens) {
    Token token = new Token(index, tokens, null);
    return new Instance(token, null, null, null);
  }
}
