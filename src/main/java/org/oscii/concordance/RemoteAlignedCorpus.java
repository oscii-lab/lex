package org.oscii.concordance;

import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import gnu.trove.THashMap;
import org.apache.commons.collections4.map.LRUMap;
import org.oscii.lex.Expression;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

/**
 * A corpus hosted remotely and accessed through an AlignedCorpusClient.
 */
public class RemoteAlignedCorpus extends AlignedCorpus {

  Map<Expression, Function<Expression, Double>> frequenciesCache;
  AlignedCorpusClient client;
  List<String> languages;

  public RemoteAlignedCorpus(int cacheMax, AlignedCorpusClient client) {
    this.frequenciesCache = new LRUMap<>(cacheMax);
    this.client = client;
  }

  @Override
  public void read(String path, String sourceLanguage, String targetLanguage, int max) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Function<Expression, Double> translationFrequencies(Expression source) {
    Map<String, Map<String, Long>> counts = new THashMap<>();
    languages.forEach(target -> counts.put(target, countAll(source.text, source.language, target)));
    return normalizeByLanguage(counts);
  }

  /**
   * Count translations of text (sampled).
   */
  private Map<String, Long> countAll(String query, String source, String target) {
    Stream<PhrasalRule> rules = client.getRules(query, source, target).stream();
    return rules.collect(groupingBy(PhrasalRule::getTarget, counting()));
  }

  @Override
  public List<AlignedSentence> examples(String query, String source, String target, int max) {
    AlignedCorpusClient.ExamplesResponse response = client.getExamples(query, source, target, max);
    // TODO Convert Examples to AlignedSentences
    return null;
  }
}
