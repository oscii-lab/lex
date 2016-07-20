package org.oscii.morph;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.corpus.Corpus;
import org.oscii.corpus.Tokenizer;
import org.oscii.neural.EmbeddingContainer;
import org.oscii.neural.Word2VecManager;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * Implementation of Unsupervised Morphology Induction Using Word Embeddings
 * by Radu Soricut & Franz Och
 */
public class SubstituteMain {
    private static final Integer DEFAULT_API_PORT = 8091;
    private final static Logger log = LogManager.getLogger(SubstituteMain.class);

    public static void main(String[] args) throws IOException {
        OptionSet options = parse(args);

        String corpusPath = (String) options.valueOf("corpus");
        Corpus corpus = new Corpus(Tokenizer.alphanumericLower);
        corpus.addLines(corpusPath);

        int minVocabCount = (int) options.valueOf("minVocabCount");
        Set<String> vocab = corpus.vocab().stream().filter(w -> corpus.count(w) >= minVocabCount).collect(toSet());
        EmbeddingContainer embeddings = EmbeddingContainer.fromBinFile((File) options.valueOf("embeddings"), vocab);

        Substitutor subber = new Substitutor(embeddings);
        subber.extractAll(corpus, vocab);
        subber.prune((int) options.valueOf("maxSplitsPerPair"), (int) options.valueOf("minPairCount"));

        RuleScored.ScoringParams params = new RuleScored.ScoringParams(
                (int) options.valueOf("maxSupportSize"),
                (int) options.valueOf("maxRankRule"),
                (int) options.valueOf("maxRankTransformation"),
                (double) options.valueOf("minCosineTransformation"),
                (int) options.valueOf("minSizeDirection"));
        subber.scoreRules(params);

        for (RuleScored r : subber.getScored()) {
            log.info(r.toString());
        }

        log.info("Done.");
    }

    /*
   * Parse command-line arguments.
   */
    private static OptionSet parse(String[] args) throws IOException {
        OptionParser parser = new OptionParser();

        // Corpus
        parser.accepts("corpus", "path to corpus file").withRequiredArg();
        parser.accepts("cacheCounts", "whether to cache corpus counts");
        parser.accepts("minVocabCount", "minimum corpus count to include a word type")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        parser.accepts("minPairCount", "minimum word pair count to include a substitution")
                .withRequiredArg().ofType(Integer.class).defaultsTo(2);
        parser.accepts("maxSplitsPerPair", "maximum number of ways a pair can be split")
                .withRequiredArg().ofType(Integer.class).defaultsTo(4);

        // Scoring
        parser.accepts("maxSupportSize", "Number of word pairs to consider when scoring a rule")
        .withRequiredArg().ofType(Integer.class).defaultsTo(1000);
        parser.accepts("maxRankRule", "Max rank of a word pair for a rule to be a hit")
        .withRequiredArg().ofType(Integer.class).defaultsTo(100);
        parser.accepts("maxRankTransformation", "Max rank of a word pair for a transformation to be kept")
                .withRequiredArg().ofType(Integer.class).defaultsTo(30);
        parser.accepts("minCosineTransformation", "Min cosine distance of a word pair for a transformation to be kept")
                .withRequiredArg().ofType(Double.class).defaultsTo(0.5);
        parser.accepts("minSizeDirection", "Min number of word pairs for a direction vector to be kept")
                .withRequiredArg().ofType(Integer.class).defaultsTo(10);

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
