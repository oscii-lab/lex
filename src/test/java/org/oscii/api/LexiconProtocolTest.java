package org.oscii.api;

import junit.framework.TestCase;
import org.junit.Test;
import org.oscii.concordance.AlignedCorpus;
import org.oscii.concordance.AlignedSentence;
import org.oscii.concordance.SentenceExample;
import org.oscii.lex.Expression;
import org.oscii.lex.Lexicon;
import org.oscii.lex.Translation;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class LexiconProtocolTest extends TestCase {

  public static LexiconProtocol.Request translation(String query, String source, String target) {
    LexiconProtocol.Request r = new LexiconProtocol.Request();
    r.query = query;
    r.source = source;
    r.target = target;
    r.translate = true;
    r.example = true;
    return r;
  }

  @Test
  public void testRespond() throws Exception {
    List<String> noun = Arrays.asList(new String[]{"noun"});
    List<Translation> translations = Arrays.asList(new Translation[]{
            new Translation(new Expression("perro", "es"), noun),
            new Translation(new Expression("perra", "es"), noun)
    });

    Lexicon lexicon = new Lexicon() {
      @Override
      public List<Translation> translate(String query, String source, String target) {
        assertEquals("dog", query);
        assertEquals("en", source);
        assertEquals("es", target);
        return translations;
      }
    };

    // TODO Check for term promotion and frequency ranking
    AlignedCorpus corpus = new AlignedCorpus() {
      @Override
      public void read(String path, String sourceLanguage, String targetLanguage, int max) throws IOException {
      }

      @Override
      public Function<Expression, Double> translationFrequencies(Expression source) {
        return null;
      }

      String[] delimit(String[] tokens) {
        String[] delimiters = new String[tokens.length+1];
        delimiters[0] = "";
        delimiters[tokens.length] = "";
        for (int i = 1; i < tokens.length; i++) {
          delimiters[i] = " ";
        }
        return delimiters;
      }

      AlignedSentence aligned(String src, String trg) {
        String[] srcTokens = src.split(" ");
        String[] trgTokens = trg.split(" ");
        String[] srcDelimiters  = delimit(srcTokens);
        String[] trgDelimiters = delimit(trgTokens);
        return AlignedSentence.create(srcTokens, srcDelimiters, trgTokens, trgDelimiters, null, "en", "es");
      }

      @Override
      public List<SentenceExample> examples(String query, String source, String target, String systemId, int max, int memoryId, boolean bLimit) {
        assertEquals("dog", query);
        assertEquals("en", source);
        assertEquals("es", target);
        List<SentenceExample> examples = Arrays.asList(new SentenceExample[]{
                new SentenceExample(aligned("small dog", "perrito"), 1, 1, 0, 1, 1),
                new SentenceExample(aligned("Dog", "p e r r o"), 0, 1, 0, 5, 1)
        });
        return examples;
      }
    };

    LexiconProtocol protocol = new LexiconProtocol(lexicon, corpus, null, null, null);
    LexiconProtocol.Request request = translation("dog", "en", "es");
    request.minFrequency = 0.0;
    request.translate = true;
    LexiconProtocol.Response response = protocol.respond(request);
    assertEquals("perro", response.translations.get(0).target);
    assertEquals("perra", response.translations.get(1).target);
    assertEquals(3, response.translations.size());
    assertEquals("p e r r o", response.translations.get(2).target);
    assertEquals(1, response.examples.size());
  }
}
