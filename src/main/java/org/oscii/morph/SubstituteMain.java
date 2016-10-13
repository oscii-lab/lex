package org.oscii.morph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.corpus.Corpus;
import org.oscii.corpus.Tokenizer;
import org.oscii.neural.EmbeddingContainer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
        int maxLines = (Integer) options.valueOf("corpusMaxLines");
        Corpus corpus = new Corpus(Tokenizer.alphanumericLower);
        corpus.addLines(corpusPath, maxLines);

        int minVocabCount = (int) options.valueOf("minVocabCount");
        Set<String> vocab = corpus.vocab().stream().filter(w -> corpus.count(w) >= minVocabCount).collect(toSet());
        log.info("Loading embeddings for {} word types", vocab.size());
        EmbeddingContainer embeddings = EmbeddingContainer.fromBinFile((File) options.valueOf("embeddings"), vocab, true);
        vocab = vocab.parallelStream().filter(embeddings::contains).collect(toSet());
        log.info("Found embeddings for {} word types", vocab.size());

        Substitutor subber = new Substitutor(embeddings);
        int minStemLength = (int) options.valueOf("minStemLength");
        int maxStemCardinality = (int) options.valueOf("maxStemCardinality");
        subber.extractAll(corpus, vocab, minStemLength, maxStemCardinality);
        subber.prune((int) options.valueOf("maxSplitsPerPair"), (int) options.valueOf("minPairCount"));

        RuleScored.ScoringParams params = new RuleScored.ScoringParams();
        params.maxSupportSize = (int) options.valueOf("maxSupportSize");
        params.maxRankRule = (int) options.valueOf("maxRankRule");
        params.maxRankTransformation = (int) options.valueOf("maxRankTransformation");
        params.minCosineTransformation = (double) options.valueOf("minCosineTransformation");
        params.minSizeDirection = (int) options.valueOf("minSizeDirection");
        params.minHitRate = (double) options.valueOf("minHitRate");
        subber.scoreRules(params);

        OutputStream out = System.out;
        String outPath = (String) options.valueOf("outfile");
        log.info("Writing to {}", outPath.equals("-") ? "stdout" : outPath);
        if (!outPath.equals("-")) {
            out = new FileOutputStream(new File(outPath));
        }
        BufferedWriter outWriter = new BufferedWriter(new OutputStreamWriter(out));
        if (options.valueOf("format").equals("json")) {
            Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
            gson.toJson(subber.getScored(), outWriter);
        }
        outWriter.close();

        // Verify that serialized data can be read again.
        MorphologyManager morphologyManager = new MorphologyManager(null);
        morphologyManager.add("en", outPath);

        log.info("Done.");
    }

    /*
   * Parse command-line arguments.
   */
    private static OptionSet parse(String[] args) throws IOException {
        OptionParser parser = new OptionParser();

        // Corpus
        parser.accepts("corpus", "path to corpus file").withRequiredArg();
        parser.accepts("corpusMaxLines", "max lines to read").withRequiredArg().ofType(Integer.class).defaultsTo(0);
        parser.accepts("cacheCounts", "whether to cache corpus counts");
        parser.accepts("minVocabCount", "minimum corpus count to include a word type")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        parser.accepts("minPairCount", "minimum word pair count to include a substitution")
                .withRequiredArg().ofType(Integer.class).defaultsTo(2);
        parser.accepts("maxSplitsPerPair", "maximum number of ways a pair can be split")
                .withRequiredArg().ofType(Integer.class).defaultsTo(4);
        parser.accepts("minStemLength", "minimum length of a stem")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        parser.accepts("maxStemCardinality", "minimum length of a stem")
                .withRequiredArg().ofType(Integer.class).defaultsTo(100);

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
        parser.accepts("minHitRate", "Min hit rate of a rule to score transformations")
                .withRequiredArg().ofType(Double.class).defaultsTo(0.0);

        // HTTP Rest API
        parser.accepts("port", "API port").withRequiredArg().ofType(Integer.class).defaultsTo(DEFAULT_API_PORT);

        // Word2Vec
        parser.accepts("embeddings", "binary Word2Vec model file").withRequiredArg().ofType(File.class);
        parser.accepts("language", "language for Word2Vec embeddings").withRequiredArg();

        // Output
        parser.accepts("format", "output format").withRequiredArg().defaultsTo("json");
        parser.accepts("outfile", "output file (- for stdout)").withRequiredArg().defaultsTo("-");

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
