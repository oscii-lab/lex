package org.oscii;

import junit.framework.TestCase;
import org.junit.Test;
import org.oscii.lex.Expression;
import org.oscii.lex.Translation;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

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

        RabbitHandler handler = new RabbitHandler("", "", "", "", lexicon);
        RabbitHandler.Request request = new RabbitHandler.Request();
        request.query = "dog";
        request.source = "en";
        request.target = "es";
        RabbitHandler.Response response = handler.respond("", request);
        assertEquals("perro", response.translations.get(0).target);
    }
}