package org.oscii;

import org.apache.commons.cli.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.oscii.api.Protocol;
import org.oscii.api.RabbitHandler;
import org.oscii.api.Servlet;
import org.oscii.concordance.AlignedCorpus;
import org.oscii.panlex.PanLexDir;
import org.oscii.panlex.PanLexJSONParser;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) throws Exception {
        CommandLine line = ParseArgs(args);
        Lexicon lexicon = new Lexicon();

        List<String> languages = Arrays.asList("en", "es");
        if (line.hasOption("l")) {
            languages = Arrays.asList(line.getOptionValue("l").split(","));
        }

        // Parse PanLex
        if (line.hasOption("p")) {
            String path = line.getOptionValue("p");
            PanLexJSONParser panLex = new PanLexJSONParser(new PanLexDir(path));
            panLex.addLanguages(languages);
            Pattern pattern = Pattern.compile("(?U)\\p{Lower}*");
            if (line.hasOption("pattern")) {
                pattern = Pattern.compile(line.getOptionValue("pattern"));
            }
            panLex.read(pattern);
            panLex.forEachMeaning(lexicon::add);
        } else if (line.hasOption("r")) {
            lexicon.read(new File(line.getOptionValue("r")));
        }

        // Index corpus
        if (line.hasOption("c")) {
            String corpusPath = line.getOptionValue("c");
            AlignedCorpus corpus = new AlignedCorpus();
            // TODO(denero) Load all corpora for languages
            corpus.read(corpusPath, "en", "es",
                    line.hasOption("m") ? Integer.parseInt(line.getOptionValue("m")) : 0);
            lexicon.addFrequencies(corpus);
        }

        if (line.hasOption("w")) {
            lexicon.write(new File(line.getOptionValue("w")));
        }

        Protocol protocol = new Protocol(lexicon);

        // Serve lexicon (rabbitmq)
        if (line.hasOption("s")) {
            String host = line.getOptionValue("t", "localhost");
            String queue = line.getOptionValue("q", "lexicon");
            String username = line.getOptionValue("u", "");
            String password = line.getOptionValue("v", "");
            RabbitHandler handler =
                    new RabbitHandler(host, queue, username, password, protocol);
            handler.ConnectAndListen();
        }

        // Serve lexicon (http API)
        if (line.hasOption("a")) {
            int port = 8080;
            if (line.hasOption("port")) {
                Integer.parseInt(line.getOptionValue("port"));
            }
            Server server = new Server(port);
            ServletHandler handler = new ServletHandler();
            ServletHolder holder = new ServletHolder(new Servlet(protocol));
            handler.addServletWithMapping(holder, "/translate/lexicon");
            server.setHandler(handler);
            server.start();
            // TODO(denero) allow both serving methods at once
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
        options.addOption("port", false, "API port");

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
        // TODO(denero) make -m an int value with default 0
        options.addOption("m", "max", true, "maximum number of sentence pairs");

        CommandLineParser parser = new BasicParser();
        CommandLine line = parser.parse(options, args);

        if (line.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("lex", options);
        }
        return line;
    }
}
