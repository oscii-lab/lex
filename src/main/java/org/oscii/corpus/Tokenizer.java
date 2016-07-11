package org.oscii.corpus;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Simple corpus tokenizer
 */
public interface Tokenizer {
    String[] tokenize(String line);

    Tokenizer alphanumeric = new Tokenizer() {
        String delimiter = "[^\\w']+";
        Pattern splitter = Pattern.compile(delimiter);

        @Override
        public String[] tokenize(String line) {
            String[] tokens = splitter.split(line);
            if (tokens.length > 0 && tokens[0].isEmpty()) {
                return Arrays.copyOfRange(tokens, 1, tokens.length);
            } else {
                return tokens;
            }
        }
    };

    Tokenizer alphanumericLower = new Tokenizer() {
        @Override
        public String[] tokenize(String line) {
            return alphanumeric.tokenize(line.toLowerCase());
        }
    };


}
