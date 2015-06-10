package org.oscii.detokenize;

import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.classify.ClassifierTrainer;
import cc.mallet.classify.MaxEntTrainer;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.iterator.ArrayIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import edu.stanford.nlp.mt.process.Preprocessor;

import java.util.ArrayList;
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
    List<Instance> unprocessed = examples.stream().map(e ->
            new Instance(e, null, null, null)).collect(toList());
    List<Pipe> pipes = new ArrayList<>();
    pipes.add(new TokenPipe(preprocessor));
    pipes.add(new FeaturePipe());
    InstanceList instances = new InstanceList(new SerialPipes(pipes));
    instances.addThruPipe(new ArrayIterator(unprocessed));
    ClassifierTrainer trainer = new MaxEntTrainer();
    return new Detokenizer(trainer.train(instances));
  }

  public List<String> inferSeparators(List<String> tokens) {
    Stream<Integer> range = IntStream.range(0, tokens.size()).boxed();
    Iterator<Instance> unlabeled = range.map(i -> Token.unlabeledInstance(i, tokens)).iterator();
    InstanceList instances = new InstanceList(new FeaturePipe());
    instances.addThruPipe(unlabeled);
    List<Classification> predictions = classifier.classify(instances);
    // TODO(denero) Interpreter classifier
    return null;
  }
}
