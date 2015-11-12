package org.oscii.lex;

import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class RankerTest {

  @Test
  public void testRank() throws Exception {

    String content = "de,en,1,der\nde,en,2,die\nde,en,3,das\nde,en,4,wieso\nde,en,5,weshalb\nde,en,6,warum";
    InputStream is = new ByteArrayInputStream(content.getBytes());
    LineNumberReader reader = new LineNumberReader(new InputStreamReader(is, "UTF-8"));

    Ranker ranker = new Ranker(reader);

    List<Expression> expressions = new ArrayList<Expression>();
    expressions.add(new Expression("warum", "de"));
    expressions.add(new Expression("diese", "de"));
    expressions.add(new Expression("wieso", "de"));
    expressions.add(new Expression("das", "de"));
    expressions.add(new Expression("deren", "de"));
    expressions.add(new Expression("weshalb", "de"));

    System.err.println("before rerank= " + expressions);
    ranker.rerank(expressions, "en", "de");
    System.err.println(" after rerank= " + expressions);
    assertEquals(6, expressions.size());
    assertEquals("das", expressions.get(0).text);
    assertEquals("wieso", expressions.get(1).text);
    assertEquals("weshalb", expressions.get(2).text);
    assertEquals("warum", expressions.get(3).text);
    assertEquals("diese", expressions.get(4).text);
    assertEquals("deren", expressions.get(5).text);
  }

}
