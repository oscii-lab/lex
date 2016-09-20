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
            new Case(Rule.makePrefix("", "un"), "able", "unable"),
            new Case(Rule.makePrefix("", "un"), "pun", "unpun"),
            new Case(Rule.makePrefix("an", "un"), "anvil", "unvil"),
            new Case(Rule.makePrefix("an", "un"), "anan", "unan"),
            new Case(Rule.makePrefix("an", ""), "anan", "an"),
            new Case(Rule.makePrefix("", "ün"), "áble", "ünáble"),
            new Case(Rule.makeSuffix("", "un"), "able", "ableun"),
            new Case(Rule.makeSuffix("", "un"), "pun", "punun"),
            new Case(Rule.makeSuffix("an", "un"), "villian", "villiun"),
            new Case(Rule.makeSuffix("an", "un"), "anvillian", "anvilliun"),
            new Case(Rule.makeSuffix("an", ""), "anan", "an"),
            new Case(Rule.makeSuffix("", "ün"), "áble", "ábleün"),
    });
    for (Case c : cases) {
      assertEquals(c.sub.toString(), c.expected, c.sub.apply(c.input));
    }
  }

  @Test
  public void testToString() throws Exception {
    assertEquals("s/ε/able", Rule.makeSuffix("", "able").toString());
  }
}