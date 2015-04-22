package org.oscii.panlex;

import com.google.gson.Gson;
import junit.framework.TestCase;
import org.junit.Test;
import org.oscii.lex.Meaning;

import java.util.*;
import java.util.function.Predicate;

public class PanLexJSONParserTest extends TestCase {

    /*
     * Create an object like obj from JSON object s.
     */
    private <T> T create(T obj, String s) {
        return (T) new Gson().fromJson(s, obj.getClass());
    }

    /*
     * Find and return the first element of s matching condition.
     */
    private <T> T getAndCheck(Collection<T> s, Predicate<? super T> condition) {
        Optional<T> maybe = s.stream().filter(condition).findFirst();
        assertTrue(maybe.isPresent());
        return maybe.get();
    }

    @Test
    public void testForEachMeaning() throws Exception {
        Set<String> languages = new HashSet<>(Arrays.asList(new String[]{"spa", "eng"}));
        PanLexJSONParser parser = new PanLexJSONParser(PanLexDir.empty());
        Predicate<Models.Lv> storeLanguage = parser.storeLanguage(languages);
        Predicate<Models.Ex> storeExpression = parser.storeEx(null);

        // Initialize parser
        assertTrue(parser.storeSource(create(new Models.Source(), "{\"ap\": 10, \"li\": \"gp\"}")));
        assertTrue(storeLanguage.test(create(new Models.Lv(), "{\"lv\": 20, \"lc\": \"spa\"}")));
        assertTrue(storeLanguage.test(create(new Models.Lv(), "{\"lv\": 30, \"lc\": \"eng\"}")));
        parser.addLanguages(languages);

        // Load a translation: dog (noun) <-> perro is defined as "a creature"
        assertTrue(storeExpression.test(create(new Models.Ex(), "{\"ex\": 40, \"lv\": 20, \"tt\": \"perro\"}")));
        assertTrue(storeExpression.test(create(new Models.Ex(), "{\"ex\": 50, \"lv\": 30, \"tt\": \"dog\"}")));
        assertTrue(parser.storeMn(create(new Models.Mn(), "{\"mn\": 60, \"ap\": 10}")));
        assertTrue(parser.storeDn(create(new Models.Dn(), "{\"dn\": 70, \"mn\": 60, \"ex\": 40}")));
        assertTrue(parser.storeDn(create(new Models.Dn(), "{\"dn\": 80, \"mn\": 60, \"ex\": 50}")));
        assertTrue(parser.storeDf(create(new Models.Df(), "{\"df\": 90, \"mn\": 60, \"lv\": 30, \"tt\": \"a creature\"}")));
        assertTrue(parser.storeWc(create(new Models.Wc(), "{\"wc\": 100, \"dn\": 80, \"ex\": 1000}")));
        assertTrue(parser.storeWcex(create(new Models.Wcex(), "{\"ex\": 1000, \"tt\": \"noun\"}")));
        parser.read(null);

        // Verify meanings
        List<Meaning> meanings = new ArrayList<>();
        parser.forEachMeaning(meanings::add);
        assertEquals(2, meanings.size());
        Meaning es = getAndCheck(meanings, m -> m.expression.language.equals("es"));
        Meaning en = getAndCheck(meanings, m -> m.expression.language.equals("en"));
        // Definition associated with same-language expression
        assertEquals(0, es.definitions.size());
        assertEquals(1, en.definitions.size());
        // Part-of-speech associated only with English expression
        assertEquals(0, es.pos.size());
        assertEquals(1, en.pos.size());
    }
}
