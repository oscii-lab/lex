package org.oscii.detokenize;

import cc.mallet.types.Instance;

import java.util.List;

/**
 * A token in a sentence.
 */
public class Token {
    // TODO(denero) Maybe this constant is defined somewhere in CoreNLP or Phrasal
    private static final String END = "</S>";
    // This label must appear in the training set, or Mallet will crash (silly behavior)
    // See: http://comments.gmane.org/gmane.comp.ai.mallet.devel/620
    private static final TokenLabel DUMMY_LABEL = new TokenLabel(false, "", null);
    int index;
    List<String> tokens;
    TokenLabel label;

    private Token(int index, List<String> tokens, TokenLabel label) {
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
        return new Instance(token, DUMMY_LABEL, null, null);
    }

    public String current() {
        return tokens.get(index);
    }

    public String after(int k) {
        return (index + k >= tokens.size()) ? END : tokens.get(index + k);
    }
}
