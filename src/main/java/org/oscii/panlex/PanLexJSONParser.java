package org.oscii.panlex;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.oscii.lex.Expression;
import org.oscii.lex.Meaning;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * Parse JSON export of PanLex.
 */
public class PanLexJSONParser {
    // Filtered subsets of original PanLex tables, keyed by PanLex ID.
    private final Map<Integer, Models.LanguageVariety> varieties = new HashMap<>();
    private final Map<Integer, Models.Source> sources = new HashMap<>();
    private final Map<Integer, Models.Expression> expressions = new HashMap<>();
    private final Map<Integer, Models.Meaning> meanings = new HashMap<>();
    private final Map<Integer, Models.Denotation> denotations = new HashMap<>();
    private final Map<Integer, Models.Definition> definitions = new HashMap<>();

    // Directory containing exported .json files
    private File dir;
    // Open-source license types
    private final Set<String> allowedLicenseTypes = new HashSet<>(Arrays.asList(
            new String[]{"nr", "cc", "gp", "gl", "gd", "mi"}));

    // TODO(denero) Currently, tags are all ISO-639-1 2-letter language codes.
    // PanLex Language variety ID -> RFC5654 language tag (e.g., "en-GB")
    private final Map<Integer, String> languageTags = new HashMap<>();

    // Logging
    private final static Logger logger = Logger.getLogger(PanLexJSONParser.class.getName());

    static {
        logger.setLevel(Level.INFO);
    }

    public PanLexJSONParser(String jsonDir) {
        dir = new File(jsonDir);
        parse(new File(dir, "ap.json"), new Models.Source(), this::storeSource);
    }

    /*
     * Read structural PanLex data from JSON export for languages.
     */
    public void addLanguages(Collection<String> languages) {
        // Convert 2-letter to 3-letter language codes
        Set<String> three_letter_codes = languages.stream()
                .map(s -> s.length() == 2 ? new Locale(s).getISO3Language() : s)
                .collect(Collectors.toSet());
        File lv = new File(dir, "lv.json");
        parse(lv, new Models.LanguageVariety(), storeLanguage(three_letter_codes));
        populateLanguageTags();
    }

    /*
     * Convert language varieties (PanLex-specific) to RFC5654 language tags.
     */
    public void populateLanguageTags() {
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
     * Read expressions and denotations.
     */
    public void read(Pattern filter) {
        parse(new File(dir, "ex.json"), new Models.Expression(), storeExpression(filter));
        parse(new File(dir, "mn.json"), new Models.Meaning(), this::storeMeaning);
        parse(new File(dir, "dn.json"), new Models.Denotation(), this::storeDenotation);
        parse(new File(dir, "df.json"), new Models.Definition(), this::storeDefinition);
    }

    /*
     * Parses a JSON file of T records and calls process on each.
     */
    private <T> void parse(File path, T record, Predicate<T> process) {
        Class type = record.getClass();
        String name = type.getSimpleName();
        Gson gson = new Gson();
        logger.info(String.format("Parsing %s as %s", path, name));
        int accepted = 0;
        try {
            InputStream in = new FileInputStream(path);
            JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
            reader.beginArray();
            // TODO(denero) remove number limits
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
        logger.info(String.format("Read %d %s records", accepted, name));
    }

    /*
     * Returns a store function that filters by 3-letter language codes.
     */
    private Predicate<Models.LanguageVariety> storeLanguage(Set<String> languages) {
        return (Models.LanguageVariety lv) -> {
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

    private Predicate<Models.Expression> storeExpression(Pattern filter) {
        return (Models.Expression ex) -> {
            if (varieties.containsKey(ex.lv) &&
                    (filter == null || filter.matcher(ex.tt).matches())) {
                expressions.put(ex.ex, ex);
                return true;
            }
            return false;
        };
    }

    private boolean storeMeaning(Models.Meaning mn) {
        if (sources.containsKey(mn.ap)) {
            meanings.put(mn.mn, mn);
            return true;
        }
        return false;
    }

    private boolean storeDenotation(Models.Denotation dn) {
        if (expressions.containsKey(dn.ex) && meanings.containsKey(dn.mn)) {
            denotations.put(dn.dn, dn);
            return true;
        }
        return false;
    }

    private boolean storeDefinition(Models.Definition df) {
        if (varieties.containsKey(df.lv) && meanings.containsKey(df.mn)) {
            definitions.put(df.df, df);
            return true;
        }
        return false;
    }

    /*
     * Processes all Meaning values that can be generated by PanLex data.
     */
    public void yieldTranslations(Consumer<Meaning> process) {
        denotations.values().stream()
                // For denotations with the same meaning...
                .collect(Collectors.groupingBy(dn -> dn.mn)).values().stream()
                .filter(dns -> dns.size() > 1)
                        // Group their expressions by language...
                .map(dns -> dns.stream().map(dn -> expressions.get(dn.ex)))
                .map(exs -> exs.collect(Collectors.groupingBy(ex -> languageTags.get(ex.lv))))
                        // And yield translations for each pair of languages
                .filter(m -> m.keySet().size() > 1)
                .forEach(m -> yieldTranslationsForMeaning(process, m));
    }

    /*
     * Constructs Meaning values for all expressions with the same meaning.
     */
    private void yieldTranslationsForMeaning(
            Consumer<Meaning> process,
            Map<String, List<Models.Expression>> byLanguage) {
        for (String sourceLanguage : byLanguage.keySet()) {
            for (String targetLanguage : byLanguage.keySet()) {
                if (!sourceLanguage.equals(targetLanguage)) {
                    constructTranslations(
                            sourceLanguage,
                            targetLanguage,
                            byLanguage.get(sourceLanguage),
                            byLanguage.get(targetLanguage)).forEach(process);
                }
            }
        }
    }

    /*
     * Constructs translations for each source expression.
     */
    private Stream<Meaning> constructTranslations(
            String sourceLanguage,
            String targetLanguage,
            List<Models.Expression> sourceExpressions,
            List<Models.Expression> targetExpressions) {
        List<Expression> translations = targetExpressions.stream()
                .map(ex -> new Expression(ex.tt, targetLanguage))
                .collect(Collectors.toList());
        return sourceExpressions.stream().map(ex ->
                new Meaning(new Expression(ex.tt, sourceLanguage), translations));
    }
}
