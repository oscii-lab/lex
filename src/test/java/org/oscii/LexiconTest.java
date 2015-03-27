package org.oscii;

import junit.framework.TestCase;
import org.junit.Test;
import org.oscii.concordance.AlignedCorpus;
import org.oscii.lex.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class LexiconTest extends TestCase {

    @Test
    public void testAddFrequencies() throws Exception {
        Expression dog = new Expression("dog", "en");
        Expression perro = new Expression("perro", "es"); // dog
        Expression can = new Expression("can", "es"); // dog
        Expression zorro = new Expression("zorro", "es"); // fox
        Expression perseguir = new Expression("perseguir", "es"); // to follow
        Meaning common = new Meaning(dog);
        Meaning medium = new Meaning(dog);
        Meaning rare = new Meaning(dog);
        List<String> noun = Arrays.asList(new String[]{"noun"});
        List<String> verb = Arrays.asList(new String[]{"verb"});
        common.translations.add(new Translation(perro, noun));
        common.translations.add(new Translation(can, noun));
        medium.translations.add(new Translation(zorro, noun));
        rare.translations.add(new Translation(perseguir, verb));

        // Assign frequencies
        AlignedCorpus corpus = new AlignedCorpus() {
            @Override
            public Function<Expression, Double> translationFrequencies(Expression source) {
                assertEquals(source, dog);
                return target -> {
                    if (target.equals(perro)) {
                        return 0.8;
                    } else if (target.equals(zorro)) {
                        return 0.1;
                    } else if (target.equals(can)) {
                        return 0.09;
                    } else if (target.equals(perseguir)) {
                        return 0.01;
                    } else {
                        return 0.0;
                    }
                };
            }
        };

        // Check translation order
        Lexicon lex = new Lexicon();
        lex.add(medium);
        lex.add(rare);
        lex.add(common);
        lex.addFrequencies(corpus);
        List<Translation> translations = lex.translate("dog", "en", "es");
        assertEquals(4, translations.size());
        assertEquals(0.8, translations.get(0).frequency);
        assertEquals(0.1, translations.get(1).frequency);
        assertEquals(0.09, translations.get(2).frequency);
        assertEquals(0.01, translations.get(3).frequency);
    }

    @Test
    public void testDefinitions() throws Exception {

    }

    private Meaning enMeaning(String exp) {
        Meaning m = new Meaning(new Expression(exp, "en"));
        m.definitions.add(new Definition(exp, Collections.EMPTY_LIST, exp));
        return m;
    }

    @Test
    public void testExtensions() throws Exception {
        Meaning dog = enMeaning("dog");
        Meaning doggy = enMeaning("doggy");
        Meaning donkey = enMeaning("donkey");

        Lexicon lex = new Lexicon();
        Arrays.asList(new Meaning[]{dog, doggy, donkey}).stream().forEach(
                m -> lex.add(m));

        assertEquals(Collections.singletonList(dog), lex.lookup("Dog", "en"));
        assertEquals(Arrays.asList(new Expression[]{dog.expression, doggy.expression}), lex.extend("Dog", "en", 0));
        assertEquals(Arrays.asList(new Expression[]{dog.expression, doggy.expression, donkey.expression}), lex.extend("Do", "en", 3));
        assertEquals(Arrays.asList(new Expression[]{dog.expression, doggy.expression}), lex.extend("Do", "en", 2));
        assertEquals(Collections.EMPTY_LIST, lex.extend("cat", "en", 0));
    }
}