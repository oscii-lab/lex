package org.oscii.morph;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.lex.Lexicon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Apply neural morphology rules across multiple languages
 */
public class MorphologyManager {

    private final static Logger logger = LogManager.getLogger(MorphologyManager.class);
    private final static Gson gson = new Gson();

    private final Map<String, Stemmer> stemmers = new HashMap<>();
    private final Lexicon lexicon;

    public MorphologyManager(Lexicon lexicon) {
        this.lexicon = lexicon;
    }

    public void add(String lang, String path) throws FileNotFoundException {
        if (stemmers.containsKey(lang)) {
            logger.error("Repeat language: {}", lang);
        }
        logger.info("Loading morphology rules for {} from {}.", lang, path);
        JsonReader reader = new JsonReader(new FileReader(new File(path)));
        Type listOfRuleScored = new TypeToken<ArrayList<RuleScored>>(){}.getType();
        List<RuleScored> rules = gson.fromJson(reader, listOfRuleScored);
        logger.info("Indexing morphological transformations for {}.", lang);
        Stemmer stemmer = new Stemmer(rules, lexicon, lang);
        logger.info("Done.");
        stemmers.put(lang, stemmer);
    }

    /**
     * Return the stem in the lexicon that is the most likely match to the query.
     *
     * @param query  text (raw)
     * @param language
     * @return stem or original query if no stem is found
     */
    public String getKnownStem(String query, String language) {
        Stemmer stemmer = stemmers.get(language);
        List<String> stems = stemmer.getKnownStems(query);
        if (stems.isEmpty()) {
            return query;
        } else {
            return stems.get(0);
        }
    }
}
