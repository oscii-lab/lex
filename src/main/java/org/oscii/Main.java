package org.oscii;

import org.oscii.panlex.PanLexJSONParser;

import java.io.File;
import java.util.Arrays;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args)
            throws java.io.IOException,
            java.lang.InterruptedException {
        // TODO Real command-line argument parsing
        String path = args[args.length - 1];
        boolean readPanLex = Arrays.asList(args).contains("-p");
        boolean writePanLex = Arrays.asList(args).contains("-w");
        boolean rabbit = Arrays.asList(args).contains("-r");

        Lexicon lexicon = new Lexicon();

        if (readPanLex) {
            PanLexJSONParser panLex = new PanLexJSONParser(path);
            panLex.addLanguages(Arrays.asList("en", "es", "fr", "de"));
            panLex.read(Pattern.compile("a.*"));
            panLex.yieldTranslations(lexicon::add);
            if (writePanLex) {
                lexicon.write(new File("panlex.json"));
            }
        } else {
            lexicon.read(new File(path));
        }

        if (rabbit) {
            RabbitHandler handler = new RabbitHandler("localhost", "lexicon", lexicon);
            handler.ConnectAndListen();
        }

        System.out.println("Done");
    }
}
