package org.oscii.concordance;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An extracted phrase pair. Words are always separated by spaces.
 */
public class PhrasalRule {
  List<String> sourceWords;
  List<String> targetWords;
  double score = 0.0;

  public PhrasalRule(String sourcePhrase, String targetPhrase, double score) {
    this.sourceWords = splitPhrase(sourcePhrase);
    this.targetWords = splitPhrase(targetPhrase);
    this.score = score;
  }

  public String getSource() {
    return String.join(" ", sourceWords);
  }

  public String getTarget() {
    return String.join(" ", targetWords);
  }

  public double getScore() {
    return score;
  }

  private static List<String> splitPhrase(String phrase) {
    if (phrase == null) {
      return Collections.emptyList();
    } else {
      return Arrays.asList(phrase.split("\\s+"));
    }
  }
}
