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
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Command-line utility to train a detokenizer.
 */
public class TrainDetokenizer {
    public static void main(String[] args) throws IOException {
        OptionSet options = parse(args);

        Stream<String> rawCorpus = getLines(options, "raw");
        TokenizedCorpus corpus;
        if (options.has("tokenized")) {
            corpus = new TokenizedCorpus.ParallelCorpus(rawCorpus,  getLines(options, "tokenized"));
        } else {
            Preprocessor preprocessor = getPreprocessor(options);
            corpus = new TokenizedCorpus.PreprocessorCorpus(preprocessor, rawCorpus);
        }

        // Split corpus into test and trainining
        Iterator<TokenizedCorpus.Entry> all = corpus.stream().iterator();
        int testSize = (Integer) options.valueOf("testsize");
        List<TokenizedCorpus.Entry> test = new ArrayList<>(testSize);
        for (int i = 0; i < testSize; i++) {
            test.add(all.next());
        }
        int trainSize = (Integer) options.valueOf("trainsize");
        Iterator<TokenizedCorpus.Entry> training = trainSize == 0 ? all : Iterators.limit(all, trainSize);

        double regularization = (double) options.valueOf("regularization");
        Detokenizer detokenizer = Detokenizer.train(regularization, training);

        System.out.println("Test accuracy: " + detokenizer.evaluate(test.iterator()));
        if (options.has("errors")) {
            test.forEach(ex -> {
                List<String> tokens = ex.getTokens();
                String roundTrip = TokenLabel.render(tokens, detokenizer.predictLabels(tokens));
                if (!ex.getRaw().equals(roundTrip)) {
                    System.out.println("Original:  " + ex);
                    System.out.println("Detoken'd: " + roundTrip);
                }
            });
        }

        if (options.has("out")) {
            File outFile = (File) options.valueOf("out");
            detokenizer.save(outFile);
        }
    }

    /*
     * Get lines of a file (maybe gzipped) from a command line option.
     */
    private static Stream<String> getLines(OptionSet options, String option) throws IOException {
        File trainFile = (File) options.valueOf(option);
        InputStream trainStream = new FileInputStream(trainFile);
        if (trainFile.getName().endsWith(".gz")) {
            trainStream = new GZIPInputStream(trainStream);
        }
        BufferedReader buffered = new BufferedReader(new InputStreamReader(trainStream, "utf-8"));
        return buffered.lines();
    }

    private static Preprocessor getPreprocessor(OptionSet options) {
        Preprocessor preprocessor = null;
        boolean cased = true;
        switch (((String) options.valueOf("language")).toLowerCase()) {
            case "de":
            case "german":
                preprocessor = new GermanPreprocessor(cased);
                break;
            case "en":
            case "english":
                preprocessor = new EnglishPreprocessor(cased);
                break;
            case "es":
            case "spanish":
                preprocessor = new SpanishPreprocessor(cased);
                break;
            case "fr":
            case "french":
                preprocessor = new FrenchPreprocessor(cased);
                break;
        }
        return preprocessor;
    }

    private static OptionSet parse(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.accepts("language").withRequiredArg().defaultsTo("de");
        parser.accepts("raw").withRequiredArg().ofType(File.class);
        parser.accepts("tokenized").withRequiredArg().ofType(File.class);
        parser.accepts("out").withRequiredArg().ofType(File.class);
        parser.accepts("trainsize").withRequiredArg().ofType(Integer.class).defaultsTo(0); // Unlimited
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

