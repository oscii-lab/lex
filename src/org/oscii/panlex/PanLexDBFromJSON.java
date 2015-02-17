package org.oscii.panlex;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class PanLexDBFromJSON implements PanLexDB {

    private final Set<String> languages; // ISO 639-3 language codes (3-letter)
    private Pattern expressionPattern; // Filtering pattern for expressions
    private final Map<Integer, LanguageVariety> languageVarieties; // Indexed by language variety ID
    private final Map<Integer, Expression> expressions; // Indexed by expression ID
    private final Map<Integer, List<Denotation>> denotations; // Indexed by meaning ID
    private final Map<PanLexKey, List<List<String>>> translations; // Translations grouped by meaning

    public PanLexDBFromJSON() {
        languages = new HashSet<String>();
        languageVarieties = new HashMap<Integer, LanguageVariety>();
        expressions = new HashMap<Integer, Expression>();
        denotations = new HashMap<Integer, List<Denotation>>();
        translations = new HashMap<PanLexKey, List<List<String>>>();
        populateFilters(); // TODO(denero) Filters should be constructed programmatically
    }

    // Read PanLex data from JSON export.
    public void read(String jsonDir) {
        File d = new File(jsonDir);
        parse(new File(d, "lv.json"), new LanguageVariety(), this::storeLanguageVariety);
        parse(new File(d, "ex.json"), new Expression(), this::storeExpression);
        parse(new File(d, "dn.json"), new Denotation(), this::storeDenotation);
        indexTranslations();
        languageVarieties.clear();
        expressions.clear();
        denotations.clear();
        // TODO(denero) load definitions
    }

    // Selects languages to support, etc.
    private void populateFilters() {
        languages.add("eng");
        languages.add("spa");
        expressionPattern = Pattern.compile("[a-z]+");
    }

    // Parses a JSON file of T records and call process on each.
    private <T> void parse(File path, T record, Predicate<T> process) {
        Gson gson = new Gson();
        try {
            InputStream in = new FileInputStream(path);
            JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
            reader.beginArray();
            while (reader.hasNext()) {
                record = gson.fromJson(reader, record.getClass());
                process.test(record);
            }
            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Store LanguageVariety records, filtered by language.
    private boolean storeLanguageVariety(LanguageVariety lv) {
        if (languages.contains(lv.lc)) {
            languageVarieties.put(lv.lv, lv);
            return true;
        }
        return false;
    }

    // Store Expression records, filtered by language variety.
    private boolean storeExpression(Expression ex) {
        if (languageVarieties.containsKey(ex.lv) &&
                expressionPattern.matcher(ex.tt).matches()) {
            expressions.put(ex.ex, ex);
            return true;
        }
        return false;
    }

    private boolean storeDenotation(Denotation dn) {
        if (expressions.containsKey(dn.ex)) {
            addToValues(denotations, dn.mn, dn);
            return true;
        }
        return false;
    }

    private static <K, V> void addToValues(Map<K, List<V>> m, K key, V value) {
        if (m.containsKey(key)) {
            m.get(key).add(value);
        } else {
            List<V> s = new ArrayList<V>(1);
            s.add(value);
            m.put(key, s);
        }
    }

    // Add all expressions that share a meaning but not a language to translations.
    private void indexTranslations() {
        for (List<Denotation> dns : denotations.values()) {
            if (dns.size() > 1) {
                // Index by language
                Map<String, List<String>> termsByLanguage = new HashMap<String, List<String>>();
                for (Denotation dn : dns) {
                    Expression ex = expressions.get(dn.ex);
                    String language = languageVarieties.get(ex.lv).lc;
                    addToValues(termsByLanguage, language, ex.tt);
                }
                if (termsByLanguage.keySet().size() > 1) {
                    addTranslations(termsByLanguage);
                }
            }
        }
    }

    private void addTranslations(Map<String, List<String>> termsByLanguage) {
        for (String sourceLanguage : termsByLanguage.keySet()) {
            for (String targetLanguage : termsByLanguage.keySet()) {
                addTranslation(sourceLanguage, targetLanguage, termsByLanguage.get(sourceLanguage), termsByLanguage.get(targetLanguage));
            }
        }
    }

    private void addTranslation(String sourceLanguage, String targetLanguage,
                                List<String> sourceExpressions, List<String> targetExpressions) {
        for (String source : sourceExpressions) {
            PanLexKey key = new PanLexKey(source, sourceLanguage, targetLanguage);
            addToValues(translations, key, targetExpressions);
        }
    }

    @Override
    public PanLexRecord lookup(String query, String sourceLanguage, String targetLanguage) {
        PanLexKey key = new PanLexKey(query, sourceLanguage, targetLanguage);
        if (translations.containsKey(key)) {
            // TODO(denero) The result should contain the correctly capitalized term, not the query.
            return new PanLexRecord(query, sourceLanguage, targetLanguage, translations.get(key));
        } else {
            return null;
        }
    }

    // JSON serialization classes

    // E.g., {"sy":1,"lc":"aar","vc":0,"am":1,"lv":1,"ex":1453510}
    class LanguageVariety {
        String lc; // ISO 639-3 (3-letter) language code
        int lv; // Language variety key
        int ex; // Expression whose text is the language name
        int sy;
        int vc;
        int am;
    }

    // E.g., {"td":"𠁥","tt":"𠁥","lv":1836,"ex":19202960}
    class Expression {
        int lv; // Language variety
        int ex; // Key
        String tt; // Text
        String td; // Degraded text
    }

    // E.g., {"mn":1,"ex":14,"dn":4}
    class Denotation {
        int dn;
        int mn;
        int ex;
    }

    class PanLexKey {
        String query;
        String sourceLanguage;
        String targetLanguage;

        public PanLexKey(String query, String sourceLanguage, String targetLanguage) {
            this.query = query.toLowerCase();
            this.sourceLanguage = sourceLanguage.toLowerCase();
            this.targetLanguage = targetLanguage.toLowerCase();
        }
    }
}
