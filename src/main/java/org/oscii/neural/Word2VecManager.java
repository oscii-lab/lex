package org.oscii.neural;

import no.uib.cipr.matrix.Vector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.concordance.SentenceExample;
import org.oscii.lex.Lexicon;
import org.oscii.lex.Order;
import org.oscii.math.VectorMath;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages a collection of language-specific word2vec models.
 *
 * @author Sasa Hasan
 * @author Spence Green
 */
public class Word2VecManager {

    private final static Logger logger = LogManager.getLogger(Word2VecManager.class);

    private static final int MIN_SEG_LEN = 25; // minimum segment length to activate reduction
    private static final int MIN_TOK_LEN = 5; // minimum token length to denote a candidate
    private static final int MAX_RES_LEN = 50; // maximum length of resulting reduced output

    private final Map<String, EmbeddingContainer> models;

    /**
     * Constructor.
     */
    public Word2VecManager() {
        this.models = new HashMap<>();
    }

    /**
     * Adds a word2vec model from a binary model file for given language.
     *
     * @return true if model was added successfully, else false
     */
    public boolean add(String lang, File file) throws IOException {
        return add(lang, file, null);
    }

    public boolean add(String lang, File file, Set<String> vocab) throws IOException {
        logger.info("Loading {} embeddings from {}", lang, file);
        EmbeddingContainer model = EmbeddingContainer.fromBinFile(file, vocab);
        put(lang, model);
        return true;
    }

    private void put(String lang, EmbeddingContainer model) {
        models.put(lang, model);
    }

    public boolean hasModels() {
        return !models.isEmpty();
    }

    public boolean supports(String lang) {
        return models.containsKey(lang);
    }

    /**
     * Checks whether the query is part of model for given language.
     */
    public boolean containsQuery(String lang, String query) {
        return supports(lang) && models.get(lang).contains(query);
    }

    /**
     * Checks whether the degraded query (e.g. lowercased) is part of
     * model for given language.
     */
    public boolean containsDegradedQuery(String lang, String query) {
        return supports(lang) && models.get(lang).contains(Lexicon.degrade(query));
    }

    /**
     * Returns the query or degraded query if it is part of the model
     * for a given language. Throws UnknownWordException if not.
     */
    public String getMatchingQuery(String lang, String query) {
        if (containsQuery(lang, query)) {
            return query;

        } else if (containsDegradedQuery(lang, query)) {
            return Lexicon.degrade(query);

        } else {
            return null;
        }
    }

    /**
     * Ranks a list of concordance entries by calculating the similarity of each
     * example's mean vector to the mean vector of the context. The
     * result is sorted according to distance/similarity. In order to
     * reduce computation time, the candidates are reduced in size by
     * filtering out short entries (likely stop words) and truncating
     * the result to a maximum number of entries.
     */
    public boolean rankConcordances(String lang, String context, List<SentenceExample> concordances, int memoryId) {
        if (!supports(lang) || context.length() == 0 || concordances.size() == 0) {
            return false;
        }
        final EmbeddingContainer model = models.get(lang);
        // retrieve and score context; replaceAll() strips all punctuation
        String[] contextTokens = reduceTokens(context.replaceAll("\\p{P}", "").split("\\s+"), MIN_SEG_LEN, MIN_TOK_LEN, MAX_RES_LEN);
        Vector contextMean = model.getMean(contextTokens);
        logger.info("context={} ({})", contextTokens, contextTokens.length);
        // iterate over concordance results
        concordances.forEach(ex -> {
            String[] tokens = reduceTokens(ex.sentence.tokens, MIN_SEG_LEN, MIN_TOK_LEN, MAX_RES_LEN);
            Vector tokensMean = model.getMean(tokens);
            logger.debug("means: tokens=[{},{},{},...] context=[{},{},{},...]",
                    tokensMean.get(0), tokensMean.get(1), tokensMean.get(2),
                    contextMean.get(0), contextMean.get(1), contextMean.get(2));
            try {
                double sim = VectorMath.cosineSimilarity(tokensMean, contextMean);
                if (Double.isNaN(sim)) {
                    sim = -2.0; // Give it a low score.
                } else if (ex.memoryId == memoryId) {
                    // personal TM match in same project: place on top by adding +2.0 (note: similarity is not cosine sim any more)
                    sim += 2.0;
                } else if (ex.memoryId > 0) {
                    // personal TM match: add +1.0 so entries appear on top but below same-project matches (also note: see above)
                    sim += 1.0;
                }
                ex.similarity = sim;
            } catch (Exception e) {
                logger.warn("Zero vector for concordance ranking");
                ex.similarity = -2.0; // Give it a low score.
            }
            logger.debug("distance: {}", ex.similarity);
        });
        // rerank according to word2vec similarities
        Collections.sort(concordances, Order.bySimilarity);
        logger.info("top similarity: {}", concordances.get(0).similarity);
        return true;
    }

