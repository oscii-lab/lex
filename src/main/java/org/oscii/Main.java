package org.oscii;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.cli.*;
import org.oscii.lex.Meaning;
import org.oscii.panlex.PanLexJSONParser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args)
            throws java.io.IOException,
            java.lang.InterruptedException,
            ParseException {
        CommandLine line = ParseArgs(args);
        Lexicon lexicon = new Lexicon();

        if (line.hasOption("p")) {
            String path = line.getOptionValue("p");
            PanLexJSONParser panLex = new PanLexJSONParser(path);

            List<String> languages = Arrays.asList("en", "es");
            if (line.hasOption("l")) {
                languages = Arrays.asList(line.getOptionValue("l").split(","));
            }
            panLex.addLanguages(languages);

            Pattern pattern = Pattern.compile("(?U)\\p{Lower}*");
            if (line.hasOption("pattern")) {
                pattern = Pattern.compile(line.getOptionValue("pattern"));
            }
            panLex.read(pattern);

            if (line.hasOption("w")) {
                writeMeanings(line.getOptionValue("w"), panLex);
            } else {
                panLex.yieldTranslations(lexicon::add);
            }
        }

        if (line.hasOption("r")) {
            lexicon.read(new File(line.getOptionValue("r")));
        }

        if (line.hasOption("s")) {
            String host = line.getOptionValue("t", "localhost");
            String queue = line.getOptionValue("q", "lexicon");
            String username = line.getOptionValue("u", "");
            String password = line.getOptionValue("v", "");
            RabbitHandler handler = new RabbitHandler(host, queue, username, password, lexicon);
            handler.ConnectAndListen();
        }
    }

    private static CommandLine ParseArgs(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("h", "help", false, "print this message");

        // Vanilla I/O
        options.addOption("r", "read", true, "read JSON file");
        options.addOption("w", "write", true, "write JSON file");

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

        CommandLineParser parser = new BasicParser();
        CommandLine line = parser.parse(options, args);

        if (line.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("lex", options);
        }
        return line;
    }

    private static void writeMeanings(String path, PanLexJSONParser panLex)
            throws IOException {
        JsonWriter writer = new JsonWriter(new FileWriter(path));
        Gson gson = new Gson();
        writer.beginArray();
        panLex.yieldTranslations(m -> gson.toJson(m, Meaning.class, writer));
        writer.endArray();
        writer.close();
    }
}
