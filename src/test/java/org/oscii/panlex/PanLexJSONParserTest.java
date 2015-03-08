package org.oscii.panlex;

import com.google.gson.Gson;
import junit.framework.TestCase;
import org.junit.Test;
import org.oscii.lex.Meaning;

import java.util.*;
import java.util.function.Predicate;

import static org.junit.Assert.*;

public class PanLexJSONParserTest extends TestCase {

    private <T> T create(T obj, String s) {
        return (T) new Gson().fromJson(s, obj.getClass());
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
        // TODO(denero) Check content
    }
}