    /**
     * Returns the averaged raw word vector (n-dimensional array of
     * doubles) for given query and language. Applies tokenization
     * through punctuation removal and reducing into a bag of words.
     */
    public Vector getRawVector(String lang, String query) throws UnsupportedLanguageException {
        if (!supports(lang)) throw new UnsupportedLanguageException(lang);
        String[] bagOfWords = getBagOfWords(query.replaceAll("\\p{P}", "").split("\\s+"));
        logger.debug("BOW: {}", Arrays.toString(bagOfWords));
        return models.get(lang).getMean(bagOfWords);
    }

    /**
     * Get the mean vector for a query.
     */
    public Vector getMeanVector(String lang, String[] query) throws UnsupportedLanguageException {
        if (!supports(lang)) throw new UnsupportedLanguageException(lang);
        return models.get(lang).getMean(query);
    }

    /**
     * Returns the similarity (cosine distance) between two strings for
     * the given language. If query2 is of length 0, the method expects
     * a '|||'-delimited query pair in query1, e.g. "dog|||cat", or
     * throws a MalformedQueryException else. The query is degraded if
     * needed.
     */
    public double getSimilarity(String lang, String query1, String query2) throws
            UnsupportedLanguageException, MalformedQueryException {
        if (!supports(lang)) throw new UnsupportedLanguageException(lang);
        if (query2.length() == 0) {
            String[] splitQuery = query1.trim().split("\\|\\|\\|");
            if (splitQuery.length == 2) {
                query1 = getMatchingQuery(lang, splitQuery[0].trim());
                query2 = getMatchingQuery(lang, splitQuery[1].trim());
            } else {
                throw new MalformedQueryException(query1);
            }
        }
        if (query1 == null || query2 == null) return -1.0;
        Vector v1 = getRawVector(lang, query1);
        Vector v2 = getRawVector(lang, query2);
        return VectorMath.cosineSimilarity(v1, v2);
    }

    /**
     * The K nearest words to a query vector in a language.
     */
    public List<String> nearestNeighbors(String lang, float[] query, int k) {
        // TODO
        return null;
    }

    /**
     * Heuristically reduce the array of tokens to "content" words,
     * limiting the result in size.
     * <p>
     * - Filters short tokens (tokens of length < minTokLength).
     * - Limits the result to maxSegLength entries.
     * - Filters duplicates.
     */
    public static String[] reduceTokens(String[] tokens, int minSegLength, int minTokLength, int maxSegLength) {
        if (tokens.length < minSegLength) {
            return tokens.clone();
        }
        Set<String> tokset = new HashSet<>();
        for (int i = 0; i < tokens.length; ++i) {
            if (tokens[i].length() >= minTokLength) {
                tokset.add(tokens[i]);
            }
            if (tokset.size() >= maxSegLength) {
                break;
            }
        }
        if (tokset.size() == 0) {
            return tokens.clone();
        } else {
            return tokset.toArray(new String[tokset.size()]);
        }
    }

    /**
     * Returns bag-of-words for given token sequence.
     */
    public static String[] getBagOfWords(String[] tokens) {
        Set<String> tokset = new HashSet<>();
        for (String token : tokens) {
            tokset.add(token);
        }
        return tokset.toArray(new String[tokset.size()]);
    }

    public List<String> getVocabulary(String lang) {
        return models.get(lang).vocab();
    }

    /**
     * Exception when a language is not supported.
     */
    public static class UnsupportedLanguageException extends Exception {
        private static final long serialVersionUID = -5764353544971858767L;

        public UnsupportedLanguageException(String lang) {
            super(String.format("Unsupported language '%s'", lang));
        }
    }

    /**
     * Exception when a query is malformed.
     */
    public static class MalformedQueryException extends Exception {
        private static final long serialVersionUID = 8933002861674016549L;

        public MalformedQueryException(String query) {
            super(String.format("Malformed query '%s'", query));
        }
    }
}
