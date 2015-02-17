package org.oscii;

import org.oscii.panlex.PanLexDBFromJSON;

public class Main {

    public static void main(String[] args) {
        // TODO Command line argument parsing
        String panLexDir = args[0];

        PanLexDBFromJSON panLex = new PanLexDBFromJSON();
        System.out.println("Reading PanLex.");
        panLex.read(panLexDir);

        System.out.println("Done.");
    }
}
