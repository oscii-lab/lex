package org.oscii.corpus;

import org.junit.Test;

import static org.junit.Assert.*;

public class TokenizerTest {
    @Test
    public void tokenize() throws Exception {
        Tokenizer t = Tokenizer.alphanumericLower;
        String[] tokens = t.tokenize(" `a1` 2;: 3cc/ /44/ \"5e5\" 6's. .");
        assertArrayEquals(new String[]{"a1", "2", "3cc", "44", "5e5", "6's"}, tokens);
        tokens = t.tokenize("a1` 2;: 3cc/ /44/ \"5e5\" 6's. .");
        assertArrayEquals(new String[]{"a1", "2", "3cc", "44", "5e5", "6's"}, tokens);
        tokens = t.tokenize(".");
        assertArrayEquals(new String[0], tokens);
        tokens = t.tokenize("");
        assertArrayEquals(new String[0], tokens);
    }
}