package org.oscii;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.oscii.api.Protocol;
import org.oscii.api.RabbitHandler;
import org.oscii.api.Servlet;
import org.oscii.concordance.AlignedCorpus;
import org.oscii.concordance.IndexedAlignedCorpus;
import org.oscii.concordance.SuffixArrayCorpus;
import org.oscii.lex.Lexicon;
import org.oscii.panlex.PanLexDir;
import org.oscii.panlex.PanLexJSONParser;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class Main {

    private final static Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        CommandLine line = ParseArgs(args);
        Lexicon lexicon = new Lexicon();
        final AlignedCorpus corpus = line.hasOption("suffix") ? new SuffixArrayCorpus() : new IndexedAlignedCorpus();

        final String defaultLanguages = "en,es";
        final List<String> languages =
                Arrays.asList(line.getOptionValue("l", defaultLanguages).split(","));

        // Parse PanLex
        if (line.hasOption("panlex")) {
            final String path = line.getOptionValue("panlex");
            final PanLexJSONParser panLex = new PanLexJSONParser(new PanLexDir(path));
            panLex.addLanguages(languages);
            final String defaultPattern = "(?U)\\p{Lower}*";
            Pattern pattern = Pattern.compile(line.getOptionValue("pattern", defaultPattern));
            panLex.read(pattern);
            panLex.forEachMeaning(lexicon::add);
        } else if (line.hasOption("read")) {
            lexicon.read(new File(line.getOptionValue("read")));
        }

        // Index corpus
        if (line.hasOption("corpus")) {
            final String corpusPath = line.getOptionValue("corpus");
            final int max = Integer.parseInt(line.getOptionValue("max", "0"));
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

        if (line.hasOption("write")) {
            lexicon.write(new File(line.getOptionValue("write")));
        }

        final Protocol protocol = new Protocol(lexicon, corpus);

        // Serve lexicon (http API)
        Server server = null;
        if (line.hasOption("api")) {
            final int port = Integer.parseInt(line.getOptionValue("port", "8080"));
            server = new Server(port);
            final ServletHandler handler = new ServletHandler();
            final ServletHolder holder = new ServletHolder(new Servlet(protocol));
            handler.addServletWithMapping(holder, "/translate/lexicon");
            server.setHandler(handler);
            server.start();
        }

        // Serve lexicon (rabbitmq)
        if (line.hasOption("serve")) {
            final String host = line.getOptionValue("host", "localhost");
            final String queue = line.getOptionValue("queue", "lexicon");
            final String username = line.getOptionValue("username", "");
            final String password = line.getOptionValue("password", "");
            final RabbitHandler handler =
                    new RabbitHandler(host, queue, username, password, protocol);
            handler.ConnectAndListen();
        }

        if (server != null) {
            server.join();
        }
    }

    /*
     * Parse command-line arguments.
     */
    private static CommandLine ParseArgs(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("h", "help", false, "print this message");

        // Vanilla I/O
        options.addOption("r", "read", true, "read JSON file");
        options.addOption("w", "write", true, "write JSON file");

        // HTTP Rest API
        options.addOption("a", "api", false, "serve API over HTTP");
        options.addOption(OptionBuilder
                .withLongOpt("port")
                .withDescription("API port")
                .withType(Number.class)
                .hasArg()
                .create());

        // Rabbitmq
        options.addOption("s", "serve", false, "listen on local rabbitmq");
        options.addOption("t", "host", true, "rabbitmq host");
        options.addOption("q", "queue", true, "rabbitmq queue");
        options.addOption("u", "username", true, "rabbitmq username");
        options.addOption("v", "password", true, "rabbitmq password");

        // Parsing PanLex
        options.addOption("p", "panlex", true, "parse PanLex JSON");
        options.addOption("x", "pattern", true, "expression pattern");
        options.addOption("l", "languages", true, "comma-separated languages");

        // Concordance
        options.addOption("c", "corpus", true, "path to corpus (no suffixes)");
        options.addOption(OptionBuilder
                .withLongOpt("max")
                .withDescription("maximum number of sentence pairs")
                .withType(Number.class)
                .hasArg()
                .create("m"));
        options.addOption("f", "suffix", false, "use suffix array");

        CommandLineParser parser = new BasicParser();
        CommandLine line = parser.parse(options, args);

        if (line.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("lex", options);
        }
        return line;
    }
}
