package org.oscii.detokenize;

import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.classify.ClassifierTrainer;
import cc.mallet.classify.MaxEntL1Trainer;
import cc.mallet.pipe.*;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * A classification-based detokenizer.
 *
 * Please make sure that edu.stanford.nlp.* imports are isolated to this file.
 */
public class Detokenizer {

  private static final double L1_WEIGHT = 0.1;
  Classifier classifier; // Weights

  private Detokenizer(Classifier classifier) {
    this.classifier = classifier;
  }

  /*
   * Save to a file.
   */
  public void save(File file) throws IOException {
    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
    oos.writeObject(classifier);
    oos.close();
  }

  /*
   * Load from a file.
   */
  public static Detokenizer load(File file) throws IOException, ClassNotFoundException {
    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
    Classifier classifier = (Classifier) ois.readObject();
    ois.close();
    return new Detokenizer(classifier);
  }

  /*
   * Train a detokenizer for a preprocessor by inspecting its behavior on examples.
   */
  public static Detokenizer train(Preprocessor preprocessor, Iterator<String> examples) {
    Pipe pipe = new SerialPipes(new Pipe[]{
            new FeaturePipe(),
            new TokenSequence2FeatureSequence(),
            new FeatureSequence2FeatureVector(false),
            new Target2Label()
    });
    InstanceList instances = new InstanceList(pipe);
    Iterator<Instance> labeled = toLabeledInstances(examples, preprocessor);
    instances.addThruPipe(labeled);
    ClassifierTrainer trainer = new MaxEntL1Trainer(L1_WEIGHT);
    Classifier classifier = trainer.train(instances);
    return new Detokenizer(classifier);
  }

  public double evaluate(Preprocessor preprocessor, Iterator<String> testExamples) {
    InstanceList instances = new InstanceList(classifier.getInstancePipe());
    Iterator<Instance> labeled = toLabeledInstances(testExamples, preprocessor);
    instances.addThruPipe(labeled);
    return classifier.getAccuracy(instances);
  }

  /*
   * Label a list of tokens that has been tokenized using the preprocessor.
   */
  public List<TokenLabel> predictLabels(List<String> tokens) {
    InstanceList instances = new InstanceList(classifier.getInstancePipe());
    Stream<Integer> range = IntStream.range(0, tokens.size()).boxed();
    Iterator<Instance> unlabeled = range.map(i -> Token.unlabeledInstance(i, tokens)).iterator();
    instances.addThruPipe(unlabeled);
    List<Classification> predictions = classifier.classify(instances);
    List<TokenLabel> labels = predictions.stream().map(Detokenizer::getLabel).collect(toList());
    return labels;
  }

  /*
   * Generate labeled training data from a preprocessor.
   */
  private static Iterator<Instance> toLabeledInstances(Iterator<String> examples, Preprocessor preprocessor) {
    Labeler labeler = new Labeler();
    List<Instance> instances = new ArrayList<>();
    examples.forEachRemaining(ex -> {
      List<String> tokens = tokenize(preprocessor, ex);
      List<TokenLabel> labels = labeler.getLabels(ex, tokens);
      for (int i = 0; i < tokens.size(); i++) {
        TokenLabel label = labels.get(i);
        instances.add(Token.labeledInstance(i, tokens, label));
      }
    });
    return instances.iterator();
  }

  /*
   * Extract the best label from a classifier prediction.
   */
  private static TokenLabel getLabel(Classification c) {
    return (TokenLabel) c.getLabeling().getBestLabel().getEntry();
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
