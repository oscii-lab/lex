package org.oscii.morph;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class SubstitutionTest {
  @Test
  public void apply() throws Exception {
    class Case {
      Substitution sub;
      String input;
      String expected;

      public Case(Substitution sub, String input, String expected) {
        this.sub = sub;
        this.input = input;
        this.expected = expected;
      }
    }

    List<Case> cases = Arrays.asList(new Case[]{
            new Case(new Substitution.Prefix("", "un"), "able", "unable"),
            new Case(new Substitution.Prefix("", "un"), "pun", "unpun"),
            new Case(new Substitution.Prefix("an", "un"), "anvil", "unvil"),
            new Case(new Substitution.Prefix("an", "un"), "anan", "unan"),
            new Case(new Substitution.Prefix("an", ""), "anan", "an"),
            new Case(new Substitution.Prefix("", "ün"), "áble", "ünáble"),
            new Case(new Substitution.Suffix("", "un"), "able", "ableun"),
            new Case(new Substitution.Suffix("", "un"), "pun", "punun"),
            new Case(new Substitution.Suffix("an", "un"), "villian", "villiun"),
            new Case(new Substitution.Suffix("an", "un"), "anvillian", "anvilliun"),
            new Case(new Substitution.Suffix("an", ""), "anan", "an"),
            new Case(new Substitution.Suffix("", "ün"), "áble", "ábleün"),
    });
    for (Case c : cases) {
      assertEquals(c.sub.toString(), c.expected, c.sub.apply(c.input));
    }
  }

  @Test
  public void testToString() throws Exception {
    assertEquals("s/ε/able", new Substitution.Suffix("", "able").toString());
  }
}