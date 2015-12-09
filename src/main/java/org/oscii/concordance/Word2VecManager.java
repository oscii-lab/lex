package org.oscii.concordance;

import com.medallia.word2vec.Searcher;
import com.medallia.word2vec.Searcher.Match;
import com.medallia.word2vec.Searcher.UnknownWordException;
import com.medallia.word2vec.Word2VecModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.lex.Lexicon;
import org.oscii.lex.Order;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class that manages a collection of language-specific word2vec models.
 */
public class Word2VecManager {
  private static final int MIN_SEG_LEN = 25; // minimum segment length to activate reduction
  private static final int MIN_TOK_LEN = 5; // minimum token length to denote a candidate
  private static final int MAX_RES_LEN = 50; // maximum length of resulting reduced output
  private Map<String, Searcher> models;

  private final static Logger logger = LogManager.getLogger(Word2VecManager.class);

  public Word2VecManager() {
    this.models = new HashMap<>();
  }
  
  public Word2VecManager(String lang, File file) {
    this();
    this.add(lang, file);
  }
  
  public Word2VecManager(String lang, Word2VecModel word2vec) {
    this();
    this.add(lang, word2vec);
  }

  public boolean add(String lang, Word2VecModel word2vec) {
    if (!supports(lang)) {
      put(lang, word2vec);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Adds a word2vec model from a binary model file for given language.
   *
   * @param lang
   * @param file
   * @return true if model was added successfully, else false
   */
  public boolean add(String lang, File file) {
    try {
      Word2VecModel model = Word2VecModel.fromBinFile(file);
      put(lang, model);
      return true;
    } catch (IOException e) {
      logger.warn(e.getMessage());
    }
    return false;
  }

  private void put(String lang, Word2VecModel model) {
    models.put(lang, model.forSearch());
  }

  public boolean hasModels() {
    return !models.isEmpty();
  }

  public boolean supports(String lang) {
    return models.containsKey(lang);
  }

  /**
   * Checks whether the query is part of model for given language.
   *
   * @param lang
   * @param query
   */
  public boolean containsQuery(String lang, String query) {
    return supports(lang) && models.get(lang).contains(query);
  }

  /**
   * Checks whether the degraded query (e.g. lowercased) is part of
   * model for given language.
   *
   * @param lang
   * @param query
   */
  public boolean containsDegradedQuery(String lang, String query) {
    return supports(lang) && models.get(lang).contains(Lexicon.degrade(query));
  }

  /**
   * Returns the query or degraded query if it is part of the model
   * for a given language.
   *
   * @param lang
   * @param query
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
   * Ranks a list of cocordances by calculating the similarity of each
   * example's mean vector to the mean vector of the context. The
   * result is sorted according to distance/similarity. In order to
   * reduce computation time, the candidates are reduced in size by
   * filtering out short entries (likely stop words) and truncating
   * the result to a maximum number of entries.
   *
   * @param lang
   * @param context
   * @param concordances
   */
  public boolean rankConcordances(String lang, String context, List<SentenceExample> concordances) {
    if (!supports(lang) || context.length() == 0 || concordances.size() == 0) {
      return false;
    }
    Searcher searcher = models.get(lang);
    // retrieve and score context; replaceAll() strips all punctuation
    String[] contextTokens = reduceTokens(context.replaceAll("\\p{P}", "").split("\\s+"), MIN_SEG_LEN, MIN_TOK_LEN, MAX_RES_LEN);
    double[] contextMean = searcher.getMean(contextTokens);
    logger.info("context={} ({})", contextTokens, contextTokens.length);
    // iterate over concordance results
    concordances.forEach(ex -> {
        String[] tokens = reduceTokens(ex.sentence.tokens, MIN_SEG_LEN, MIN_TOK_LEN, MAX_RES_LEN);
        double[] tokensMean = searcher.getMean(tokens);
        logger.debug("tokens=" + tokens);
        logger.debug("means: tokens=[{},{},{},...] context=[{},{},{},...]",
                     tokensMean[0], tokensMean[1], tokensMean[2],
                     contextMean[0], contextMean[1], contextMean[2]);
        double dist = searcher.cosineDistance(tokensMean, contextMean);
        ex.similarity = (Double.isNaN(dist) ? -1.0 : dist);
        logger.debug("distance: {}", ex.similarity);
      });
    // rerank according to word2vec similarities
    Collections.sort(concordances, Order.bySimilarity);
    logger.info("top similarity: {}", concordances.get(0).similarity);
    return true;
  }

  /**
   * Returns the raw word vector (n-dimensional array of doubles) for
   * given query and language.
   *
   * @param lang
   * @param query
   */
  public List<Double> getRawVector(String lang, String query) throws UnknownWordException {
    List<Double> result = new ArrayList<>();
    query = getMatchingQuery(lang, query);
    if (query != null) {
      result = models.get(lang).getRawVector(query).asList();
    }
    return result;
  }

  /**
   * Returns the similarity (cosine distance) between two strings for
   * the given language. If query2 is of length 0, the method expects
   * a '|||'-delimited query pair in query1, e.g. "dog|||cat", or
   * throws a MalformedQueryException else.
   *
   * @param lang
   * @param query1
   * @param query2
   */
  public double getSimilarity(String lang, String query1, String query2) throws UnsupportedLanguageException, MalformedQueryException, UnknownWordException {
    if (!supports(lang)) {
      throw new UnsupportedLanguageException(lang);
    }
    Searcher searcher = models.get(lang);
    if (query2.length() == 0) {
      String[] splitQuery = query1.split("\\|\\|\\|");
      if (splitQuery.length >= 2) {
        query1 = getMatchingQuery(lang, splitQuery[0]);
        query2 = getMatchingQuery(lang, splitQuery[1]);
      } else {
        throw new MalformedQueryException(query1);
      }
    }
    return searcher.cosineDistance(query1, query2);
  }

  public List<Match> getMatches(String lang, String query, int maxCount) throws UnsupportedLanguageException, MalformedQueryException, UnknownWordException {
    if (!supports(lang)) {
      throw new UnsupportedLanguageException(lang);
    }
    query = getMatchingQuery(lang, query);
    if (query == null) {
      throw new MalformedQueryException(query);
    }
    return models.get(lang).getMatches(query, maxCount);
  }

  /**
   * Heuristically reduce the array of tokens to "content" words,
   * limiting the result in size.
   *
   * - Filters short tokens (tokens of length < minTokLength).
   * - Limits the result to maxSegLength entries.
   * - Filters duplicates.
   *
   * @param tokens
   * @param minSegLength
   * @param minTokLength
   * @param maxSegLength
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
   * Exception when a language is not supported.
   */
  public static class UnsupportedLanguageException extends Exception {
    UnsupportedLanguageException(String lang) {
      super(String.format("Unsupported language '%s'", lang));
    }
  }

  /**
   * Exception when a query is malformed.
   */
  public static class MalformedQueryException extends Exception {
    MalformedQueryException(String query) {
      super(String.format("Malformed query '%s'", query));
    }
  }

}
