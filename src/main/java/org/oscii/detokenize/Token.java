package org.oscii.detokenize;

import cc.mallet.types.Instance;

import java.util.List;

/**
 * A token in a sentence.
 */
public class Token {
  private static final String END = "</S>";
  int index;
  List<String> tokens;
  TokenLabel label;

  public Token(int index, List<String> tokens, TokenLabel label) {
    this.index = index;
    this.tokens = tokens;
    this.label = label;
  }

  public static Instance labeledInstance(int index, List<String> tokens, TokenLabel label) {
    Token token = new Token(index, tokens, label);
    return new Instance(token, label, null, null);
  }

  public static Instance unlabeledInstance(int index, List<String> tokens) {
    Token token = new Token(index, tokens, null);
    return new Instance(token, null, null, null);
  }

  public String current() {
    return tokens.get(index);
  }

  public String next() {
    return (index + 1 == tokens.size()) ? END : tokens.get(index);
  }
}
