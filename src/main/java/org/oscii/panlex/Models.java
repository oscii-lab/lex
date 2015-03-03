package org.oscii.panlex;

/**
 * Models of the PanLex data export.
 */
public interface Models {
    // E.g., {"sy":1,"lc":"aar","vc":0,"am":1,"lv":1,"ex":1453510}
    static class Lv {
        int lv; // ID
        String lc; // ISO 639 (3-letter) language code
        int ex; // Expression whose text is the language name
        int vc; // Language-specific ID (for lv with the same lc)
        int am; // permits ambiguity
        int sy; // Permits synonymy
    }

    // E.g., {"td":"𠁥","tt":"𠁥","lv":1836,"ex":19202960}
    static class Ex {
        int ex; // ID
        int lv; // Language variety
        String tt; // Text
        String td; // Degraded text (lowercase, etc.)
    }

    // E.g., {"mn":1,"ap":1}
    static class Mn {
        int mn; // ID
        int ap; // Source
    }

    // E.g., {"mn":1,"ex":14,"dn":4}
    static class Dn {
        int dn; // ID
        int mn; // Meaning
        int ex; // Expression
    }

    // E.g., {"df":2657373,"tt":"come(s) from","mn":6,"lv":187,"td":"comesfrom"}
    static class Df {
        int df; // ID
        int mn; // Meaning
        int lv; // Language variety of the text
        String tt; // Text
        String td; // Degraded text (lowercase, etc.)
    }

    // E.g., {"ti":"Castellano–Totonaco, Totonaco–Castellano: Dialecto de la Sierra","ip":"© Summer Institute of Linguistics 1962. Derechos reservados conforme a la ley.","ad":null,"ul":"Serie de Vocabularios Indigenas “Mariano Silva y Aceves”, Núm. 7; segunda edición; content = pp. 7–73 of file","bn":null,"ui":4106,"dt":"2014-01-14","uq":8,"au":"Herman Pedro Aschmann","tt":"spa-tos:Aschmann","li":"co","pb":"Instituto Lingüístico de Verano","ur":"http://www-01.sil.org/mexico/totonaca/sierra/S007a-VocTotonacoFacs-tos.htm","yr":1983,"co":null,"ap":4106}
    static class Source {
        int ap; // ID
        String dt; // Registration date
        String tt; // Label
        String ti; // Title
        int uq; // Quality (editor's judgement)
        String li; // License type
    }

    // E.g., {"dn":51926083,"ex":3846608,"wc":4384599}
    static class Wc {
        int wc;
        int dn;
        int ex;
    }

    // E.g., {"tt":"noun","ex":3846607}
    static class Wcex {
        int ex;
        String tt;
    }
}
