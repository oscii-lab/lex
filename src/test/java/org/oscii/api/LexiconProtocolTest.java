package org.oscii.api;

import junit.framework.TestCase;
import org.junit.Test;
import org.oscii.concordance.AlignedCorpus;
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
        Translation translation = new Translation(new Expression("perro", "es"), noun);
        Lexicon lexicon = new Lexicon() {
            @Override
            public List<Translation> translate(String query, String source, String target) {
                assertEquals("dog", query);
                assertEquals("en", source);
                assertEquals("es", target);
                return Arrays.asList(new Translation[]{translation});
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

            @Override
            public List<SentenceExample> examples(String query, String source, String target, int max, int memoryId, boolean bLimit) {
                return null;
            }
        };

        LexiconProtocol protocol = new LexiconProtocol(lexicon, null, null, null);
        LexiconProtocol.Request request = translation("dog", "en", "es");
        request.minFrequency = 0.0;
        request.translate = true;
        LexiconProtocol.Response response = protocol.respond(request);
        assertEquals("perro", response.translations.get(0).target);
    }
}
