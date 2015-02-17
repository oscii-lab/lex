package org.oscii;

import org.oscii.panlex.PanLexDB;
import org.oscii.panlex.PanLexDBFromJSON;

public class Main {

    public static void main(String[] args) {
        // TODO Command line argument parsing
        String panLexDir = args[0];

        PanLexDB panLex = new PanLexDBFromJSON(panLexDir);

        System.out.println("Done.");
    }
}
