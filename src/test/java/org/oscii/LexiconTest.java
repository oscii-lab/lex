package org.oscii;

import junit.framework.TestCase;
import org.junit.Test;
import org.oscii.concordance.AlignedCorpus;
import org.oscii.lex.Expression;
import org.oscii.lex.Meaning;
import org.oscii.lex.Translation;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.*;

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
}