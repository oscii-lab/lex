package org.oscii.concordance;

/**
 * Aligned sentence containing an aligned span.
 */
public class SentenceExample {

  final public AlignedSentence sentence;
  final public int sourceStart, sourceLength, targetStart, targetLength;
  public double similarity; // Word2Vec similarity to request.context
  public int memoryId; // indicates a foreground (<=0) vs. background TM match (-1)

  public SentenceExample(AlignedSentence sentence, int sourceStart, int sourceLength, int targetStart, int targetLength, int memoryId) {
    this.sentence = sentence;
    this.sourceStart = sourceStart;
    this.sourceLength = sourceLength;
    this.targetStart = targetStart;
    this.targetLength = targetLength;
    this.similarity = 0.0;
    this.memoryId = memoryId;
  }

  public SentenceExample(AlignedSentence sentence, int sourceStart, int sourceLength, int targetStart, int targetLength, int memoryId, double similarity) {
    this(sentence, sourceStart, sourceLength, targetStart, targetLength, memoryId);
    this.similarity = similarity;
  }

  public static SentenceExample create(AlignedSentence s, int sourceStart, int sourceLength, int memoryId) {
    int targetMin = s.aligned.tokens.length;
    int targetMax = -1;
    for (int i = sourceStart; i < sourceStart + sourceLength; i++) {
      int[] a = s.getAlignment()[i];
      for (int j : a) {
        if (j < targetMin) targetMin = j;
        if (j > targetMax) targetMax = j;
      }
    }
    if (targetMax == -1) {
      targetMin = 0;
    }
    return new SentenceExample(s, sourceStart, sourceLength, targetMin, targetMax - targetMin + 1, memoryId);
  }
}
