package org.oscii.detokenize;

import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.classify.ClassifierTrainer;
import cc.mallet.classify.MaxEntL1Trainer;
import cc.mallet.pipe.*;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import com.google.common.collect.Iterators;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyIterator;
import static java.util.stream.Collectors.toList;

/**
 * A classification-based detokenizer.
 */
public class Detokenizer {
    Classifier classifier;
    private final static Logger log = LogManager.getLogger(Detokenizer.class);

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
    public static Detokenizer train(double regularization, Iterator<TokenizedCorpus.Entry> examples) {
        Pipe pipe = new SerialPipes(new Pipe[]{
                new FeaturePipe(),
                new TokenSequence2FeatureSequence(),
                new FeatureSequence2FeatureVector(false),
                new Target2Label()
        });
        InstanceList instances = new InstanceList(pipe);
        Iterator<Instance> labeled = toLabeledInstances(examples);
        instances.addThruPipe(labeled);
        ClassifierTrainer trainer = new MaxEntL1Trainer(regularization);
        Classifier classifier = trainer.train(instances);
        return new Detokenizer(classifier);
    }

    /*
     * Return classifier accuracy.
     */
    public double evaluate(Iterator<TokenizedCorpus.Entry> examples) {
        InstanceList instances = new InstanceList(classifier.getInstancePipe());
        Iterator<Instance> labeled = toLabeledInstances(examples);
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
    private static Iterator<Instance> toLabeledInstances(Iterator<TokenizedCorpus.Entry> examples) {
        Labeler labeler = new Labeler();
        return Iterators.concat(Iterators.transform(examples, s -> {
            List<String> tokens = s.getTokens();
            try {
                final List<TokenLabel> labels = labeler.getLabels(s.getRaw(), tokens);
                if (labels.size() != tokens.size()) {
                    throw new Labeler.LabelException("Label count mismatch: " + labels.size() + " not " + tokens.size());
                }
                Stream<Integer> range = IntStream.range(0, tokens.size()).boxed();
                return range.map(i -> Token.labeledInstance(i, tokens, labels.get(i))).iterator();
            } catch (Labeler.LabelException e) {
                log.debug("Skipping: " + e);
                return emptyIterator();
            }
        }));
    }

    /*
     * Extract the best label from a classifier prediction.
     */
    private static TokenLabel getLabel(Classification c) {
        String json = (String) c.getLabeling().getBestLabel().getEntry();
        return TokenLabel.interpret(json);
    }
}
