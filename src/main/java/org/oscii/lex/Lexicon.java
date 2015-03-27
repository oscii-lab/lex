package org.oscii.lex;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.concordance.AlignedCorpus;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A map from expressions to meanings.
 */
public class Lexicon {
    Map<Expression, List<Meaning>> lexicon = new THashMap<>();
    // language -> degraded text -> matching expressions
    Map<String, PatriciaTrie<Set<Expression>>> index = new PatriciaTrie<>();

    private final static Logger log = LogManager.getLogger(Lexicon.class);

    /* Construction */

    public void add(Meaning meaning) {
        if (meaning.translations.size() == 0 && meaning.definitions.size() == 0) {
            return;
        }
        if (!lexicon.containsKey(meaning.expression)) {
            lexicon.put(meaning.expression, new ArrayList<>());
        }
        lexicon.get(meaning.expression).add(meaning);
        addToIndex(meaning.expression);
    }

    private void addToIndex(Expression expression) {
        if (!index.containsKey(expression.language)) {
            index.put(expression.language, new PatriciaTrie<>());
        }
        String key = expression.degraded_text;
        Set<Expression> expressions = index.get(expression.language).get(key);
        if (expressions == null) {
            expressions = new THashSet<>(1);
            index.get(expression.language).put(key, expressions);
        }
        expressions.add(expression);
    }

    /*
     * Add translation frequency information from a corpus.
     */
    public void addFrequencies(AlignedCorpus corpus) {
        log.info("Computing translation frequencies");
        lexicon.values().forEach(ms -> {
            ms.forEach(m -> {
                Function<Expression, Double> getFrequency = corpus.translationFrequencies(m.expression);
                m.translations.forEach(translation -> {
                    translation.frequency = getFrequency.apply(translation.translation);
                });
                m.translations.sort(Order.byFrequency);
            });
            ms.sort(Order.byMaxTranslationFrequency);
        });
    }

    /* Lexicon access methods */

    /*
     * Return all meanings for all matching expressions.
     */
    public List<Meaning> lookup(String query, String language) {
        if (!index.containsKey(language)) {
            return Collections.EMPTY_LIST;
        }

        Set<Expression> expressions = index.get(language).get(degrade(query));
        if (expressions == null) {
            return Collections.EMPTY_LIST;
        }
        return expressions.stream().flatMap(e -> lexicon.get(e).stream()).collect(Collectors.toList());
    }

    static String degrade(String query) {
        // TODO(denero) Unicode normalize, remove non alpha, & normalize diacritics
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

    public List<Expression> extend(String query, String language, int max) {
        if (!index.containsKey(language)) {
            return Collections.EMPTY_LIST;
        }
        Collection<Set<Expression>> all = index.get(language).prefixMap(degrade(query)).values();
        Stream<Expression> extensions = all.stream().flatMap(Set::stream);
        if (max > 0) {
            extensions = extensions.limit(max);
        }
        return extensions.collect(Collectors.toList());
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
        lexicon.values().stream().forEachOrdered(ms -> ms.stream().forEach(
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
}
