package org.oscii.concordance;

import gnu.trove.THashMap;
import org.apache.commons.collections4.map.LRUMap;
import org.oscii.lex.Expression;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

/**
 * A template for a remotely hosted corpus.
 * Override methods to supply a connection.
 */
public abstract class RemoteAlignedCorpus extends AlignedCorpus {

  Map<Expression, Function<Expression, Double>> frequenciesCache;
  List<String> languages;

  public RemoteAlignedCorpus(int cacheMax, List<String> languages) {
    this.frequenciesCache = new LRUMap<>(cacheMax);
    this.languages = languages;
  }

  @Override
  public void read(String path, String sourceLanguage, String targetLanguage, int max) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Function<Expression, Double> translationFrequencies(Expression source) {
    if (frequenciesCache.containsKey(source)) {
      return frequenciesCache.get(source);
    }
    Map<String, Map<String, Long>> counts = new THashMap<>();
    languages.forEach(target -> counts.put(target, countAll(source.text, source.language, target)));
    Function<Expression, Double> frequencies = normalizeByLanguage(counts);
    frequenciesCache.put(source, frequencies);
    return frequencies;
  }

  /**
   * Count translations of text (sampled).
   */
  private Map<String, Long> countAll(String query, String source, String target) {
    Stream<PhrasalRule> rules = getRules(query, source, target).stream();
    return rules.collect(groupingBy(PhrasalRule::getTarget, counting()));
  }

  /**
   * Return a sequence of extracted rules to be counted.
   *
   * @param query raw source language phrase
   * @param source source language code
   * @param target target language code
   * @return
   */
  public abstract List<PhrasalRule> getRules(String query, String source, String target);


}
