package org.oscii.panlex;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import gnu.trove.THashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.lex.Definition;
import org.oscii.lex.Expression;
import org.oscii.lex.Meaning;
import org.oscii.lex.Translation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

/*
 * Parse JSON export of PanLex.
 */
public class PanLexJSONParser {
    // Filtered subsets of original PanLex tables, keyed by PanLex ID.
    private final Map<Integer, Models.Lv> varieties = new THashMap<>();
    private final Map<Integer, Models.Source> sources = new THashMap<>();
    private final Map<Integer, Models.Ex> expressions = new THashMap<>();
    private final Map<Integer, Models.Mn> meanings = new THashMap<>();
    private final Map<Integer, Models.Dn> denotations = new THashMap<>();
    private final Map<Integer, Models.Df> definitions = new THashMap<>();
    private final Map<Integer, Models.Wc> wordClasses = new THashMap<>();
    private final Map<Integer, String> wordClassNames = new THashMap<>();

    private PanLexDir dir;

    // Open-source license types
    private final Set<String> allowedLicenseTypes = new HashSet<>(Arrays.asList(
            new String[]{"nr", "cc", "gp", "gl", "gd", "mi"}));

    // TODO(denero) Currently, tags are all ISO-639-1 2-letter language codes.
    // PanLex Language variety ID -> RFC5654 language tag (e.g., "en-GB")
    private final Map<Integer, String> languageTags = new HashMap<>();

    // Indices
    private Map<Integer, List<Models.Wc>> wordClassByDn;
    private Map<Integer, List<Models.Df>> definitionByMeaning;

    private final static Logger log = LogManager.getLogger(PanLexJSONParser.class);

    public PanLexJSONParser(PanLexDir dir) {
        this.dir = dir;
        parse(dir.open("ap.json"), new Models.Source(), this::storeSource);
    }

    /*
     * Reads structural PanLex data from JSON export for languages.
     */
    public void addLanguages(Collection<String> languages) {
        // Convert 2-letter to 3-letter language codes
        Set<String> three_letter_codes = languages.stream()
                .map(s -> s.length() == 2 ? new Locale(s).getISO3Language() : s)
                .collect(toSet());
        parse(dir.open("lv.json"), new Models.Lv(), storeLanguage(three_letter_codes));
        populateLanguageTags();
    }

    /*
     * Converts language varieties (PanLex-specific) to RFC5654 language tags.
     */
    private void populateLanguageTags() {
        Map<String, String> threeToTwo = new HashMap<>();
        for (Locale loc : Locale.getAvailableLocales()) {
            if (!loc.getLanguage().isEmpty()) {
                threeToTwo.put(loc.getISO3Language(), loc.getLanguage());
            }
        }
        // TODO(denero) Reconstruct other language tag info (e.g., region)
        // TODO(denero) What about 3-letter codes with no 2-letter equivalent?
        varieties.entrySet().stream().forEach(
                e -> languageTags.put(e.getKey(), threeToTwo.getOrDefault(e.getValue().lc, "")));
    }

    /*
     * Reads expressions and denotations.
     */
    public void read(Pattern filter) {
        parse(dir.open("ex.json"), new Models.Ex(), storeEx(filter));
        parse(dir.open("mn.json"), new Models.Mn(), this::storeMn);
        parse(dir.open("dn.json"), new Models.Dn(), this::storeDn);
        parse(dir.open("df.json"), new Models.Df(), this::storeDf);
        parse(dir.open("wc.json"), new Models.Wc(), this::storeWc);
        parse(dir.open("wcex.json"), new Models.Wcex(), this::storeWcex);

        log.info("Indexing word classes");
        wordClassByDn = wordClasses.values().parallelStream().collect(groupingBy(wc -> wc.dn));
        log.info("Indexing definitions");
        definitionByMeaning = definitions.values().parallelStream().collect(groupingBy(df -> df.mn));
    }

