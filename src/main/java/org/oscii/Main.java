package org.oscii;

import org.apache.commons.cli.*;
import org.oscii.panlex.PanLexJSONParser;

import java.io.File;
import java.util.Arrays;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args)
            throws java.io.IOException,
            java.lang.InterruptedException,
            ParseException {
        Options options = new Options();
        options.addOption("r", "read", true, "read JSON file");
        options.addOption("w", "write", true, "write JSON file");
        options.addOption("p", "panlex", true, "parse PanLex JSON");
        options.addOption("s", "serve", false, "listen on local rabbitmq");
        CommandLineParser parser = new BasicParser();
        CommandLine line = parser.parse( options, args );

        Lexicon lexicon = new Lexicon();

        if (line.hasOption("p")) {
            String path = line.getOptionValue("p");
            PanLexJSONParser panLex = new PanLexJSONParser(path);
            // TODO(denero) Add "languages" command-line option
            panLex.addLanguages(Arrays.asList("en", "es", "fr", "de"));
            panLex.read(null);
            panLex.yieldTranslations(lexicon::add);
        } else if (line.hasOption("r")) {
            lexicon.read(new File(line.getOptionValue("r")));
        }

        if (line.hasOption("w")) {
            lexicon.write(new File(line.getOptionValue("w")));
        }

        if (line.hasOption("s")) {
            RabbitHandler handler = new RabbitHandler("localhost", "lexicon", lexicon);
            handler.ConnectAndListen();
        }
    }
}
