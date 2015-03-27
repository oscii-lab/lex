package org.oscii.api;

import junit.framework.TestCase;
import org.junit.Test;
import org.oscii.lex.Lexicon;
import org.oscii.lex.Expression;
import org.oscii.lex.Translation;

import java.util.Arrays;
import java.util.List;

public class RabbitHandlerTest extends TestCase {

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
        Protocol protocol = new Protocol(lexicon, 0);

        RabbitHandler handler = new RabbitHandler("", "", "", "", protocol);
        Protocol.Request request = new Protocol.Request("dog", "en", "es");
        Protocol.Response response = protocol.respond(request);
        assertEquals("perro", response.translations.get(0).target);
    }
}