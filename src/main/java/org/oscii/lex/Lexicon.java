package org.oscii.lex;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import gnu.trove.THashMap;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.oscii.concordance.AlignedCorpus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * A map from expressions to meanings.
 */
public class Lexicon {
    // language -> degraded text -> matching expressions -> meanings
    private Map<String, PatriciaTrie<Map<Expression, Meanings>>> index = new PatriciaTrie<>();
    private final boolean skipIdentity;
    private int numRemoved = 0;

    private final static Logger log = LogManager.getLogger(Lexicon.class);

    public Lexicon() {
        this.skipIdentity = false;
    }

    public Lexicon(boolean skipIdentity) {
        this.skipIdentity = skipIdentity;
    }

    /* Construction */

    public void add(Meaning meaning) {
        if (meaning.translations.size() == 0 && meaning.definitions.size() == 0) {
            return;
        }

        if (skipIdentity) {
            // remove identity translations
            for (Iterator<Translation> iterator = meaning.translations.iterator(); iterator.hasNext(); ) {
                Translation t = iterator.next();
                if (t.translation.text.equals(meaning.expression.text)) {
                    iterator.remove();
                    ++numRemoved;
                }
            }
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

    public void merge(Lexicon otherLex) {
        log.info("Merging lexicons");
        otherLex.forEachMeanings(ms -> ms.meanings.stream().forEach(m -> this.add(m)));
    }

    public void forEachMeanings(Consumer<Meanings> fn) {
        index.values().stream().forEach(trie -> trie.values().forEach(map -> map.values().forEach(fn)));
    }

    /*
     * Add translation frequency information from a corpus.
     */
    public void addFrequencies(AlignedCorpus corpus) {
        log.info("Computing translation frequencies");
        forEachMeanings(ms -> {
            ms.meanings.parallelStream().forEach(m -> setTranslationFrequencies(m, corpus));
            ms.meanings.sort(Order.byMaxTranslationFrequency);
        });
    }

    public void addScores(AlignedCorpus corpus) {
        log.info("Computing meaning scores");
        forEachMeanings(ms -> {
            ms.meanings.stream().forEach(m -> corpus.scoreMeaning(m));
        });
    }

    private void setTranslationFrequencies(Meaning m, AlignedCorpus corpus) {
        Function<Expression, Double> getFrequency = corpus.translationFrequencies(m.expression);
        m.translations.parallelStream().forEach(t -> t.frequency = getFrequency.apply(t.translation));
        m.translations.sort(Order.byFrequency);
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
        return entries.values().stream().flatMap(ms -> ms.meanings.stream()).collect(toList());
    }

    public List<Meaning> lookup(Expression expression) {
        String language = expression.language;
        if (!index.containsKey(language)) {
            return Collections.EMPTY_LIST;
        }
        return index.get(language).get(expression.degraded_text).get(expression).meanings;
    }

    public static String degrade(String query) {
      Objects.requireNonNull(query);
      // TODO(denero) Unicode normalize, remove non-alpha, & normalize diacritics
      String[] tokens = query.trim().split("\\s+");
      String newQuery = String.join(" ", tokens).toLowerCase();
      return newQuery;
    }

    // Prefer to return translations with parts of speech
    private static Translation pickTranslation(List<Translation> ts) {
        return ts.stream()
                .filter(t -> !t.pos.isEmpty()).findFirst()
                .orElse(ts.iterator().next());
    }

    public List<Translation> translate(String query, String source, String target) {
        return translate(query, source, target, null);
    }

    public List<Translation> translate(String query, String source, String target, AlignedCorpus corpus) {
        List<Meaning> all = lookup(query, source);
        if (corpus != null) {
            all.forEach(m -> setTranslationFrequencies(m, corpus));
        }
        List<Translation> translations = all.stream()
                // Aggregate and filter by target language
                .flatMap(m -> m.translations.stream()
                        .filter(t -> t.translation.language.equals(target)))
                // Remove textual duplicates, choosing the first of each group
                .collect(groupingBy(t -> t.translation.text))
                .values().stream().map(Lexicon::pickTranslation)
                .collect(toList());
        translations.sort(Order.byFrequency);
        return translations;
    }

    public List<Definition> define(String query, String source) {
        List<Meaning> all = lookup(query, source);
        return all.stream().flatMap((Meaning m) -> m.definitions.stream()).collect(toList());
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

        // filter meanings to exactly match case of query
        List<Meanings> meaningsList = meanings.collect(toList());
        Stream<Meanings> meaningsFilt = meaningsList.stream().filter(ms -> ms.expression.text.startsWith(query));
        List<Meanings> exactCasePrefixMatches = meaningsFilt.collect(toList());
        if (!exactCasePrefixMatches.isEmpty()) {
            meanings = exactCasePrefixMatches.stream();
        } else {
            meanings = meaningsList.stream();
        }

        Stream<Expression> expressions = meanings.map(ms -> ms.expression).sorted(Order.byLength);
        if (max > 0) {
            expressions = expressions.limit(max);
        }
        return expressions.collect(toList());
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
        log.info("Total removed due to identity: {}", numRemoved);
    }

    public Set<String> getVocabulary(String language) {
        return index.get(language).values().stream()
                .flatMap(m -> m.keySet().stream())
                .map(e -> e.text)
                .collect(toSet());
    }

    // What is known about the meanings of an expression.
    public static class Meanings {
        public Expression expression;
        public List<Meaning> meanings = new ArrayList<>(1);
        public boolean hasDefinition;
        public Set<String> translationLanguages = new HashSet<>();

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
