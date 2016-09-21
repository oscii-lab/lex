package org.oscii;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.oscii.api.LexiconProtocol;
import org.oscii.api.LexServlet;
import org.oscii.concordance.AlignedCorpus;
import org.oscii.concordance.IndexedAlignedCorpus;
import org.oscii.concordance.RemoteAlignedCorpus;
import org.oscii.concordance.SentenceExample;
import org.oscii.lex.Lexicon;
import org.oscii.lex.Ranker;
import org.oscii.morph.MorphologyManager;
import org.oscii.neural.Word2VecManager;
import org.oscii.panlex.PanLexDir;
import org.oscii.panlex.PanLexJSONParser;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class Main {

    private final static Logger log = LogManager.getLogger(Main.class);
    private static final Integer DEFAULT_API_PORT = 8090;
    private static final Integer DEFAULT_MAX_SENTENCE_PAIRS = 100;
    private static final String DEFAULT_LANGUAGES = "en,es,de,fr";
    private static final String DEFAULT_PATTERN = "(?U)\\p{Lower}*";

    public static void main(String[] args) throws Exception {
        final OptionSet options = parse(args);
        final Lexicon lexicon = new Lexicon();
        final AlignedCorpus corpus = new IndexedAlignedCorpus();
        final List<String> languages = Arrays.asList(((String) options.valueOf("languages")).split(","));

        // Parse PanLex
        if (options.has("panlex")) {
            final String path = (String) options.valueOf("panlex");
            final PanLexJSONParser panLex = new PanLexJSONParser(new PanLexDir(path));
            panLex.addLanguages(languages);
            Pattern pattern = Pattern.compile((String) options.valueOf("pattern"));
            panLex.read(pattern);
            panLex.forEachMeaning(lexicon::add);
        } else if (options.has("read")) {
            lexicon.read((File) options.valueOf("read"));
        }

        // Index corpus (assumes a non-remote corpus; deprecated)
        if (options.has("corpus")) {
            final String corpusPath = (String) options.valueOf("corpus");
            final int max = (Integer) options.valueOf("max");
            for (String source : languages) {
                for (String target : languages) {
                    if (source.equals(target)) {
                        continue;
                    }
                    if (corpus.exists(corpusPath, source, target)) {
                        corpus.read(corpusPath, source, target, max);
                    } else {
                        log.warn("Could not find corpus for " + source + "-" + target + " in " + corpusPath);
                    }
                }
            }
            corpus.tally();
            lexicon.addFrequencies(corpus);
        }

        if (options.has("write")) {
            lexicon.write((File) options.valueOf("write"));
        }

        Ranker ranker = null;
        if (options.has("rank")) {
            ranker = new Ranker((File) options.valueOf("rank"));
        }

        Word2VecManager embeddings = null;
        MorphologyManager morphology = null;
        if (options.has("embeddings")) {
            if (!options.has("embeddingslangs")) {
                log.fatal("If using embeddings, you have to set the corresponding languages that are being represented in each model.");
                System.exit(-2);
            }
            String embeddingsFiles = (String) options.valueOf("embeddings");
            String embeddingsLangs = (String) options.valueOf("embeddingslangs");
            String[] files = embeddingsFiles.split(",");
            String[] langs = embeddingsLangs.split(",");
            String[] morph = new String[]{};
            if (options.has("embeddingsmorph")) {
                morph = ((String) options.valueOf("embeddingsmorph")).split(",");
            }
            if (langs.length != files.length) {
                log.fatal("Unequal number of Word2Vec models ({}) and languages ({}).",
                          files.length, langs.length);
                System.exit(-1);
            }
            embeddings = new Word2VecManager();
            if (morph.length == langs.length) {
                morphology = new MorphologyManager(lexicon);
            }
            for (int i = 0; i < files.length; ++i) {
                embeddings.add(langs[i], new File(files[i]));
                if (morphology != null) {
                    morphology.add(langs[i], morph[i], embeddings.getVocabulary(langs[i]));
                }
            }
        }

        final LexiconProtocol protocol = new LexiconProtocol(lexicon, corpus, ranker, embeddings, morphology);

        // Serve lexicon (http API)
        Server server = null;
        if (options.has("api")) {
            final int port = (Integer) options.valueOf("port");
            server = new Server(port);
            final ServletHandler handler = new ServletHandler();
            final ServletHolder holder = new ServletHolder(new LexServlet(protocol));
            handler.addServletWithMapping(holder, "/translate/lexicon");
            server.setHandler(handler);
            server.start();
            server.join();
        }
    }

    /*
     * Parse command-line arguments.
     */
    private static OptionSet parse(String[] args) throws IOException {
        OptionParser parser = new OptionParser();

        // Vanilla I/O
        parser.accepts("read", "read JSON file").withRequiredArg().ofType(File.class);
        parser.accepts("write", "write JSON file").withRequiredArg().ofType(File.class);

        // HTTP Rest API
        parser.accepts("api", "Whether to serve API over HTTP");
        parser.accepts("port", "API port").withRequiredArg().ofType(Integer.class).defaultsTo(DEFAULT_API_PORT);

        // Parsing PanLex
        parser.accepts("panlex", "parse PanLex JSON").withRequiredArg();
        parser.accepts("pattern", "expression pattern").withRequiredArg().defaultsTo(DEFAULT_PATTERN);
        parser.accepts("languages", "comma-separated languages").withRequiredArg().defaultsTo(DEFAULT_LANGUAGES);

        // Concordance
        parser.accepts("corpus", "path to corpus (no suffixes)").withRequiredArg();
        parser.accepts("max", "maximum number of sentence pairs").withRequiredArg().ofType(Integer.class).defaultsTo(DEFAULT_MAX_SENTENCE_PAIRS);

        // Ranker
        parser.accepts("rank", "path to CSV file with rankings").withRequiredArg().ofType(File.class);

        // Word2Vec
        parser.accepts("embeddings", "comma-separated list of binary Word2Vec model files").withRequiredArg().describedAs("FileList");
        parser.accepts("embeddingslangs", "comma-separated list of languages for Word2Vec models").withRequiredArg().describedAs("LangList");
        parser.accepts("embeddingsmorph", "comma-separated list of JSON neural morphology files").withRequiredArg().describedAs("MorphList");

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
