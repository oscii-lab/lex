package org.oscii.morph;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.corpus.Corpus;
import org.oscii.corpus.Tokenizer;
import org.oscii.neural.Word2VecManager;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Implementation of Unsupervised Morphology Induction Using Word Embeddings
 * by Radu Soricut & Franz Och
 */
public class SubstituteMain {
  private static final Integer DEFAULT_API_PORT = 8091;
  private final static Logger log = LogManager.getLogger(SubstituteMain.class);
  private static Word2VecManager embeddings;

  public static void main(String[] args) throws IOException {
    OptionSet options = parse(args);

    String corpusPath = (String) options.valueOf("corpus");
    log.info("Loading corpus from {}", corpusPath);
    Corpus corpus = new Corpus(Tokenizer.alphanumeric);
    corpus.addLines(corpusPath);

    embeddings = new Word2VecManager();
    // embeddings.add((String) options.valueOf("language"), (File) options.valueOf("embeddings"));

    log.info("Extracting substitution rules");
    Substitutor subber = new Substitutor(embeddings);
    subber.extractAll(corpus, (int) options.valueOf("minVocabCount"), (int) options.valueOf("minPairCount"));

    log.info("Computing substitution vectors");
  }

  /*
 * Parse command-line arguments.
 */
  private static OptionSet parse(String[] args) throws IOException {
    OptionParser parser = new OptionParser();

    // Corpus
    parser.accepts("corpus", "path to corpus file").withRequiredArg();
    parser.accepts("cacheCounts", "whether to cache corpus counts");
    parser.accepts("minVocabCount", "minimum corpus count to include a word type").withRequiredArg().ofType(Integer.class).defaultsTo(1);
    parser.accepts("minPairCount", "minimum word pair count to include a substitution").withRequiredArg().ofType(Integer.class).defaultsTo(2);

    // HTTP Rest API
    parser.accepts("port", "API port").withRequiredArg().ofType(Integer.class).defaultsTo(DEFAULT_API_PORT);

    // Word2Vec
    parser.accepts("embeddings", "binary Word2Vec model file").withRequiredArg().ofType(File.class);
    parser.accepts("language", "language for Word2Vec embeddings").withRequiredArg();

    OptionSet options = null;
    parser.acceptsAll(Arrays.asList("h", "help"), "show help").forHelp();

    boolean printHelp = false;
    try {
      options = parser.parse(args);
      if (options.has("help")) {
        printHelp = true;
      }
    } catch (Exception e) {
      printHelp = true;
    }
    if (printHelp) {
      parser.printHelpOn(System.out);
      options = null;
      System.exit(0);
    }
    return options;
  }
}
