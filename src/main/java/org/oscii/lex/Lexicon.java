package org.oscii.lex;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import gnu.trove.map.hash.THashMap;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.concordance.AlignedCorpus;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A map from expressions to meanings.
 */
public class Lexicon {
    // language -> degraded text -> matching expressions -> meanings
    Map<String, PatriciaTrie<Map<Expression, Meanings>>> index = new PatriciaTrie<>();

    private final static Logger log = LogManager.getLogger(Lexicon.class);

    /* Construction */

    public void add(Meaning meaning) {
        if (meaning.translations.size() == 0 && meaning.definitions.size() == 0) {
            return;
        }
        Expression expression = meaning.expression;
        if (!index.containsKey(expression.language)) {
            index.put(expression.language, new PatriciaTrie<>());
        }
        String key = expression.degraded_text;
        Map<Expression, Meanings> entries = index.get(expression.language).get(key);
        if (entries == null) {
            entries = new THashMap<>(1);
            index.get(expression.language).put(key, entries);
        }
        Meanings meanings = entries.get(expression);
        if (meanings == null) {
            meanings = new Meanings(expression);
            entries.put(expression, meanings);
        }
        meanings.add(meaning);
    }

    private void forEachMeanings(Consumer<Meanings> fn) {
        index.values().stream().forEach(trie -> trie.values().forEach(map -> map.values().forEach(fn)));
    }

    /*
     * Add translation frequency information from a corpus.
     */
    public void addFrequencies(AlignedCorpus corpus) {
        log.info("Computing translation frequencies");
        forEachMeanings(ms -> {
            ms.meanings.forEach(m -> {
                Function<Expression, Double> getFrequency = corpus.translationFrequencies(m.expression);
                m.translations.forEach(translation -> {
                    translation.frequency = getFrequency.apply(translation.translation);
                });
                m.translations.sort(Order.byFrequency);
            });
            ms.meanings.sort(Order.byMaxTranslationFrequency);
        });
    }

    /* Lexicon access methods */

    /*
     * Return all meanings for all expressions matching a query.
     */
    public List<Meaning> lookup(String query, String language) {
        if (!index.containsKey(language)) {
            return Collections.EMPTY_LIST;
        }
        Map<Expression, Meanings> entries = index.get(language).get(degrade(query));
        if (entries == null) {
            return Collections.EMPTY_LIST;
        }
        return entries.values().stream().flatMap(ms -> ms.meanings.stream()).collect(Collectors.toList());
    }

    public List<Meaning> lookup(Expression expression) {
        String language = expression.language;
        if (!index.containsKey(language)) {
            return Collections.EMPTY_LIST;
        }
        return index.get(language).get(expression.degraded_text).get(expression).meanings;
    }

    static String degrade(String query) {
        // TODO(denero) Unicode normalize, remove non-alpha, & normalize diacritics
        return query.toLowerCase();
    }

    // Prefer to return translations with parts of speech
    private static Translation pickTranslation(List<Translation> ts) {
        return ts.stream()
                .filter(t -> !t.pos.isEmpty()).findFirst()
                .orElse(ts.iterator().next());
    }

    public List<Translation> translate(String query, String source, String target) {
        List<Meaning> all = lookup(query, source);
        List<Translation> translations = all.stream()
                // Aggregate and filter by target language
                .flatMap(m -> m.translations.stream()
                        .filter(t -> t.translation.language.equals(target)))
                        // Remove textual duplicates, choosing the first of each group
                .collect(Collectors.groupingBy(t -> t.translation.text))
                .values().stream().map(Lexicon::pickTranslation)
                .collect(Collectors.toList());
        translations.sort(Order.byFrequency);
        return translations;
    }

    public List<Definition> define(String query, String source) {
        List<Meaning> all = lookup(query, source);
        return all.stream().flatMap((Meaning m) -> m.definitions.stream()).collect(Collectors.toList());
    }

    public List<Expression> extend(String query, String language, String translationLanguage, int max) {
        if (!index.containsKey(language)) {
            return Collections.EMPTY_LIST;
        }
        Collection<Map<Expression, Meanings>> all = index.get(language).prefixMap(degrade(query)).values();
        Stream<Meanings> meanings = all.stream().flatMap(m -> m.values().stream());
        if (translationLanguage != null) {
            meanings = meanings.filter(ms -> ms.translationLanguages.contains(translationLanguage));
        }
        Stream<Expression> expressions = meanings.map(ms -> ms.expression).sorted(Order.byLength);
        if (max > 0) {
            expressions = expressions.limit(max);
        }
        return expressions.collect(Collectors.toList());
    }

    /* I/O */

    /*
     * Write all meanings to a file.
     */
    public void write(File file) throws IOException {
        log.info("Writing " + file);
        JsonWriter writer = new JsonWriter(new FileWriter(file));
        writer.setIndent("  ");
        Gson gson = new Gson();
        writer.beginArray();
        forEachMeanings(ms -> ms.meanings.stream().forEach(
                meaning -> gson.toJson(meaning, Meaning.class, writer)));
        writer.endArray();
        writer.close();
    }

    /*
     * Read all meanings from a file.
     */
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

    // What is known about the meanings of an expression.
    private static class Meanings {
        Expression expression;
        List<Meaning> meanings = new ArrayList<>(1);
        boolean hasDefinition;
        Set<String> translationLanguages = new HashSet<>();

        public Meanings(Expression expression) {
            this.expression = expression;
        }

        public void add(Meaning meaning) {
            meanings.add(meaning);
            hasDefinition &= !meaning.definitions.isEmpty();
            meaning.translations.forEach(t -> translationLanguages.add(t.translation.language));
        }
    }
}
