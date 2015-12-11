package org.oscii.lex;

import org.oscii.concordance.PhrasalRule;
import org.oscii.concordance.SentenceExample;

import java.util.Comparator;

/**
 * Ordering on lexical items
 */
public class Order {
    public static final Comparator<? super Translation> byFrequency = new Comparator<Translation>() {
        @Override
        public int compare(Translation o1, Translation o2) {
            return Double.compare(o2.frequency, o1.frequency);
        }
    };

    public static final Comparator<? super Meaning> byMaxTranslationFrequency = new Comparator<Meaning>() {
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

    public static final Comparator<? super Expression> byLength = new Comparator<Expression>() {
        @Override
        public int compare(Expression o1, Expression o2) {
            return o1.text.length() - o2.text.length();
        }
    };

    public static final Comparator<? super PhrasalRule> byScore = new Comparator<PhrasalRule>() {
        @Override
        public int compare(PhrasalRule x, PhrasalRule y) {
            if (x != null && y != null) {
                return Double.compare(y.getScore(), x.getScore());
            } else {
                return 0;
            }
        }
    };

    public static final Comparator<? super SentenceExample> bySimilarity =
            (o1, o2) -> Double.compare(o2.similarity, o1.similarity);

}
