package org.oscii.detokenize;

import com.google.common.collect.Iterators;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.de.GermanPreprocessor;
import edu.stanford.nlp.mt.process.en.EnglishPreprocessor;
import edu.stanford.nlp.mt.process.es.SpanishPreprocessor;
import edu.stanford.nlp.mt.process.fr.FrenchPreprocessor;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Command-line utility to train a detokenizer.
 */
public class TrainDetokenizer {
  public static void main(String[] args) throws IOException {
    OptionSet options = parse(args);

    File trainFile = (File) options.valueOf("data");
    InputStream trainStream = new FileInputStream(trainFile);
    if (trainFile.getName().endsWith(".gz")) {
      trainStream = new GZIPInputStream(trainStream);
    }
    BufferedReader buffered = new BufferedReader(new InputStreamReader(trainStream, "utf-8"));
    Iterator<String> trainExamples = buffered.lines().iterator();

    int testSize = (Integer) options.valueOf("testsize");
    List<String> testExamples = new ArrayList<>(testSize);
    for (int i = 0; i < testSize; i++) {
      testExamples.add(trainExamples.next());
    }

    int trainSize = (Integer) options.valueOf("trainsize");
    trainExamples = Iterators.limit(trainExamples, trainSize);

    Preprocessor preprocessor = getPreprocessor(options);
    double regularization = (double) options.valueOf("regularization");
    Detokenizer detokenizer = Detokenizer.train(preprocessor, regularization, trainExamples);

    System.out.println("Test accuracy: " + detokenizer.evaluate(preprocessor, testExamples.iterator()));
    if (options.has("errors")) {
      testExamples.forEach(ex -> {
        List<String> tokens = Detokenizer.tokenize(preprocessor, ex);
        String roundTrip = TokenLabel.render(tokens, detokenizer.predictLabels(tokens));
        if (!ex.equals(roundTrip)) {
          System.out.println("Original:  " + ex);
          System.out.println("Detoken'd: " + roundTrip);
        }
      });
    }

    File outFile = (File) options.valueOf("out");
    detokenizer.save(outFile);
  }

  private static Preprocessor getPreprocessor(OptionSet options) {
    Preprocessor preprocessor = null;
    boolean cased = true;
    switch ((String) options.valueOf("language")) {
      case "de":
        preprocessor = new GermanPreprocessor(cased);
        break;
      case "en":
        preprocessor = new EnglishPreprocessor(cased);
        break;
      case "es":
        preprocessor = new SpanishPreprocessor(cased);
        break;
      case "fr":
        preprocessor = new FrenchPreprocessor(cased);
        break;
    }
    return preprocessor;
  }

  private static OptionSet parse(String[] args) throws IOException {
    OptionParser parser = new OptionParser();
    File deTestData = new File("data/detok/detok_sample_1M_de_raw.clean.gz");
    File deTokenizer = new File("data/detok/detok_sample_1M_de_raw.detokenizer");
    parser.accepts("language").withRequiredArg().defaultsTo("de");
    parser.accepts("data").withRequiredArg().ofType(File.class).defaultsTo(deTestData);
    parser.accepts("out").withRequiredArg().ofType(File.class).defaultsTo(deTokenizer);
    parser.accepts("trainsize").withRequiredArg().ofType(Integer.class).defaultsTo(100000);
    parser.accepts("testsize").withRequiredArg().ofType(Integer.class).defaultsTo(10000);
    parser.accepts("regularization").withRequiredArg().ofType(Double.class).defaultsTo(10.0);
    parser.accepts("errors");
    parser.accepts("help").forHelp();

    OptionSet options = parser.parse(args);
    if (options.has("help")) {
      parser.printHelpOn(System.out);
      System.exit(0);
    }
    return options;
  }
}

