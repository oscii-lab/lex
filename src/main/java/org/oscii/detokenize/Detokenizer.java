package org.oscii.detokenize;

import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.classify.ClassifierTrainer;
import cc.mallet.classify.MaxEntTrainer;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.Target2Label;
import cc.mallet.pipe.iterator.ArrayIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * A classification-based detokenizer.
 */
public class Detokenizer {

  Classifier classifier;

  public Detokenizer(Classifier classifier) {
    this.classifier = classifier;
  }

  public static Detokenizer train(Preprocessor preprocessor, List<String> examples) {
    InstanceList instances = new InstanceList(new SerialPipes(Arrays.asList(
            new TokenPipe(preprocessor),
            new FeaturePipe(),
            new Target2Label()
    )));
    instances.addThruPipe(new ArrayIterator(examples));
    ClassifierTrainer trainer = new MaxEntTrainer();
    return new Detokenizer(trainer.train(instances));
  }

  public List<TokenLabel> label(List<String> tokens) {
    Stream<Integer> range = IntStream.range(0, tokens.size()).boxed();
    Iterator<Instance> unlabeled = range.map(i -> Token.unlabeledInstance(i, tokens)).iterator();
    InstanceList instances = new InstanceList(new FeaturePipe());
    instances.addThruPipe(unlabeled);
    List<Classification> predictions = classifier.classify(instances);
    return predictions.stream().map(c -> (TokenLabel) c.getLabeling().getBestLabel().getEntry()).collect(toList());
  }

  /*
   * Convenience method for converting from CoreNLP proprietary data structures.
   */
  public static List<String> tokenize(Preprocessor preprocessor, String sentence) {
    Sequence<IString> tokenSequence = preprocessor.process(sentence);
    List<String> tokens = new ArrayList<>(tokenSequence.size());
    tokenSequence.forEach(t -> tokens.add(t.toString()));
    return tokens;
  }
}
