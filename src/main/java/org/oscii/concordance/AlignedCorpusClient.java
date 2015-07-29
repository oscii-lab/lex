package org.oscii.concordance;

/**
 * A client to fetch aligned sentence pairs over rabbitMQ.
 */
public interface AlignedCorpusClient {

  public ExamplesResponse getExamples(String query, String source, String target, int max);

  // TODO Match subset of DecoderResponse in Core
  public static class ExamplesResponse {
  }
}
