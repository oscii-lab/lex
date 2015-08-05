package org.oscii;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.oscii.api.LexiconProtocol;
import org.oscii.api.Servlet;
import org.oscii.concordance.AlignedCorpus;
import org.oscii.concordance.IndexedAlignedCorpus;
import org.oscii.lex.Lexicon;
import org.oscii.panlex.PanLexDir;
import org.oscii.panlex.PanLexJSONParser;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
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

        // Index corpus
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

        final LexiconProtocol protocol = new LexiconProtocol(lexicon, corpus);

        // Serve lexicon (http API)
        Server server = null;
        if (options.has("api")) {
            final int port = (Integer) options.valueOf("port");
            server = new Server(port);
            final ServletHandler handler = new ServletHandler();
            final ServletHolder holder = new ServletHolder(new Servlet(protocol));
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
