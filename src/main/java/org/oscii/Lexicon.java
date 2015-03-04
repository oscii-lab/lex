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

    public void add(Meaning meaning) {
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
        return lookup(query, source).stream()
                // Aggregate and filter by target language
                .flatMap(m -> m.translations.stream()
                        .filter(t -> t.translation.language.equals(target)))
                        // Remove textual duplicates, choosing the first of each group
                .collect(Collectors.groupingBy((Translation t) -> t.translation.text))
                .values().stream().map(ts -> ts.iterator().next())
                .collect(Collectors.toList());
    }

    // Write all meanings to a file.
    public void write(File file) throws IOException {
        log.info("Writing %s", file);
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
        log.info("Reading %s", file);
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
        // TODO
    }

    // A lexicon that always translates "adult" to "adulto", ignoring args
    public static class Mock extends Lexicon {
        @Override
        public List<Meaning> lookup(String query, String language) {
            Expression adult = new Expression("adulto", "en");
            Expression adulto = new Expression("adulto", "es");
            Meaning meaning = new Meaning(adult);
            meaning.translations.add(new Translation(adulto, 0));
            return Arrays.asList(meaning);
        }
    }
}
