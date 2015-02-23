package org.oscii.panlex;

/**
 * Created by denero on 2/22/15.
 */
public class Models {
    // E.g., {"sy":1,"lc":"aar","vc":0,"am":1,"lv":1,"ex":1453510}
    static class LanguageVariety {
        String lc; // ISO 639-3 (3-letter) language code
        int lv; // Language variety key
        int ex; // Expression whose text is the language name
        int sy;
        int vc;
        int am;
    }

    // E.g., {"td":"𠁥","tt":"𠁥","lv":1836,"ex":19202960}
    static class Expression {
        int lv; // Language variety
        int ex; // Key
        String tt; // Text
        String td; // Degraded text
    }

    // E.g., {"mn":1,"ex":14,"dn":4}
    static class Denotation {
        int dn;
        int mn;
        int ex;
    }
}
