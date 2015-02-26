package org.oscii;

import org.apache.commons.cli.*;
import org.oscii.panlex.PanLexJSONParser;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args)
            throws java.io.IOException,
            java.lang.InterruptedException,
            ParseException {
        Options options = new Options();

        // Vanilla I/O
        options.addOption("r", "read", true, "read JSON file");
        options.addOption("w", "write", true, "write JSON file");

        // Rabbitmq
        options.addOption("s", "serve", false, "listen on local rabbitmq");
        options.addOption("h", "host", true, "rabbitmq host");
        options.addOption("q", "queue", true, "rabbitmq queue");
        options.addOption("u", "username", true, "rabbitmq username");
        options.addOption("a", "password", true, "rabbitmq password");

        // Parsing PanLex
        options.addOption("p", "panlex", true, "parse PanLex JSON");
        options.addOption("pattern", true, "expression pattern");
        options.addOption("l", "languages", true, "expression pattern");

        CommandLineParser parser = new BasicParser();
        CommandLine line = parser.parse(options, args);

        Lexicon lexicon = new Lexicon();

        if (line.hasOption("p")) {
            String path = line.getOptionValue("p");
            PanLexJSONParser panLex = new PanLexJSONParser(path);

            List<String> languages = Arrays.asList("en", "es", "fr", "de");
            if (line.hasOption("l")) {
                languages = Arrays.asList(line.getOptionValue("l").split(" "));
            }
            panLex.addLanguages(languages);

            Pattern pattern = Pattern.compile("(?U)\\p{Lower}*");
            if (line.hasOption("pattern")) {
                pattern = Pattern.compile(line.getOptionValue("pattern"));
            }
            panLex.read(pattern);

            panLex.yieldTranslations(lexicon::add);
        } else if (line.hasOption("r")) {
            lexicon.read(new File(line.getOptionValue("r")));
        }

        if (line.hasOption("w")) {
            lexicon.write(new File(line.getOptionValue("w")));
        }

        if (line.hasOption("s")) {
            String host = line.getOptionValue("h", "localhost");
            String queue = line.getOptionValue("q", "lexicon");
            String username = line.getOptionValue("u", "");
            String password = line.getOptionValue("a", "");
            RabbitHandler handler = new RabbitHandler(host, queue, username, password, lexicon);
            handler.ConnectAndListen();
        }
    }
}
