package org.oscii.detokenize;

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
  public static void main(String[] args) throws Exception {
    OptionSet options = parse(args);

    File trainFile = (File) options.valueOf("train");
    InputStream trainStream = new FileInputStream(trainFile);
    if (trainFile.getName().endsWith(".gz")) {
      trainStream = new GZIPInputStream(trainStream);
    }
    BufferedReader buffered = new BufferedReader(new InputStreamReader(trainStream, "utf-8"));
    Iterator<String> trainExamples = buffered.lines().iterator();
    int testSize = (Integer) options.valueOf("test");
    List<String> testExamples = new ArrayList<>(testSize);
    for (int i = 0; i < testSize; i++) {
      testExamples.add(trainExamples.next());
    }

    Preprocessor preprocessor = null;
    boolean cased = true;
    switch ((String) options.valueOf("language")) {
      case "de":
        preprocessor = new GermanPreprocessor(cased); break;
      case "en":
        preprocessor = new EnglishPreprocessor(cased); break;
      case "es":
        preprocessor = new SpanishPreprocessor(cased); break;
      case "fr":
        preprocessor = new FrenchPreprocessor(cased); break;
    }
    Detokenizer detokenizer = Detokenizer.train(preprocessor, trainExamples);

    System.out.println(detokenizer.evaluate(preprocessor, testExamples.iterator()));
  }

  private static OptionSet parse(String[] args) throws IOException {
    OptionParser parser = new OptionParser();
    File deTestData = new File("data/detok/detok_sample_1M_de_raw.clean.gz");
    File deTokenizer = new File("data/detok/detok_sample_1M_de_raw.detokenizer");
    parser.accepts("language").withRequiredArg().defaultsTo("de");
    parser.accepts("train").withRequiredArg().ofType(File.class).defaultsTo(deTestData);
    parser.accepts("out").withRequiredArg().ofType(File.class).defaultsTo(deTokenizer);
    parser.accepts("test").withRequiredArg().ofType(Integer.class).defaultsTo(10000);
    parser.accepts("help").forHelp();

    OptionSet options = parser.parse(args);
    if (options.has("help")) {
      parser.printHelpOn(System.out);
      System.exit(0);
    }
    return options;
  }
}

