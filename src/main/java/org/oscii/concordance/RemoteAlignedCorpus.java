package org.oscii.concordance;

import org.apache.commons.collections4.map.LRUMap;
import org.oscii.lex.Expression;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A corpus hosted remotely and accessed through an AlignedCorpusClient.
 */
public class RemoteAlignedCorpus extends AlignedCorpus {

  Map<Expression, Function<Expression, Double>> frequenciesCache;
  AlignedCorpusClient client;

  public RemoteAlignedCorpus(int cacheMax, AlignedCorpusClient client) {
    this.frequenciesCache = new LRUMap<>(cacheMax);
    this.client = client;
  }

  @Override
  public void read(String path, String sourceLanguage, String targetLanguage, int max) throws IOException {
    throw new NotImplementedException();
  }

  @Override
  public Function<Expression, Double> translationFrequencies(Expression source) {

    return null;
  }

  @Override
  public List<AlignedSentence> examples(String query, String source, String target, int max) {
    AlignedCorpusClient.ExamplesResponse response = client.getExamples(query, source, target, max);
    // TODO Convert Examples to AlignedSentences
    return null;
  }
}
