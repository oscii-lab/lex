package org.oscii;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.concordance.AlignedCorpus;
import org.oscii.lex.Expression;
import org.oscii.lex.Meaning;
import org.oscii.lex.Translation;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A map from expressions to meanings.
 */
public class Lexicon {
    Map<Expression, List<Meaning>> lexicon = new HashMap<>();

    private final static Logger log = LogManager.getLogger(Lexicon.class);
    private static Comparator<? super Translation> byFrequency = new Comparator<Translation>() {
        @Override
        public int compare(Translation o1, Translation o2) {
            return Double.compare(o2.frequency, o1.frequency);
        }
    };
    private static Comparator<? super Meaning> byMaxTranslationFrequency = new Comparator<Meaning>() {
        @Override
        public int compare(Meaning o1, Meaning o2) {
            if (o2.translations.size() == 0) {
                return -1;
            } else if (o1.translations.size() == 0) {
                return 1;
            } else {
                return Double.compare(
                        o2.translations.get(0).frequency,
                        o1.translations.get(0).frequency);
            }
        }
    };

    public void add(Meaning meaning) {
        if (meaning.translations.size() == 0 && meaning.definitions.size() == 0) {
            return;
        }
        if (!lexicon.containsKey(meaning.expression)) {
            lexicon.put(meaning.expression, new ArrayList<>());
        }
        lexicon.get(meaning.expression).add(meaning);
    }

    public List<Meaning> lookup(String query, String language) {
        // TODO(denero) Query using degraded text
        return lexicon.getOrDefault(
                new Expression(query, language),
                Collections.emptyList());
    }

    public List<Translation> translate(String query, String source, String target) {
        List<Translation> translations = lookup(query, source).stream()
                // Aggregate and filter by target language
                .flatMap(m -> m.translations.stream()
                        .filter(t -> t.translation.language.equals(target)))
                        // Remove textual duplicates, choosing the first of each group
                .collect(Collectors.groupingBy((Translation t) -> t.translation.text))
                .values().stream().map(ts -> ts.get(0))
                .collect(Collectors.toList());
        translations.sort(byFrequency);
        return translations;
    }

    // Write all meanings to a file.
    public void write(File file) throws IOException {
        log.info("Writing " + file);
        JsonWriter writer = new JsonWriter(new FileWriter(file));
        Gson gson = new Gson();

        writer.beginArray();
        for (List<Meaning> meanings : lexicon.values()) {
            for (Meaning meaning : meanings) {
                gson.toJson(meaning, Meaning.class, writer);
            }
        }
        writer.endArray();
        writer.close();
    }

    // Read all meanings from a file.
    public void read(File file) throws IOException {
        log.info("Reading " + file);
        Gson gson = new Gson();
        InputStream in = new FileInputStream(file);
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        reader.beginArray();
        while (reader.hasNext()) {
            add(gson.fromJson(reader, Meaning.class));
        }
        reader.close();
    }

    /*
     * Add translation frequency information from a corpus.
     */
    public void addFrequencies(AlignedCorpus corpus) {
        lexicon.values().forEach(ms -> {
            ms.forEach(m -> {
                m.translations.forEach(translation -> {
                    Expression source = m.expression;
                    Expression target = translation.translation;
                    translation.frequency = corpus.getFrequency(source, target);
                });
                m.translations.sort(byFrequency);
            });
            ms.sort(byMaxTranslationFrequency);
        });
    }

    // A lexicon that always translates "adult" to "adulto", ignoring args
    public static class Mock extends Lexicon {
        @Override
        public List<Meaning> lookup(String query, String language) {
            Expression adult = new Expression("adulto", "en");
            Expression adulto = new Expression("adulto", "es");
            Meaning meaning = new Meaning(adult);
            meaning.translations.add(new Translation(adulto));
            return Arrays.asList(meaning);
        }
    }
}
