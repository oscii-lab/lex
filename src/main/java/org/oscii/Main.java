package org.oscii;

import org.oscii.panlex.PanLexDBFromJSON;

public class Main {

    public static void main(String[] args)
            throws java.io.IOException,
            java.lang.InterruptedException {
        // TODO Command line argument parsing
        String panLexDir = args[0];

        System.out.println("Reading PanLex");
        PanLexDBFromJSON panLex = new PanLexDBFromJSON();
        panLex.setMaxRecordsPerType(10000); // TODO(denero) Remove this limit
        panLex.read(panLexDir);

        System.out.println("Starting request handler");
        RabbitHandler handler = new RabbitHandler("localhost", "lexicon", panLex);
        handler.ConnectAndListen();

        System.out.println("Done");
    }
}
