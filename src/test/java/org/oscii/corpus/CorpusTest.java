package org.oscii.corpus;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class CorpusTest {
  @Test
  public void addLines() throws Exception {
    List<String> lines = Arrays.asList(new String[] {
            "one, 2;  three3.",
            "three,3\ttwo and one"
    });
    Corpus c = new Corpus(Tokenizer.alphanumeric);
    c.addLines(lines.parallelStream());
    assertEquals(2, c.count("one"));
    assertEquals(1, c.count("2"));
    assertEquals(1, c.count("three"));
    assertEquals(1, c.count("three3"));
  }
}