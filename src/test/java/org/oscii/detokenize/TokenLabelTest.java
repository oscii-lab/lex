package org.oscii.detokenize;

import junit.framework.TestCase;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by denero on 6/17/15.
 */
public class TokenLabelTest extends TestCase {

  @Test
  public void testRenderToken() throws Exception {
    List<String> tokens = Arrays.asList("hello", ",", "mundo");
    List<TokenLabel> labels = Arrays.asList(
            new TokenLabel(true, "", null),
            new TokenLabel(false, " ", null),
            new TokenLabel(true, "", "world"));
    assertEquals("Hello, World", TokenLabel.render(tokens, labels));
  }

  @Test
  public void testRender() throws Exception {
    List<RenderTokenCase> cases = Arrays.asList(
            new RenderTokenCase("dog", new TokenLabel(true, " ", null), "Dog"),
            new RenderTokenCase("dog", new TokenLabel(false, " ", "cat"), "cat"),
            new RenderTokenCase("dog", new TokenLabel(true, " ", "cat"), "Cat"),
    new RenderTokenCase("dog", new TokenLabel(false, "", ""), ""));
    cases.forEach(c -> assertEquals(c.label.renderToken(c.token), c.result));
  }

  private class RenderTokenCase {
    String token;
    TokenLabel label;
    String result;

    public RenderTokenCase(String token, TokenLabel label, String result) {
      this.token = token;
      this.label = label;
      this.result = result;
    }
  }
}