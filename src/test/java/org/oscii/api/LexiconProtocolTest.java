package org.oscii.api;

import junit.framework.TestCase;
import org.junit.Test;
import org.oscii.lex.Expression;
import org.oscii.lex.Lexicon;
import org.oscii.lex.Translation;

import java.util.Arrays;
import java.util.List;

public class LexiconProtocolTest extends TestCase {

    public static LexiconProtocol.Request translation(String query, String source, String target) {
        LexiconProtocol.Request r = new LexiconProtocol.Request();
        r.query = query;
        r.source = source;
        r.target = target;
        r.translate = true;
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
        LexiconProtocol protocol = new LexiconProtocol(lexicon, null, null, null);
        LexiconProtocol.Request request = translation("dog", "en", "es");
        request.minFrequency = 0.0;
        request.translate = true;
        LexiconProtocol.Response response = protocol.respond(request);
        assertEquals("perro", response.translations.get(0).target);
    }
}
