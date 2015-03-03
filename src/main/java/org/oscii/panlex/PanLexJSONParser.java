package org.oscii.panlex;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.lex.Definition;
import org.oscii.lex.Expression;
import org.oscii.lex.Meaning;
import org.oscii.lex.Translation;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
 * Parse JSON export of PanLex.
 */
public class PanLexJSONParser {
    // Filtered subsets of original PanLex tables, keyed by PanLex ID.
    private final Map<Integer, Models.Lv> varieties = new HashMap<>();
    private final Map<Integer, Models.Source> sources = new HashMap<>();
    private final Map<Integer, Models.Ex> expressions = new HashMap<>();
    private final Map<Integer, Models.Mn> meanings = new HashMap<>();
    private final Map<Integer, Models.Dn> denotations = new HashMap<>();
    private final Map<Integer, Models.Df> definitions = new HashMap<>();
    private final Map<Integer, Models.Wc> wordClasses = new HashMap<>();
    private final Map<Integer, String> wordClassNames = new HashMap<>();

    // Directory containing exported .json files
    private File dir;

    // Open-source license types
    private final Set<String> allowedLicenseTypes = new HashSet<>(Arrays.asList(
            new String[]{"nr", "cc", "gp", "gl", "gd", "mi"}));

    // TODO(denero) Currently, tags are all ISO-639-1 2-letter language codes.
    // PanLex Language variety ID -> RFC5654 language tag (e.g., "en-GB")
    private final Map<Integer, String> languageTags = new HashMap<>();

    // Indices
    private Map<Integer, List<Models.Wc>> wordClassByExpression;
    private Map<Integer, List<Models.Df>> definitionByMeaning;

    private final static Logger log = LogManager.getLogger(PanLexJSONParser.class);

    public PanLexJSONParser(String jsonDir) {
        dir = new File(jsonDir);
        parse(new File(dir, "ap.json"), new Models.Source(), this::storeSource);
    }

    /*
     * Reads structural PanLex data from JSON export for languages.
     */
    public void addLanguages(Collection<String> languages) {
        // Convert 2-letter to 3-letter language codes
        Set<String> three_letter_codes = languages.stream()
                .map(s -> s.length() == 2 ? new Locale(s).getISO3Language() : s)
                .collect(Collectors.toSet());
        File lv = new File(dir, "lv.json");
        parse(lv, new Models.Lv(), storeLanguage(three_letter_codes));
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
        parse(new File(dir, "ex.json"), new Models.Ex(), storeEx(filter));
        parse(new File(dir, "mn.json"), new Models.Mn(), this::storeMn);
        parse(new File(dir, "dn.json"), new Models.Dn(), this::storeDn);
        parse(new File(dir, "df.json"), new Models.Df(), this::storeDf);
        parse(new File(dir, "wc.json"), new Models.Wc(), this::storeWc);
        parse(new File(dir, "wcex.json"), new Models.Wcex(), this::storeWcex);

        log.info("Indexing word classes");
        wordClassByExpression = wordClasses.values().stream()
                .collect(Collectors.groupingBy(wc -> wc.ex));
        log.info("Indexing definitions");
        definitionByMeaning = definitions.values().stream()
                .collect(Collectors.groupingBy(df -> df.mn));
    }

    /*
     * Parses a JSON file of T records and calls process on each.
     */
    private <T> void parse(File path, T record, Predicate<T> process) {
        Class type = record.getClass();
        String name = type.getSimpleName();
        Gson gson = new Gson();
        log.info(String.format("Parsing %s from %s", name, path));
        int accepted = 0;
        try {
            InputStream in = new FileInputStream(path);
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
     * Processes all Meaning values that can be generated by PanLex data.
     */
    public void forEachMeaning(Consumer<Meaning> process) {
        // For all words with a shared meaning, process Meanings by language.
        denotations.values().stream()
                .collect(Collectors.groupingBy(dn -> dn.mn)).values().stream()
                .map(dns -> dns.stream().map(this::createMeaning))
                .map(exs -> exs.collect(Collectors.groupingBy(
                        m -> m.expression.languageTag)))
                .forEach(g -> processMeanings(process, g));
    }

    /*
     * Create a meaning for a denotation containing in-language definitions.
     */
    private Meaning createMeaning(Models.Dn dn) {
        Models.Ex ex = expressions.get(dn.ex);
        String languageTag = languageTags.get(ex.lv);
        Expression expression = new Expression(ex.tt, languageTag);
        Meaning meaning = new Meaning(expression);
        for (Models.Wc wc : wordClassByExpression.getOrDefault(dn.ex, Collections.emptyList())) {
            meaning.pos.add(wordClassNames.get(wc.ex));
        }
        for (Models.Df df : definitionByMeaning.getOrDefault(dn.mn, Collections.emptyList())) {
            if (df.lv == ex.lv) {
                meaning.definitions.add(new Definition(df.tt, languageTag));
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
                            source.translations.add(new Translation(target.expression, 0));
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

    // JSON parsing predicates

    private Predicate<Models.Lv> storeLanguage(Set<String> languages) {
        return (Models.Lv lv) -> {
            if (languages.contains(lv.lc)) {
                varieties.put(lv.lv, lv);
                return true;
            }
            return false;
        };
    }

    private boolean storeSource(Models.Source ap) {
        if (allowedLicenseTypes.contains(ap.li)) {
            sources.put(ap.ap, ap);
            return true;
        }
        return false;
    }

    private Predicate<Models.Ex> storeEx(Pattern filter) {
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

    private boolean storeMn(Models.Mn mn) {
        if (sources.containsKey(mn.ap)) {
            meanings.put(mn.mn, mn);
            return true;
        }
        return false;
    }

    private boolean storeDn(Models.Dn dn) {
        if (expressions.containsKey(dn.ex) && meanings.containsKey(dn.mn)) {
            denotations.put(dn.dn, dn);
            return true;
        }
        return false;
    }

    private boolean storeDf(Models.Df df) {
        if (varieties.containsKey(df.lv) && meanings.containsKey(df.mn)) {
            definitions.put(df.df, df);
            return true;
        }
        return false;
    }

    private boolean storeWc(Models.Wc wc) {
        if (denotations.containsKey(wc.dn)) {
            wordClasses.put(wc.wc, wc);
            return true;
        }
        return false;
    }

    private boolean storeWcex(Models.Wcex wcex) {
        wordClassNames.put(wcex.ex, wcex.tt);
        return true;
    }
}
