package org.oscii.lex;

import java.util.Comparator;

/**
 * Ordering on lexical items
 */
public class Order {
  static Comparator<? super Translation> byFrequency = new Comparator<Translation>() {
    @Override
    public int compare(Translation o1, Translation o2) {
      return Double.compare(o2.frequency, o1.frequency);
    }
  };

  static Comparator<? super Meaning> byMaxTranslationFrequency = new Comparator<Meaning>() {
    @Override
    public int compare(Meaning o1, Meaning o2) {
      if (o2.translations.size() == 0) {
        return -1;
      } else if (o1.translations.size() == 0) {
        return 1;
      } else {
        return Double.compare(
                o2.translations.get(0).frequency,
                o1.translations.get(0).frequency);
      }
    }
  };

  static Comparator<? super Expression> byLength = new Comparator<Expression>() {
    @Override
    public int compare(Expression o1, Expression o2) {
      return o1.text.length() - o2.text.length();
    }
  };
}