    /*
     * Parses a JSON file of T records and calls process on each.
     */
    private <T> void parse(InputStream in, T record, Predicate<T> process) {
        int accepted = 0;
        Class type = record.getClass();
        String name = type.getSimpleName();
        Gson gson = new Gson();
        log.info(String.format("Parsing %s records", name));
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
            reader.beginArray();
            while (reader.hasNext()) {
                record = gson.fromJson(reader, type);
                if (process.test(record)) {
                    accepted++;
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info(String.format("Parsed %d %s records", accepted, name));
    }

    /*
     * For all words with a shared meaning, process Meanings by language.
     */
    public void forEachMeaning(Consumer<Meaning> process) {
      Map<String, List<Meaning>> byLanguage = denotations.values().stream().map(this::createMeaning)
        .collect(groupingBy(m -> m.expression.languageTag));
      processMeanings(process, byLanguage);
    }

    /*
     * Create a meaning for a denotation containing in-language definitions.
     */
    private Meaning createMeaning(Models.Dn dn) {
        Models.Ex ex = expressions.get(dn.ex);
        String languageTag = languageTags.get(ex.lv);
        Expression expression = new Expression(ex.tt, ex.td, languageTag);
        Meaning meaning = new Meaning(expression);
        for (Models.Wc wc : wordClassByDn.getOrDefault(dn.dn, Collections.emptyList())) {
            meaning.pos.add(wordClassNames.get(wc.ex));
        }
        for (Models.Df df : definitionByMeaning.getOrDefault(dn.mn, Collections.emptyList())) {
            // TODO(denero) Should definitions be restricted by source language? They are now
            if (df.lv == ex.lv) {
                String dataSource = sources.get(meanings.get(df.mn).ap).ti;
                meaning.definitions.add(new Definition(df.tt, meaning.pos, languageTag, dataSource));
            }
        }
        return meaning;
    }

    /*
     * Constructs Meaning values for all expressions with the same meaning.
     */
    private void processMeanings(
            Consumer<Meaning> process,
            Map<String, List<Meaning>> byLanguage) {
        for (String sourceLanguage : byLanguage.keySet()) {
            List<Meaning> meanings = byLanguage.get(sourceLanguage);
            // Add Translations
            for (String targetLanguage : byLanguage.keySet()) {
                if (!sourceLanguage.equals(targetLanguage)) {
                    for (Meaning source : meanings) {
                        for (Meaning target : byLanguage.get(targetLanguage)) {
                            source.translations.add(new Translation(target.expression, target.pos));
                        }
                    }
                }
            }
            // Add synonyms
            for (Meaning original : meanings) {
                for (Meaning other : meanings) {
                    if (original != other) {
                        original.synonyms.add(other.expression);
                    }
                }
            }
            meanings.forEach(process);
        }
    }

    // JSON parsing predicates (package visible for testing)

    Predicate<Models.Lv> storeLanguage(Set<String> languages) {
        return (Models.Lv lv) -> {
            if (languages.contains(lv.lc)) {
                varieties.put(lv.lv, lv);
                return true;
            }
            return false;
        };
    }

    boolean storeSource(Models.Source ap) {
        if (allowedLicenseTypes.contains(ap.li)) {
            sources.put(ap.ap, ap);
            return true;
        }
        return false;
    }

    Predicate<Models.Ex> storeEx(Pattern filter) {
        return (Models.Ex ex) -> {
            if (varieties.containsKey(ex.lv) &&
                    (filter == null || filter.matcher(ex.tt).matches())) {
                ex.td = null;
                expressions.put(ex.ex, ex);
                return true;
            }
            return false;
        };
    }

    boolean storeMn(Models.Mn mn) {
        if (sources.containsKey(mn.ap)) {
            meanings.put(mn.mn, mn);
            return true;
        }
        return false;
    }

    boolean storeDn(Models.Dn dn) {
        if (expressions.containsKey(dn.ex) && meanings.containsKey(dn.mn)) {
            denotations.put(dn.dn, dn);
            return true;
        }
        return false;
    }

    boolean storeDf(Models.Df df) {
        if (varieties.containsKey(df.lv) && meanings.containsKey(df.mn)) {
            definitions.put(df.df, df);
            return true;
        }
        return false;
    }

    boolean storeWc(Models.Wc wc) {
        if (denotations.containsKey(wc.dn)) {
            wordClasses.put(wc.wc, wc);
            return true;
        }
        return false;
    }

    boolean storeWcex(Models.Wcex wcex) {
        wordClassNames.put(wcex.ex, wcex.tt);
        return true;
    }
}
