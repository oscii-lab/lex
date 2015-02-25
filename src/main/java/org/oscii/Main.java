package org.oscii;

import org.oscii.panlex.PanLexJSONParser;

import java.util.Arrays;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args)
            throws java.io.IOException,
            java.lang.InterruptedException {
        // TODO Command line argument parsing
        String panLexDir = args[0];

        PanLexJSONParser panLex = new PanLexJSONParser(panLexDir);
        panLex.addLanguages(Arrays.asList("en", "es"));
        panLex.read(Pattern.compile("a.*"));

        Lexicon lexicon = new Lexicon();
        panLex.yieldTranslations(lexicon::add);

        System.out.println("Starting request handler");
        RabbitHandler handler = new RabbitHandler("localhost", "lexicon", lexicon);
        handler.ConnectAndListen();

        System.out.println("Done");
    }
}
