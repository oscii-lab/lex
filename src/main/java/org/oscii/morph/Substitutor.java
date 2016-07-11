package org.oscii.morph;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.oscii.corpus.Corpus;
import org.oscii.neural.Word2VecManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * A collection of substitution rules.
 */
public class Substitutor {

  private final Word2VecManager embeddings;
  private Map<Substitution, List<Transformation>> substitutions;
  Interner<Substitution> subInterner;

  private static class Segmentation {
    private final String word;
    private final String stem;
    private final String affix;

    private Segmentation(String stem, String affix, String word) {
      this.stem = stem;
      this.affix = affix;
      this.word = word;
    }
  }

  private static class Transformation {
    private final String input;
    private final String output;
    private final Substitution sub;

    public Transformation(String input, String output, Substitution sub) {
      this.input = input;
      this.output = output;
      this.sub = sub;
    }
  }

  public Substitutor(Word2VecManager embeddings) {
    this.embeddings = embeddings;
    this.subInterner = Interners.newWeakInterner();
  }

  /**
   * Extract all substitutions from the vocabulary of the corpus.
   *
   * @param corpus a monolingual corpus.
   */
  public void extractAll(Corpus corpus, int minVocabCount, int minPairCount) {
    List<String> vocab = corpus.vocab().stream().filter(w -> corpus.count(w) >= minVocabCount).collect(toList());
    Stream<Transformation> p = IndexByStem(vocab, Substitutor::getPrefix).values()
            .parallelStream().flatMap(x -> transformations(x, Substitution.Prefix::new));
    Stream<Transformation> s = IndexByStem(vocab, Substitutor::getSuffix).values()
            .parallelStream().flatMap(x -> transformations(x, Substitution.Suffix::new));
    substitutions = Stream.concat(p, s).collect(groupingBy(t -> t.sub))
            .entrySet().parallelStream().filter(kv -> kv.getValue().size() >= minPairCount)
            .collect(toMap(kv -> kv.getKey(), kv -> kv.getValue()));
  }

  // Takes lists of segmentations with a common stem.
  private Stream<Transformation> transformations(List<Segmentation> segs, BiFunction<String, String, ? extends Substitution> newSub) {
    final int n = segs.size();
    if (n < 2) return Stream.empty();
    List<Transformation> ts = new ArrayList<>(n * (n - 1));
    for (Segmentation input : segs) {
      for (Segmentation output : segs) {
        if (input == output) continue;
        Substitution sub = newSub.apply(input.affix, output.affix);
        sub = subInterner.intern(sub);
        ts.add(new Transformation(input.word, output.word, sub));
      }
    }
    return ts.stream();
  }

  // Index all segmented words by the remaining stem.
  Map<String, List<Segmentation>> IndexByStem(Collection<String> vocab, BiFunction<String, Integer, Segmentation> f) {
    return vocab.parallelStream().flatMap(w -> {
      int n = w.length();
      if (n == 0) return Stream.empty();
      int affixMax = Math.min(6, n - 1);
      List<Segmentation> entries = new ArrayList<>(affixMax);
      for (int k = 0; k <= affixMax; k++) {
        entries.add(f.apply(w, k));
      }
      return entries.stream();
    }).collect(groupingBy(p -> p.stem));
  }

  private static Segmentation getPrefix(String word, int k) {
    String prefix = word.substring(0, k);
    String stem = word.substring(k, word.length());
    return new Segmentation(stem, prefix, word);
  }

  private static Segmentation getSuffix(String word, int k) {
    final int n = word.length();
    String suffix = word.substring(n - k, n);
    String stem = word.substring(0, n - k);
    return new Segmentation(stem, suffix, word);
  }
}
