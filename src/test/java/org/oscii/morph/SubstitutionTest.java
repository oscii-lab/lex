package org.oscii.morph;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class SubstitutionTest {
  @Test
  public void apply() throws Exception {
    class Case {
      Rule sub;
      String input;
      String expected;

      public Case(Rule sub, String input, String expected) {
        this.sub = sub;
        this.input = input;
        this.expected = expected;
      }
    }

    List<Case> cases = Arrays.asList(new Case[]{
            new Case(new Rule.Prefix("", "un"), "able", "unable"),
            new Case(new Rule.Prefix("", "un"), "pun", "unpun"),
            new Case(new Rule.Prefix("an", "un"), "anvil", "unvil"),
            new Case(new Rule.Prefix("an", "un"), "anan", "unan"),
            new Case(new Rule.Prefix("an", ""), "anan", "an"),
            new Case(new Rule.Prefix("", "ün"), "áble", "ünáble"),
            new Case(new Rule.Suffix("", "un"), "able", "ableun"),
            new Case(new Rule.Suffix("", "un"), "pun", "punun"),
            new Case(new Rule.Suffix("an", "un"), "villian", "villiun"),
            new Case(new Rule.Suffix("an", "un"), "anvillian", "anvilliun"),
            new Case(new Rule.Suffix("an", ""), "anan", "an"),
            new Case(new Rule.Suffix("", "ün"), "áble", "ábleün"),
    });
    for (Case c : cases) {
      assertEquals(c.sub.toString(), c.expected, c.sub.apply(c.input));
    }
  }

  @Test
  public void testToString() throws Exception {
    assertEquals("s/ε/able", new Rule.Suffix("", "able").toString());
  }
}