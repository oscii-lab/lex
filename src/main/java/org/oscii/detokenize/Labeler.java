package org.oscii.detokenize;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

import java.util.ArrayList;
import java.util.List;

/**
 * Infer label sequence from a preprocessor applied to a string.
 * <p>
 * TODO(denero) Infer capitalization and replacement
 * <p>
 * Note: The logic in this class could be removed if we exposed the CoreLabel
 * sequence of the pre-processor, but since we may move away from CoreNLP, lets
 * keep it for now.
 */
public class Labeler {
    public List<TokenLabel> getLabels(String sentence, List<String> tokens) throws LabelException {
        List<TokenLabel> labels = new ArrayList<>(tokens.size());
        int t = 0; // token number
        PeekingIterator<Integer> chars = Iterators.peekingIterator(sentence.codePoints().iterator());
        PeekingIterator<Integer> token = null;
        while (t < tokens.size()) {
            // Check for beginning/end of token
            if (token == null) {
                token = Iterators.peekingIterator(tokens.get(t).codePoints().iterator());
            }
            if (!token.hasNext()) {
                t += 1;
                token = null;
                continue;
            }

            String replace = null;

            if (!chars.hasNext() || (token.peek() == '.' && chars.peek() != '.')) {
                // Check for . insertion, which happens in sentence-final abbreviations.
                if (token.next() == '.') {
                    replace = "";
                } else {
                    throw new LabelException("No next char AT " + tokens.get(t) + " OF " + tokens + " FROM " + sentence);
                }
            } else {
                // Advance both and check tokenization consistency
                int charsNext = chars.next();
                int tokensNext = token.next();
                if (charsNext != tokensNext) {
                    throw new LabelException("Invalid tokenization " + charsNext + " != " + tokensNext + " AT \"" + tokens.get(t) + "\" OF " + tokens + " FROM " + sentence);
                }
            }

            // Add separator
            if (!token.hasNext()) {
                boolean hasSpace = false;
                while (chars.hasNext() && (Character.isWhitespace(chars.peek()) || Character.isSpaceChar(chars.peek()))) {
                    chars.next();
                    hasSpace = true;
                }
                String following = hasSpace ? " " : "";
                labels.add(new TokenLabel(false, following, replace));
            }
        }
        return labels;
    }

    public static class LabelException extends Exception {
        public LabelException(String s) {
            super(s);
        }
    }
}
