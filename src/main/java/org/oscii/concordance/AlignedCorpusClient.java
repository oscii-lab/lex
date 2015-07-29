package org.oscii.concordance;

import java.util.List;

/**
 * A client to fetch aligned sentence pairs over rabbitMQ.
 */
public interface AlignedCorpusClient {

  public ExamplesResponse getExamples(String query, String source, String target, int max);

  List<PhrasalRule> getRules(String query, String source, String target);

  // TODO Match subset of DecoderResponse in Core
  public static class ExamplesResponse {
  }
}
